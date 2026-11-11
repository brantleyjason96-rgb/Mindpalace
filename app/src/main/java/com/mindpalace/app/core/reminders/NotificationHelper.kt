package com.mindpalace.app.core.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mindpalace.app.MainActivity
import com.mindpalace.app.R

const val REMINDER_CHANNEL_ID = "reminders"
const val EXTRA_OPEN_NOTE_ID = "open_note_id"

/** Carries which notification action button was tapped (plan 11 Part A) — read by
 *  [ReminderAlarmReceiver], which also handles the original alarm firing (no extra = fire). */
const val EXTRA_NOTIFICATION_ACTION = "notification_action"
const val ACTION_MARK_DONE = "mark_done"
const val ACTION_SNOOZE_1H = "snooze_1h"

object NotificationHelper {

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            REMINDER_CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Tapping the notification opens the note it was set on; "Mark done"/"Snooze 1h" action
     *  buttons are handled by [ReminderAlarmReceiver] without opening the app (plan 11 Part A). */
    fun show(context: Context, reminderId: String, noteId: String, text: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_NOTE_ID, noteId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            reminderId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(context.getString(R.string.reminder_notification_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, context.getString(R.string.reminder_mark_done), actionPendingIntent(context, reminderId, ACTION_MARK_DONE))
            .addAction(0, context.getString(R.string.reminder_snooze_1h), actionPendingIntent(context, reminderId, ACTION_SNOOZE_1H))
            .build()
        NotificationManagerCompat.from(context).notify(reminderId.hashCode(), notification)
    }

    fun cancel(context: Context, reminderId: String) {
        NotificationManagerCompat.from(context).cancel(reminderId.hashCode())
    }

    private fun actionPendingIntent(context: Context, reminderId: String, action: String): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            putExtra(EXTRA_REMINDER_ID, reminderId)
            putExtra(EXTRA_NOTIFICATION_ACTION, action)
        }
        return PendingIntent.getBroadcast(
            context,
            (reminderId + action).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
