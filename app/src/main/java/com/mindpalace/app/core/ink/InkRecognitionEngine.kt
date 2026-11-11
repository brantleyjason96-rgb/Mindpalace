package com.mindpalace.app.core.ink

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.hypot

/** One pen stroke: screen-space points (drawing/rendering, with color/width/alpha) and ML Kit's
 *  timestamped points (recognition) in lockstep, so the UI never has to keep two lists in sync by
 *  hand. Width/alpha are fixed at stroke creation, so changing the tool mid-canvas never alters
 *  strokes already drawn. */
data class InkStroke(
    val color: Color,
    val widthPx: Float = 6f,
    val alpha: Float = 1f,
    val points: List<Offset> = emptyList(),
    val inkPoints: List<Ink.Point> = emptyList(),
) {
    fun withPoint(offset: Offset, atMillis: Long) = copy(
        points = points + offset,
        inkPoints = inkPoints + Ink.Point.create(offset.x, offset.y, atMillis),
    )

    /** True if any point of this stroke falls within [radiusPx] of [offset] — object-eraser hit test. */
    fun isNear(offset: Offset, radiusPx: Float): Boolean =
        points.any { hypot((it.x - offset.x).toDouble(), (it.y - offset.y).toDouble()) < radiusPx }
}

/**
 * Handwriting recognition (D26) — the one deliberate exception to this app's zero-network
 * design: ML Kit's ink model has no bundled variant (unlike [com.mindpalace.app.core.ocr.OcrEngine]'s),
 * so the model is downloaded once per language and cached; recognition itself still runs fully
 * on-device after that. Same thin coroutine-wrapper-around-Task pattern as OcrEngine.
 *
 * English only for now — ponytail: add a language picker when multi-language actually matters,
 * not speculatively.
 */
object InkRecognitionEngine {
    private const val LANGUAGE_TAG = "en-US"

    private val modelIdentifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag(LANGUAGE_TAG)
    private val model = modelIdentifier?.let { DigitalInkRecognitionModel.builder(it).build() }
    private val remoteModelManager = RemoteModelManager.getInstance()

    /** Downloads the model if it isn't already cached; throws on failure (no model, no network). */
    suspend fun ensureModelReady() {
        val m = checkNotNull(model) { "No ink model for $LANGUAGE_TAG" }
        val downloaded = remoteModelManager.isModelDownloaded(m).awaitTask()
        if (!downloaded) {
            remoteModelManager.download(m, DownloadConditions.Builder().build()).awaitTask()
        }
    }

    /** Recognizes [strokes] and returns the top candidate's text, or blank if nothing was
     *  recognized. Color is irrelevant to recognition — only each stroke's timestamped points
     *  matter. Opens and closes its own client — this isn't called often enough to justify
     *  keeping one alive between calls. */
    suspend fun recognize(strokes: List<InkStroke>): String {
        val m = checkNotNull(model) { "No ink model for $LANGUAGE_TAG" }
        val ink = Ink.builder().apply {
            strokes.forEach { stroke ->
                if (stroke.inkPoints.isNotEmpty()) {
                    val strokeBuilder = Ink.Stroke.builder()
                    stroke.inkPoints.forEach(strokeBuilder::addPoint)
                    addStroke(strokeBuilder.build())
                }
            }
        }.build()
        val recognizer = DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(m).build())
        return try {
            recognizer.recognize(ink).awaitTask().candidates.firstOrNull()?.text.orEmpty()
        } finally {
            recognizer.close()
        }
    }

    /** Renders the drawn strokes, in their own colors, to a PNG in [dir] for provenance — same
     *  "an image + its recognized text" shape as Photo capture. */
    fun renderToFile(strokes: List<InkStroke>, widthPx: Int, heightPx: Int, dir: File): File {
        val bitmap = Bitmap.createBitmap(widthPx.coerceAtLeast(1), heightPx.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        val paint = Paint().apply {
            style = Paint.Style.STROKE
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        // ponytail: straight polyline segments, not bezier-smoothed — add smoothing if jagged
        // strokes turn out to matter in practice.
        strokes.forEach { stroke ->
            paint.color = stroke.color.toArgb()
            paint.alpha = (stroke.alpha * 255).toInt().coerceIn(0, 255)
            paint.strokeWidth = stroke.widthPx
            val points = stroke.points
            for (i in 1 until points.size) {
                canvas.drawLine(points[i - 1].x, points[i - 1].y, points[i].x, points[i].y, paint)
            }
        }
        val file = File(dir, "${UUID.randomUUID()}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }

    private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
    }
}
