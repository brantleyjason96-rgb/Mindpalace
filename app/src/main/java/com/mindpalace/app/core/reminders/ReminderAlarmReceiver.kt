package com.mindpalace.app.core.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mindpalace.app.MindPalaceApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fires when a reminder's exact time arrives, delegating to the same
 * [com.mindpalace.app.core.commands.ReminderCommands.fire] that `rescheduleAll` uses for a
 * missed reminder, so firing is idempotent and decided in exactly one place. Also handles the
 * "Mark done"/"Snooze 1h" notification action buttons (plan 11 Part A) — both resolve without
 * ever opening the app.
 */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return
        val action = intent.getStringExtra(EXTRA_NOTIFICATION_ACTION)
        val pending = goAsync()
        val container = (context.applicationContext as MindPalaceApplication).container
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_MARK_DONE -> {
                        container.database.reminderDao().byId(reminderId)?.let {
                            container.reminderCommands.complete(context, it)
                        }
                        NotificationHelper.cancel(context, reminderId)
                    }
                    ACTION_SNOOZE_1H -> {
                        container.database.reminderDao().byId(reminderId)?.let {
                            container.reminderCommands.snooze(context, it, System.currentTimeMillis() + 3_600_000L)
                        }
                        NotificationHelper.cancel(context, reminderId)
                    }
                    else -> container.reminderCommands.fire(context, reminderId)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
