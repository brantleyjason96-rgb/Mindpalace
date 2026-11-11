package com.mindpalace.app.core.db

import android.content.Context
import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
}
