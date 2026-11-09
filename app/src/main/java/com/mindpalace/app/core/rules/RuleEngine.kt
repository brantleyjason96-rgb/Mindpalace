package com.mindpalace.app.core.rules

import com.mindpalace.app.core.db.RuleEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Rules engine (D16, docs/DECISIONS.md): pure Kotlin, no Android types, fully unit-testable.
 * v1 condition is keyword matching; v1 action is setting the note's CATEGORY derived fact — the
 * only condition/action pair the backlog (CLAUDE.md "Rules list + editor") actually calls for.
 * Extend [RuleMatcher]/[RuleAction] if a second kind is ever needed; don't speculate ahead of it.
 */
@Serializable
data class RuleMatcher(val keywords: List<String>) {
    /** Case-insensitive substring match against ANY keyword; blank keywords never match. */
    fun matches(text: String): Boolean {
        val lower = text.lowercase()
        return keywords.any { it.isNotBlank() && lower.contains(it.trim().lowercase()) }
    }
}

@Serializable
data class RuleAction(val category: String)

data class Rule(
    val id: String,
    val version: Int,
    val enabled: Boolean,
    val matcher: RuleMatcher,
    val action: RuleAction,
)

/** Rules run in list order; the first enabled match wins — one note, one rule-derived category. */
object RuleEngine {
    fun firstMatch(rules: List<Rule>, text: String): Rule? =
        rules.firstOrNull { it.enabled && it.matcher.matches(text) }
}

private val ruleJson = Json { ignoreUnknownKeys = true }

/** A row with unparseable matcher/action JSON is skipped rather than crashing capture. */
fun RuleEntity.toRule(): Rule? = runCatching {
    Rule(
        id = id,
        version = version,
        enabled = enabled,
        matcher = ruleJson.decodeFromString(RuleMatcher.serializer(), matcher),
        action = ruleJson.decodeFromString(RuleAction.serializer(), action),
    )
}.getOrNull()

fun Rule.toEntity(): RuleEntity = RuleEntity(
    id = id,
    version = version,
    enabled = enabled,
    matcher = ruleJson.encodeToString(RuleMatcher.serializer(), matcher),
    action = ruleJson.encodeToString(RuleAction.serializer(), action),
)
