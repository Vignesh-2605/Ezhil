package com.ezhil.app.ui.screens.student

import androidx.compose.animation.core.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

// ── Games Hub ─────────────────────────────────────────────────────────────────

@Composable
fun GamesHubScreen(
    navController: NavHostController,
    langVm: AppLanguageViewModel = hiltViewModel()
) {
    val language by langVm.language.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = screenGutter(), vertical = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextSecondary)
                }
                Column {
                    Text(
                        "விளையாடலாம்! / Let's Play!",
                        fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold,
                        fontSize = 18.sp, color = TextPrimary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("இன்று: ", fontFamily = BaloTamizha2, fontSize = 12.sp, color = TextMuted)
                        Text("★★", color = Gold, fontSize = 12.sp)
                        Text("☆", color = GoldDim, fontSize = 12.sp)
                        Text(" (Today's Stars)", fontFamily = DMSans, fontSize = 12.sp, color = TextMuted)
                    }
                }
            }
            LanguageToggle(current = language, onToggle = { langVm.toggle() })
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = screenGutter()),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Spacer(Modifier.height(Spacing.xs))

            // Slim game rows
            SlimGameRow(
                gameName = "Match Sound",
                tamilSubtitle = "ஒலியை இணைத்திடு",
                buttonColor = Cyan,
                onClick = { navController.navigate(Screen.MatchSound.route) }
            )
            SlimGameRow(
                gameName = "Spot the Letter",
                tamilSubtitle = "எழுத்தைக் கண்டுபிடி",
                buttonColor = Amber,
                onClick = { navController.navigate(Screen.SpotLetter.route) }
            )
            SlimGameRow(
                gameName = "Build a Word",
                tamilSubtitle = "சொல்லை உருவாக்கு",
                buttonColor = Purple,
                onClick = { navController.navigate(Screen.BuildWord.route) }
            )
            SlimGameRow(
                gameName = "Phonics Quiz",
                tamilSubtitle = "ஒலிவியல் வினாடி வினா",
                buttonColor = RiskLow,
                onClick = { navController.navigate(Screen.PhonicsGames.route) }
            )

            Spacer(Modifier.height(Spacing.md))

            // Footer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgCard, RoundedCornerShape(12.dp))
                    .border(1.dp, Border, RoundedCornerShape(12.dp))
                    .padding(Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "புதிய சவால்! New Challenge: Master the 'அ' sounds today.",
                    fontFamily = BaloTamizha2, fontSize = 13.sp, color = TextSecondary,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    "● SYSTEM ONLINE • READY TO PLAY",
                    fontFamily = DMSans, fontSize = 12.sp, color = Success
                )
            }

            Spacer(Modifier.height(Spacing.md))
        }
    }
}

@Composable
private fun SlimGameRow(
    gameName: String,
    tamilSubtitle: String,
    buttonColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgCard, RoundedCornerShape(12.dp))
            .border(1.dp, buttonColor.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(horizontal = screenGutter(), vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                gameName,
                fontFamily = DMSans, fontWeight = FontWeight.Bold,
                fontSize = 16.sp, color = TextPrimary
            )
            Text(
                tamilSubtitle,
                fontFamily = BaloTamizha2, fontSize = 13.sp, color = TextMuted
            )
        }
        Spacer(Modifier.width(Spacing.sm))
        Box(
            modifier = Modifier
                .background(buttonColor, RoundedCornerShape(10.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = screenGutter(), vertical = Spacing.sm),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "▶ Play",
                fontFamily = DMSans, fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = if (buttonColor == Purple) TextPrimary else TextOnCyan
            )
        }
    }
}

// ── Match Sound Game ──────────────────────────────────────────────────────────

data class SoundRound(
    val soundPromptTamil: String,
    val soundPromptEn: String,
    val options: List<String>,
    val correctIndex: Int,
    /** The phoneme the child must actually hear. A game called "match the
     *  sound" is unplayable without it. */
    val spokenSound: String
)

private val SOUND_ROUNDS = listOf(
    SoundRound("'அ' ஒலியை கேள்", "Listen for 'A' sound", listOf("அம்மா", "கம்பி", "சாதம்", "நாய்"), 0, "அ"),
    SoundRound("'ம' ஒலியை கேள்", "Listen for 'Ma' sound", listOf("கல்", "மரம்", "தண்ணீர்", "வீடு"), 1, "ம"),
    SoundRound("'க' ஒலியை கேள்", "Listen for 'Ka' sound", listOf("பூ", "தாய்", "கடல்", "இலை"), 2, "க"),
)

@HiltViewModel
class MatchSoundViewModel @Inject constructor(
    private val db: EzhilDatabase,
    private val prefs: SecurePrefs
) : ViewModel() {

    private var roundIndex = 0
    private val _correctCount = MutableStateFlow(0)
    private val _roundIndex = MutableStateFlow(0)
    val currentRoundIndex: StateFlow<Int> = _roundIndex
    private val startMs = System.currentTimeMillis()

    private val _showIntro = MutableStateFlow(true)
    val showIntro: StateFlow<Boolean> = _showIntro

    private val _round = MutableStateFlow(SOUND_ROUNDS[0])
    val round: StateFlow<SoundRound> = _round

    private val _lastAnswer = MutableStateFlow<Int?>(null)
    val lastAnswer: StateFlow<Int?> = _lastAnswer

    private val _completed = MutableStateFlow(false)
    val completed: StateFlow<Boolean> = _completed

    private val _stars = MutableStateFlow(0)
    val stars: StateFlow<Int> = _stars

    val accuracy: StateFlow<Int> = _correctCount.map { c ->
        if (SOUND_ROUNDS.isNotEmpty()) c * 100 / SOUND_ROUNDS.size else 0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val xpEarned: StateFlow<Int> = _stars.map { s -> s * 40 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun startGame(difficulty: String = "Medium") {
        _showIntro.value = false
    }

    fun answer(index: Int) {
        _lastAnswer.value = index
        if (index == SOUND_ROUNDS[roundIndex].correctIndex) _correctCount.value++
        viewModelScope.launch {
            kotlinx.coroutines.delay(600)
            _lastAnswer.value = null
            roundIndex++
            _roundIndex.value = roundIndex.coerceAtMost(SOUND_ROUNDS.size - 1)
            if (roundIndex >= SOUND_ROUNDS.size) {
                val c = _correctCount.value
                val earned = when {
                    c == SOUND_ROUNDS.size         -> 3
                    c >= SOUND_ROUNDS.size * 2 / 3 -> 2
                    c > 0                           -> 1
                    else                            -> 0
                }
                _stars.value = earned
                saveSession(earned)
                _completed.value = true
            } else {
                _round.value = SOUND_ROUNDS[roundIndex]
            }
        }
    }

    private fun saveSession(earned: Int) {
        val studentId = prefs.activeStudentId ?: return
        viewModelScope.launch {
            db.gameSessionDao().insert(GameSessionEntity(
                id = UUID.randomUUID().toString(),
                studentId = studentId,
                gameType = "match_sound",
                playedAt = Instant.now().toString(),
                roundsTotal = SOUND_ROUNDS.size,
                roundsCorrect = _correctCount.value,
                durationMs = (System.currentTimeMillis() - startMs).toInt(),
                starsEarned = earned,
                syncStatus = "pending"
            ))
        }
    }

    fun reset() {
        roundIndex = 0
        _correctCount.value = 0
        _roundIndex.value = 0
        _round.value = SOUND_ROUNDS[0]
        _completed.value = false
        _showIntro.value = true
    }
}

@Composable
fun MatchSoundGameScreen(
    navController: NavHostController,
    vm: MatchSoundViewModel = hiltViewModel(),
    langVm: AppLanguageViewModel = hiltViewModel()
) {
    val language        by langVm.language.collectAsState()
    val round           by vm.round.collectAsState()
    val lastAnswer      by vm.lastAnswer.collectAsState()
    val completed       by vm.completed.collectAsState()
    val stars           by vm.stars.collectAsState()
    val showIntro       by vm.showIntro.collectAsState()
    val xpEarned        by vm.xpEarned.collectAsState()
    val currentRoundIdx by vm.currentRoundIndex.collectAsState()
    val tts = rememberTamilTts()
    val clips = rememberPhonemeAudio()

    // Bundled recording first, system TTS only as a fallback. A classroom
    // should not have to download a voice pack to hear three letters.
    fun playSound() {
        if (!clips.play(round.spokenSound)) {
            tts.speak(round.spokenSound, "match_sound_${round.spokenSound}")
        }
    }
    val canPlay = clips.hasClip(round.spokenSound) || tts.available

    // Play the target sound when the round changes, so the child hears it
    // without having to discover the speaker button first. Waits for the
    // answer word to finish first — the round advances 600ms after a tap while
    // the word runs ~800ms, so starting immediately cut it off mid-syllable.
    LaunchedEffect(round, showIntro) {
        if (!showIntro) {
            clips.awaitIdle()
            playSound()
        }
    }

    if (completed) {
        GameCompleteScreen(
            stars = stars,
            language = language,
            xpEarned = xpEarned,
            onReplay = { vm.reset() },
            onBack = { navController.navigate(Screen.Achievement.route("well_done", stars)) }
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

    if (showIntro) {
        MatchSoundIntroContent(
            language = language,
            onStart = { difficulty -> vm.startGame(difficulty) },
            onBack = { navController.popBackStack() }
        )
        return
    }

    // Pulsing animation for the sound icon
    val infiniteTransition = rememberInfiniteTransition(label = "soundPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse"
    )

    Column(
        modifier = Modifier.fillMaxSize().background(BgDark)
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgCard)
                .border(1.dp, Border)
                .padding(Spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { showQuitDialog = true }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextSecondary)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (language == AppLanguage.TAMIL) "ஒலியை பொருத்து" else "Match the Sound",
                    fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold,
                    fontSize = 18.sp, color = TextPrimary
                )
                Text(
                    "ROUND ${currentRoundIdx + 1} / ${SOUND_ROUNDS.size}",
                    fontFamily = DMSans, fontSize = 12.sp, color = Cyan
                )
            }
            LanguageToggle(current = language, onToggle = { langVm.toggle() })
        }

        // Progress bar
        LinearProgressIndicator(
            progress = { (currentRoundIdx + 1).toFloat() / SOUND_ROUNDS.size },
            modifier = Modifier.fillMaxWidth(),
            color = Cyan, trackColor = Border
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Sound prompt card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgCard, RoundedCornerShape(20.dp))
                    .border(1.dp, Cyan.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    .padding(Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size((80 * pulseScale).dp)
                        .background(CyanDim, RoundedCornerShape(20.dp))
                        .border(2.dp, Cyan, RoundedCornerShape(20.dp))
                        .clickable(enabled = canPlay) { playSound() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (canPlay) "🔊" else "🔇", fontSize = 36.sp)
                }
                Spacer(Modifier.height(Spacing.md))
                Text(
                    if (language == AppLanguage.TAMIL) round.soundPromptTamil else round.soundPromptEn,
                    fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold,
                    fontSize = 20.sp, color = TextPrimary
                )
                Spacer(Modifier.height(Spacing.xs))
                if (canPlay) {
                    Text(
                        EzhilStrings.get(StringKey.GAMES_LISTEN_TO_SOUND, language),
                        fontFamily = DMSans, fontSize = 13.sp, color = Cyan
                    )
                } else {
                    // Silence with no explanation reads as a broken game. Say
                    // what is missing and how to fix it.
                    val ctx = LocalContext.current
                    Text(
                        "தமிழ் குரல் நிறுவப்படவில்லை / Tamil voice not installed — tap to add",
                        fontFamily = DMSans, fontSize = 12.sp, color = Amber,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.clickable { tts.openVoiceInstaller(ctx) }
                    )
                }
                Spacer(Modifier.height(Spacing.sm))
                // Round dots
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    repeat(SOUND_ROUNDS.size) { i ->
                        Box(
                            modifier = Modifier
                                .size(if (i == currentRoundIdx) 10.dp else 7.dp)
                                .background(
                                    if (i <= currentRoundIdx) Cyan else Border,
                                    CircleShape
                                )
                        )
                    }
                }
            }

            // Options
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                round.options.forEachIndexed { idx, option ->
                    val isWrong   = lastAnswer == idx && idx != round.correctIndex
                    val isCorrect = lastAnswer != null && idx == round.correctIndex
                    GameOption(
                        text = option,
                        isWrong = isWrong,
                        isCorrect = isCorrect,
                        onClick = {
                            if (lastAnswer == null) {
                                // Say the word as it is chosen — the child hears
                                // the sound they matched, which is the point of
                                // the exercise rather than decoration.
                                clips.play(option)
                                vm.answer(idx)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MatchSoundIntroContent(
    language: AppLanguage,
    onStart: (String) -> Unit,
    onBack: () -> Unit
) {
    var selectedDifficulty by remember { mutableStateOf("Medium") }

    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val wave1 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ), label = "w1"
    )
    val wave2 by infiniteTransition.animateFloat(
        initialValue = 0.7f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ), label = "w2"
    )
    val wave3 by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ), label = "w3"
    )
    val wave4 by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ), label = "w4"
    )
    val wave5 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ), label = "w5"
    )

    val waveHeights = listOf(wave1, wave2, wave3, wave4, wave5)
    val maxBarHeight = 40.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextSecondary)
            }
            Text("ஒலி சேர்ப்பு / MATCH THE SOUND",
                fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold,
                fontSize = 18.sp, color = TextPrimary)
            Spacer(Modifier.width(48.dp))
        }

        Spacer(Modifier.height(Spacing.sm))

        // Animated waveform
        Row(
            modifier = Modifier
                .background(BgCard, RoundedCornerShape(16.dp))
                .border(1.dp, Cyan.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                .padding(horizontal = screenGutter(), vertical = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            waveHeights.forEach { fraction ->
                Box(
                    modifier = Modifier
                        .width(10.dp)
                        .height(maxBarHeight * fraction)
                        .background(Cyan, RoundedCornerShape(4.dp))
                )
            }
        }

        // Status text
        Text(
            "● SYSTEM READY",
            fontFamily = DMSans, fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp, color = Success
        )

        // Info card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgCard, RoundedCornerShape(16.dp))
                .border(1.dp, Cyan.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                .padding(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                if (language == AppLanguage.TAMIL)
                    "ஒலியை கவனமாக கேள்! பொருத்தமான சொல்லை தேர்ந்தெடு."
                else
                    "Listen carefully to the sound and pick the matching word.",
                fontFamily = BaloTamizha2, fontSize = 14.sp,
                color = TextSecondary, lineHeight = 22.sp
            )
        }

        // Difficulty selector
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Text(
                if (language == AppLanguage.TAMIL) "நிலை தேர்ந்தெடு" else "Select Difficulty",
                fontFamily = DMSans, fontSize = 13.sp, color = TextMuted
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("Easy" to "எளிது", "Medium" to "சாதரண", "Hard" to "கடினம்").forEach { (key, tamil) ->
                    val isSelected = selectedDifficulty == key
                    Box(
                        modifier = Modifier
                            .background(
                                if (isSelected) CyanDim else BgCard,
                                RoundedCornerShape(20.dp)
                            )
                            .border(
                                1.dp,
                                if (isSelected) Cyan else Border,
                                RoundedCornerShape(20.dp)
                            )
                            .clickable { selectedDifficulty = key }
                            .padding(horizontal = screenGutter(), vertical = Spacing.sm),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "$tamil $key",
                            fontFamily = BaloTamizha2, fontSize = 13.sp,
                            color = if (isSelected) Cyan else TextMuted,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(Spacing.sm))

        // Start button
        EzhilButton(
            label = "தொடங்கு! / Start! ▶",
            onClick = { onStart(selectedDifficulty) },
            modifier = Modifier.fillMaxWidth()
        )

        // Footer
        Text(
            "● PROGRESS SYNCED",
            fontFamily = DMSans, fontSize = 12.sp, color = TextMuted
        )

        Spacer(Modifier.height(Spacing.md))
    }
}

// ── Spot the Letter Game ──────────────────────────────────────────────────────

data class SpotRound(
    val targetTamil: String,
    val targetEn: String,
    val letters: List<String>,
    val correctIndices: Set<Int>
)

private val SPOT_ROUNDS = listOf(
    SpotRound(
        "'அ' எழுத்தை கண்டுபிடி", "Find 'அ'",
        listOf("அ", "ஆ", "அ", "இ", "உ", "அ", "ஈ", "ஒ", "அ", "ஓ", "ஐ", "அ", "ஔ", "அ", "ஏ", "ஆ"),
        setOf(0, 2, 5, 8, 11, 13)
    ),
    SpotRound(
        "'க' எழுத்தை கண்டுபிடி", "Find 'க'",
        listOf("க", "ட", "ந", "க", "ம", "வ", "க", "ர", "ய", "க", "ள", "ப", "ல", "ழ", "க", "ஞ"),
        setOf(0, 3, 6, 9, 14)
    ),
    SpotRound(
        "'ப' எழுத்தை கண்டுபிடி", "Find 'ப'",
        listOf("ப", "வ", "ய", "ர", "ப", "ல", "ழ", "ம", "ட", "ப", "ந", "க", "ர", "ள", "ய", "ப"),
        setOf(0, 4, 9, 15)
    ),
)

// Romanized labels for the target letters
private val SPOT_ROMANIZED = mapOf(
    "'அ' எழுத்தை கண்டுபிடி" to "A (Vowel)",
    "'க' எழுத்தை கண்டுபிடி" to "Ka (Consonant)",
    "'ப' எழுத்தை கண்டுபிடி" to "Pa (Consonant)"
)

@HiltViewModel
class SpotLetterViewModel @Inject constructor(
    private val db: EzhilDatabase,
    private val prefs: SecurePrefs
) : ViewModel() {

    private var roundIndex = 0
    private val _correctCount = MutableStateFlow(0)
    private val startMs = System.currentTimeMillis()

    private val _round = MutableStateFlow(SPOT_ROUNDS[0])
    val round: StateFlow<SpotRound> = _round

    private val _roundIndex = MutableStateFlow(0)
    val currentRoundIndex: StateFlow<Int> = _roundIndex

    // Map from tile index -> feedback state: null=untouched, true=correct, false=wrong
    private val _tileFeedback = MutableStateFlow<Map<Int, Boolean>>(emptyMap())
    val tileFeedback: StateFlow<Map<Int, Boolean>> = _tileFeedback

    private val _completed = MutableStateFlow(false)
    val completed: StateFlow<Boolean> = _completed

    private val _stars = MutableStateFlow(0)
    val stars: StateFlow<Int> = _stars

    val accuracy: StateFlow<Int> = _correctCount.map { c ->
        if (SPOT_ROUNDS.isNotEmpty()) c * 100 / SPOT_ROUNDS.size else 0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val xpEarned: StateFlow<Int> = _stars.map { s -> s * 40 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun tapTile(index: Int) {
        val current = _tileFeedback.value
        if (current.containsKey(index)) return // already tapped
        val isCorrect = SPOT_ROUNDS[roundIndex].correctIndices.contains(index)
        _tileFeedback.value = current + (index to isCorrect)

        val updatedFeedback = _tileFeedback.value
        val correctTiles = SPOT_ROUNDS[roundIndex].correctIndices
        val allFound = correctTiles.all { updatedFeedback[it] == true }
        val anyWrong = updatedFeedback.any { (_, correct) -> !correct }

        if (allFound || anyWrong) {
            if (allFound && !anyWrong) _correctCount.value++
            advance()
        }
    }

    fun skip() { advance() }

    private fun advance() {
        viewModelScope.launch {
            kotlinx.coroutines.delay(900)
            roundIndex++
            if (roundIndex >= SPOT_ROUNDS.size) {
                val c = _correctCount.value
                val earned = when {
                    c == SPOT_ROUNDS.size         -> 3
                    c >= SPOT_ROUNDS.size * 2 / 3 -> 2
                    c > 0                          -> 1
                    else                           -> 0
                }
                _stars.value = earned
                saveSession(earned)
                _completed.value = true
            } else {
                _round.value = SPOT_ROUNDS[roundIndex]
                _roundIndex.value = roundIndex
                _tileFeedback.value = emptyMap()
            }
        }
    }

    private fun saveSession(earned: Int) {
        val studentId = prefs.activeStudentId ?: return
        viewModelScope.launch {
            db.gameSessionDao().insert(GameSessionEntity(
                id = UUID.randomUUID().toString(),
                studentId = studentId,
                gameType = "spot_letter",
                playedAt = Instant.now().toString(),
                roundsTotal = SPOT_ROUNDS.size,
                roundsCorrect = _correctCount.value,
                durationMs = (System.currentTimeMillis() - startMs).toInt(),
                starsEarned = earned,
                syncStatus = "pending"
            ))
        }
    }

    fun reset() {
        roundIndex = 0
        _correctCount.value = 0
        _round.value = SPOT_ROUNDS[0]
        _roundIndex.value = 0
        _tileFeedback.value = emptyMap()
        _completed.value = false
        _stars.value = 0
    }
}

@Composable
fun SpotLetterGameScreen(
    navController: NavHostController,
    vm: SpotLetterViewModel = hiltViewModel(),
    langVm: AppLanguageViewModel = hiltViewModel()
) {
    val language by langVm.language.collectAsState()
    val round by vm.round.collectAsState()
    val currentRoundIndex by vm.currentRoundIndex.collectAsState()
    val tileFeedback by vm.tileFeedback.collectAsState()
    val completed by vm.completed.collectAsState()
    val stars by vm.stars.collectAsState()
    val xpEarned by vm.xpEarned.collectAsState()

    if (completed) {
        GameCompleteScreen(
            stars = stars,
            language = language,
            xpEarned = xpEarned,
            onReplay = { vm.reset() },
            onBack = { navController.navigate(Screen.Achievement.route("well_done", stars)) }
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

    // Extract target letter from round title
    val targetLetter = when {
        round.targetTamil.contains("'அ'") -> "அ"
        round.targetTamil.contains("'க'") -> "க"
        round.targetTamil.contains("'ப'") -> "ப"
        else -> round.letters.getOrNull(round.correctIndices.firstOrNull() ?: 0) ?: "?"
    }
    val romanized = SPOT_ROMANIZED[round.targetTamil] ?: ""

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(horizontal = screenGutter(), vertical = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top bar: round counter + stars
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { showQuitDialog = true }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextSecondary)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Round ${currentRoundIndex + 1}/${SPOT_ROUNDS.size}",
                    fontFamily = DMSans, fontWeight = FontWeight.Bold,
                    fontSize = 14.sp, color = Amber
                )
                StarRow(filled = stars, total = 3, size = 14.dp)
            }
            LanguageToggle(current = language, onToggle = { langVm.toggle() })
        }

        Spacer(Modifier.height(Spacing.sm))

        // Target letter card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgCard, RoundedCornerShape(20.dp))
                .border(1.dp, Amber.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                .padding(vertical = Spacing.md, horizontal = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "FIND THE LETTER",
                fontFamily = DMSans, fontWeight = FontWeight.Bold,
                fontSize = 12.sp, color = Amber
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                targetLetter,
                fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold,
                fontSize = 56.sp, color = TextPrimary
            )
            if (romanized.isNotEmpty()) {
                Text(romanized, fontFamily = DMSans, fontSize = 12.sp, color = TextMuted)
            }
            Spacer(Modifier.height(Spacing.xs))
            val foundCount = tileFeedback.count { it.value }
            val totalTarget = round.correctIndices.size
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text("FOUND", fontFamily = DMSans, fontSize = 12.sp, color = TextMuted,
                    fontWeight = FontWeight.SemiBold)
                repeat(totalTarget) { i ->
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                if (i < foundCount) RiskLow else Border,
                                CircleShape
                            )
                    )
                }
                Text("$foundCount / $totalTarget", fontFamily = DMSans,
                    fontWeight = FontWeight.Bold, fontSize = 12.sp,
                    color = if (foundCount > 0) RiskLow else TextMuted)
            }
        }

        Spacer(Modifier.height(Spacing.sm))

        // 4×4 letter grid
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            modifier = Modifier.fillMaxWidth()
        ) {
            round.letters.chunked(4).forEachIndexed { rowIdx, rowLetters ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    rowLetters.forEachIndexed { colIdx, letter ->
                        val idx = rowIdx * 4 + colIdx
                        val feedback = tileFeedback[idx]
                        val isCorrect = feedback == true
                        val isWrong   = feedback == false

                        val borderColor = when {
                            isCorrect -> RiskLow
                            isWrong   -> RiskHigh
                            else      -> Border
                        }
                        val bgColor = when {
                            isCorrect -> RiskLowBg
                            isWrong   -> RiskHighBg
                            else      -> BgCard
                        }
                        val textColor = when {
                            isCorrect -> RiskLow
                            isWrong   -> RiskHigh
                            else      -> TextPrimary
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .background(bgColor, RoundedCornerShape(10.dp))
                                .border(2.dp, borderColor, RoundedCornerShape(10.dp))
                                .clickable { if (feedback == null) vm.tapTile(idx) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                letter,
                                fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold,
                                fontSize = 22.sp, color = textColor
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(Spacing.sm))

        // Bottom: Skip + volume
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { vm.skip() }) {
                Text(
                    "தவிர் | Skip",
                    fontFamily = BaloTamizha2, fontSize = 14.sp, color = TextMuted
                )
            }
            IconButton(onClick = { /* volume toggle */ }) {
                Text("🔊", fontSize = 22.sp)
            }
        }
    }
}

// ── Build a Word Game ─────────────────────────────────────────────────────────

data class BuildRound(
    val targetTamil: String,
    val targetEn: String,
    val scrambled: List<String>,
    val answer: String,
    val imageEmoji: String,
    val level: Int
)

private val BUILD_ROUNDS = listOf(
    BuildRound("'நாய்' என்ற சொல்லை கட்டு", "Build 'Naay'", listOf("ய்", "நா", "கி", "பு", "லி"), "நாய்", "🐕", 4),
    BuildRound("'அம்மா' என்ற சொல்லை கட்டு", "Build 'Amma'", listOf("ம்", "அ", "மா"), "அம்மா", "👩", 2),
    BuildRound("'கடல்' என்ற சொல்லை கட்டு", "Build 'Kadal'", listOf("ல்", "க", "ட"), "கடல்", "🌊", 3),
)

private data class IndexedLetter(val tileIdx: Int, val letter: String)

@HiltViewModel
class BuildWordViewModel @Inject constructor(
    private val db: EzhilDatabase,
    private val prefs: SecurePrefs
) : ViewModel() {

    private var roundIndex = 0
    private val _correctCount = MutableStateFlow(0)
    private val _roundIndex = MutableStateFlow(0)
    val currentRoundIndex: StateFlow<Int> = _roundIndex
    private val startMs = System.currentTimeMillis()

    private val _round = MutableStateFlow(BUILD_ROUNDS[0])
    val round: StateFlow<BuildRound> = _round

    private val _arranged = MutableStateFlow<List<IndexedLetter>>(emptyList())

    val arrangedLetters: StateFlow<List<String>> = _arranged
        .map { it.map { item -> item.letter } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val usedTileIndices: StateFlow<Set<Int>> = _arranged
        .map { it.map { item -> item.tileIdx }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val _completed = MutableStateFlow(false)
    val completed: StateFlow<Boolean> = _completed

    private val _stars = MutableStateFlow(0)
    val stars: StateFlow<Int> = _stars

    private val _checked = MutableStateFlow<Boolean?>(null)
    val checked: StateFlow<Boolean?> = _checked

    val accuracy: StateFlow<Int> = _correctCount.map { c ->
        if (BUILD_ROUNDS.isNotEmpty()) c * 100 / BUILD_ROUNDS.size else 0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val xpEarned: StateFlow<Int> = _stars.map { s -> s * 40 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun addLetter(tileIdx: Int, letter: String) {
        if (_arranged.value.any { it.tileIdx == tileIdx }) return
        _arranged.value = _arranged.value + IndexedLetter(tileIdx, letter)
    }

    fun removeLast() {
        if (_arranged.value.isNotEmpty())
            _arranged.value = _arranged.value.dropLast(1)
    }

    fun checkWord() {
        val isCorrect = _arranged.value.joinToString("") { it.letter } == BUILD_ROUNDS[roundIndex].answer
        _checked.value = isCorrect
        if (isCorrect) _correctCount.value++
        viewModelScope.launch {
            kotlinx.coroutines.delay(800)
            roundIndex++
            _roundIndex.value = roundIndex.coerceAtMost(BUILD_ROUNDS.size - 1)
            if (roundIndex >= BUILD_ROUNDS.size) {
                val c = _correctCount.value
                val earned = when {
                    c == BUILD_ROUNDS.size         -> 3
                    c >= BUILD_ROUNDS.size * 2 / 3 -> 2
                    c > 0                           -> 1
                    else                            -> 0
                }
                _stars.value = earned
                saveSession(earned)
                _completed.value = true
            } else {
                _round.value = BUILD_ROUNDS[roundIndex]
                _arranged.value = emptyList()
                _checked.value = null
            }
        }
    }

    private fun saveSession(earned: Int) {
        val studentId = prefs.activeStudentId ?: return
        viewModelScope.launch {
            db.gameSessionDao().insert(GameSessionEntity(
                id = UUID.randomUUID().toString(),
                studentId = studentId,
                gameType = "build_word",
                playedAt = Instant.now().toString(),
                roundsTotal = BUILD_ROUNDS.size,
                roundsCorrect = _correctCount.value,
                durationMs = (System.currentTimeMillis() - startMs).toInt(),
                starsEarned = earned,
                syncStatus = "pending"
            ))
        }
    }

    fun reset() {
        roundIndex = 0
        _correctCount.value = 0
        _roundIndex.value = 0
        _round.value = BUILD_ROUNDS[0]
        _arranged.value = emptyList()
        _completed.value = false
        _checked.value = null
        _stars.value = 0
    }
}

@Composable
fun BuildWordGameScreen(
    navController: NavHostController,
    vm: BuildWordViewModel = hiltViewModel(),
    langVm: AppLanguageViewModel = hiltViewModel()
) {
    val language        by langVm.language.collectAsState()
    val round           by vm.round.collectAsState()
    val arrangedLetters by vm.arrangedLetters.collectAsState()
    val usedTileIndices by vm.usedTileIndices.collectAsState()
    val completed       by vm.completed.collectAsState()
    val checked         by vm.checked.collectAsState()
    val stars           by vm.stars.collectAsState()
    val xpEarned        by vm.xpEarned.collectAsState()
    val currentRoundIdx by vm.currentRoundIndex.collectAsState()
    if (completed) {
        GameCompleteScreen(
            stars = stars,
            language = language,
            xpEarned = xpEarned,
            onReplay = { vm.reset() },
            onBack = { navController.navigate(Screen.Achievement.route("well_done", stars)) }
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

    val isCorrect = checked == true
    val isWrong   = checked == false
    val typeColor  = Purple

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { showQuitDialog = true }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextSecondary)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (language == AppLanguage.TAMIL) "சொல் கட்டு" else "Build a Word",
                    fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold,
                    fontSize = 18.sp, color = TextPrimary
                )
                Text(
                    "ROUND ${currentRoundIdx + 1} / ${BUILD_ROUNDS.size}",
                    fontFamily = DMSans, fontSize = 12.sp, color = Purple
                )
            }
            LanguageToggle(current = language, onToggle = { langVm.toggle() })
        }

        // Image card with emoji
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgCard, RoundedCornerShape(16.dp))
                .border(1.dp, typeColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                .padding(Spacing.md),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                // Big emoji with subtle glow background
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(typeColor.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                        .border(1.dp, typeColor.copy(alpha = 0.3f), RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(round.imageEmoji, fontSize = 64.sp)
                }
                // LEVEL chip
                Box(
                    modifier = Modifier
                        .background(typeColor, RoundedCornerShape(20.dp))
                        .padding(horizontal = screenGutter(), vertical = Spacing.xs),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "LEVEL ${round.level}",
                        fontFamily = DMSans, fontWeight = FontWeight.Bold,
                        fontSize = 12.sp, color = TextPrimary
                    )
                }
                Text(
                    if (language == AppLanguage.TAMIL) round.targetTamil else round.targetEn,
                    fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold,
                    fontSize = 16.sp, color = TextSecondary
                )
            }
        }

        // Answer slots
        val answerBg = when {
            isCorrect -> SuccessBg
            isWrong   -> ErrorBg
            else      -> BgCardElevated
        }
        val answerBorder = when {
            isCorrect -> Success
            isWrong   -> Error
            else      -> Border
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(answerBg, RoundedCornerShape(12.dp))
                .border(2.dp, answerBorder, RoundedCornerShape(12.dp))
                .padding(Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val displayCount = round.scrambled.size.coerceAtMost(6)
            repeat(displayCount) { slotIdx ->
                val letter = arrangedLetters.getOrNull(slotIdx)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(BgCard, RoundedCornerShape(8.dp))
                        .border(
                            1.dp,
                            if (letter != null) typeColor else Border,
                            RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (letter != null) {
                        Text(
                            letter,
                            fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold,
                            fontSize = 20.sp, color = TextPrimary
                        )
                    } else {
                        Text("_", fontFamily = DMSans, fontSize = 16.sp, color = TextMuted)
                    }
                }
            }
        }

        // Letter tile buttons — used tiles are grayed out and non-tappable
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            round.scrambled.forEachIndexed { idx, letter ->
                val isUsed = idx in usedTileIndices
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            if (isUsed) BgCardElevated else BgCard,
                            RoundedCornerShape(12.dp)
                        )
                        .border(
                            2.dp,
                            if (isUsed) Border else typeColor.copy(alpha = 0.6f),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable(enabled = !isUsed && checked == null) {
                            vm.addLetter(idx, letter)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        letter,
                        fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = if (isUsed) TextMuted.copy(alpha = 0.4f) else typeColor
                    )
                }
            }
        }

        // Control buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            EzhilOutlinedButton(
                label = "⌫ ${if (language == AppLanguage.TAMIL) "நீக்கு" else "Remove"}",
                onClick = { vm.removeLast() },
                modifier = Modifier.weight(1f)
            )
            EzhilButton(
                label = EzhilStrings.get(StringKey.GAMES_CHECK, language),
                onClick = { vm.checkWord() },
                modifier = Modifier.weight(1f),
                enabled = arrangedLetters.isNotEmpty() && checked == null
            )
        }
    }
}

// ── Shared Game Complete Screen ───────────────────────────────────────────────

@Composable
internal fun GameCompleteScreen(
    stars: Int,
    language: AppLanguage,
    xpEarned: Int,
    onReplay: () -> Unit,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            EzhilanWidget(state = EzhilanState.CELEBRATING, size = 100.dp)

            StarRow(filled = stars, total = 3, size = 36.dp)

            Text(
                if (stars >= 2) EzhilStrings.get(StringKey.ACHIEVE_SUPER, language)
                else EzhilStrings.get(StringKey.ACHIEVE_WELL_DONE, language),
                fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold,
                fontSize = 22.sp, color = if (stars >= 2) Gold else Amber
            )
            Text(
                EzhilStrings.get(StringKey.GAMES_ROUND_COMPLETE, language),
                fontFamily = DMSans, fontSize = 14.sp, color = TextMuted
            )

            // Reward row. Children see stars and XP — never an accuracy
            // percentage. A struggling reader should not be handed a number
            // to compare; the counts live in the teacher's reports.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                // XP Earned card
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(BgCard, RoundedCornerShape(12.dp))
                        .border(1.dp, Gold.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(Spacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "XP EARNED",
                        fontFamily = DMSans, fontWeight = FontWeight.Bold,
                        fontSize = 12.sp, color = Gold
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        "+${xpEarned}",
                        fontFamily = DMSans, fontWeight = FontWeight.Bold,
                        fontSize = 28.sp, color = TextPrimary
                    )
                    Text(
                        "அடுத்த புள்ளிகள்",
                        fontFamily = BaloTamizha2, fontSize = 12.sp, color = TextMuted
                    )
                }
            }

            Spacer(Modifier.height(Spacing.xs))

            // Buttons
            EzhilButton(
                label = "🏠 Go Home",
                onClick = onBack,
                modifier = Modifier.fillMaxWidth()
            )
            EzhilOutlinedButton(
                label = "🔄 Play Again",
                onClick = onReplay,
                modifier = Modifier.fillMaxWidth()
            )

        }
    }
}
