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
    fun `text with spaces matching a link prefix is NOT treated as a link`() {
        val req = ShareIngest.toRequest(
            "https://example.org read this later",
            null,
            null,
        )!!
        assertNull(req.sourceUrl)
        assertEquals("https://example.org read this later", req.rawText)
    }
}
