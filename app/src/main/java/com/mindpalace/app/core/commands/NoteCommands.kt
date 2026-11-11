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
    val attachmentPath: String? = null, // D25 — path inside app-private storage
    val attachmentFileName: String? = null, // D25 — original display name, for NoteCard only
    val sourceApp: String? = null,
    val sourceUrl: String? = null,
) {
    /** A note must carry at least SOME durable content. */
    fun hasCaptureContent(): Boolean =
        !rawText.isNullOrBlank() ||
            !transcript.isNullOrBlank() ||
            !ocrText.isNullOrBlank() ||
            !attachmentPath.isNullOrBlank() ||
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

    /** Matches a bare URL token for [toRequest]'s single-URL detection (plan 01 C3). */
    private val URL_PATTERN = Regex("(?i)\\b(?:https?://|www\\.)\\S+")

    fun toRequest(text: String?, subject: String?, sourcePackage: String?): CreateNoteRequest? {
        val original = text.orEmpty()
        val trimmedText = original.trim()
        val cleanSubject = subject?.trim().orEmpty()
        if (trimmedText.isEmpty() && cleanSubject.isEmpty()) return null

        // "Link-only" requires the ENTIRE text to be one whitespace-free URL token — checking
        // for a literal space only (the old bug) missed "url\nmore text", which then got stored
        // whole as source_url instead of being recognized as a note WITH a link in it.
        val linkOnly = trimmedText.isNotEmpty() && trimmedText.none { it.isWhitespace() } && looksLikeLink(trimmedText)
        val title = cleanSubject.ifEmpty { null }

        // Not link-only, but exactly one URL appears in the text (e.g. a link on its own line
        // followed by notes): still worth recording as source_url. Two or more is ambiguous —
        // record neither rather than guess which one matters.
        val sourceUrl = if (linkOnly) trimmedText else URL_PATTERN.findAll(trimmedText).map { it.value }.toList().singleOrNull()

        // Link-only share WITH a subject: subject becomes the title, the URL stays as raw text
        // so it remains searchable. Otherwise raw text is exactly what was shared, UNTRIMMED
        // (rule 4) — trimming above is only for the emptiness and link checks.
        val rawText = if (trimmedText.isEmpty() && title != null) title else original.ifEmpty { title }

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
