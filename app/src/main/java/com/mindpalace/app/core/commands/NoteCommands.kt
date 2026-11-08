package com.mindpalace.app.core.commands

import com.mindpalace.app.core.model.NoteSource

/**
 * Input for saving a new note. Capture is SAVE-FIRST (§3.1): whatever content is present is
 * persisted immediately in one transaction — nothing waits on rules, indexing, or AI. Text is
 * stored VERBATIM (§3.2); no trimming or rewriting happens here.
 */
data class CreateNoteRequest(
    val source: NoteSource,
    val title: String? = null,
    val rawText: String? = null,
    val transcript: String? = null,
    val ocrText: String? = null,   // Phase 2
    val imagePath: String? = null, // Phase 2 — path inside app-private storage
    val sourceApp: String? = null,
    val sourceUrl: String? = null,
) {
    /** A note must carry at least SOME durable content. */
    fun hasCaptureContent(): Boolean =
        !rawText.isNullOrBlank() ||
            !transcript.isNullOrBlank() ||
            !ocrText.isNullOrBlank() ||
            !title.isNullOrBlank()
}

/** Thrown when a create request carries nothing worth saving. */
class EmptyCaptureException(message: String = "Nothing to save") : Exception(message)

/** Thrown when a revision-guarded mutation lost a race; caller may retry with fresh state. */
class NoteConflictException(val noteId: String) : Exception("Note $noteId changed concurrently")

/** Thrown when an operation names a note that does not exist (or is soft-deleted). */
class NoteMissingException(val noteId: String) : Exception("Note $noteId does not exist")

/**
 * Pure mapping from an ACTION_SEND share into a [CreateNoteRequest] — no Android types, fully
 * unit-testable. Returns null when the share carries no text at all.
 *
 * Offline rule (§2): a shared link is saved as link + title only; we never fetch the article.
 */
object ShareIngest {

    fun toRequest(text: String?, subject: String?, sourcePackage: String?): CreateNoteRequest? {
        val trimmedText = text?.trim().orEmpty()
        val cleanSubject = subject?.trim().orEmpty()
        if (trimmedText.isEmpty() && cleanSubject.isEmpty()) return null

        val linkOnly = trimmedText.isNotEmpty() && !trimmedText.contains(' ') && looksLikeLink(trimmedText)
        val sourceUrl = if (linkOnly) trimmedText else null
        val title = cleanSubject.ifEmpty { null }

        // Link-only share WITH a subject: subject becomes the title, the URL stays as raw text
        // so it remains searchable. Otherwise raw text is exactly what was shared.
        val rawText = if (trimmedText.isEmpty() && title != null) title else trimmedText.ifEmpty { title }

        return CreateNoteRequest(
            source = NoteSource.SHARE,
            title = title,
            rawText = rawText,
            sourceUrl = sourceUrl,
            sourceApp = sourcePackage,
        )
    }

    private fun looksLikeLink(s: String): Boolean =
        s.startsWith("http://", ignoreCase = true) ||
            s.startsWith("https://", ignoreCase = true) ||
            s.startsWith("www.", ignoreCase = true)
}
