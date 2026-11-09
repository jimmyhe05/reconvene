package com.reconvene.testing

import com.reconvene.data.model.Pod
import com.reconvene.data.model.PodMember
import com.reconvene.data.model.PodMode
import java.time.Duration
import java.time.Instant

val TEST_HOST = PodMember(uid = "host", name = "Host")

// members counts everyone going, the host included.
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
    hostUid = TEST_HOST.uid,
    hostName = TEST_HOST.name,
    members = listOf(TEST_HOST) + (1 until members).map { PodMember(uid = "member-$it", name = "Member $it") },
    mode = PodMode.QUIET,
    canceled = canceled,
)
