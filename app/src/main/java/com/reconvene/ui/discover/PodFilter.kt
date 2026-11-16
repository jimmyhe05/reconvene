package com.reconvene.ui.discover

import com.reconvene.data.model.Pod
import com.reconvene.data.model.PodMode
import java.io.Serializable
import java.time.Instant
import java.time.ZoneId

// What the user has typed and switched on to narrow the pod list. The default matches every pod.
data class PodFilter(
    val query: String = "",
    val openSpots: Boolean = false,
    val today: Boolean = false,
    val mine: Boolean = false,
    // No modes chosen means any mode.
    val modes: Set<PodMode> = emptySet(),
) : Serializable {
    val isActive: Boolean
        get() = query.isNotBlank() || openSpots || today || mine || modes.isNotEmpty()

    fun withMode(mode: PodMode, on: Boolean): PodFilter = copy(modes = if (on) modes + mode else modes - mode)
}

// A pod has to pass every part of the filter that is set.
fun PodFilter.matches(pod: Pod, viewerUid: String?, now: Instant, zone: ZoneId): Boolean {
    if (openSpots && pod.isFull) return false
    if (today && pod.startsAt.atZone(zone).toLocalDate() != now.atZone(zone).toLocalDate()) return false
    if (mine && (viewerUid == null || !pod.hasMember(viewerUid))) return false
    if (modes.isNotEmpty() && pod.mode !in modes) return false
    return pod.containsEveryWordOf(query)
}

// Every word of the query has to appear somewhere in the pod's text, in any order.
private fun Pod.containsEveryWordOf(query: String): Boolean {
    val words = query.lowercase().split(whitespace).filter { it.isNotEmpty() }
    if (words.isEmpty()) return true
    val fields = listOfNotNull(title, course, building, meetingPoint, hostName).map { it.lowercase() }
    // Also compared with the spaces taken out, so "cs330" finds "CS 330".
    val squashed = fields.map { it.replace(whitespace, "") }
    return words.all { word -> fields.any { word in it } || squashed.any { word in it } }
}

private val whitespace = Regex("\\s+")
