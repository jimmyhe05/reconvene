package com.reconvene.ui.pod

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reconvene.R
import com.reconvene.data.auth.LOCAL_USER
import com.reconvene.data.auth.UnavailableAuthRepository
import com.reconvene.data.local.ReconveneDatabase
import com.reconvene.data.local.toEntity
import com.reconvene.data.model.Pod
import com.reconvene.data.model.PodMember
import com.reconvene.data.model.PodMode
import com.reconvene.data.remote.FakeGroupRemoteDataSource
import com.reconvene.data.repository.OfflineFirstGroupRepository
import com.reconvene.data.repository.PodRepository
import com.reconvene.ui.theme.ReconveneTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(AndroidJUnit4::class)
class PodDetailScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val zone = ZoneId.of("America/New_York")
    private val now = ZonedDateTime.of(2026, 11, 16, 14, 0, 0, 0, zone).toInstant()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val maya = PodMember("maya", "Maya Lin")
    private val clock = Clock.fixed(now, zone)
    private val database = Room.inMemoryDatabaseBuilder(context, ReconveneDatabase::class.java).build()
    private val groupRepository = OfflineFirstGroupRepository(
        groupDao = database.groupDao(),
        messageDao = database.messageDao(),
        podDao = database.podDao(),
        remote = FakeGroupRemoteDataSource(clock, delayMillis = 0),
        clock = clock,
    )

    @After
    fun closeDatabase() = database.close()

    private val you = PodMember(LOCAL_USER.uid, "You")

    private fun pod(host: PodMember, members: List<PodMember> = listOf(host), startsIn: Duration = Duration.ofMinutes(30)) = Pod(
        id = "algo",
        title = "Algorithms exam review",
        course = "CS 330",
        building = "Mugar Library",
        meetingPoint = "2nd floor",
        startsAt = now.plus(startsIn),
        endsAt = now.plus(startsIn).plus(Duration.ofMinutes(60)),
        capacity = 4,
        hostUid = host.uid,
        hostName = host.name,
        members = members,
        mode = PodMode.QUIET,
    )

    // Started 10 minutes ago, hosted by you, with Maya in it.
    private val running = pod(host = you, members = listOf(you, maya), startsIn = Duration.ofMinutes(-10))

    // Without Firebase settings, the acting user is LOCAL_USER, shown as "You".
    private fun showDetail(pod: Pod) {
        // Keeping a group reads the pod from Room.
        runBlocking { database.podDao().upsertAll(listOf(pod.toEntity())) }
        val viewModel = PodDetailViewModel(pod.id, MemoryPodRepository(pod), groupRepository, UnavailableAuthRepository, clock)
        composeRule.setContent {
            ReconveneTheme {
                PodDetailScreen(onBack = {}, viewModel = viewModel)
            }
        }
    }

    @Test
    fun whereShowsTheWalkAndOffersToOpenMaps() {
        composeRule.setContent {
            ReconveneTheme {
                PodDetailContent(uiState = PodDetailUiState.Content(pod(host = maya), now, viewerUid = null, walkMinutes = 4), onBack = {})
            }
        }
        val place = context.getString(R.string.pod_place, "Mugar Library", "2nd floor")

        composeRule.onNodeWithText(context.getString(R.string.pod_place_walk, place, 4), useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.pod_detail_open_maps)).assertIsDisplayed()
    }

    @Test
    fun showsWhoIsGoingAndJoiningAddsYou() {
        showDetail(pod(host = maya))

        composeRule.onNodeWithText("Algorithms exam review").assertIsDisplayed()
        composeRule.onNodeWithText("Maya Lin").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.pod_detail_going_count, 1, 4)).assertIsDisplayed()

        composeRule.onNodeWithText(context.getString(R.string.pod_join)).performClick()

        composeRule.onNodeWithText(context.getString(R.string.pod_detail_going_count, 2, 4)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.pod_you_joined)).assertIsDisplayed()
    }

    @Test
    fun cancelingYourPodSaysYouCanceledIt() {
        showDetail(pod(host = PodMember(LOCAL_USER.uid, "You")))
        val cancel = context.getString(R.string.pod_cancel)

        composeRule.onNodeWithText(cancel).performClick()
        composeRule.onNode(hasText(cancel) and hasAnyAncestor(isDialog())).performClick()

        composeRule.onNodeWithText(context.getString(R.string.pod_detail_canceled_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.pod_detail_back)).assertIsDisplayed()
    }

    @Test
    fun keepingARunningPodCreatesTheGroupAndInvitesTheOthers() {
        showDetail(running)
        val keepThisGroup = context.getString(R.string.keep_group_action)

        composeRule.onNodeWithText(keepThisGroup).performClick()
        composeRule.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextReplacement("Algo crew")
        composeRule.onNode(hasText(context.getString(R.string.keep_group_confirm)) and hasAnyAncestor(isDialog()))
            .performClick()

        // Room answers on its own threads, so wait for the section to go instead of assuming it has.
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText(keepThisGroup).fetchSemanticsNodes().isEmpty() }
        composeRule.onNodeWithText(context.getString(R.string.pod_group_kept)).assertIsDisplayed()
        val group = runBlocking { groupRepository.observeGroups().first().single() }
        assertEquals("Algo crew", group.name)
        assertEquals(listOf(maya), group.invited)
    }

    @Test
    fun aGroupNeedsANameOfAtLeastThreeCharacters() {
        showDetail(running)

        composeRule.onNodeWithText(context.getString(R.string.keep_group_action)).performClick()
        composeRule.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextReplacement("Al")

        composeRule.onNode(hasText(context.getString(R.string.keep_group_confirm)) and hasAnyAncestor(isDialog()))
            .assertIsNotEnabled()
    }
}

// Applies join, leave and cancel straight away, the way the server would accept them.
private class MemoryPodRepository(pod: Pod) : PodRepository {
    private val pods = MutableStateFlow(listOf(pod))

    override fun observePods(): Flow<List<Pod>> = pods

    override suspend fun refresh() = Unit

    override suspend fun createPod(pod: Pod) = pods.update { it + pod }

    override suspend fun joinPod(podId: String, member: PodMember) = change(podId) { it.withMember(member) }

    override suspend fun leavePod(podId: String, uid: String) = change(podId) { it.withoutMember(uid) }

    override suspend fun cancelPod(podId: String, uid: String) = change(podId) { it.copy(canceled = true) }

    private fun change(podId: String, edit: (Pod) -> Pod) =
        pods.update { list -> list.map { if (it.id == podId) edit(it) else it } }
}
