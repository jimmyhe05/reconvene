package com.reconvene.ui.discover

import android.Manifest
import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.reconvene.R
import com.reconvene.data.auth.UnavailableAuthRepository
import com.reconvene.data.location.GeoPoint
import com.reconvene.data.location.LocationRepository
import com.reconvene.data.location.buildingLocations
import com.reconvene.data.model.Pod
import com.reconvene.data.model.PodMember
import com.reconvene.data.model.PodMode
import com.reconvene.data.repository.PodRepository
import com.reconvene.ui.theme.ReconveneTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(AndroidJUnit4::class)
class DiscoverScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val zone = ZoneId.of("America/New_York")
    private val now = ZonedDateTime.of(2026, 11, 16, 14, 0, 0, 0, zone).toInstant()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var openedPodId: String? = null

    private fun pod(id: String, title: String, startsAt: Instant, full: Boolean = false) = Pod(
        id = id,
        title = title,
        course = null,
        building = "Mugar Library",
        meetingPoint = "2nd floor",
        startsAt = startsAt,
        endsAt = startsAt.plus(Duration.ofHours(1)),
        capacity = if (full) 2 else 4,
        hostUid = "host",
        hostName = "Host",
        members = listOf(PodMember("host", "Host")) + if (full) listOf(PodMember("guest", "Guest")) else emptyList(),
        mode = PodMode.QUIET,
    )

    @Before
    fun showDiscover() {
        val pods = listOf(
            pod("algo", "Algorithms exam review", now.plus(Duration.ofMinutes(30))),
            pod("chem", "Organic chemistry", now.plus(Duration.ofMinutes(90))),
            pod("calc", "Calculus review", now.plus(Duration.ofHours(4)), full = true),
        )
        val viewModel = DiscoverViewModel(
            StaticPodRepository(pods),
            UnavailableAuthRepository,
            Clock.fixed(now, zone),
            locationRepository = AtMugar,
        )
        composeRule.setContent {
            ReconveneTheme {
                DiscoverScreen(onCreatePod = {}, onSignIn = {}, onOpenPod = { podId, _ -> openedPodId = podId }, viewModel = viewModel)
            }
        }
    }

    // The permission is granted up front, as if the user tapped Allow in the system dialog.
    @Test
    fun nearestFirstShowsWalkingTimes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(instrumentation.targetContext.packageName, Manifest.permission.ACCESS_FINE_LOCATION)

        composeRule.onNodeWithText(context.getString(R.string.nearby_chip)).performClick()

        val place = context.getString(R.string.pod_place, "Mugar Library", "2nd floor")
        // All three pods are in Mugar, so every card shows the walk.
        composeRule.onAllNodesWithText(context.getString(R.string.pod_place_walk, place, 1), useUnmergedTree = true)
            .assertCountEquals(3)
    }

    @Test
    fun searchNarrowsTheListAndClearingBringsItBack() {
        composeRule.onNodeWithText(context.getString(R.string.discover_search)).performTextInput("chem")

        composeRule.onNodeWithText("Organic chemistry").assertIsDisplayed()
        composeRule.onNodeWithText("Algorithms exam review").assertDoesNotExist()
        composeRule.onNodeWithText(context.resources.getQuantityString(R.plurals.discover_match_count, 1, 1))
            .assertIsDisplayed()

        composeRule.onNodeWithContentDescription(context.getString(R.string.discover_search_clear)).performClick()

        composeRule.onNodeWithText("Algorithms exam review").assertIsDisplayed()
        composeRule.onNodeWithText("Organic chemistry").assertIsDisplayed()
    }

    @Test
    fun openSpotsChipHidesFullPodsUntilTappedAgain() {
        val list = composeRule.onNode(hasScrollToIndexAction())
        list.performScrollToNode(hasText("Calculus review"))

        composeRule.onNodeWithText(context.getString(R.string.discover_filter_open_spots)).performScrollTo().performClick()
        composeRule.onNodeWithText("Calculus review").assertDoesNotExist()
        composeRule.onNodeWithText(context.resources.getQuantityString(R.plurals.discover_match_count, 2, 2))
            .assertIsDisplayed()

        composeRule.onNodeWithText(context.getString(R.string.discover_filter_open_spots)).performClick()
        list.performScrollToNode(hasText("Calculus review"))
        composeRule.onNodeWithText("Calculus review").assertIsDisplayed()
    }

    @Test
    fun tappingACardOpensThatPod() {
        composeRule.onNodeWithText("Organic chemistry").performClick()

        assertEquals("chem", openedPodId)
    }

    @Test
    fun noMatchesOffersToClearTheSearch() {
        composeRule.onNodeWithText(context.getString(R.string.discover_search)).performTextInput("physics")

        composeRule.onNodeWithText(context.getString(R.string.discover_no_matches_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.action_clear_filters)).performClick()

        composeRule.onNodeWithText("Algorithms exam review").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.discover_no_matches_title)).assertDoesNotExist()
    }
}

private class StaticPodRepository(private val pods: List<Pod>) : PodRepository {
    override fun observePods(): Flow<List<Pod>> = flowOf(pods)

    override suspend fun refresh() = Unit

    override suspend fun createPod(pod: Pod) = Unit

    override suspend fun joinPod(podId: String, member: PodMember) = Unit

    override suspend fun leavePod(podId: String, uid: String) = Unit

    override suspend fun cancelPod(podId: String, uid: String) = Unit
}

private object AtMugar : LocationRepository {
    override suspend fun currentLocation(): GeoPoint = buildingLocations.getValue("Mugar Library")
}
