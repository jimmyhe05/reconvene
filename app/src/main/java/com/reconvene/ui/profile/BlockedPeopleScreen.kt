package com.reconvene.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reconvene.R
import com.reconvene.ui.theme.ReconveneTheme

@Composable
fun BlockedPeopleScreen(
    onBack: () -> Unit,
    viewModel: BlockedPeopleViewModel = viewModel(factory = BlockedPeopleViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BlockedPeopleContent(uiState, onBack, viewModel::unblock)
}

@Composable
fun BlockedPeopleContent(
    uiState: BlockedPeopleUiState,
    onBack: () -> Unit,
    onUnblock: (uid: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ReconveneTheme.colors
    ProfileSubScreen(stringResource(R.string.blocked_title), onBack, modifier) {
        Text(
            text = stringResource(R.string.profile_blocked_body),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.inkMuted,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        if (uiState.blocked.isEmpty()) {
            Text(
                text = stringResource(R.string.blocked_empty),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
        uiState.blocked.forEach { person ->
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(person.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    // "Unblock Ben" rather than a row of identical "Unblock" buttons for a screen reader.
                    val label = stringResource(R.string.profile_unblock_name, person.name)
                    TextButton(
                        onClick = { onUnblock(person.uid) },
                        modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = label },
                    ) {
                        Text(stringResource(R.string.profile_unblock))
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        if (uiState.unblockFailed) {
            Text(
                text = stringResource(R.string.profile_unblock_failed),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}
