package com.ezhil.app.ui.screens.student

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.ezhil.app.data.local.entity.GameSessionEntity
import com.ezhil.app.data.remote.dto.LessonContent
import com.ezhil.app.data.remote.dto.LessonQuizItem
import com.ezhil.app.ui.components.*
import com.ezhil.app.ui.navigation.Screen
import com.ezhil.app.ui.strings.AppLanguage
import com.ezhil.app.ui.theme.*
import com.ezhil.app.ui.viewmodel.AppLanguageViewModel
import com.squareup.moshi.Moshi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

enum class QuizPhase { LOADING, ANSWERING, FEEDBACK, COMPLETE }

@HiltViewModel
class ComprehensionQuizViewModel @Inject constructor(
    private val db: EzhilDatabase,
    private val prefs: SecurePrefs,
    private val moshi: Moshi
) : ViewModel() {

    private val _items         = MutableStateFlow<List<LessonQuizItem>>(emptyList())
    private val _lessonTitle   = MutableStateFlow("")
    private val _phase         = MutableStateFlow(QuizPhase.LOADING)
    val phase: StateFlow<QuizPhase> = _phase

    private val _index         = MutableStateFlow(0)
    val index: StateFlow<Int>  = _index

    private val _correctCount  = MutableStateFlow(0)
    val correctCount: StateFlow<Int> = _correctCount

    private val _selectedOption = MutableStateFlow(-1)
    val selectedOption: StateFlow<Int> = _selectedOption

    val currentQuestion: StateFlow<LessonQuizItem?> =
        combine(_items, _index) { items, idx -> items.getOrNull(idx) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val totalQuestions: StateFlow<Int> = _items
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _startMs = MutableStateFlow(0L)

    fun load(lessonId: String) {
        _index.value = 0
        _correctCount.value = 0
        _selectedOption.value = -1
        _phase.value = QuizPhase.LOADING
        viewModelScope.launch {
            val lesson = db.lessonDao().getById(lessonId)
            if (lesson == null) { _phase.value = QuizPhase.COMPLETE; return@launch }
            _lessonTitle.value = lesson.title
            val content = try {
                moshi.adapter(LessonContent::class.java).fromJson(lesson.contentJson)
            } catch (e: Exception) { null }
            val quizItems = content?.quiz ?: emptyList()
            if (quizItems.isEmpty()) { _phase.value = QuizPhase.COMPLETE; return@launch }
            _items.value = quizItems
            _startMs.value = System.currentTimeMillis()
            _phase.value = QuizPhase.ANSWERING
        }
    }

    fun selectAnswer(optionIndex: Int) {
        if (_phase.value != QuizPhase.ANSWERING) return
        _selectedOption.value = optionIndex
        _phase.value = QuizPhase.FEEDBACK
        if (optionIndex == (_items.value.getOrNull(_index.value)?.correctIndex ?: -1))
            _correctCount.value++
        viewModelScope.launch {
            delay(1200)
            val next = _index.value + 1
            if (next >= _items.value.size) {
                saveSession()
                _phase.value = QuizPhase.COMPLETE
            } else {
                _index.value = next
                _selectedOption.value = -1
                _phase.value = QuizPhase.ANSWERING
            }
        }
    }

    private fun saveSession() {
        val studentId  = prefs.activeStudentId ?: return
        val durationMs = (System.currentTimeMillis() - _startMs.value).toInt()
        val total      = _items.value.size
        val correct    = _correctCount.value
        viewModelScope.launch {
            db.gameSessionDao().insert(GameSessionEntity(
                id            = UUID.randomUUID().toString(),
                studentId     = studentId,
                gameType      = "comprehension_quiz",
                playedAt      = Instant.now().toString(),
                roundsTotal   = total,
                roundsCorrect = correct,
                durationMs    = durationMs,
                starsEarned   = starsForScore(correct, total),
                syncStatus    = "pending"
            ))
        }
    }

    fun starsForScore(
        correct: Int = _correctCount.value,
        total: Int = _items.value.size
    ): Int = when {
        total == 0       -> 0
        correct == total -> 3
        correct * 3 >= total * 2 -> 2
        correct * 2 >= total     -> 1
        else             -> 0
    }
}

@Composable
fun ComprehensionQuizScreen(
    navController: NavHostController,
    lessonId: String,
    vm: ComprehensionQuizViewModel = hiltViewModel(),
    langVm: AppLanguageViewModel = hiltViewModel()
) {
    val language       by langVm.language.collectAsState()
    val phase          by vm.phase.collectAsState()
    val currentQuestion by vm.currentQuestion.collectAsState()
    val index          by vm.index.collectAsState()
    val totalQuestions by vm.totalQuestions.collectAsState()
    val correctCount   by vm.correctCount.collectAsState()
    val selectedOption by vm.selectedOption.collectAsState()

    LaunchedEffect(lessonId) { vm.load(lessonId) }

    when (phase) {
        QuizPhase.LOADING -> Box(
            modifier = Modifier.fillMaxSize().background(BgDark),
            contentAlignment = Alignment.Center
        ) { CircularProgressIndicator(color = Cyan) }

        QuizPhase.COMPLETE -> QuizCompleteScreen(
            correct  = correctCount,
            total    = totalQuestions,
            stars    = vm.starsForScore(),
            language = language,
            onClaim  = {
                navController.navigate(Screen.Achievement.route("lesson_complete", vm.starsForScore())) {
                    popUpTo(Screen.ComprehensionQuiz.route) { inclusive = true }
                }
            },
            onRetry  = { vm.load(lessonId) }
        )

        else -> currentQuestion?.let { question ->
            QuizQuestionScreen(
                question       = question,
                questionNumber = index + 1,
                totalQuestions = totalQuestions,
                selectedOption = selectedOption,
                isFeedback     = phase == QuizPhase.FEEDBACK,
                language       = language,
                onAnswer       = { vm.selectAnswer(it) },
                onBack         = { navController.popBackStack() }
            )
        } ?: Box(Modifier.fillMaxSize().background(BgDark), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Cyan)
        }
    }
}

@Composable
private fun QuizQuestionScreen(
    question: LessonQuizItem,
    questionNumber: Int,
    totalQuestions: Int,
    selectedOption: Int,
    isFeedback: Boolean,
    language: AppLanguage,
    onAnswer: (Int) -> Unit,
    onBack: () -> Unit
) {
    val optionLabels = listOf("A", "B", "C", "D")
    val options = if (language == AppLanguage.ENGLISH && !question.optionsEn.isNullOrEmpty())
        question.optionsEn!! else question.optionsTa
    val questionText = if (language == AppLanguage.ENGLISH && question.questionEn != null)
        question.questionEn!! else question.questionTa

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
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextSecondary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (language == AppLanguage.TAMIL) "புரிதல் கேள்வி" else "Comprehension Quiz",
                    fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold,
                    fontSize = 18.sp, color = TextPrimary
                )
                Text("QUESTION $questionNumber / $totalQuestions",
                    fontFamily = DMSans, fontSize = 12.sp, color = TextMuted)
            }
            Box(
                modifier = Modifier
                    .background(CyanDim, RoundedCornerShape(20.dp))
                    .padding(horizontal = screenGutter(), vertical = Spacing.xs)
            ) {
                Text("Q$questionNumber", fontFamily = DMSans, fontWeight = FontWeight.Bold,
                    fontSize = 12.sp, color = Cyan)
            }
        }

        // Progress
        LinearProgressIndicator(
            progress = { questionNumber.toFloat() / totalQuestions.coerceAtLeast(1) },
            modifier = Modifier.fillMaxWidth(),
            color = Cyan, trackColor = Border
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Spacer(Modifier.height(Spacing.sm))

            // Question card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgCard, RoundedCornerShape(16.dp))
                    .border(1.dp, Cyan.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .padding(Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("❓", fontSize = 32.sp)
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    questionText,
                    fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold,
                    fontSize = 18.sp, color = TextPrimary, textAlign = TextAlign.Center
                )
            }

            Text(
                if (language == AppLanguage.TAMIL) "சரியான விடையை தேர்வு செய்"
                else "Choose the correct answer",
                fontFamily = DMSans, fontSize = 12.sp, color = TextMuted
            )

            // Option buttons
            val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
            options.take(4).forEachIndexed { idx, option ->
                val isSelected = selectedOption == idx
                val isCorrect  = idx == question.correctIndex
                val bgColor = when {
                    !isFeedback && isSelected             -> Cyan.copy(alpha = 0.18f)
                    isFeedback && isCorrect               -> Success.copy(alpha = 0.18f)
                    isFeedback && isSelected && !isCorrect -> RiskHigh.copy(alpha = 0.18f)
                    else                                  -> BgCard
                }
                val borderColor = when {
                    !isFeedback && isSelected             -> Cyan
                    isFeedback && isCorrect               -> Success
                    isFeedback && isSelected && !isCorrect -> RiskHigh
                    else                                  -> Border
                }
                val textColor = when {
                    isFeedback && isCorrect               -> Success
                    isFeedback && isSelected && !isCorrect -> RiskHigh
                    else                                  -> TextPrimary
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bgColor, RoundedCornerShape(12.dp))
                        .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                        .clickable(enabled = !isFeedback) {
                            // Felt feedback: crisp confirm on correct, one soft
                            // tick on wrong — never a harsh buzz for a child.
                            haptics.performHapticFeedback(
                                if (idx == question.correctIndex)
                                    androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                                else
                                    androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove
                            )
                            onAnswer(idx)
                        }
                        .padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(BgCardElevated, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            when {
                                isFeedback && isCorrect               -> "✓"
                                isFeedback && isSelected && !isCorrect -> "✗"
                                else -> optionLabels.getOrElse(idx) { "${idx + 1}" }
                            },
                            fontFamily = DMSans, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                            color = when {
                                isFeedback && isCorrect  -> Success
                                isFeedback && isSelected -> RiskHigh
                                else                     -> TextMuted
                            }
                        )
                    }
                    Spacer(Modifier.width(Spacing.md))
                    Text(option, fontFamily = BaloTamizha2, fontSize = 16.sp, color = textColor)
                }
            }
        }
    }
}

@Composable
private fun QuizCompleteScreen(
    correct: Int,
    total: Int,
    stars: Int,
    language: AppLanguage,
    onClaim: () -> Unit,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        EzhilanWidget(
            state = if (stars >= 2) EzhilanState.CELEBRATING else EzhilanState.IDLE,
            size  = 100.dp
        )
        Spacer(Modifier.height(Spacing.md))
        Row {
            repeat(stars) { Text("⭐", fontSize = 28.sp) }
            repeat((3 - stars).coerceAtLeast(0)) { Text("☆", fontSize = 28.sp) }
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            if (language == AppLanguage.TAMIL) "வினாடி வினா முடிந்தது!" else "Quiz Complete!",
            fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold,
            fontSize = 24.sp, color = TextPrimary
        )
        Spacer(Modifier.height(Spacing.lg))

        // Stars above already carry the result. A child never sees the raw
        // score or an accuracy percentage — the counts are saved to
        // lesson_progress for the teacher's reports instead.
        Text(
            when {
                stars >= 3 -> if (language == AppLanguage.TAMIL) "அருமை!" else "Excellent!"
                stars == 2 -> if (language == AppLanguage.TAMIL) "நல்லது!" else "Well done!"
                else       -> if (language == AppLanguage.TAMIL) "தொடர்ந்து முயற்சி!" else "Keep practising!"
            },
            fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold,
            fontSize = 18.sp, color = if (stars >= 2) Gold else Amber
        )

        Spacer(Modifier.height(Spacing.xl))
        EzhilButton(
            label    = if (language == AppLanguage.TAMIL) "🏆 பரிசு பெறு!" else "🏆 Claim Reward!",
            onClick  = onClaim,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(Spacing.sm))
        EzhilOutlinedButton(
            label    = if (language == AppLanguage.TAMIL) "🔄 மீண்டும் முயற்சி" else "🔄 Try Again",
            onClick  = onRetry,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

