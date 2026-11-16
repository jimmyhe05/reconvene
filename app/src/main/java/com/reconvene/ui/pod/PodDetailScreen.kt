package com.reconvene.ui.pod

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
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
import com.reconvene.data.model.Pod
import com.reconvene.data.model.PodMember
import com.reconvene.data.remote.samplePods
import com.reconvene.ui.components.EmptyState
import com.reconvene.ui.components.Tag
import com.reconvene.ui.discover.PodBadges
import com.reconvene.ui.discover.hintRes
import com.reconvene.ui.discover.labelRes
import com.reconvene.ui.discover.podTimeText
import com.reconvene.ui.theme.ReconveneTheme
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@Composable
fun PodDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PodDetailViewModel = viewModel(factory = PodDetailViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    PodDetailContent(
        uiState = uiState,
        onBack = onBack,
        onRetry = viewModel::retry,
        onJoin = viewModel::join,
        onLeave = viewModel::leave,
        onCancel = viewModel::cancel,
        message = message,
        onMessageShown = viewModel::messageShown,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodDetailContent(
    uiState: PodDetailUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    onJoin: () -> Unit = {},
    onLeave: () -> Unit = {},
    onCancel: () -> Unit = {},
    message: PodMessage? = null,
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
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.pod_detail_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
                }
            },
        )
        Box(modifier = Modifier.weight(1f)) {
            when (uiState) {
                PodDetailUiState.Loading -> Loading()
                PodDetailUiState.Error -> CenteredState {
                    EmptyState(
                        icon = R.drawable.ic_alert,
                        title = stringResource(R.string.pod_detail_error_title),
                        body = stringResource(R.string.discover_error_body),
                        action = {
                            FilledTonalButton(onClick = onRetry) {
                                Text(stringResource(R.string.action_retry))
                            }
                        },
                    )
                }
                is PodDetailUiState.Gone -> CenteredState {
                    EmptyState(
                        icon = R.drawable.ic_calendar,
                        title = stringResource(
                            if (uiState.canceledByYou) R.string.pod_detail_canceled_title else R.string.pod_detail_gone_title,
                        ),
                        body = stringResource(
                            if (uiState.canceledByYou) R.string.pod_detail_canceled_body else R.string.pod_detail_gone_body,
                        ),
                        action = {
                            OutlinedButton(onClick = onBack) {
                                Text(stringResource(R.string.pod_detail_back))
                            }
                        },
                    )
                }
                is PodDetailUiState.Content -> PodDetails(uiState, onJoin, onLeave, onCancel)
            }
            SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
        }
    }
}

@Composable
private fun Loading() {
    val label = stringResource(R.string.pod_detail_loading)
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

// The details scroll, and the buttons stay pinned below them so Join is always in reach.
@Composable
private fun PodDetails(
    uiState: PodDetailUiState.Content,
    onJoin: () -> Unit,
    onLeave: () -> Unit,
    onCancel: () -> Unit,
) {
    val pod = uiState.pod
    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    pod.course?.let { Tag(it) }
                    Spacer(Modifier.weight(1f))
                    PodBadges(pod, uiState.now)
                }
                Text(
                    text = pod.title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Detail(stringResource(R.string.pod_detail_when), podTimeText(pod, uiState.now))
                Detail(stringResource(R.string.pod_detail_where), stringResource(R.string.pod_place, pod.building, pod.meetingPoint))
                Detail(
                    label = stringResource(R.string.pod_detail_vibe),
                    value = stringResource(pod.mode.labelRes),
                    hint = stringResource(pod.mode.hintRes),
                )
            }
            WhoIsGoing(pod, uiState.viewerUid)
        }
        if (uiState.viewerUid != null) {
            HorizontalDivider()
            Box(Modifier.padding(16.dp)) {
                PodActions(pod, uiState.now, uiState.viewerUid, uiState.isBusy, onJoin, onLeave, onCancel)
            }
        }
    }
}

@Composable
private fun Detail(label: String, value: String, hint: String? = null) {
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = ReconveneTheme.colors.inkMuted)
        Text(value, style = MaterialTheme.typography.bodyLarge)
        hint?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = ReconveneTheme.colors.inkMuted) }
    }
}

@Composable
private fun WhoIsGoing(pod: Pod, viewerUid: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.pod_detail_going),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            Text(
                text = stringResource(R.string.pod_detail_going_count, pod.members.size, pod.capacity),
                style = MaterialTheme.typography.bodyMedium,
                color = ReconveneTheme.colors.inkMuted,
            )
        }
        pod.members.forEach { member ->
            key(member.uid) {
                MemberRow(member, isHost = member.uid == pod.hostUid, isYou = member.uid == viewerUid)
            }
        }
    }
}

@Composable
private fun MemberRow(member: PodMember, isHost: Boolean, isYou: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Initials(member.name)
        Text(member.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (isYou) Tag(stringResource(R.string.pod_detail_you))
        if (isHost) Tag(stringResource(R.string.pod_detail_host), accent = true)
    }
}

// Initials keep one size at every font scale, so the circle never clips them. The name next to
// the circle is what TalkBack reads.
@Composable
private fun Initials(name: String) {
    val fontSize = with(LocalDensity.current) { 15.dp.toSp() }
    Surface(
        modifier = Modifier
            .size(40.dp)
            .clearAndSetSemantics {},
        shape = CircleShape,
        color = ReconveneTheme.colors.surfaceDim,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(initialsOf(name), style = MaterialTheme.typography.labelLarge.copy(fontSize = fontSize))
        }
    }
}

internal fun initialsOf(name: String): String =
    name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }

private val previewClock = Clock.fixed(Instant.parse("2026-11-16T19:00:00Z"), ZoneId.of("America/New_York"))

private class PodDetailPreviewStates : PreviewParameterProvider<PodDetailUiState> {
    override val values = sequenceOf(
        PodDetailUiState.Content(samplePods(previewClock).first(), previewClock.instant(), viewerUid = "sample-maya"),
        PodDetailUiState.Gone(canceledByYou = true),
        PodDetailUiState.Error,
    )
}

@Preview(showBackground = true)
@Composable
private fun PodDetailPreview(@PreviewParameter(PodDetailPreviewStates::class) uiState: PodDetailUiState) {
    ReconveneTheme {
        // A Surface, like the app's Scaffold, so text gets the theme's color in dark mode.
        Surface(color = MaterialTheme.colorScheme.background) {
            PodDetailContent(uiState = uiState, onBack = {})
        }
    }
}
