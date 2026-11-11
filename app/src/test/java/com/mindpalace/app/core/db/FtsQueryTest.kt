package com.mindpalace.app.core.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Plan 02 §3.3 (untagged): plain-JVM tests for the current v1 sanitizer. */
class FtsQueryTest {

    @Test
    fun `blank input yields null`() {
        assertNull(FtsQuery.sanitize(""))
        assertNull(FtsQuery.sanitize("   "))
        assertNull(FtsQuery.sanitize("\t\n"))
    }

    @Test
    fun `a lone quote character yields null`() {
        assertNull(FtsQuery.sanitize("\""))
    }

    @Test
    fun `two words become two quoted tokens`() {
        assertEquals("\"oat\" \"milk\"", FtsQuery.sanitize("oat milk"))
    }

    @Test
    fun `embedded quotes are stripped before quoting`() {
        assertEquals("\"ab\"", FtsQuery.sanitize("a\"b"))
    }

    @Test
    fun `fts5 operator-like tokens are quoted unchanged inside`() {
        assertEquals("\"AND\"", FtsQuery.sanitize("AND"))
        assertEquals("\"NEAR\"", FtsQuery.sanitize("NEAR"))
        assertEquals("\"title:x\"", FtsQuery.sanitize("title:x"))
        assertEquals("\"^x\"", FtsQuery.sanitize("^x"))
    }

    @Test
    fun `whitespace splitting produces the expected token count`() {
        // Probe (T-S6): if Kotlin's \s does not split this as expected, the regex needs widening.
        val sanitized = FtsQuery.sanitize("oat milk")!!
        assertEquals(2, sanitized.split(" ").size)
    }

    // ------------------------------------------------------------------------- plan 01 step 1c

    @Test
    fun `an embedded control character is stripped and the last token gets a prefix star`() {
        assertEquals("\"oat\"*", FtsQuery.sanitize("\u0000oat", prefixLast = true))
    }

    @Test
    fun `only the first 12 tokens are kept`() {
        val forty = (1..40).joinToString(" ") { "w$it" }
        val sanitized = FtsQuery.sanitize(forty)!!
        assertEquals(12, sanitized.split(" ").size)
    }

    @Test
    fun `a single token is cut to 64 characters`() {
        val longToken = "a".repeat(500)
        val sanitized = FtsQuery.sanitize(longToken)!!
        // sanitized is the token wrapped in quotes: "<64 a's>"
        assertEquals(64, sanitized.length - 2)
    }

    @Test
    fun `prefix star only applies to the last token`() {
        assertEquals("\"oat\" \"mi\"*", FtsQuery.sanitize("oat mi", prefixLast = true))
    }
}
