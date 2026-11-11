package com.mindpalace.app

import android.app.Application
import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.mindpalace.app.ai.AiNightRunner
import com.mindpalace.app.ai.AiNightWorker
import com.mindpalace.app.core.commands.NoteCommandHandler
import com.mindpalace.app.core.db.ALL_MIGRATIONS
import com.mindpalace.app.core.db.MindPalaceDatabase
import com.mindpalace.app.core.reminders.NotificationHelper
import com.mindpalace.app.export.NoteExporter
import androidx.work.Configuration
import androidx.work.WorkManager

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
        .addMigrations(*ALL_MIGRATIONS.toTypedArray())
        .build()

    val noteDao: com.mindpalace.app.core.db.NoteDao
        get() = database.noteDao()

    val commands = NoteCommandHandler(database)

    val aiNightRunner = AiNightRunner(database)

    val exporter = NoteExporter(database.noteDao())

    /** Exports land here (private, never shared implicitly). */
    val exportDir: java.io.File = context.applicationContext.filesDir.resolve("exports")

    /** Camera/OCR captures land here (private); matches `photos` in file_paths.xml. */
    val photosDir: java.io.File = context.applicationContext.filesDir.resolve("photos").apply { mkdirs() }

    /** Attach-file captures land here (private, any file type, never previewed/opened by name). */
    val attachmentsDir: java.io.File = context.applicationContext.filesDir.resolve("attachments").apply { mkdirs() }
}

class MindPalaceApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.createChannel(this)
        ensureWorkManagerInitialized()
        AiNightWorker.schedule(this)
    }

    /**
     * WorkManager normally self-initializes via a ContentProvider before Application.onCreate()
     * ever runs (true on a real device). That ContentProvider path doesn't reliably fire under
     * Robolectric for this library version, so [WorkManager.getInstance] would otherwise throw
     * for every unit test in the module, not just AI ones — manual [WorkManager.initialize] is
     * the documented fallback, but calling it unconditionally throws "already initialized" on a
     * real device where the provider DID already run. Try first, initialize only if that's
     * genuinely needed.
     */
    private fun ensureWorkManagerInitialized() {
        try {
            WorkManager.getInstance(this)
        } catch (_: IllegalStateException) {
            WorkManager.initialize(this, Configuration.Builder().build())
        }
    }
}
