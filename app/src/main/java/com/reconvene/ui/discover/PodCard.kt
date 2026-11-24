package com.reconvene.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.reconvene.R
import com.reconvene.data.model.Pod
import com.reconvene.data.model.PodTiming
import com.reconvene.ui.components.BadgeKind
import com.reconvene.ui.components.StatusBadge
import com.reconvene.ui.pod.PodActions
import com.reconvene.ui.theme.ReconveneTheme
import com.reconvene.ui.theme.TimeStyles
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// One pod as a timetable row: start and end on the left in mono, then course and vibe, title, place,
// host and spots, with a rule underneath. The section above it says which day.
// viewerUid is null when nobody can join (no signed-in user), which hides the Join and Leave buttons.
// With onOpen, tapping anywhere outside those buttons opens the pod.
@Composable
fun PodCard(
    pod: Pod,
    now: Instant,
    modifier: Modifier = Modifier,
    viewerUid: String? = null,
    isBusy: Boolean = false,
    onJoin: () -> Unit = {},
    onLeave: () -> Unit = {},
    onCancel: () -> Unit = {},
    onOpen: (() -> Unit)? = null,
    walkMinutes: Int? = null,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val openLabel = stringResource(R.string.pod_detail_open)
    val opens = if (onOpen != null) Modifier.clickable(onClickLabel = openLabel, onClick = onOpen) else Modifier
    Column(modifier.fillMaxWidth().then(opens).semantics(mergeDescendants = true) {}) {
        Row(
            modifier = Modifier.padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TimeColumn(pod, zone)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                MetaLine(pod, now)
                Text(pod.title, style = MaterialTheme.typography.titleMedium)
                // "Starts in 20 min · 7:20 PM" or "On now, until 6:00 PM" when it's close; otherwise
                // the time column and the section heading say it all.
                if (pod.timing(now) == PodTiming.ONGOING || pod.startsAt < now.plus(soonWindow)) {
                    Text(podTimeText(pod, now, zone), style = MaterialTheme.typography.bodyMedium)
                }
                val place = stringResource(R.string.pod_place, pod.building, pod.meetingPoint)
                Text(
                    text = walkMinutes?.let { stringResource(R.string.pod_place_walk, place, it) } ?: place,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ReconveneTheme.colors.inkMuted,
                )
                Text(hostLine(pod), style = MaterialTheme.typography.bodySmall, color = ReconveneTheme.colors.inkMuted)
                SpotsLine(pod)
            }
        }
        if (viewerUid != null) {
            Box(Modifier.padding(start = TIME_COLUMN + 14.dp, bottom = 14.dp)) {
                PodActions(pod, now, viewerUid, isBusy, onJoin, onLeave, onCancel)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
fun PodCardPlaceholder(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.width(TIME_COLUMN), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SkeletonBar(widthFraction = 0.8f, height = 18.dp)
                SkeletonBar(widthFraction = 0.6f, height = 14.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SkeletonBar(widthFraction = 0.4f, height = 14.dp)
                SkeletonBar(widthFraction = 0.85f, height = 20.dp)
                SkeletonBar(widthFraction = 0.6f, height = 16.dp)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

// Status badges for the pod's detail screen.
@Composable
fun PodBadges(pod: Pod, now: Instant) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (pod.timing(now) == PodTiming.ONGOING) {
            StatusBadge(BadgeKind.LIVE, stringResource(R.string.badge_ongoing))
        }
        if (pod.isFull) {
            StatusBadge(BadgeKind.FULL, stringResource(R.string.badge_full))
        } else {
            StatusBadge(
                kind = BadgeKind.OPEN,
                text = pluralStringResource(R.plurals.badge_spots_left, pod.spotsLeft, pod.spotsLeft),
            )
        }
    }
}

@Composable
private fun TimeColumn(pod: Pod, zone: ZoneId) {
    Column(Modifier.width(TIME_COLUMN), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(START_TIME.format(pod.startsAt.atZone(zone)), style = TimeStyles.start)
        Text(END_TIME.format(pod.endsAt.atZone(zone)), style = TimeStyles.end, color = ReconveneTheme.colors.inkMuted)
    }
}

// "CS 330 · OPEN TO CHAT", led by an orange dot and "ONGOING" while it's running.
@Composable
private fun MetaLine(pod: Pod, now: Instant) {
    val ongoing = pod.timing(now) == PodTiming.ONGOING
    val parts = buildList {
        if (ongoing) add(stringResource(R.string.badge_ongoing))
        pod.course?.let { add(it) }
        add(stringResource(pod.mode.labelRes))
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (ongoing) {
            Box(Modifier.size(8.dp).background(ReconveneTheme.colors.live, CircleShape))
        }
        Text(
            text = parts.joinToString(" · ").uppercase(LocalConfiguration.current.locales[0]),
            style = TimeStyles.meta,
            color = ReconveneTheme.colors.inkMuted,
        )
    }
}

// Spots left, called out once only a couple remain.
@Composable
private fun SpotsLine(pod: Pod) {
    val colors = ReconveneTheme.colors
    if (pod.isFull) {
        Text(stringResource(R.string.badge_full), style = MaterialTheme.typography.bodySmall, color = colors.inkMuted)
    } else {
        Text(
            text = pluralStringResource(R.plurals.badge_spots_left, pod.spotsLeft, pod.spotsLeft),
            style = MaterialTheme.typography.bodySmall,
            color = if (pod.spotsLeft <= 2) colors.onLate else colors.inkMuted,
        )
    }
}

@Composable
private fun hostLine(pod: Pod): String =
    if (pod.memberNames.size > 3) {
        stringResource(R.string.pod_hosted_by_going, pod.hostName, pod.memberNames.size)
    } else {
        stringResource(R.string.pod_hosted_by, pod.hostName)
    }

@Composable
private fun SkeletonBar(widthFraction: Float, height: Dp) {
    Box(
        Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .background(ReconveneTheme.colors.surfaceDim, MaterialTheme.shapes.extraSmall)
    )
}

private val TIME_COLUMN = 60.dp
private val START_TIME = DateTimeFormatter.ofPattern("h:mm", Locale.US)
private val END_TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
