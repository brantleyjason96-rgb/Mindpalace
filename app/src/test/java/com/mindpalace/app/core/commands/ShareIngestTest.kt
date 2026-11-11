package com.mindpalace.app.core.commands

import com.mindpalace.app.core.model.NoteSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure-JVM tests for the share→request mapper (fast feedback loop, no Robolectric needed). */
class ShareIngestTest {

    @Test
    fun `plain text share is preserved verbatim with source app`() {
        val req = ShareIngest.toRequest("quiz tomorrow — finish ELL rubric", null, "com.android.keepnotes")!!
        assertEquals(NoteSource.SHARE, req.source)
        assertEquals("quiz tomorrow — finish ELL rubric", req.rawText)
        assertEquals("com.android.keepnotes", req.sourceApp)
        assertNull(req.sourceUrl)
        assertNull(req.title)
    }

    @Test
    fun `link-only share becomes a note with source_url`() {
        val req = ShareIngest.toRequest("https://example.org/continental-drift", null, "com.android.chrome")!!
        assertEquals("https://example.org/continental-drift", req.sourceUrl)
        assertEquals("https://example.org/continental-drift", req.rawText)
    }

    @Test
    fun `link with subject keeps the subject as title`() {
        val req = ShareIngest.toRequest("https://example.org/unit-3", "Unit 3 pacing ideas", "com.android.chrome")!!
        assertEquals("Unit 3 pacing ideas", req.title)
        assertEquals("https://example.org/unit-3", req.rawText)
        assertEquals("https://example.org/unit-3", req.sourceUrl)
    }

    @Test
    fun `subject-only share uses the subject as text`() {
        val req = ShareIngest.toRequest(null, "Quick thought about the app", null)!!
        assertEquals("Quick thought about the app", req.title)
        assertEquals("Quick thought about the app", req.rawText)
    }

    @Test
    fun `empty shares are rejected with null, never an empty note`() {
        assertNull(ShareIngest.toRequest(null, null, null))
        assertNull(ShareIngest.toRequest("", "", "com.android.chrome"))
        assertNull(ShareIngest.toRequest("   ", "\t", null))
    }

    @Test
    fun `text with spaces matching a link prefix is NOT link-only, but its one URL is still recorded`() {
        // Updated by plan 01 C3: a URL followed by more text is no longer treated as "the whole
        // share IS a link" (linkOnly stays false, rawText stays the full text) — but when exactly
        // one URL appears, it is still worth recording as source_url metadata.
        val req = ShareIngest.toRequest(
            "https://example.org read this later",
            null,
            null,
        )!!
        assertEquals("https://example.org", req.sourceUrl)
        assertEquals("https://example.org read this later", req.rawText)
    }

    // ------------------------------------------------------------------------- plan 01 step 1a

    @Test
    fun `url followed by a newline and text is not link-only`() {
        val req = ShareIngest.toRequest("https://a.org\nmy notes", null, null)!!
        assertEquals("https://a.org\nmy notes", req.rawText)
        assertEquals("https://a.org", req.sourceUrl)
    }

    @Test
    fun `shared text is stored untrimmed`() {
        val req = ShareIngest.toRequest("  hi  ", null, null)!!
        assertEquals("  hi  ", req.rawText)
    }

    @Test
    fun `two urls set no source url`() {
        val req = ShareIngest.toRequest("https://a.org and https://b.org", null, null)!!
        assertNull(req.sourceUrl)
    }
}
