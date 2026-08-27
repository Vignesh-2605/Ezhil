package com.ezhil.app.ui.screens.student

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.ezhil.app.data.local.EzhilDatabase
import com.ezhil.app.data.local.SecurePrefs
import com.ezhil.app.data.local.entity.AssessmentEntity
import com.ezhil.app.ml.ScreeningModel
import com.ezhil.app.ui.components.*
import com.ezhil.app.ui.navigation.Screen
import com.ezhil.app.ui.strings.AppLanguage
import com.ezhil.app.ui.strings.EzhilStrings
import com.ezhil.app.ui.strings.StringKey
import com.ezhil.app.ui.theme.*
import com.ezhil.app.ui.viewmodel.AppLanguageViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@HiltViewModel
class ReadAloudViewModel @Inject constructor(
    private val db: EzhilDatabase,
    private val prefs: SecurePrefs,
    private val screeningModel: ScreeningModel
) : ViewModel() {

    enum class AssessmentState { INSTRUCTIONS, IDLE, RECORDING, PROCESSING, COMPLETE, TRY_AGAIN }

    private val _state = MutableStateFlow(AssessmentState.INSTRUCTIONS)
    val state: StateFlow<AssessmentState> = _state

    private val _showStopConfirm = MutableStateFlow(false)
    val showStopConfirm: StateFlow<Boolean> = _showStopConfirm

    private val _processingStep = MutableStateFlow(0)
    val processingStep: StateFlow<Int> = _processingStep

    /**
     * Reading passage, rotated per screening so repeat assessments measure
     * reading — not memorisation of one fixed text. Sourced from the
     * published lesson bank; falls back to the built-in passage when the
     * device has no lessons yet.
     */
    val passage: StateFlow<List<String>> = kotlinx.coroutines.flow.flow {
        val studentId = prefs.activeStudentId
        val priorCount = if (studentId != null) {
            db.assessmentDao().observeByStudent(studentId).first().size
        } else 0
        db.lessonDao().observePublished().collect { lessons ->
            val passages = lessons
                .mapNotNull { passageLines(it.contentJson) }
                .filter { it.size >= 3 }
            emit(if (passages.isEmpty()) DEFAULT_PASSAGE else passages[priorCount % passages.size])
        }
    }.stateIn(
        viewModelScope,
        kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        DEFAULT_PASSAGE
    )

    private fun passageLines(contentJson: String): List<String>? = try {
        val arr = org.json.JSONObject(contentJson).getJSONObject("passage").getJSONArray("lines")
        (0 until arr.length())
            .map { arr.getString(it).trim() }
            .filter { it.isNotBlank() && !it.startsWith("###") }
    } catch (_: Exception) {
        null
    }

    private var audioFile: File? = null
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val shouldRecord = AtomicBoolean(false)
    private val analysisStarted = AtomicBoolean(false)

    /** Rolling window of recent mic RMS levels (0..1) driving the live
     *  waveform — the child sees the bars move with their own voice. */
    private val _micLevels = MutableStateFlow(List(WAVEFORM_BARS) { 0f })
    val micLevels: StateFlow<List<Float>> = _micLevels

    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val WAVEFORM_BARS = 9

        /** Hard ceiling on one reading session. A child who walks away leaves
         *  the mic open otherwise, and the PCM buffer grows until the app is
         *  killed. Matches the web client's 180 s cap. */
        const val MAX_RECORDING_MS = 180_000L
        private const val BYTES_PER_SECOND = SAMPLE_RATE * 2  // 16-bit mono
        private const val MAX_PCM_BYTES = (MAX_RECORDING_MS / 1000) * BYTES_PER_SECOND

        val DEFAULT_PASSAGE = listOf(
            "மழை பெய்கிறது.",
            "மரங்கள் குளிக்கின்றன.",
            "குழந்தைகள் மகிழ்கின்றனர்.",
            "புல் பசுமையாக இருக்கிறது.",
            "வானவில் தோன்றுகிறது.",
            "அனைவரும் மகிழ்ச்சியாக இருக்கிறார்கள்."
        )
    }

    fun proceed() {
        _state.value = AssessmentState.IDLE
    }

    @SuppressLint("MissingPermission")
    fun startRecording(context: Context) {
        analysisStarted.set(false)   // new session — allow scoring again
        audioFile = File(context.cacheDir, "assessment_${System.currentTimeMillis()}.wav")
        val bufSize = maxOf(AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT), 4096)
        audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufSize * 4)
        shouldRecord.set(true)
        audioRecord!!.startRecording()
        _state.value = AssessmentState.RECORDING
        recordingJob = viewModelScope.launch(Dispatchers.IO) {
            val pcmStream = ByteArrayOutputStream()
            val buffer = ByteArray(bufSize)
            var hitLimit = false
            while (shouldRecord.get()) {
                val read = audioRecord?.read(buffer, 0, bufSize) ?: break
                if (read > 0) {
                    pcmStream.write(buffer, 0, read)

                    // Stop at the ceiling rather than recording until the
                    // process dies. Measured on bytes captured, so it holds
                    // even if the coroutine is descheduled.
                    if (pcmStream.size() >= MAX_PCM_BYTES) {
                        hitLimit = true
                        shouldRecord.set(false)
                    }

                    // RMS of this chunk (16-bit LE) → 0..1 level for the bars
                    var sumSq = 0.0
                    var i = 0
                    while (i + 1 < read) {
                        val s = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort()
                        sumSq += (s / 32768.0) * (s / 32768.0)
                        i += 2
                    }
                    val rms = kotlin.math.sqrt(sumSq / (read / 2).coerceAtLeast(1))
                    val level = (rms * 6f).toFloat().coerceIn(0f, 1f) // speech gain
                    _micLevels.value = _micLevels.value.drop(1) + level
                }
            }
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            _micLevels.value = List(WAVEFORM_BARS) { 0f }
            writeWavFile(audioFile!!, pcmStream.toByteArray())

            // Cap reached: score what we captured instead of leaving the child
            // on a recording screen that never ends. stopAndAnalyse() joins
            // this job from a separate coroutine, so this does not deadlock.
            if (hitLimit) stopAndAnalyse()
        }
    }

    fun requestStop() { _showStopConfirm.value = true }
    fun cancelStop()  { _showStopConfirm.value = false }
    fun confirmStop() { _showStopConfirm.value = false; stopAndAnalyse() }

    private fun stopAndAnalyse() {
        shouldRecord.set(false)
        // The child tapping Stop and the duration cap can fire together;
        // scoring the same clip twice would save two assessments.
        if (!analysisStarted.compareAndSet(false, true)) return
        _state.value = AssessmentState.PROCESSING
        _processingStep.value = 0
        viewModelScope.launch {
            launch {
                while (_state.value == AssessmentState.PROCESSING) {
                    delay(1800)
                    _processingStep.value = (_processingStep.value + 1) % 4
                }
            }
            try {
                recordingJob?.join()
                val file = audioFile ?: run { _state.value = AssessmentState.TRY_AGAIN; return@launch }
                val result = screeningModel.run(file, emptyList())
                saveAssessment(result)
                file.delete()
                audioFile = null
                _state.value = AssessmentState.COMPLETE
            } catch (e: ScreeningModel.InsufficientAudioException) {
                // Expected: the child did not read, or the mic caught nothing.
                android.util.Log.i("EzhilScreening", "not scored — ${e.message}")
                audioFile?.delete()
                audioFile = null
                _state.value = AssessmentState.TRY_AGAIN
            } catch (e: Exception) {
                android.util.Log.e("EzhilScreening", "screening failed", e)
                audioFile?.delete()
                audioFile = null
                _state.value = AssessmentState.TRY_AGAIN
            }
        }
    }

    fun retry() { _state.value = AssessmentState.IDLE }
    fun reset() { _state.value = AssessmentState.INSTRUCTIONS }

    private suspend fun saveAssessment(result: com.ezhil.app.ml.ScreeningResult) {
        val studentId = prefs.activeStudentId ?: return
        db.assessmentDao().insert(AssessmentEntity(
            id = UUID.randomUUID().toString(), studentId = studentId,
            conductedAt = Instant.now().toString(),
            readingSpeedWpm = result.readingSpeedWpm, phonemeErrorRate = result.phonemeErrorRate,
            letterReversalRate = result.letterReversalRate, syllableSkipRate = result.syllableSkipRate,
            lipSyncConfidence = result.lipSyncConfidence, cnnRiskScore = result.riskScore,
            riskLevel = result.riskLevel, errorTagsJson = result.errorTagsJson,
            audioDurationMs = result.audioDurationMs, modelVersion = result.modelVersion,
            syncStatus = "pending"
        ))
        db.studentDao().updateRiskLevel(studentId, result.riskLevel)
    }

    override fun onCleared() {
        super.onCleared()
        shouldRecord.set(false)
        audioRecord?.stop()
        audioRecord?.release()
    }

    private fun writeWavFile(file: File, pcmData: ByteArray) {
        val byteRate = SAMPLE_RATE * 1 * 16 / 8
        file.outputStream().use { out ->
            out.write("RIFF".toByteArray()); out.write(le32(36 + pcmData.size))
            out.write("WAVE".toByteArray()); out.write("fmt ".toByteArray())
            out.write(le32(16)); out.write(le16(1)); out.write(le16(1))
            out.write(le32(SAMPLE_RATE)); out.write(le32(byteRate))
            out.write(le16(2)); out.write(le16(16))
            out.write("data".toByteArray()); out.write(le32(pcmData.size)); out.write(pcmData)
        }
    }
    private fun le32(v: Int)   = byteArrayOf(v.toByte(),(v shr 8).toByte(),(v shr 16).toByte(),(v shr 24).toByte())
    private fun le16(v: Int)   = byteArrayOf(v.toByte(),(v shr 8).toByte())
    private fun le16(v: Short) = byteArrayOf(v.toByte(),(v.toInt() shr 8).toByte())
}

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun ReadAloudScreen(
    navController: NavHostController,
    vm: ReadAloudViewModel = hiltViewModel(),
    langVm: AppLanguageViewModel = hiltViewModel()
) {
    val language by langVm.language.collectAsState()
    val state by vm.state.collectAsState()
    val showStopConfirm by vm.showStopConfirm.collectAsState()
    val processingStep by vm.processingStep.collectAsState()
    val context = LocalContext.current

    var showMicDenied by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) vm.startRecording(context)
        else showMicDenied = true
    }

    fun onStartRecording() {
        if (hasPermission) vm.startRecording(context)
        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // ── Mic denied full-screen ────────────────────────────────────────────────
    if (showMicDenied) {
        MicPermissionScreen(
            language = language,
            onOpenSettings = {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}"))
                context.startActivity(intent)
            },
            onNotNow = { showMicDenied = false; navController.popBackStack() }
        )
        return
    }

    when (state) {
        ReadAloudViewModel.AssessmentState.INSTRUCTIONS -> {
            ReadAloudInstructionsScreen(
                language = language,
                onStart = { vm.proceed() },
                onBack = { navController.popBackStack() },
                langVm = langVm
            )
        }

        ReadAloudViewModel.AssessmentState.TRY_AGAIN -> {
            ReadAloudTryAgainScreen(
                language = language,
                onRetry = { vm.retry() },
                onGoHome = { vm.reset(); navController.navigate(Screen.StudentHome.route) { popUpTo(Screen.StudentHome.route) { inclusive = false } } }
            )
        }

        ReadAloudViewModel.AssessmentState.PROCESSING -> {
            ReadAloudProcessingScreen(language = language, step = processingStep)
        }

        ReadAloudViewModel.AssessmentState.COMPLETE -> {
            ReadAloudCompleteScreen(
                language = language,
                onContinue = {
                    vm.reset()
                    navController.navigate(Screen.Achievement.route("reader_badge", 3)) {
                        popUpTo(Screen.ReadAloud.route) { inclusive = true }
                    }
                }
            )
        }

        else -> {
            // IDLE or RECORDING — shared reader layout
            val tamilPassage by vm.passage.collectAsState()
            val micLevels by vm.micLevels.collectAsState()
            ReadAloudReaderLayout(
                state = state,
                language = language,
                tamilPassage = tamilPassage,
                micLevels = micLevels,
                showStopConfirm = showStopConfirm,
                langVm = langVm,
                onBack = { navController.popBackStack() },
                onStart = { onStartRecording() },
                onRequestStop = { vm.requestStop() },
                onCancelStop = { vm.cancelStop() },
                onConfirmStop = { vm.confirmStop() }
            )
        }
    }
}

// ── Instructions Screen (screen_007) ─────────────────────────────────────────

@Composable
private fun ReadAloudInstructionsScreen(
    language: AppLanguage,
    onStart: () -> Unit,
    onBack: () -> Unit,
    langVm: AppLanguageViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgCard)
                .border(width = 1.dp, color = Border)
                .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextSecondary
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (language == AppLanguage.TAMIL) "படிக்க ஆரம்பிக்கலாம்!" else "Read Aloud",
                    fontFamily = BaloTamizha2,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = TextPrimary
                )
                Text(
                    "READ ALOUD ASSESSMENT",
                    fontFamily = DMSans,
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
            LanguageToggle(current = language, onToggle = { langVm.toggle() })
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Spacer(Modifier.height(Spacing.lg))

            EzhilanWidget(state = EzhilanState.IDLE, size = 100.dp)

            Spacer(Modifier.height(Spacing.xs))

            Text(
                if (language == AppLanguage.TAMIL) "படிக்க ஆரம்பிக்கலாம்!" else "Let's start reading!",
                fontFamily = BaloTamizha2,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                color = Cyan,
                textAlign = TextAlign.Center
            )
            Text(
                if (language == AppLanguage.TAMIL) "Let's start reading!" else "உரக்கப் படிக்கவும்",
                fontFamily = DMSans,
                fontSize = 14.sp,
                color = TextMuted,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(Spacing.xs))

            val instructions = listOf(
                Triple(
                    "📱",
                    "மொபைலை இயற்பாகப் பிடிக்கவும்.",
                    "Hold phone naturally while reading"
                ),
                Triple(
                    "👁",
                    "படிக்கும் போது திரையைப் பார்க்கவும்.",
                    "Look at the screen while reading"
                ),
                Triple(
                    "🎤",
                    "பத்தியைத் தெளிவாக உரக்கப் படிக்கவும்.",
                    "Read the passage out loud, clearly"
                )
            )

            instructions.forEach { (icon, tamil, english) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BgCard, RoundedCornerShape(14.dp))
                        .border(1.dp, Border, RoundedCornerShape(14.dp))
                        .padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(CyanDim, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(icon, fontSize = 20.sp)
                    }
                    Spacer(Modifier.width(Spacing.md))
                    Column {
                        Text(
                            if (language == AppLanguage.TAMIL) tamil else english,
                            fontFamily = if (language == AppLanguage.TAMIL) NotoSansTamil else DMSans,
                            fontSize = 14.sp,
                            color = TextPrimary
                        )
                        if (language == AppLanguage.TAMIL) {
                            Text(
                                english,
                                fontFamily = DMSans,
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(Spacing.md))
        }

        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            EzhilButton(
                label = if (language == AppLanguage.TAMIL) "ஆரம்பிக்கலாம்! / START!" else "ஆரம்பிக்கலாம்! / START!",
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = Amber,
                textColor = TextOnAmber
            )
            Text(
                if (language == AppLanguage.TAMIL)
                    "எல்லாம் இந்த சாதனத்திலேயே சேமிக்கப்படும் — இணையம் தேவையில்லை"
                else
                    "Everything is saved on this device — no internet needed",
                fontFamily = DMSans,
                fontSize = 12.sp,
                color = TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ── Reader Layout (IDLE + RECORDING) ─────────────────────────────────────────

@Composable
private fun ReadAloudReaderLayout(
    state: ReadAloudViewModel.AssessmentState,
    language: AppLanguage,
    tamilPassage: List<String>,
    micLevels: List<Float>,
    showStopConfirm: Boolean,
    langVm: AppLanguageViewModel,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onRequestStop: () -> Unit,
    onCancelStop: () -> Unit,
    onConfirmStop: () -> Unit
) {
    val isRecording = state == ReadAloudViewModel.AssessmentState.RECORDING

    // Tamil passages rotate from the lesson bank (see ReadAloudViewModel.passage);
    // English mode keeps a fixed simple passage — lessons are Tamil-first.
    val passage = if (language == AppLanguage.TAMIL) tamilPassage else listOf(
        "The rain is falling.",
        "Trees are bathing.",
        "Children are happy.",
        "The grass is green.",
        "A rainbow appears.",
        "Everyone is joyful."
    )

    // Bars driven by the child's REAL voice level (see ReadAloudViewModel.micLevels)
    val waveHeights = micLevels.map { level ->
        animateFloatAsState(
            targetValue = 6f + level * 42f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessHigh
            ),
            label = "wave_bar"
        ).value
    }

    Column(modifier = Modifier.fillMaxSize().background(BgDark)) {

        // ── Top bar ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgCard)
                .border(width = 1.dp, color = Border)
                .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextSecondary
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                if (isRecording) {
                    Box(
                        modifier = Modifier
                            .background(RiskHighBg, RoundedCornerShape(12.dp))
                            .border(1.dp, RiskHigh.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                    ) {
                        Text(
                            "● REC",
                            fontFamily = DMSans,
                            fontSize = 12.sp,
                            color = RiskHigh,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        EzhilStrings.get(StringKey.READ_ALOUD, language),
                        fontFamily = BaloTamizha2,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                    Text(
                        "READ ALOUD",
                        fontFamily = DMSans,
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                EzhilanWidget(
                    state = if (isRecording) EzhilanState.PROCESSING else EzhilanState.IDLE,
                    size = 36.dp
                )
                LanguageToggle(current = language, onToggle = { langVm.toggle() })
            }
        }

        // ── Listen-to-passage row (hidden when no Tamil voice installed) ──────
        if (!isRecording) {
            val tts = rememberTamilTts()
            if (tts.available) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = screenGutter(), vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .clickable { tts.speak(passage.joinToString(" "), "read_aloud_passage") }
                            .background(CyanDim, RoundedCornerShape(20.dp))
                            .border(1.dp, Cyan.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Listen to the passage",
                            tint = Cyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            if (language == AppLanguage.TAMIL) "கேட்க" else "Listen",
                            fontFamily = DMSans,
                            fontSize = 12.sp,
                            color = Cyan,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // ── Cream reader area — dyslexia spec, NEVER dark background ─────────
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = screenGutter(), vertical = Spacing.sm)
                .background(BgReader, RoundedCornerShape(20.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Spacing.lg)
                    .verticalScroll(rememberScrollState())
            ) {
                passage.forEach { line ->
                    Text(
                        text = line,
                        style = TextStyle(
                            fontFamily = ReaderConstraints.FontFamily,
                            fontSize = ReaderConstraints.FontSize,
                            lineHeight = ReaderConstraints.LineHeight,
                            letterSpacing = ReaderConstraints.LetterSpacing,
                            color = ReaderConstraints.TextColor
                        )
                    )
                    Spacer(Modifier.height(Spacing.sm))
                }
            }
        }

        // ── Control bar ───────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgCard)
                .border(width = 1.dp, color = Border)
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            if (isRecording) {
                // Live waveform visualizer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    waveHeights.forEach { h ->
                        // Louder voice → taller, brighter bar
                        val alpha = 0.35f + ((h - 6f) / 42f) * 0.65f
                        Box(
                            modifier = Modifier
                                .width(5.dp)
                                .height(h.dp)
                                .background(Cyan.copy(alpha = alpha.coerceIn(0.35f, 1f)), RoundedCornerShape(3.dp))
                        )
                        Spacer(Modifier.width(5.dp))
                    }
                }
                Text(
                    EzhilStrings.get(StringKey.ASSESS_LISTENING, language),
                    fontFamily = BaloTamizha2,
                    fontSize = 14.sp,
                    color = Cyan,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                EzhilButton(
                    label = if (language == AppLanguage.TAMIL) "⏹ நிறுத்து / STOP RECORDING" else "⏹ STOP RECORDING",
                    onClick = onRequestStop,
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = RiskHigh,
                    textColor = TextPrimary
                )
            } else {
                EzhilButton(
                    label = EzhilStrings.get(StringKey.ASSESS_START, language),
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    // ── Stop Confirm Dialog (screen_003) ──────────────────────────────────────
    if (showStopConfirm) {
        AlertDialog(
            onDismissRequest = onCancelStop,
            containerColor = BgCard,
            shape = RoundedCornerShape(20.dp),
            icon = {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(RiskHighBg, CircleShape)
                        .border(2.dp, RiskHigh.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("⚠️", fontSize = 24.sp)
                }
            },
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Recording நிறுத்தணுமா?",
                        fontFamily = BaloTamizha2,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "Stop Recording?",
                        fontFamily = DMSans,
                        fontSize = 13.sp,
                        color = TextMuted
                    )
                }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "உங்களது பதிவு நீக்கப்படும்.",
                        fontFamily = NotoSansTamil,
                        fontSize = 14.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "Your recording will be lost.",
                        fontFamily = DMSans,
                        fontSize = 13.sp,
                        color = TextMuted,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                EzhilButton(
                    label = "▶ தொடர் / Keep Recording",
                    onClick = onCancelStop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = screenGutter())
                )
            },
            dismissButton = {
                TextButton(onClick = onConfirmStop, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "✗ நிறுத்து / Discard",
                        color = RiskHigh,
                        fontFamily = DMSans,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        )
    }
}

// ── Processing / Analysis Screen (screen_005) ─────────────────────────────────

@Composable
private fun ReadAloudProcessingScreen(language: AppLanguage, step: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "progress")
    val progressAnim by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Restart),
        label = "bar"
    )
    val brainScale by infiniteTransition.animateFloat(
        initialValue = 0.95f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "brain_scale"
    )

    val statusSteps = listOf(
        Pair("ஒலி சரிபார்ப்பு", "Phonetic Check") to Pair("ஒலி வரைபடம்", "Sound Mapping"),
        Pair("வார்த்தை அடையாளம்", "Word Recognition") to Pair("உச்சரிப்புத் திறன்", "Fluency Mapping"),
        Pair("இலக்கண சரிபார்ப்பு", "Grammar Check") to Pair("ஆபத்து மதிப்பீடு", "Risk Assessment"),
        Pair("முடிவுகள் தயார்", "Results Ready") to Pair("தரவு சேமிப்பு", "Saving Data"),
    )
    val (statusPair, stepPair) = statusSteps[step % statusSteps.size]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(Modifier.height(Spacing.xl))

        // Animated brain
        Box(
            modifier = Modifier
                .size(120.dp)
                .graphicsLayer(scaleX = brainScale, scaleY = brainScale)
                .background(BgCard, CircleShape)
                .border(3.dp, Cyan.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("🧠", fontSize = 56.sp)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(
                if (language == AppLanguage.TAMIL) "படிப்பை பகுப்பாய்வு செய்கிறேன்..." else "Analysing your reading...",
                fontFamily = BaloTamizha2,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                "Analysing your reading progress and linguistic patterns...",
                fontFamily = DMSans,
                fontSize = 13.sp,
                color = TextMuted,
                textAlign = TextAlign.Center
            )
        }

        // Animated progress bar
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(Border, RoundedCornerShape(3.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progressAnim)
                        .background(Cyan, RoundedCornerShape(3.dp))
                )
            }
        }

        // Status + Step cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(BgCard, RoundedCornerShape(14.dp))
                    .border(1.dp, Cyan.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    "STATUS",
                    fontFamily = DMSans,
                    fontSize = 12.sp,
                    color = Cyan,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    statusPair.first,
                    fontFamily = NotoSansTamil,
                    fontSize = 13.sp,
                    color = TextPrimary
                )
                Text(
                    statusPair.second,
                    fontFamily = DMSans,
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(BgCard, RoundedCornerShape(14.dp))
                    .border(1.dp, Amber.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    "STEP",
                    fontFamily = DMSans,
                    fontSize = 12.sp,
                    color = Amber,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stepPair.first,
                    fontFamily = NotoSansTamil,
                    fontSize = 13.sp,
                    color = TextPrimary
                )
                Text(
                    stepPair.second,
                    fontFamily = DMSans,
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
        }

        // Footer privacy note
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgCard, RoundedCornerShape(14.dp))
                .border(1.dp, Border, RoundedCornerShape(14.dp))
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🔒", fontSize = 16.sp)
            Spacer(Modifier.width(Spacing.sm))
            Column {
                Text(
                    if (language == AppLanguage.TAMIL)
                        "மதிப்பாடு நடைபெறுகிறது. தயவுசெய்து காத்திருக்கவும்."
                    else
                        "Assessment in progress. Please wait.",
                    fontFamily = if (language == AppLanguage.TAMIL) NotoSansTamil else DMSans,
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
        }

        Spacer(Modifier.height(Spacing.md))
    }
}

// ── Try Again Screen (screen_008) ─────────────────────────────────────────────

@Composable
private fun ReadAloudTryAgainScreen(
    language: AppLanguage,
    onRetry: () -> Unit,
    onGoHome: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(Modifier.height(Spacing.xl))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(RiskMediumBg, CircleShape)
                    .border(2.dp, RiskMedium.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("⚠️", fontSize = 44.sp)
            }
            Text(
                if (language == AppLanguage.TAMIL) "மீண்டும் முயற்சிக்கலாம்" else "Let's Try Again",
                fontFamily = BaloTamizha2,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                "LET'S TRY AGAIN",
                fontFamily = DMSans,
                fontSize = 12.sp,
                color = TextMuted,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(Spacing.xs))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgCard, RoundedCornerShape(20.dp))
                    .border(1.dp, Border, RoundedCornerShape(20.dp))
                    .padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    if (language == AppLanguage.TAMIL)
                        "பதிவு மிகவும் அமைதியாக இருந்தது."
                    else
                        "The recording may have been too quiet.",
                    fontFamily = if (language == AppLanguage.TAMIL) NotoSansTamil else DMSans,
                    fontSize = 14.sp,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
                Text(
                    if (language == AppLanguage.TAMIL)
                        "மைக்ரோஃபோன் தெளிவாக இருக்கிறதா என்று பார்க்கவும்."
                    else
                        "Make sure the mic is clear.",
                    fontFamily = if (language == AppLanguage.TAMIL) NotoSansTamil else DMSans,
                    fontSize = 13.sp,
                    color = TextMuted,
                    textAlign = TextAlign.Center
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            EzhilButton(
                label = if (language == AppLanguage.TAMIL) "🔄 மீண்டும் முயற்சி / Try Again" else "🔄 Try Again",
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = Amber,
                textColor = TextOnAmber
            )
            EzhilOutlinedButton(
                label = if (language == AppLanguage.TAMIL) "வீடு / Go Home" else "வீடு / Go Home",
                onClick = onGoHome,
                modifier = Modifier.fillMaxWidth(),
                borderColor = Border,
                textColor = TextSecondary
            )
        }
    }
}

// ── Complete Screen ───────────────────────────────────────────────────────────

@Composable
private fun ReadAloudCompleteScreen(language: AppLanguage, onContinue: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "complete_pulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = screenGutter()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            EzhilanWidget(state = EzhilanState.CELEBRATING, size = 100.dp)

            StarRow(filled = 3, total = 3, size = 36.dp)

            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(SuccessBg.copy(alpha = glowAlpha), CircleShape)
                    .border(2.dp, Success.copy(alpha = glowAlpha), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("✓", fontSize = 36.sp, color = Success, fontWeight = FontWeight.Bold.also { FontWeight.Bold })
            }

            Text(
                EzhilStrings.get(StringKey.ASSESS_COMPLETE, language),
                fontFamily = BaloTamizha2,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                color = Success,
                textAlign = TextAlign.Center
            )
            Text(
                if (language == AppLanguage.TAMIL)
                    "மதிப்பீடு முடிந்தது. முடிவுகள் சேமிக்கப்பட்டன."
                else
                    "Assessment complete. Results saved.",
                fontFamily = DMSans,
                fontSize = 14.sp,
                color = TextMuted,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(Spacing.md))

            EzhilButton(
                label = if (language == AppLanguage.TAMIL) "தொடர்க →" else "Continue →",
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ── Mic Permission Screen (screen_027) ───────────────────────────────────────

@Composable
private fun MicPermissionScreen(
    language: AppLanguage,
    onOpenSettings: () -> Unit,
    onNotNow: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(Modifier.height(Spacing.xl))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(RiskHighBg, CircleShape)
                    .border(2.dp, RiskHigh.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("🎤", fontSize = 44.sp)
            }
            Text(
                "Microphone அனுமதி தேவை",
                fontFamily = BaloTamizha2,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                "Microphone Permission Needed",
                fontFamily = DMSans,
                fontSize = 14.sp,
                color = TextMuted
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                if (language == AppLanguage.TAMIL)
                    "உங்கள் வாசிப்பைக் கேட்க எழிலுக்கு மைக்ரோபோன் அணுகல் தேவை."
                else
                    "Ezhil needs mic access to hear you read.",
                fontFamily = if (language == AppLanguage.TAMIL) NotoSansTamil else DMSans,
                fontSize = 14.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
            if (language == AppLanguage.TAMIL) {
                Text(
                    "Ezhil needs mic access to hear you read.",
                    fontFamily = DMSans,
                    fontSize = 12.sp,
                    color = TextMuted,
                    textAlign = TextAlign.Center
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgCard, RoundedCornerShape(14.dp))
                    .border(1.dp, Border, RoundedCornerShape(14.dp))
                    .padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🛡️", fontSize = 18.sp)
                Spacer(Modifier.width(Spacing.sm))
                Column {
                    Text(
                        "தகவல் பாதுகாப்பு",
                        fontFamily = NotoSansTamil,
                        fontSize = 12.sp,
                        color = Cyan
                    )
                    Text(
                        "உங்கள் குரல் மட்டும் மதிப்பீட்டிற்காக மட்டுமே பயன்படுத்தப்படும்.",
                        fontFamily = NotoSansTamil,
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
            }
            EzhilButton(
                label = "Settings திற / Open Settings",
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth()
            )
            EzhilOutlinedButton(
                label = "இப்போது வேண்டாம் / Not Now",
                onClick = onNotNow,
                modifier = Modifier.fillMaxWidth(),
                borderColor = Border,
                textColor = TextMuted
            )
        }
    }
}
