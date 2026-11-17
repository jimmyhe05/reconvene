package com.reconvene.ui.groups

import com.reconvene.data.auth.LOCAL_USER
import com.reconvene.data.auth.SignedInUser
import com.reconvene.data.model.Group
import com.reconvene.data.model.GroupActionException
import com.reconvene.data.model.GroupProblem
import com.reconvene.data.remote.FakeGroupRemoteDataSource
import com.reconvene.data.remote.GroupRemoteDataSource
import com.reconvene.data.repository.OfflineFirstGroupRepository
import com.reconvene.testing.FakeGroupDao
import com.reconvene.testing.FakeMessageDao
import com.reconvene.testing.GroupTestPodDao
import com.reconvene.testing.MainDispatcherRule
import com.reconvene.testing.TERRIER
import com.reconvene.testing.TestAuthRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.ZoneId
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class GroupsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val zone = ZoneId.of("America/New_York")
    private val clock = Clock.fixed(ZonedDateTime.of(2026, 11, 17, 10, 0, 0, 0, zone).toInstant(), zone)
    private val remote = ControlledRemote(FakeGroupRemoteDataSource(clock, delayMillis = 0))
    private val messageDao = FakeMessageDao()
    private val repository =
        OfflineFirstGroupRepository(FakeGroupDao(messageDao), messageDao, GroupTestPodDao(), remote, clock) { "new" }

    // The sample groups are built around LOCAL_USER: in g1 and g3, invited to g2.
    private fun viewModel(user: SignedInUser? = LOCAL_USER) = GroupsViewModel(repository, TestAuthRepository(user))

    // uiState only updates while something collects it, as the screen does.
    private fun TestScope.watch(viewModel: GroupsViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    private fun GroupsViewModel.content() = uiState.value as GroupsUiState.Content

    private fun names(groups: List<Group>) = groups.map { it.name }

    @Test
    fun signedOutAsksToSignIn() = runTest {
        val viewModel = viewModel(user = null)
        watch(viewModel)

        assertEquals(GroupsUiState.SignedOut, viewModel.uiState.value)
    }

    @Test
    fun syncedGroupsSplitIntoInvitesAndYourGroups() = runTest {
        val viewModel = viewModel()
        watch(viewModel)

        val content = viewModel.content()
        assertEquals(listOf("Signals study group"), names(content.invites))
        assertEquals(listOf("Algorithms study group", "Deep work crew"), names(content.groups))
        assertEquals(LOCAL_USER.uid, content.viewerUid)
    }

    @Test
    fun noGroupsLoadsUntilTheServerAnswersThenIsEmpty() = runTest {
        val gate = CompletableDeferred<Unit>()
        remote.gate = gate
        val viewModel = viewModel(user = TERRIER)
        watch(viewModel)
        assertEquals(GroupsUiState.Loading, viewModel.uiState.value)

        gate.complete(Unit)
        runCurrent()

        assertEquals(GroupsUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun failedSyncWithNothingStoredShowsErrorUntilRetried() = runTest {
        remote.failSync = true
        val viewModel = viewModel()
        watch(viewModel)
        assertEquals(GroupsUiState.Error, viewModel.uiState.value)

        remote.failSync = false
        viewModel.retry()
        runCurrent()

        assertEquals(listOf("Signals study group"), names(viewModel.content().invites))
    }

    @Test
    fun acceptingMovesTheInviteIntoYourGroups() = runTest {
        val viewModel = viewModel()
        watch(viewModel)

        viewModel.accept("g2")
        runCurrent()

        val content = viewModel.content()
        assertEquals(emptyList<String>(), names(content.invites))
        assertEquals(listOf("Algorithms study group", "Deep work crew", "Signals study group"), names(content.groups))
        assertEquals(GroupNotice.JOINED, viewModel.message.value)
    }

    @Test
    fun decliningDropsTheInvite() = runTest {
        val viewModel = viewModel()
        watch(viewModel)

        viewModel.decline("g2")
        runCurrent()

        val content = viewModel.content()
        assertEquals(emptyList<String>(), names(content.invites))
        assertEquals(listOf("Algorithms study group", "Deep work crew"), names(content.groups))
        assertEquals(GroupNotice.DECLINED, viewModel.message.value)
    }

    @Test
    fun aRefusedAnswerSaysWhyAndKeepsTheInvite() = runTest {
        remote.refuseAnswers = GroupProblem.DISBANDED
        val viewModel = viewModel()
        watch(viewModel)

        viewModel.accept("g2")
        runCurrent()

        assertEquals(GroupNotice.INVITE_GONE, viewModel.message.value)
        assertEquals(listOf("Signals study group"), names(viewModel.content().invites))
        assertEquals(emptySet<String>(), viewModel.content().busyGroupIds)

        viewModel.messageShown()
        assertEquals(null, viewModel.message.value)
    }
}

// The fake server, plus switches to hold back its first answer, fail the sync, or refuse an answer.
private class ControlledRemote(private val inner: GroupRemoteDataSource) : GroupRemoteDataSource by inner {
    var gate: CompletableDeferred<Unit>? = null
    var failSync = false
    var refuseAnswers: GroupProblem? = null

    override fun observeGroups(uid: String): Flow<List<Group>> = flow {
        gate?.await()
        if (failSync) throw IOException("No connection")
        emitAll(inner.observeGroups(uid))
    }

    override suspend fun answerInvite(group: Group, uid: String, accept: Boolean): Group {
        refuseAnswers?.let { throw GroupActionException(it) }
        return inner.answerInvite(group, uid, accept)
    }
}
