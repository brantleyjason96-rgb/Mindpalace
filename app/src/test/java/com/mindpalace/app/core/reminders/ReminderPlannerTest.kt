package com.mindpalace.app.core.reminders

import com.mindpalace.app.core.db.ReminderEntity
import com.mindpalace.app.core.model.ReminderState
import com.mindpalace.app.core.model.ReminderType
import org.junit.Assert.assertEquals
import org.junit.Test

/** Plan 11 Part A A3: pure decision-function tests, no Android types. */
class ReminderPlannerTest {

    private fun reminder(
        state: ReminderState = ReminderState.ACTIVE,
        fireAt: Long? = 10_000L,
        firedAt: Long? = null,
    ) = ReminderEntity(
        id = "r1",
        noteId = "n1",
        type = ReminderType.TIME,
        spec = "irrelevant-for-planning",
        state = state,
        createdAt = 1_000L,
        fireAt = fireAt,
        firedAt = firedAt,
    )

    @Test
    fun `future time with exact alarms allowed schedules exact`() {
        val plan = ReminderPlanner.plan(reminder(fireAt = 10_000L), now = 5_000L, canExact = true, noteDeleted = false)
        assertEquals(AlarmPlan.Exact(10_000L), plan)
    }

    @Test
    fun `future time without exact alarms schedules inexact`() {
        val plan = ReminderPlanner.plan(reminder(fireAt = 10_000L), now = 5_000L, canExact = false, noteDeleted = false)
        assertEquals(AlarmPlan.Inexact(10_000L), plan)
    }

    @Test
    fun `a past time fires now`() {
        val plan = ReminderPlanner.plan(reminder(fireAt = 1_000L), now = 5_000L, canExact = true, noteDeleted = false)
        assertEquals(AlarmPlan.FireNow, plan)
    }

    @Test
    fun `done or cancelled reminders are nothing`() {
        assertEquals(AlarmPlan.Nothing, ReminderPlanner.plan(reminder(state = ReminderState.DONE), 5_000L, true, false))
        assertEquals(AlarmPlan.Nothing, ReminderPlanner.plan(reminder(state = ReminderState.CANCELLED), 5_000L, true, false))
    }

    @Test
    fun `an already-fired reminder is nothing`() {
        val plan = ReminderPlanner.plan(reminder(firedAt = 4_000L), now = 5_000L, canExact = true, noteDeleted = false)
        assertEquals(AlarmPlan.Nothing, plan)
    }

    @Test
    fun `a deleted note is nothing`() {
        val plan = ReminderPlanner.plan(reminder(), now = 5_000L, canExact = true, noteDeleted = true)
        assertEquals(AlarmPlan.Nothing, plan)
    }

    @Test
    fun `exactly now fires now`() {
        val plan = ReminderPlanner.plan(reminder(fireAt = 5_000L), now = 5_000L, canExact = true, noteDeleted = false)
        assertEquals(AlarmPlan.FireNow, plan)
    }

    @Test
    fun `a null fire_at is nothing`() {
        val plan = ReminderPlanner.plan(reminder(fireAt = null), now = 5_000L, canExact = true, noteDeleted = false)
        assertEquals(AlarmPlan.Nothing, plan)
    }
}
