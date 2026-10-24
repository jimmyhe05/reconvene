package com.reconvene.ui.discover

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.reconvene.R
import com.reconvene.data.model.Pod
import com.reconvene.data.model.PodMode
import com.reconvene.data.model.PodTiming
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val shortTime: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
private val shortDay: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")

@Composable
fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(R.string.day_today)
    today.plusDays(1) -> stringResource(R.string.day_tomorrow)
    else -> shortDay.format(date)
}

@Composable
fun podTimeText(pod: Pod, now: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
    val untilStart = Duration.between(now, pod.startsAt)
    return when {
        pod.timing(now) == PodTiming.ONGOING ->
            stringResource(R.string.pod_time_ongoing, shortTime.format(pod.endsAt.atZone(zone)))
        untilStart < soonWindow -> {
            val minutes = untilStart.toMinutes().coerceAtLeast(1).toInt()
            pluralStringResource(R.plurals.pod_time_starts_in, minutes, minutes)
        }
        else -> stringResource(
            R.string.pod_time_range,
            dayLabel(pod.startsAt.atZone(zone).toLocalDate(), now.atZone(zone).toLocalDate()),
            shortTime.format(pod.startsAt.atZone(zone)),
            shortTime.format(pod.endsAt.atZone(zone)),
        )
    }
}

@get:StringRes
val PodMode.labelRes: Int
    get() = when (this) {
        PodMode.QUIET -> R.string.mode_quiet
        PodMode.COORDINATE -> R.string.mode_coordinate
        PodMode.TALK -> R.string.mode_talk
    }
