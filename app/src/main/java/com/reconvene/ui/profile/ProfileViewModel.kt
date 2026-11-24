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
import com.reconvene.data.model.Pod
import com.reconvene.data.repository.GroupRepository
import com.reconvene.data.repository.PodRepository
import com.reconvene.data.settings.Appearance
import com.reconvene.data.settings.ReminderLead
import com.reconvene.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant

enum class SignInError { NOT_BU_ACCOUNT, NO_BU_ACCOUNT, FAILED }

data class ProfileUiState(
    val isAvailable: Boolean,
    val user: SignedInUser? = null,
    val isSigningIn: Boolean = false,
    val error: SignInError? = null,
    val record: SemesterRecord = NO_RECORD,
    // The pod the user is in that's running or starts soonest.
    val nextPod: Pod? = null,
    val blockedCount: Int = 0,
    val appearance: Appearance = Appearance.SYSTEM,
    val reminderLead: ReminderLead = ReminderLead.OFF,
    val now: Instant = Instant.EPOCH,
)

val NO_RECORD = SemesterRecord(pods = 0, hosted = 0, studied = Duration.ZERO, groups = 0)

class ProfileViewModel(
    private val authRepository: AuthRepository,
    private val podRepository: PodRepository,
    private val groupRepository: GroupRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
    // The OAuth web client the Google ID token is issued for, from local.properties.
    val googleWebClientId: String,
) : ViewModel() {

    private data class SignInProgress(val isSigningIn: Boolean = false, val error: SignInError? = null)

    private data class Activity(val record: SemesterRecord, val nextPod: Pod?, val blockedCount: Int, val now: Instant)

    private val progress = MutableStateFlow(SignInProgress())

    // From what's stored on the phone, for whoever is signed in. A list that can't be read counts as
    // empty rather than hiding the page.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val activity: Flow<Activity?> = authRepository.currentUser.flatMapLatest { user ->
        if (user == null) {
            flowOf(null)
        } else {
            combine(
                podRepository.observePods(),
                groupRepository.observeGroups().catch { emit(emptyList()) },
                groupRepository.observeBlocked(user.uid).catch { emit(emptySet()) },
            ) { pods, groups, blocked ->
                val now = clock.instant()
                Activity(semesterRecord(pods, groups, user.uid, now), nextPod(pods, user.uid, now), blocked.size, now)
            }
        }
    }

    private val preferences = combine(settingsRepository.appearance, settingsRepository.reminderLead) { appearance, lead ->
        appearance to lead
    }

    // current user + login progress + their activity + preferences = page state
    val uiState: StateFlow<ProfileUiState> =
        combine(authRepository.currentUser, progress, activity, preferences) { user, progress, activity, (appearance, lead) ->
            ProfileUiState(
                isAvailable = authRepository.isAvailable,
                user = user,
                isSigningIn = progress.isSigningIn,
                error = progress.error,
                record = activity?.record ?: NO_RECORD,
                nextPod = activity?.nextPod,
                blockedCount = activity?.blockedCount ?: 0,
                appearance = appearance,
                reminderLead = lead,
                now = activity?.now ?: clock.instant(),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ProfileUiState(isAvailable = authRepository.isAvailable),
        )

    init {
        // Brings in the user's past pods, so the record is right on a new phone, and their groups, so
        // the count is right before the Groups tab is opened. Failures leave what's stored.
        viewModelScope.launch {
            authRepository.currentUser.map { it?.uid }.distinctUntilChanged().collectLatest { uid ->
                if (uid != null) {
                    coroutineScope {
                        launch { quietly { podRepository.refreshMine(uid) } }
                        quietly { groupRepository.syncGroups(uid) }
                    }
                }
            }
        }
    }

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
        viewModelScope.launch { authRepository.signOut() }
    }

    fun setAppearance(appearance: Appearance) {
        viewModelScope.launch { settingsRepository.setAppearance(appearance) }
    }

    // The screen asks for permission to notify first; this only saves the choice.
    fun setReminderLead(lead: ReminderLead) {
        viewModelScope.launch { settingsRepository.setReminderLead(lead) }
    }

    private suspend fun quietly(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Offline or refused: the page shows what's stored.
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as ReconveneApplication).container
                ProfileViewModel(
                    authRepository = container.authRepository,
                    podRepository = container.podRepository,
                    groupRepository = container.groupRepository,
                    settingsRepository = container.settingsRepository,
                    clock = container.clock,
                    googleWebClientId = container.googleWebClientId,
                )
            }
        }
    }
}
