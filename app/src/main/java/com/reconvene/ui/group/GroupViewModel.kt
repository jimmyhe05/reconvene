package com.reconvene.ui.group

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
import com.reconvene.data.auth.asMember
import com.reconvene.data.model.Group
import com.reconvene.data.model.GroupActionException
import com.reconvene.data.model.GroupMessage
import com.reconvene.data.model.GroupProblem
import com.reconvene.data.model.PodMember
import com.reconvene.data.model.ReportReason
import com.reconvene.data.repository.GroupRepository
import com.reconvene.ui.create.CreatePodForm
import com.reconvene.ui.navigation.GroupRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.util.UUID

class GroupViewModel(
    private val groupId: String,
    private val groupRepository: GroupRepository,
    authRepository: AuthRepository,
    private val clock: Clock,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {

    private val viewer: StateFlow<SignedInUser?> =
        authRepository.actingUser().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val chatSyncFailed = MutableStateFlow(false)
    private val syncAttempts = MutableStateFlow(0)
    private val busy = MutableStateFlow(false)

    private val _notice = MutableStateFlow<GroupNotice?>(null)
    val notice: StateFlow<GroupNotice?> = _notice.asStateFlow()

    // Reads the user straight from auth, like the Groups tab, so it stays Loading until auth answers.
    // Who's blocked depends on who's signed in, hence the switch per user.
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<GroupUiState> = authRepository.actingUser()
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(GroupUiState.Gone)
            } else {
                combine(
                    groupRepository.observeGroup(groupId),
                    groupRepository.observeMessages(groupId),
                    groupRepository.observeBlocked(user.uid),
                    chatSyncFailed,
                    busy,
                ) { group, messages, blocked, syncFailed, isBusy ->
                    toUiState(group, messages, blocked, user, syncFailed, isBusy)
                }
            }
        }
        .catch { emit(GroupUiState.Error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupUiState.Loading)

    init {
        // Keeps the group and its chat matching the server while the screen is open. Starts over on
        // sign-in or when the user taps Try again.
        viewModelScope.launch {
            combine(viewer, syncAttempts) { user, _ -> user }.collectLatest { user ->
                if (user != null) sync(user.uid)
            }
        }
    }

    fun retrySync() = syncAttempts.update { it + 1 }

    // Not guarded like the actions below: the message is saved and shown as sending at once, and
    // its status says how it went.
    fun send(text: String) {
        val user = viewer.value ?: return
        launchReporting { groupRepository.sendMessage(groupId, user.asMember(), text) }
    }

    fun retry(messageId: String) = launchReporting { groupRepository.retryMessage(messageId) }

    fun report(messageId: String, reason: ReportReason) = runAction(GroupNotice.REPORTED) { user ->
        groupRepository.report(groupId, messageId, user.uid, reason)
    }

    // Saved with the name their messages show, so Profile can list them to unblock.
    fun block(blockedUid: String) = runAction(GroupNotice.BLOCKED) { user ->
        val content = uiState.value as? GroupUiState.Content
        val name = content?.messages?.firstOrNull { it.authorUid == blockedUid }?.authorName
            ?: content?.group?.members?.firstOrNull { it.uid == blockedUid }?.name
            ?: throw GroupActionException(GroupProblem.NOT_FOUND)
        groupRepository.block(user.uid, PodMember(blockedUid, name))
    }

    fun propose(form: CreatePodForm) = runAction(GroupNotice.PROPOSED) { user ->
        groupRepository.propose(groupId, form.toProposal(id = newId(), byUid = user.uid, zone = clock.zone))
    }

    fun vote(canCome: Boolean) = runAction { user ->
        groupRepository.vote(groupId, user.asMember(), canCome)
    }

    // The new pod is saved for Discover before this returns.
    fun schedule() = runAction(GroupNotice.SCHEDULED) { user ->
        groupRepository.schedule(groupId, user.uid)
    }

    fun noticeShown() {
        _notice.value = null
    }

    // The group syncs here too, not only on the Groups tab: after Android restores this screen on its
    // own, the Groups tab isn't running, and votes would never arrive.
    private suspend fun sync(uid: String) = coroutineScope {
        chatSyncFailed.value = false
        launch { reportingFailure { groupRepository.syncGroups(uid) } }
        reportingFailure { groupRepository.syncMessages(groupId) }
    }

    private suspend fun reportingFailure(sync: suspend () -> Unit) {
        try {
            sync()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            chatSyncFailed.value = true
        }
    }

    // One action at a time, so a double tap can't vote or schedule twice.
    private fun runAction(done: GroupNotice? = null, action: suspend (SignedInUser) -> Unit) {
        val user = viewer.value ?: return
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                action(user)
                done?.let { _notice.value = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: GroupActionException) {
                _notice.value = e.problem.toNotice()
            } catch (e: Exception) {
                _notice.value = GroupNotice.FAILED
            } finally {
                busy.value = false
            }
        }
    }

    private fun launchReporting(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: GroupActionException) {
                _notice.value = e.problem.toNotice()
            } catch (e: Exception) {
                _notice.value = GroupNotice.FAILED
            }
        }
    }

    private fun toUiState(
        group: Group?,
        messages: List<GroupMessage>,
        blocked: Set<String>,
        user: SignedInUser,
        syncFailed: Boolean,
        isBusy: Boolean,
    ): GroupUiState = if (group == null || !group.isMember(user.uid)) {
        GroupUiState.Gone
    } else {
        GroupUiState.Content(
            group = group,
            messages = messages.filterNot { it.authorUid in blocked },
            viewerUid = user.uid,
            now = clock.instant(),
            chatSyncFailed = syncFailed,
            isBusy = isBusy,
        )
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ReconveneApplication).container
                GroupViewModel(
                    groupId = createSavedStateHandle().toRoute<GroupRoute>().groupId,
                    groupRepository = container.groupRepository,
                    authRepository = container.authRepository,
                    clock = container.clock,
                )
            }
        }
    }
}
