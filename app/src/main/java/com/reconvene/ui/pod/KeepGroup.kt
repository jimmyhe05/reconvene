package com.reconvene.ui.pod

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.reconvene.R
import com.reconvene.data.model.GROUP_NAME_MAX
import com.reconvene.data.model.GROUP_NAME_MIN
import com.reconvene.data.model.Pod
import com.reconvene.data.model.suggestedGroupName
import com.reconvene.ui.create.toSingleLine
import com.reconvene.ui.theme.ReconveneTheme

// Shown to the host of a running pod. The button opens a dialog to name the group, since everyone
// else in the pod gets an invite with that name.
@Composable
internal fun KeepGroupSection(pod: Pod, isKeeping: Boolean, onKeep: (name: String) -> Unit) {
    var naming by rememberSaveable { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.keep_group_heading),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.keep_group_body),
                style = MaterialTheme.typography.bodyMedium,
                color = ReconveneTheme.colors.inkMuted,
            )
            FilledTonalButton(onClick = { naming = true }, enabled = !isKeeping) {
                Icon(
                    painterResource(R.drawable.ic_users),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Text(
                    text = stringResource(if (isKeeping) R.string.keep_group_keeping else R.string.keep_group_action),
                    modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                )
            }
        }
    }
    if (naming) {
        KeepGroupDialog(
            pod = pod,
            onKeep = { name ->
                naming = false
                onKeep(name)
            },
            onDismiss = { naming = false },
        )
    }
}

@Composable
private fun KeepGroupDialog(pod: Pod, onKeep: (name: String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(pod.suggestedGroupName()) }
    val trimmed = name.trim()
    val isValid = trimmed.length in GROUP_NAME_MIN..GROUP_NAME_MAX
    val invites = pod.members.size - 1
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.keep_group_action)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(pluralStringResource(R.plurals.keep_group_invites, invites, invites))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.toSingleLine().take(GROUP_NAME_MAX) },
                    label = { Text(stringResource(R.string.keep_group_name)) },
                    supportingText = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.keep_group_name_hint), Modifier.weight(1f))
                            Text(stringResource(R.string.create_counter, name.length, GROUP_NAME_MAX))
                        }
                    },
                    isError = !isValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onKeep(trimmed) }, enabled = isValid) {
                Text(stringResource(R.string.keep_group_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
