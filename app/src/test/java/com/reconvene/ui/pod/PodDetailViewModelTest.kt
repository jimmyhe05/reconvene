package com.reconvene.ui.pod

import com.reconvene.data.local.toEntity
import com.reconvene.data.model.Group
import com.reconvene.data.model.Pod
import com.reconvene.data.model.PodActionException
import com.reconvene.data.model.PodActionProblem
import com.reconvene.data.model.PodMember
import com.reconvene.data.remote.FakeGroupRemoteDataSource
import com.reconvene.data.remote.GroupRemoteDataSource
import com.reconvene.data.repository.OfflineFirstGroupRepository
import com.reconvene.testing.FakeGroupDao
import com.reconvene.testing.FakeMessageDao
import com.reconvene.testing.GroupTestPodDao
import com.reconvene.testing.MainDispatcherRule
import com.reconvene.testing.TERRIER
import com.reconvene.testing.TestAuthRepository
import com.reconvene.testing.TestPodRepository
import com.reconvene.testing.testPod
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class PodDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val zone = ZoneId.of("America/New_York")
    private val now = ZonedDateTime.of(2026, 11, 16, 14, 0, 0, 0, zone).toInstant()
    private val clock = Clock.fixed(now, zone)
    private val repository = TestPodRepository()
    private val auth = TestAuthRepository()
    private val pod = testPod(id = "a1", startsAt = now.plus(Duration.ofMinutes(30)))

    // Groups read the pod from Room, so a pod the host keeps is stored there too.
    private val groupPodDao = GroupTestPodDao()
    private val messageDao = FakeMessageDao()
    private val groupRemote = FailingCreateRemote(FakeGroupRemoteDataSource(clock, delayMillis = 300))
    private val groupRepository =
        OfflineFirstGroupRepository(FakeGroupDao(messageDao), messageDao, groupPodDao, groupRemote, clock) { "kept" }

    // Terrier hosts it, Maya joined, and it started 10 minutes ago.
    private val running = pod.copy(
        startsAt = now.minus(Duration.ofMinutes(10)),
        endsAt = now.plus(Duration.ofMinutes(50)),
        hostUid = TERRIER.uid,
        hostName = "Terrier",
        members = listOf(PodMember(TERRIER.uid, "Terrier"), PodMember("maya", "Maya")),
    )

    private fun viewModel() = PodDetailViewModel("a1", repository, groupRepository, auth, clock)

    private suspend fun store(pod: Pod) {
        repository.cache(listOf(pod))
        groupPodDao.upsertAll(listOf(pod.toEntity()))
    }

    private fun PodDetailViewModel.keep() = (uiState.value as PodDetailUiState.Content).keep

    private fun TestScope.keepOptionFor(pod: Pod): KeepOption {
        repository.cache(listOf(pod))
        val viewModel = viewModel()
        watch(viewModel)
        return viewModel.keep()
    }

    // uiState only updates while something collects it, as the screen does.
    private fun TestScope.watch(viewModel: PodDetailViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    @Test
    fun storedPodShowsAtOnceWhileTheRefreshRuns() = runTest {
        repository.cache(listOf(pod))
        val viewModel = viewModel()
        watch(viewModel)

        assertEquals(PodDetailUiState.Content(pod, now, TERRIER.uid), viewModel.uiState.value)
    }

    @Test
    fun missingPodLoadsUntilTheRefreshThenIsGone() = runTest {
        val viewModel = viewModel()
        watch(viewModel)
        assertEquals(PodDetailUiState.Loading, viewModel.uiState.value)

        repository.finishRefresh(emptyList())

        assertEquals(PodDetailUiState.Gone(), viewModel.uiState.value)
    }

    @Test
    fun failedRefreshWithoutAStoredPodCanBeRetried() = runTest {
        val viewModel = viewModel()
        watch(viewModel)

        repository.failRefresh()
        assertEquals(PodDetailUiState.Error, viewModel.uiState.value)

        viewModel.retry()
        assertEquals(PodDetailUiState.Loading, viewModel.uiState.value)
        repository.finishRefresh(listOf(pod))
        assertEquals(PodDetailUiState.Content(pod, now, TERRIER.uid), viewModel.uiState.value)
    }

    @Test
    fun cancelingYourPodShowsThatYouCanceledIt() = runTest {
        repository.cache(listOf(pod.copy(hostUid = TERRIER.uid)))
        val viewModel = viewModel()
        watch(viewModel)

        viewModel.cancel()

        assertEquals(PodDetailUiState.Gone(canceledByYou = true), viewModel.uiState.value)
        assertEquals(PodMessage.CANCELED, viewModel.message.value)
    }

    @Test
    fun podCanceledBySomeoneElseIsGone() = runTest {
        repository.cache(listOf(pod.copy(canceled = true)))
        val viewModel = viewModel()
        watch(viewModel)

        assertEquals(PodDetailUiState.Gone(canceledByYou = false), viewModel.uiState.value)
    }

    @Test
    fun joinIsBusyUntilTheServerAnswers() = runTest {
        repository.cache(listOf(pod))
        val viewModel = viewModel()
        watch(viewModel)
        val answer = CompletableDeferred<Unit>()
        repository.joinAnswer = answer

        viewModel.join()
        assertEquals(true, (viewModel.uiState.value as PodDetailUiState.Content).isBusy)

        answer.complete(Unit)
        assertEquals(false, (viewModel.uiState.value as PodDetailUiState.Content).isBusy)
        assertEquals(listOf("a1" to PodMember(TERRIER.uid, "Terrier")), repository.joins)
    }

    @Test
    fun refusedJoinShowsWhy() = runTest {
        repository.cache(listOf(pod))
        val viewModel = viewModel()
        watch(viewModel)
        repository.joinError = PodActionException(PodActionProblem.FULL)

        viewModel.join()

        assertEquals(PodMessage.FULL, viewModel.message.value)
    }

    @Test
    fun theHostCanKeepARunningPodWithSomeoneToInvite() = runTest {
        assertEquals(KeepOption.AVAILABLE, keepOptionFor(running))
    }

    @Test
    fun keepIsHiddenBeforeTheStartForGuestsAndWhenTheHostIsAlone() = runTest {
        assertEquals(KeepOption.HIDDEN, keepOptionFor(running.copy(startsAt = now.plus(Duration.ofMinutes(5)))))
        assertEquals(KeepOption.HIDDEN, keepOptionFor(running.copy(hostUid = "maya", hostName = "Maya")))
        assertEquals(KeepOption.HIDDEN, keepOptionFor(running.copy(members = running.members.take(1))))
    }

    @Test
    fun keepingCreatesTheGroupAndHidesTheOption() = runTest {
        store(running)
        val viewModel = viewModel()
        watch(viewModel)

        viewModel.keep("CS 501 crew")
        assertEquals(KeepOption.KEEPING, viewModel.keep())

        advanceUntilIdle()
        assertEquals(KeepOption.HIDDEN, viewModel.keep())
        assertEquals(PodMessage.GROUP_KEPT, viewModel.message.value)
        val group: Group = groupRepository.observeGroups().first().single()
        assertEquals("CS 501 crew", group.name)
        assertEquals("a1", group.fromPodId)
        assertEquals(listOf("maya"), group.invited.map { it.uid })
    }

    @Test
    fun aFailedKeepSaysSoAndCanBeTriedAgain() = runTest {
        store(running)
        groupRemote.failCreate = true
        val viewModel = viewModel()
        watch(viewModel)

        viewModel.keep("CS 501 crew")
        advanceUntilIdle()

        assertEquals(PodMessage.FAILED, viewModel.message.value)
        assertEquals(KeepOption.AVAILABLE, viewModel.keep())
    }
}

// The fake group server, plus a switch to make creating a group fail the way an offline write would.
private class FailingCreateRemote(private val inner: GroupRemoteDataSource) : GroupRemoteDataSource by inner {
    var failCreate = false

    override suspend fun createGroup(group: Group) {
        if (failCreate) throw IOException("No connection")
        inner.createGroup(group)
    }
}
