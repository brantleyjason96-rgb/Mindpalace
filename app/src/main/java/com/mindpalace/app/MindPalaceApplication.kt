package com.mindpalace.app

import android.app.Application
import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.mindpalace.app.core.commands.NoteCommandHandler
import com.mindpalace.app.core.db.MindPalaceDatabase
import com.mindpalace.app.export.NoteExporter

/**
 * Tiny manual DI container (docs/DECISIONS.md D8 — deliberately no Hilt while the backend is
 * this small). The future UI session should build on exactly these three objects:
 *  - [database] / [noteDao] for reads (Flow-based),
 *  - [commands] for ALL canonical writes (never touch DAOs from UI),
 *  - [exporter] for manual exports.
 *
 * BundledSQLiteDriver (real SQLite with FTS5 compiled in) is used on purpose — see D3.
 */
class AppContainer(context: Context) {

    val database: MindPalaceDatabase = Room.databaseBuilder(
        context.applicationContext,
        MindPalaceDatabase::class.java,
        MindPalaceDatabase.NAME,
    )
        .setDriver(BundledSQLiteDriver())
        .build()

    val noteDao: com.mindpalace.app.core.db.NoteDao
        get() = database.noteDao()

    val commands = NoteCommandHandler(database)

    val exporter = NoteExporter(database.noteDao())

    /** Exports land here (private, never shared implicitly). */
    val exportDir: java.io.File = context.applicationContext.filesDir.resolve("exports")

    /** Camera/OCR captures land here (private); matches `photos` in file_paths.xml. */
    val photosDir: java.io.File = context.applicationContext.filesDir.resolve("photos").apply { mkdirs() }
}

class MindPalaceApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
