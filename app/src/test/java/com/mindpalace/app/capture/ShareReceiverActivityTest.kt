package com.mindpalace.app.capture

import android.content.Intent
import android.text.SpannableString
import androidx.test.core.app.ApplicationProvider
import com.mindpalace.app.MindPalaceApplication
import com.mindpalace.app.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/** Plan 02 §3.4: the share-target activity has no UI, so it is tested end-to-end. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ShareReceiverActivityTest {

    private fun activeNoteCount(): Int {
        val app = ApplicationProvider.getApplicationContext<MindPalaceApplication>()
        return kotlinx.coroutines.runBlocking { app.container.database.noteDao().countActive() }
    }

    private fun sendIntent(text: CharSequence? = null, subject: CharSequence? = null): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            text?.let { putExtra(Intent.EXTRA_TEXT, it) }
            subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
        }

    @Test
    fun `non-SEND intent finishes without saving`() {
        val controller = Robolectric.buildActivity(ShareReceiverActivity::class.java, Intent(Intent.ACTION_VIEW))
        val activity = controller.create().get()

        assertEquals(0, activeNoteCount())
        assertEquals(true, activity.isFinishing)
    }

    // ------------------------------------------------------------------------- plan 01 step 1a

    @Test
    fun `styled text share is saved`() {
        val controller = Robolectric.buildActivity(ShareReceiverActivity::class.java, sendIntent(text = SpannableString("styled")))
        val activity = controller.create().get()

        assertEquals(1, activeNoteCount())
        assertEquals(true, activity.isFinishing)
    }

    @Test
    fun `empty share shows a toast and saves nothing`() {
        Robolectric.buildActivity(ShareReceiverActivity::class.java, sendIntent()).create().get()

        val expected = ApplicationProvider.getApplicationContext<MindPalaceApplication>().getString(R.string.share_empty_toast)
        assertEquals(expected, ShadowToast.getTextOfLatestToast())
        assertEquals(0, activeNoteCount())
    }
}
