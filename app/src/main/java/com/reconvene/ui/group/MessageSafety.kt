package com.reconvene.ui.group

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.reconvene.R
import com.reconvene.data.model.ReportReason

@get:StringRes
val ReportReason.labelRes: Int
    get() = when (this) {
        ReportReason.HARASSMENT -> R.string.group_reason_harassment
        ReportReason.SPAM -> R.string.group_reason_spam
        ReportReason.ACADEMIC_DISHONESTY -> R.string.group_reason_academic_dishonesty
        ReportReason.UNSAFE_MEETUP -> R.string.group_reason_unsafe_meetup
        ReportReason.OTHER -> R.string.group_reason_other
    }

// A button, not a long press, so it's easy to find and works with TalkBack and switch access.
@Composable
fun MessageMenu(authorName: String, onReport: () -> Unit, onBlock: () -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                painter = painterResource(R.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.group_message_options, authorName),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.group_report)) },
                onClick = {
                    open = false
                    onReport()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.group_block_name, authorName)) },
                onClick = {
                    open = false
                    onBlock()
                },
            )
        }
    }
}

@Composable
fun ReportDialog(authorName: String, onReport: (ReportReason) -> Unit, onDismiss: () -> Unit) {
    var reason by rememberSaveable { mutableStateOf<ReportReason?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.group_report_title)) },
        text = {
            // Scrolls so every reason stays reachable at large font sizes.
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.group_report_body, authorName))
                // Each whole row is the radio button, so it's a big target and read as one choice.
                Column(Modifier.selectableGroup()) {
                    ReportReason.entries.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(
                                    selected = option == reason,
                                    onClick = { reason = option },
                                    role = Role.RadioButton,
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = option == reason, onClick = null)
                            Text(
                                text = stringResource(option.labelRes),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { reason?.let(onReport) }, enabled = reason != null) {
                Text(stringResource(R.string.group_report_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
fun BlockDialog(authorName: String, onBlock: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.group_block_title, authorName)) },
        text = { Text(stringResource(R.string.group_block_body)) },
        confirmButton = {
            TextButton(onClick = onBlock) { Text(stringResource(R.string.group_block_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
