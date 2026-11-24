package com.reconvene.ui.group

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reconvene.R
import com.reconvene.data.auth.LOCAL_USER
import com.reconvene.data.auth.UnavailableAuthRepository
import com.reconvene.data.local.ReconveneDatabase
import com.reconvene.data.model.PodMember
import com.reconvene.data.model.PodMode
import com.reconvene.data.model.SessionProposal
import com.reconvene.data.remote.FakeGroupRemoteDataSource
import com.reconvene.data.repository.OfflineFirstGroupRepository
import com.reconvene.ui.theme.ReconveneTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(AndroidJUnit4::class)
class GroupScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val zone = ZoneId.of("America/New_York")
    private val clock = Clock.fixed(ZonedDateTime.of(2026, 11, 17, 10, 0, 0, 0, zone).toInstant(), zone)
    private val database = Room.inMemoryDatabaseBuilder(context, ReconveneDatabase::class.java).build()
    private val repository = OfflineFirstGroupRepository(
        groupDao = database.groupDao(),
        messageDao = database.messageDao(),
        podDao = database.podDao(),
        remote = FakeGroupRemoteDataSource(clock, delayMillis = 0),
        clock = clock,
    )
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // In the app the Groups tab keeps groups in Room. Without Firebase settings the user is
    // LOCAL_USER: a member of g1 and the host of g3.
    @Before
    fun syncGroups() {
        syncScope.launch { repository.syncGroups(LOCAL_USER.uid) }
        runBlocking { withTimeout(5_000) { repository.observeGroup("g3").first { it != null } } }
    }

    @After
    fun tearDown() {
        syncScope.cancel()
        database.close()
    }

    private fun open(groupId: String) {
        val viewModel = GroupViewModel(groupId, repository, UnavailableAuthRepository, clock)
        composeRule.setContent {
            ReconveneTheme {
                GroupScreen(onBack = {}, viewModel = viewModel)
            }
        }
    }

    // Room answers on its own threads, which the test can't see, so it waits for the text instead.
    private fun waitForText(text: String) = composeRule.waitUntil(5_000) {
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    private fun string(id: Int) = context.getString(id)

    @Test
    fun sentMessageShowsInTheChat() {
        open("g1")
        waitForText("Start with problems 3 and 4.")

        composeRule.onNode(hasSetTextAction()).performTextInput("See you tomorrow")
        composeRule.onNodeWithContentDescription(string(R.string.group_send)).performClick()

        waitForText("See you tomorrow")
        composeRule.onNodeWithText("See you tomorrow").assertIsDisplayed()
    }

    @Test
    fun answeringYesAddsYouToWhoCanCome() {
        open("g1")
        waitForText(string(R.string.group_tab_plan))
        composeRule.onNodeWithText(string(R.string.group_tab_plan)).performClick()
        composeRule.onNodeWithText("2 can come").assertIsDisplayed()

        composeRule.onNodeWithText(string(R.string.group_plan_yes)).performClick()

        waitForText("3 can come")
        composeRule.onNodeWithText("Maya, Jordan, You").assertIsDisplayed()
    }

    @Test
    fun hostSchedulesOnceTwoCanCome() {
        runBlocking {
            repository.propose(
                "g3",
                SessionProposal(
                    id = "next",
                    startsAt = ZonedDateTime.of(2026, 11, 18, 16, 0, 0, 0, zone).toInstant(),
                    endsAt = ZonedDateTime.of(2026, 11, 18, 17, 30, 0, 0, zone).toInstant(),
                    building = "CDS",
                    meetingPoint = "3rd floor commons",
                    mode = PodMode.QUIET,
                    byUid = LOCAL_USER.uid,
                ),
            )
            repository.vote("g3", PodMember("sample-noah", "Noah"), canCome = true)
        }
        open("g3")
        waitForText(string(R.string.group_tab_plan))
        composeRule.onNodeWithText(string(R.string.group_tab_plan)).performClick()
        waitForText("2 can come")

        composeRule.onNodeWithText(string(R.string.group_plan_schedule)).performClick()

        waitForText(string(R.string.group_scheduled))
        waitForText(string(R.string.group_plan_none_title))
    }
}
