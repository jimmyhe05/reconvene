package com.reconvene.ui.pod

import com.reconvene.data.model.PodActionException
import com.reconvene.data.model.PodActionProblem
import com.reconvene.data.model.PodMember
import com.reconvene.testing.MainDispatcherRule
import com.reconvene.testing.TERRIER
import com.reconvene.testing.TestAuthRepository
import com.reconvene.testing.TestPodRepository
import com.reconvene.testing.testPod
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
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

    private fun viewModel() = PodDetailViewModel("a1", repository, auth, clock)

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
}
