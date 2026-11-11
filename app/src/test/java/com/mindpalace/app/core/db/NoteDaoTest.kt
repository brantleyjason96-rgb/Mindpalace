package com.mindpalace.app.core.db

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import android.database.SQLException
import androidx.room3.executeSQL
import androidx.room3.useWriterConnection
import com.mindpalace.app.core.model.DerivedField
import com.mindpalace.app.core.model.NoteSource
import com.mindpalace.app.core.model.NoteStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Ignore
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

    // ------------------------------------------------------------------ plan 02 §3.2 (untagged)

    @Test
    fun guardedStatusDeleteAndRestoreRejectStaleRevisions() = runTest {
        db.noteDao().insert(note("n1", "a", rev = 1))
        assertEquals(0, db.noteDao().updateStatusGuarded("n1", NoteStatus.DONE, now = 2_000L, revision = 7))
        assertEquals(0, db.noteDao().softDeleteGuarded("n1", now = 2_000L, revision = 7))

        // Delete with the correct revision, then try to restore with the wrong one.
        assertEquals(1, db.noteDao().softDeleteGuarded("n1", now = 2_000L, revision = 1))
        assertEquals(0, db.noteDao().restoreGuarded("n1", now = 3_000L, revision = 7))
    }

    @Test
    fun ftsFollowsEdits() = runTest {
        db.noteDao().insert(note("n1", "alpha"))
        db.noteDao().updateRawTextGuarded("n1", "beta", now = 2_000L, revision = 1)

        assertEquals(0, db.noteDao().searchFts(FtsQuery.sanitize("alpha")!!, limit = 10).size)
        assertEquals(1, db.noteDao().searchFts(FtsQuery.sanitize("beta")!!, limit = 10).size)
    }

    @Test
    fun ftsFindsRestoredNotesAgain() = runTest {
        db.noteDao().insert(note("n1", "lesson plan draft"))
        db.noteDao().softDeleteGuarded("n1", now = 2_000L, revision = 1)
        db.noteDao().restoreGuarded("n1", now = 3_000L, revision = 2)

        assertEquals(1, db.noteDao().searchFts(FtsQuery.sanitize("draft")!!, limit = 10).size)
    }

    @Test
    fun ftsCoversTranscriptAndOcrColumns() = runTest {
        db.noteDao().insert(note("n1", raw = "n/a").copy(transcript = "spoken words here"))
        db.noteDao().insert(note("n2", raw = "n/a").copy(ocrText = "scanned handwriting text"))

        assertEquals(1, db.noteDao().searchFts(FtsQuery.sanitize("spoken")!!, limit = 10).size)
        assertEquals(1, db.noteDao().searchFts(FtsQuery.sanitize("scanned")!!, limit = 10).size)
    }

    @Test
    fun ftsFoldsCaseAndDiacritics() = runTest {
        db.noteDao().insert(note("n1", "El café"))
        assertEquals(1, db.noteDao().searchFts(FtsQuery.sanitize("CAFE")!!, limit = 10).size)
    }

    @Test
    fun ftsIntegrityCheckPassesAfterChurn() = runTest {
        repeat(50) { i -> db.noteDao().insert(note("n$i", "note body $i")) }
        repeat(20) { i -> db.noteDao().updateRawTextGuarded("n$i", "edited body $i", now = 2_000L, revision = 1) }
        repeat(10) { i -> db.noteDao().softDeleteGuarded("n$i", now = 3_000L, revision = 2) }

        // Throws SQLiteException if the shadow FTS5 index has drifted from the content table.
        db.useWriterConnection { connection ->
            connection.executeSQL("INSERT INTO notes_fts(notes_fts) VALUES('integrity-check')")
        }
    }

    @Test
    fun foreignKeysAreEnforced() = runTest {
        // Probe (plan 02 §3.2 T-FK1): if this does not throw, foreign keys are not enforced and
        // the plan's assumption needs to be revisited rather than this test loosened.
        try {
            db.derivedDao().upsert(
                DerivedEntity(
                    noteId = "does-not-exist",
                    field = DerivedField.CATEGORY,
                    value = "Inbox",
                    origin = "system",
                    confidence = 1f,
                    createdAt = 1_000L,
                ),
            )
            fail("expected a foreign key violation for a derived row on a missing note")
        } catch (e: SQLException) {
            assertTrue(e.message.orEmpty().contains("FOREIGN KEY", ignoreCase = true))
        }
    }

    // ------------------------------------------------------------------------- plan 01 step 1c

    @Test
    fun prefixSearchMatchesAPartialLastWord() = runTest {
        db.noteDao().insert(note("n1", "buy oatmeal for breakfast"))
        val hits = NoteSearch.search(db.noteDao(), "oat")
        assertEquals(listOf("n1"), hits.map { it.id })
    }

    @Test
    fun searchResultsAreRanked() = runTest {
        db.noteDao().insert(note("n1", "drift drift drift continental", at = 1_000L))
        db.noteDao().insert(note("n2", "a little continental drift", at = 2_000L))
        // n2 is newer, but n1 mentions "drift" three times and must rank first by relevance.
        val hits = NoteSearch.search(db.noteDao(), "drift")
        assertEquals(listOf("n1", "n2"), hits.map { it.id })
    }

    @Test
    fun likeSearchFallbackTreatsPercentAndUnderscoreLiterally() = runTest {
        db.noteDao().insert(note("n1", "100% done"))
        db.noteDao().insert(note("n2", "100 done"))
        val hits = db.noteDao().searchLike(NoteSearch.likeEscape("100%"), 10)
        assertEquals(listOf("n1"), hits.map { it.id })
    }

    @Test
    fun searchNeverThrowsOnHostileInput() = runTest {
        db.noteDao().insert(note("n1", "safe text"))
        val hostileInputs = listOf("-", "( )", "***", "+", "{}", "🙂", "\u0000", "a OR", "'")
        for (q in hostileInputs) {
            NoteSearch.search(db.noteDao(), q) // must not throw
        }
    }

    @Test
    @Ignore("G1, fixed in plan 10: RuleDao.enabled() orders by id (random UUID), not insertion order")
    fun enabledRulesComeBackInAStableIntendedOrder() = runTest {
        db.ruleDao().upsert(RuleEntity(id = "b", version = 1, enabled = true, matcher = "{}", action = "{}"))
        db.ruleDao().upsert(RuleEntity(id = "a", version = 1, enabled = true, matcher = "{}", action = "{}"))

        assertEquals(listOf("b", "a"), db.ruleDao().enabled().map { it.id })
    }
}
