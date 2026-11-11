package com.mindpalace.app.ai

import com.mindpalace.app.core.model.ProposalKind
import kotlinx.serialization.Serializable

@Serializable
data class AiProposalResponse(
    val summary: String? = null,
    val proposals: List<AiProposalItem> = emptyList(),
)

@Serializable
data class AiProposalItem(
    val kind: String,
    val value: String,
    val evidence: String,
    val confidence: Float,
)

/** One [AiProposalItem] that survived validation, with its kind resolved to the real enum. */
data class ValidatedProposal(
    val kind: ProposalKind,
    val value: String,
    val evidence: String,
    val confidence: Float,
)
