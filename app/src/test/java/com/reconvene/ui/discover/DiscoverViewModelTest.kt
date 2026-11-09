package com.reconvene.ui.discover

import com.reconvene.data.model.Pod
import com.reconvene.data.model.PodActionException
import com.reconvene.data.model.PodActionProblem
import com.reconvene.data.model.PodMember
import com.reconvene.data.repository.PodRepository
import com.reconvene.testing.MainDispatcherRule
import com.reconvene.testing.TERRIER
import com.reconvene.testing.TestAuthRepository
import com.reconvene.testing.testPod
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class DiscoverViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val zone = ZoneId.of("America/New_York")
    private val now = ZonedDateTime.of(2026, 10, 23, 14, 0, 0, 0, zone).toInstant()
    private val clock = Clock.fixed(now, zone)
    private val repository = TestPodRepository()
    private val auth = TestAuthRepository()

    private fun minutesFromNow(minutes: Long) = now.plus(Duration.ofMinutes(minutes))

    @Test
    fun startsInLoadingUntilRefreshFinishes() {
        val viewModel = DiscoverViewModel(repository, auth, clock)

        assertEquals(DiscoverUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun showsCachedPodsWhileRefreshingAndAfterItFails() = runTest {
        repository.cache(listOf(testPod(startsAt = minutesFromNow(30))))
        val viewModel = DiscoverViewModel(repository, auth, clock)
        assertTrue(viewModel.uiState.value is DiscoverUiState.Content)

        repository.failRefresh()
        assertTrue(viewModel.uiState.value is DiscoverUiState.Content)
    }

    @Test
    fun groupsPodsIntoSectionsInStartOrder() = runTest {
        val viewModel = DiscoverViewModel(repository, auth, clock)

        repository.finishRefresh(
            listOf(
                testPod(id = "monday", startsAt = ZonedDateTime.of(2026, 10, 26, 9, 0, 0, 0, zone).toInstant()),
                testPod(id = "tonight", startsAt = minutesFromNow(5 * 60)),
                testPod(id = "ongoing", startsAt = minutesFromNow(-30)),
                testPod(id = "tomorrow", startsAt = ZonedDateTime.of(2026, 10, 24, 10, 0, 0, 0, zone).toInstant()),
                testPod(id = "soon", startsAt = minutesFromNow(45)),
            )
        )

        val content = viewModel.uiState.value as DiscoverUiState.Content
        assertEquals(
            listOf(
                SectionHeading.NowAndSoon to listOf("ongoing", "soon"),
                SectionHeading.LaterToday to listOf("tonight"),
                SectionHeading.Tomorrow to listOf("tomorrow"),
                SectionHeading.Day(LocalDate.of(2026, 10, 26)) to listOf("monday"),
            ),
            content.sections.map { section -> section.heading to section.pods.map { it.id } },
        )
    }

    @Test
    fun showsEmptyWhenOnlyEndedOrCanceledPods() = runTest {
        val viewModel = DiscoverViewModel(repository, auth, clock)

        repository.finishRefresh(
            listOf(
                testPod(id = "ended", startsAt = minutesFromNow(-120), durationMinutes = 60),
                testPod(id = "canceled", startsAt = minutesFromNow(60), canceled = true),
            )
        )

        assertEquals(DiscoverUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun showsErrorWhenRefreshFailsWithNothingCachedAndRecoversOnRetry() = runTest {
        repository.failRefresh()
        val viewModel = DiscoverViewModel(repository, auth, clock)
        assertEquals(DiscoverUiState.Error, viewModel.uiState.value)

        viewModel.retry()
        assertEquals(DiscoverUiState.Loading, viewModel.uiState.value)

        repository.finishRefresh(listOf(testPod(startsAt = minutesFromNow(30))))
        assertTrue(viewModel.uiState.value is DiscoverUiState.Content)
    }

    @Test
    fun signedOutUserIsAskedToSignInAndPodsLoadAfterSigningIn() = runTest {
        auth.currentUser.value = null
        val viewModel = DiscoverViewModel(repository, auth, clock)
        assertEquals(DiscoverUiState.SignedOut, viewModel.uiState.value)

        auth.currentUser.value = TERRIER
        assertEquals(DiscoverUiState.Loading, viewModel.uiState.value)

        repository.finishRefresh(listOf(testPod(startsAt = minutesFromNow(30))))
        assertEquals(TERRIER.uid, (viewModel.uiState.value as DiscoverUiState.Content).viewerUid)
    }

    @Test
    fun joinMarksThePodBusyUntilTheServerAnswers() = runTest {
        repository.cache(listOf(testPod(id = "a1", startsAt = minutesFromNow(30))))
        val viewModel = DiscoverViewModel(repository, auth, clock)
        val answer = CompletableDeferred<Unit>()
        repository.joinAnswer = answer

        viewModel.join("a1")
        assertEquals(setOf("a1"), (viewModel.uiState.value as DiscoverUiState.Content).busyPodIds)

        answer.complete(Unit)
        assertEquals(emptySet<String>(), (viewModel.uiState.value as DiscoverUiState.Content).busyPodIds)
        assertEquals(listOf("a1" to PodMember(TERRIER.uid, "Terrier")), repository.joins)
        assertNull(viewModel.message.value)
    }

    @Test
    fun refusedOrFailedJoinShowsAMessageOnce() = runTest {
        repository.cache(listOf(testPod(id = "a1", startsAt = minutesFromNow(30))))
        val viewModel = DiscoverViewModel(repository, auth, clock)

        repository.joinError = PodActionException(PodActionProblem.FULL)
        viewModel.join("a1")
        assertEquals(PodMessage.FULL, viewModel.message.value)
        viewModel.messageShown()
        assertNull(viewModel.message.value)

        repository.joinError = IOException("Network unavailable")
        viewModel.join("a1")
        assertEquals(PodMessage.FAILED, viewModel.message.value)
    }

    @Test
    fun canceledPodLeavesTheListWithAConfirmation() = runTest {
        val viewModel = DiscoverViewModel(repository, auth, clock)
        repository.finishRefresh(listOf(testPod(id = "a1", startsAt = minutesFromNow(30))))

        viewModel.cancel("a1")

        assertEquals(PodMessage.CANCELED, viewModel.message.value)
        assertEquals(DiscoverUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun failedCancelKeepsThePodAndSaysWhy() = runTest {
        repository.cache(listOf(testPod(id = "a1", startsAt = minutesFromNow(30))))
        val viewModel = DiscoverViewModel(repository, auth, clock)
        repository.cancelError = IOException("Network unavailable")

        viewModel.cancel("a1")

        assertEquals(PodMessage.FAILED, viewModel.message.value)
        assertTrue(viewModel.uiState.value is DiscoverUiState.Content)
    }
}

private class TestPodRepository : PodRepository {
    private val cachedPods = MutableStateFlow<List<Pod>>(emptyList())
    private val refreshResults = Channel<Result<List<Pod>>>(Channel.UNLIMITED)

    fun cache(pods: List<Pod>) {
        cachedPods.value = pods
    }

    suspend fun finishRefresh(pods: List<Pod>) = refreshResults.send(Result.success(pods))

    suspend fun failRefresh() = refreshResults.send(Result.failure(IOException("Network unavailable")))

    override fun observePods(): Flow<List<Pod>> = cachedPods

    override suspend fun refresh() {
        cachedPods.value = refreshResults.receive().getOrThrow()
    }

    override suspend fun createPod(pod: Pod) {
        cachedPods.value += pod
    }

    val joins = mutableListOf<Pair<String, PodMember>>()
    var joinAnswer: CompletableDeferred<Unit>? = null
    var joinError: Exception? = null

    override suspend fun joinPod(podId: String, member: PodMember) {
        joinAnswer?.await()
        joinError?.let { throw it }
        joins += podId to member
    }

    override suspend fun leavePod(podId: String, uid: String) = Unit

    var cancelError: Exception? = null

    override suspend fun cancelPod(podId: String, uid: String) {
        cancelError?.let { throw it }
        cachedPods.value = cachedPods.value.map { if (it.id == podId) it.copy(canceled = true) else it }
    }
}
