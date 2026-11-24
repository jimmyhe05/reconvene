package com.reconvene.ui.group

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.reconvene.R
import com.reconvene.data.model.SCHEDULE_YES_MIN
import com.reconvene.data.model.SessionProposal
import com.reconvene.ui.components.Tag
import com.reconvene.ui.create.CreatePodForm
import com.reconvene.ui.discover.dayLabel
import com.reconvene.ui.discover.labelRes
import com.reconvene.ui.theme.ReconveneTheme
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val shortTime: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

// Whether this tab should ask for attention: there's a session to answer and the viewer hasn't.
val GroupUiState.Content.needsYourVote: Boolean
    get() = proposal != null && !isReadOnly && !isHost && myVote == null

@Composable
fun GroupPlan(
    uiState: GroupUiState.Content,
    onVote: (canCome: Boolean) -> Unit,
    onSchedule: () -> Unit,
    onPropose: (CreatePodForm) -> Unit,
    modifier: Modifier = Modifier,
) {
    var proposing by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val proposal = uiState.proposal
        if (proposal == null) {
            NoProposal()
        } else {
            ProposalCard(uiState, proposal, onVote, onSchedule)
        }
        if (!uiState.isReadOnly) {
            ProposeButton(replaces = proposal != null, enabled = !uiState.isBusy, onClick = { proposing = true })
        } else {
            Text(
                text = stringResource(R.string.group_plan_read_only),
                style = MaterialTheme.typography.bodyMedium,
                color = ReconveneTheme.colors.inkMuted,
            )
        }
    }
    if (proposing) {
        ProposeDialog(
            now = uiState.now,
            onPropose = {
                onPropose(it)
                proposing = false
            },
            onDismiss = { proposing = false },
        )
    }
}

// A new proposal replaces the current one, answers included, so the button says so.
@Composable
private fun ProposeButton(replaces: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text(stringResource(if (replaces) R.string.group_propose_other else R.string.group_propose))
        }
        if (replaces) {
            Text(
                text = stringResource(R.string.group_propose_replaces),
                style = MaterialTheme.typography.bodySmall,
                color = ReconveneTheme.colors.inkMuted,
            )
        }
    }
}

@Composable
private fun NoProposal() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.group_plan_none_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.group_plan_none_body),
            style = MaterialTheme.typography.bodyMedium,
            color = ReconveneTheme.colors.inkMuted,
        )
    }
}

@Composable
private fun ProposalCard(
    uiState: GroupUiState.Content,
    proposal: SessionProposal,
    onVote: (canCome: Boolean) -> Unit,
    onSchedule: () -> Unit,
) {
    val group = uiState.group
    val host = group.members.first { it.uid == group.hostUid }
    val you = stringResource(R.string.group_you)
    val going = proposal.goingWith(host).map { if (it.uid == uiState.viewerUid) you else it.name }
    val proposer = group.members.firstOrNull { it.uid == proposal.byUid }
        ?.let { if (it.uid == uiState.viewerUid) you else it.name }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(
                modifier = Modifier.semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.group_plan_next),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
                proposer?.let {
                    Text(
                        text = stringResource(R.string.group_plan_proposed_by, it),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ReconveneTheme.colors.inkMuted,
                    )
                }
            }
            Detail(stringResource(R.string.pod_detail_when), whenText(proposal, uiState))
            Detail(
                stringResource(R.string.pod_detail_where),
                stringResource(R.string.pod_place, proposal.building, proposal.meetingPoint),
            )
            Column(
                modifier = Modifier.semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Label(stringResource(R.string.pod_detail_vibe))
                Tag(stringResource(proposal.mode.labelRes), accent = true)
            }
            Detail(
                pluralStringResource(R.plurals.group_plan_can_come, going.size, going.size),
                going.joinToString(", "),
            )
            if (!uiState.isReadOnly) {
                if (uiState.isHost) {
                    HostScheduling(uiState, onSchedule)
                } else {
                    VoteButtons(myVote = uiState.myVote, enabled = !uiState.isBusy, onVote = onVote)
                }
            }
        }
    }
}

@Composable
private fun whenText(proposal: SessionProposal, uiState: GroupUiState.Content, zone: ZoneId = ZoneId.systemDefault()): String {
    val start = proposal.startsAt.atZone(zone)
    return stringResource(
        R.string.create_when_range,
        dayLabel(start.toLocalDate(), uiState.now.atZone(zone).toLocalDate()),
        shortTime.format(start),
        shortTime.format(proposal.endsAt.atZone(zone)),
    )
}

@Composable
private fun Detail(label: String, value: String) {
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Label(label)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun Label(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelLarge, color = ReconveneTheme.colors.inkMuted)
}

// The chosen answer is the filled button, with a check, and says so to TalkBack.
@Composable
private fun VoteButtons(myVote: Boolean?, enabled: Boolean, onVote: (Boolean) -> Unit) {
    val chosen = stringResource(R.string.group_plan_your_answer)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(true to R.string.group_plan_yes, false to R.string.group_plan_no).forEach { (canCome, label) ->
            val isChosen = myVote == canCome
            val buttonModifier = Modifier
                .heightIn(min = 48.dp)
                .semantics { if (isChosen) stateDescription = chosen }
            val content: @Composable () -> Unit = {
                if (isChosen) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(18.dp),
                    )
                }
                Text(stringResource(label))
            }
            if (isChosen) {
                Button(onClick = { onVote(canCome) }, enabled = enabled, modifier = buttonModifier) { content() }
            } else {
                OutlinedButton(onClick = { onVote(canCome) }, enabled = enabled, modifier = buttonModifier) { content() }
            }
        }
    }
}

@Composable
private fun HostScheduling(uiState: GroupUiState.Content, onSchedule: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.group_plan_host_going),
            style = MaterialTheme.typography.bodyMedium,
            color = ReconveneTheme.colors.inkMuted,
        )
        Button(
            onClick = onSchedule,
            enabled = uiState.canSchedule && !uiState.isBusy,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.group_plan_schedule))
        }
        Text(
            text = if (uiState.canSchedule) {
                stringResource(R.string.group_plan_schedule_ready)
            } else {
                stringResource(R.string.group_plan_schedule_hint, SCHEDULE_YES_MIN)
            },
            style = MaterialTheme.typography.bodySmall,
            color = ReconveneTheme.colors.inkMuted,
        )
    }
}
