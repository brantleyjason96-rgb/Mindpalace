package com.mindpalace.app.core.db

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import com.mindpalace.app.core.model.AuditAction
import com.mindpalace.app.core.model.AuditActor
import com.mindpalace.app.core.model.JobState
import com.mindpalace.app.core.model.ProposalKind
import com.mindpalace.app.core.model.ProposalState

/**
 * AI index (§6 ai_index; §8 Tier A): auto-generated, never replaces user fields, always shown
 * with an "AI" label, user can delete any item. keywords/entities/open_questions are JSON
 * payloads so the validator can enforce structure before storage (§3.4).
 */
@Entity(
    tableName = "ai_index",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["note_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("note_id"), Index("run_id")],
)
data class AiIndexEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "note_id")
    val noteId: String,

    @ColumnInfo(name = "summary")
    val summary: String,

    /** JSON array of keyword strings. */
    @ColumnInfo(name = "keywords")
    val keywords: String = "[]",

    /** JSON object of entities, e.g. {"people":[...],"places":[...]}. */
    @ColumnInfo(name = "entities")
    val entities: String = "{}",

    /** JSON array of open questions. */
    @ColumnInfo(name = "open_questions")
    val openQuestions: String = "[]",

    @ColumnInfo(name = "model")
    val model: String,

    @ColumnInfo(name = "run_id")
    val runId: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)

/**
 * Tier-B proposals (§6/§8): every field is chosen so the validator can enforce policy BEFORE
 * storage — evidence_quote MUST be a literal substring of the note (checked in the AI phase's
 * validator), confidence must beat a threshold, and state records the decision even after
 * rejection (no re-proposing rejected things for the same note).
 */
@Entity(
    tableName = "proposals",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["note_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("note_id"), Index("state"), Index("run_id")],
)
data class ProposalEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "note_id")
    val noteId: String,

    @ColumnInfo(name = "kind")
    val kind: ProposalKind,

    @ColumnInfo(name = "old_value")
    val oldValue: String? = null,

    @ColumnInfo(name = "new_value")
    val newValue: String,

    @ColumnInfo(name = "evidence_quote")
    val evidenceQuote: String,

    @ColumnInfo(name = "confidence")
    val confidence: Float,

    @ColumnInfo(name = "run_id")
    val runId: String,

    @ColumnInfo(name = "state")
    val state: ProposalState,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)

/** Audit trail of AI night runs (§8 mechanical guardrails; health screen comes with the AI phase). */
@Entity(tableName = "ai_runs")
data class AiRunEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "model")
    val model: String,

    @ColumnInfo(name = "model_version")
    val modelVersion: String,

    @ColumnInfo(name = "prompt_version")
    val promptVersion: String,

    @ColumnInfo(name = "started")
    val started: Long,

    @ColumnInfo(name = "finished")
    val finished: Long? = null,

    @ColumnInfo(name = "outcome")
    val outcome: String,

    @ColumnInfo(name = "notes_processed")
    val notesProcessed: Int = 0,
)

/**
 * Audit trail (§3.7 "everything reversible"): before/after are [com.mindpalace.app.core.model.NoteSnapshot]
 * JSON for the note entity in v1; undo_token is unique per change. NOTHING may write rows except
 * the command handler.
 */
@Entity(
    tableName = "audit",
    indices = [
        Index("entity_id"),
        Index(value = ["undo_token"], unique = true),
        Index("at"),
    ],
)
data class AuditEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "entity")
    val entity: String,

    @ColumnInfo(name = "entity_id")
    val entityId: String,

    @ColumnInfo(name = "action")
    val action: AuditAction,

    @ColumnInfo(name = "actor")
    val actor: AuditActor,

    @ColumnInfo(name = "before")
    val before: String? = null,

    @ColumnInfo(name = "after")
    val after: String? = null,

    @ColumnInfo(name = "undo_token")
    val undoToken: String,

    @ColumnInfo(name = "at")
    val at: Long,
)

/** Durable work queue (§6 jobs). Consumed from the Phase 5 night loop; table exists from v1. */
@Entity(
    tableName = "jobs",
    indices = [Index(value = ["idempotency_key"], unique = true), Index("state")],
)
data class JobEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    /** Idempotency per (note revision, task, prompt version) — §8 guardrail. */
    @ColumnInfo(name = "idempotency_key")
    val idempotencyKey: String,

    /** Job kind discriminator, e.g. "ai.index", "ai.propose", "rules.rerun". */
    @ColumnInfo(name = "kind")
    val kind: String,

    /** JSON payload. */
    @ColumnInfo(name = "payload")
    val payload: String,

    @ColumnInfo(name = "state")
    val state: JobState,

    @ColumnInfo(name = "attempts")
    val attempts: Int = 0,

    /** Lease deadline for crash recovery (stuck RUNNING jobs are re-leased). */
    @ColumnInfo(name = "lease_until")
    val leaseUntil: Long? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
