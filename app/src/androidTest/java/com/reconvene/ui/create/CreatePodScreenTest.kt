package com.reconvene.ui.create

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reconvene.R
import com.reconvene.data.auth.UnavailableAuthRepository
import com.reconvene.data.model.Pod
import com.reconvene.data.model.PodMember
import com.reconvene.data.repository.PodRepository
import com.reconvene.ui.theme.ReconveneTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@RunWith(AndroidJUnit4::class)
class CreatePodScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val zone = ZoneId.of("America/New_York")
    private val clock = Clock.fixed(ZonedDateTime.of(2026, 11, 10, 14, 0, 0, 0, zone).toInstant(), zone)
    private val repository = RecordingPodRepository()
    private val viewModel = CreatePodViewModel(SavedStateHandle(), repository, clock, UnavailableAuthRepository)
    private var created = false

    private fun string(@StringRes id: Int, vararg args: Any): String =
        ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

    @Test
    fun blankFormShowsErrorsAndFilledFormCreatesAPod() {
        composeRule.setContent {
            ReconveneTheme {
                CreatePodScreen(onBack = {}, onCreated = { created = true }, viewModel = viewModel)
            }
        }

        composeRule.onNodeWithText(string(R.string.action_create_pod)).performClick()
        composeRule.onNodeWithText(string(R.string.error_building_missing)).performScrollTo().assertIsDisplayed()
        assertTrue(repository.created.isEmpty())

        composeRule.onNodeWithText(string(R.string.create_topic)).performScrollTo().performTextInput("Algorithms exam review")
        composeRule.onNodeWithText(string(R.string.create_building)).performScrollTo().performClick()
        composeRule.onNodeWithText("Mugar Library").performClick()
        composeRule.onNodeWithText(string(R.string.create_meeting_point)).performScrollTo().performTextInput("2nd floor")
        composeRule.onNodeWithText(string(R.string.action_create_pod)).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { created }
        val pod = repository.created.single()
        assertEquals("Algorithms exam review", pod.title)
        assertEquals("Mugar Library", pod.building)
        assertEquals("You", pod.hostName)
    }

    @Test
    fun tappingASuggestedSpotFillsTheMeetingPoint() {
        composeRule.setContent {
            ReconveneTheme {
                CreatePodScreen(onBack = {}, onCreated = { created = true }, viewModel = viewModel)
            }
        }

        composeRule.onNodeWithText(string(R.string.create_topic)).performScrollTo().performTextInput("Data structures review")
        composeRule.onNodeWithText(string(R.string.create_building)).performScrollTo().performClick()
        composeRule.onNodeWithText("GSU").performClick()
        composeRule.onNodeWithText("Food court tables").performScrollTo().performClick()
        composeRule.onNodeWithText(string(R.string.action_create_pod)).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { created }
        val pod = repository.created.single()
        assertEquals("GSU", pod.building)
        assertEquals("Food court tables", pod.meetingPoint)
    }

    @Test
    fun pickingAStartSlotAndALengthSetsThePodTimes() {
        composeRule.setContent {
            ReconveneTheme {
                CreatePodScreen(onBack = {}, onCreated = { created = true }, viewModel = viewModel)
            }
        }
        val threeOClock = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(LocalTime.of(15, 0))

        composeRule.onNodeWithText(string(R.string.create_topic)).performScrollTo().performTextInput("Operating systems lab")
        composeRule.onNodeWithText(string(R.string.create_building)).performScrollTo().performClick()
        composeRule.onNodeWithText("CDS").performClick()
        composeRule.onNodeWithText(string(R.string.create_meeting_point)).performScrollTo().performTextInput("Lobby")
        composeRule.onNodeWithText(threeOClock).performScrollTo().performClick()
        composeRule.onNodeWithText(string(R.string.duration_hours, 2)).performScrollTo().performClick()
        composeRule.onNodeWithText(string(R.string.action_create_pod)).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { created }
        val pod = repository.created.single()
        assertEquals(ZonedDateTime.of(2026, 11, 10, 15, 0, 0, 0, zone).toInstant(), pod.startsAt)
        assertEquals(ZonedDateTime.of(2026, 11, 10, 17, 0, 0, 0, zone).toInstant(), pod.endsAt)
    }

    @Test
    fun pickingADateFromTheCalendarSetsThePodDay() {
        composeRule.setContent {
            ReconveneTheme {
                CreatePodScreen(onBack = {}, onCreated = { created = true }, viewModel = viewModel)
            }
        }
        val friday = LocalDate.of(2026, 11, 13)
        // How the calendar names each of its day cells.
        val calendarDay = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy").format(friday)

        composeRule.onNodeWithText(string(R.string.create_topic)).performScrollTo().performTextInput("Networks problem set")
        composeRule.onNodeWithText(string(R.string.create_building)).performScrollTo().performClick()
        composeRule.onNodeWithText("CDS").performClick()
        composeRule.onNodeWithText(string(R.string.create_meeting_point)).performScrollTo().performTextInput("Lobby")
        composeRule.onNodeWithText(string(R.string.create_pick_date)).performScrollTo().performClick()
        composeRule.onNode(hasText(calendarDay, substring = true) or hasContentDescription(calendarDay, substring = true))
            .performClick()
        composeRule.onNodeWithText(string(R.string.action_ok)).performClick()
        composeRule.onNodeWithText(DateTimeFormatter.ofPattern("EEE, MMM d").format(friday)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.action_create_pod)).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { created }
        val pod = repository.created.single()
        assertEquals(ZonedDateTime.of(2026, 11, 13, 14, 30, 0, 0, zone).toInstant(), pod.startsAt)
        assertEquals(ZonedDateTime.of(2026, 11, 13, 16, 0, 0, 0, zone).toInstant(), pod.endsAt)
    }

    @Test
    fun previewCardShowsWhatWasTyped() {
        composeRule.setContent {
            ReconveneTheme {
                CreatePodScreen(onBack = {}, onCreated = { created = true }, viewModel = viewModel)
            }
        }

        composeRule.onNodeWithText(string(R.string.create_preview_topic), substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.create_topic)).performScrollTo().performTextInput("Compilers midterm")
        composeRule.onNodeWithText(string(R.string.create_building)).performScrollTo().performClick()
        composeRule.onNodeWithText("GSU").performClick()
        composeRule.onNodeWithText("Food court tables").performScrollTo().performClick()

        // Once in the topic field and once on the preview card.
        composeRule.onAllNodesWithText("Compilers midterm").assertCountEquals(2)
        composeRule.onNodeWithText(string(R.string.pod_place, "GSU", "Food court tables"), substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun otherBuildingTakesATypedName() {
        composeRule.setContent {
            ReconveneTheme {
                CreatePodScreen(onBack = {}, onCreated = { created = true }, viewModel = viewModel)
            }
        }

        composeRule.onNodeWithText(string(R.string.create_topic)).performScrollTo().performTextInput("Figure drawing practice")
        composeRule.onNodeWithText(string(R.string.create_building)).performScrollTo().performClick()
        composeRule.onNodeWithText(string(R.string.create_building_other)).performScrollTo().performClick()
        composeRule.onNodeWithText(string(R.string.create_building_name)).performScrollTo().performTextInput("College of Fine Arts")
        composeRule.onNodeWithText(string(R.string.create_meeting_point)).performScrollTo().performTextInput("Lobby")
        composeRule.onNodeWithText(string(R.string.action_create_pod)).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { created }
        assertEquals("College of Fine Arts", repository.created.single().building)
    }
}

private class RecordingPodRepository : PodRepository {
    val created = mutableListOf<Pod>()

    override fun observePods(): Flow<List<Pod>> = flowOf(created)

    override suspend fun refresh() = Unit

    override suspend fun createPod(pod: Pod) {
        created += pod
    }

    override suspend fun joinPod(podId: String, member: PodMember) = Unit

    override suspend fun leavePod(podId: String, uid: String) = Unit

    override suspend fun cancelPod(podId: String, uid: String) = Unit
}
