package com.mindpalace.app.core.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mindpalace.app.MindPalaceApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fires when the user grants/revokes exact-alarm scheduling for this app in system settings
 * (plan 11 Part A R1#6) — without this, a reminder that fell back to inexact scheduling because
 * exact alarms were off stayed inexact forever, even after the user went and turned exact alarms
 * back on. No permission is needed to receive this broadcast.
 */
class ExactAlarmPermissionChangedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
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
