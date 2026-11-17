package com.reconvene.ui.groups

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
import com.reconvene.data.model.Group
import com.reconvene.data.model.GroupActionException
import com.reconvene.data.model.GroupProblem
import com.reconvene.data.repository.GroupRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GroupsViewModel(
    private val groupRepository: GroupRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    private val viewer: StateFlow<SignedInUser?> =
        authRepository.actingUser().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val syncStatus = MutableStateFlow(SyncStatus.RUNNING)
    private val retries = MutableStateFlow(0)
    private val busyGroupIds = MutableStateFlow<Set<String>>(emptySet())

    private val _message = MutableStateFlow<GroupNotice?>(null)
    val message: StateFlow<GroupNotice?> = _message.asStateFlow()

    // Reads the user straight from auth, not from viewer, so the screen stays Loading until auth
    // answers instead of flashing the signed-out prompt.
    val uiState: StateFlow<GroupsUiState> =
        combine(groupRepository.observeGroups(), authRepository.actingUser(), syncStatus, busyGroupIds) {
                groups, user, sync, busy ->
            toUiState(groups, user, sync, busy)
        }
            .catch { emit(GroupsUiState.Error) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupsUiState.Loading)

    init {
        // Starts over when someone signs in or out, or taps Try again.
        viewModelScope.launch {
            combine(viewer, retries) { user, _ -> user }.collectLatest { user ->
                if (user != null) sync(user.uid)
            }
        }
    }

    fun retry() = retries.update { it + 1 }

    fun accept(groupId: String) = answer(groupId, accept = true)

    fun decline(groupId: String) = answer(groupId, accept = false)

    fun messageShown() {
        _message.value = null
    }

    private suspend fun sync(uid: String) {
        syncStatus.value = SyncStatus.RUNNING
        try {
            groupRepository.syncGroups(uid) { syncStatus.value = SyncStatus.DONE }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            syncStatus.value = SyncStatus.FAILED
        }
    }

    private fun answer(groupId: String, accept: Boolean) {
        val user = viewer.value ?: return
        if (groupId in busyGroupIds.value) return
        busyGroupIds.update { it + groupId }
        viewModelScope.launch {
            try {
                groupRepository.answerInvite(groupId, user.uid, accept)
                _message.value = if (accept) GroupNotice.JOINED else GroupNotice.DECLINED
            } catch (e: CancellationException) {
                throw e
            } catch (e: GroupActionException) {
                _message.value = e.problem.toNotice()
            } catch (e: Exception) {
                _message.value = GroupNotice.FAILED
            } finally {
                busyGroupIds.update { it - groupId }
            }
        }
    }

    private fun toUiState(
        groups: List<Group>,
        user: SignedInUser?,
        sync: SyncStatus,
        busy: Set<String>,
    ): GroupsUiState {
        if (user == null) return GroupsUiState.SignedOut
        // Filtered by user because Room can still hold the last account's groups until this one syncs.
        val invites = groups.filter { it.isInvited(user.uid) }
        val joined = groups.filter { it.isMember(user.uid) }
        return when {
            invites.isNotEmpty() || joined.isNotEmpty() -> GroupsUiState.Content(invites, joined, user.uid, busy)
            sync == SyncStatus.DONE -> GroupsUiState.Empty
            sync == SyncStatus.FAILED -> GroupsUiState.Error
            else -> GroupsUiState.Loading
        }
    }

    private enum class SyncStatus { RUNNING, DONE, FAILED }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ReconveneApplication).container
                GroupsViewModel(container.groupRepository, container.authRepository)
            }
        }
    }
}

private fun GroupProblem.toNotice(): GroupNotice = when (this) {
    GroupProblem.NOT_FOUND, GroupProblem.DISBANDED, GroupProblem.NOT_INVITED -> GroupNotice.INVITE_GONE
    GroupProblem.ARCHIVED -> GroupNotice.ARCHIVED
    else -> GroupNotice.FAILED
}
