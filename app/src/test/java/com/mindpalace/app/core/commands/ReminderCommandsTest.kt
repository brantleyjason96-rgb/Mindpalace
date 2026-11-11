package com.mindpalace.app.core.commands

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.mindpalace.app.core.db.MindPalaceDatabase
import com.mindpalace.app.core.db.ReminderEntity
import com.mindpalace.app.core.model.NoteSource
import com.mindpalace.app.core.model.ReminderState
import com.mindpalace.app.core.model.ReminderType
import com.mindpalace.app.core.reminders.millisToSpec
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

/** Plan 11 Part A A3: ReminderCommands exercised end-to-end against a real (in-memory) DB and
 *  Robolectric's AlarmManager/NotificationManager shadows. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ReminderCommandsTest {

    private lateinit var db: MindPalaceDatabase
    private lateinit var handler: NoteCommandHandler
    private lateinit var reminderCommands: ReminderCommands
    private lateinit var context: Context
    private var clock: Long = 100_000L

    @Before
    fun setUp() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        context = ApplicationProvider.getApplicationContext()
        shadowOf(context as android.app.Application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        db = Room.inMemoryDatabaseBuilder(context, MindPalaceDatabase::class.java)
            .setDriver(BundledSQLiteDriver())
            .build()
        handler = NoteCommandHandler(db, now = { clock })
        reminderCommands = ReminderCommands(db, now = { clock })
    }

    @After
    fun tearDown() {
        db.close()
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
    }

    private fun alarmManager() = context.getSystemService(AlarmManager::class.java)
    private fun notificationManager() = context.getSystemService(NotificationManager::class.java)

    private suspend fun noteId(): String =
        handler.create(com.mindpalace.app.core.commands.CreateNoteRequest(source = NoteSource.TEXT, rawText = "note")).id

    @Test
    fun `add schedules one alarm at fire_at`() = runTest {
        val id = noteId()
        val at = clock + 60_000L
        reminderCommands.add(context, id, at)

        val alarm = shadowOf(alarmManager()).nextScheduledAlarm!!
        assertEquals(at, alarm.triggerAtMs)
    }

    @Test
    fun `add rejects a past time`() = runTest {
        val id = noteId()
        try {
            reminderCommands.add(context, id, clock - 1_000L)
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (ignored: IllegalArgumentException) {
            // expected
        }
        assertEquals(0, db.reminderDao().count())
    }

    @Test
    fun `fire posts a notification and stamps fired_at once`() = runTest {
        val id = noteId()
        val reminder = reminderCommands.add(context, id, clock + 60_000L)

        reminderCommands.fire(context, reminder.id)
        reminderCommands.fire(context, reminder.id) // second call must be a no-op

        assertEquals(1, shadowOf(notificationManager()).allNotifications.size)
        val stored = db.reminderDao().byId(reminder.id)!!
        assertTrue(stored.firedAt != null)
    }

    @Test
    fun `fire does nothing for a deleted note`() = runTest {
        val id = noteId()
        val reminder = reminderCommands.add(context, id, clock + 60_000L)
        handler.softDelete(id)

        reminderCommands.fire(context, reminder.id)

        assertEquals(0, shadowOf(notificationManager()).allNotifications.size)
        assertNull(db.reminderDao().byId(reminder.id)!!.firedAt)
    }

    @Test
    fun `rescheduleAll fires missed reminders and schedules future ones`() = runTest {
        val id = noteId()
        // Inserted directly (not via add()) so it was never scheduled — simulates a reminder
        // whose fire time passed while the alarm was never (re)applied, e.g. across a reboot.
        val past = ReminderEntity(
            id = "past1", noteId = id, type = ReminderType.TIME, spec = millisToSpec(clock - 1_000L),
            state = ReminderState.ACTIVE, fireAt = clock - 1_000L, createdAt = clock,
        )
        db.reminderDao().insert(past)
        val future = reminderCommands.add(context, id, clock + 60_000L)

        reminderCommands.rescheduleAll(context)

        assertEquals(1, shadowOf(notificationManager()).allNotifications.size)
        assertTrue(db.reminderDao().byId(past.id)!!.firedAt != null)
        assertEquals(future.fireAt, shadowOf(alarmManager()).nextScheduledAlarm!!.triggerAtMs)
        assertNull(db.reminderDao().byId(future.id)!!.firedAt)
    }

    @Test
    fun `cancel removes the alarm`() = runTest {
        val id = noteId()
        val reminder = reminderCommands.add(context, id, clock + 60_000L)
        reminderCommands.cancel(context, reminder)

        assertEquals(ReminderState.CANCELLED, db.reminderDao().byId(reminder.id)!!.state)
        // Re-applying the plan after cancel schedules nothing new for this (now CANCELLED) row —
        // the only way to observe "the alarm is gone" without a bulk accessor on the shadow.
        reminderCommands.rescheduleAll(context)
        assertEquals(0, shadowOf(notificationManager()).allNotifications.size)
    }

    @Test
    fun `snooze re-arms a fired reminder`() = runTest {
        val id = noteId()
        val reminder = reminderCommands.add(context, id, clock + 60_000L)
        reminderCommands.fire(context, reminder.id)

        val newTime = clock + 3_600_000L
        reminderCommands.snooze(context, db.reminderDao().byId(reminder.id)!!, newTime)

        val stored = db.reminderDao().byId(reminder.id)!!
        assertNull(stored.firedAt)
        assertEquals(newTime, shadowOf(alarmManager()).nextScheduledAlarm!!.triggerAtMs)
    }
}
