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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SignInError { NOT_BU_ACCOUNT, NO_BU_ACCOUNT, FAILED }

data class ProfileUiState(
    val isAvailable: Boolean,
    val user: SignedInUser? = null,
    val isSigningIn: Boolean = false,
    val error: SignInError? = null,
)

class ProfileViewModel(
    private val authRepository: AuthRepository,
    // The OAuth web client the Google ID token is issued for, from local.properties.
    val googleWebClientId: String,
) : ViewModel() {

    private data class SignInProgress(val isSigningIn: Boolean = false, val error: SignInError? = null)

    private val progress = MutableStateFlow(SignInProgress())

    val uiState: StateFlow<ProfileUiState> =
        combine(authRepository.currentUser, progress) { user, progress ->
            ProfileUiState(
                isAvailable = authRepository.isAvailable,
                user = user,
                isSigningIn = progress.isSigningIn,
                error = progress.error,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ProfileUiState(isAvailable = authRepository.isAvailable),
        )

    // The screen passes in how to get a Google ID token, because the account picker needs an
    // Activity. It runs in the screen's scope, so leaving the screen cancels a pending picker.
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
        viewModelScope.launch { authRepository.signOut() }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ReconveneApplication).container
                ProfileViewModel(container.authRepository, container.googleWebClientId)
            }
        }
    }
}
