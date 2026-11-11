package com.mindpalace.app.core.db

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.FtsOptions
import androidx.room3.Fts5
import androidx.room3.Index
import androidx.room3.PrimaryKey
import com.mindpalace.app.core.model.NoteSource
import com.mindpalace.app.core.model.NoteStatus

/**
 * Canonical note row (§6). Two IDs, deliberately:
 *  - [rowId] is the SQLite INTEGER PRIMARY KEY (rowid alias). The FTS5 external-content index
 *    syncs by docid == rowid, so it must stay an integer.
 *  - [id] is the public UUID (§6 says "id (UUID)") used by links, derived facts, exports, audit.
 * Raw text/transcript/ocr are stored VERBATIM and are never edited by anything except the user
 * through the command handler (§3.2 "original is sacred").
 */
@Entity(
    tableName = "notes",
    indices = [
        Index(value = ["id"], unique = true),
        Index("created_at"),
        Index("status"),
        Index("updated_at"),
    ],
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "rowid")
    val rowId: Long = 0L,

    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "source")
    val source: NoteSource,

    @ColumnInfo(name = "source_app")
    val sourceApp: String? = null,

    @ColumnInfo(name = "source_url")
    val sourceUrl: String? = null,

    @ColumnInfo(name = "title")
    val title: String? = null,

    @ColumnInfo(name = "raw_text")
    val rawText: String? = null,

    @ColumnInfo(name = "transcript")
    val transcript: String? = null,

    @ColumnInfo(name = "image_path")
    val imagePath: String? = null,

    @ColumnInfo(name = "ocr_text")
    val ocrText: String? = null,

    /** App-private storage path for an ATTACHMENT-source note (D25); any file type, no preview. */
    @ColumnInfo(name = "attachment_path")
    val attachmentPath: String? = null,

    /** Original display filename, kept only to show the user what they attached. */
    @ColumnInfo(name = "attachment_file_name")
    val attachmentFileName: String? = null,

    @ColumnInfo(name = "status")
    val status: NoteStatus,

    @ColumnInfo(name = "revision")
    val revision: Int,

    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long? = null,
)

/**
 * FTS5 index over the searchable note columns (plan §4: "FTS5 for search" — satisfied via
 * androidx.room3, D3 in docs/DECISIONS.md). External-content mode: Room keeps it in sync with
 * [NoteEntity] via generated triggers; writes go ONLY to the notes table. quilks
 */
@Fts5(
    contentEntity = NoteEntity::class,
    contentRowId = "rowid",
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
)
@Entity(tableName = "notes_fts")
data class NoteFtsEntity(
    @ColumnInfo(name = "title")
    val title: String?,

    @ColumnInfo(name = "raw_text")
    val rawText: String?,

    @ColumnInfo(name = "transcript")
    val transcript: String?,

    @ColumnInfo(name = "ocr_text")
    val ocrText: String?,
)
