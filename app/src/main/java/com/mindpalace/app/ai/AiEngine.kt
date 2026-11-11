package com.mindpalace.app.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * On-device llama.cpp engine (D13/D22/D23): load a user-supplied GGUF once per night-loop run,
 * then run one grammar-constrained completion per changed note, then unload. No chat history, no
 * streaming — a batch job needs one JSON blob back per note, not a live conversation.
 */
object AiEngine {
    private var backendInitialized = false
    private var modelLoaded = false

    private external fun init(nativeLibDir: String)
    private external fun load(modelPath: String): Int
    private external fun benchModel(pp: Int, tg: Int, pl: Int, nr: Int): String
    private external fun runCompletion(systemPrompt: String, userPrompt: String, grammar: String, nPredict: Int): String
    private external fun unload()

    suspend fun loadModel(context: Context, modelPath: String): Boolean = withContext(Dispatchers.IO) {
        System.loadLibrary("ai-spike")
        if (!backendInitialized) {
            init(context.applicationInfo.nativeLibraryDir)
            backendInitialized = true
        }
        modelLoaded = load(modelPath) == 0
        modelLoaded
    }

    suspend fun runBench(): String = withContext(Dispatchers.IO) {
        check(modelLoaded) { "Model not loaded" }
        benchModel(/* pp = */ 512, /* tg = */ 128, /* pl = */ 1, /* nr = */ 3)
    }

    /** Returns the model's raw generated text (expected to be one grammar-constrained JSON blob). */
    suspend fun complete(systemPrompt: String, userPrompt: String, grammar: String, nPredict: Int = 400): String =
        withContext(Dispatchers.IO) {
            check(modelLoaded) { "Model not loaded" }
            runCompletion(systemPrompt, userPrompt, grammar, nPredict)
        }

    suspend fun unloadModel() = withContext(Dispatchers.IO) {
        if (modelLoaded) {
            unload()
            modelLoaded = false
        }
    }
}
