package com.reconvene.ui.groups

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reconvene.R
import com.reconvene.data.auth.UnavailableAuthRepository
import com.reconvene.data.local.ReconveneDatabase
import com.reconvene.data.remote.FakeGroupRemoteDataSource
import com.reconvene.data.repository.OfflineFirstGroupRepository
import com.reconvene.ui.theme.ReconveneTheme
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(AndroidJUnit4::class)
class GroupsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val zone = ZoneId.of("America/New_York")
    private val clock = Clock.fixed(ZonedDateTime.of(2026, 11, 17, 10, 0, 0, 0, zone).toInstant(), zone)
    private val database = Room.inMemoryDatabaseBuilder(context, ReconveneDatabase::class.java).build()

    @After
    fun closeDatabase() = database.close()

    // Room answers on its own threads, which the test can't see, so it waits for the text instead.
    private fun waitForText(text: String, present: Boolean = true) = composeRule.waitUntil(5_000) {
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() == present
    }

    // Without Firebase settings the user is LOCAL_USER, who is in two sample groups and invited to one.
    @Test
    fun acceptingAnInviteMovesItIntoYourGroups() {
        val repository = OfflineFirstGroupRepository(
            groupDao = database.groupDao(),
            messageDao = database.messageDao(),
            podDao = database.podDao(),
            remote = FakeGroupRemoteDataSource(clock, delayMillis = 0),
            clock = clock,
        )
        val viewModel = GroupsViewModel(repository, UnavailableAuthRepository)
        composeRule.setContent {
            ReconveneTheme {
                GroupsScreen(onSignIn = {}, onOpenGroup = {}, viewModel = viewModel)
            }
        }
        val invites = context.getString(R.string.groups_invites)

        waitForText("Signals study group")
        composeRule.onNodeWithText(invites).assertIsDisplayed()
        composeRule.onNodeWithText("Algorithms study group").assertIsDisplayed()

        composeRule.onNodeWithContentDescription(context.getString(R.string.groups_accept_label, "Signals study group"))
            .performClick()

        waitForText(invites, present = false)
        composeRule.onNodeWithText("Signals study group").assertIsDisplayed()
        waitForText(context.getString(R.string.groups_joined))
    }

    @Test
    fun signedOutPromptGoesToSignIn() {
        var signInTapped = false
        composeRule.setContent {
            ReconveneTheme {
                GroupsContent(uiState = GroupsUiState.SignedOut, onSignIn = { signInTapped = true })
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.groups_signed_out_title)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.action_sign_in)).performClick()

        assertTrue(signInTapped)
    }
}
