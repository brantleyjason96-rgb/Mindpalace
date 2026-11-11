package com.mindpalace.app.core.db

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Verifies MIGRATION_1_2 adds `categories` seeded with the six defaults, in order (D20). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MigrationTest {

    private val testDbFile =
        ApplicationProvider.getApplicationContext<Context>().getDatabasePath("migration-test")

    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        file = testDbFile,
        driver = BundledSQLiteDriver(),
        databaseClass = MindPalaceDatabase::class,
    )

    @Test
    fun `migrate 1 to 2 seeds the six default categories in order`() = runTest {
        helper.createDatabase(1).close()

        val connection = helper.runMigrationsAndValidate(2, listOf(MIGRATION_1_2))
        val statement = connection.prepare("SELECT name FROM categories ORDER BY position ASC")
        val names = mutableListOf<String>()
        while (statement.step()) {
            names.add(statement.getText(0))
        }
        statement.close()
        connection.close()

        assertEquals(
            listOf("Task/Errand", "Classroom", "Curriculum/Pacing", "App idea", "Personal", "Inbox"),
            names,
        )
    }

    @Test
    fun `migrate 2 to 3 adds nullable attachment columns without touching existing rows`() = runTest {
        val db = helper.createDatabase(2)
        db.execSQL(
            "INSERT INTO notes (id, created_at, updated_at, source, status, revision) " +
                "VALUES ('n1', 1000, 1000, 'TEXT', 'INBOX', 1)",
        )
        db.close()

        val connection = helper.runMigrationsAndValidate(3, listOf(MIGRATION_1_2, MIGRATION_2_3))
        val statement = connection.prepare("SELECT attachment_path, attachment_file_name FROM notes WHERE id = 'n1'")
        assertEquals(true, statement.step())
        assertEquals(true, statement.isNull(0))
        assertEquals(true, statement.isNull(1))
        statement.close()
        connection.close()
    }

    // ----------------------------------------------------------- plan 14: full-chain template

    @Test
    fun `migrating 1 to latest keeps every row`() = runTest {
        val v1 = helper.createDatabase(1)
        MigrationFixtures.seedV1(v1)
        val before = MigrationFixtures.rowCounts(v1, MigrationFixtures.V1_TABLES)
        v1.close()

        val connection = helper.runMigrationsAndValidate(LATEST, ALL_MIGRATIONS)
        val after = MigrationFixtures.rowCounts(connection, MigrationFixtures.V1_TABLES)
        assertEquals(before, after)

        val statement = connection.prepare("SELECT raw_text FROM notes WHERE id = 'n1'")
        assertEquals(true, statement.step())
        assertEquals("seismograph reading", statement.getText(0))
        statement.close()
        connection.close()
    }

    @Test
    fun `fts still finds old notes after migrating`() = runTest {
        val v1 = helper.createDatabase(1)
        MigrationFixtures.seedV1(v1)
        v1.close()

        val connection = helper.runMigrationsAndValidate(LATEST, ALL_MIGRATIONS)
        val statement = connection.prepare("SELECT COUNT(*) FROM notes_fts WHERE notes_fts MATCH '\"seismograph\"'")
        statement.step()
        assertEquals(1L, statement.getLong(0))
        statement.close()

        // Throws if the shadow FTS5 index drifted from the content table during migration.
        connection.execSQL("INSERT INTO notes_fts(notes_fts) VALUES('integrity-check')")
        connection.close()
    }

    @Test
    fun `fts triggers survive migration`() = runTest {
        val v1 = helper.createDatabase(1)
        MigrationFixtures.seedV1(v1)
        v1.close()

        val connection = helper.runMigrationsAndValidate(LATEST, ALL_MIGRATIONS)
        connection.execSQL(
            "INSERT INTO notes (id, created_at, updated_at, source, raw_text, status, revision) " +
                "VALUES ('n3', 3000, 3000, 'TEXT', 'isobar pattern', 'INBOX', 1)",
        )
        val statement = connection.prepare("SELECT COUNT(*) FROM notes_fts WHERE notes_fts MATCH '\"isobar\"'")
        statement.step()
        assertEquals(1L, statement.getLong(0))
        statement.close()
        connection.close()
    }

    @Test
    fun `real database opens a migrated file`() = runTest {
        val v1 = helper.createDatabase(1)
        MigrationFixtures.seedV1(v1)
        v1.close()
        helper.runMigrationsAndValidate(LATEST, ALL_MIGRATIONS).close()

        val db = Room.databaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            MindPalaceDatabase::class.java,
            testDbFile.name,
        ).setDriver(BundledSQLiteDriver()).addMigrations(*ALL_MIGRATIONS.toTypedArray()).build()

        assertEquals(2, db.noteDao().countActive())
        assertEquals(6, db.categoryDao().observeAll().first().size)
        db.close()
    }

    @Test
    fun `migration list is contiguous and reaches the current version`() {
        val expected = (1 until LATEST).map { it to it + 1 }
        val actual = ALL_MIGRATIONS.map { it.startVersion to it.endVersion }
        assertEquals(expected, actual)
    }

    @Test
    fun `a schema file exists for every version`() {
        for (v in 1..LATEST) {
            val file = File("schemas/com.mindpalace.app.core.db.MindPalaceDatabase/$v.json")
            assertTrue("missing schema file for version $v: ${file.path}", file.exists())
        }
    }

    @Test
    fun `destructive fallback is never configured`() {
        val mainSrc = File("src/main/java/com/mindpalace/app")
        val offender = mainSrc.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .firstOrNull { it.readText().contains("fallbackToDestructiveMigration") }
        assertEquals("fallbackToDestructiveMigration must never be configured — it erases every note on upgrade", null, offender)
    }

    private companion object {
        /** Read from the `@Database` annotation when retained at runtime; 3 is the fallback if
         *  not (T-MIG6 keeps this in sync with reality either way, by asserting a schema file
         *  exists for every version up to this one). */
        val LATEST: Int = runCatching {
            MindPalaceDatabase::class.java.getAnnotation(Database::class.java)!!.version
        }.getOrElse { 3 }
    }
}
