package com.mindpalace.app.core.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.mindpalace.app.core.db.ReminderEntity
import java.time.Instant

const val EXTRA_REMINDER_ID = "reminder_id"
const val EXTRA_NOTE_ID = "note_id"

/** TIME reminder spec is an ISO-8601 instant string (§6 "ISO datetime"). */
fun ReminderEntity.scheduledAtMillis(): Long? = runCatching { Instant.parse(spec).toEpochMilli() }.getOrNull()

fun millisToSpec(atMillis: Long): String = Instant.ofEpochMilli(atMillis).toString()

/**
 * Alarm scheduling for TIME reminders (D19; plan 11 Part A) — applies a decision already made by
 * [ReminderPlanner]; it doesn't decide whether/when to fire itself. AlarmManager alarms don't
 * survive reboot — [com.mindpalace.app.core.commands.ReminderCommands.rescheduleAll] (called from
 * [BootReceiver], `Application.onCreate`, and [ExactAlarmPermissionChangedReceiver]) re-plans and
 * re-applies every still-active one.
 */
object AlarmScheduler {

    fun canScheduleExact(context: Context): Boolean =
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** [AlarmPlan.Exact] — fires within about a second of [atMillis]. */
    fun scheduleExact(context: Context, reminder: ReminderEntity, atMillis: Long) {
        context.getSystemService(AlarmManager::class.java)
            .setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pendingIntentFor(context, reminder))
    }

    /** [AlarmPlan.Inexact] — needs no permission; may fire several minutes late. */
    fun scheduleInexact(context: Context, reminder: ReminderEntity, atMillis: Long) {
        context.getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pendingIntentFor(context, reminder))
    }

    fun cancel(context: Context, reminder: ReminderEntity) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntentFor(context, reminder))
    }

    private fun pendingIntentFor(context: Context, reminder: ReminderEntity): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            putExtra(EXTRA_REMINDER_ID, reminder.id)
            putExtra(EXTRA_NOTE_ID, reminder.noteId)
        }
        return PendingIntent.getBroadcast(
            context,
            reminder.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
