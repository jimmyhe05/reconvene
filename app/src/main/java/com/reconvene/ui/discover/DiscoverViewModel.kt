package com.reconvene.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
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
) : ViewModel() {

    private val _uiState = MutableStateFlow<DiscoverUiState>(DiscoverUiState.Loading)
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

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

    fun join(podId: String) = changePod(podId) { user ->
        podRepository.joinPod(podId, user.asMember())
    }

    fun leave(podId: String) = changePod(podId) { user ->
        podRepository.leavePod(podId, user.uid)
    }

    // The card asks the host to confirm first. The pod then drops out of the list, so the message
    // confirms where it went.
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
            combine(podRepository.observePods(), refresh, busyPodIds) { pods, refreshStatus, busy ->
                toUiState(pods, refreshStatus, user.uid, busy)
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
    ): DiscoverUiState {
        val now = clock.instant()
        val sections = groupIntoSections(pods, now, clock.zone)
        return when {
            sections.isNotEmpty() -> DiscoverUiState.Content(sections, now, viewerUid, busy)
            refresh == RefreshStatus.RUNNING -> DiscoverUiState.Loading
            refresh == RefreshStatus.FAILED -> DiscoverUiState.Error
            else -> DiscoverUiState.Empty
        }
    }

    private fun PodActionProblem.toMessage(): PodMessage = when (this) {
        PodActionProblem.FULL -> PodMessage.FULL
        PodActionProblem.CLOSED -> PodMessage.CLOSED
        PodActionProblem.NOT_FOUND -> PodMessage.GONE
        // The card shows Leave only to members and Cancel only to the host, so these mean the
        // cached pod is out of date.
        PodActionProblem.HOST_CANNOT_LEAVE, PodActionProblem.NOT_HOST -> PodMessage.FAILED
    }

    private enum class RefreshStatus { RUNNING, DONE, FAILED }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ReconveneApplication).container
                DiscoverViewModel(container.podRepository, container.authRepository, container.clock)
            }
        }
    }
}
