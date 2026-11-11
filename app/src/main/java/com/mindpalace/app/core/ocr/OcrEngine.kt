package com.mindpalace.app.core.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Bundled, on-device text recognition (D17) — the model ships in the APK, so this never touches
 * the network. A thin coroutine wrapper around ML Kit's callback-based Task API; no new
 * coroutines-play-services dependency needed for one call site.
 */
object OcrEngine {
    suspend fun recognize(context: Context, imageUri: Uri): String = suspendCancellableCoroutine { cont ->
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = InputImage.fromFilePath(context, imageUri)
        recognizer.process(image)
            .addOnSuccessListener { text -> cont.resume(text.text) }
            .addOnFailureListener { e -> cont.resumeWithException(e) }
    }
}
