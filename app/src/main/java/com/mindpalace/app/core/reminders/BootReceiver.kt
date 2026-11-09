package com.mindpalace.app.core.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mindpalace.app.MindPalaceApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** AlarmManager drops every alarm on reboot; this re-schedules what's still active and future. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val container = (context.applicationContext as MindPalaceApplication).container
        CoroutineScope(Dispatchers.IO).launch {
            try {
                container.database.reminderDao().activeTimeReminders().forEach { reminder ->
                    AlarmScheduler.schedule(context, reminder)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
