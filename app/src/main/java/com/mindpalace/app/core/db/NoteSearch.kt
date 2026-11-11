package com.mindpalace.app.core.db

import android.database.SQLException

/**
 * The search entry point the app should use (plan 01 S1–S3): sanitizes the query with prefix
 * matching on the last token, ranks FTS5 hits by relevance, and falls back to a plain LIKE scan
 * if FTS5 itself ever throws (§15 mitigation) — a hostile query must never end the search flow
 * with an uncaught exception.
 */
object NoteSearch {

    suspend fun search(noteDao: NoteDao, query: String, limit: Int = 50): List<NoteEntity> {
        val sanitized = FtsQuery.sanitize(query, prefixLast = true) ?: return emptyList()
        return try {
            noteDao.searchFtsRanked(sanitized, limit)
        } catch (e: SQLException) {
            noteDao.searchLike(likeEscape(query), limit)
        }
    }

    /** Escapes LIKE wildcards (`%`, `_`) and the escape character itself, for [NoteDao.searchLike]'s
     *  `ESCAPE '\'` clause — a literal `%`/`_` the user typed must match literally, not wildcard. */
    fun likeEscape(raw: String): String = raw
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
}
