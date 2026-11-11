package com.mindpalace.app.core.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import com.mindpalace.app.core.model.DerivedField
import com.mindpalace.app.core.model.JobState
import com.mindpalace.app.core.model.LinkState
import com.mindpalace.app.core.model.NoteStatus
import com.mindpalace.app.core.model.ProposalState
import com.mindpalace.app.core.model.ReminderState
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(category: CategoryEntity)

    @Query("SELECT * FROM categories ORDER BY position ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT COALESCE(MAX(position), -1) FROM categories")
    suspend fun maxPosition(): Int
}

/**
 * Note DAO. Only the command handler performs writes to `notes`; reads are open to the UI layer.
 * All mutating queries are revision-guarded so concurrent edits fail loudly instead of
 * silently clobbering (§3.7).
 */
@Dao
interface NoteDao {

    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun updateRow(note: NoteEntity)

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun byId(id: String): NoteEntity?

    /** Live single-note lookup (plan 01 N1) — lets a detail screen tell "still loading" (no
     *  emission yet) apart from "genuinely gone" (emits null), instead of treating an empty
     *  not-yet-populated list the same as a deleted note. */
    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeById(id: String): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE id IN (:ids)")
    suspend fun byIds(ids: List<String>): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE deleted_at IS NULL ORDER BY created_at DESC, id DESC")
    fun observeActive(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deleted_at IS NULL ORDER BY created_at DESC, id DESC")
    suspend fun activeNotes(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE deleted_at IS NOT NULL ORDER BY updated_at DESC LIMIT :limit")
    suspend fun recentlyDeleted(limit: Int): List<NoteEntity>

    /** Live Trash list (plan 01 S3) — re-queries on any table change, unlike the one-shot above. */
    @Query("SELECT * FROM notes WHERE deleted_at IS NOT NULL ORDER BY updated_at DESC LIMIT :limit")
    fun observeDeleted(limit: Int): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deleted_at IS NULL AND updated_at > :since ORDER BY updated_at ASC")
    suspend fun changedSince(since: Long): List<NoteEntity>

    @Query("SELECT COUNT(*) FROM notes WHERE deleted_at IS NULL")
    suspend fun countActive(): Int

    // ---- revision-guarded mutations (command handler only) ----

    @Query(
        "UPDATE notes SET raw_text = :rawText, updated_at = :now, revision = revision + 1 " +
            "WHERE id = :id AND revision = :revision AND deleted_at IS NULL",
    )
    suspend fun updateRawTextGuarded(id: String, rawText: String, now: Long, revision: Int): Int

    @Query(
        "UPDATE notes SET status = :status, updated_at = :now, revision = revision + 1 " +
            "WHERE id = :id AND revision = :revision AND deleted_at IS NULL",
    )
    suspend fun updateStatusGuarded(id: String, status: NoteStatus, now: Long, revision: Int): Int

    @Query(
        "UPDATE notes SET deleted_at = :now, updated_at = :now, revision = revision + 1 " +
            "WHERE id = :id AND revision = :revision AND deleted_at IS NULL",
    )
    suspend fun softDeleteGuarded(id: String, now: Long, revision: Int): Int

    @Query(
        "UPDATE notes SET deleted_at = NULL, updated_at = :now, revision = revision + 1 " +
            "WHERE id = :id AND revision = :revision AND deleted_at IS NOT NULL",
    )
    suspend fun restoreGuarded(id: String, now: Long, revision: Int): Int

    @Query(
        "UPDATE notes SET title = :title, updated_at = :now, revision = revision + 1 " +
            "WHERE id = :id AND revision = :revision AND deleted_at IS NULL",
    )
    suspend fun updateTitleGuarded(id: String, title: String?, now: Long, revision: Int): Int

    /**
     * Restores every undoable field from an audit snapshot in one guarded write (plan 01 U1) —
     * deliberately has NO `deleted_at IS NULL` condition, since undoing a SOFT_DELETE/RESTORE must
     * be able to write onto a row in either delete state. The revision guard is what makes this
     * safe: it only applies if nothing has changed since the audited action.
     */
    @Query(
        "UPDATE notes SET title = :title, raw_text = :rawText, status = :status, " +
            "deleted_at = :deletedAt, updated_at = :now, revision = revision + 1 " +
            "WHERE id = :id AND revision = :revision",
    )
    suspend fun restoreSnapshotGuarded(
        id: String,
        title: String?,
        rawText: String?,
        status: NoteStatus,
        deletedAt: Long?,
        now: Long,
        revision: Int,
    ): Int

    /**
     * FTS5 search over title/raw text/transcript/OCR. External-content table ⇒ join on
     * notes.rowid == notes_fts.rowid (docid). Pass queries through [FtsQuery.sanitize].
     */
    @Query(
        "SELECT notes.* FROM notes JOIN notes_fts ON notes.rowid = notes_fts.rowid " +
            "WHERE notes_fts MATCH :ftsQuery AND notes.deleted_at IS NULL " +
            "ORDER BY notes.updated_at DESC LIMIT :limit",
    )
    suspend fun searchFts(ftsQuery: String, limit: Int): List<NoteEntity>

    /**
     * Same match as [searchFts], ordered by relevance (plan 01 S3) instead of recency — `bm25`
     * scores more relevant rows more negative, so ascending order puts the best match first.
     * This is what [NoteSearch.search] actually uses; [searchFts] stays for the one existing
     * recency-ordered caller/test.
     */
    @Query(
        "SELECT notes.* FROM notes JOIN notes_fts ON notes.rowid = notes_fts.rowid " +
            "WHERE notes_fts MATCH :ftsQuery AND notes.deleted_at IS NULL " +
            "ORDER BY bm25(notes_fts) ASC LIMIT :limit",
    )
    suspend fun searchFtsRanked(ftsQuery: String, limit: Int): List<NoteEntity>

    /**
     * Plain LIKE fallback if FTS5 is unavailable on some device build (§15 mitigation). The
     * ESCAPE clause makes `\` the escape character so a literal `%`/`_` in the query (escaped by
     * the caller, e.g. [NoteSearch]) is matched literally instead of acting as a wildcard.
     */
    @Query(
        "SELECT * FROM notes WHERE deleted_at IS NULL AND (" +
            "title LIKE '%' || :q || '%' ESCAPE '\\' OR raw_text LIKE '%' || :q || '%' ESCAPE '\\' " +
            "OR transcript LIKE '%' || :q || '%' ESCAPE '\\' OR ocr_text LIKE '%' || :q || '%' ESCAPE '\\') " +
            "ORDER BY updated_at DESC LIMIT :limit",
    )
    suspend fun searchLike(q: String, limit: Int): List<NoteEntity>
}

/** One row of [DerivedDao.observeCountsByField] — how many distinct notes carry a given value
 *  for a field (e.g. how many notes are filed under each category). Column names must match
 *  the query's aliases exactly for Room to map this without `@Embedded`. */
data class FieldValueCount(val value: String, val count: Int)

@Dao
interface DerivedDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(derived: DerivedEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(derived: DerivedEntity): Long

    @Query("SELECT * FROM derived WHERE note_id = :noteId ORDER BY created_at ASC")
    suspend fun forNote(noteId: String): List<DerivedEntity>

    @Query("SELECT * FROM derived WHERE note_id = :noteId ORDER BY created_at ASC")
    fun observeForNote(noteId: String): Flow<List<DerivedEntity>>

    @Query("DELETE FROM derived WHERE note_id = :noteId AND field = :field AND origin = :origin")
    suspend fun deleteForField(noteId: String, field: DerivedField, origin: String): Int

    @Query("SELECT COUNT(*) FROM derived")
    suspend fun count(): Int

    /** Vault per-notebook/entity note counts (Phase 2) — distinct note count per value for one
     *  derived field, e.g. how many notes are filed under each category. */
    @Query("SELECT value, COUNT(DISTINCT note_id) AS count FROM derived WHERE field = :field GROUP BY value")
    fun observeCountsByField(field: DerivedField): Flow<List<FieldValueCount>>
}

@Dao
interface AuditDao {

    @Insert
    suspend fun append(entry: AuditEntity): Long

    @Query("SELECT * FROM audit WHERE id = :id")
    suspend fun byId(id: Long): AuditEntity?

    @Query("SELECT * FROM audit WHERE entity = :entity AND entity_id = :entityId ORDER BY id DESC LIMIT :limit")
    suspend fun recentFor(entity: String, entityId: String, limit: Int): List<AuditEntity>

    @Query("SELECT * FROM audit WHERE entity = :entity AND entity_id = :entityId ORDER BY id DESC LIMIT :limit")
    fun observeRecentFor(entity: String, entityId: String, limit: Int): Flow<List<AuditEntity>>

    @Query("SELECT COUNT(*) FROM audit")
    suspend fun count(): Int
}

@Dao
interface TopicDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(topic: TopicEntity): Long

    @Query("SELECT * FROM topics ORDER BY name")
    fun observeAll(): Flow<List<TopicEntity>>

    @Query("SELECT * FROM topics WHERE id = :id")
    suspend fun byId(id: String): TopicEntity?

    @Query("SELECT * FROM topics WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun byName(name: String): TopicEntity?
}

@Dao
interface NoteLinkDao {

    @Insert
    suspend fun insert(link: NoteLinkEntity): Long

    @Query("SELECT * FROM note_links WHERE from_id = :noteId OR to_id = :noteId ORDER BY created_at DESC")
    suspend fun touching(noteId: String): List<NoteLinkEntity>

    @Query("SELECT * FROM note_links WHERE from_id = :noteId OR to_id = :noteId ORDER BY created_at DESC")
    fun observeTouching(noteId: String): Flow<List<NoteLinkEntity>>

    @Query("UPDATE note_links SET state = :state WHERE id = :id")
    suspend fun setState(id: Long, state: LinkState): Int

    @Query("SELECT COUNT(*) FROM note_links")
    suspend fun count(): Int
}

@Dao
interface ReminderDao {

    @Insert
    suspend fun insert(reminder: ReminderEntity)

    @Query("SELECT * FROM reminders WHERE state = :state ORDER BY created_at ASC")
    fun observeByState(state: ReminderState): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE note_id = :noteId ORDER BY created_at DESC")
    fun observeForNote(noteId: String): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun byId(id: String): ReminderEntity?

    @Query(
        "SELECT * FROM reminders WHERE state = 'ACTIVE' AND type = 'TIME' " +
            "ORDER BY created_at ASC",
    )
    suspend fun activeTimeReminders(): List<ReminderEntity>

    @Query("UPDATE reminders SET state = :state, snoozed_until = :snoozedUntil WHERE id = :id")
    suspend fun setState(id: String, state: ReminderState, snoozedUntil: Long?): Int

    @Query("SELECT COUNT(*) FROM reminders")
    suspend fun count(): Int
}

@Dao
interface PersonDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(person: PersonEntity): Long

    @Query("SELECT * FROM people ORDER BY name")
    fun observeAll(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM people WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun byName(name: String): PersonEntity?
}

@Dao
interface PlaceDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(place: PlaceEntity): Long

    @Query("SELECT * FROM places ORDER BY name")
    fun observeAll(): Flow<List<PlaceEntity>>

    @Query("SELECT * FROM places WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun byName(name: String): PlaceEntity?
}

@Dao
interface RuleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: RuleEntity)

    @Query("SELECT * FROM rules WHERE id = :id")
    suspend fun byId(id: String): RuleEntity?

    @Query("SELECT * FROM rules WHERE enabled = 1 ORDER BY id")
    suspend fun enabled(): List<RuleEntity>

    @Query("SELECT * FROM rules ORDER BY id")
    fun observeAll(): Flow<List<RuleEntity>>

    @Query("UPDATE rules SET enabled = :enabled, version = version + 1 WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean): Int

    @Query("DELETE FROM rules WHERE id = :id")
    suspend fun delete(id: String): Int
}

@Dao
interface AiIndexDao {

    @Insert
    suspend fun insert(entry: AiIndexEntity): Long

    @Query("SELECT * FROM ai_index WHERE note_id = :noteId ORDER BY created_at DESC")
    suspend fun forNote(noteId: String): List<AiIndexEntity>

    @Query("SELECT * FROM ai_index WHERE note_id = :noteId ORDER BY created_at DESC")
    fun observeForNote(noteId: String): Flow<List<AiIndexEntity>>

    /** User can delete any AI item (§8 Tier A) — but originals are never affected. */
    @Query("DELETE FROM ai_index WHERE note_id = :noteId")
    suspend fun deleteForNote(noteId: String): Int
}

@Dao
interface ProposalDao {

    @Insert
    suspend fun insert(proposal: ProposalEntity)

    @Query("SELECT * FROM proposals WHERE id = :id")
    suspend fun byId(id: String): ProposalEntity?

    @Query("SELECT * FROM proposals WHERE note_id = :noteId ORDER BY created_at DESC")
    suspend fun forNote(noteId: String): List<ProposalEntity>

    @Query("SELECT * FROM proposals WHERE state = :state ORDER BY created_at ASC LIMIT :limit")
    suspend fun byState(state: ProposalState, limit: Int): List<ProposalEntity>

    /** For the Proposals review screen — live, not a one-shot read like [byState]. */
    @Query("SELECT * FROM proposals WHERE state = :state ORDER BY created_at ASC")
    fun observeByState(state: ProposalState): Flow<List<ProposalEntity>>

    @Query("SELECT COUNT(*) FROM proposals WHERE state = :state")
    suspend fun countByState(state: ProposalState): Int

    @Query("UPDATE proposals SET state = :state, updated_at = :at WHERE id = :id")
    suspend fun setState(id: String, state: ProposalState, at: Long): Int
}

@Dao
interface AiRunDao {

    @Insert
    suspend fun insert(run: AiRunEntity)

    @Query("SELECT * FROM ai_runs WHERE id = :id")
    suspend fun byId(id: String): AiRunEntity?

    @Query(
        "UPDATE ai_runs SET finished = :finished, outcome = :outcome, notes_processed = :notesProcessed " +
            "WHERE id = :id",
    )
    suspend fun finish(id: String, finished: Long, outcome: String, notesProcessed: Int): Int

    @Query("SELECT * FROM ai_runs ORDER BY started DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AiRunEntity>>

    /** Drives [com.mindpalace.app.ai.AiNightWorker]'s `since` — null means "process everything". */
    @Query("SELECT MAX(finished) FROM ai_runs WHERE outcome = 'SUCCEEDED'")
    suspend fun lastSucceededFinishedAt(): Long?
}

@Dao
interface JobDao {

    /** Idempotent enqueue: a unique idempotency_key means the job can never run twice. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(job: JobEntity): Long

    @Query("SELECT * FROM jobs WHERE id = :id")
    suspend fun byId(id: String): JobEntity?

    @Query("SELECT * FROM jobs WHERE state IN ('PENDING', 'RETRY') ORDER BY created_at ASC LIMIT :limit")
    suspend fun nextBatch(limit: Int): List<JobEntity>

    @Query(
        "UPDATE jobs SET state = :state, attempts = :attempts, lease_until = :leaseUntil, updated_at = :at " +
            "WHERE id = :id",
    )
    suspend fun updateState(id: String, state: JobState, attempts: Int, leaseUntil: Long?, at: Long): Int

    @Query("SELECT COUNT(*) FROM jobs WHERE state = :state")
    suspend fun countByState(state: JobState): Int
}

