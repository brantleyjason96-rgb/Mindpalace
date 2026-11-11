package com.mindpalace.app.ai

import android.content.Context
import com.mindpalace.app.core.db.AiIndexEntity
import com.mindpalace.app.core.db.AiRunEntity
import com.mindpalace.app.core.db.MindPalaceDatabase
import com.mindpalace.app.core.db.ProposalEntity
import com.mindpalace.app.core.model.ProposalKind
import com.mindpalace.app.core.model.ProposalState
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * The night-loop orchestrator (D13/D22/D23): one GGUF load, one grammar-constrained completion per
 * changed note, everything written straight to `ai_index`/`proposals` — never to the note itself
 * (CLAUDE.md rule 5). Runs synchronously on the caller's dispatcher; the WorkManager wrapper that
 * schedules this nightly (charging + idle, stop on heat/battery) is the next piece, not built yet.
 */
class AiNightRunner(private val database: MindPalaceDatabase) {

    data class RunSummary(val notesProcessed: Int, val proposalsCreated: Int, val outcome: String)

    suspend fun run(context: Context, modelPath: String, modelName: String, since: Long): RunSummary {
        val aiRunDao = database.aiRunDao()

        val runId = UUID.randomUUID().toString()
        aiRunDao.insert(
            AiRunEntity(
                id = runId,
                model = modelName,
                // ponytail: no GGUF metadata reader yet, so modelVersion is unknown — fine for now,
                // add if a prompt/model mismatch ever needs distinguishing in ai_runs history.
                modelVersion = "",
                promptVersion = AiPrompt.PROMPT_VERSION,
                started = System.currentTimeMillis(),
                outcome = "RUNNING",
            ),
        )

        if (!AiEngine.loadModel(context, modelPath)) {
            aiRunDao.finish(runId, System.currentTimeMillis(), "FAILED_LOAD", 0)
            return RunSummary(0, 0, "FAILED_LOAD")
        }

        val (notesProcessed, proposalsCreated) = try {
            processNotes(runId, modelName, since)
        } finally {
            AiEngine.unloadModel()
        }

        aiRunDao.finish(runId, System.currentTimeMillis(), "SUCCEEDED", notesProcessed)
        return RunSummary(notesProcessed, proposalsCreated, "SUCCEEDED")
    }

    private suspend fun processNotes(runId: String, modelName: String, since: Long): Pair<Int, Int> {
        val noteDao = database.noteDao()
        val categoryDao = database.categoryDao()
        val topicDao = database.topicDao()
        val aiIndexDao = database.aiIndexDao()
        val proposalDao = database.proposalDao()
        val json = Json

        val categories = categoryDao.observeAll().first().map { it.name }
        val topics = topicDao.observeAll().first().map { it.name }
        val systemPrompt = AiPrompt.systemPrompt(categories, topics)

        val notes = noteDao.changedSince(since)
        var proposalsCreated = 0
        for (note in notes) {
            val noteText = listOfNotNull(note.title, note.rawText, note.transcript, note.ocrText, note.attachmentFileName)
                .joinToString("\n")
                .trim()
            if (noteText.isBlank()) continue

            val raw = AiEngine.complete(systemPrompt, AiPrompt.userPrompt(noteText), AiPrompt.GRAMMAR)
            val result = AiProposalValidator.validate(raw, noteText)

            val topicValues = result.proposals.filter { it.kind == ProposalKind.TOPIC }.map { it.value }
            val people = result.proposals.filter { it.kind == ProposalKind.PERSON }.map { it.value }
            val places = result.proposals.filter { it.kind == ProposalKind.PLACE }.map { it.value }
            if (result.summary != null || topicValues.isNotEmpty() || people.isNotEmpty() || places.isNotEmpty()) {
                aiIndexDao.insert(
                    AiIndexEntity(
                        noteId = note.id,
                        summary = result.summary ?: "",
                        keywords = json.encodeToString(topicValues),
                        entities = json.encodeToString(mapOf("people" to people, "places" to places)),
                        openQuestions = "[]",
                        model = modelName,
                        runId = runId,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            }

            val now = System.currentTimeMillis()
            for (proposal in result.proposals) {
                proposalDao.insert(
                    ProposalEntity(
                        id = UUID.randomUUID().toString(),
                        noteId = note.id,
                        kind = proposal.kind,
                        newValue = proposal.value,
                        evidenceQuote = proposal.evidence,
                        confidence = proposal.confidence,
                        runId = runId,
                        state = ProposalState.PENDING,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                proposalsCreated++
            }
        }
        return notes.size to proposalsCreated
    }
}
