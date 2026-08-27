package com.ezhil.app.ui.screens.student

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.ezhil.app.ui.components.*
import com.ezhil.app.ui.navigation.Screen
import com.ezhil.app.ui.strings.AppLanguage
import com.ezhil.app.ui.strings.EzhilStrings
import com.ezhil.app.ui.strings.StringKey
import com.ezhil.app.ui.theme.*
import com.ezhil.app.ui.viewmodel.AppLanguageViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class MyJourneyViewModel @Inject constructor(
    private val db: EzhilDatabase,
    private val prefs: SecurePrefs
) : ViewModel() {

    data class JourneyData(
        val streakDays: Int = 0,
        val lessonsCompleted: Int = 0,
        val gamesPlayed: Int = 0,
        val totalStars: Int = 0,
        val activeDays: Set<LocalDate> = emptySet()
    )

    val data: StateFlow<JourneyData> = flow {
        val studentId = prefs.activeStudentId ?: return@flow
        combine(
            db.lessonProgressDao().observeByStudent(studentId),
            db.gameSessionDao().observeByStudent(studentId),
            db.studentDao().observeById(studentId)
        ) { progress, sessions, student ->
            val activeDays = sessions
                .mapNotNull { session ->
                    try {
                        Instant.parse(session.playedAt)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                    } catch (e: Exception) { null }
                }
                .toSet()
            JourneyData(
                streakDays       = student?.streakDays ?: 0,
                lessonsCompleted = progress.count { it.completedAt != null },
                gamesPlayed      = sessions.size,
                totalStars       = sessions.sumOf { it.starsEarned },
                activeDays       = activeDays
            )
        }.collect { emit(it) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), JourneyData())
}

private data class JourneyNode(
    val icon: String,
    val tamilLabel: String,
    val englishLabel: String,
    val threshold: Int,
    val accentColor: androidx.compose.ui.graphics.Color
)

private val JOURNEY_NODES = listOf(
    JourneyNode("🌱", "அறிமுகம்",          "Introduction",  0,  RiskLow),
    JourneyNode("📖", "உயிரெழுத்துக்கள்", "Vowels",        2,  Cyan),
    JourneyNode("✏️", "மெய்யெழுத்துக்கள்","Consonants",    5,  Amber),
    JourneyNode("🔤", "வார்த்தைகள்",       "Words",         10, Purple),
    JourneyNode("📝", "வாக்கியங்கள்",      "Sentences",     15, RiskMedium),
    JourneyNode("📚", "கதைகள்",            "Stories",       20, Gold),
)

private val WEEK_DAY_SHORT_EN = listOf("Sun","Mon","Tue","Wed","Thu","Fri","Sat")
private val WEEK_DAY_SHORT_TA = listOf("ஞா","தி","செ","பு","வி","வெ","ச")

@Composable
fun MyJourneyScreen(
    navController: NavHostController,
    vm: MyJourneyViewModel = hiltViewModel(),
    langVm: AppLanguageViewModel = hiltViewModel()
) {
    val language by langVm.language.collectAsState()
    val data     by vm.data.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(BgDark)) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = screenGutter(), vertical = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = TextMuted)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                EzhilText(
                    key = StringKey.MY_JOURNEY,
                    language = language,
                    style = TextStyle(fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold,
                        fontSize = 18.sp, color = TextPrimary)
                )
                Text("MY JOURNEY", fontFamily = DMSans, fontSize = 12.sp, color = TextMuted)
            }
            LanguageToggle(current = language, onToggle = { langVm.toggle() })
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── 7-day calendar strip ──────────────────────────────────────────
            val today = LocalDate.now()
            val weekDates = (6 downTo 0).map { today.minusDays(it.toLong()) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = screenGutter())
                    .background(BgCard, RoundedCornerShape(16.dp))
                    .border(1.dp, Border, RoundedCornerShape(16.dp))
                    .padding(Spacing.md)
            ) {
                EzhilText(
                    key = StringKey.LAST_7_DAYS,
                    language = language,
                    style = TextStyle(fontFamily = DMSans, fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp, color = TextMuted)
                )
                Spacer(Modifier.height(Spacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    weekDates.forEach { date ->
                        val isToday  = date == today
                        val isActive = date in data.activeDays
                        val dayIdx   = date.dayOfWeek.value % 7  // Sun=0..Sat=6
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                if (language == AppLanguage.TAMIL) WEEK_DAY_SHORT_TA[dayIdx]
                                else WEEK_DAY_SHORT_EN[dayIdx],
                                fontFamily = DMSans, fontSize = 12.sp,
                                color = if (isToday) Cyan else TextMuted
                            )
                            Spacer(Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(
                                        when {
                                            isToday  -> CyanDim
                                            isActive -> SuccessBg
                                            else     -> BgCardElevated
                                        },
                                        CircleShape
                                    )
                                    .border(
                                        1.dp,
                                        when {
                                            isToday  -> Cyan
                                            isActive -> Success
                                            else     -> Border
                                        },
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    when {
                                        isToday  -> "●"
                                        isActive -> "✓"
                                        else     -> "${date.dayOfMonth}"
                                    },
                                    fontSize = 12.sp,   // never below the 12sp floor: these are day numbers a child reads
                                    color = when {
                                        isToday  -> Cyan
                                        isActive -> Success
                                        else     -> TextMuted
                                    },
                                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(Spacing.lg))

            // ── Vertical journey path ─────────────────────────────────────────
            JOURNEY_NODES.forEachIndexed { index, node ->
                val completed = data.lessonsCompleted >= node.threshold
                val isCurrent = completed &&
                    (index == JOURNEY_NODES.size - 1 ||
                     data.lessonsCompleted < JOURNEY_NODES[index + 1].threshold)

                val isLeft = index % 2 == 0

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Connector line from previous node
                    if (index > 0) {
                        val prevCompleted = data.lessonsCompleted >= JOURNEY_NODES[index - 1].threshold
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(40.dp)
                                .background(
                                    if (prevCompleted) JOURNEY_NODES[index - 1].accentColor.copy(alpha = 0.5f)
                                    else Border
                                )
                        )
                    }

                    // Node row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = screenGutter()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = if (isLeft) Arrangement.Start else Arrangement.End
                    ) {
                        if (!isLeft) Spacer(Modifier.weight(1f))

                        // Node circle
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .background(
                                    when {
                                        isCurrent -> node.accentColor
                                        completed -> node.accentColor.copy(alpha = 0.25f)
                                        else      -> BgCard
                                    },
                                    CircleShape
                                )
                                .border(2.dp,
                                    if (completed || isCurrent) node.accentColor else Border,
                                    CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                when {
                                    completed && !isCurrent -> "✓"
                                    !completed             -> "🔒"
                                    else                   -> node.icon
                                },
                                fontSize = if (completed && !isCurrent) 20.sp else 22.sp,
                                color = if (completed && !isCurrent) node.accentColor else TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.width(Spacing.md))

                        // Label chip or text
                        if (isCurrent) {
                            Box(
                                modifier = Modifier
                                    .background(node.accentColor, RoundedCornerShape(20.dp))
                                    .padding(horizontal = screenGutter(), vertical = Spacing.xs)
                            ) {
                                Text(
                                    if (language == AppLanguage.TAMIL) node.tamilLabel else node.englishLabel,
                                    color = TextOnCyan, fontFamily = BaloTamizha2,
                                    fontWeight = FontWeight.Bold, fontSize = 14.sp
                                )
                            }
                        } else {
                            Column {
                                Text(
                                    if (language == AppLanguage.TAMIL) node.tamilLabel else node.englishLabel,
                                    color = if (completed) TextSecondary else TextMuted,
                                    fontFamily = BaloTamizha2, fontSize = 14.sp,
                                    fontWeight = if (completed) FontWeight.SemiBold else FontWeight.Normal
                                )
                                if (!completed) {
                                    Text(
                                        "${node.threshold} ${if (language == AppLanguage.TAMIL) "பாடங்கள்" else "lessons"}",
                                        fontFamily = DMSans, fontSize = 12.sp, color = TextMuted
                                    )
                                }
                            }
                        }

                        if (isLeft) Spacer(Modifier.weight(1f))
                    }
                }
            }

            Spacer(Modifier.height(Spacing.lg))

            EzhilOutlinedButton(
                label = EzhilStrings.get(StringKey.ASSESS_HISTORY, language) + " →",
                onClick = { navController.navigate(Screen.AssessmentsHistory.route) },
                modifier = Modifier
                    .padding(horizontal = Spacing.lg)
                    .fillMaxWidth()
            )

            Spacer(Modifier.height(Spacing.xl))
        }

        // Bottom stats bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgCard)
                .border(1.dp, Border)
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🔥", fontSize = 18.sp)
                Spacer(Modifier.width(Spacing.xs))
                Column {
                    val daysLabel = EzhilStrings.get(StringKey.STREAK_DAYS, language)
                    ResponsiveText(
                        text = "${data.streakDays} $daysLabel",
                        style = TextStyle(color = Amber, fontFamily = BaloTamizha2,
                        fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    )
                    EzhilText(key = StringKey.STREAK, language = language, style = TextStyle(color = TextMuted, fontFamily = DMSans, fontSize = 12.sp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("📖", fontSize = 18.sp)
                Spacer(Modifier.width(Spacing.xs))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ResponsiveText(
                        text = "${data.lessonsCompleted}",
                        style = TextStyle(color = Cyan, fontFamily = DMSans, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    )
                    EzhilText(key = StringKey.LESSONS, language = language, style = TextStyle(color = TextMuted, fontFamily = DMSans, fontSize = 12.sp))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⭐", fontSize = 18.sp)
                Spacer(Modifier.width(Spacing.xs))
                Column(horizontalAlignment = Alignment.End) {
                    ResponsiveText(
                        text = "${data.totalStars}",
                        style = TextStyle(color = Gold, fontFamily = DMSans, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    )
                    EzhilText(key = StringKey.STARS, language = language, style = TextStyle(color = TextMuted, fontFamily = DMSans, fontSize = 12.sp))
                }
            }
        }
    }
}
