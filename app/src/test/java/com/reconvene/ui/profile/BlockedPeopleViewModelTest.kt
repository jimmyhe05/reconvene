package com.reconvene.ui.profile

import com.reconvene.data.model.PodMember
import com.reconvene.data.remote.FakeGroupRemoteDataSource
import com.reconvene.data.repository.OfflineFirstGroupRepository
import com.reconvene.testing.FakeGroupDao
import com.reconvene.testing.FakeMessageDao
import com.reconvene.testing.GroupTestPodDao
import com.reconvene.testing.MainDispatcherRule
import com.reconvene.testing.TERRIER
import com.reconvene.testing.TestAuthRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class BlockedPeopleViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-11-24T15:00:00Z"), ZoneOffset.UTC)
    private val messageDao = FakeMessageDao()
    private val groups = OfflineFirstGroupRepository(
        FakeGroupDao(messageDao),
        messageDao,
        GroupTestPodDao(),
        FakeGroupRemoteDataSource(clock, delayMillis = 0),
        clock,
    ) { "new" }
    private val auth = TestAuthRepository()

    private val ben = PodMember("ben", "Ben")
    private val ana = PodMember("ana", "Ana")

    @Test
    fun blockedPeopleShowByNameAndGoOnUnblock() = runTest {
        groups.block(TERRIER.uid, ben)
        groups.block(TERRIER.uid, ana)
        val viewModel = BlockedPeopleViewModel(auth, groups)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

        assertEquals(listOf(ana, ben), viewModel.uiState.value.blocked)

        viewModel.unblock(ben.uid)
        assertEquals(listOf(ana), viewModel.uiState.value.blocked)
        assertFalse(viewModel.uiState.value.unblockFailed)
    }

    @Test
    fun anotherAccountsBlocksDontShowAndSignOutClearsTheList() = runTest {
        groups.block("someone-else", ben)
        val viewModel = BlockedPeopleViewModel(auth, groups)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        assertTrue(viewModel.uiState.value.blocked.isEmpty())

        groups.block(TERRIER.uid, ben)
        assertEquals(listOf(ben), viewModel.uiState.value.blocked)

        auth.signOut()
        assertTrue(viewModel.uiState.value.blocked.isEmpty())
        assertFalse(viewModel.uiState.value.signedIn)
    }
}
