package com.reconvene.ui.discover

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reconvene.R
import com.reconvene.ui.pod.PodMessage
import com.reconvene.ui.theme.ReconveneTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class DiscoverMessageTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun aDeniedLocationOffersToOpenSettings() {
        var openedSettings = false
        composeRule.setContent {
            ReconveneTheme {
                DiscoverContent(
                    uiState = DiscoverUiState.Content(sections = emptyList(), now = Instant.parse("2026-11-24T23:00:00Z")),
                    actions = PodListActions(onOpenAppSettings = { openedSettings = true }),
                    message = PodMessage.LOCATION_DENIED,
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.nearby_denied)).assertExists()
        composeRule.onNodeWithText(context.getString(R.string.nearby_open_settings)).performClick()

        composeRule.waitForIdle()
        assertTrue(openedSettings)
    }
}
