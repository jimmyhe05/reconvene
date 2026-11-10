package com.reconvene.ui.profile

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reconvene.R
import com.reconvene.ui.theme.ReconveneTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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
}
