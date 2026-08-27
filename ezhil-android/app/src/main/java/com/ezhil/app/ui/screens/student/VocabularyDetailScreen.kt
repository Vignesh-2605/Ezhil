package com.ezhil.app.ui.screens.student

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.ezhil.app.data.local.EzhilDatabase
import com.ezhil.app.data.remote.dto.LessonContent
import com.ezhil.app.data.remote.dto.LessonVocabEntry
import com.ezhil.app.ui.components.*
import com.ezhil.app.ui.strings.AppLanguage
import com.ezhil.app.ui.theme.*
import com.ezhil.app.ui.viewmodel.AppLanguageViewModel
import com.squareup.moshi.Moshi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VocabularyDetailViewModel @Inject constructor(
    private val db: EzhilDatabase,
    private val moshi: Moshi
) : ViewModel() {

    data class UiState(
        val lessonTitle: String = "",
        val vocabList: List<LessonVocabEntry> = emptyList(),
        val loading: Boolean = true
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    fun load(lessonId: String) {
        viewModelScope.launch {
            val lesson = db.lessonDao().getById(lessonId)
            if (lesson == null) {
                _uiState.value = UiState(loading = false)
                return@launch
            }
            val content = try {
                moshi.adapter(LessonContent::class.java).fromJson(lesson.contentJson)
            } catch (e: Exception) { null }
            _uiState.value = UiState(
                lessonTitle = lesson.title,
                vocabList   = content?.vocabulary ?: emptyList(),
                loading     = false
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocabularyDetailScreen(
    navController: NavHostController,
    lessonId: String,
    vm: VocabularyDetailViewModel = hiltViewModel(),
    langVm: AppLanguageViewModel = hiltViewModel()
) {
    val language by langVm.language.collectAsState()
    val uiState  by vm.uiState.collectAsState()
    val context  = LocalContext.current

    LaunchedEffect(lessonId) { vm.load(lessonId) }

    val tts = rememberTamilTts()

    var selectedWord by remember { mutableStateOf<LessonVocabEntry?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(modifier = Modifier.fillMaxSize().background(BgDark)) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgCard)
                .border(1.dp, Border)
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextSecondary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (language == AppLanguage.TAMIL) "சொற்கள் விளக்கம்" else "Vocabulary",
                    fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold,
                    fontSize = 18.sp, color = TextPrimary
                )
                if (uiState.lessonTitle.isNotEmpty())
                    Text(uiState.lessonTitle, fontFamily = DMSans, fontSize = 12.sp, color = TextMuted)
            }
            LanguageToggle(current = language, onToggle = { langVm.toggle() })
        }

        when {
            uiState.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Cyan)
            }
            uiState.vocabList.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    Text("📚", fontSize = 48.sp)
                    Text(
                        if (language == AppLanguage.TAMIL) "சொற்கள் இல்லை" else "No Vocabulary",
                        fontFamily = BaloTamizha2, fontSize = 18.sp, color = TextPrimary
                    )
                    Text("No vocabulary entries for this lesson",
                        fontFamily = DMSans, fontSize = 13.sp, color = TextMuted)
                }
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).background(Cyan, CircleShape))
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            if (language == AppLanguage.TAMIL)
                                "${uiState.vocabList.size} சொற்கள் கற்கலாம்"
                            else "${uiState.vocabList.size} Words to Learn",
                            fontFamily = DMSans, fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp, color = Cyan
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            if (language == AppLanguage.TAMIL) "• தொட்டு விரிவாக படிக்க"
                            else "• Tap a word for details",
                            fontFamily = DMSans, fontSize = 12.sp, color = TextMuted
                        )
                    }
                }
                items(uiState.vocabList) { entry ->
                    VocabCard(
                        entry    = entry,
                        language = language,
                        onSpeak  = if (tts.available) ({ tts.speak(entry.word, "vocab_${entry.word}") }) else null,
                        onClick  = { selectedWord = entry }
                    )
                }
                item { Spacer(Modifier.height(Spacing.xl)) }
            }
        }
    }

    // Word-detail bottom sheet
    selectedWord?.let { entry ->
        ModalBottomSheet(
            onDismissRequest = { selectedWord = null },
            sheetState       = sheetState,
            containerColor   = BgCard,
            tonalElevation   = 0.dp
        ) {
            WordDetailSheet(
                entry    = entry,
                language = language,
                vocabList = uiState.vocabList,
                onSpeak  = if (tts.available) ({ tts.speak(entry.word, "sheet_${entry.word}") }) else null,
                onDismiss = { selectedWord = null }
            )
        }
    }
}

@Composable
private fun WordDetailSheet(
    entry: LessonVocabEntry,
    language: AppLanguage,
    vocabList: List<LessonVocabEntry>,
    onSpeak: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    // Build a mini-MCQ: correct meaning + 3 distractors from other words
    val distractors = remember(entry, vocabList) {
        vocabList.filter { it.word != entry.word }
            .shuffled()
            .take(3)
            .map { if (language == AppLanguage.TAMIL) it.meaningTa else it.meaningEn }
    }
    val correctMeaning = if (language == AppLanguage.TAMIL) entry.meaningTa else entry.meaningEn
    val options = remember(distractors, correctMeaning) {
        (distractors + correctMeaning).shuffled()
    }
    val correctIdx = remember(options, correctMeaning) { options.indexOf(correctMeaning) }

    var selectedOption by remember { mutableStateOf(-1) }
    val isFeedback = selectedOption >= 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
            .padding(bottom = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        // Word header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                entry.word,
                fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold,
                fontSize = 32.sp, color = Cyan
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onSpeak != null) {
                    IconButton(onClick = onSpeak) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Pronounce", tint = Cyan)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("✕", color = TextMuted, fontSize = 18.sp)
                }
            }
        }

        // Syllable pills
        if (entry.syllables.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text("SYLLABLES",
                    fontFamily = DMSans, fontSize = 12.sp, color = TextMuted,
                    fontWeight = FontWeight.SemiBold)
                entry.syllables.forEachIndexed { i, syl ->
                    if (i > 0) Text("·", color = TextMuted, fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 2.dp))
                    Box(
                        modifier = Modifier
                            .background(AmberDim, RoundedCornerShape(8.dp))
                            .padding(horizontal = Spacing.sm, vertical = 2.dp)
                    ) {
                        Text(syl, fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold,
                            fontSize = 16.sp, color = Amber)
                    }
                }
            }
        }

        HorizontalDivider(color = Border, thickness = 0.5.dp)

        // Meanings side-by-side
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Column(modifier = Modifier.weight(1f)) {
                Text("தமிழ்", fontFamily = DMSans, fontSize = 12.sp, color = TextMuted,
                    fontWeight = FontWeight.SemiBold)
                Text(entry.meaningTa, fontFamily = BaloTamizha2, fontSize = 16.sp, color = TextPrimary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("ENGLISH", fontFamily = DMSans, fontSize = 12.sp, color = TextMuted,
                    fontWeight = FontWeight.SemiBold)
                Text(entry.meaningEn, fontFamily = DMSans, fontSize = 16.sp, color = TextPrimary)
            }
        }

        // Mini-MCQ "Test Yourself" — only shown when we have enough distractors
        if (distractors.size >= 3) {
            HorizontalDivider(color = Border, thickness = 0.5.dp)

            Text(
                if (language == AppLanguage.TAMIL) "🧠 சோதனை — சரியான பொருளை தேர்"
                else "🧠 Test Yourself — Pick the correct meaning",
                fontFamily = DMSans, fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp, color = Cyan
            )

            options.forEachIndexed { idx, option ->
                val isSelected = selectedOption == idx
                val isCorrect  = idx == correctIdx
                val bgColor = when {
                    !isFeedback && isSelected             -> Cyan.copy(alpha = 0.15f)
                    isFeedback && isCorrect               -> Success.copy(alpha = 0.15f)
                    isFeedback && isSelected && !isCorrect -> RiskHigh.copy(alpha = 0.15f)
                    else                                  -> BgCardElevated
                }
                val borderColor = when {
                    !isFeedback && isSelected             -> Cyan
                    isFeedback && isCorrect               -> Success
                    isFeedback && isSelected && !isCorrect -> RiskHigh
                    else                                  -> Border
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bgColor, RoundedCornerShape(10.dp))
                        .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                        .clickable(enabled = !isFeedback) { selectedOption = idx }
                        .padding(horizontal = screenGutter(), vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(
                        when {
                            isFeedback && isCorrect               -> "✓"
                            isFeedback && isSelected && !isCorrect -> "✗"
                            else -> "${('A' + idx)}"
                        },
                        fontFamily = DMSans, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        color = when {
                            isFeedback && isCorrect               -> Success
                            isFeedback && isSelected && !isCorrect -> RiskHigh
                            else                                  -> TextMuted
                        }
                    )
                    Text(option,
                        fontFamily = BaloTamizha2, fontSize = 14.sp,
                        color = when {
                            isFeedback && isCorrect               -> Success
                            isFeedback && isSelected && !isCorrect -> RiskHigh
                            else                                  -> TextPrimary
                        }
                    )
                }
            }

            if (isFeedback) {
                Text(
                    if (selectedOption == correctIdx)
                        (if (language == AppLanguage.TAMIL) "✅ சரியான பதில்!" else "✅ Correct!")
                    else
                        (if (language == AppLanguage.TAMIL) "❌ தவறு — சரியான பதில்: $correctMeaning"
                        else "❌ Wrong — Correct: $correctMeaning"),
                    fontFamily = DMSans, fontSize = 12.sp,
                    color = if (selectedOption == correctIdx) Success else RiskHigh,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun VocabCard(
    entry: LessonVocabEntry,
    language: AppLanguage,
    onSpeak: (() -> Unit)?,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgCard, RoundedCornerShape(16.dp))
            .border(1.dp, Cyan.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        // Word + TTS button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                entry.word,
                fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold,
                fontSize = 22.sp, color = Cyan
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (language == AppLanguage.TAMIL) "விரிவாக →" else "Details →",
                    fontFamily = DMSans, fontSize = 12.sp, color = TextMuted
                )
                if (onSpeak != null) {
                    IconButton(onClick = onSpeak) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Hear pronunciation", tint = Cyan)
                    }
                }
            }
        }

        // Syllable breakdown row
        if (entry.syllables.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgCardElevated, RoundedCornerShape(8.dp))
                    .padding(horizontal = screenGutter(), vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("SYLLABLES", fontFamily = DMSans, fontSize = 12.sp, color = TextMuted,
                    fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(Spacing.sm))
                entry.syllables.forEachIndexed { i, syl ->
                    if (i > 0) Text(" · ", color = TextMuted, fontSize = 14.sp)
                    Text(syl, fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold,
                        fontSize = 16.sp, color = Amber)
                }
            }
        }

        HorizontalDivider(color = Border, thickness = 0.5.dp)

        // Tamil + English meanings side by side
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Column(modifier = Modifier.weight(1f)) {
                Text("தமிழ்", fontFamily = DMSans, fontSize = 12.sp, color = TextMuted,
                    fontWeight = FontWeight.SemiBold)
                Text(entry.meaningTa, fontFamily = BaloTamizha2, fontSize = 14.sp, color = TextPrimary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("ENGLISH", fontFamily = DMSans, fontSize = 12.sp, color = TextMuted,
                    fontWeight = FontWeight.SemiBold)
                Text(entry.meaningEn, fontFamily = DMSans, fontSize = 14.sp, color = TextPrimary)
            }
        }
    }
}
