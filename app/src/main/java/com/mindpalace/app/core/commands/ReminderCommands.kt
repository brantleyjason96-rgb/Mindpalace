package com.mindpalace.app.core.commands

import android.content.Context
import com.mindpalace.app.R
import com.mindpalace.app.core.db.MindPalaceDatabase
import com.mindpalace.app.core.db.ReminderEntity
import com.mindpalace.app.core.model.ReminderState
import com.mindpalace.app.core.model.ReminderType
import com.mindpalace.app.core.reminders.AlarmPlan
import com.mindpalace.app.core.reminders.AlarmScheduler
import com.mindpalace.app.core.reminders.NotificationHelper
import com.mindpalace.app.core.reminders.ReminderPlanner
import com.mindpalace.app.core.reminders.millisToSpec
import java.util.UUID

/** A reminder time must be at least this far in the future (plan 11 Part A A1#2 — a past time
 *  used to be silently accepted and simply never scheduled). */
const val REMINDER_MIN_LEAD_MILLIS = 30_000L

/**
 * Every reminder write and scheduling decision in one place (plan 11 Part A) — not audited (D19
 * keeps reminders outside the note audit trail) but the one place `MainViewModel` calls through,
 * so the scheduling rules in [ReminderPlanner] are applied consistently everywhere instead of
 * re-derived at each call site (the R1 bugs: silent failures on exact-alarm denial, accepted past
 * times, reminders that never re-schedule after boot or a permission grant).
 */
class ReminderCommands(
    private val database: MindPalaceDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val reminderDao get() = database.reminderDao()

    /** Creates the reminder row and immediately applies [ReminderPlanner]'s decision for it. */
    suspend fun add(context: Context, noteId: String, atMillis: Long): ReminderEntity {
        if (atMillis < now() + REMINDER_MIN_LEAD_MILLIS) {
            throw IllegalArgumentException("Reminder time must be at least 30 seconds in the future")
        }
        val reminder = ReminderEntity(
            id = newId(),
            noteId = noteId,
            type = ReminderType.TIME,
            spec = millisToSpec(atMillis),
            state = ReminderState.ACTIVE,
            fireAt = atMillis,
            createdAt = now(),
        )
        reminderDao.insert(reminder)
        applyPlan(context, reminder)
        return reminder
    }

    suspend fun cancel(context: Context, reminder: ReminderEntity) {
        reminderDao.setState(reminder.id, ReminderState.CANCELLED, null)
        AlarmScheduler.cancel(context, reminder)
    }

    suspend fun complete(context: Context, reminder: ReminderEntity) {
        reminderDao.setState(reminder.id, ReminderState.DONE, null)
        AlarmScheduler.cancel(context, reminder)
    }

    /** Re-arms a (usually already-fired) reminder at a new time. */
    suspend fun snooze(context: Context, reminder: ReminderEntity, untilMillis: Long) {
        reminderDao.snoozeTo(reminder.id, untilMillis)
        applyPlan(context, reminder.copy(fireAt = untilMillis, firedAt = null, spec = millisToSpec(untilMillis)))
    }

    /**
     * Posts the notification and stamps `fired_at` — idempotent (a second call for an
     * already-fired reminder, a non-ACTIVE one, or one whose note was soft-deleted does nothing;
     * audit R1#4/#10). Called from [com.mindpalace.app.core.reminders.ReminderAlarmReceiver]'s
     * exact-alarm firing AND from [rescheduleAll]'s `FireNow` path — one place, not two.
     */
    suspend fun fire(context: Context, reminderId: String) {
        val reminder = reminderDao.byId(reminderId) ?: return
        if (reminder.state != ReminderState.ACTIVE || reminder.firedAt != null) return
        val note = database.noteDao().byId(reminder.noteId)
        if (note == null || note.deletedAt != null) return
        val text = note.title ?: note.rawText ?: note.transcript ?: note.ocrText
            ?: context.getString(R.string.reminder_default_text)
        NotificationHelper.show(context, reminder.id, reminder.noteId, text)
        reminderDao.setFiredAt(reminder.id, now())
    }

    /**
     * Runs [ReminderPlanner.plan] over every active TIME reminder: missed ones fire now, future
     * ones get (re)scheduled for whatever's currently possible (exact or inexact). Call after
     * boot, on `Application.onCreate` (AlarmManager drops every alarm on both), and after an
     * exact-alarm permission change (R1#6 — these used to just sit unscheduled forever).
     */
    suspend fun rescheduleAll(context: Context) {
        val canExact = AlarmScheduler.canScheduleExact(context)
        reminderDao.activeTimeReminders().forEach { reminder ->
            applyPlan(context, reminder, canExact)
        }
    }

    private suspend fun applyPlan(context: Context, reminder: ReminderEntity, canExact: Boolean = AlarmScheduler.canScheduleExact(context)) {
        val note = database.noteDao().byId(reminder.noteId)
        when (val plan = ReminderPlanner.plan(reminder, now(), canExact, noteDeleted = note == null || note.deletedAt != null)) {
            is AlarmPlan.Exact -> AlarmScheduler.scheduleExact(context, reminder, plan.atMillis)
            is AlarmPlan.Inexact -> AlarmScheduler.scheduleInexact(context, reminder, plan.atMillis)
            AlarmPlan.FireNow -> fire(context, reminder.id)
            AlarmPlan.Nothing -> Unit
        }
    }
}
