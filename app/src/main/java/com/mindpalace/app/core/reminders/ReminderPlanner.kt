package com.mindpalace.app.core.reminders

import com.mindpalace.app.core.db.ReminderEntity
import com.mindpalace.app.core.model.ReminderState

/** What to do about one reminder right now — decided once, applied by [AlarmScheduler]/
 *  [com.mindpalace.app.core.commands.ReminderCommands]. No Android types, fully unit-testable. */
sealed interface AlarmPlan {
    /** Schedule with `setExactAndAllowWhileIdle` — fires within a second or so of [atMillis]. */
    data class Exact(val atMillis: Long) : AlarmPlan

    /** Schedule with `setAndAllowWhileIdle` (no permission needed) — may run several minutes
     *  late, used only when exact alarms aren't allowed. Better than never. */
    data class Inexact(val atMillis: Long) : AlarmPlan

    /** The time has already passed (missed while the phone was off, or just overdue) — fire now
     *  instead of scheduling for the past. */
    data object FireNow : AlarmPlan

    /** Not ACTIVE, not a TIME reminder with a parsed time, already fired, or its note is gone —
     *  there is nothing to schedule. */
    data object Nothing : AlarmPlan
}

/**
 * Pure decision function for time reminders (plan 11 Part A / audit R1): every caller that needs
 * to decide whether/how to schedule a reminder (adding one, rescheduling after boot, after an
 * exact-alarm permission change, after app start) goes through this one function instead of
 * re-deriving the rules ad hoc.
 */
object ReminderPlanner {

    fun plan(reminder: ReminderEntity, now: Long, canExact: Boolean, noteDeleted: Boolean): AlarmPlan {
        if (reminder.state != ReminderState.ACTIVE) return AlarmPlan.Nothing
        if (reminder.firedAt != null) return AlarmPlan.Nothing
        if (noteDeleted) return AlarmPlan.Nothing
        val fireAt = reminder.fireAt ?: return AlarmPlan.Nothing
        if (fireAt <= now) return AlarmPlan.FireNow
        return if (canExact) AlarmPlan.Exact(fireAt) else AlarmPlan.Inexact(fireAt)
    }
}
