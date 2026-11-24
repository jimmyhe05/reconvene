package com.reconvene.ui.group

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reconvene.R
import com.reconvene.data.auth.LOCAL_USER
import com.reconvene.data.model.GroupMessage
import com.reconvene.data.model.MESSAGE_MAX
import com.reconvene.data.model.MessageStatus
import com.reconvene.data.model.ReportReason
import com.reconvene.data.remote.sampleGroups
import com.reconvene.ui.components.EmptyState
import com.reconvene.ui.create.CreatePodForm
import com.reconvene.ui.discover.dayLabel
import com.reconvene.ui.theme.ReconveneTheme
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val shortTime: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

// Show the counter once a message gets this close to the limit.
private const val COUNTER_FROM = MESSAGE_MAX - 50

@Composable
fun GroupScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GroupViewModel = viewModel(factory = GroupViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    GroupContent(
        uiState = uiState,
        onBack = onBack,
        onRetrySync = viewModel::retrySync,
        onSend = viewModel::send,
        onRetryMessage = viewModel::retry,
        onReport = viewModel::report,
        onBlock = viewModel::block,
        onVote = viewModel::vote,
        onSchedule = viewModel::schedule,
        onPropose = viewModel::propose,
        notice = notice,
        onNoticeShown = viewModel::noticeShown,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupContent(
    uiState: GroupUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onRetrySync: () -> Unit = {},
    onSend: (String) -> Unit = {},
    onRetryMessage: (messageId: String) -> Unit = {},
    onReport: (messageId: String, ReportReason) -> Unit = { _, _ -> },
    onBlock: (uid: String) -> Unit = {},
    onVote: (canCome: Boolean) -> Unit = {},
    onSchedule: () -> Unit = {},
    onPropose: (CreatePodForm) -> Unit = {},
    notice: GroupNotice? = null,
    onNoticeShown: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val noticeText = notice?.let { stringResource(it.textRes) }
    LaunchedEffect(notice) {
        if (noticeText != null) {
            snackbarHostState.showSnackbar(noticeText)
            onNoticeShown()
        }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        TopAppBar(
            title = {
                Text(
                    text = (uiState as? GroupUiState.Content)?.group?.name ?: stringResource(R.string.group_title),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                }
            },
        )
        Box(modifier = Modifier.weight(1f)) {
            when (uiState) {
                GroupUiState.Loading -> Loading()
                GroupUiState.Gone -> CenteredState {
                    EmptyState(
                        icon = R.drawable.ic_users,
                        title = stringResource(R.string.group_gone_title),
                        body = stringResource(R.string.group_gone_body),
                        action = { BackToGroups(onBack) },
                    )
                }
                GroupUiState.Error -> CenteredState {
                    EmptyState(
                        icon = R.drawable.ic_alert,
                        title = stringResource(R.string.group_error_title),
                        body = stringResource(R.string.discover_error_body),
                        action = { BackToGroups(onBack) },
                    )
                }
                is GroupUiState.Content -> {
                    var tab by rememberSaveable { mutableStateOf(GroupTab.CHAT) }
                    // Kept above the tabs, so checking the Plan tab doesn't throw away a half-written message.
                    var draft by rememberSaveable { mutableStateOf("") }
                    Column(Modifier.fillMaxSize()) {
                        GroupTabRow(tab = tab, needsYourVote = uiState.needsYourVote, onSelect = { tab = it })
                        when (tab) {
                            GroupTab.CHAT -> Chat(uiState, draft, { draft = it }, onRetrySync, onSend, onRetryMessage, onReport, onBlock)
                            GroupTab.PLAN -> GroupPlan(uiState, onVote, onSchedule, onPropose)
                        }
                    }
                }
            }
            SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp))
        }
    }
}

private enum class GroupTab { CHAT, PLAN }

// Tabs, not one long page, so the chat keeps its height at large font sizes. The Plan tab shows a
// dot while there's a session waiting for the viewer's answer.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupTabRow(tab: GroupTab, needsYourVote: Boolean, onSelect: (GroupTab) -> Unit) {
    val planLabel = stringResource(if (needsYourVote) R.string.group_tab_plan_needs_vote else R.string.group_tab_plan)
    PrimaryTabRow(selectedTabIndex = tab.ordinal) {
        Tab(
            selected = tab == GroupTab.CHAT,
            onClick = { onSelect(GroupTab.CHAT) },
            text = { Text(stringResource(R.string.group_tab_chat)) },
        )
        Tab(
            selected = tab == GroupTab.PLAN,
            onClick = { onSelect(GroupTab.PLAN) },
            modifier = Modifier.semantics { contentDescription = planLabel },
            text = {
                BadgedBox(badge = { if (needsYourVote) Badge() }) {
                    Text(stringResource(R.string.group_tab_plan))
                }
            },
        )
    }
}

@Composable
private fun Loading() {
    val label = stringResource(R.string.group_loading)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.semantics { contentDescription = label })
    }
}

@Composable
private fun CenteredState(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun BackToGroups(onBack: () -> Unit) {
    OutlinedButton(onClick = onBack) {
        Text(stringResource(R.string.group_back))
    }
}

@Composable
private fun Chat(
    uiState: GroupUiState.Content,
    draft: String,
    onDraftChange: (String) -> Unit,
    onRetrySync: () -> Unit,
    onSend: (String) -> Unit,
    onRetryMessage: (messageId: String) -> Unit,
    onReport: (messageId: String, ReportReason) -> Unit,
    onBlock: (uid: String) -> Unit,
) {
    // The message being reported or whose author is being blocked, by id, so it survives rotation.
    var reportingId by rememberSaveable { mutableStateOf<String?>(null) }
    var blockingId by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        if (uiState.chatSyncFailed) SyncFailedBanner(onRetrySync)
        if (uiState.messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.group_chat_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = ReconveneTheme.colors.inkMuted,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            // Reversed, so the list starts at the newest message and stays there as new ones arrive.
            LazyColumn(
                modifier = Modifier.weight(1f),
                reverseLayout = true,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(uiState.messages.asReversed(), key = { it.id }) { message ->
                    MessageRow(
                        message = message,
                        isMine = message.authorUid == uiState.viewerUid,
                        now = uiState.now,
                        onRetry = { onRetryMessage(message.id) },
                        onReport = { reportingId = message.id },
                        onBlock = { blockingId = message.id },
                    )
                }
            }
        }
        if (uiState.isReadOnly) ReadOnlyBanner() else Composer(draft, onDraftChange, onSend)
    }
    uiState.messages.firstOrNull { it.id == reportingId }?.let { message ->
        ReportDialog(
            authorName = message.authorName,
            onReport = { reason ->
                onReport(message.id, reason)
                reportingId = null
            },
            onDismiss = { reportingId = null },
        )
    }
    uiState.messages.firstOrNull { it.id == blockingId }?.let { message ->
        BlockDialog(
            authorName = message.authorName,
            onBlock = {
                onBlock(message.authorUid)
                blockingId = null
            },
            onDismiss = { blockingId = null },
        )
    }
}

@Composable
private fun SyncFailedBanner(onRetry: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.group_chat_sync_failed),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.action_retry))
            }
        }
    }
}

// The bubble, author, time and status are read as one item. Retry and the menu for other people's
// messages sit outside it as their own buttons.
@Composable
private fun MessageRow(
    message: GroupMessage,
    isMine: Boolean,
    now: Instant,
    onRetry: () -> Unit,
    onReport: () -> Unit,
    onBlock: () -> Unit,
) {
    val author = if (isMine) stringResource(R.string.group_you) else message.authorName
    val status = when (message.status) {
        MessageStatus.SENDING -> stringResource(R.string.group_sending)
        MessageStatus.FAILED -> stringResource(R.string.group_not_sent)
        MessageStatus.SENT -> null
    }
    val meta = listOfNotNull(messageTime(message.sentAt, now), status).joinToString(" · ")
    val label = stringResource(R.string.group_message_label, author, message.text, meta)
    val alignment = if (isMine) Alignment.End else Alignment.Start
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (isMine) 48.dp else 0.dp),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
    ) {
        Column(Modifier.weight(1f, fill = false), horizontalAlignment = alignment) {
            Column(
                modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = label },
                horizontalAlignment = alignment,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Surface(
                    shape = bubbleShape(isMine),
                    color = if (isMine) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        if (!isMine) {
                            Text(
                                text = message.authorName,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Text(message.text, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (message.status == MessageStatus.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        ReconveneTheme.colors.inkMuted
                    },
                )
            }
            if (message.status == MessageStatus.FAILED) {
                val retryLabel = stringResource(R.string.group_retry_label, message.text.take(40))
                TextButton(onClick = onRetry, modifier = Modifier.semantics { contentDescription = retryLabel }) {
                    Text(stringResource(R.string.group_retry))
                }
            }
        }
        if (!isMine) MessageMenu(message.authorName, onReport, onBlock)
    }
}

private fun bubbleShape(isMine: Boolean) = RoundedCornerShape(
    topStart = 18.dp,
    topEnd = 18.dp,
    bottomEnd = if (isMine) 4.dp else 18.dp,
    bottomStart = if (isMine) 18.dp else 4.dp,
)

// Just the time today, with the day before that.
@Composable
private fun messageTime(sentAt: Instant, now: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
    val sent = sentAt.atZone(zone)
    val today = now.atZone(zone).toLocalDate()
    val time = shortTime.format(sent)
    return if (sent.toLocalDate() == today) {
        time
    } else {
        stringResource(R.string.group_message_day_time, dayLabel(sent.toLocalDate(), today), time)
    }
}

@Composable
private fun Composer(draft: String, onDraftChange: (String) -> Unit, onSend: (String) -> Unit) {
    Column {
        HorizontalDivider()
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { onDraftChange(it.take(MESSAGE_MAX)) },
                label = { Text(stringResource(R.string.group_message_field)) },
                supportingText = if (draft.length >= COUNTER_FROM) {
                    { Text(stringResource(R.string.create_counter, draft.length, MESSAGE_MAX)) }
                } else {
                    null
                },
                maxLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    onSend(draft)
                    onDraftChange("")
                },
                enabled = draft.isNotBlank(),
            ) {
                Icon(painterResource(R.drawable.ic_send), contentDescription = stringResource(R.string.group_send))
            }
        }
    }
}

@Composable
private fun ReadOnlyBanner() {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(
            text = stringResource(R.string.group_read_only),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
    }
}

private val previewClock = Clock.fixed(Instant.parse("2026-11-17T15:00:00Z"), ZoneId.of("America/New_York"))

private class GroupPreviewStates : PreviewParameterProvider<GroupUiState> {
    private val now = previewClock.instant()
    private val group = sampleGroups(previewClock).first()
    private fun message(id: String, uid: String, name: String, text: String, minutesAgo: Long, status: MessageStatus) =
        GroupMessage(id, group.id, uid, name, text, now.minus(Duration.ofMinutes(minutesAgo)), status)

    private val messages = listOf(
        message("m1", "sample-maya", "Maya", "I proposed a time for the next session. Vote in the plan.", 90, MessageStatus.SENT),
        message("m2", LOCAL_USER.uid, "You", "Works for me.", 30, MessageStatus.SENT),
        message("m3", "sample-jordan", "Jordan", "Can someone share the practice set?", 20, MessageStatus.SENT),
        message("m4", LOCAL_USER.uid, "You", "Start with problems 3 and 4.", 2, MessageStatus.FAILED),
        message("m5", LOCAL_USER.uid, "You", "I'll bring the slides.", 0, MessageStatus.SENDING),
    )

    override val values = sequenceOf(
        GroupUiState.Content(group, messages, LOCAL_USER.uid, now),
        GroupUiState.Content(group, messages, LOCAL_USER.uid, group.archivesAt, chatSyncFailed = true),
        GroupUiState.Content(group, emptyList(), LOCAL_USER.uid, now),
        GroupUiState.Gone,
    )
}

@Preview(showBackground = true)
@Composable
private fun GroupContentPreview(@PreviewParameter(GroupPreviewStates::class) uiState: GroupUiState) {
    ReconveneTheme {
        // A Surface, like the app's Scaffold, so text gets the theme's color in dark mode.
        Surface(color = MaterialTheme.colorScheme.background) {
            GroupContent(uiState = uiState, onBack = {})
        }
    }
}
