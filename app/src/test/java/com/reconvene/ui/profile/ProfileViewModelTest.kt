package com.reconvene.ui.profile

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.reconvene.data.auth.AuthRepository
import com.reconvene.data.auth.NotBuAccountException
import com.reconvene.data.auth.SignedInUser
import com.reconvene.data.model.PodMember
import com.reconvene.data.remote.FakeGroupRemoteDataSource
import com.reconvene.data.repository.OfflineFirstGroupRepository
import com.reconvene.testing.FakeGroupDao
import com.reconvene.testing.FakeMessageDao
import com.reconvene.testing.GroupTestPodDao
import com.reconvene.testing.MainDispatcherRule
import com.reconvene.testing.TestPodRepository
import com.reconvene.testing.testPod
import com.reconvene.data.settings.Appearance
import com.reconvene.data.settings.ReminderLead
import com.reconvene.data.settings.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val terrier = SignedInUser(uid = "u1", email = "terrier@bu.edu", displayName = "Terrier")
    private val auth = FakeAuthRepository()

    private val clock = Clock.fixed(Instant.parse("2026-11-24T15:00:00Z"), ZoneOffset.UTC)
    private val groupRemote = FakeGroupRemoteDataSource(clock, delayMillis = 0)
    private val messageDao = FakeMessageDao()
    private val groups =
        OfflineFirstGroupRepository(FakeGroupDao(messageDao), messageDao, GroupTestPodDao(), groupRemote, clock) { "new" }

    private val pods = TestPodRepository()
    private val settings = InMemorySettings()
    private val ben = PodMember("ben", "Ben")

    private fun viewModel() = ProfileViewModel(auth, pods, groups, settings, clock, googleWebClientId = "web-client")

    @Test
    fun successfulSignInShowsTheUser() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.signIn { "token" }

        assertEquals("token", auth.lastIdToken)
        assertEquals(terrier, viewModel.uiState.value.user)
        assertFalse(viewModel.uiState.value.isSigningIn)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun nonBuAccountShowsThatError() = runTest {
        auth.signInError = NotBuAccountException()
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.signIn { "token" }

        assertNull(viewModel.uiState.value.user)
        assertEquals(SignInError.NOT_BU_ACCOUNT, viewModel.uiState.value.error)
    }

    @Test
    fun closingThePickerIsNotAnError() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.signIn { throw GetCredentialCancellationException() }

        assertNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSigningIn)
    }

    @Test
    fun noBuAccountOnTheDeviceSaysToAddOne() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.signIn { throw NoCredentialException() }

        assertNull(viewModel.uiState.value.user)
        assertEquals(SignInError.NO_BU_ACCOUNT, viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSigningIn)
    }

    @Test
    fun otherFailureShowsGenericErrorThatClearsOnRetry() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.signIn { throw IOException("offline") }
        assertEquals(SignInError.FAILED, viewModel.uiState.value.error)

        viewModel.signIn { "token" }
        assertNull(viewModel.uiState.value.error)
        assertEquals(terrier, viewModel.uiState.value.user)
    }

    @Test
    fun signOutClearsTheUser() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        viewModel.signIn { "token" }

        viewModel.signOut()

        assertNull(viewModel.uiState.value.user)
    }

    @Test
    fun signedInShowsTheirSemesterNextPodAndBlockedCount() = runTest {
        val me = PodMember(terrier.uid, "Terrier")
        val now = clock.instant()
        val past = testPod(id = "past", startsAt = now.minusSeconds(24 * 3600), durationMinutes = 90).withMember(me)
        val next = testPod(id = "next", startsAt = now.plusSeconds(3600)).withMember(me)
        pods.cache(listOf(past, next, testPod(id = "theirs", startsAt = now.plusSeconds(600))))
        groups.block(terrier.uid, ben)
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.signIn { "token" }

        val state = viewModel.uiState.value
        assertEquals(1, state.record.pods)
        assertEquals("1.5", state.record.studied.hoursText())
        assertEquals("next", state.nextPod?.id)
        assertEquals(1, state.blockedCount)
    }

    @Test
    fun signedOutShowsNoRecord() = runTest {
        pods.cache(listOf(testPod(id = "next", startsAt = clock.instant().plusSeconds(3600)).withMember(PodMember(terrier.uid, "T"))))
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        assertEquals(NO_RECORD, viewModel.uiState.value.record)
        assertNull(viewModel.uiState.value.nextPod)
    }

    @Test
    fun choosingAnAppearanceSavesIt() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        assertEquals(Appearance.SYSTEM, viewModel.uiState.value.appearance)

        viewModel.setAppearance(Appearance.DARK)

        assertEquals(Appearance.DARK, viewModel.uiState.value.appearance)
    }

    private class InMemorySettings : SettingsRepository {
        override val appearance = MutableStateFlow(Appearance.SYSTEM)
        override val reminderLead = MutableStateFlow(ReminderLead.OFF)

        override suspend fun setAppearance(appearance: Appearance) {
            this.appearance.value = appearance
        }

        override suspend fun setReminderLead(lead: ReminderLead) {
            reminderLead.value = lead
        }
    }

    private inner class FakeAuthRepository : AuthRepository {
        private val user = MutableStateFlow<SignedInUser?>(null)
        var signInError: Exception? = null
        var lastIdToken: String? = null

        override val isAvailable = true
        override val currentUser: StateFlow<SignedInUser?> = user

        override suspend fun signInWithGoogleIdToken(idToken: String): SignedInUser {
            lastIdToken = idToken
            signInError?.let { throw it }
            user.value = terrier
            return terrier
        }

        override suspend fun signOut() {
            user.value = null
        }
    }
}
