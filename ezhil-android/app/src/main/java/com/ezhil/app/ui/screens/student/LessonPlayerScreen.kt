package com.ezhil.app.ui.screens.student

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.ezhil.app.data.local.EzhilDatabase
import com.ezhil.app.data.local.SecurePrefs
import com.ezhil.app.data.local.entity.LessonProgressEntity
import com.ezhil.app.data.remote.dto.LessonContent
import com.ezhil.app.ui.components.*
import com.ezhil.app.ui.navigation.Screen
import com.ezhil.app.ui.strings.AppLanguage
import com.ezhil.app.ui.strings.EzhilStrings
import com.ezhil.app.ui.strings.StringKey
import com.ezhil.app.ui.theme.*
import com.ezhil.app.ui.viewmodel.AppLanguageViewModel
import com.squareup.moshi.Moshi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class LessonPlayerViewModel @Inject constructor(
    private val db: EzhilDatabase,
    private val prefs: SecurePrefs,
    private val moshi: Moshi
) : ViewModel() {

    private val _lesson = MutableStateFlow<com.ezhil.app.data.local.entity.LessonEntity?>(null)
    val lesson: StateFlow<com.ezhil.app.data.local.entity.LessonEntity?> = _lesson

    private val _content = MutableStateFlow<LessonContent?>(null)
    val content: StateFlow<LessonContent?> = _content

    private val _completed = MutableStateFlow(false)
    val completed: StateFlow<Boolean> = _completed

    private val _startMs = MutableStateFlow(0L)

    fun load(lessonId: String) {
        viewModelScope.launch {
            val l = db.lessonDao().getById(lessonId)
            _lesson.value = l
            l?.let {
                try {
                    _content.value = moshi.adapter(LessonContent::class.java).fromJson(it.contentJson)
                } catch (e: Exception) {
                    _content.value = LessonContent(
                        title = it.title,
                        passage = com.ezhil.app.data.remote.dto.LessonPassage(lines = it.contentJson.split("\n"))
                    )
                }
            }
            _startMs.value = System.currentTimeMillis()
        }
    }

    fun markComplete() {
        val studentId = prefs.activeStudentId ?: return
        val lessonId  = _lesson.value?.id ?: return
        val durationMs = (System.currentTimeMillis() - _startMs.value).toInt()
        viewModelScope.launch {
            db.lessonProgressDao().insert(LessonProgressEntity(
                id          = UUID.randomUUID().toString(),
                studentId   = studentId,
                lessonId    = lessonId,
                completedAt = Instant.now().toString(),
                durationMs  = durationMs,
                syncStatus  = "pending"
            ))
            _completed.value = true
        }
    }
}

@Composable
fun LessonPlayerScreen(
    navController: NavHostController,
    lessonId: String,
    vm: LessonPlayerViewModel = hiltViewModel(),
    langVm: AppLanguageViewModel = hiltViewModel()
) {
    val language by langVm.language.collectAsState()
    val lesson   by vm.lesson.collectAsState()
    val content  by vm.content.collectAsState()
    val completed by vm.completed.collectAsState()
    val context  = LocalContext.current

    LaunchedEffect(lessonId) { vm.load(lessonId) }

    var showExitDialog by remember { mutableStateOf(false) }
    val tts = rememberTamilTts()

    // Exit confirmation dialog
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            containerColor = BgCard,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("⚠️", fontSize = 32.sp)
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        if (language == AppLanguage.TAMIL) "பாடத்தை விட்டு வெளியேரணுமா?"
                        else "Exit Lesson?",
                        fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold,
                        fontSize = 18.sp, color = TextPrimary
                    )
                }
            },
            text = {
                Text(
                    if (language == AppLanguage.TAMIL)
                        "முன்னேற்றம் சேமிக்கப்படவில்லை."
                    else "Your progress has not been saved.",
                    fontFamily = DMSans, fontSize = 13.sp, color = TextSecondary
                )
            },
            confirmButton = {
                EzhilButton(
                    label = if (language == AppLanguage.TAMIL) "📖 தொடர்" else "📖 Keep Reading",
                    onClick = { showExitDialog = false }
                )
            },
            dismissButton = {
                TextButton(onClick = { navController.popBackStack() }) {
                    Text(
                        if (language == AppLanguage.TAMIL) "✗ வெளியேர்" else "✗ Exit",
                        color = RiskHigh, fontFamily = DMSans, fontWeight = FontWeight.SemiBold
                    )
                }
            }
        )
    }

    if (completed) {
        Box(
            modifier = Modifier.fillMaxSize().background(BgDark),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                EzhilanWidget(state = EzhilanState.CELEBRATING, size = 100.dp)
                Spacer(Modifier.height(Spacing.md))
                Text("🏆", fontSize = 48.sp)
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    EzhilStrings.get(StringKey.LESSON_COMPLETE, language),
                    fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold,
                    fontSize = 24.sp, color = Success
                )
                Spacer(Modifier.height(Spacing.lg))
                EzhilButton(
                    label = "🏆 ${EzhilStrings.get(StringKey.DONE, language)}",
                    onClick = {
                        navController.navigate(Screen.Achievement.route("lesson_complete", 3)) {
                            popUpTo(Screen.LessonPlayer.route) { inclusive = true }
                        }
                    },
                    modifier = Modifier.padding(horizontal = screenGutter())
                )
            }
        }
        return
    }

    lesson?.let { l ->
        Column(modifier = Modifier.fillMaxSize().background(BgDark)) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgCard)
                    .border(width = 1.dp, color = Border)
                    .padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showExitDialog = true }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextSecondary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(l.title, fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold,
                        fontSize = 18.sp, color = TextPrimary)
                }
                if (tts.available) {
                    IconButton(onClick = { tts.speak(l.title, "title") }) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = EzhilStrings.get(StringKey.HEAR_PRONUNCIATION, language),
                            tint = Cyan)
                    }
                }
                LanguageToggle(current = language, onToggle = { langVm.toggle() })
            }

            // Reader area — cream (dyslexia spec)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(Spacing.md)
                    .background(BgReader, RoundedCornerShape(16.dp))
                    .padding(Spacing.lg)
                    .verticalScroll(rememberScrollState())
            ) {
                content?.passage?.lines?.forEach { line ->
                    Text(
                        text = line,
                        style = TextStyle(
                            fontFamily    = ReaderConstraints.FontFamily,
                            fontSize      = ReaderConstraints.FontSize,
                            lineHeight    = ReaderConstraints.LineHeight,
                            letterSpacing = ReaderConstraints.LetterSpacing,
                            color         = ReaderConstraints.TextColor
                        )
                    )
                    Spacer(Modifier.height(Spacing.sm))
                }
            }

            // Footer bar — Words | Quiz | Done
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgCard)
                    .border(width = 1.dp, color = Border)
                    .padding(horizontal = screenGutter(), vertical = Spacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    EzhilOutlinedButton(
                        label = if (language == AppLanguage.TAMIL) "📖 சொற்கள்" else "📖 Words",
                        onClick = { navController.navigate(Screen.VocabularyDetail.route(lessonId)) }
                    )
                    EzhilOutlinedButton(
                        label = if (language == AppLanguage.TAMIL) "❓ வினா" else "❓ Quiz",
                        onClick = { navController.navigate(Screen.ComprehensionQuiz.route(lessonId)) }
                    )
                }
                EzhilButton(
                    label = "✓ ${EzhilStrings.get(StringKey.DONE, language)}",
                    onClick = { vm.markComplete() },
                    backgroundColor = Success
                )
            }
        }
    } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Cyan)
    }
}
