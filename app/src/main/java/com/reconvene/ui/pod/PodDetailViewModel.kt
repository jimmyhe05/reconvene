package com.reconvene.ui.pod

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.toRoute
import com.reconvene.ReconveneApplication
import com.reconvene.data.auth.AuthRepository
import com.reconvene.data.auth.SignedInUser
import com.reconvene.data.auth.actingUser
import com.reconvene.data.model.Pod
import com.reconvene.data.repository.GroupRepository
import com.reconvene.data.repository.PodRepository
import com.reconvene.ui.navigation.PodDetailRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock

class PodDetailViewModel(
    private val podId: String,
    private val podRepository: PodRepository,
    private val groupRepository: GroupRepository,
    authRepository: AuthRepository,
    private val clock: Clock,
) : ViewModel() {

    private val actions = PodActionRunner(podRepository, viewModelScope)
    val message: StateFlow<PodMessage?> = actions.message

    private val viewer: StateFlow<SignedInUser?> =
        authRepository.actingUser().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val refresh = MutableStateFlow(RefreshStatus.RUNNING)

    private val keeping = MutableStateFlow(false)

    // The kept group is saved in Room before keepGroup returns, so once it's there the option hides.
    private val keepStatus: Flow<KeepStatus> = combine(
        groupRepository.observeGroups().map { groups -> groups.any { it.fromPodId == podId } },
        keeping,
    ) { alreadyKept, isKeeping -> KeepStatus(alreadyKept, isKeeping) }

    val uiState: StateFlow<PodDetailUiState> =
        combine(podRepository.observePod(podId), viewer, refresh, actions.busyPodIds, keepStatus) {
                pod, user, refresh, busy, keep ->
            toUiState(pod, user, refresh, podId in busy, keep)
        }
            .catch { emit(PodDetailUiState.Error) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PodDetailUiState.Loading)

    init {
        refreshPods()
    }

    fun retry() = refreshPods()

    fun join() {
        viewer.value?.let { actions.join(podId, it) }
    }

    fun leave() {
        viewer.value?.let { actions.leave(podId, it) }
    }

    fun cancel() {
        viewer.value?.let { actions.cancel(podId, it) }
    }

    // The dialog only allows a valid name, so any failure here means the server couldn't be reached.
    fun keep(name: String) {
        val user = viewer.value ?: return
        if (keeping.value) return
        keeping.value = true
        viewModelScope.launch {
            try {
                groupRepository.keepGroup(podId, user.uid, name)
                actions.show(PodMessage.GROUP_KEPT)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                actions.show(PodMessage.FAILED)
            } finally {
                keeping.value = false
            }
        }
    }

    fun messageShown() = actions.messageShown()

    // The stored copy shows at once. The refresh catches changes since Discover last loaded, and
    // tells "not stored yet" apart from "gone".
    private fun refreshPods() {
        refresh.value = RefreshStatus.RUNNING
        viewModelScope.launch {
            refresh.value = try {
                podRepository.refresh()
                RefreshStatus.DONE
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                RefreshStatus.FAILED
            }
        }
    }

    private fun toUiState(
        pod: Pod?,
        user: SignedInUser?,
        refresh: RefreshStatus,
        isBusy: Boolean,
        keep: KeepStatus,
    ) = when {
        pod == null -> when (refresh) {
            RefreshStatus.RUNNING -> PodDetailUiState.Loading
            RefreshStatus.FAILED -> PodDetailUiState.Error
            RefreshStatus.DONE -> PodDetailUiState.Gone()
        }
        pod.canceled -> PodDetailUiState.Gone(canceledByYou = user != null && pod.hostUid == user.uid)
        else -> PodDetailUiState.Content(pod, clock.instant(), user?.uid, isBusy, keepOption(pod, user, keep))
    }

    // Only the host, while the pod is running, with someone to invite, and once per pod.
    private fun keepOption(pod: Pod, user: SignedInUser?, keep: KeepStatus): KeepOption {
        val now = clock.instant()
        val canKeep = user != null &&
            user.uid == pod.hostUid &&
            now >= pod.startsAt &&
            now < pod.endsAt &&
            pod.members.size >= 2 &&
            !keep.alreadyKept
        return when {
            !canKeep -> KeepOption.HIDDEN
            keep.isKeeping -> KeepOption.KEEPING
            else -> KeepOption.AVAILABLE
        }
    }

    private enum class RefreshStatus { RUNNING, DONE, FAILED }

    private data class KeepStatus(val alreadyKept: Boolean, val isKeeping: Boolean)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ReconveneApplication).container
                PodDetailViewModel(
                    podId = createSavedStateHandle().toRoute<PodDetailRoute>().podId,
                    podRepository = container.podRepository,
                    groupRepository = container.groupRepository,
                    authRepository = container.authRepository,
                    clock = container.clock,
                )
            }
        }
    }
}
