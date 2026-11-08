package com.mindpalace.app.core.db

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.mindpalace.app.core.model.NoteSource
import com.mindpalace.app.core.model.NoteStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NoteDaoTest {

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

    private fun note(id: String, raw: String, rev: Int = 1, at: Long = 1_000L) = NoteEntity(
        id = id,
        createdAt = at,
        updatedAt = at,
        source = NoteSource.TEXT,
        rawText = raw,
        status = NoteStatus.INBOX,
        revision = rev,
    )

    @Test
    fun insertAndFetchById() = runTest {
        db.noteDao().insert(note("n1", "Buy oat milk"))
        val loaded = db.noteDao().byId("n1")
        assertEquals("Buy oat milk", loaded?.rawText)
        assertEquals(NoteStatus.INBOX, loaded?.status)
        assertEquals(1, db.noteDao().countActive())
    }

    @Test
    fun revisionGuardRejectsStaleUpdates() = runTest {
        db.noteDao().insert(note("n1", "a", rev = 1))
        // Stale revision -> no rows touched, no clobbering.
        assertEquals(0, db.noteDao().updateRawTextGuarded("n1", "b", now = 2_000L, revision = 7))
        assertEquals("a", db.noteDao().byId("n1")?.rawText)
        // Correct revision -> applied and revision bumped.
        assertEquals(1, db.noteDao().updateRawTextGuarded("n1", "b", 2_000L, 1))
        val after = db.noteDao().byId("n1")!!
        assertEquals("b", after.rawText)
        assertEquals(2, after.revision)
    }

    @Test
    fun softDeleteHidesFromActiveAndRestoreBringsBack() = runTest {
        db.noteDao().insert(note("n1", "x"))
        assertEquals(1, db.noteDao().softDeleteGuarded("n1", now = 2_000L, revision = 1))
        assertEquals(0, db.noteDao().countActive())
        assertEquals("x", db.noteDao().recentlyDeleted(10).single().rawText)
        assertEquals(1, db.noteDao().restoreGuarded("n1", now = 3_000L, revision = 2))
        assertEquals(1, db.noteDao().countActive())
    }

    @Test
    fun ftsSearchFindsWordsAcrossColumns() = runTest {
        db.noteDao().insert(note("n1", "Buy oat milk tomorrow", at = 1_000L))
        db.noteDao().insert(note("n2", "Lesson plan: continental drift", at = 1_100L))
        db.noteDao().insert(note("n3", "get more", at = 1_200L).copy(title = "Oat supply"))

        val hits = db.noteDao().searchFts(FtsQuery.sanitize("oat")!!, limit = 10)
        assertEquals(setOf("n1", "n3"), hits.map { it.id }.toSet())

        // Multi-token search is an implicit AND.
        val both = db.noteDao().searchFts(FtsQuery.sanitize("oat milk")!!, limit = 10)
        assertEquals(listOf("n1"), both.map { it.id })
    }

    @Test
    fun ftsQuerySurvivesHostileUserInput() = runTest {
        db.noteDao().insert(note("n1", "don't panic (it's fine)"))
        // Unsantized "don't" or "(" would be invalid / operator syntax in FTS5 MATCH.
        val hits = db.noteDao().searchFts(FtsQuery.sanitize("don't")!!, limit = 10)
        assertEquals(1, hits.size)
        assertEquals("don't panic (it's fine)", hits[0].rawText)

        val parens = db.noteDao().searchFts(FtsQuery.sanitize("(it's fine)")!!, limit = 10)
        assertEquals(1, parens.size)
    }

    @Test
    fun ftsSearchExcludesDeletedNotes() = runTest {
        db.noteDao().insert(note("n1", "temporary doodle"))
        db.noteDao().softDeleteGuarded("n1", now = 2_000L, revision = 1)
        assertEquals(0, db.noteDao().searchFts(FtsQuery.sanitize("doodle")!!, limit = 10).size)
    }

    @Test
    fun likeSearchFallbackWorks() = runTest {
        db.noteDao().insert(note("n1", "continental drift quiz"))
        assertEquals(1, db.noteDao().searchLike("drift", 10).size)
    }
}
