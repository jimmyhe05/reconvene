package com.reconvene.ui.discover

import com.reconvene.data.model.Pod
import com.reconvene.data.model.PodTiming
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

sealed interface DiscoverUiState {
    data object Loading : DiscoverUiState
    data object Empty : DiscoverUiState
    data object Error : DiscoverUiState
    data class Content(val sections: List<PodSection>, val now: Instant) : DiscoverUiState
}

data class PodSection(val heading: SectionHeading, val pods: List<Pod>)

sealed interface SectionHeading {
    data object NowAndSoon : SectionHeading
    data object LaterToday : SectionHeading
    data object Tomorrow : SectionHeading
    data class Day(val date: LocalDate) : SectionHeading
}

private val listedTimings = setOf(PodTiming.UPCOMING, PodTiming.ONGOING)
internal val soonWindow: Duration = Duration.ofHours(2)

fun groupIntoSections(pods: List<Pod>, now: Instant, zone: ZoneId): List<PodSection> {
    val today = now.atZone(zone).toLocalDate()
    return pods
        .filter { it.timing(now) in listedTimings }
        .sortedBy { it.startsAt }
        .groupBy { pod ->
            val day = pod.startsAt.atZone(zone).toLocalDate()
            when {
                pod.startsAt < now.plus(soonWindow) -> SectionHeading.NowAndSoon
                day == today -> SectionHeading.LaterToday
                day == today.plusDays(1) -> SectionHeading.Tomorrow
                else -> SectionHeading.Day(day)
            }
        }
        .map { (heading, podsInSection) -> PodSection(heading, podsInSection) }
}
