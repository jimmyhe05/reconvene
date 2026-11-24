package com.reconvene.ui.pod

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.reconvene.R
import com.reconvene.data.model.Pod
import com.reconvene.ui.theme.ReconveneTheme
import java.time.Instant

// Join, Leave or Cancel, whichever fits the viewer, or a note when none applies. Used by the
// Discover card and the pod detail screen.
@Composable
fun PodActions(
    pod: Pod,
    now: Instant,
    viewerUid: String,
    isBusy: Boolean,
    onJoin: () -> Unit,
    onLeave: () -> Unit,
    onCancel: () -> Unit,
) {
    when {
        pod.hostUid == viewerUid -> HostRow(isBusy, onCancel)
        pod.hasMember(viewerUid) -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.pod_you_joined),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(shape = MaterialTheme.shapes.small, onClick = onLeave, enabled = !isBusy, modifier = Modifier.heightIn(min = 48.dp)) {
                ButtonLabel(R.string.pod_leave, R.string.pod_leaving, isBusy)
            }
        }
        pod.joinProblem(now) == null -> Button(
            shape = MaterialTheme.shapes.small,
            onClick = onJoin,
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            ButtonLabel(R.string.pod_join, R.string.pod_joining, isBusy)
        }
        // A full pod already says so in its badge.
        !pod.isFull -> MembershipNote(stringResource(R.string.pod_join_closed))
    }
}

@Composable
private fun HostRow(isBusy: Boolean, onCancel: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.pod_you_host),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(
            shape = MaterialTheme.shapes.small,
            onClick = { confirming = true },
            enabled = !isBusy,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            ButtonLabel(R.string.pod_cancel, R.string.pod_canceling, isBusy)
        }
    }
    // Canceling can't be undone, so it always asks first.
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.pod_cancel_title)) },
            text = { Text(stringResource(R.string.pod_cancel_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        onCancel()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.pod_cancel))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) {
                    Text(stringResource(R.string.pod_keep))
                }
            },
        )
    }
}

@Composable
private fun MembershipNote(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = ReconveneTheme.colors.inkMuted)
}

@Composable
private fun ButtonLabel(@StringRes idle: Int, @StringRes busy: Int, isBusy: Boolean) {
    if (isBusy) {
        val busyLabel = stringResource(busy)
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp).semantics { contentDescription = busyLabel },
            strokeWidth = 2.dp,
        )
    } else {
        Text(stringResource(idle))
    }
}
