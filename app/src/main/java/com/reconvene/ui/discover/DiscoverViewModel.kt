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
import com.reconvene.data.model.Pod
import com.reconvene.data.repository.PodRepository
import com.reconvene.ui.pod.PodActionRunner
import com.reconvene.ui.pod.PodMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
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

    private val actions = PodActionRunner(podRepository, viewModelScope)
    val message: StateFlow<PodMessage?> = actions.message

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

    fun join(podId: String) {
        viewer?.let { actions.join(podId, it) }
    }

    fun leave(podId: String) {
        viewer?.let { actions.leave(podId, it) }
    }

    fun cancel(podId: String) {
        viewer?.let { actions.cancel(podId, it) }
    }

    fun messageShown() = actions.messageShown()

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
            combine(podRepository.observePods(), refresh, actions.busyPodIds, filter) { pods, refreshStatus, busy, filter ->
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
