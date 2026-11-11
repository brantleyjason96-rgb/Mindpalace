package com.mindpalace.app.capture

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.mindpalace.app.MindPalaceApplication
import com.mindpalace.app.R
import com.mindpalace.app.core.commands.ShareIngest
import kotlinx.coroutines.runBlocking

/**
 * ACTION_SEND (text/plain) receiver — the backend half of Phase 1 share capture. Maps the share
 * to a CreateNoteRequest with the pure, unit-tested [ShareIngest] mapper, saves IMMEDIATELY
 * (save-first, §3.1) with the source app recorded, toasts, and finishes. No UI by design.
 */
class ShareReceiverActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent?.action == Intent.ACTION_SEND) {
            // getCharSequenceExtra, not getStringExtra: styled text arrives as a SpannableString,
            // and getStringExtra silently returns null for it (plan 01 C1) — the share would be
            // dropped with no message at all.
            val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            val subject = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString()
                ?: intent.getCharSequenceExtra(Intent.EXTRA_TITLE)?.toString()
            val callingApp = referrer?.host ?: intent.`package`

            val request = ShareIngest.toRequest(text, subject, callingApp)
            if (request != null) {
                try {
                    val container = (application as MindPalaceApplication).container
                    // Deliberately synchronous: the save MUST complete before this transient
                    // activity finishes, so shared text can never be lost.
                    runBlocking { container.commands.create(request) }
                    Toast.makeText(this, R.string.saved_toast, Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    // A failed save must never silently swallow the user's shared text.
                    Toast.makeText(this, R.string.share_save_failed_toast, Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, R.string.share_empty_toast, Toast.LENGTH_SHORT).show()
            }
        }
        finish()
    }
}
