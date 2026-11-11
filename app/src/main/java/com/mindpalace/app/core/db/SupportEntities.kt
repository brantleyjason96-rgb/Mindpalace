package com.mindpalace.app.core.db

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import com.mindpalace.app.core.model.DerivedField
import com.mindpalace.app.core.model.LinkState
import com.mindpalace.app.core.model.LinkType
import com.mindpalace.app.core.model.ReminderState
import com.mindpalace.app.core.model.ReminderType

/**
 * Derived facts (§6 `derived`): every category/tag/due-date/person/place/topic assignment lives
 * here with its ORIGIN (rule id, ai run, user, or system) and confidence — so the Review UI can
 * always answer "who decided this?" (Checkpoint 3: "every derived field shows its origin").
 */
@Entity(
    tableName = "derived",
    primaryKeys = ["note_id", "field", "value", "origin"],
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["note_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("note_id")],
)
data class DerivedEntity(
    @ColumnInfo(name = "note_id")
    val noteId: String,

    @ColumnInfo(name = "field")
    val field: DerivedField,

    @ColumnInfo(name = "value")
    val value: String,

    /** Encoded [com.mindpalace.app.core.model.Origin] ("user" | "system" | "rule:<id>" | "ai:<run>"). */
    @ColumnInfo(name = "origin")
    val origin: String,

    @ColumnInfo(name = "confidence")
    val confidence: Float = 1f,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)

/**
 * User-editable category vocabulary (D20) — the six defaults (plan §2: "not yet confirmed; use
 * these and make them editable") plus whatever the user adds in Settings. `name` is the primary
 * key since categories are simple unique labels; no separate id needed. Append-only for now, same
 * reasoning as topics/people/places (D18): nothing safely renames/removes a category already
 * referenced by a rule action or a derived CATEGORY fact without a cascade story this doesn't
 * need yet.
 */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "name")
    val name: String,

    /** Display order; new categories append at the end. */
    @ColumnInfo(name = "position")
    val position: Int,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)

/** Controlled vocabulary for topics (§6). AI must choose from this vocabulary first (§8 Tier B). */
@Entity(tableName = "topics")
data class TopicEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    /** JSON array of alias strings. */
    @ColumnInfo(name = "aliases")
    val aliases: String = "[]",

    /** Parent topic id, or null for roots. */
    @ColumnInfo(name = "parent")
    val parent: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    /** Encoded Origin of creation ("user" | "ai:<run>"). */
    @ColumnInfo(name = "created_by")
    val createdBy: String,
)

/**
 * Obsidian-style links (§11): explicit [[name]], rule-made (shared person/tag/place), or
 * AI-suggested (Tier B, carries an evidence quote). State machine: suggested -> confirmed/rejected.
 */
@Entity(
    tableName = "note_links",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["from_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["to_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("from_id"), Index("to_id"), Index("state")],
)
data class NoteLinkEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "from_id")
    val fromId: String,

    @ColumnInfo(name = "to_id")
    val toId: String,

    @ColumnInfo(name = "type")
    val type: LinkType,

    @ColumnInfo(name = "reason")
    val reason: String,

    /** §8 Tier B rule: AI links MUST carry an evidence quote found in the source note. */
    @ColumnInfo(name = "evidence_quote")
    val evidenceQuote: String? = null,

    @ColumnInfo(name = "state")
    val state: LinkState,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)

/** Reminder rows (§6; the surfacing engine arrives in Phase 4). */
@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["note_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("note_id"), Index("state")],
)
data class ReminderEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "note_id")
    val noteId: String,

    @ColumnInfo(name = "type")
    val type: ReminderType,

    /** Opaque spec (ISO datetime / place id / person id) interpreted by the surfacing engine. */
    @ColumnInfo(name = "spec")
    val spec: String,

    @ColumnInfo(name = "state")
    val state: ReminderState,

    @ColumnInfo(name = "snoozed_until")
    val snoozedUntil: Long? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    /** Parsed epoch millis of [spec] for a TIME reminder (plan 11 Part A, schema v4) — avoids
     *  re-parsing the ISO string on every read/scheduling decision. Null for non-TIME reminders
     *  or an unparseable spec. [ReminderPlanner] is the only thing that reads this to decide
     *  whether/how to schedule. */
    @ColumnInfo(name = "fire_at")
    val fireAt: Long? = null,

    /** Set once this reminder has posted its notification — makes firing idempotent and lets the
     *  Reminders screen show it under "Due" instead of re-firing it on every reschedule pass. */
    @ColumnInfo(name = "fired_at")
    val firedAt: Long? = null,
)

/**
 * People (§6). NEVER auto-merged, ever — two plausible matches must ask, not guess (§7).
 * Aliases are a JSON array; growing them is an explicitly approved proposal.
 */
@Entity(tableName = "people", indices = [Index("name")])
data class PersonEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    /** JSON array of alias strings. */
    @ColumnInfo(name = "aliases")
    val aliases: String = "[]",

    /** Future hook: stable contact/calendar key. Never synced automatically. */
    @ColumnInfo(name = "external_ref")
    val externalRef: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)

/** Places (§6): school, home, grocery store, parents' house (+ aliases). Geofence wiring is Phase 4. */
@Entity(tableName = "places", indices = [Index("name")])
data class PlaceEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    /** JSON array of alias strings. */
    @ColumnInfo(name = "aliases")
    val aliases: String = "[]",

    @ColumnInfo(name = "latitude")
    val latitude: Double? = null,

    @ColumnInfo(name = "longitude")
    val longitude: Double? = null,

    @ColumnInfo(name = "radius_meters")
    val radiusMeters: Float? = null,

    /** Geofence request id once Phase 4 registers one. */
    @ColumnInfo(name = "external_ref")
    val externalRef: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)

/** Rule registry (§6; engine + matcher/action schemas arrive in Phase 3, versioned + dry-runnable). */
@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "version")
    val version: Int,

    @ColumnInfo(name = "enabled")
    val enabled: Boolean,

    /** JSON description of the matcher (Phase 3 schema). */
    @ColumnInfo(name = "matcher")
    val matcher: String,

    /** JSON description of the action (Phase 3 schema). */
    @ColumnInfo(name = "action")
    val action: String,
)

