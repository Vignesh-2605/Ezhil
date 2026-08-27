package com.ezhil.app.ui.screens.teacher

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.ezhil.app.data.local.EzhilDatabase
import com.ezhil.app.data.local.SecurePrefs
import com.ezhil.app.data.local.entity.LessonEntity
import com.ezhil.app.data.remote.EzhilApiService
import com.ezhil.app.data.remote.dto.GenerateRequest
import com.ezhil.app.data.remote.dto.LessonContent
import com.ezhil.app.data.remote.dto.OcrResponse
import com.ezhil.app.ml.MlKitOcrModel
import com.ezhil.app.ml.OcrModel
import com.ezhil.app.ui.components.*
import com.ezhil.app.ui.navigation.Screen
import com.ezhil.app.ui.strings.AppLanguage
import com.ezhil.app.ui.strings.EzhilStrings
import com.ezhil.app.ui.strings.StringKey
import com.ezhil.app.ui.theme.*
import com.ezhil.app.ui.viewmodel.AppLanguageViewModel
import com.squareup.moshi.Moshi
import dagger.hilt.android.lifecycle.HiltViewModel
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject

// ── File queue item ──────────────────────────────────────────────────────────

enum class FileItemStatus { PENDING, EXTRACTING, DONE, ERROR }

data class FileQueueItem(
    val uri:           Uri,
    val name:          String,
    val mimeType:      String,
    val status:        FileItemStatus = FileItemStatus.PENDING,
    val extractedText: String         = "",
    val progress:      String         = "",
    val error:         String?        = null
)

// ── ViewModel ────────────────────────────────────────────────────────────────

@HiltViewModel
class LessonStudioViewModel @Inject constructor(
    private val api:          EzhilApiService,
    private val db:           EzhilDatabase,
    private val prefs:        SecurePrefs,
    private val mlKitOcr:     MlKitOcrModel,
    private val ocrModel:     OcrModel,
    private val moshi:        Moshi
) : ViewModel() {

    sealed class StudioState {
        object Idle                                                   : StudioState()
        data class FileQueue(val items: List<FileQueueItem>)          : StudioState()
        object Generating                                             : StudioState()
        /**
         * The reader was not confident. These words become a passage a dyslexic
         * child will practise, so a wrong one costs more than the minute it
         * takes to check.
         */
        data class ReviewText(val text: String, val quality: OcrOutcome) : StudioState()
        data class ManualEntry(val hint: String = "")                 : StudioState()
        data class Done(val lessonId: String, val cacheHit: Boolean)  : StudioState()
        data class Error(val message: String)                         : StudioState()
    }

    private val _state = MutableStateFlow<StudioState>(StudioState.Idle)
    val state: StateFlow<StudioState> = _state

    var selectedDifficulty by mutableIntStateOf(1)
    var selectedLanguage   by mutableStateOf("tamil")

    // ── Entry point: process list of URIs ────────────────────────────────────
    fun processFiles(uris: List<Uri>, context: Context) {
        viewModelScope.launch {
            val items = uris.map { uri ->
                val mime = context.contentResolver.getType(uri) ?: ""
                FileQueueItem(uri = uri, name = getFileName(context, uri), mimeType = mime)
            }
            _state.value = StudioState.FileQueue(items)

            val working        = items.toMutableList()
            val combinedParts  = mutableListOf<String>()
            val outcomes       = mutableListOf<OcrOutcome>()

            for (i in working.indices) {
                working[i] = working[i].copy(status = FileItemStatus.EXTRACTING, progress = "…")
                _state.value = StudioState.FileQueue(working.toList())

                val item  = working[i]
                val bytes = context.contentResolver.openInputStream(item.uri)?.readBytes()
                    ?: ByteArray(0)

                val outcome = try {
                    extractBytes(bytes, item, context)
                } catch (e: Exception) {
                    working[i] = working[i].copy(
                        status = FileItemStatus.ERROR, error = e.message ?: "Error"
                    )
                    _state.value = StudioState.FileQueue(working.toList())
                    continue
                }

                working[i] = working[i].copy(
                    status        = FileItemStatus.DONE,
                    extractedText = outcome.text,
                    progress      = if (outcome.text.isNotBlank())
                        "${outcome.text.length} chars" else "nothing readable"
                )
                _state.value = StudioState.FileQueue(working.toList())
                if (outcome.text.isNotBlank()) {
                    combinedParts.add(outcome.text)
                    outcomes.add(outcome)
                }
            }

            val combined = combinedParts.joinToString("\n\n---\n\n")
            if (combined.isBlank()) { _state.value = StudioState.ManualEntry(); return@launch }

            // The batch inherits its weakest reading: one bad page is enough to
            // make the whole lesson wrong, so it is enough to warrant a check.
            val worst = outcomes.minByOrNull { it.confidence ?: 1f }
            val quality = OcrOutcome(
                text           = combined,
                confidence     = worst?.confidence,
                minConfidence  = worst?.minConfidence,
                engine         = worst?.engine,
                // Only a single image can reuse the hash the server cached
                // against this extraction; anything recombined here needs its own.
                sourceHash     = outcomes.singleOrNull()?.sourceHash
                    ?: ("multi:" + sha256(combined.toByteArray()).take(32)),
                requiresReview = outcomes.any { it.requiresReview },
                reviewReason   = outcomes.firstNotNullOfOrNull { it.reviewReason },
            )

            if (quality.requiresReview) {
                _state.value = StudioState.ReviewText(combined, quality)
                return@launch
            }

            generateFromText(combined, quality.sourceHash ?: "", quality)
        }
    }

    private suspend fun extractBytes(
        bytes: ByteArray, item: FileQueueItem, context: Context
    ): OcrOutcome {
        val mime = item.mimeType.lowercase()
        val name = item.name.lowercase()

        // A .docx or .txt is read exactly — no reader confidence is involved.
        fun exact(text: String) = OcrOutcome(text, 1f, 1f, "native")

        return when {
            mime.startsWith("image/") -> {
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                resolveOcrText(bmp, bytes, sha256(bytes))
            }
            mime == "application/pdf" || name.endsWith(".pdf") ->
                extractFromPdf(bytes, context)
            mime.contains("wordprocessingml") || name.endsWith(".docx") ->
                exact(uploadForExtraction(bytes, item.name, item.mimeType.ifBlank {
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                }))
            mime == "text/plain" || name.endsWith(".txt") ->
                exact(bytes.toString(Charsets.UTF_8).trim())
            else -> OcrOutcome.Empty
        }
    }

    /**
     * What a reading attempt produced, and how far to trust it.
     *
     * On-device readers report a confidence on their own scale, which is not
     * comparable to the server's. Only the engine name distinguishes them, so
     * it travels with the text.
     */
    data class OcrOutcome(
        val text: String,
        val confidence: Float?     = null,
        val minConfidence: Float?  = null,
        val engine: String?        = null,
        val sourceHash: String?    = null,
        val requiresReview: Boolean = false,
        val reviewReason: String?  = null,
    ) {
        companion object { val Empty = OcrOutcome("") }
    }

    // ── OCR priority chain ───────────────────────────────────────────────────
    /**
     * The server reads Tamil far better than anything on the device: PaddleOCR
     * scores 94% word accuracy on our reference page, against the on-device
     * models' single digits. So the server goes first whenever it is reachable,
     * and the local readers exist for when it is not — a classroom with no
     * connection still gets a lesson, just one the teacher has to check.
     *
     * This used to run the other way round, with the local ONNX model accepted
     * at 0.45 confidence and the server tried last, which meant the good engine
     * was almost never the one that answered.
     */
    private suspend fun resolveOcrText(bitmap: Bitmap, bytes: ByteArray, hash: String): OcrOutcome {
        val server = runCatching { uploadForOcr(bytes, hash) }
            .onFailure { Log.w("LessonStudio", "Server OCR unavailable, falling back on device", it) }
            .getOrNull()

        if (server != null && server.extractedText.isNotBlank()) {
            return OcrOutcome(
                text           = server.extractedText,
                confidence     = server.confidence,
                minConfidence  = server.minimumConfidence,
                engine         = server.ocrEngine,
                sourceHash     = server.sourceHash,
                requiresReview = server.requiresReview,
                reviewReason   = server.reviewReason,
            )
        }

        // Offline. Anything read on the device is weak enough at Tamil that the
        // teacher must check it, whatever confidence the model reports.
        val offlineReason =
            "This page was read on the device because the server could not be " +
            "reached. On-device reading is much less accurate for Tamil — " +
            "please check every line."

        if (ocrModel.isAvailable) {
            val onnx = runCatching { ocrModel.run(bitmap) }
                .onFailure { Log.e("LessonStudio", "ONNX OCR failed", it) }
                .getOrNull()

            if (onnx != null && onnx.text.isNotBlank() && onnx.confidence >= 0.45f) {
                return OcrOutcome(
                    text = onnx.text, confidence = onnx.confidence,
                    minConfidence = onnx.confidence, engine = "onnx",
                    requiresReview = true, reviewReason = offlineReason,
                )
            }
        }

        // ML Kit has no Tamil model; it is only useful for Latin text.
        val mlKit = runCatching { mlKitOcr.run(bitmap) }
            .onFailure { Log.e("LessonStudio", "ML Kit OCR failed", it) }
            .getOrNull()

        if (mlKit != null && mlKit.text.isNotBlank() && looksMostlyLatin(mlKit.text)) {
            return OcrOutcome(
                text = mlKit.text, engine = "mlkit",
                requiresReview = true, reviewReason = offlineReason,
            )
        }

        return OcrOutcome.Empty
    }

    private fun looksMostlyLatin(text: String): Boolean {
        val letters = text.filter { it.isLetter() }
        if (letters.isEmpty()) return false
        val latin = letters.count { it.code in 0x0041..0x007A }
        return (latin.toFloat() / letters.length) >= 0.7f
    }


    // ── PDF: PdfRenderer → bitmap per page → OCR ────────────────────────────
    private suspend fun extractFromPdf(bytes: ByteArray, context: Context): OcrOutcome {
        val tmpFile = File.createTempFile("pdf_", ".pdf", context.cacheDir)
        tmpFile.writeBytes(bytes)

        val fd       = ParcelFileDescriptor.open(tmpFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(fd)
        val texts    = mutableListOf<String>()
        val pages    = mutableListOf<OcrOutcome>()

        for (i in 0 until renderer.pageCount) {
            val page = renderer.openPage(i)
            val bmp  = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()

            val pageBytes = bitmapToJpeg(bmp)
            val outcome   = resolveOcrText(bmp, pageBytes, sha256(bytes) + "_p$i")
            if (outcome.text.isNotBlank()) texts.add(outcome.text)
            pages.add(outcome)
        }

        renderer.close()
        fd.close()
        tmpFile.delete()

        // Every page here was rendered and read, so the worst one sets the bar.
        val worst = pages.filter { it.text.isNotBlank() }.minByOrNull { it.confidence ?: 1f }
        return OcrOutcome(
            text           = texts.joinToString("\n\n"),
            confidence     = worst?.confidence,
            minConfidence  = worst?.minConfidence,
            engine         = worst?.engine,
            sourceHash     = null,
            requiresReview = pages.any { it.text.isNotBlank() && it.requiresReview },
            reviewReason   = worst?.reviewReason,
        )
    }

    // ── DOCX / unknown: send single file to server extract-multi ────────────
    private suspend fun uploadForExtraction(bytes: ByteArray, filename: String, mimeType: String): String {
        val part = MultipartBody.Part.createFormData(
            "files", filename, bytes.toRequestBody(mimeType.toMediaType())
        )
        return api.extractMulti(listOf(part)).combinedText
    }

    // ── Image: server OCR (the primary reader) ───────────────────────────────
    private suspend fun uploadForOcr(bytes: ByteArray, hash: String): OcrResponse =
        api.ocrUpload(
            MultipartBody.Part.createFormData(
                "image", "upload.jpg", bytes.toRequestBody("image/jpeg".toMediaType())
            ),
            hash.toRequestBody("text/plain".toMediaType())
        )

    // ── Lesson generation ────────────────────────────────────────────────────
    /** Teacher has read (and possibly corrected) the extracted text. */
    fun confirmReview(text: String, quality: OcrOutcome) {
        generateFromText(text, quality.sourceHash ?: "", quality, reviewed = true)
    }

    fun generateFromText(
        text: String,
        hash: String = "",
        quality: OcrOutcome? = null,
        reviewed: Boolean = false,
    ) {
        viewModelScope.launch {
            _state.value = StudioState.Generating
            try {
                val genResp     = api.generateLesson(GenerateRequest(
                    ocrText    = text,
                    difficulty = selectedDifficulty,
                    language   = selectedLanguage,
                    sourceHash = hash,
                    averageConfidence = quality?.confidence,
                    minimumConfidence = quality?.minConfidence,
                    ocrEngine  = quality?.engine,
                    textReviewed = reviewed
                ))
                val contentJson = moshi.adapter(LessonContent::class.java).toJson(genResp.lesson)
                db.lessonDao().upsert(LessonEntity(
                    id          = genResp.lessonId,
                    teacherId   = prefs.teacherId,
                    sourceHash  = hash,
                    title       = genResp.lesson.title.ifBlank { "ஆசிரியர் பாடம்" },
                    lessonType  = "story",
                    difficulty  = selectedDifficulty,
                    language    = selectedLanguage,
                    contentJson = contentJson,
                    isPublished = false,
                    cacheHit    = genResp.cacheHit,
                    syncStatus  = "pending"
                ))
                _state.value = StudioState.Done(genResp.lessonId, cacheHit = genResp.cacheHit)
            } catch (e: retrofit2.HttpException) {
                // 428: the server wants the text checked before it becomes a
                // lesson. Send the teacher to review rather than a dead end.
                if (e.code() == 428 && !reviewed) {
                    _state.value = StudioState.ReviewText(
                        text,
                        (quality ?: OcrOutcome(text)).copy(
                            requiresReview = true,
                            reviewReason = serverDetail(e)
                                ?: "Please check the text before creating the lesson.",
                        )
                    )
                } else {
                    _state.value = StudioState.Error(serverDetail(e) ?: e.message())
                }
            } catch (e: Exception) {
                _state.value = StudioState.Error(e.message ?: "Unknown error")
            }
        }
    }

    /** The API reports problems as {"detail": "…"}; anything else is not ours. */
    private fun serverDetail(e: retrofit2.HttpException): String? = runCatching {
        val body = e.response()?.errorBody()?.string().orEmpty()
        org.json.JSONObject(body).optString("detail").takeIf { it.isNotBlank() }
    }.getOrNull()

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun bitmapToJpeg(bmp: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return out.toByteArray()
    }

    private fun getFileName(context: Context, uri: Uri): String =
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            if (col >= 0) cursor.getString(col) else null
        } ?: uri.lastPathSegment ?: "unknown"

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun reset() { _state.value = StudioState.Idle }
}

// ── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun LessonStudioScreen(
    navController: NavHostController,
    vm:      LessonStudioViewModel  = hiltViewModel(),
    langVm:  AppLanguageViewModel   = hiltViewModel()
) {
    val language by langVm.language.collectAsState()
    val state    by vm.state.collectAsState()
    val context  = LocalContext.current

    // Multi-file picker (images + PDF + DOCX + TXT)
    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) vm.processFiles(uris, context)
    }

    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) cameraUri?.let { vm.processFiles(listOf(it), context) }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val uri = File.createTempFile("ocr_capture_", ".jpg", context.cacheDir).let {
                FileProvider.getUriForFile(context, "${context.packageName}.provider", it)
            }
            cameraUri = uri
            cameraLauncher.launch(uri)
        }
    }

    fun launchCamera() {
        val tmpFile = File.createTempFile("ocr_capture_", ".jpg", context.cacheDir)
        val uri     = FileProvider.getUriForFile(context, "${context.packageName}.provider", tmpFile)
        cameraUri   = uri
        cameraLauncher.launch(uri)
    }

    LaunchedEffect(state) {
        if (state is LessonStudioViewModel.StudioState.Done) {
            val lessonId = (state as LessonStudioViewModel.StudioState.Done).lessonId
            navController.navigate(Screen.LessonStudioReview.route(lessonId))
            vm.reset()
        }
    }

    Scaffold(
        containerColor = BgDark,
        bottomBar = {
            TeacherBottomNavBar(navController = navController, currentRoute = Screen.LessonLibrary.route)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BgDark)
                .padding(padding)
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgCard)
                    .border(1.dp, Border)
                    .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextSecondary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text       = EzhilStrings.get(StringKey.LESSON_STUDIO, language),
                        fontFamily = BaloTamizha2,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 17.sp,
                        color      = TextPrimary
                    )
                    Text(
                        text       = "LESSON STUDIO",
                        fontFamily = DMSans,
                        fontSize   = MinType.caption,
                        letterSpacing = Tracking.caption,
                        color      = TextMuted
                    )
                }
                LanguageToggle(current = language, onToggle = { langVm.toggle() })
            }

            // Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    // The studio holds the longest text on any teacher screen —
                    // extracted passages and the review box — so it benefits
                    // most from a wider gutter on a tablet.
                    .padding(horizontal = screenGutter(), vertical = Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (state) {
                    LessonStudioViewModel.StudioState.Idle -> {
                        IdleContent(
                            language  = language,
                            vm        = vm,
                            onCamera  = {
                                val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.CAMERA
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (granted) launchCamera()
                                else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            },
                            onPickFiles = {
                                fileLauncher.launch(arrayOf(
                                    "image/*",
                                    "application/pdf",
                                    "text/plain",
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                                ))
                            }
                        )
                    }

                    is LessonStudioViewModel.StudioState.FileQueue -> {
                        val items = (state as LessonStudioViewModel.StudioState.FileQueue).items
                        FileQueueContent(language = language, items = items)
                    }

                    LessonStudioViewModel.StudioState.Generating -> {
                        Spacer(Modifier.height(Spacing.xxl))
                        EzhilanWidget(state = EzhilanState.PROCESSING, size = 80.dp)
                        Spacer(Modifier.height(Spacing.md))
                        Text(
                            text       = EzhilStrings.get(StringKey.PROCESSING, language),
                            fontFamily = NotoSansTamil,
                            fontSize   = 18.sp,
                            color      = TextMuted
                        )
                    }

                    is LessonStudioViewModel.StudioState.ReviewText -> {
                        ReviewTextContent(
                            state    = state as LessonStudioViewModel.StudioState.ReviewText,
                            language = language,
                            vm       = vm,
                        )
                    }

                    is LessonStudioViewModel.StudioState.ManualEntry -> {
                        ManualEntryContent(language = language, vm = vm)
                    }

                    is LessonStudioViewModel.StudioState.Error -> {
                        Spacer(Modifier.height(Spacing.xl))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ErrorBg, RoundedCornerShape(14.dp))
                                .border(1.dp, Error.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                                .padding(Spacing.md)
                        ) {
                            Text(
                                text       = (state as LessonStudioViewModel.StudioState.Error).message,
                                color      = Error,
                                fontFamily = DMSans,
                                fontSize   = 14.sp
                            )
                        }
                        Spacer(Modifier.height(Spacing.md))
                        EzhilButton(
                            label           = EzhilStrings.get(StringKey.RETRY, language),
                            onClick         = { vm.reset() },
                            modifier        = Modifier.fillMaxWidth(),
                            backgroundColor = Amber,
                            textColor       = TextOnAmber
                        )
                    }

                    else -> {}
                }
            }
        }
    }
}

// ── Idle state — file picker + camera + settings ─────────────────────────────

@Composable
private fun IdleContent(
    language:    AppLanguage,
    vm:          LessonStudioViewModel,
    onCamera:    () -> Unit,
    onPickFiles: () -> Unit
) {
    // Drop-zone
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .background(BgCard, RoundedCornerShape(20.dp))
            .border(2.dp, Border, RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Image, contentDescription = null, tint = TextMuted, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text       = if (language == AppLanguage.TAMIL)
                    "படங்கள், PDF, Word, அல்லது உரை கோப்புகளை சேர்க்கவும்"
                else
                    "Add images, PDFs, Word docs, or text files",
                fontFamily = NotoSansTamil,
                color      = TextMuted,
                fontSize   = 13.sp
            )
        }
    }

    Spacer(Modifier.height(Spacing.lg))
    SectionDivider(label = if (language == AppLanguage.TAMIL) "சிரம நிலை" else "Difficulty")

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        listOf(
            1 to EzhilStrings.get(StringKey.EASY,   language),
            2 to EzhilStrings.get(StringKey.MEDIUM, language),
            3 to EzhilStrings.get(StringKey.HARD,   language)
        ).forEach { (level, label) ->
            FilterChip(
                selected = vm.selectedDifficulty == level,
                onClick  = { vm.selectedDifficulty = level },
                label    = { Text(label, fontFamily = DMSans) },
                modifier = Modifier.weight(1f),
                colors   = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AmberDim,
                    selectedLabelColor     = Amber,
                    containerColor         = BgCard,
                    labelColor             = TextMuted
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled             = true,
                    selected            = vm.selectedDifficulty == level,
                    selectedBorderColor = Amber,
                    borderColor         = Border
                )
            )
        }
    }

    Spacer(Modifier.height(Spacing.md))
    SectionDivider(label = if (language == AppLanguage.TAMIL) "மொழி" else "Language")

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        listOf(
            "tamil"   to "தமிழ்",
            "english" to "English",
            "both"    to if (language == AppLanguage.TAMIL) "இரண்டும்" else "Both"
        ).forEach { (code, label) ->
            FilterChip(
                selected = vm.selectedLanguage == code,
                onClick  = { vm.selectedLanguage = code },
                label    = { Text(label, fontFamily = DMSans) },
                modifier = Modifier.weight(1f),
                colors   = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = AmberDim,
                    selectedLabelColor     = Amber,
                    containerColor         = BgCard,
                    labelColor             = TextMuted
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled             = true,
                    selected            = vm.selectedLanguage == code,
                    selectedBorderColor = Amber,
                    borderColor         = Border
                )
            )
        }
    }

    Spacer(Modifier.height(Spacing.lg))

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        EzhilButton(
            label           = EzhilStrings.get(StringKey.TAKE_PHOTO, language),
            onClick         = onCamera,
            modifier        = Modifier.weight(1f),
            backgroundColor = Amber,
            textColor       = TextOnAmber
        )
        EzhilButton(
            label    = if (language == AppLanguage.TAMIL) "கோப்புகள்" else "Add Files",
            onClick  = onPickFiles,
            modifier = Modifier.weight(1f),
            backgroundColor = Purple
        )
    }
}

// ── File queue state — per-file status chips ─────────────────────────────────

@Composable
private fun FileQueueContent(language: AppLanguage, items: List<FileQueueItem>) {
    val allDone = items.all { it.status == FileItemStatus.DONE || it.status == FileItemStatus.ERROR }

    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text       = if (language == AppLanguage.TAMIL) "கோப்புகளை பிரித்தெடுக்கிறது…" else "Extracting files…",
            fontFamily = NotoSansTamil,
            fontSize   = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color      = TextPrimary
        )
        Spacer(Modifier.height(Spacing.md))

        items.forEach { item ->
            FileQueueRow(item = item)
            Spacer(Modifier.height(Spacing.sm))
        }

        if (allDone) {
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text       = if (language == AppLanguage.TAMIL)
                    "உருவாக்குகிறது…"
                else "Generating lesson from extracted text…",
                fontFamily = NotoSansTamil,
                fontSize   = 13.sp,
                color      = TextMuted
            )
        }
    }
}

@Composable
private fun FileQueueRow(item: FileQueueItem) {
    val (statusColor, statusIcon, statusText) = when (item.status) {
        FileItemStatus.PENDING    -> Triple(TextMuted,   "schedule",     item.name)
        FileItemStatus.EXTRACTING -> Triple(Amber,       "sync",         item.progress.ifBlank { "…" })
        FileItemStatus.DONE       -> Triple(Success,     "check_circle", item.progress)
        FileItemStatus.ERROR      -> Triple(Error,       "error",        item.error ?: "Error")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgCard, RoundedCornerShape(12.dp))
            .border(1.dp, Border, RoundedCornerShape(12.dp))
            .padding(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text       = statusIcon,
            fontFamily = DMSans,
            color      = statusColor,
            fontSize   = 18.sp,
            modifier   = Modifier.width(24.dp)
        )
        Spacer(Modifier.width(Spacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text       = item.name,
                color      = TextPrimary,
                fontFamily = DMSans,
                fontSize   = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text       = statusText,
                color      = statusColor,
                fontFamily = DMSans,
                fontSize   = MinType.caption
            )
        }
    }
}

// ── Manual entry state ───────────────────────────────────────────────────────

@Composable
private fun ManualEntryContent(language: AppLanguage, vm: LessonStudioViewModel) {
    var manualText by remember { mutableStateOf("") }

    Spacer(Modifier.height(Spacing.sm))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(AmberDim, RoundedCornerShape(14.dp))
            .border(1.dp, Amber.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(Spacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Amber, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text       = if (language == AppLanguage.TAMIL)
                    "படத்திலிருந்து உரை கண்டுபிடிக்க முடியவில்லை. கீழே தட்டச்சு செய்யுங்கள்."
                else
                    "Couldn't read text from the files. Type the passage below.",
                fontFamily = NotoSansTamil,
                fontSize   = 13.sp,
                color      = Amber
            )
        }
    }

    Spacer(Modifier.height(Spacing.md))
    SectionDivider(label = if (language == AppLanguage.TAMIL) "பாடப்பகுதி" else "Lesson Passage")

    OutlinedTextField(
        value         = manualText,
        onValueChange = { manualText = it },
        modifier      = Modifier.fillMaxWidth().heightIn(min = 140.dp),
        placeholder   = {
            Text(
                text  = if (language == AppLanguage.TAMIL)
                    "பாடப்பகுதியை இங்கே தட்டச்சு செய்யுங்கள்..."
                else "Type the lesson passage here...",
                color = TextMuted.copy(0.5f)
            )
        },
        maxLines = 10,
        shape    = RoundedCornerShape(14.dp),
        colors   = OutlinedTextFieldDefaults.colors(
            focusedBorderColor      = Amber,
            unfocusedBorderColor    = Border,
            focusedTextColor        = TextPrimary,
            unfocusedTextColor      = TextPrimary,
            focusedContainerColor   = BgCardElevated,
            unfocusedContainerColor = BgCardElevated,
            cursorColor             = Amber
        )
    )

    Spacer(Modifier.height(Spacing.md))

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        EzhilOutlinedButton(
            label    = if (language == AppLanguage.TAMIL) "திரும்பு" else "Back",
            onClick  = { vm.reset() },
            modifier = Modifier.weight(1f)
        )
        EzhilButton(
            label           = if (language == AppLanguage.TAMIL) "பாடம் உருவாக்கு" else "Generate",
            // Typed by hand, so it is reviewed by definition — the quality gate
            // must not block text a teacher wrote themselves.
            onClick         = {
                if (manualText.isNotBlank()) vm.generateFromText(manualText, reviewed = true)
            },
            modifier        = Modifier.weight(1f),
            backgroundColor = Amber,
            textColor       = TextOnAmber
        )
    }
}

/**
 * Check the extracted text before it becomes a lesson.
 *
 * Shown when the reader was not confident, or when the page had to be read on
 * the device because the server was unreachable. The words here become a
 * passage a dyslexic child will practise, so a wrong one costs more than the
 * minute it takes to check.
 */
@Composable
private fun ReviewTextContent(
    state: LessonStudioViewModel.StudioState.ReviewText,
    language: AppLanguage,
    vm: LessonStudioViewModel,
) {
    var text by remember(state.text) { mutableStateOf(state.text) }
    val tamil = language == AppLanguage.TAMIL

    Spacer(Modifier.height(Spacing.sm))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(AmberDim, RoundedCornerShape(14.dp))
            .border(1.dp, Amber.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(Spacing.md)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Default.CameraAlt, contentDescription = null,
                tint = Amber, modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(Spacing.sm))
            Column {
                Text(
                    text = if (tamil) "உரையைச் சரிபார்க்கவும்" else "Check the text",
                    fontFamily = NotoSansTamil,
                    fontSize   = TypeScale.bodySm,
                    fontWeight = FontWeight.SemiBold,
                    color      = Amber
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = state.quality.reviewReason ?: if (tamil)
                        "சில சொற்கள் தவறாகப் படிக்கப்பட்டிருக்கலாம். சரிபார்க்கவும்."
                    else
                        "Some words may have been read incorrectly. Fix anything that looks wrong.",
                    fontFamily = NotoSansTamil,
                    fontSize   = MinType.caption,
                    color      = Amber.copy(alpha = 0.85f)
                )
                state.quality.confidence?.let { c ->
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = (if (tamil) "படித்த துல்லியம் " else "Reading accuracy ") +
                            "${(c * 100).toInt()}%" +
                            (state.quality.engine?.let { " · $it" } ?: ""),
                        fontSize = MinType.caption,
                        color    = Amber.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(Spacing.md))
    SectionDivider(label = if (tamil) "பாடப்பகுதி" else "Lesson Passage")

    OutlinedTextField(
        value         = text,
        onValueChange = { text = it },
        modifier      = Modifier.fillMaxWidth().heightIn(min = 180.dp),
        maxLines      = 14,
        shape         = RoundedCornerShape(14.dp),
        placeholder   = {
            Text(
                text  = if (tamil) "பாடப்பகுதியை இங்கே தட்டச்சு செய்யுங்கள்..."
                        else "Type the lesson passage here...",
                color = TextMuted.copy(0.5f)
            )
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor      = Amber,
            unfocusedBorderColor    = Border,
            focusedTextColor        = TextPrimary,
            unfocusedTextColor      = TextPrimary,
            focusedContainerColor   = BgCardElevated,
            unfocusedContainerColor = BgCardElevated,
            cursorColor             = Amber
        )
    )

    // A barely-readable photo lands here with almost nothing in the box.
    // Without this the button is simply dead and the reason invisible.
    if (text.trim().length < 10) {
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = if (text.isBlank()) {
                if (tamil) "இந்தப் படத்திலிருந்து எதுவும் படிக்க முடியவில்லை. மேலே தட்டச்சு செய்யுங்கள்."
                else "Nothing could be read from this photo. Type the passage above, or retake it."
            } else {
                if (tamil) "சில எழுத்துகள் மட்டுமே கிடைத்தன. மீதியை மேலே தட்டச்சு செய்யுங்கள்."
                else "Only a few characters came through. Type the rest above, or retake the photo."
            },
            fontFamily = NotoSansTamil,
            fontSize   = MinType.caption,
            color      = TextMuted
        )
    }

    Spacer(Modifier.height(Spacing.md))

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        EzhilOutlinedButton(
            label    = if (tamil) "திரும்பு" else "Back",
            onClick  = { vm.reset() },
            modifier = Modifier.weight(1f)
        )
        EzhilButton(
            label           = if (tamil) "சரி — பாடம் உருவாக்கு" else "Correct — create lesson",
            onClick         = {
                if (text.trim().length >= 10) vm.confirmReview(text, state.quality)
            },
            modifier        = Modifier.weight(1.4f),
            backgroundColor = Amber,
            textColor       = TextOnAmber
        )
    }
}

// ── Shared composable ────────────────────────────────────────────────────────

@Composable
private fun SectionDivider(label: String) {
    Row(
        modifier          = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = Border)
        Text(
            text       = label,
            fontFamily = DMSans,
            fontWeight = FontWeight.SemiBold,
            fontSize   = MinType.caption,
            // No letter-spacing: this label is often Tamil ("பாடப்பகுதி"), and
            // tracking breaks the script's conjuncts.
            color      = TextMuted,
            modifier   = Modifier.padding(horizontal = Spacing.sm)
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = Border)
    }
    Spacer(Modifier.height(Spacing.xs))
}
