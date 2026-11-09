package com.mindpalace.app.core.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mindpalace.app.MindPalaceApplication
import com.mindpalace.app.R
import com.mindpalace.app.core.model.ReminderState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Fires when a reminder's exact time arrives. A CANCELLED/DONE reminder silently shows nothing
 *  (it may have been resolved after the alarm was scheduled but before it fired). */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return
        val noteId = intent.getStringExtra(EXTRA_NOTE_ID) ?: return
        val pending = goAsync()
        val container = (context.applicationContext as MindPalaceApplication).container
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val reminder = container.database.reminderDao().byId(reminderId)
                if (reminder?.state == ReminderState.ACTIVE) {
                    val note = container.database.noteDao().byId(noteId)
                    val text = note?.title ?: note?.rawText ?: note?.transcript ?: note?.ocrText
                        ?: context.getString(R.string.reminder_default_text)
                    NotificationHelper.show(context, reminderId, noteId, text)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
