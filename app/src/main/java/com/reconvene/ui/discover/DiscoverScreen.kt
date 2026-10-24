package com.reconvene.ui.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
import com.reconvene.data.repository.samplePods
import com.reconvene.ui.components.EmptyState
import com.reconvene.ui.theme.ReconveneTheme
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun DiscoverScreen(
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel = viewModel(factory = DiscoverViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DiscoverContent(uiState = uiState, onRetry = viewModel::retry, modifier = modifier)
}

@Composable
fun DiscoverContent(
    uiState: DiscoverUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val loadingLabel = stringResource(R.string.discover_loading)
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
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
                )
            }
            DiscoverUiState.Error -> item(key = "error") {
                EmptyState(
                    icon = R.drawable.ic_alert,
                    title = stringResource(R.string.discover_error_title),
                    body = stringResource(R.string.discover_error_body),
                    action = {
                        FilledTonalButton(onClick = onRetry) {
                            Text(stringResource(R.string.action_retry))
                        }
                    },
                )
            }
            is DiscoverUiState.Content -> {
                val today = uiState.now.atZone(ZoneId.systemDefault()).toLocalDate()
                uiState.sections.forEach { section ->
                    item(key = "section-${section.heading}") { SectionHeader(section, today) }
                    items(section.pods, key = { it.id }) { pod -> PodCard(pod, uiState.now) }
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
    )
}

@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun DiscoverContentPreview(
    @PreviewParameter(DiscoverStateProvider::class) uiState: DiscoverUiState,
) {
    ReconveneTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            DiscoverContent(uiState = uiState, onRetry = {})
        }
    }
}
