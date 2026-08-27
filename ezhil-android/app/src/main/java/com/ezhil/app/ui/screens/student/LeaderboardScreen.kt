package com.ezhil.app.ui.screens.student

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.ezhil.app.ui.components.*
import com.ezhil.app.ui.strings.AppLanguage
import com.ezhil.app.ui.theme.*
import com.ezhil.app.ui.viewmodel.AppLanguageViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class LeaderboardViewModel @Inject constructor(
    private val db: EzhilDatabase,
    private val prefs: SecurePrefs
) : ViewModel() {

    data class GameStat(
        val emoji: String,
        val label: String,
        val labelTa: String,
        val sessionsPlayed: Int,
        val totalStars: Int,
        val bestStars: Int,
        val color: Color
    )

    data class Stats(
        val studentName: String = "",
        val totalGames: Int = 0,
        val totalStars: Int = 0,
        val bestStreak: Int = 0,
        val rank: String = "Beginner",
        val rankTa: String = "தொடக்கநிலை",
        val rankColor: Color = Border,
        val starsToNextRank: Int = 10,
        val nextRank: String = "Learner",
        val perGame: List<GameStat> = emptyList()
    )

    private val gameConfig = listOf(
        Triple("match_sound",        "🔊", Pair("Match Sound",   "ஒலி பொருத்து")),
        Triple("spot_letter",        "🔍", Pair("Spot Letter",   "எழுத்து கண்டுபிடி")),
        Triple("build_word",         "🔤", Pair("Build Word",    "சொல் கட்டு")),
        Triple("phonics_quiz",       "🎮", Pair("Phonics Quiz",  "ஒலியியல் வினா")),
        Triple("comprehension_quiz", "💡", Pair("Quiz",          "வினாடி வினா")),
    )

    private val rankTiers = listOf(
        Triple(0,  "Beginner",  Pair("தொடக்கநிலை", Border)),
        Triple(10, "Learner",   Pair("கற்பவர்",     Cyan)),
        Triple(25, "Explorer",  Pair("ஆராய்வாளர்", Amber)),
        Triple(50, "Expert",    Pair("நிபுணர்",     Purple)),
        Triple(100,"Champion",  Pair("சாம்பியன்",   Gold)),
    )

    val stats: StateFlow<Stats> = flow {
        val studentId = prefs.activeStudentId ?: return@flow
        combine(
            db.gameSessionDao().observeByStudent(studentId),
            db.studentDao().observeById(studentId)
        ) { sessions, student ->
            val totalStars = sessions.sumOf { it.starsEarned }

            val currentTier = rankTiers.lastOrNull { it.first <= totalStars } ?: rankTiers.first()
            val nextTier    = rankTiers.firstOrNull { it.first > totalStars }
            val starsToNext = nextTier?.first?.minus(totalStars) ?: 0

            val perGame = gameConfig.mapNotNull { (type, emoji, labels) ->
                val typeSessions = sessions.filter { it.gameType == type }
                if (typeSessions.isEmpty()) return@mapNotNull null
                GameStat(
                    emoji          = emoji,
                    label          = labels.first,
                    labelTa        = labels.second,
                    sessionsPlayed = typeSessions.size,
                    totalStars     = typeSessions.sumOf { it.starsEarned },
                    bestStars      = typeSessions.maxOf { it.starsEarned },
                    color          = when (type) {
                        "match_sound"        -> Cyan
                        "spot_letter"        -> Amber
                        "build_word"         -> Purple
                        "phonics_quiz"       -> RiskLow
                        else                 -> Gold
                    }
                )
            }.sortedByDescending { it.totalStars }

            Stats(
                studentName    = student?.name ?: "",
                totalGames     = sessions.size,
                totalStars     = totalStars,
                bestStreak     = student?.streakDays ?: 0,
                rank           = currentTier.second,
                rankTa         = currentTier.third.first,
                rankColor      = currentTier.third.second,
                starsToNextRank = starsToNext,
                nextRank       = nextTier?.second ?: "Champion",
                perGame        = perGame
            )
        }.collect { emit(it) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Stats())
}

@Composable
fun LeaderboardScreen(
    navController: NavHostController,
    vm: LeaderboardViewModel = hiltViewModel(),
    langVm: AppLanguageViewModel = hiltViewModel()
) {
    val language by langVm.language.collectAsState()
    val stats    by vm.stats.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(BgDark)) {
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
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextSecondary)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (language == AppLanguage.TAMIL) "தரவரிசை" else "Leaderboard",
                    fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold,
                    fontSize = 18.sp, color = TextPrimary
                )
                Text("RANKINGS", fontFamily = DMSans, fontSize = 12.sp, color = TextMuted)
            }
            LanguageToggle(current = language, onToggle = { langVm.toggle() })
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // Player hero card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgCard, RoundedCornerShape(20.dp))
                    .border(1.dp, stats.rankColor.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    .padding(Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(stats.rankColor.copy(alpha = 0.15f), CircleShape)
                        .border(2.dp, stats.rankColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stats.studentName.take(1).uppercase().ifEmpty { "?" },
                        fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold,
                        fontSize = 32.sp, color = stats.rankColor
                    )
                }

                Text(stats.studentName.ifEmpty { "—" },
                    fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold,
                    fontSize = 20.sp, color = TextPrimary)

                // Rank badge
                Box(
                    modifier = Modifier
                        .background(stats.rankColor.copy(alpha = 0.18f), RoundedCornerShape(20.dp))
                        .border(1.dp, stats.rankColor.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                        .padding(horizontal = screenGutter(), vertical = Spacing.xs)
                ) {
                    Text(
                        if (language == AppLanguage.TAMIL) "🏅 ${stats.rankTa}" else "🏅 ${stats.rank}",
                        fontFamily = DMSans, fontWeight = FontWeight.Bold,
                        fontSize = 12.sp, color = stats.rankColor
                    )
                }

                Spacer(Modifier.height(Spacing.xs))

                // Stat pills row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    LeaderStatPill("⭐", "${stats.totalStars}",
                        if (language == AppLanguage.TAMIL) "நட்சத்திரங்கள்" else "Stars", Gold)
                    LeaderStatPill("🎮", "${stats.totalGames}",
                        if (language == AppLanguage.TAMIL) "விளையாட்டுகள்" else "Games", Amber)
                    LeaderStatPill("🔥", "${stats.bestStreak}",
                        if (language == AppLanguage.TAMIL) "தொடர்ச்சி" else "Streak", RiskMedium)
                }

                // Progress to next rank
                if (stats.starsToNextRank > 0) {
                    HorizontalDivider(color = Border, thickness = 0.5.dp)
                    Text(
                        if (language == AppLanguage.TAMIL)
                            "${stats.starsToNextRank} நட்சத்திரங்கள் — ${stats.nextRank} நிலைக்கு"
                        else
                            "${stats.starsToNextRank} more stars to reach ${stats.nextRank}",
                        fontFamily = DMSans, fontSize = 12.sp, color = TextMuted,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Per-game breakdown
            if (stats.perGame.isNotEmpty()) {
                Text(
                    if (language == AppLanguage.TAMIL) "விளையாட்டு வகை சாதனைகள்" else "Game Breakdown",
                    fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold,
                    fontSize = 16.sp, color = TextPrimary
                )

                stats.perGame.forEach { game ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BgCard, RoundedCornerShape(14.dp))
                            .border(1.dp, game.color.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                            .padding(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(game.color.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                                .border(1.dp, game.color.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(game.emoji, fontSize = 22.sp)
                        }
                        Spacer(Modifier.width(Spacing.md))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (language == AppLanguage.TAMIL) game.labelTa else game.label,
                                fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold,
                                fontSize = 16.sp, color = TextPrimary
                            )
                            Text(
                                "${game.sessionsPlayed} ${if (language == AppLanguage.TAMIL) "சுற்றுகள்" else "rounds played"}",
                                fontFamily = DMSans, fontSize = 12.sp, color = TextMuted
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Best ", fontFamily = DMSans, fontSize = 12.sp, color = TextMuted)
                                repeat(game.bestStars) { Text("⭐", fontSize = 12.sp) }
                                repeat((3 - game.bestStars).coerceAtLeast(0)) {
                                    Text("☆", fontSize = 12.sp, color = GoldDim)
                                }
                            }
                            Text(
                                "${game.totalStars} ⭐ total",
                                fontFamily = DMSans, fontWeight = FontWeight.Bold,
                                fontSize = 12.sp, color = game.color
                            )
                        }
                    }
                }
            }

            // Class ranking info card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgCardElevated, RoundedCornerShape(14.dp))
                    .border(1.dp, Border, RoundedCornerShape(14.dp))
                    .padding(Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Text("🏆", fontSize = 32.sp)
                Text(
                    if (language == AppLanguage.TAMIL) "வகுப்பு தரவரிசை" else "Class Rankings",
                    fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold,
                    fontSize = 16.sp, color = TextPrimary
                )
                Text(
                    if (language == AppLanguage.TAMIL)
                        "உன் ஆசிரியர் வழியாக வகுப்பு தரவரிசை காணலாம்"
                    else
                        "Full class rankings are visible through your teacher's dashboard",
                    fontFamily = DMSans, fontSize = 12.sp, color = TextMuted,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

@Composable
private fun LeaderStatPill(icon: String, value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 20.sp)
        Text(value, fontFamily = DMSans, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = color)
        Text(label, fontFamily = DMSans, fontSize = 12.sp, color = TextMuted, textAlign = TextAlign.Center)
    }
}
