package com.reconvene.ui.pod

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reconvene.R
import com.reconvene.data.auth.LOCAL_USER
import com.reconvene.data.auth.UnavailableAuthRepository
import com.reconvene.data.model.Pod
import com.reconvene.data.model.PodMember
import com.reconvene.data.model.PodMode
import com.reconvene.data.repository.PodRepository
import com.reconvene.ui.theme.ReconveneTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
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

    private fun pod(host: PodMember) = Pod(
        id = "algo",
        title = "Algorithms exam review",
        course = "CS 330",
        building = "Mugar Library",
        meetingPoint = "2nd floor",
        startsAt = now.plus(Duration.ofMinutes(30)),
        endsAt = now.plus(Duration.ofMinutes(90)),
        capacity = 4,
        hostUid = host.uid,
        hostName = host.name,
        members = listOf(host),
        mode = PodMode.QUIET,
    )

    // Without Firebase settings, the acting user is LOCAL_USER, shown as "You".
    private fun showDetail(pod: Pod) {
        val viewModel = PodDetailViewModel(pod.id, MemoryPodRepository(pod), UnavailableAuthRepository, Clock.fixed(now, zone))
        composeRule.setContent {
            ReconveneTheme {
                PodDetailScreen(onBack = {}, viewModel = viewModel)
            }
        }
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
