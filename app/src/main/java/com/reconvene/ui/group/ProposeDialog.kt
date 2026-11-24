package com.reconvene.ui.group

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.reconvene.R
import com.reconvene.data.model.PodMode
import com.reconvene.ui.create.BuildingField
import com.reconvene.ui.create.ChoiceRow
import com.reconvene.ui.create.CreatePodForm
import com.reconvene.ui.create.FieldGroup
import com.reconvene.ui.create.FormErrors
import com.reconvene.ui.create.MeetingPointField
import com.reconvene.ui.create.WhenFields
import com.reconvene.ui.create.message
import com.reconvene.ui.create.newPodForm
import com.reconvene.ui.create.startSlots
import com.reconvene.ui.create.validate
import com.reconvene.ui.discover.hintRes
import com.reconvene.ui.discover.labelRes
import java.time.Instant
import java.time.ZoneId

// The proposal's own checks, which are the create form's without the topic.
fun CreatePodForm.proposalErrors(now: Instant, zone: ZoneId): FormErrors = validate(now, zone).copy(title = null)

// Full screen, since the when and where fields are too tall for an alert. Errors show once the user
// tries to propose, like the Create screen.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProposeDialog(
    now: Instant,
    onPropose: (CreatePodForm) -> Unit,
    onDismiss: () -> Unit,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val zonedNow = now.atZone(zone)
    var form by rememberSaveable { mutableStateOf(newPodForm(zonedNow)) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val errors = if (submitted) form.proposalErrors(now, zone) else FormErrors()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.imePadding()) {
                TopAppBar(
                    title = { Text(stringResource(R.string.group_propose_title)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_cancel))
                        }
                    },
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Text(
                        text = stringResource(R.string.group_propose_intro),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    WhenFields(
                        form = form,
                        today = zonedNow.toLocalDate(),
                        startSlots = startSlots(form.day, zonedNow),
                        error = errors.time?.message(),
                        onFormChange = { form = it },
                    )
                    BuildingField(form = form, hasError = errors.building != null, onFormChange = { form = it })
                    MeetingPointField(form = form, error = errors.meetingPoint?.message(), onFormChange = { form = it })
                    FieldGroup(label = stringResource(R.string.create_vibe), hint = stringResource(form.mode.hintRes)) {
                        ChoiceRow(
                            options = PodMode.entries,
                            selected = form.mode,
                            label = { stringResource(it.labelRes) },
                            onSelect = { mode -> mode?.let { form = form.copy(mode = it) } },
                        )
                    }
                }
                HorizontalDivider()
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!errors.isEmpty) {
                        Text(
                            text = stringResource(R.string.create_fix_errors),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    Button(
                        onClick = {
                            submitted = true
                            if (form.proposalErrors(now, zone).isEmpty) onPropose(form)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp),
                    ) {
                        Text(stringResource(R.string.group_propose_confirm))
                    }
                }
            }
        }
    }
}
