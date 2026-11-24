package com.reconvene.ui.profile

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reconvene.R
import com.reconvene.data.auth.SignedInUser
import com.reconvene.data.model.PodMember
import com.reconvene.data.settings.Appearance
import com.reconvene.ui.theme.ReconveneTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration

@RunWith(AndroidJUnit4::class)
class ProfileContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun string(@StringRes id: Int): String =
        ApplicationProvider.getApplicationContext<Context>().getString(id)

    @Test
    fun signedOutShowsTheFullEmailHintNextToSignIn() {
        var signInClicked = false
        composeRule.setContent {
            ReconveneTheme {
                ProfileContent(
                    uiState = ProfileUiState(isAvailable = true),
                    onSignIn = { signInClicked = true },
                    onSignOut = {},
                )
            }
        }

        composeRule.onNodeWithText(string(R.string.profile_sign_in_hint)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.profile_sign_in)).performClick()
        assertTrue(signInClicked)
    }

    @Test
    fun noBuAccountErrorSaysToAddOneWithTheFullEmail() {
        composeRule.setContent {
            ReconveneTheme {
                ProfileContent(
                    uiState = ProfileUiState(isAvailable = true, error = SignInError.NO_BU_ACCOUNT),
                    onSignIn = {},
                    onSignOut = {},
                )
            }
        }

        composeRule.onNodeWithText(string(R.string.profile_error_no_bu_account)).assertIsDisplayed()
    }

    private val terrier = SignedInUser(uid = "u1", email = "terrier@bu.edu", displayName = "Terrier Dog")

    @Test
    fun signedInShowsTheRecordAndOpensTheirPages() {
        var opened: String? = null
        var appearance: Appearance? = null
        composeRule.setContent {
            ReconveneTheme {
                ProfileContent(
                    uiState = ProfileUiState(
                        isAvailable = true,
                        user = terrier,
                        record = SemesterRecord(pods = 6, hosted = 3, studied = Duration.ofMinutes(570), groups = 2),
                        blockedCount = 2,
                    ),
                    onSignIn = {},
                    onSignOut = {},
                    actions = ProfileActions(
                        onOpenBlocked = { opened = "blocked" },
                        onAppearanceChange = { appearance = it },
                    ),
                )
            }
        }

        composeRule.onNodeWithContentDescription("This semester: 6 pods, 9.5 hours, 2 groups").assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.blocked_title)).performScrollTo().performClick()
        assertEquals("blocked", opened)

        composeRule.onNodeWithText(string(R.string.profile_appearance)).performScrollTo().performClick()
        composeRule.onNodeWithText(string(R.string.appearance_dark)).performClick()
        assertEquals(Appearance.DARK, appearance)
    }

    @Test
    fun blockedPeopleCanBeUnblockedByName() {
        var unblocked: String? = null
        composeRule.setContent {
            ReconveneTheme {
                BlockedPeopleContent(
                    uiState = BlockedPeopleUiState(signedIn = true, blocked = listOf(PodMember("ana", "Ana"), PodMember("ben", "Ben"))),
                    onBack = {},
                    onUnblock = { unblocked = it },
                )
            }
        }

        composeRule.onNodeWithText("Ana").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Unblock Ben").performClick()
        assertEquals("ben", unblocked)
    }
}
