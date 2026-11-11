package com.mindpalace.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Plan 02 §3.6 T-O1 (untagged): the wire format is part of the data contract. */
class OriginTest {

    @Test
    fun `every kind round-trips through encode and parse`() {
        val kinds = listOf(
            Origin.UserOrigin,
            Origin.SystemOrigin,
            Origin.RuleOrigin("r1"),
            Origin.AiOrigin("run-1"),
        )
        for (origin in kinds) {
            assertEquals(origin, Origin.parse(origin.encode()))
        }
    }

    @Test
    fun `an unrecognized wire value parses to null`() {
        assertNull(Origin.parse("bogus"))
    }
}
