package com.ezhil.app.ml

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp
import kotlin.math.min

/**
 * On-device Tamil OCR using a custom CRNN model via ONNX Runtime.
 *
 * Model file:  assets/models/ezhil_ocr_v1.onnx
 * Vocab file:  assets/models/ezhil_ocr_vocab.txt
 *
 * Input:  [1, 3, 32, 512]  float32  NCHW
 * Output: [T, 1, VOCAB]    float32  log-softmax logits (CTC decoded here)
 *
 * isAvailable = false when model file is absent — LessonStudioViewModel
 * falls back to ML Kit → server OCR automatically.
 */
@Singleton
class OcrModel @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class OcrResult(val text: String, val confidence: Float)

    private var ortEnv:     OrtEnvironment? = null
    private var ortSession: OrtSession?     = null
    private var idxToChar:  List<String>    = emptyList()

    init {
        try {
            val modelBytes = context.assets.open("models/ezhil_ocr_v1.onnx").readBytes()
            ortEnv     = OrtEnvironment.getEnvironment()
            ortSession = ortEnv!!.createSession(modelBytes, OrtSession.SessionOptions())
            idxToChar  = context.assets.open("models/ezhil_ocr_vocab.txt")
                .bufferedReader(Charsets.UTF_8).readLines()

            Log.i(TAG, "ONNX OCR loaded. vocab=${idxToChar.size}")
        } catch (e: Exception) {
            Log.e(TAG, "ONNX OCR init failed", e)
        }
    }

    /** True when the ONNX model is loaded and ready. */
    val isAvailable: Boolean get() = ortSession != null && idxToChar.isNotEmpty()

    // ── Inference ─────────────────────────────────────────────────────────────

    suspend fun run(bitmap: Bitmap): OcrResult = withContext(Dispatchers.Default) {
        val sess = ortSession ?: return@withContext OcrResult("", 0f)
        val env  = ortEnv    ?: return@withContext OcrResult("", 0f)

        try {
            val floatBuf = preprocess(bitmap)
            val shape    = longArrayOf(1L, 3L, INPUT_H.toLong(), INPUT_W.toLong())

            OnnxTensor.createTensor(env, floatBuf, shape).use { inTensor ->
                sess.run(mapOf(INPUT_NAME to inTensor)).use { output ->
                    val outTensor = output[0] as? OnnxTensor
                        ?: return@withContext OcrResult("", 0f)
                    val outShape = outTensor.info.shape

                    if (outShape.size != 3) {
                        Log.e(TAG, "Unexpected output rank: ${outShape.contentToString()}")
                        return@withContext OcrResult("", 0f)
                    }

                    val timeSteps = outShape[0].toInt()
                    val vocabSize = outShape[2].toInt()
                    if (vocabSize != idxToChar.size) {
                        Log.e(TAG, "Vocab mismatch. output=$vocabSize file=${idxToChar.size}")
                        return@withContext OcrResult("", 0f)
                    }

                    val flat = outTensor.floatBuffer
                        ?: return@withContext OcrResult("", 0f)

                    val logits = Array(timeSteps) { t ->
                        FloatArray(vocabSize) { v -> flat.get(t * vocabSize + v) }
                    }

                    OcrResult(
                        text = ctcDecode(logits).trim(),
                        confidence = decodedConfidence(logits),
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "ONNX OCR failed", e)
            OcrResult("", 0f)
        }
    }

    // ── Preprocessing ─────────────────────────────────────────────────────────

    private fun preprocess(bitmap: Bitmap): FloatBuffer {
        // Scale to INPUT_H height (preserve aspect ratio), pad width to INPUT_W
        val scaledW = (bitmap.width.toFloat() * INPUT_H / bitmap.height)
            .toInt().coerceAtLeast(1)
        val scaled  = Bitmap.createScaledBitmap(bitmap, scaledW, INPUT_H, true)

        val padded  = Bitmap.createBitmap(INPUT_W, INPUT_H, Bitmap.Config.ARGB_8888)
        val canvas  = Canvas(padded)
        canvas.drawColor(Color.WHITE)

        val copyW = min(scaledW, INPUT_W)
        canvas.drawBitmap(
            scaled,
            Rect(0, 0, copyW, INPUT_H),
            RectF(0f, 0f, copyW.toFloat(), INPUT_H.toFloat()),
            null,
        )

        // NCHW float32: channel first, normalised to [0, 1]
        val buf = FloatBuffer.allocate(3 * INPUT_H * INPUT_W)
        for (ch in 0..2) {
            for (y in 0 until INPUT_H) {
                for (x in 0 until INPUT_W) {
                    val px = padded.getPixel(x, y)
                    val value = when (ch) {
                        0    -> Color.red(px)
                        1    -> Color.green(px)
                        else -> Color.blue(px)
                    }
                    buf.put(value / 255f)
                }
            }
        }
        buf.rewind()
        return buf
    }

    // ── CTC greedy decode ─────────────────────────────────────────────────────

    private fun ctcDecode(logits: Array<FloatArray>): String {
        val sb      = StringBuilder()
        val blankIdx = blankIndex()
        var prevIdx = -1

        for (stepLogits in logits) {
            val best = stepLogits.indices.maxByOrNull { stepLogits[it] } ?: blankIdx
            if (best != prevIdx && best != blankIdx) {
                idxToChar.getOrNull(best)?.let { sb.append(it) }
            }
            prevIdx = best
        }

        return sb.toString()
    }

    private fun decodedConfidence(logits: Array<FloatArray>): Float {
        val blankIdx = blankIndex()
        var prevIdx = -1
        var total = 0.0
        var count = 0

        for (step in logits) {
            val best = step.indices.maxByOrNull { step[it] } ?: blankIdx
            if (best != prevIdx && best != blankIdx) {
                total += softmaxProbability(step, best)
                count += 1
            }
            prevIdx = best
        }

        return if (count == 0) 0f else (total / count).toFloat()
    }

    private fun softmaxProbability(values: FloatArray, index: Int): Double {
        val maxValue = values.max()
        val expSum = values.sumOf { exp((it - maxValue).toDouble()) }
        return exp((values[index] - maxValue).toDouble()) / expSum
    }

    private fun blankIndex(): Int =
        idxToChar.indexOf("<BLANK>").takeIf { it >= 0 } ?: (idxToChar.size - 1)

    companion object {
        private const val TAG = "EzhilOcrModel"
        private const val INPUT_H = 32
        private const val INPUT_W = 512
        private const val INPUT_NAME = "input"
    }
}
