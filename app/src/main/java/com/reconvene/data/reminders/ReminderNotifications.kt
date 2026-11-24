package com.reconvene.data.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.reconvene.MainActivity
import com.reconvene.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

object ReminderNotifications {
    private const val CHANNEL = "pod_reminders"

    // Tapping a reminder opens this pod. MainActivity reads it.
    const val EXTRA_POD_ID = "com.reconvene.extra.POD_ID"

    // Android 13 and up ask the user; before that notifications are on unless turned off in settings.
    fun canNotify(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    // "Problem set 6" / "Starts at 5:20 PM · Mugar Library, 2nd floor". The clock time rather than
    // "in 15 min", which would be wrong if the reminder runs late.
    fun show(context: Context, podId: String, title: String, building: String, meetingPoint: String, startsAt: Instant) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(startsAt.atZone(ZoneId.systemDefault()))
        val open = PendingIntent.getActivity(
            context,
            podId.hashCode(),
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_POD_ID, podId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_clock)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.reminder_text, time, building, meetingPoint))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(podId.hashCode(), notification)
        } catch (e: SecurityException) {
            // Permission was taken away between the check and now.
        }
    }

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL,
            context.getString(R.string.reminder_channel),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.reminder_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
