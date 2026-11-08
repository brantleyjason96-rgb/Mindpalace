package com.mindpalace.app.core.db

/**
 * Minimal sanitizer for FTS5 MATCH queries: wraps each whitespace-separated token in double
 * quotes (after stripping embedded quotes) so arbitrary user input — "don't", "quiz-13/",
 * "issuer(" — can never produce invalid FTS5 syntax or shell-match operators. Tokens are
 * combined with implicit AND. Returns null when there is nothing to search for.
 *
 * v1 tokenizer is room3's default unicode61; prefix/infix operators are intentionally NOT
 * supported from user input.
 */
object FtsQuery {

    fun sanitize(rawQuery: String): String? {
        val tokens = rawQuery
            .split(Regex("\\s+"))
            .map { it.replace("\"", "").trim() }
            .filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return null
        return tokens.joinToString(" ") { token -> "\"$token\"" }
    }
}
