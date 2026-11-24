package com.reconvene.ui.discover

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reconvene.R
import com.reconvene.data.model.Pod
import com.reconvene.data.model.PodMember
import com.reconvene.data.model.PodMode
import com.reconvene.ui.theme.ReconveneTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@RunWith(AndroidJUnit4::class)
class PodTimeTextTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val now = Instant.parse("2026-11-24T23:00:00Z")

    @Test
    fun aPodStartingSoonShowsTheCountdownAndTheClockTime() {
        val startsAt = now.plus(Duration.ofMinutes(20))
        val pod = Pod(
            id = "soon",
            title = "Linear algebra midterm review",
            course = "MA 242",
            building = "CDS",
            meetingPoint = "3rd floor commons",
            startsAt = startsAt,
            endsAt = startsAt.plus(Duration.ofMinutes(90)),
            capacity = 8,
            hostUid = "host",
            hostName = "Host",
            members = listOf(PodMember("host", "Host")),
            mode = PodMode.TALK,
        )
        composeRule.setContent {
            ReconveneTheme { PodCard(pod = pod, now = now) }
        }
        val clock = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(startsAt.atZone(ZoneId.systemDefault()))

        composeRule.onNodeWithText(
            context.resources.getQuantityString(R.plurals.pod_time_starts_in, 20, 20, clock),
            useUnmergedTree = true,
        ).assertIsDisplayed()
    }
}
