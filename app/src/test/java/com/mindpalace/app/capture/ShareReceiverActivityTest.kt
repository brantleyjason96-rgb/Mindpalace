package com.mindpalace.app.capture

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.mindpalace.app.MindPalaceApplication
import org.junit.Assert.assertEquals
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.runner.RunWith

/** Plan 02 §3.4 (untagged part): the share-target activity has no UI, so it is tested end-to-end. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ShareReceiverActivityTest {

    private fun activeNoteCount(): Int {
        val app = ApplicationProvider.getApplicationContext<MindPalaceApplication>()
        return runBlockingCount(app)
    }

    private fun runBlockingCount(app: MindPalaceApplication): Int =
        kotlinx.coroutines.runBlocking { app.container.database.noteDao().countActive() }

    @Test
    fun `non-SEND intent finishes without saving`() {
        val controller = Robolectric.buildActivity(ShareReceiverActivity::class.java, Intent(Intent.ACTION_VIEW))
        val activity = controller.create().get()

        assertEquals(0, activeNoteCount())
        assertEquals(true, activity.isFinishing)
    }
}
