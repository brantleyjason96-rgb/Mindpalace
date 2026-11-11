package com.mindpalace.app.core.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mindpalace.app.MindPalaceApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Fires when a reminder's exact time arrives. Delegates to the same
 *  [com.mindpalace.app.core.commands.ReminderCommands.fire] that [rescheduleAll] uses for a
 *  missed reminder, so firing is idempotent and decided in exactly one place. */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return
        val pending = goAsync()
        val container = (context.applicationContext as MindPalaceApplication).container
        CoroutineScope(Dispatchers.IO).launch {
            try {
                container.reminderCommands.fire(context, reminderId)
            } finally {
                pending.finish()
            }
        }
    }
}
