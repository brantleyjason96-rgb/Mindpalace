package com.mindpalace.app.core.db

/**
 * Sanitizer for FTS5 MATCH queries: wraps each whitespace-separated token in double quotes
 * (after stripping embedded quotes and control characters) so arbitrary user input — "don't",
 * "quiz-13/", "issuer(", an embedded NUL byte — can never produce invalid FTS5 syntax or
 * shell-match operators. Tokens are combined with implicit AND. Returns null when there is
 * nothing to search for.
 *
 * Splitting on whitespace happens FIRST — Kotlin/Java's `\s` already treats tab/newline/etc as
 * token separators — so per-token cleanup only ever strips genuinely embedded control characters
 * (like a NUL byte stuck to a word with no separating whitespace), never a legitimate separator.
 */
object FtsQuery {

    private const val MAX_TOKENS = 12
    private const val MAX_TOKEN_LENGTH = 64

    /**
     * [prefixLast] appends FTS5's `*` prefix operator to the final token only (plan 01 S1) — the
     * word the user is still typing — so "oat" matches "oatmeal". Earlier tokens stay exact,
     * since widening every word to a prefix match would make short common words match everything.
     */
    fun sanitize(rawQuery: String, prefixLast: Boolean = false): String? {
        val tokens = rawQuery
            .split(Regex("\\s+"))
            .map { token -> token.filter { it.code >= 0x20 && it != '"' } }
            .filter { it.isNotEmpty() }
            .take(MAX_TOKENS)
            .map { it.take(MAX_TOKEN_LENGTH) }
        if (tokens.isEmpty()) return null
        return tokens.mapIndexed { index, token ->
            val quoted = "\"$token\""
            if (prefixLast && index == tokens.lastIndex) "$quoted*" else quoted
        }.joinToString(" ")
    }
}
