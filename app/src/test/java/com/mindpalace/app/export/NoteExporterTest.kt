package com.mindpalace.app.export

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.mindpalace.app.core.commands.CreateNoteRequest
import com.mindpalace.app.core.commands.NoteCommandHandler
import com.mindpalace.app.core.db.MindPalaceDatabase
import com.mindpalace.app.core.model.NoteSource
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Checkpoint 1 requirement: "export round-trips". */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NoteExporterTest {

    private lateinit var db: MindPalaceDatabase
    private lateinit var handler: NoteCommandHandler

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            MindPalaceDatabase::class.java,
        ).setDriver(BundledSQLiteDriver()).build()
        handler = NoteCommandHandler(db, now = { 10_000 }, newId = { java.util.UUID.randomUUID().toString() })
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `json export round-trips active notes and excludes deleted ones`() = runTest {
        val a = handler.create(
            CreateNoteRequest(source = NoteSource.TEXT, title = "Groceries", rawText = "oat milk"),
        )
        handler.create(CreateNoteRequest(source = NoteSource.TEXT, rawText = "continental drift quiz"))
        val doomed = handler.create(CreateNoteRequest(source = NoteSource.TEXT, rawText = "throw me away"))
        handler.softDelete(doomed.id)

        val exporter = NoteExporter(db.noteDao(), now = { 9_999 })
        val payload = NoteExporter.decode(exporter.buildJson())

        assertEquals(1, payload.schemaVersion)
        assertEquals(2, payload.noteCount)
        assertEquals(2, payload.notes.size)

        val byId = payload.notes.associateBy { it.id }
        assertEquals("oat milk", byId[a.id]?.rawText)
        assertEquals("Groceries", byId[a.id]?.title)
        assertEquals("TEXT", byId[a.id]?.source)
        assertEquals("INBOX", byId[a.id]?.status)
        assertFalse("deleted notes must not be exported", byId.containsKey(doomed.id))
    }

    @Test
    fun `markdown export is human-readable and contains note text`() = runTest {
        handler.create(
            CreateNoteRequest(source = NoteSource.TEXT, title = "Groceries", rawText = "oat milk"),
        )
        val md = NoteExporter(db.noteDao(), now = { 123 }).buildMarkdown()

        assertTrue(md.contains("# Mind Palace export"))
        assertTrue(md.contains("## Groceries"))
        assertTrue(md.contains("oat milk"))
    }

    @Test
    fun `exports write to files in the caller-given (private) directory`() = runTest {
        handler.create(CreateNoteRequest(source = NoteSource.TEXT, rawText = "file round trip"))
        val exporter = NoteExporter(db.noteDao(), now = { 5_000 })
        val dir = ApplicationProvider.getApplicationContext<Context>().filesDir

        val jsonFile = exporter.writeJson(dir)
        val mdFile = exporter.writeMarkdown(dir.resolve("md"))

        assertTrue(jsonFile.exists())
        assertTrue(jsonFile.readText().startsWith("{"))
        assertTrue("file round trip" in jsonFile.readText())
        assertTrue(mdFile.exists())
        assertTrue(mdFile.readText().contains("file round trip"))
    }
}
