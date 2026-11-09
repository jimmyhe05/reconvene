package com.reconvene.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
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
import com.reconvene.data.remote.samplePods
import com.reconvene.ui.components.EmptyState
import com.reconvene.ui.theme.ReconveneTheme
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun DiscoverScreen(
    onCreatePod: () -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel = viewModel(factory = DiscoverViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    DiscoverContent(
        uiState = uiState,
        actions = PodListActions(
            onRetry = viewModel::retry,
            onCreatePod = onCreatePod,
            onSignIn = onSignIn,
            onJoin = viewModel::join,
            onLeave = viewModel::leave,
            onCancel = viewModel::cancel,
        ),
        message = message,
        onMessageShown = viewModel::messageShown,
        modifier = modifier,
    )
}

data class PodListActions(
    val onRetry: () -> Unit = {},
    val onCreatePod: () -> Unit = {},
    val onSignIn: () -> Unit = {},
    val onJoin: (podId: String) -> Unit = {},
    val onLeave: (podId: String) -> Unit = {},
    val onCancel: (podId: String) -> Unit = {},
)

@Composable
fun DiscoverContent(
    uiState: DiscoverUiState,
    actions: PodListActions,
    modifier: Modifier = Modifier,
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
    Box(modifier = modifier.fillMaxSize()) {
        PodList(uiState = uiState, actions = actions)
        if (uiState != DiscoverUiState.SignedOut) {
            CreatePodButton(actions.onCreatePod, Modifier.align(Alignment.BottomEnd))
        }
        // Above the Create pod button, so it doesn't cover it.
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp),
        )
    }
}

private val PodMessage.textRes: Int
    get() = when (this) {
        PodMessage.CANCELED -> R.string.pod_canceled
        PodMessage.FULL -> R.string.pod_error_full
        PodMessage.CLOSED -> R.string.pod_error_closed
        PodMessage.GONE -> R.string.pod_error_gone
        PodMessage.FAILED -> R.string.pod_error_failed
    }

@Composable
private fun CreatePodButton(onCreatePod: () -> Unit, modifier: Modifier) {
    ExtendedFloatingActionButton(
        onClick = onCreatePod,
        icon = {
            Icon(painterResource(R.drawable.ic_plus), contentDescription = stringResource(R.string.action_create_pod))
        },
        text = { Text(stringResource(R.string.action_create_pod)) },
        modifier = modifier.padding(16.dp),
    )
}

@Composable
private fun PodList(uiState: DiscoverUiState, actions: PodListActions) {
    val loadingLabel = stringResource(R.string.discover_loading)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "title") {
            Text(
                text = stringResource(R.string.discover_title),
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier
                    .padding(bottom = 4.dp)
                    .semantics { heading() },
            )
        }
        when (uiState) {
            DiscoverUiState.Loading -> item(key = "loading") {
                Column(
                    modifier = Modifier.clearAndSetSemantics { contentDescription = loadingLabel },
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PodCardPlaceholder()
                    PodCardPlaceholder()
                }
            }
            DiscoverUiState.Empty -> item(key = "empty") {
                EmptyState(
                    icon = R.drawable.ic_compass,
                    title = stringResource(R.string.discover_empty_title),
                    body = stringResource(R.string.discover_empty_body),
                    action = {
                        Button(onClick = actions.onCreatePod) {
                            Text(stringResource(R.string.action_create_pod))
                        }
                    },
                )
            }
            DiscoverUiState.Error -> item(key = "error") {
                EmptyState(
                    icon = R.drawable.ic_alert,
                    title = stringResource(R.string.discover_error_title),
                    body = stringResource(R.string.discover_error_body),
                    action = {
                        FilledTonalButton(onClick = actions.onRetry) {
                            Text(stringResource(R.string.action_retry))
                        }
                    },
                )
            }
            DiscoverUiState.SignedOut -> item(key = "signed-out") {
                EmptyState(
                    icon = R.drawable.ic_user,
                    title = stringResource(R.string.discover_signed_out_title),
                    body = stringResource(R.string.discover_signed_out_body),
                    action = {
                        Button(onClick = actions.onSignIn) {
                            Text(stringResource(R.string.action_sign_in))
                        }
                    },
                )
            }
            is DiscoverUiState.Content -> {
                val today = uiState.now.atZone(ZoneId.systemDefault()).toLocalDate()
                uiState.sections.forEach { section ->
                    item(key = "section-${section.heading}") { SectionHeader(section, today) }
                    items(section.pods, key = { it.id }) { pod ->
                        PodCard(
                            pod = pod,
                            now = uiState.now,
                            viewerUid = uiState.viewerUid,
                            isBusy = pod.id in uiState.busyPodIds,
                            onJoin = { actions.onJoin(pod.id) },
                            onLeave = { actions.onLeave(pod.id) },
                            onCancel = { actions.onCancel(pod.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(section: PodSection, today: LocalDate) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    ) {
        Text(
            text = sectionTitle(section.heading, today),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier
                .weight(1f)
                .alignByBaseline()
                .semantics { heading() },
        )
        val count = section.pods.size
        Text(
            text = pluralStringResource(R.plurals.section_pod_count, count, count),
            style = MaterialTheme.typography.bodySmall,
            color = ReconveneTheme.colors.inkSubtle,
            modifier = Modifier.alignByBaseline(),
        )
    }
}

@Composable
private fun sectionTitle(heading: SectionHeading, today: LocalDate): String = when (heading) {
    SectionHeading.NowAndSoon -> stringResource(R.string.section_now_and_soon)
    SectionHeading.LaterToday -> stringResource(R.string.section_later_today)
    SectionHeading.Tomorrow -> stringResource(R.string.day_tomorrow)
    is SectionHeading.Day -> dayLabel(heading.date, today)
}

private class DiscoverStateProvider : PreviewParameterProvider<DiscoverUiState> {
    private val clock = Clock.fixed(Instant.parse("2026-10-23T18:00:00Z"), ZoneId.of("America/New_York"))

    override val values = sequenceOf(
        DiscoverUiState.Content(
            sections = groupIntoSections(samplePods(clock), clock.instant(), clock.zone),
            now = clock.instant(),
        ),
        DiscoverUiState.Loading,
        DiscoverUiState.Empty,
        DiscoverUiState.Error,
        DiscoverUiState.SignedOut,
    )
}

@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun DiscoverContentPreview(
    @PreviewParameter(DiscoverStateProvider::class) uiState: DiscoverUiState,
) {
    ReconveneTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            DiscoverContent(uiState = uiState, actions = PodListActions())
        }
    }
}
