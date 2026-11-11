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
}
