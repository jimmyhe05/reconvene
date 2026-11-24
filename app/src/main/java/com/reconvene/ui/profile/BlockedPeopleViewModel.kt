package com.reconvene.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.reconvene.ReconveneApplication
import com.reconvene.data.auth.AuthRepository
import com.reconvene.data.auth.SignedInUser
import com.reconvene.data.model.PodMember
import com.reconvene.data.repository.GroupRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BlockedPeopleUiState(
    val signedIn: Boolean = false,
    // By name.
    val blocked: List<PodMember> = emptyList(),
    val unblockFailed: Boolean = false,
)

class BlockedPeopleViewModel(
    authRepository: AuthRepository,
    private val groupRepository: GroupRepository,
) : ViewModel() {

    private val unblockFailed = MutableStateFlow(false)
    private val viewer: StateFlow<SignedInUser?> =
        authRepository.currentUser.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Follows whoever is signed in, so another account never sees this one's list. If the list can't
    // be read it shows as empty; blocking still works from the chat.
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<BlockedPeopleUiState> = authRepository.currentUser
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(BlockedPeopleUiState())
            } else {
                combine(
                    groupRepository.observeBlockedPeople(user.uid).catch { emit(emptyList()) },
                    unblockFailed,
                ) { blocked, failed -> BlockedPeopleUiState(signedIn = true, blocked = blocked, unblockFailed = failed) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BlockedPeopleUiState())

    // They drop off the list as soon as the phone applies it; offline it reaches the server later.
    fun unblock(blockedUid: String) {
        val uid = viewer.value?.uid ?: return
        unblockFailed.value = false
        viewModelScope.launch {
            try {
                groupRepository.unblock(uid, blockedUid)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                unblockFailed.value = true
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ReconveneApplication).container
                BlockedPeopleViewModel(container.authRepository, container.groupRepository)
            }
        }
    }
}
