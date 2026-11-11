package com.mindpalace.app.core.db

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.mindpalace.app.core.model.DEFAULT_CATEGORIES

/** Adds the user-editable `categories` table (D20), seeded with the six defaults in order. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS categories (" +
                "name TEXT NOT NULL PRIMARY KEY, position INTEGER NOT NULL, created_at INTEGER NOT NULL)",
        )
        val now = System.currentTimeMillis()
        DEFAULT_CATEGORIES.forEachIndexed { index, name ->
            connection.execSQL(
                "INSERT INTO categories (name, position, created_at) VALUES ('${name.replace("'", "''")}', $index, $now)",
            )
        }
    }
}

/** Adds the two nullable columns ATTACHMENT-source notes need (D25); existing rows are
 *  unaffected (both default to NULL). */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE notes ADD COLUMN attachment_path TEXT")
        connection.execSQL("ALTER TABLE notes ADD COLUMN attachment_file_name TEXT")
    }
}

/** Every migration, in order (plan 14 G4). `AppContainer` and every test use this list —
 *  nothing else — so a new migration can never be written and then forgotten at the registration
 *  point (the app would otherwise crash on upgrade with "no migration found"). */
val ALL_MIGRATIONS: List<Migration> = listOf(MIGRATION_1_2, MIGRATION_2_3)
