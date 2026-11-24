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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    private val ben = PodMember("ben", "Ben")
    private val ana = PodMember("ana", "Ana")

    private fun viewModel() = ProfileViewModel(auth, groups, googleWebClientId = "web-client")

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
    fun blockedPeopleShowByNameAndGoOnUnblock() = runTest {
        groups.block(terrier.uid, ben)
        groups.block(terrier.uid, ana)
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        assertTrue(viewModel.uiState.value.blocked.isEmpty())

        viewModel.signIn { "token" }
        assertEquals(listOf(ana, ben), viewModel.uiState.value.blocked)

        viewModel.unblock(ben.uid)
        assertEquals(listOf(ana), viewModel.uiState.value.blocked)
        assertFalse(viewModel.uiState.value.unblockFailed)
    }

    @Test
    fun anotherAccountsBlocksDontShow() = runTest {
        groups.block("someone-else", ben)
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        viewModel.signIn { "token" }
        assertTrue(viewModel.uiState.value.blocked.isEmpty())

        groups.block(terrier.uid, ben)
        assertEquals(listOf(ben), viewModel.uiState.value.blocked)

        viewModel.signOut()
        assertTrue(viewModel.uiState.value.blocked.isEmpty())
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
