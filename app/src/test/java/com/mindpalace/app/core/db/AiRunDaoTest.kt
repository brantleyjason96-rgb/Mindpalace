package com.mindpalace.app.core.db

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [AiRunDao.lastSucceededFinishedAt] drives [com.mindpalace.app.ai.AiNightWorker]'s `since`. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AiRunDaoTest {

    private lateinit var db: MindPalaceDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            MindPalaceDatabase::class.java,
        ).setDriver(BundledSQLiteDriver()).build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun run(id: String, started: Long) = AiRunEntity(
        id = id,
        model = "test-model",
        modelVersion = "",
        promptVersion = "v1",
        started = started,
        outcome = "RUNNING",
    )

    @Test
    fun `no runs means null, process everything`() = runTest {
        assertNull(db.aiRunDao().lastSucceededFinishedAt())
    }

    @Test
    fun `only the most recent SUCCEEDED run counts, failures are ignored`() = runTest {
        db.aiRunDao().insert(run("r1", 1_000))
        db.aiRunDao().finish("r1", finished = 2_000, outcome = "SUCCEEDED", notesProcessed = 3)

        db.aiRunDao().insert(run("r2", 3_000))
        db.aiRunDao().finish("r2", finished = 4_000, outcome = "FAILED_LOAD", notesProcessed = 0)

        assertEquals(2_000L, db.aiRunDao().lastSucceededFinishedAt())

        db.aiRunDao().insert(run("r3", 5_000))
        db.aiRunDao().finish("r3", finished = 6_000, outcome = "SUCCEEDED", notesProcessed = 1)

        assertEquals(6_000L, db.aiRunDao().lastSucceededFinishedAt())
    }
}
