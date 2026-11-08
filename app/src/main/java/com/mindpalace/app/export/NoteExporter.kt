package com.mindpalace.app.export

import com.mindpalace.app.core.db.NoteDao
import com.mindpalace.app.core.db.NoteEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Export schema v1 (Phase 1: "export all notes to JSON/Markdown file"). Mirrors the §6 `notes`
 * columns deliberately. Additive changes only from here on — bump schema_version if it changes.
 *
 * Privacy (§3.6): export is a manual, user-triggered action writing into the app's PRIVATE files
 * dir; the user then decides what to do with the file. Nothing leaves the device by itself.
 */
@Serializable
data class ExportPayload(
    @SerialName("schema_version") val schemaVersion: Int = 1,
    @SerialName("app") val app: String = "MindPalace",
    @SerialName("exported_at") val exportedAt: Long,
    @SerialName("note_count") val noteCount: Int,
    val notes: List<ExportNote>,
)

@Serializable
data class ExportNote(
    val id: String,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long,
    val source: String,
    @SerialName("source_app") val sourceApp: String? = null,
    @SerialName("source_url") val sourceUrl: String? = null,
    val title: String? = null,
    @SerialName("raw_text") val rawText: String? = null,
    val transcript: String? = null,
    @SerialName("ocr_text") val ocrText: String? = null,
    val status: String,
    val revision: Int,
)

private fun NoteEntity.toExportNote(): ExportNote = ExportNote(
    id = id,
    createdAt = createdAt,
    updatedAt = updatedAt,
    source = source.name,
    sourceApp = sourceApp,
    sourceUrl = sourceUrl,
    title = title,
    rawText = rawText,
    transcript = transcript,
    ocrText = ocrText,
    status = status.name,
    revision = revision,
)

/**
 * Manual export only: active (non-deleted) notes → JSON (round-trippable) or Markdown (readable).
 * "Export round-trips" (Checkpoint 1) is enforced by NoteExporterTest.
 */
class NoteExporter(
    private val noteDao: NoteDao,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true }

    suspend fun buildJson(): String {
        val notes = noteDao.activeNotes().map { it.toExportNote() }
        val payload = ExportPayload(exportedAt = now(), noteCount = notes.size, notes = notes)
        return json.encodeToString(payload)
    }

    suspend fun buildMarkdown(): String {
        val notes = noteDao.activeNotes().map { it.toExportNote() }
        return buildMarkdown(notes, now())
    }

    /** Writes the JSON export into [dir] (caller passes a private files dir) and returns the file. */
    suspend fun writeJson(dir: File): File = withContext(Dispatchers.IO) {
        val file = dir.resolve("mind-palace-notes-${now()}.json")
        file.parentFile?.mkdirs()
        file.writeText(buildJson())
        file
    }

    /** Writes the Markdown export into [dir] and returns the file. */
    suspend fun writeMarkdown(dir: File): File = withContext(Dispatchers.IO) {
        val file = dir.resolve("mind-palace-notes-${now()}.md")
        file.parentFile?.mkdirs()
        file.writeText(buildMarkdown())
        file
    }

    private fun buildMarkdown(notes: List<ExportNote>, exportedAt: Long): String = buildString {
        appendLine("# Mind Palace export")
        appendLine()
        appendLine("- Exported at (epoch ms): $exportedAt")
        appendLine("- Notes: ${notes.size}")
        appendLine()
        for (n in notes) {
            appendLine("## ${n.title ?: n.id}")
            appendLine()
            appendLine("- id: ${n.id}")
            appendLine("- created: ${n.createdAt}")
            appendLine("- status: ${n.status} (rev ${n.revision})")
            if (n.sourceUrl != null) appendLine("- link: <${n.sourceUrl}>")
            appendLine()
            val body = listOfNotNull(n.rawText, n.transcript, n.ocrText)
                .filter { it.isNotBlank() }
                .joinToString("\n\n")
            if (body.isNotEmpty()) {
                appendLine(body)
                appendLine()
            }
            appendLine("---")
            appendLine()
        }
    }

    companion object {
        // Hoisted so decode() doesn't rebuild a Json instance per call.
        private val decodeJson = Json { ignoreUnknownKeys = true }

        /** Decodes an export back into a payload (used by tests and by a future import). */
        fun decode(jsonString: String): ExportPayload =
            decodeJson.decodeFromString(ExportPayload.serializer(), jsonString)
    }
}
