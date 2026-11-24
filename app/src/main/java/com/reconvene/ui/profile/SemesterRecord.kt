package com.reconvene.ui.profile

import com.reconvene.data.model.Group
import com.reconvene.data.model.Pod
import com.reconvene.data.model.SEMESTER_START
import java.time.Duration
import java.time.Instant

// What the user did this semester: pods they were in that have ended and weren't canceled, the
// time spent in them, how many of those they hosted, and the groups they're in now.
data class SemesterRecord(
    val pods: Int,
    val hosted: Int,
    val studied: Duration,
    val groups: Int,
)

fun semesterRecord(pods: List<Pod>, groups: List<Group>, uid: String, now: Instant): SemesterRecord {
    val done = pods.filter {
        it.hasMember(uid) && !it.canceled && it.endsAt <= now && it.startsAt >= SEMESTER_START
    }
    return SemesterRecord(
        pods = done.size,
        hosted = done.count { it.hostUid == uid },
        studied = done.fold(Duration.ZERO) { total, pod -> total + Duration.between(pod.startsAt, pod.endsAt) },
        groups = groups.count { it.isMember(uid) && !it.disbanded },
    )
}

// The user's next pod: one they're in that hasn't ended or been canceled, soonest first. A running
// pod counts, since it's where they should be.
fun nextPod(pods: List<Pod>, uid: String, now: Instant): Pod? =
    pods.filter { it.hasMember(uid) && !it.canceled && it.endsAt > now }.minByOrNull { it.startsAt }

// Hours to one decimal, "9.5", the way the record shows them.
fun Duration.hoursText(): String {
    val tenths = (toMinutes() + 3) / 6
    return if (tenths % 10 == 0L) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
}
