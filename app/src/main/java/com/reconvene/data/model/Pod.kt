package com.reconvene.data.model

import java.time.Instant

data class Pod(
    val id: String,
    val title: String,
    val course: String?,
    val building: String,
    val meetingPoint: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val capacity: Int,
    val hostName: String,
    val memberNames: List<String>,
    val mode: PodMode,
    val canceled: Boolean = false,
) {
    val spotsLeft: Int
        get() = (capacity - memberNames.size).coerceAtLeast(0)

    val isFull: Boolean
        get() = spotsLeft == 0

    fun timing(now: Instant): PodTiming = when {
        canceled -> PodTiming.CANCELED
        now >= endsAt -> PodTiming.ENDED
        now >= startsAt -> PodTiming.ONGOING
        else -> PodTiming.UPCOMING
    }
}

enum class PodMode { QUIET, COORDINATE, TALK }

enum class PodTiming { UPCOMING, ONGOING, ENDED, CANCELED }
