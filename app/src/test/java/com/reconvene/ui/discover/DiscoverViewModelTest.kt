package com.reconvene.ui.discover

import com.reconvene.data.model.Pod
import com.reconvene.data.repository.PodRepository
import com.reconvene.testing.MainDispatcherRule
import com.reconvene.testing.testPod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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

    private fun minutesFromNow(minutes: Long) = now.plus(Duration.ofMinutes(minutes))

    @Test
    fun startsInLoadingUntilPodsArrive() {
        val viewModel = DiscoverViewModel(repository, clock)

        assertEquals(DiscoverUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun groupsPodsIntoSectionsInStartOrder() = runTest {
        val viewModel = DiscoverViewModel(repository, clock)

        repository.emit(
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
        val viewModel = DiscoverViewModel(repository, clock)

        repository.emit(
            listOf(
                testPod(id = "ended", startsAt = minutesFromNow(-120), durationMinutes = 60),
                testPod(id = "canceled", startsAt = minutesFromNow(60), canceled = true),
            )
        )

        assertEquals(DiscoverUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun showsErrorAndRecoversOnRetry() = runTest {
        repository.shouldFail = true
        val viewModel = DiscoverViewModel(repository, clock)
        assertEquals(DiscoverUiState.Error, viewModel.uiState.value)

        repository.shouldFail = false
        viewModel.retry()
        assertEquals(DiscoverUiState.Loading, viewModel.uiState.value)

        repository.emit(listOf(testPod(startsAt = minutesFromNow(30))))
        assertTrue(viewModel.uiState.value is DiscoverUiState.Content)
    }
}

private class TestPodRepository : PodRepository {
    private val pods = MutableSharedFlow<List<Pod>>()
    var shouldFail = false

    suspend fun emit(value: List<Pod>) = pods.emit(value)

    override fun observePods(): Flow<List<Pod>> = flow {
        if (shouldFail) throw IOException("Network unavailable")
        emitAll(pods)
    }
}
