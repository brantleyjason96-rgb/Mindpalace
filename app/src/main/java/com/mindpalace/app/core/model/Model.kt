package com.mindpalace.app.core.model

import kotlinx.serialization.Serializable

/**
 * Domain vocabulary for the canonical data model (plan §6).
 * These enums are persisted BY NAME via Room's built-in enum mapping — never rename a constant
 * after release; add a new one and migrate data instead.
 */

/** How a note first entered the palace (§6 notes.source). */
enum class NoteSource { TEXT, VOICE, PHOTO, SHARE, ATTACHMENT, INK }

/** Lifecycle status of a note (§6 notes.status). */
enum class NoteStatus { INBOX, ACTIVE, DONE, ARCHIVED }

/** The kind of derived fact (§6 derived.field). */
enum class DerivedField { CATEGORY, TAG, DUE_AT, PERSON, PLACE, TOPIC }

enum class LinkType { EXPLICIT, RULE, AI }

enum class LinkState { SUGGESTED, CONFIRMED, REJECTED }

enum class ReminderType { TIME, PLACE, PERSON }

enum class ReminderState { ACTIVE, DONE, CANCELLED }

enum class ProposalKind { CATEGORY, TAG, TOPIC, LINK, DUE_DATE, PERSON, PLACE }

enum class ProposalState { PENDING, APPROVED, REJECTED, UNDONE, AUTO_APPLIED }

/** States of the durable night/background queue (§6 jobs). */
enum class JobState { PENDING, RUNNING, RETRY, SUCCEEDED, FAILED }

/**
 * Who/what caused an audit entry. The plan listed (user|rule|ai); SYSTEM is an addition for
 * mechanical attribution (default Inbox seeding, future night-loop lease recovery) — D7 in
 * docs/DECISIONS.md.
 */
enum class AuditActor { USER, RULE, AI, SYSTEM }

/** Canonical mutations recorded for the `note` entity. */
enum class AuditAction { CREATE, UPDATE_RAW_TEXT, SET_STATUS, SOFT_DELETE, RESTORE, UNDO }

/**
 * Origin of a derived fact (§6 derived.origin). Wire format: "user" | "system" | "rule:\<id\>" |
 * "ai:\<runId\>". Stored encoded in a TEXT column; parse with [Origin.parse]. The wire format is
 * part of the data contract — never change it.
 */
sealed interface Origin {
    data object UserOrigin : Origin
    data object SystemOrigin : Origin
    data class RuleOrigin(val ruleId: String) : Origin
    data class AiOrigin(val runId: String) : Origin

    fun encode(): String = when (this) {
        UserOrigin -> "user"
        SystemOrigin -> "system"
        is RuleOrigin -> "rule:$ruleId"
        is AiOrigin -> "ai:$runId"
    }

    companion object {
        fun parse(raw: String): Origin? = when {
            raw == "user" -> UserOrigin
            raw == "system" -> SystemOrigin
            raw.startsWith("rule:") -> RuleOrigin(raw.removePrefix("rule:"))
            raw.startsWith("ai:") -> AiOrigin(raw.removePrefix("ai:"))
            else -> null
        }
    }
}

/**
 * The six starter categories (plan §2). Seeded into the `categories` table by MIGRATION_1_2
 * (D20) — editable (add-only) from there on via Settings > Categories. This constant is now only
 * the seed list, not the live source of truth; read `CategoryDao.observeAll()` for that.
 */
val DEFAULT_CATEGORIES =
    listOf("Task/Errand", "Classroom", "Curriculum/Pacing", "App idea", "Personal", "Inbox")

/** Every new note starts here (derived CATEGORY, origin=system) until a rule or the user sorts it. */
const val CATEGORY_INBOX = "Inbox"

/** The one entity type the audit trail knows in v1. */
const val AUDIT_ENTITY_NOTE = "note"

/**
 * Audited field snapshot of a note row (§6 audit.before/after). Stored as JSON text.
 * imagePath/attachmentPath/attachmentFileName added in plan 01 H1/H2 — new fields default to
 * null so older persisted JSON (written before this change) still decodes.
 */
@Serializable
data class NoteSnapshot(
    val id: String,
    val status: String,
    val revision: Int,
    val title: String? = null,
    val rawText: String? = null,
    val transcript: String? = null,
    val ocrText: String? = null,
    val sourceUrl: String? = null,
    val sourceApp: String? = null,
    val deletedAt: Long? = null,
    val imagePath: String? = null,
    val attachmentPath: String? = null,
    val attachmentFileName: String? = null,
)
