package com.reconvene.ui.groups

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reconvene.R
import com.reconvene.data.auth.LOCAL_USER
import com.reconvene.data.model.Group
import com.reconvene.data.model.SEMESTER_END
import com.reconvene.data.remote.sampleGroups
import com.reconvene.ui.components.EmptyState
import com.reconvene.ui.components.Tag
import com.reconvene.ui.theme.ReconveneTheme
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun GroupsScreen(
    onSignIn: () -> Unit,
    onOpenGroup: (groupId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GroupsViewModel = viewModel(factory = GroupsViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    GroupsContent(
        uiState = uiState,
        onSignIn = onSignIn,
        onOpenGroup = onOpenGroup,
        onRetry = viewModel::retry,
        onAccept = viewModel::accept,
        onDecline = viewModel::decline,
        message = message,
        onMessageShown = viewModel::messageShown,
        modifier = modifier,
    )
}

@Composable
fun GroupsContent(
    uiState: GroupsUiState,
    modifier: Modifier = Modifier,
    onSignIn: () -> Unit = {},
    onOpenGroup: (groupId: String) -> Unit = {},
    onRetry: () -> Unit = {},
    onAccept: (groupId: String) -> Unit = {},
    onDecline: (groupId: String) -> Unit = {},
    message: GroupNotice? = null,
    onMessageShown: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val messageText = message?.let { stringResource(it.textRes) }
    LaunchedEffect(message) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            onMessageShown()
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "title") {
                Text(
                    text = stringResource(R.string.nav_groups),
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .semantics { heading() },
                )
            }
            when (uiState) {
                GroupsUiState.Loading -> item(key = "loading") { Loading() }
                GroupsUiState.SignedOut -> item(key = "signed-out") {
                    EmptyState(
                        icon = R.drawable.ic_user,
                        title = stringResource(R.string.groups_signed_out_title),
                        body = stringResource(R.string.groups_signed_out_body),
                        action = {
                            Button(shape = MaterialTheme.shapes.small, onClick = onSignIn) {
                                Text(stringResource(R.string.action_sign_in))
                            }
                        },
                    )
                }
                GroupsUiState.Empty -> item(key = "empty") {
                    EmptyState(
                        icon = R.drawable.ic_users,
                        title = stringResource(R.string.groups_empty_title),
                        body = stringResource(R.string.groups_empty_body),
                    )
                }
                GroupsUiState.Error -> item(key = "error") {
                    EmptyState(
                        icon = R.drawable.ic_alert,
                        title = stringResource(R.string.groups_error_title),
                        body = stringResource(R.string.discover_error_body),
                        action = {
                            FilledTonalButton(shape = MaterialTheme.shapes.small, onClick = onRetry) {
                                Text(stringResource(R.string.action_retry))
                            }
                        },
                    )
                }
                is GroupsUiState.Content -> {
                    if (uiState.invites.isNotEmpty()) {
                        item(key = "invites-title") { SectionTitle(stringResource(R.string.groups_invites)) }
                        items(uiState.invites, key = { "invite-${it.id}" }) { group ->
                            InviteCard(
                                group = group,
                                isBusy = group.id in uiState.busyGroupIds,
                                onAccept = { onAccept(group.id) },
                                onDecline = { onDecline(group.id) },
                            )
                        }
                    }
                    if (uiState.groups.isNotEmpty()) {
                        item(key = "groups-title") { SectionTitle(stringResource(R.string.groups_yours)) }
                        items(uiState.groups, key = { "group-${it.id}" }) { group ->
                            GroupCard(
                                group = group,
                                isHost = group.hostUid == uiState.viewerUid,
                                onOpen = { onOpenGroup(group.id) },
                            )
                        }
                    }
                    item(key = "private-note") { PrivateNote(uiState.groups.firstOrNull()?.archivesAt ?: SEMESTER_END) }
                }
            }
        }
        SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

@Composable
private fun Loading() {
    val label = stringResource(R.string.groups_loading)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(Modifier.semantics { contentDescription = label })
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier
            .padding(top = 8.dp)
            .semantics { heading() },
    )
}

// Accept and Decline name the group, so TalkBack users know which invite each button answers.
@Composable
private fun InviteCard(group: Group, isBusy: Boolean, onAccept: () -> Unit, onDecline: () -> Unit) {
    val acceptLabel = stringResource(R.string.groups_accept_label, group.name)
    val declineLabel = stringResource(R.string.groups_decline_label, group.name)
    GroupSurface {
        Column(
            modifier = Modifier.semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            group.course?.let { Tag(it) }
            Text(group.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.groups_invited_by, group.hostName, memberCount(group)),
                style = MaterialTheme.typography.bodyMedium,
                color = ReconveneTheme.colors.inkMuted,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                shape = MaterialTheme.shapes.small,
                onClick = onAccept,
                enabled = !isBusy,
                modifier = Modifier.semantics { contentDescription = acceptLabel },
            ) {
                Text(stringResource(R.string.groups_accept))
            }
            TextButton(
                onClick = onDecline,
                enabled = !isBusy,
                modifier = Modifier.semantics { contentDescription = declineLabel },
            ) {
                Text(stringResource(R.string.groups_decline))
            }
        }
    }
}

// Tapping anywhere on the card opens the group. Invites can't be opened until they're accepted.
@Composable
private fun GroupCard(group: Group, isHost: Boolean, onOpen: () -> Unit) {
    val openLabel = stringResource(R.string.group_open)
    GroupSurface(
        Modifier
            .clip(MaterialTheme.shapes.large)
            .clickable(onClickLabel = openLabel, onClick = onOpen)
            .semantics(mergeDescendants = true) {},
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                group.course?.let { Tag(it) }
                Text(group.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = memberCount(group),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ReconveneTheme.colors.inkMuted,
                )
            }
            if (isHost) Tag(stringResource(R.string.groups_host), accent = true)
        }
    }
}

@Composable
private fun memberCount(group: Group): String =
    pluralStringResource(R.plurals.groups_members, group.members.size, group.members.size)

@Composable
private fun GroupSurface(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun PrivateNote(archivesAt: Instant) {
    val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(archivesAt.atZone(ZoneId.systemDefault()))
    Text(
        text = stringResource(R.string.groups_private_note, date),
        style = MaterialTheme.typography.bodySmall,
        color = ReconveneTheme.colors.inkMuted,
        modifier = Modifier.padding(top = 8.dp),
    )
}

private val previewClock = Clock.fixed(Instant.parse("2026-11-17T15:00:00Z"), ZoneId.of("America/New_York"))

private class GroupsPreviewStates : PreviewParameterProvider<GroupsUiState> {
    private val groups = sampleGroups(previewClock)
    override val values = sequenceOf(
        GroupsUiState.Content(
            invites = groups.filter { it.isInvited(LOCAL_USER.uid) },
            groups = groups.filter { it.isMember(LOCAL_USER.uid) },
            viewerUid = LOCAL_USER.uid,
        ),
        GroupsUiState.Empty,
        GroupsUiState.SignedOut,
    )
}

@Preview(showBackground = true)
@Composable
private fun GroupsContentPreview(@PreviewParameter(GroupsPreviewStates::class) uiState: GroupsUiState) {
    ReconveneTheme {
        // A Surface, like the app's Scaffold, so text gets the theme's color in dark mode.
        Surface(color = MaterialTheme.colorScheme.background) {
            GroupsContent(uiState = uiState)
        }
    }
}
