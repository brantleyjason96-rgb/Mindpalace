package com.mindpalace.app.ai

import com.mindpalace.app.core.model.ProposalKind
import kotlinx.serialization.json.Json

/**
 * Enforces the policy CLAUDE.md requires BEFORE anything touches the database: a quote the model
 * made up is worse than no proposal at all. The GBNF grammar guarantees valid JSON shape; this is
 * the semantic check grammar can't express — evidence must actually appear in the note, and the
 * 50-word summary rule (D23) is re-checked here rather than just trusted from the prompt.
 */
object AiProposalValidator {
    const val MIN_CONFIDENCE = 0.5f

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    data class Result(
        val summary: String?,
        val proposals: List<ValidatedProposal>,
    )

    fun validate(rawResponse: String, noteText: String): Result {
        val parsed = runCatching { json.decodeFromString<AiProposalResponse>(rawResponse) }
            .getOrElse { return Result(summary = null, proposals = emptyList()) }

        val summary = parsed.summary
            ?.trim()
            ?.takeIf { it.isNotBlank() && AiPrompt.needsSummary(noteText) }

        val proposals = parsed.proposals.mapNotNull { item -> validateItem(item, noteText) }

        return Result(summary = summary, proposals = proposals)
    }

    private fun validateItem(item: AiProposalItem, noteText: String): ValidatedProposal? {
        if (item.confidence < MIN_CONFIDENCE || item.confidence > 1f) return null
        if (item.value.isBlank() || item.evidence.isBlank()) return null
        if (!noteText.contains(item.evidence, ignoreCase = false)) return null
        val kind = runCatching { ProposalKind.valueOf(item.kind) }.getOrNull() ?: return null
        return ValidatedProposal(kind, item.value.trim(), item.evidence, item.confidence)
    }
}
