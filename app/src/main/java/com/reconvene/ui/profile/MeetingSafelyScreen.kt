package com.reconvene.ui.profile

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.reconvene.R
import com.reconvene.ui.theme.ReconveneTheme
import com.reconvene.ui.theme.TimeStyles

// Plain advice for meeting people from the app, numbered like a checklist.
@Composable
fun MeetingSafelyScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    ProfileSubScreen(stringResource(R.string.safety_title), onBack, modifier) {
        Text(
            text = stringResource(R.string.safety_intro),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        SAFETY_TIPS.forEachIndexed { index, tip -> NumberedLine(index + 1, tip) }
    }
}

// "01  Everyone you meet ..." with a rule under it. Also used on the signed-out Profile.
@Composable
fun NumberedLine(number: Int, @StringRes text: Int) {
    Column(Modifier.semantics(mergeDescendants = true) {}) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "%02d".format(number),
                style = TimeStyles.meta,
                color = ReconveneTheme.colors.inkMuted,
                modifier = Modifier.width(24.dp).padding(top = 2.dp),
            )
            Text(stringResource(text), style = MaterialTheme.typography.bodyLarge)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

private val SAFETY_TIPS = listOf(
    R.string.safety_public,
    R.string.safety_share,
    R.string.safety_name_only,
    R.string.safety_leave,
    R.string.safety_report,
)
