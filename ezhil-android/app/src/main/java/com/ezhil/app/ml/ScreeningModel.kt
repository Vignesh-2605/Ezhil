package com.ezhil.app.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.nnapi.NnApiDelegate
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

data class ScreeningResult(
    val riskScore: Float,
    val phonemeErrorRate: Float,
    val letterReversalRate: Float?,   // null = not measurable on this path
    val syllableSkipRate: Float,
    val lipSyncConfidence: Float?,    // null = no video captured
    val riskLevel: String,            // "low" | "medium" | "high"
    val errorTagsJson: String,
    val audioDurationMs: Int,
    val readingSpeedWpm: Float?,      // null = no transcription available
    val modelVersion: String          // "cnn-1.0.0" | "heuristic-0.1"
)

@Singleton
class ScreeningModel @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var interpreter: Interpreter? = null

    /** True when the trained TFLite model is bundled and loaded. */
    val isModelLoaded: Boolean get() = interpreter != null

    init {
        try {
            val model = loadModelFile("models/ezhil_screening_v1.tflite")
            val options = Interpreter.Options().apply {
                addDelegate(NnApiDelegate())
                setNumThreads(2)
            }
            interpreter = Interpreter(model, options)
        } catch (_: Exception) {
            // Model file not yet bundled — will use heuristic fallback
        }
    }

    // ── Public entry point ────────────────────────────────────────────────────

    /** Not enough speech to score. Reported instead of a fabricated result. */
    class InsufficientAudioException(message: String) : Exception(message)

    suspend fun run(audioFile: File, videoFrames: List<Bitmap>): ScreeningResult =
        withContext(Dispatchers.Default) {
            val durationMs = estimateAudioDurationMs(audioFile)

            // The heuristic derives everything from energy and pause counts, so
            // silence, background noise or two seconds of humming all yield a
            // confident-looking risk score. Refuse rather than rate: a teacher
            // acting on a number produced from no reading is worse than being
            // asked to try again.
            requireEnoughSpeech(audioFile, durationMs)

            // Compute the heuristic directly rather than asking a network to
            // approximate it.
            //
            // The bundled TFLite model is a distillation of this same
            // heuristic, and the distillation collapsed: measured against the
            // app's own MFCC pipeline it returns ~0.22 risk for anything —
            // fluent reading, halting reading, silence, even pure noise, which
            // it rated *higher* than fluent reading. Retraining it from
            // corrected labels did not help; it predicts the dataset mean and
            // ignores the audio, and the script's own ordering check fails.
            //
            // There was never a reason for a network here. The heuristic is a
            // handful of arithmetic on energy and pause counts — exactly
            // computable on device in microseconds, explainable to a teacher,
            // and unit-testable. A model earns its place when it learns
            // something we cannot express; this one only ever imitated code we
            // already had, and imitated it badly.
            //
            // The interpreter path is kept for a genuinely trained model —
            // one fitted to real recordings with known outcomes rather than
            // distilled from this heuristic. Set USE_TFLITE_MODEL when that
            // exists and has passed validation.
            if (USE_TFLITE_MODEL && interpreter != null) {
                runModelInference(audioFile, videoFrames, durationMs)
            } else {
                heuristicResult(audioFile, durationMs)
            }
        }

    private fun requireEnoughSpeech(audioFile: File, durationMs: Int) {
        if (durationMs < MIN_DURATION_MS) {
            throw InsufficientAudioException("recording too short (${durationMs}ms)")
        }
        val samples = readPcmSamples(audioFile)
        if (samples.isEmpty()) throw InsufficientAudioException("no audio captured")

        // Share of samples above the silence floor. Real reading sits well
        // above this; a muted mic or a quiet room sits far below.
        val voiced = samples.count { kotlin.math.abs(it) > SILENCE_LEVEL }.toFloat() / samples.size
        if (voiced < MIN_VOICED_FRACTION) {
            throw InsufficientAudioException("almost no speech detected (voiced=%.3f)".format(voiced))
        }
    }

    // ── TFLite inference path ─────────────────────────────────────────────────

    private fun runModelInference(
        audioFile: File,
        videoFrames: List<Bitmap>,
        durationMs: Int
    ): ScreeningResult {
        return try {
            val mfcc        = extractMFCC(audioFile)
            val frameEmbeds = extractFrameEmbeddings(videoFrames)
            val output      = Array(1) { FloatArray(4) }

            // Multi-input run: audio branch + visual branch
            interpreter!!.runForMultipleInputsOutputs(
                arrayOf(mfcc, frameEmbeds),
                mapOf(0 to output)
            )

            val scores = output[0]
            buildResult(
                riskScore          = scores[0].coerceIn(0f, 1f),
                phonemeErrorRate   = scores[1].coerceIn(0f, 1f),
                letterReversalRate = scores[2].coerceIn(0f, 1f),
                syllableSkipRate   = scores[3].coerceIn(0f, 1f),
                // The current model output has no lip-sync head, and callers do
                // not capture video frames yet — never fabricate this value.
                lipSyncConfidence  = null,
                durationMs         = durationMs,
                modelVersion       = MODEL_VERSION_CNN,
            )
        } catch (e: Exception) {
            heuristicResult(audioFile, durationMs)
        }
    }

    // ── Audio preprocessing: MFCC extraction ─────────────────────────────────

    /**
     * Extracts 40 MFCC coefficients × up to 300 frames from the audio file.
     * Returns a ByteBuffer shaped [1, 300, 40] in float32 for TFLite.
     */
    private fun extractMFCC(audioFile: File): ByteBuffer {
        val samples     = readPcmSamples(audioFile)
        val sampleRate  = 16_000
        val frameLen    = (0.025f * sampleRate).toInt()   // 25 ms
        val hopLen      = (0.010f * sampleRate).toInt()   // 10 ms
        val numFilters  = 40
        val maxFrames   = 300

        val preEmphasised = preEmphasise(samples)
        val frames        = framing(preEmphasised, frameLen, hopLen)
        val limitedFrames = if (frames.size > maxFrames) frames.take(maxFrames) else frames

        val mfccMatrix = Array(maxFrames) { FloatArray(numFilters) }
        limitedFrames.forEachIndexed { i, frame ->
            val windowed     = applyHamming(frame)
            val powerSpec    = powerSpectrum(windowed)
            val melEnergies  = melFilterbank(powerSpec, sampleRate, numFilters)
            mfccMatrix[i]    = dct(melEnergies)
        }

        val buf = ByteBuffer.allocateDirect(1 * maxFrames * numFilters * 4)
        buf.order(ByteOrder.nativeOrder())
        for (frame in mfccMatrix) {
            for (v in frame) buf.putFloat(v)
        }
        buf.rewind()
        return buf
    }

    /** Read raw PCM float samples.  Falls back to silent (all-zero) array on failure. */
    private fun readPcmSamples(file: File): FloatArray {
        return try {
            val bytes = file.readBytes()
            // Treat file as raw 16-bit LE PCM @ 16 kHz (MediaRecorder default).
            // Skip any WAV header (44 bytes) if present.
            val offset = if (bytes.size > 44 &&
                bytes[0] == 'R'.code.toByte() &&
                bytes[1] == 'I'.code.toByte()
            ) 44 else 0
            val numSamples = (bytes.size - offset) / 2
            FloatArray(numSamples) { i ->
                val lo  = bytes[offset + i * 2].toInt() and 0xFF
                val hi  = bytes[offset + i * 2 + 1].toInt()
                (hi shl 8 or lo).toShort() / 32768f
            }
        } catch (_: Exception) {
            FloatArray(16_000) // 1 s of silence as fallback
        }
    }

    private fun preEmphasise(samples: FloatArray, coeff: Float = 0.97f): FloatArray {
        val out = FloatArray(samples.size)
        out[0] = samples[0]
        for (i in 1 until samples.size) out[i] = samples[i] - coeff * samples[i - 1]
        return out
    }

    private fun framing(samples: FloatArray, frameLen: Int, hopLen: Int): List<FloatArray> {
        val frames = mutableListOf<FloatArray>()
        var start = 0
        while (start + frameLen <= samples.size) {
            frames.add(samples.copyOfRange(start, start + frameLen))
            start += hopLen
        }
        return frames
    }

    private fun applyHamming(frame: FloatArray): FloatArray {
        val n = frame.size
        return FloatArray(n) { i ->
            frame[i] * (0.54f - 0.46f * cos(2.0 * Math.PI * i / (n - 1)).toFloat())
        }
    }

    /** Single-sided power spectrum via real DFT (O(N²) — acceptable for N≈400). */
    private fun powerSpectrum(frame: FloatArray): FloatArray {
        val n    = frame.size
        val half = n / 2 + 1
        val ps   = FloatArray(half)
        for (k in 0 until half) {
            var re = 0.0
            var im = 0.0
            for (t in 0 until n) {
                val angle = -2.0 * Math.PI * k * t / n
                re += frame[t] * cos(angle)
                im += frame[t] * kotlin.math.sin(angle)
            }
            ps[k] = (re * re + im * im).toFloat()
        }
        return ps
    }

    /** Convert Hz to Mel scale and back. */
    private fun hzToMel(hz: Float) = 2595f * log10(1f + hz / 700f)
    private fun melToHz(mel: Float) = 700f * (10f.pow(mel / 2595f) - 1f)

    private fun melFilterbank(powerSpec: FloatArray, sampleRate: Int, numFilters: Int): FloatArray {
        val n       = (powerSpec.size - 1) * 2
        val lowMel  = hzToMel(80f)
        val highMel = hzToMel(sampleRate / 2f)
        val melPoints = FloatArray(numFilters + 2) { i ->
            melToHz(lowMel + i * (highMel - lowMel) / (numFilters + 1))
        }
        val binPoints = IntArray(numFilters + 2) { i ->
            ((melPoints[i] / (sampleRate / 2f)) * (n / 2)).toInt().coerceIn(0, powerSpec.size - 1)
        }

        val energies = FloatArray(numFilters)
        for (m in 0 until numFilters) {
            val left  = binPoints[m]
            val peak  = binPoints[m + 1]
            val right = binPoints[m + 2]
            var sum = 0f
            for (k in left..peak) {
                if (peak > left) sum += powerSpec[k] * (k - left).toFloat() / (peak - left)
            }
            for (k in peak..right) {
                if (right > peak) sum += powerSpec[k] * (right - k).toFloat() / (right - peak)
            }
            energies[m] = if (sum > 0f) ln(sum.toDouble()).toFloat() else 0f
        }
        return energies
    }

    /** Type-II DCT. */
    private fun dct(input: FloatArray): FloatArray {
        val n = input.size
        return FloatArray(n) { k ->
            var sum = 0.0
            for (t in 0 until n) {
                sum += input[t] * cos(Math.PI * k * (2.0 * t + 1) / (2.0 * n))
            }
            (sum * sqrt(2.0 / n)).toFloat()
        }
    }

    // ── Video preprocessing: frame embeddings ────────────────────────────────

    /**
     * Resize frames to 224×224, normalise to [-1,1], flatten.
     * Returns a FloatArray sized frames×1280 (MobileNetV2 output dim placeholder).
     */
    private fun extractFrameEmbeddings(frames: List<Bitmap>): FloatArray {
        if (frames.isEmpty()) return FloatArray(1280)
        val embedDim = 1280
        val allEmbeds = FloatArray(frames.size * embedDim)
        frames.forEachIndexed { fi, frame ->
            val scaled = Bitmap.createScaledBitmap(frame, 224, 224, true)
            // Simple mean-colour embedding per channel block (placeholder until model is ready)
            val base = fi * embedDim
            var idx  = 0
            val blockSz = 224 / 32
            for (by in 0 until 32) {
                for (bx in 0 until 32) {
                    var rSum = 0f; var gSum = 0f; var bSum = 0f; var count = 0f
                    for (py in 0 until blockSz) {
                        for (px in 0 until blockSz) {
                            val pixel = scaled.getPixel(bx * blockSz + px, by * blockSz + py)
                            rSum += Color.red(pixel) / 127.5f - 1f
                            gSum += Color.green(pixel) / 127.5f - 1f
                            bSum += Color.blue(pixel) / 127.5f - 1f
                            count++
                        }
                    }
                    if (idx + 2 < embedDim) {
                        allEmbeds[base + idx++] = rSum / count
                        allEmbeds[base + idx++] = gSum / count
                        allEmbeds[base + idx++] = bSum / count
                    }
                }
            }
        }
        return allEmbeds
    }

    // ── Heuristic fallback (no model file) ───────────────────────────────────

    /**
     * When the TFLite model is unavailable, produce a rough risk estimate from
     * audio duration and basic energy variance (pause detection proxy).
     *
     * Only metrics the audio can actually support are populated; letter
     * reversal, lip-sync and WPM stay null rather than being invented.
     * Results carry modelVersion "heuristic-0.1" so the teacher UI can label
     * them as estimates, never clinical readings.
     */
    private fun heuristicResult(audioFile: File, durationMs: Int): ScreeningResult {
        val samples = readPcmSamples(audioFile)
        val pauses  = countLongPauses(samples, sampleRate = 16_000, thresholdMs = 300)
        val meanSquare = samples.map { it * it }.average().toFloat()
        val scores = ScreeningHeuristic.score(meanSquare, pauses)

        return buildResult(
            riskScore          = scores.risk,
            phonemeErrorRate   = scores.phonemeErrorRate,
            letterReversalRate = null,   // audio alone cannot detect reversals
            syllableSkipRate   = scores.syllableSkipRate,
            lipSyncConfidence  = null,   // no video captured
            durationMs         = durationMs,
            modelVersion       = MODEL_VERSION_HEURISTIC,
        )
    }

    private fun countLongPauses(
        samples: FloatArray,
        sampleRate: Int,
        thresholdMs: Int,
        silenceLevel: Float = 0.01f
    ): Int {
        val minSilenceSamples = sampleRate * thresholdMs / 1000
        var count = 0
        var silenceRun = 0
        var inSilence = false
        samples.forEach { s ->
            if (kotlin.math.abs(s) < silenceLevel) {
                silenceRun++
                if (silenceRun >= minSilenceSamples && !inSilence) {
                    count++
                    inSilence = true
                }
            } else {
                silenceRun = 0
                inSilence = false
            }
        }
        return count
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun estimateAudioDurationMs(file: File): Int {
        // ~32 kbps AMR-NB/3GP from MediaRecorder; WAV @ 16-bit 16 kHz = 32000 bytes/s
        val bytesPerSecond = 32_000
        return ((file.length().toDouble() / bytesPerSecond) * 1000).toInt().coerceAtLeast(1_000)
    }

    private fun buildResult(
        riskScore: Float,
        phonemeErrorRate: Float,
        letterReversalRate: Float?,
        syllableSkipRate: Float,
        lipSyncConfidence: Float?,
        durationMs: Int,
        modelVersion: String,
    ): ScreeningResult {
        val tags = mutableListOf<String>()
        if (phonemeErrorRate > 0.40f) tags.add("\"phoneme_confusion\"")
        if ((letterReversalRate ?: 0f) > 0.30f) tags.add("\"letter_reversal\"")
        if (syllableSkipRate > 0.30f) tags.add("\"syllable_skip\"")

        return ScreeningResult(
            riskScore          = riskScore,
            phonemeErrorRate   = phonemeErrorRate,
            letterReversalRate = letterReversalRate,
            syllableSkipRate   = syllableSkipRate,
            lipSyncConfidence  = lipSyncConfidence,
            riskLevel          = riskLevel(riskScore),
            errorTagsJson      = "[${tags.joinToString(",")}]",
            audioDurationMs    = durationMs,
            // WPM needs a transcription (words actually read); the fixed
            // syllable-rate formula used before reduced to a constant 96 WPM
            // for every child. Report nothing until ASR lands.
            readingSpeedWpm    = null,
            modelVersion       = modelVersion,
        )
    }

    private fun riskLevel(score: Float) = when {
        score >= 0.65f -> "high"
        score >= 0.35f -> "medium"
        else           -> "low"
    }

    private fun loadModelFile(assetPath: String): MappedByteBuffer {
        val fd = context.assets.openFd(assetPath)
        return FileInputStream(fd.fileDescriptor).channel
            .map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
    }

    companion object {
        // The bundled TFLite model is a CNN distillation of the audio
        // heuristic (see ml-tools/train_screening_tflite.py) — not a
        // clinically trained classifier. The "heuristic" prefix keeps the
        // teacher UI's "Estimate" label on these results. When a clinically
        // validated model ships, change this to a "cnn-" version.
        /** Below these the clip is rejected rather than scored. */
        private const val MIN_DURATION_MS = 3_000
        private const val MIN_VOICED_FRACTION = 0.02f
        private const val SILENCE_LEVEL = 0.01f

        /**
         * Off until a model trained on real recordings replaces the
         * distillation. See the note in [run].
         */
        const val USE_TFLITE_MODEL = false

        const val MODEL_VERSION_CNN = "heuristic-cnn-1.0"
        const val MODEL_VERSION_HEURISTIC = "heuristic-0.1"
    }
}
