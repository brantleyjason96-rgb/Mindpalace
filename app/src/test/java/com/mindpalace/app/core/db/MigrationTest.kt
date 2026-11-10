package com.mindpalace.app.core.db

import android.content.Context
import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
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
}
