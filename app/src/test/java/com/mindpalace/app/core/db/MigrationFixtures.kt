package com.mindpalace.app.core.db

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/** Row fixture + counting helpers for `MigrationTest`'s full-chain tests (plan 14 G1). */
object MigrationFixtures {

    /** Every table that exists in schema v1 (`notes_fts` excluded — it's FTS5 shadow state, kept
     *  in sync by triggers, not a row count to compare). */
    val V1_TABLES = listOf(
        "notes", "derived", "topics", "note_links", "reminders", "people",
        "places", "rules", "ai_index", "proposals", "ai_runs", "audit", "jobs",
    )

    /**
     * Inserts one row into every table that exists in schema v1 — raw SQL only, since this runs
     * against a database still at v1 (no entity classes match it). Column lists come from
     * `app/schemas/.../1.json`; every `NOT NULL` column is given a value.
     */
    fun seedV1(connection: SQLiteConnection) {
        connection.execSQL(
            "INSERT INTO notes (id, created_at, updated_at, source, title, raw_text, status, revision) " +
                "VALUES ('n1', 1000, 1000, 'TEXT', 'Seismograph', 'seismograph reading', 'INBOX', 1)",
        )
        connection.execSQL(
            "INSERT INTO notes (id, created_at, updated_at, source, raw_text, status, revision) " +
                "VALUES ('n2', 2000, 2000, 'TEXT', 'second note', 'INBOX', 1)",
        )
        connection.execSQL(
            "INSERT INTO derived (note_id, field, value, origin, confidence, created_at) " +
                "VALUES ('n1', 'CATEGORY', 'Inbox', 'system', 1.0, 1000)",
        )
        connection.execSQL(
            "INSERT INTO topics (id, name, aliases, created_at, created_by) " +
                "VALUES ('t1', 'Earthquakes', '[]', 1000, 'user')",
        )
        connection.execSQL(
            "INSERT INTO note_links (from_id, to_id, type, reason, state, created_at) " +
                "VALUES ('n1', 'n2', 'EXPLICIT', 'manual link', 'CONFIRMED', 1000)",
        )
        connection.execSQL(
            "INSERT INTO reminders (id, note_id, type, spec, state, created_at) " +
                "VALUES ('r1', 'n1', 'TIME', '2026-12-01T10:00:00', 'ACTIVE', 1000)",
        )
        connection.execSQL(
            "INSERT INTO people (id, name, aliases, created_at) VALUES ('p1', 'Maya Lin', '[]', 1000)",
        )
        connection.execSQL(
            "INSERT INTO places (id, name, aliases, created_at) VALUES ('pl1', 'School', '[]', 1000)",
        )
        connection.execSQL(
            "INSERT INTO rules (id, version, enabled, matcher, action) VALUES ('rule1', 1, 1, '{}', '{}')",
        )
        connection.execSQL(
            "INSERT INTO ai_index (note_id, summary, keywords, entities, open_questions, model, run_id, created_at) " +
                "VALUES ('n1', 'test summary', '[]', '{}', '[]', 'test-model', 'run-1', 1000)",
        )
        connection.execSQL(
            "INSERT INTO proposals (id, note_id, kind, new_value, evidence_quote, confidence, run_id, state, created_at, updated_at) " +
                "VALUES ('prop1', 'n1', 'CATEGORY', 'Personal', 'seismograph reading', 0.9, 'run-1', 'PENDING', 1000, 1000)",
        )
        connection.execSQL(
            "INSERT INTO ai_runs (id, model, model_version, prompt_version, started, outcome, notes_processed) " +
                "VALUES ('run-1', 'test-model', 'v1', 'v1', 1000, 'SUCCEEDED', 1)",
        )
        connection.execSQL(
            "INSERT INTO audit (entity, entity_id, action, actor, undo_token, at) " +
                "VALUES ('note', 'n1', 'CREATE', 'USER', 'token-1', 1000)",
        )
        connection.execSQL(
            "INSERT INTO jobs (id, idempotency_key, kind, payload, state, attempts, created_at, updated_at) " +
                "VALUES ('job1', 'key-1', 'test.job', '{}', 'PENDING', 0, 1000, 1000)",
        )
    }

    /** Row counts per table name, for before/after comparison. */
    fun rowCounts(connection: SQLiteConnection, tables: List<String>): Map<String, Long> =
        tables.associateWith { table ->
            val statement = connection.prepare("SELECT COUNT(*) FROM $table")
            try {
                statement.step()
                statement.getLong(0)
            } finally {
                statement.close()
            }
        }
}
