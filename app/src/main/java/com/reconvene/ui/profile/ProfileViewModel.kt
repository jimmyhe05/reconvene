package com.reconvene.ui.profile

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.reconvene.ReconveneApplication
import com.reconvene.data.auth.AuthRepository
import com.reconvene.data.auth.NotBuAccountException
import com.reconvene.data.auth.SignedInUser
import com.reconvene.data.model.PodMember
import com.reconvene.data.repository.GroupRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SignInError { NOT_BU_ACCOUNT, NO_BU_ACCOUNT, FAILED }

data class ProfileUiState(
    val isAvailable: Boolean,
    val user: SignedInUser? = null,
    val isSigningIn: Boolean = false,
    val error: SignInError? = null,
    // People the signed-in user blocked, by name, so they can be unblocked here.
    val blocked: List<PodMember> = emptyList(),
    val unblockFailed: Boolean = false,
)

class ProfileViewModel(
    private val authRepository: AuthRepository,
    private val groupRepository: GroupRepository,
    // The OAuth web client the Google ID token is issued for, from local.properties.
    val googleWebClientId: String,
) : ViewModel() {

    private data class SignInProgress(val isSigningIn: Boolean = false, val error: SignInError? = null)

    private val progress = MutableStateFlow(SignInProgress())
    private val unblockFailed = MutableStateFlow(false)

    // Follows whoever is signed in, so another account never sees this one's list. If the list can't
    // be read it shows as empty; blocking still works from the chat.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val blocked: Flow<List<PodMember>> = authRepository.currentUser.flatMapLatest { user ->
        if (user == null) flowOf(emptyList()) else groupRepository.observeBlockedPeople(user.uid).catch { emit(emptyList()) }
    }

    // current user + login progress + blocked people = page state
    val uiState: StateFlow<ProfileUiState> =
        combine(authRepository.currentUser, progress, blocked, unblockFailed) { user, progress, blocked, unblockFailed ->
            ProfileUiState(
                isAvailable = authRepository.isAvailable,
                user = user,
                isSigningIn = progress.isSigningIn,
                error = progress.error,
                blocked = if (user == null) emptyList() else blocked,
                unblockFailed = unblockFailed,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ProfileUiState(isAvailable = authRepository.isAvailable),
        )

    // The screen passes in how to get a Google ID token, because the account picker needs an activity. It runs in the screen's scope, so leaving the screen cancels a pending picker.
    suspend fun signIn(getIdToken: suspend () -> String) {
        if (progress.value.isSigningIn) return
        progress.value = SignInProgress(isSigningIn = true)
        var error: SignInError? = null
        try {
            authRepository.signInWithGoogleIdToken(getIdToken())
        } catch (e: GetCredentialCancellationException) {
            // The user closed the picker. Not an error.
        } catch (e: NoCredentialException) {
            // The picker only offers bu.edu accounts, and the device has none (or adding one wasn't finished).
            error = SignInError.NO_BU_ACCOUNT
        } catch (e: NotBuAccountException) {
            error = SignInError.NOT_BU_ACCOUNT
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = SignInError.FAILED
        } finally {
            progress.value = SignInProgress(isSigningIn = false, error = error)
        }
    }

    fun signOut() {
        progress.update { it.copy(error = null) }
        unblockFailed.value = false
        viewModelScope.launch { authRepository.signOut() }
    }

    // They drop off the list as soon as the phone applies it; offline it reaches the server later.
    fun unblock(blockedUid: String) {
        val uid = uiState.value.user?.uid ?: return
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
                ProfileViewModel(container.authRepository, container.groupRepository, container.googleWebClientId)
            }
        }
    }
}
