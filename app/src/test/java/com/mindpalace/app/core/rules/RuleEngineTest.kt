package com.mindpalace.app.core.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure-JVM, table-driven tests for the rules evaluator (no Robolectric needed). */
class RuleEngineTest {

    private fun rule(id: String, enabled: Boolean, keywords: List<String>, category: String) =
        Rule(id, version = 1, enabled = enabled, matcher = RuleMatcher(keywords), action = RuleAction(category))

    @Test
    fun `keyword match is case-insensitive`() {
        assertEquals(true, RuleMatcher(listOf("Algebra")).matches("quiz on algebra tomorrow"))
    }

    @Test
    fun `matches if ANY keyword is present`() {
        val matcher = RuleMatcher(listOf("algebra", "geometry"))
        assertEquals(true, matcher.matches("review geometry proofs"))
        assertEquals(false, matcher.matches("grade spelling tests"))
    }

    @Test
    fun `blank keywords never match`() {
        assertEquals(false, RuleMatcher(listOf("", "   ")).matches("anything at all"))
    }

    @Test
    fun `empty keyword list never matches`() {
        assertEquals(false, RuleMatcher(emptyList()).matches("anything at all"))
    }

    @Test
    fun `first enabled match wins in list order`() {
        val rules = listOf(
            rule("r1", enabled = true, keywords = listOf("math"), category = "Classroom"),
            rule("r2", enabled = true, keywords = listOf("math"), category = "Curriculum/Pacing"),
        )
        assertEquals("r1", RuleEngine.firstMatch(rules, "math homework")?.id)
    }

    @Test
    fun `disabled rules are skipped`() {
        val rules = listOf(
            rule("r1", enabled = false, keywords = listOf("math"), category = "Classroom"),
            rule("r2", enabled = true, keywords = listOf("math"), category = "Curriculum/Pacing"),
        )
        assertEquals("r2", RuleEngine.firstMatch(rules, "math homework")?.id)
    }

    @Test
    fun `no match returns null`() {
        val rules = listOf(rule("r1", enabled = true, keywords = listOf("math"), category = "Classroom"))
        assertNull(RuleEngine.firstMatch(rules, "buy oat milk"))
    }

    @Test
    fun `round-trips through RuleEntity JSON encoding`() {
        val original = rule("r1", enabled = true, keywords = listOf("math", "algebra"), category = "Classroom")
        val restored = original.toEntity().toRule()
        assertEquals(original, restored)
    }

    @Test
    fun `unparseable entity JSON decodes to null instead of throwing`() {
        val entity = com.mindpalace.app.core.db.RuleEntity(
            id = "bad",
            version = 1,
            enabled = true,
            matcher = "not json",
            action = "not json",
        )
        assertNull(entity.toRule())
    }
}
