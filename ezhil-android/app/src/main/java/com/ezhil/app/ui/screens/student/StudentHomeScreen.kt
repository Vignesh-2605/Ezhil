package com.ezhil.app.ui.screens.student

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Games
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.ezhil.app.ui.navigation.Screen
import com.ezhil.app.ui.screens.auth.SyncViewModel
import com.ezhil.app.ui.strings.AppLanguage
import com.ezhil.app.ui.strings.EzhilStrings
import com.ezhil.app.ui.strings.StringKey
import com.ezhil.app.ui.theme.*
import com.ezhil.app.ui.viewmodel.AppLanguageViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class StudentHomeViewModel @Inject constructor(
    private val db: EzhilDatabase,
    private val prefs: SecurePrefs
) : ViewModel() {

    data class UiState(
        val studentName: String = "",
        val streakDays: Int = 0,
        val totalStars: Int = 0,
        val rank: Int = 0,
        val lessonsCompleted: Int = 0,
        val dayStreak: Int = 0
    )

    val uiState: StateFlow<UiState> = flow {
        val id = prefs.activeStudentId ?: return@flow
        db.studentDao().observeById(id).collect { student ->
            if (student != null) emit(
                UiState(
                    studentName      = student.name,
                    streakDays       = student.streakDays,
                    totalStars       = student.streakDays * 8,
                    rank             = 4,
                    lessonsCompleted = 12,
                    dayStreak        = student.streakDays
                )
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())

    val hasPublishedLessons: StateFlow<Boolean> = db.lessonDao()
        .observePublished()
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
}

// ── Quick-action data ─────────────────────────────────────────────────────────

private data class QuickAction(
    val icon: ImageVector,
    val tamilLabel: String,
    val englishLabel: String,
    val accentColor: Color,
    val route: String
)

private fun quickActions() = listOf(
    QuickAction(Icons.Default.RecordVoiceOver, "படிக்கலாம்",    "Read Aloud",  Cyan,   Screen.ReadAloud.route),
    QuickAction(Icons.Default.Games,           "விளையாடலாம்",   "Word Games",  Amber,  Screen.GamesHub.route),
    QuickAction(Icons.Default.Book,            "என் பாடங்கள்",  "My Lessons",  Purple, Screen.MyLessons.route),
    QuickAction(Icons.Default.Map,             "என் பயணம்",     "My Journey",  RiskLow, Screen.MyJourney.route),
)

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun StudentHomeScreen(
    navController: NavHostController,
    vm: StudentHomeViewModel = hiltViewModel(),
    langVm: AppLanguageViewModel = hiltViewModel(),
    syncVm: SyncViewModel = hiltViewModel()
) {
    val language  by langVm.language.collectAsState()
    val uiState   by vm.uiState.collectAsState()
    val hasLessons by vm.hasPublishedLessons.collectAsState()
    val syncState by syncVm.syncState.collectAsState()

    var selectedTab by remember { mutableStateOf(NavTab.HOME) }

    Scaffold(
        containerColor = BgDark,
        bottomBar = {
            EzhilBottomNavBar(
                selected = selectedTab,
                language = language,
                onSelect = { tab ->
                    selectedTab = tab
                    when (tab) {
                        NavTab.LESSONS     -> navController.navigate(Screen.MyLessons.route)
                        NavTab.LEADERBOARD -> navController.navigate(Screen.Leaderboard.route)
                        NavTab.PROFILE     -> navController.navigate(Screen.StudentProfileView.route)
                        else               -> {}
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BgDark)
                .padding(padding)
        ) {
            SyncStatusBar(syncState = syncState, language = language)

            LazyColumn(
                // Adaptive gutter: 16dp is tight on a sub-360dp handset and
                // leaves content pinned to the left edge on a tablet.
                contentPadding = PaddingValues(
                    horizontal = screenGutter(), vertical = Spacing.md
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {

                // ── Hero greeting card ────────────────────────────────────────
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(Cyan.copy(alpha = 0.15f), BgCard),
                                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                                    end = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                                ),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .border(1.dp, Cyan.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                            .padding(Spacing.lg)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                EzhilText(
                                    key = StringKey.HELLO,
                                    language = language,
                                    style = TextStyle(
                                        fontFamily = DMSans, fontSize = 13.sp, color = Cyan,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                                Spacer(Modifier.height(2.dp))
                                ResponsiveText(
                                    text = uiState.studentName.ifEmpty { EzhilStrings.get(StringKey.STUDENT_ROLE, language) },
                                    style = TextStyle(
                                        fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold,
                                        fontSize = 26.sp, color = TextPrimary
                                    )
                                )
                                Spacer(Modifier.height(Spacing.xs))
                                EzhilText(
                                    key = StringKey.LEARN_HINT,
                                    language = language,
                                    style = TextStyle(
                                        fontFamily = DMSans, fontSize = 12.sp, color = TextSecondary
                                    )
                                )

                                if (uiState.streakDays > 0) {
                                    Spacer(Modifier.height(Spacing.sm))
                                    Row(
                                        modifier = Modifier
                                            .background(AmberDim, RoundedCornerShape(20.dp))
                                            .border(1.dp, Amber.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                                            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                                    ) {
                                        Text("🔥", fontSize = 14.sp)
                                        val streakLabel = EzhilStrings.get(StringKey.STREAK, language)
                                        val daysLabel = EzhilStrings.get(StringKey.STREAK_DAYS, language)
                                        ResponsiveText(
                                            text = "${uiState.streakDays} $daysLabel $streakLabel",
                                            style = TextStyle(
                                                fontFamily = DMSans, fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp, color = Amber
                                            )
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(Spacing.md))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                EzhilanWidget(
                                    state = if (uiState.streakDays >= 3) EzhilanState.CELEBRATING else EzhilanState.IDLE,
                                    size  = 72.dp
                                )
                                Spacer(Modifier.height(Spacing.xs))
                                LanguageToggle(current = language, onToggle = { langVm.toggle() })
                            }
                        }
                    }
                }

                // ── Stats row ─────────────────────────────────────────────────
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        EzhilStatChip(
                            label = EzhilStrings.get(StringKey.STARS, language),
                            value = "${uiState.totalStars}",
                            color = Gold
                        )
                        EzhilStatChip(
                            label = EzhilStrings.get(StringKey.LESSONS, language),
                            value = "${uiState.lessonsCompleted}",
                            color = Cyan
                        )
                        EzhilStatChip(
                            label = EzhilStrings.get(StringKey.STREAK, language),
                            value = "${uiState.streakDays}🔥",
                            color = Amber
                        )
                    }
                }

                // ── Daily goal ────────────────────────────────────────────────
                if (hasLessons) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BgCard, RoundedCornerShape(20.dp))
                                .border(1.dp, Purple.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                                .padding(Spacing.md),
                            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            EzhilSectionLabel(
                                EzhilStrings.get(StringKey.DAILY_GOAL, language),
                                color = Purple
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    EzhilText(
                                        key = StringKey.NEW_STORY,
                                        language = language,
                                        style = TextStyle(
                                            fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp, color = TextPrimary
                                        )
                                    )
                                    ResponsiveText(
                                        text = "\"யானையும் எறும்பும்\" / The Elephant and the Ant",
                                        style = TextStyle(
                                            fontFamily = DMSans, fontSize = 12.sp, color = TextMuted
                                        )
                                    )
                                }
                                Spacer(Modifier.width(Spacing.sm))
                                EzhilButton(
                                    label = EzhilStrings.get(StringKey.START, language),
                                    onClick = { navController.navigate(Screen.ReadAloud.route) },
                                    modifier = Modifier.width(100.dp)
                                )
                            }
                        }
                    }
                }

                // ── Quick actions 2×2 grid ────────────────────────────────────
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        EzhilSectionLabel(
                            EzhilStrings.get(StringKey.ACTIVITIES, language)
                        )
                        Spacer(Modifier.height(2.dp))
                        val actions = quickActions()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            QuickActionCard(actions[0], language, Modifier.weight(1f)) {
                                navController.navigate(actions[0].route)
                            }
                            QuickActionCard(actions[1], language, Modifier.weight(1f)) {
                                navController.navigate(actions[1].route)
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                        ) {
                            QuickActionCard(actions[2], language, Modifier.weight(1f)) {
                                navController.navigate(actions[2].route)
                            }
                            QuickActionCard(actions[3], language, Modifier.weight(1f)) {
                                navController.navigate(actions[3].route)
                            }
                        }
                    }
                }

                // ── Recent activity placeholder ───────────────────────────────
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        EzhilSectionLabel(
                            EzhilStrings.get(StringKey.RECENT_ACTIVITY, language)
                        )
                        Spacer(Modifier.height(2.dp))
                        RecentActivityRow(
                            emoji = "⭐",
                            tamilTitle = "சொற்கள் பயிற்சி",
                            englishTitle = "Vocabulary Mastered",
                            detail = if (language == AppLanguage.TAMIL) "5 சொற்கள்" else "5 words",
                            time = "2 min"
                        )
                        RecentActivityRow(
                            emoji = "📖",
                            tamilTitle = "கவிதை வாசிப்பு",
                            englishTitle = "Poetry Reading",
                            detail = if (language == AppLanguage.TAMIL) "முடிந்தது" else "Completed",
                            time = "1 hr"
                        )
                    }
                }

                item { Spacer(Modifier.height(Spacing.md)) }
            }
        }
    }
}

// ── QuickActionCard ───────────────────────────────────────────────────────────

@Composable
private fun QuickActionCard(
    action: QuickAction,
    language: AppLanguage,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .background(BgCard, RoundedCornerShape(20.dp))
            .border(1.dp, action.accentColor.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(Spacing.md)
            .aspectRatio(1f),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(action.accentColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                action.icon,
                contentDescription = action.englishLabel,
                tint = action.accentColor,
                modifier = Modifier.size(24.dp)
            )
        }
        Column {
            Text(
                if (language == AppLanguage.TAMIL) action.tamilLabel else action.englishLabel,
                fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold,
                fontSize = 14.sp, color = TextPrimary
            )
            Text(
                if (language == AppLanguage.TAMIL) action.englishLabel else action.tamilLabel,
                fontFamily = DMSans, fontSize = 12.sp, color = TextMuted
            )
        }
    }
}

// ── RecentActivityRow ─────────────────────────────────────────────────────────

@Composable
private fun RecentActivityRow(
    emoji: String,
    tamilTitle: String,
    englishTitle: String,
    detail: String,
    time: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgCard, RoundedCornerShape(14.dp))
            .border(1.dp, Border, RoundedCornerShape(14.dp))
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).background(BgCardElevated, CircleShape),
            contentAlignment = Alignment.Center
        ) { Text(emoji, fontSize = 18.sp) }
        Spacer(Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(tamilTitle, fontFamily = BaloTamizha2, fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp, color = TextPrimary)
            Text(englishTitle, fontFamily = DMSans, fontSize = 12.sp, color = TextMuted)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(detail, fontFamily = DMSans, fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp, color = Cyan)
            Text(time, fontFamily = DMSans, fontSize = 12.sp, color = TextMuted)
        }
    }
}
