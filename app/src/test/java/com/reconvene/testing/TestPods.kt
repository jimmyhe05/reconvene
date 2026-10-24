package com.reconvene.testing

import com.reconvene.data.model.Pod
import com.reconvene.data.model.PodMode
import java.time.Duration
import java.time.Instant

fun testPod(
    id: String = "pod",
    startsAt: Instant,
    durationMinutes: Long = 60,
    capacity: Int = 4,
    members: Int = 1,
    canceled: Boolean = false,
) = Pod(
    id = id,
    title = "Study session $id",
    course = "CS 501",
    building = "Mugar Library",
    meetingPoint = "2nd floor",
    startsAt = startsAt,
    endsAt = startsAt.plus(Duration.ofMinutes(durationMinutes)),
    capacity = capacity,
    hostName = "Host",
    memberNames = List(members) { "Member $it" },
    mode = PodMode.QUIET,
    canceled = canceled,
)
