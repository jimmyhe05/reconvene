package com.reconvene.data.reminders

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.time.Clock
import java.time.Duration

interface ReminderScheduler {
    // Makes the waiting reminders exactly these.
    fun replaceAll(reminders: List<PodReminder>)
}

// What to change to go from the reminders scheduled before to the ones wanted now. previous is null
// the first time in a run of the app, when what WorkManager still holds from last time is unknown,
// so everything is cleared and scheduled again.
data class ReminderChanges(
    val cancelEverything: Boolean,
    val cancel: Set<String>,
    val schedule: List<PodReminder>,
)

fun reminderChanges(previous: Map<String, PodReminder>?, wanted: List<PodReminder>): ReminderChanges {
    if (previous == null) return ReminderChanges(cancelEverything = true, cancel = emptySet(), schedule = wanted)
    val wantedIds = wanted.map { it.podId }.toSet()
    return ReminderChanges(
        cancelEverything = false,
        cancel = previous.keys - wantedIds,
        schedule = wanted.filter { previous[it.podId] != it },
    )
}

// One unique WorkManager job per pod, named after it, so rescheduling replaces it and leaving cancels
// it. WorkManager keeps them across restarts. It may run a few minutes late when the phone is dozing,
// which is fine for a heads-up; exact alarms would need another permission.
class WorkManagerReminderScheduler(context: Context, private val clock: Clock) : ReminderScheduler {

    private val workManager = WorkManager.getInstance(context)
    private var scheduled: Map<String, PodReminder>? = null

    @Synchronized
    override fun replaceAll(reminders: List<PodReminder>) {
        val changes = reminderChanges(scheduled, reminders)
        if (changes.cancelEverything) workManager.cancelAllWorkByTag(TAG)
        changes.cancel.forEach { workManager.cancelUniqueWork(workName(it)) }
        changes.schedule.forEach { reminder ->
            val request = OneTimeWorkRequestBuilder<PodReminderWorker>()
                .setInitialDelay(Duration.between(clock.instant(), reminder.remindAt).coerceAtLeast(Duration.ZERO))
                .setInputData(
                    workDataOf(
                        PodReminderWorker.POD_ID to reminder.podId,
                        PodReminderWorker.TITLE to reminder.title,
                        PodReminderWorker.BUILDING to reminder.building,
                        PodReminderWorker.MEETING_POINT to reminder.meetingPoint,
                        PodReminderWorker.STARTS_AT to reminder.startsAt.toEpochMilli(),
                    ),
                )
                .addTag(TAG)
                .build()
            workManager.enqueueUniqueWork(workName(reminder.podId), ExistingWorkPolicy.REPLACE, request)
        }
        scheduled = reminders.associateBy { it.podId }
    }

    private fun workName(podId: String) = "pod-reminder-$podId"

    private companion object {
        const val TAG = "pod-reminder"
    }
}
