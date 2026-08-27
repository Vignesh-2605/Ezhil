package com.ezhil.app.ui.screens.student

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.ezhil.app.data.local.EzhilDatabase
import com.ezhil.app.data.local.SecurePrefs
import com.ezhil.app.data.local.entity.GameSessionEntity
import com.ezhil.app.ui.components.*
import com.ezhil.app.ui.navigation.Screen
import com.ezhil.app.ui.strings.AppLanguage
import com.ezhil.app.ui.strings.EzhilStrings
import com.ezhil.app.ui.strings.StringKey
import com.ezhil.app.ui.theme.*
import com.ezhil.app.ui.viewmodel.AppLanguageViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PhonicsGamesViewModel @Inject constructor(
    private val db: EzhilDatabase,
    private val prefs: SecurePrefs
) : ViewModel() {

    data class Round(val question: String, val questionEn: String, val options: List<String>, val correctIndex: Int)

    private val rounds = listOf(
        Round("'அம்மா' என்ற வார்த்தையில் உள்ள முதல் எழுத்து?", "First letter of 'Amma'?", listOf("அ", "ம", "ஆ", "ஒ"), 0),
        Round("'கடல்' என்ற வார்த்தையில் எத்தனை எழுத்துக்கள்?", "How many letters in 'Kadal'?", listOf("3", "4", "2", "5"), 0),
        Round("'பால்' என்ற வார்த்தைக்கு ஒரே ஒலி வார்த்தை?", "Rhyming word for 'Paal'?", listOf("காடு", "வால்", "மால்", "தார்"), 1),
        Round("'குரங்கு' என்ற வார்த்தையில் தொடக்க எழுத்து?", "Starting letter of 'Kurangu'?", listOf("கு", "ர", "உ", "க"), 0),
    )

    private var roundIndex = 0
    private val _correctCount = MutableStateFlow(0)
    private val startMs = System.currentTimeMillis()

    private val _currentRoundIndex = MutableStateFlow(0)
    val currentRoundIndex: StateFlow<Int> = _currentRoundIndex

    private val _currentRound = MutableStateFlow(rounds[0])
    val currentRound: StateFlow<Round> = _currentRound

    private val _lastAnswer = MutableStateFlow<Int?>(null)
    val lastAnswer: StateFlow<Int?> = _lastAnswer

    private val _completed = MutableStateFlow(false)
    val completed: StateFlow<Boolean> = _completed

    private val _stars = MutableStateFlow(0)
    val stars: StateFlow<Int> = _stars

    val accuracy: StateFlow<Int> = _correctCount.map { c ->
        if (rounds.isNotEmpty()) c * 100 / rounds.size else 0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val xpEarned: StateFlow<Int> = _stars.map { s -> s * 40 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun answer(index: Int) {
        _lastAnswer.value = index
        if (index == rounds[roundIndex].correctIndex) _correctCount.value++
        viewModelScope.launch {
            kotlinx.coroutines.delay(600)
            _lastAnswer.value = null
            roundIndex++
            if (roundIndex >= rounds.size) {
                val c = _correctCount.value
                val earned = when {
                    c == rounds.size         -> 3
                    c >= rounds.size * 2 / 3 -> 2
                    c >= rounds.size / 2     -> 1
                    else                     -> 0
                }
                _stars.value = earned
                saveSession(earned)
                _completed.value = true
            } else {
                _currentRound.value = rounds[roundIndex]
                _currentRoundIndex.value = roundIndex
            }
        }
    }

    private fun saveSession(earned: Int) {
        val studentId = prefs.activeStudentId ?: return
        val durationMs = (System.currentTimeMillis() - startMs).toInt()
        viewModelScope.launch {
            db.gameSessionDao().insert(GameSessionEntity(
                id = UUID.randomUUID().toString(),
                studentId = studentId,
                gameType = "phonics_quiz",
                playedAt = Instant.now().toString(),
                roundsTotal = rounds.size,
                roundsCorrect = _correctCount.value,
                durationMs = durationMs,
                starsEarned = earned,
                syncStatus = "pending"
            ))
        }
    }

    fun reset() {
        roundIndex = 0
        _correctCount.value = 0
        _currentRoundIndex.value = 0
        _currentRound.value = rounds[0]
        _completed.value = false
        _stars.value = 0
    }
}

@Composable
fun PhonicsGamesScreen(
    navController: NavHostController,
    vm: PhonicsGamesViewModel = hiltViewModel(),
    langVm: AppLanguageViewModel = hiltViewModel()
) {
    val language        by langVm.language.collectAsState()
    val round           by vm.currentRound.collectAsState()
    val currentRoundIdx by vm.currentRoundIndex.collectAsState()
    val lastAnswer      by vm.lastAnswer.collectAsState()
    val completed       by vm.completed.collectAsState()
    val stars           by vm.stars.collectAsState()
    val xpEarned        by vm.xpEarned.collectAsState()

    if (completed) {
        GameCompleteScreen(
            stars    = stars,
            language = language,
            xpEarned = xpEarned,
            onReplay = { vm.reset() },
            onBack   = { navController.navigate(Screen.Achievement.route("well_done", stars)) }
        )
        return
    }

    var showQuitDialog by remember { mutableStateOf(false) }

    if (showQuitDialog) {
        AlertDialog(
            onDismissRequest = { showQuitDialog = false },
            containerColor = BgCard,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("⚠️", fontSize = 28.sp)
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        if (language == AppLanguage.TAMIL) "விளையாட்டை விடணுமா?" else "Quit Game?",
                        fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold,
                        fontSize = 18.sp, color = TextPrimary
                    )
                }
            },
            text = {
                Text(
                    if (language == AppLanguage.TAMIL) "முன்னேற்றம் இழக்கப்படும்."
                    else "Progress will be lost.",
                    fontFamily = DMSans, fontSize = 13.sp, color = TextSecondary
                )
            },
            confirmButton = {
                EzhilButton(
                    label = if (language == AppLanguage.TAMIL) "▶ தொடர்" else "▶ Keep Playing",
                    onClick = { showQuitDialog = false }
                )
            },
            dismissButton = {
                TextButton(onClick = { navController.popBackStack() }) {
                    Text(
                        if (language == AppLanguage.TAMIL) "✗ விடு" else "✗ Quit",
                        color = RiskHigh, fontFamily = DMSans, fontWeight = FontWeight.SemiBold
                    )
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(horizontal = screenGutter(), vertical = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { showQuitDialog = true }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextSecondary)
                }
                EzhilanWidget(state = EzhilanState.IDLE, size = 48.dp)
            }
            LanguageToggle(current = language, onToggle = { langVm.toggle() })
        }

        // Question card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgCard, RoundedCornerShape(20.dp))
                .border(1.dp, Cyan.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                .padding(Spacing.lg)
        ) {
            Text(
                "🎮 ${EzhilStrings.get(StringKey.GAMES_ROUND, language)} ${currentRoundIdx + 1}",
                fontFamily = DMSans, fontSize = 12.sp, color = Cyan,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                if (language == AppLanguage.TAMIL) round.question else round.questionEn,
                fontFamily = BaloTamizha2,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = TextPrimary
            )
        }

        // Answer options
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            round.options.forEachIndexed { idx, option ->
                val isWrong   = lastAnswer == idx && idx != round.correctIndex
                val isCorrect = lastAnswer != null && idx == round.correctIndex
                GameOption(
                    text = option,
                    isWrong = isWrong,
                    isCorrect = isCorrect,
                    onClick = { if (lastAnswer == null) vm.answer(idx) }
                )
            }
        }
    }
}
