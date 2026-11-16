package com.reconvene.ui.discover

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.reconvene.ReconveneApplication
import com.reconvene.data.auth.AuthRepository
import com.reconvene.data.auth.SignedInUser
import com.reconvene.data.auth.actingUser
import com.reconvene.data.auth.asMember
import com.reconvene.data.model.Pod
import com.reconvene.data.model.PodActionException
import com.reconvene.data.model.PodActionProblem
import com.reconvene.data.repository.PodRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock

class DiscoverViewModel(
    private val podRepository: PodRepository,
    private val authRepository: AuthRepository,
    private val clock: Clock,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    private val _uiState = MutableStateFlow<DiscoverUiState>(DiscoverUiState.Loading)
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    // Kept in saved state, so a search survives the app being closed in the background.
    private val filter: StateFlow<PodFilter> = savedStateHandle.getStateFlow(FILTER_KEY, PodFilter())

    private val _message = MutableStateFlow<PodMessage?>(null)
    val message: StateFlow<PodMessage?> = _message.asStateFlow()

    private val busyPodIds = MutableStateFlow(emptySet<String>())
    private var viewer: SignedInUser? = null
    private var loadJob: Job? = null

    init {
        // Reloads on sign-in and sign-out, since what the user may read depends on who they are.
        viewModelScope.launch {
            authRepository.actingUser()
                .distinctUntilChangedBy { it?.uid }
                .collect { user ->
                    viewer = user
                    loadPods()
                }
        }
    }

    fun retry() = loadPods()

    // Takes a change to the current filter, not a whole new one, so typing and tapping a chip
    // can't overwrite each other with an out-of-date copy.
    fun updateFilter(change: (PodFilter) -> PodFilter) {
        savedStateHandle[FILTER_KEY] = change(filter.value)
    }

    fun clearFilter() = updateFilter { PodFilter() }

    fun join(podId: String) = changePod(podId) { user ->
        podRepository.joinPod(podId, user.asMember())
    }

    fun leave(podId: String) = changePod(podId) { user ->
        podRepository.leavePod(podId, user.uid)
    }

    // The card asks the host to confirm first. The pod then drops out of the list, so the message confirms where it went.
    fun cancel(podId: String) = changePod(podId) { user ->
        podRepository.cancelPod(podId, user.uid)
        _message.value = PodMessage.CANCELED
    }

    fun messageShown() {
        _message.value = null
    }

    private fun changePod(podId: String, change: suspend (SignedInUser) -> Unit) {
        val user = viewer ?: return
        if (podId in busyPodIds.value) return
        busyPodIds.update { it + podId }
        viewModelScope.launch {
            try {
                change(user)
            } catch (e: CancellationException) {
                throw e
            } catch (e: PodActionException) {
                _message.value = e.problem.toMessage()
            } catch (e: Exception) {
                _message.value = PodMessage.FAILED
            } finally {
                busyPodIds.update { it - podId }
            }
        }
    }

    private fun loadPods() {
        loadJob?.cancel()
        val user = viewer
        if (user == null) {
            _uiState.value = DiscoverUiState.SignedOut
            return
        }
        _uiState.value = DiscoverUiState.Loading
        loadJob = viewModelScope.launch {
            val refresh = MutableStateFlow(RefreshStatus.RUNNING)
            launch {
                refresh.value = try {
                    podRepository.refresh()
                    RefreshStatus.DONE
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    RefreshStatus.FAILED
                }
            }
            combine(podRepository.observePods(), refresh, busyPodIds, filter) { pods, refreshStatus, busy, filter ->
                toUiState(pods, refreshStatus, user.uid, busy, filter)
            }
                .catch { emit(DiscoverUiState.Error) }
                .collect { _uiState.value = it }
        }
    }

    private fun toUiState(
        pods: List<Pod>,
        refresh: RefreshStatus,
        viewerUid: String,
        busy: Set<String>,
        filter: PodFilter,
    ): DiscoverUiState {
        val now = clock.instant()
        val listed = groupIntoSections(pods, now, clock.zone)
        return when {
            // With pods listed, a filter that matches none of them still shows Content, so the
            // search bar stays on screen and the user can change it.
            listed.isNotEmpty() -> {
                val sections = if (filter.isActive) {
                    groupIntoSections(pods.filter { filter.matches(it, viewerUid, now, clock.zone) }, now, clock.zone)
                } else {
                    listed
                }
                DiscoverUiState.Content(sections, now, viewerUid, busy, filter)
            }
            refresh == RefreshStatus.RUNNING -> DiscoverUiState.Loading
            refresh == RefreshStatus.FAILED -> DiscoverUiState.Error
            else -> DiscoverUiState.Empty
        }
    }

    private fun PodActionProblem.toMessage(): PodMessage = when (this) {
        PodActionProblem.FULL -> PodMessage.FULL
        PodActionProblem.CLOSED -> PodMessage.CLOSED
        PodActionProblem.NOT_FOUND -> PodMessage.GONE
        // The card shows Leave only to members and Cancel only to the host, so these mean the cached pod is out of date.
        PodActionProblem.HOST_CANNOT_LEAVE, PodActionProblem.NOT_HOST -> PodMessage.FAILED
    }

    private enum class RefreshStatus { RUNNING, DONE, FAILED }

    companion object {
        private const val FILTER_KEY = "filter"

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ReconveneApplication).container
                DiscoverViewModel(
                    podRepository = container.podRepository,
                    authRepository = container.authRepository,
                    clock = container.clock,
                    savedStateHandle = createSavedStateHandle(),
                )
            }
        }
    }
}
