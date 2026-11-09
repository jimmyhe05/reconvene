package com.reconvene.ui.discover

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.reconvene.R
import com.reconvene.data.model.Pod
import com.reconvene.data.model.PodTiming
import com.reconvene.ui.components.BadgeKind
import com.reconvene.ui.components.StatusBadge
import com.reconvene.ui.components.Tag
import com.reconvene.ui.theme.ReconveneTheme
import java.time.Instant

// viewerUid is null when nobody can join (no signed-in user), which hides the Join and Leave buttons.
@Composable
fun PodCard(
    pod: Pod,
    now: Instant,
    modifier: Modifier = Modifier,
    viewerUid: String? = null,
    isBusy: Boolean = false,
    onJoin: () -> Unit = {},
    onLeave: () -> Unit = {},
) {
    PodCardSurface(modifier.semantics(mergeDescendants = true) {}) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            pod.course?.let { Tag(it) }
            Spacer(Modifier.weight(1f))
            PodBadges(pod, now)
        }
        Text(pod.title, style = MaterialTheme.typography.titleLarge)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MetaLine(R.drawable.ic_clock, podTimeText(pod, now))
            MetaLine(R.drawable.ic_pin, stringResource(R.string.pod_place, pod.building, pod.meetingPoint))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = hostLine(pod),
                style = MaterialTheme.typography.bodySmall,
                color = ReconveneTheme.colors.inkMuted,
                modifier = Modifier.weight(1f),
            )
            Tag(stringResource(pod.mode.labelRes), accent = true)
        }
        if (viewerUid != null) {
            Membership(pod, now, viewerUid, isBusy, onJoin, onLeave)
        }
    }
}

@Composable
private fun Membership(
    pod: Pod,
    now: Instant,
    viewerUid: String,
    isBusy: Boolean,
    onJoin: () -> Unit,
    onLeave: () -> Unit,
) {
    when {
        pod.hostUid == viewerUid -> MembershipNote(stringResource(R.string.pod_you_host))
        pod.hasMember(viewerUid) -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.pod_you_joined),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onLeave, enabled = !isBusy, modifier = Modifier.heightIn(min = 48.dp)) {
                ButtonLabel(R.string.pod_leave, R.string.pod_leaving, isBusy)
            }
        }
        pod.joinProblem(now) == null -> Button(
            onClick = onJoin,
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            ButtonLabel(R.string.pod_join, R.string.pod_joining, isBusy)
        }
        // A full pod already says so in its badge.
        !pod.isFull -> MembershipNote(stringResource(R.string.pod_join_closed))
    }
}

@Composable
private fun MembershipNote(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = ReconveneTheme.colors.inkMuted)
}

@Composable
private fun ButtonLabel(@StringRes idle: Int, @StringRes busy: Int, isBusy: Boolean) {
    if (isBusy) {
        val busyLabel = stringResource(busy)
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp).semantics { contentDescription = busyLabel },
            strokeWidth = 2.dp,
        )
    } else {
        Text(stringResource(idle))
    }
}

@Composable
fun PodCardPlaceholder(modifier: Modifier = Modifier) {
    PodCardSurface(modifier) {
        SkeletonBar(widthFraction = 0.4f, height = 24.dp)
        SkeletonBar(widthFraction = 0.85f, height = 26.dp)
        SkeletonBar(widthFraction = 0.6f, height = 20.dp)
        SkeletonBar(widthFraction = 0.7f, height = 32.dp)
    }
}

@Composable
private fun PodCardSurface(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
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
private fun PodBadges(pod: Pod, now: Instant) {
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
private fun MetaLine(@DrawableRes icon: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = ReconveneTheme.colors.inkMuted,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(18.dp),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium, color = ReconveneTheme.colors.inkMuted)
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
