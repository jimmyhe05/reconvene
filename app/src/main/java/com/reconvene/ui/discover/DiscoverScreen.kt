package com.reconvene.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.reconvene.R
import com.reconvene.data.model.PodMode
import com.reconvene.data.remote.samplePods
import com.reconvene.ui.components.EmptyState
import com.reconvene.ui.pod.PodMessage
import com.reconvene.ui.theme.ReconveneTheme
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun DiscoverScreen(
    onCreatePod: () -> Unit,
    onSignIn: () -> Unit,
    onOpenPod: (podId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel = viewModel(factory = DiscoverViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    // The search field owns its text, so typing never waits on the ViewModel; each change is passed on.
    val search = rememberTextFieldState()
    LaunchedEffect(search) {
        snapshotFlow { search.text.toString() }.collect { query -> viewModel.updateFilter { it.copy(query = query) } }
    }
    DiscoverContent(
        uiState = uiState,
        actions = PodListActions(
            onRetry = viewModel::retry,
            onCreatePod = onCreatePod,
            onSignIn = onSignIn,
            onOpenPod = onOpenPod,
            onJoin = viewModel::join,
            onLeave = viewModel::leave,
            onCancel = viewModel::cancel,
            onFilterChange = viewModel::updateFilter,
            onClearFilter = viewModel::clearFilter,
        ),
        search = search,
        message = message,
        onMessageShown = viewModel::messageShown,
        modifier = modifier,
    )
}

data class PodListActions(
    val onRetry: () -> Unit = {},
    val onCreatePod: () -> Unit = {},
    val onSignIn: () -> Unit = {},
    val onOpenPod: (podId: String) -> Unit = {},
    val onJoin: (podId: String) -> Unit = {},
    val onLeave: (podId: String) -> Unit = {},
    val onCancel: (podId: String) -> Unit = {},
    val onFilterChange: (change: (PodFilter) -> PodFilter) -> Unit = {},
    val onClearFilter: () -> Unit = {},
)

@Composable
fun DiscoverContent(
    uiState: DiscoverUiState,
    actions: PodListActions,
    modifier: Modifier = Modifier,
    search: TextFieldState = rememberTextFieldState(),
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
        PodList(uiState = uiState, actions = actions, search = search)
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
private fun PodList(uiState: DiscoverUiState, actions: PodListActions, search: TextFieldState) {
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
                item(key = "search") { SearchField(search) }
                item(key = "filters") { FilterChips(uiState.filter, actions.onFilterChange) }
                if (uiState.filter.isActive && uiState.podCount > 0) {
                    item(key = "match-count") { MatchCount(uiState.podCount) }
                }
                if (uiState.podCount == 0) {
                    item(key = "no-matches") {
                        EmptyState(
                            icon = R.drawable.ic_search,
                            title = stringResource(R.string.discover_no_matches_title),
                            body = stringResource(R.string.discover_no_matches_body),
                            action = {
                                OutlinedButton(
                                    onClick = {
                                        search.clearText()
                                        actions.onClearFilter()
                                    },
                                ) {
                                    Text(stringResource(R.string.action_clear_filters))
                                }
                            },
                        )
                    }
                }
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
                            onOpen = { actions.onOpenPod(pod.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(state: TextFieldState) {
    val keyboard = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        state = state,
        label = { Text(stringResource(R.string.discover_search)) },
        placeholder = { Text(stringResource(R.string.discover_search_placeholder)) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
        trailingIcon = if (state.text.isNotEmpty()) {
            {
                IconButton(onClick = { state.clearText() }) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.discover_search_clear))
                }
            }
        } else {
            null
        },
        inputTransformation = InputTransformation.maxLength(SEARCH_MAX),
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        // The list already updates as you type, so Search only puts the keyboard away.
        onKeyboardAction = { keyboard?.hide() },
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.fillMaxWidth(),
    )
}

// Each chip flips its own part of the newest filter, so quick taps can't undo each other.
// The chips wrap onto more lines instead of scrolling sideways, so none are hidden off screen.
@Composable
private fun FilterChips(filter: PodFilter, onFilterChange: ((PodFilter) -> PodFilter) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterToggle(stringResource(R.string.discover_filter_open_spots), filter.openSpots) {
            onFilterChange { it.copy(openSpots = !it.openSpots) }
        }
        FilterToggle(stringResource(R.string.day_today), filter.today) {
            onFilterChange { it.copy(today = !it.today) }
        }
        FilterToggle(stringResource(R.string.discover_filter_mine), filter.mine) {
            onFilterChange { it.copy(mine = !it.mine) }
        }
        PodMode.entries.forEach { mode ->
            FilterToggle(stringResource(mode.labelRes), mode in filter.modes) {
                onFilterChange { it.withMode(mode, on = mode !in it.modes) }
            }
        }
    }
}

@Composable
private fun FilterToggle(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(painterResource(R.drawable.ic_check), contentDescription = null) }
        } else {
            null
        },
    )
}

@Composable
private fun MatchCount(count: Int) {
    Text(
        text = pluralStringResource(R.plurals.discover_match_count, count, count),
        style = MaterialTheme.typography.bodySmall,
        color = ReconveneTheme.colors.inkMuted,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

private const val SEARCH_MAX = 60

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
        DiscoverUiState.Content(sections = emptyList(), now = clock.instant(), filter = PodFilter(query = "physics")),
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
