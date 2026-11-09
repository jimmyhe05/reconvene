package com.reconvene.ui.create

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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
import java.time.ZoneId
import java.time.ZonedDateTime

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

        composeRule.onNodeWithText(string(R.string.action_create_pod)).performScrollTo().performClick()
        composeRule.onNodeWithText(string(R.string.error_building_missing)).performScrollTo().assertIsDisplayed()
        assertTrue(repository.created.isEmpty())

        composeRule.onNodeWithText(string(R.string.create_topic)).performScrollTo().performTextInput("Algorithms exam review")
        composeRule.onNodeWithText("Mugar Library").performScrollTo().performClick()
        composeRule.onNodeWithText(string(R.string.create_meeting_point)).performScrollTo().performTextInput("2nd floor")
        composeRule.onNodeWithText(string(R.string.action_create_pod)).performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) { created }
        val pod = repository.created.single()
        assertEquals("Algorithms exam review", pod.title)
        assertEquals("Mugar Library", pod.building)
        assertEquals("You", pod.hostName)
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
