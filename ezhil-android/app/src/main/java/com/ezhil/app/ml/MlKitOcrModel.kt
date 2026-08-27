package com.ezhil.app.ml

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device OCR using Google ML Kit Text Recognition v2.
 *
 * Runs entirely on-device — no network call, no model asset needed.
 * Handles Latin script (English, numbers, punctuation) well.
 *
 * Tamil script: ML Kit does not have a bundled Tamil recogniser.
 * Tamil text falls through to the server-side OCR path in
 * LessonStudioViewModel. A future upgrade path is Tesseract4Android
 * with `tam.traineddata` bundled as an asset.
 */
@Singleton
class MlKitOcrModel @Inject constructor() {

    data class OcrResult(val text: String, val confidence: Float)

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.Builder().build())

    /**
     * Runs ML Kit recognition on [bitmap] and returns all detected text lines
     * joined with newlines. Confidence is 0.0 when no text was found, 0.85
     * otherwise (ML Kit v2 does not expose per-block confidence scores).
     */
    suspend fun run(bitmap: Bitmap): OcrResult = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer
            .process(image)
            .addOnSuccessListener { visionText ->
                val text = visionText.textBlocks
                    .joinToString("\n") { block -> block.text }
                    .trim()
                val confidence = if (text.isNotBlank()) 0.85f else 0.0f
                cont.resume(OcrResult(text, confidence))
            }
            .addOnFailureListener { e ->
                cont.resumeWithException(e)
            }
    }
}
