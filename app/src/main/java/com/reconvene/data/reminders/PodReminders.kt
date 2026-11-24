package com.reconvene.data.reminders

import com.reconvene.data.model.Pod
import com.reconvene.data.settings.ReminderLead
import java.time.Duration
import java.time.Instant

// One notification before a pod the user is going to.
data class PodReminder(
    val podId: String,
    val title: String,
    val building: String,
    val meetingPoint: String,
    val startsAt: Instant,
    val remindAt: Instant,
)

// The reminders that should be waiting right now: one per pod the signed-in user is in that isn't
// canceled and whose reminder time is still ahead. Leaving a pod, its cancellation, signing out or
// turning reminders off drops it from the list, and the scheduler cancels it.
fun remindersFor(pods: List<Pod>, uid: String?, lead: ReminderLead, now: Instant): List<PodReminder> {
    if (uid == null || lead == ReminderLead.OFF) return emptyList()
    return pods
        .filter { it.hasMember(uid) && !it.canceled }
        .map { pod ->
            PodReminder(
                podId = pod.id,
                title = pod.title,
                building = pod.building,
                meetingPoint = pod.meetingPoint,
                startsAt = pod.startsAt,
                remindAt = pod.startsAt.minus(Duration.ofMinutes(lead.minutes)),
            )
        }
        .filter { it.remindAt > now }
        .sortedBy { it.remindAt }
}
