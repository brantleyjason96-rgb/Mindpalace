package com.mindpalace.app.core.db

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.mindpalace.app.core.model.DEFAULT_CATEGORIES
import java.time.Instant

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

/**
 * Adds `fire_at`/`fired_at` to `reminders` (plan 11 Part A) — reliable time-reminder scheduling
 * needs a parsed epoch-millis column instead of re-parsing the ISO `spec` string on every
 * scheduling decision, and a `fired_at` marker so firing is idempotent and a fired reminder can
 * show as "Due" instead of disappearing or re-firing. Backfills `fire_at` for existing TIME rows
 * by parsing their `spec`; an unparseable spec is left NULL rather than guessed — plan 11's own
 * `ReminderPlanner` treats a null `fire_at` as "nothing to schedule", which is the right behavior
 * for a row whose time was already ungoverned.
 *
 * Numbered 3→4, not the plan's "v6" placeholder: `docs/plans/INDEX.md` says whichever
 * schema-touching plan runs first takes the next free version (plans 10/12's v4/v5 haven't run
 * yet), and v4 was still free when this one landed.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE reminders ADD COLUMN fire_at INTEGER")
        connection.execSQL("ALTER TABLE reminders ADD COLUMN fired_at INTEGER")

        val statement = connection.prepare("SELECT id, spec FROM reminders WHERE type = 'TIME'")
        val backfills = mutableListOf<Pair<String, Long>>()
        try {
            while (statement.step()) {
                val id = statement.getText(0)
                val spec = statement.getText(1)
                val millis = runCatching { Instant.parse(spec).toEpochMilli() }.getOrNull()
                if (millis != null) backfills += id to millis
            }
        } finally {
            statement.close()
        }
        for ((id, millis) in backfills) {
            connection.execSQL(
                "UPDATE reminders SET fire_at = $millis WHERE id = '${id.replace("'", "''")}'",
            )
        }
    }
}

/** Every migration, in order (plan 14 G4). `AppContainer` and every test use this list —
 *  nothing else — so a new migration can never be written and then forgotten at the registration
 *  point (the app would otherwise crash on upgrade with "no migration found"). */
val ALL_MIGRATIONS: List<Migration> = listOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
