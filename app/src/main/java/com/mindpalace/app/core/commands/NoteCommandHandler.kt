package com.mindpalace.app.core.commands

import androidx.room3.withWriteTransaction
import com.mindpalace.app.core.db.AuditEntity
import com.mindpalace.app.core.db.DerivedEntity
import com.mindpalace.app.core.db.MindPalaceDatabase
import com.mindpalace.app.core.db.NoteEntity
import com.mindpalace.app.core.model.AUDIT_ENTITY_NOTE
import com.mindpalace.app.core.model.AuditAction
import com.mindpalace.app.core.model.AuditActor
import com.mindpalace.app.core.model.CATEGORY_INBOX
import com.mindpalace.app.core.model.DerivedField
import com.mindpalace.app.core.model.NoteSnapshot
import com.mindpalace.app.core.model.NoteStatus
import com.mindpalace.app.core.model.Origin
import com.mindpalace.app.core.rules.RuleEngine
import com.mindpalace.app.core.rules.toRule
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * The ONLY component allowed to mutate canonical records (plan §5). Every public operation is a
 * single Room transaction that (a) applies the change and (b) appends an audit row with a full
 * before/after snapshot — mechanically enforcing "save first" (§3.1), "everything reversible"
 * (§3.7) and "originals are sacred" (§3.2). Rules (Phase 3) and AI (Phase 5) never mutate
 * directly; they come through here (or, for AI organization changes, through validated
 * proposals only). Concurrency: mutations are revision-guarded; a lost race throws
 * [NoteConflictException] instead of silently clobbering.
 */
class NoteCommandHandler(
    private val database: MindPalaceDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    // ------------------------------------------------------------- create (share/typed/voice later)

    /**
     * Creates a new note: status INBOX, derived CATEGORY=Inbox (origin=system), revision 1 —
     * one transaction, then returns. This call IS the entire capture path; it must stay fast.
     */
    suspend fun create(request: CreateNoteRequest): NoteEntity {
        if (!request.hasCaptureContent()) throw EmptyCaptureException()

        val at = now()
        val note = NoteEntity(
            id = newId(),
            createdAt = at,
            updatedAt = at,
            source = request.source,
            sourceApp = request.sourceApp,
            sourceUrl = request.sourceUrl,
            title = request.title,
            rawText = request.rawText,
            transcript = request.transcript,
            imagePath = request.imagePath,
            ocrText = request.ocrText,
            status = NoteStatus.INBOX,
            revision = 1,
        )

        database.withWriteTransaction {
            database.noteDao().insert(note)
            database.derivedDao().upsert(
                DerivedEntity(
                    noteId = note.id,
                    field = DerivedField.CATEGORY,
                    value = CATEGORY_INBOX,
                    origin = Origin.SystemOrigin.encode(),
                    confidence = 1f,
                    createdAt = at,
                ),
            )
            database.auditDao().append(
                entry(
                    noteId = note.id,
                    action = AuditAction.CREATE,
                    actor = AuditActor.USER,
                    before = null,
                    after = note.snapshot(),
                    at = at,
                ),
            )
            applyRules(note, at)
        }
        return note
    }

    /**
     * Rules engine (D16): runs once, right after save, inside the same transaction. The first
     * enabled rule whose keywords match the note's text sets an ADDITIONAL category derived fact
     * (origin=rule:<id>), alongside the system Inbox default — it never overwrites or removes the
     * default, so this step is safely re-runnable (idempotent) and never loses the system origin.
     */
    private suspend fun applyRules(note: NoteEntity, at: Long) {
        val rules = database.ruleDao().enabled().mapNotNull { it.toRule() }
        if (rules.isEmpty()) return
        val searchableText = listOfNotNull(note.title, note.rawText, note.transcript, note.ocrText)
            .joinToString(" ")
        if (searchableText.isBlank()) return
        val match = RuleEngine.firstMatch(rules, searchableText) ?: return
        database.derivedDao().upsert(
            DerivedEntity(
                noteId = note.id,
                field = DerivedField.CATEGORY,
                value = match.action.category,
                origin = Origin.RuleOrigin(match.id).encode(),
                confidence = 1f,
                createdAt = at,
            ),
        )
    }

    // ------------------------------------------------------ single-field edits

    /** User (or an approved proposal) replaces raw text; the old text stays in the audit trail. */
    suspend fun updateRawText(noteId: String, newText: String, actor: AuditActor = AuditActor.USER) {
        doRawText(noteId, newText, AuditAction.UPDATE_RAW_TEXT, actor)
    }

    suspend fun setStatus(noteId: String, newStatus: NoteStatus, actor: AuditActor = AuditActor.USER) {
        doStatus(noteId, newStatus, AuditAction.SET_STATUS, actor)
    }

    /** Soft delete only (§3.2) — row + full history stay on disk; [restore] brings it back. */
    suspend fun softDelete(noteId: String, actor: AuditActor = AuditActor.USER) {
        val current = requireExisting(noteId, includeDeleted = true)
        val at = now()
        val applied = database.withWriteTransaction {
            val rows = database.noteDao().softDeleteGuarded(noteId, at, current.revision)
            if (rows == 1) {
                database.auditDao().append(
                    entry(
                        noteId,
                        AuditAction.SOFT_DELETE,
                        actor,
                        before = current.snapshot(),
                        after = current.copy(
                            deletedAt = at,
                            updatedAt = at,
                            revision = current.revision + 1,
                        ).snapshot(),
                        at = at,
                    ),
                )
            }
            rows
        }
        if (applied != 1) {
            throw if (current.deletedAt != null) NoteConflictException(noteId) else NoteMissingException(noteId)
        }
    }

    /** Un-deletes a soft-deleted note. */
    suspend fun restore(noteId: String, actor: AuditActor = AuditActor.USER) {
        val current = requireExisting(noteId, includeDeleted = true)
        if (current.deletedAt == null) return
        val at = now()
        val applied = database.withWriteTransaction {
            val rows = database.noteDao().restoreGuarded(noteId, at, current.revision)
            if (rows == 1) {
                database.auditDao().append(
                    entry(
                        noteId,
                        AuditAction.RESTORE,
                        actor,
                        before = current.snapshot(),
                        after = current.copy(
                            deletedAt = null,
                            updatedAt = at,
                            revision = current.revision + 1,
                        ).snapshot(),
                        at = at,
                    ),
                )
            }
            rows
        }
        if (applied != 1) throw NoteConflictException(noteId)
    }

    private suspend fun doRawText(
        noteId: String,
        newText: String,
        auditAction: AuditAction,
        actor: AuditActor,
    ) {
        val current = requireExisting(noteId)
        if (current.rawText == newText) return
        val at = now()
        val applied = database.withWriteTransaction {
            val rows = database.noteDao().updateRawTextGuarded(noteId, newText, at, current.revision)
            if (rows == 1) {
                database.auditDao().append(
                    entry(
                        noteId,
                        auditAction,
                        actor,
                        before = current.snapshot(),
                        after = current.copy(
                            rawText = newText,
                            updatedAt = at,
                            revision = current.revision + 1,
                        ).snapshot(),
                        at = at,
                    ),
                )
            }
            rows
        }
        if (applied != 1) throw NoteConflictException(noteId)
    }

    private suspend fun doStatus(
        noteId: String,
        newStatus: NoteStatus,
        auditAction: AuditAction,
        actor: AuditActor,
    ) {
        val current = requireExisting(noteId)
        if (current.status == newStatus) return
        val at = now()
        val applied = database.withWriteTransaction {
            val rows = database.noteDao().updateStatusGuarded(noteId, newStatus, at, current.revision)
            if (rows == 1) {
                database.auditDao().append(
                    entry(
                        noteId,
                        auditAction,
                        actor,
                        before = current.snapshot(),
                        after = current.copy(
                            status = newStatus,
                            updatedAt = at,
                            revision = current.revision + 1,
                        ).snapshot(),
                        at = at,
                    ),
                )
            }
            rows
        }
        if (applied != 1) throw NoteConflictException(noteId)
    }

    // -------------------------------------------------------------------- undo

    /**
     * Reverses the mutation recorded in audit row [auditId] (§3.7 "everything reversible"):
     *  - CREATE                       → soft-deletes the note ([restore] brings it back)
     *  - SOFT_DELETE                  → restores the note
     *  - UPDATE_RAW_TEXT / SET_STATUS → re-applies the recorded `before` snapshot
     *  - RESTORE / UNDO               → not directly undoable in v1
     * Every undo is itself audited (action=UNDO), so undo is undoable.
     */
    suspend fun undo(auditId: Long) {
        val original = database.auditDao().byId(auditId)
            ?: throw IllegalArgumentException("No audit entry $auditId")
        check(original.entity == AUDIT_ENTITY_NOTE) { "v1 can only undo note mutations" }

        when (original.action) {
            AuditAction.CREATE -> softDelete(original.entityId)
            AuditAction.SOFT_DELETE -> restore(original.entityId)
            AuditAction.UPDATE_RAW_TEXT -> {
                val before = original.before
                    ?.let { json.decodeFromString(NoteSnapshot.serializer(), it) }
                    ?: throw IllegalStateException("Audit entry $auditId has no before snapshot")
                doRawText(original.entityId, before.rawText ?: "", AuditAction.UNDO, AuditActor.USER)
            }
            AuditAction.SET_STATUS -> {
                val before = original.before
                    ?.let { json.decodeFromString(NoteSnapshot.serializer(), it) }
                    ?: throw IllegalStateException("Audit entry $auditId has no before snapshot")
                doStatus(original.entityId, NoteStatus.valueOf(before.status), AuditAction.UNDO, AuditActor.USER)
            }
            else -> throw IllegalArgumentException("Cannot undo action ${original.action} in v1")
        }
    }

    // ----------------------------------------------------------------- internal

    private suspend fun requireExisting(noteId: String, includeDeleted: Boolean = false): NoteEntity {
        val current = database.noteDao().byId(noteId) ?: throw NoteMissingException(noteId)
        if (!includeDeleted && current.deletedAt != null) throw NoteMissingException(noteId)
        return current
    }

    private fun entry(
        noteId: String,
        action: AuditAction,
        actor: AuditActor,
        before: NoteSnapshot?,
        after: NoteSnapshot?,
        at: Long,
    ): AuditEntity = AuditEntity(
        entity = AUDIT_ENTITY_NOTE,
        entityId = noteId,
        action = action,
        actor = actor,
        before = before?.let { json.encodeToString(NoteSnapshot.serializer(), it) },
        after = after?.let { json.encodeToString(NoteSnapshot.serializer(), it) },
        undoToken = newId(),
        at = at,
    )

    private fun NoteEntity.snapshot(): NoteSnapshot = NoteSnapshot(
        id = id,
        status = status.name,
        revision = revision,
        title = title,
        rawText = rawText,
        transcript = transcript,
        ocrText = ocrText,
        sourceUrl = sourceUrl,
        sourceApp = sourceApp,
        deletedAt = deletedAt,
    )
}
