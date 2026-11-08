package com.mindpalace.app.core.commands

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.mindpalace.app.core.db.MindPalaceDatabase
import com.mindpalace.app.core.model.AuditAction
import com.mindpalace.app.core.model.AuditActor
import com.mindpalace.app.core.model.CATEGORY_INBOX
import com.mindpalace.app.core.model.DerivedField
import com.mindpalace.app.core.model.NoteSource
import com.mindpalace.app.core.model.NoteStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for the save-first command handler: every mutation is durable + audited + reversible
 * (§3.1, §3.2, §3.7). These are the behavioral guarantees the rest of the app leans on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NoteCommandHandlerTest {

    private lateinit var db: MindPalaceDatabase
    private lateinit var handler: NoteCommandHandler
    private var clock: Long = 10_000
    private var idSeq = 0

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            MindPalaceDatabase::class.java,
        ).setDriver(BundledSQLiteDriver()).build()
        handler = NoteCommandHandler(db, now = { clock }, newId = { "id-${idSeq++}" })
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `create persists note, seeds Inbox category, and audits`() = runTest {
        val note = handler.create(CreateNoteRequest(source = NoteSource.TEXT, rawText = "Buy oat milk"))

        assertEquals(NoteStatus.INBOX, note.status)
        assertEquals(1, note.revision)
        assertEquals(10_000, note.createdAt)

        assertEquals("Buy oat milk", db.noteDao().byId(note.id)?.rawText)

        val derived = db.derivedDao().forNote(note.id).single()
        assertEquals(DerivedField.CATEGORY, derived.field)
        assertEquals(CATEGORY_INBOX, derived.value)
        assertEquals("system", derived.origin)

        val audit = db.auditDao().recentFor("note", note.id, 1).single()
        assertEquals(AuditAction.CREATE, audit.action)
        assertEquals(AuditActor.USER, audit.actor)
        assertTrue("audit after-snapshot must contain the original text", audit.after!!.contains("Buy oat milk"))
        assertTrue("audit entry must have an undo token", audit.undoToken.isNotBlank())
    }

    @Test
    fun `create with no content throws and writes nothing`() = runTest {
        try {
            handler.create(CreateNoteRequest(source = NoteSource.TEXT))
            fail("expected EmptyCaptureException")
        } catch (ignored: EmptyCaptureException) {
            // expected
        }
        assertEquals(0, db.noteDao().countActive())
        assertEquals(0, db.derivedDao().count())
        assertEquals(0, db.auditDao().count())
    }

    @Test
    fun `original text is kept verbatim`() = runTest {
        val original = "  keep   my  spacing  "
        val note = handler.create(CreateNoteRequest(source = NoteSource.TEXT, rawText = original))
        assertEquals(original, db.noteDao().byId(note.id)?.rawText)
    }

    @Test
    fun `edit then undo restores the previous text`() = runTest {
        val note = handler.create(CreateNoteRequest(source = NoteSource.TEXT, rawText = "first"))
        clock = 11_000
        handler.updateRawText(note.id, "second")
        val after = db.noteDao().byId(note.id)!!
        assertEquals("second", after.rawText)
        assertEquals(2, after.revision)

        val editAudit = db.auditDao().recentFor("note", note.id, limit = 2).first()
        assertEquals(AuditAction.UPDATE_RAW_TEXT, editAudit.action)

        clock = 12_000
        handler.undo(editAudit.id)
        val restored = db.noteDao().byId(note.id)!!
        assertEquals("first", restored.rawText)
        assertEquals(3, restored.revision)
        assertEquals(
            AuditAction.UNDO,
            db.auditDao().recentFor("note", note.id, 1).single().action,
        )
    }

    @Test
    fun `undo of a create soft-deletes then restore brings the note back`() = runTest {
        val note = handler.create(CreateNoteRequest(source = NoteSource.TEXT, rawText = "temp"))
        val createAudit = db.auditDao().recentFor("note", note.id, 1).single()

        clock = 11_000
        handler.undo(createAudit.id)
        assertEquals(0, db.noteDao().countActive())
        assertNotNull("note row must still exist (soft delete only)", db.noteDao().byId(note.id)?.deletedAt)

        clock = 12_000
        handler.restore(note.id)
        assertEquals(1, db.noteDao().countActive())
    }

    @Test
    fun `status change is audited and reversible`() = runTest {
        val note = handler.create(CreateNoteRequest(source = NoteSource.TEXT, rawText = "task"))
        clock = 11_000
        handler.setStatus(note.id, NoteStatus.DONE)
        assertEquals(NoteStatus.DONE, db.noteDao().byId(note.id)?.status)

        val statusAudit = db.auditDao().recentFor("note", note.id, 1).single()
        clock = 12_000
        handler.undo(statusAudit.id)
        assertEquals(NoteStatus.INBOX, db.noteDao().byId(note.id)?.status)
    }

    @Test
    fun `missing note operations throw, not silently no-op`() = runTest {
        try {
            handler.updateRawText("nope", "text")
            fail("expected NoteMissingException")
        } catch (ignored: NoteMissingException) {
            // expected
        }
    }

    @Test
    fun `share-sourced note keeps source metadata`() = runTest {
        val req = ShareIngest.toRequest(
            text = "https://example.org/unit-3-pacing",
            subject = "Unit 3 pacing",
            sourcePackage = "com.android.chrome",
        )!!
        val note = handler.create(req)
        assertEquals(NoteSource.SHARE, note.source)
        assertEquals("https://example.org/unit-3-pacing", note.sourceUrl)
        assertEquals("com.android.chrome", note.sourceApp)
        assertEquals("Unit 3 pacing", note.title)
    }
}

