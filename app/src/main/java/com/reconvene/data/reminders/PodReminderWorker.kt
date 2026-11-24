package com.reconvene.data.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.time.Instant

// Posts one pod's reminder when its time comes.
class PodReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val podId = inputData.getString(POD_ID) ?: return Result.failure()
        ReminderNotifications.show(
            context = applicationContext,
            podId = podId,
            title = inputData.getString(TITLE).orEmpty(),
            building = inputData.getString(BUILDING).orEmpty(),
            meetingPoint = inputData.getString(MEETING_POINT).orEmpty(),
            startsAt = Instant.ofEpochMilli(inputData.getLong(STARTS_AT, 0)),
        )
        return Result.success()
    }

    companion object {
        const val POD_ID = "podId"
        const val TITLE = "title"
        const val BUILDING = "building"
        const val MEETING_POINT = "meetingPoint"
        const val STARTS_AT = "startsAt"
    }
}
