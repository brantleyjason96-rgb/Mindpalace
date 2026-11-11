package com.mindpalace.app.ai

import android.content.Context

/**
 * Where the night loop looks for its GGUF model, shared by the debug spike buttons
 * (SettingsScreen) and [AiNightWorker] so they never drift apart. No model-file picker yet
 * (CLAUDE.md backlog "AI setup") — until then this fixed path is the only way a model gets
 * found.
 */
object AiModelConfig {
    const val MODEL_NAME = "qwen3-4b-q4_k_m"

    fun modelPath(context: Context): String = "${context.getExternalFilesDir(null)}/model.gguf"
}
