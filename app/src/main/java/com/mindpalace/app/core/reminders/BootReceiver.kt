package com.mindpalace.app.core.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mindpalace.app.MindPalaceApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** AlarmManager drops every alarm on reboot; this re-plans and re-applies every still-active
 *  TIME reminder (including firing any that were missed while the phone was off, R1#3). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val container = (context.applicationContext as MindPalaceApplication).container
        CoroutineScope(Dispatchers.IO).launch {
            try {
                container.reminderCommands.rescheduleAll(context)
            } finally {
                pending.finish()
            }
        }
    }
}
