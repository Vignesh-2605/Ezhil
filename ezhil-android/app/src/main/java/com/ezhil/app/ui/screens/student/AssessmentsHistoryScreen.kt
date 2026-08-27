package com.ezhil.app.ui.screens.student

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
import com.ezhil.app.ui.components.*
import com.ezhil.app.ui.strings.AppLanguage
import com.ezhil.app.ui.strings.EzhilStrings
import com.ezhil.app.ui.strings.StringKey
import com.ezhil.app.ui.theme.*
import com.ezhil.app.ui.viewmodel.AppLanguageViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class AssessmentsHistoryViewModel @Inject constructor(
    private val db: EzhilDatabase,
    private val prefs: SecurePrefs
) : ViewModel() {

    // Pedagogical rule: students never see risk levels or error rates —
    // this screen only surfaces effort (sessions, practice time), never scores.
    data class UiState(
        val assessments: List<AssessmentEntity> = emptyList(),
        val practiceMinutes: Int = 0,
        val thisMonthCount: Int = 0
    )

    val uiState: StateFlow<UiState> = flow {
        val studentId = prefs.activeStudentId ?: return@flow
        db.assessmentDao().observeByStudent(studentId).collect { list ->
            val minutes = (list.sumOf { (it.audioDurationMs ?: 0).toLong() } / 60_000L)
                .toInt().coerceAtLeast(if (list.isEmpty()) 0 else 1)
            val monthPrefix = java.time.YearMonth.now().toString()  // "2026-07"
            val thisMonth = list.count { it.conductedAt.startsWith(monthPrefix) }
            emit(UiState(assessments = list, practiceMinutes = minutes, thisMonthCount = thisMonth))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())
}

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun AssessmentsHistoryScreen(
    navController: NavHostController,
    vm: AssessmentsHistoryViewModel = hiltViewModel(),
    langVm: AppLanguageViewModel = hiltViewModel()
) {
    val language by langVm.language.collectAsState()
    val uiState by vm.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
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
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextSecondary
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    EzhilStrings.get(StringKey.MY_ASSESSMENTS_TITLE, language),
                    fontFamily = BaloTamizha2,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = TextPrimary
                )
                Text(
                    "MY ASSESSMENTS",
                    fontFamily = DMSans,
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
            LanguageToggle(current = language, onToggle = { langVm.toggle() })
        }

        if (uiState.assessments.isEmpty()) {
            // ── Beautiful empty state ─────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    modifier = Modifier.padding(horizontal = screenGutter())
                ) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .background(CyanDim, CircleShape)
                            .border(2.dp, Cyan.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("📋", fontSize = 44.sp)
                    }
                    Text(
                        if (language == AppLanguage.TAMIL) "இன்னும் மதிப்பீடுகள் இல்லை" else "No Assessments Yet",
                        fontFamily = BaloTamizha2,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        if (language == AppLanguage.TAMIL)
                            "உரக்கப் படிக்க ஆரம்பித்து உங்கள் முன்னேற்றத்தைக் கண்காணிக்கவும்."
                        else
                            "Start a Read Aloud session to track your reading progress.",
                        fontFamily = if (language == AppLanguage.TAMIL) NotoSansTamil else DMSans,
                        fontSize = 14.sp,
                        color = TextMuted,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )
                    if (language == AppLanguage.TAMIL) {
                        Text(
                            "Start a Read Aloud session to track your reading progress.",
                            fontFamily = DMSans,
                            fontSize = 12.sp,
                            color = TextMuted.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    EzhilButton(
                        label = if (language == AppLanguage.TAMIL) "🎤 படிக்க ஆரம்பி" else "🎤 Start Reading",
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else {
            // ── Stats summary ─────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = screenGutter(), vertical = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                AssessmentStatCard(
                    icon = "📚",
                    value = "${uiState.assessments.size}",
                    label = if (language == AppLanguage.TAMIL) "மொத்தம்" else "Total",
                    sublabel = EzhilStrings.get(StringKey.MY_ASSESSMENTS_TOTAL, language),
                    color = Cyan,
                    modifier = Modifier.weight(1f)
                )
                AssessmentStatCard(
                    icon = "⏱",
                    value = "${uiState.practiceMinutes} min",
                    label = if (language == AppLanguage.TAMIL) "பயிற்சி நேரம்" else "Practice Time",
                    sublabel = if (language == AppLanguage.TAMIL) "Practice time" else "மொத்த பயிற்சி",
                    color = Amber,
                    modifier = Modifier.weight(1f)
                )
                AssessmentStatCard(
                    icon = "🗓",
                    value = "${uiState.thisMonthCount}",
                    label = if (language == AppLanguage.TAMIL) "இந்த மாதம்" else "This Month",
                    sublabel = if (language == AppLanguage.TAMIL) "This month" else "இந்த மாதம்",
                    color = Success,
                    modifier = Modifier.weight(1f)
                )
            }

            // ── Trend indicator ───────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = screenGutter())
                    .padding(bottom = Spacing.sm)
                    .background(BgCard, RoundedCornerShape(14.dp))
                    .border(1.dp, Border, RoundedCornerShape(14.dp))
                    .padding(horizontal = screenGutter(), vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                // Effort-based encouragement only — never derived from risk scores.
                val manySessions = uiState.assessments.size >= 3
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(SuccessBg, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (manySessions) "★" else "↑", color = Success, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Column {
                    Text(
                        if (language == AppLanguage.TAMIL) "தொடர்ந்து படி!" else "Keep Reading!",
                        fontFamily = if (language == AppLanguage.TAMIL) NotoSansTamil else DMSans,
                        fontSize = 13.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (manySessions)
                            (if (language == AppLanguage.TAMIL) "அருமையான பயிற்சி! / Great practice!" else "Great practice!")
                        else
                            (if (language == AppLanguage.TAMIL) "ஒவ்வொரு நாளும் படிக்கலாம் / Read a little every day!" else "Read a little every day!"),
                        fontFamily = DMSans,
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
            }

            // ── Timeline list ─────────────────────────────────────────────────
            Text(
                EzhilStrings.get(StringKey.MY_ASSESSMENTS_RECENT, language),
                fontFamily = BaloTamizha2,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = TextPrimary,
                modifier = Modifier
                    .padding(horizontal = screenGutter())
                    .padding(bottom = Spacing.xs)
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = Spacing.md,
                    end = Spacing.md,
                    bottom = Spacing.lg
                ),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                itemsIndexed(uiState.assessments) { index, assessment ->
                    AssessmentTimelineRow(
                        assessment = assessment,
                        index = index,
                        isLast = index == uiState.assessments.lastIndex,
                        language = language
                    )
                }
            }
        }
    }
}

// ── Stat Card ─────────────────────────────────────────────────────────────────

@Composable
private fun AssessmentStatCard(
    icon: String,
    value: String,
    label: String,
    sublabel: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(BgCard, RoundedCornerShape(20.dp))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .padding(vertical = Spacing.md, horizontal = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(color.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, fontSize = 16.sp)
        }
        Text(
            value,
            fontFamily = DMSans,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = color
        )
        Text(
            label,
            fontFamily = if (label.any { it.code > 0x0BFF }) NotoSansTamil else DMSans,
            fontSize = 12.sp,
            color = TextSecondary,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Text(
            sublabel,
            fontFamily = DMSans,
            fontSize = 12.sp,
            color = TextMuted,
            textAlign = TextAlign.Center
        )
    }
}

// ── Timeline Row ──────────────────────────────────────────────────────────────

@Composable
private fun AssessmentTimelineRow(
    assessment: AssessmentEntity,
    index: Int,
    isLast: Boolean,
    language: AppLanguage
) {
    // Students never see risk levels — every completed session is a win.
    // Parse date portion
    val dateDisplay = runCatching { assessment.conductedAt.take(10) }.getOrElse { "—" }
    val monthDay = runCatching {
        val parts = dateDisplay.split("-")
        "${parts[2]}/${parts[1]}"
    }.getOrElse { dateDisplay }

    Row(modifier = Modifier.fillMaxWidth()) {
        // Timeline spine
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(40.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(Cyan, CircleShape)
                    .border(2.dp, BgDark, CircleShape)
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(Spacing.md + 72.dp) // enough to span the card height
                        .background(Border)
                )
            }
        }

        Spacer(Modifier.width(Spacing.sm))

        // Card
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else Spacing.sm)
                .background(BgCard, RoundedCornerShape(14.dp))
                .border(1.dp, Border, RoundedCornerShape(14.dp))
                .padding(Spacing.md)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "#${index + 1} · $dateDisplay",
                        fontFamily = DMSans,
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                    Text(
                        if (language == AppLanguage.TAMIL) "மதிப்பீடு" else "Assessment",
                        fontFamily = if (language == AppLanguage.TAMIL) NotoSansTamil else DMSans,
                        fontSize = 13.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                // Completion badge — encouragement only, never risk framing
                Box(
                    modifier = Modifier
                        .background(SuccessBg, RoundedCornerShape(8.dp))
                        .border(1.dp, Success.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                ) {
                    Text(
                        if (language == AppLanguage.TAMIL) "✓ முடிந்தது" else "✓ DONE",
                        fontFamily = DMSans,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Success
                    )
                }
            }

            Spacer(Modifier.height(Spacing.xs))

            // Effort chips: how long the child read, nothing evaluative
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                modifier = Modifier.fillMaxWidth()
            ) {
                assessment.audioDurationMs?.let { ms ->
                    AssessmentChip(
                        label = if (language == AppLanguage.TAMIL)
                            "🎤 ${ms / 1000} வினாடிகள்" else "🎤 ${ms / 1000}s reading",
                        color = Cyan
                    )
                }
                AssessmentChip(
                    label = if (language == AppLanguage.TAMIL) "⭐ நன்று!" else "⭐ Nice work!",
                    color = Amber
                )
            }
        }
    }
}

@Composable
private fun AssessmentChip(label: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Text(label, fontFamily = DMSans, fontSize = 12.sp, color = color)
    }
}
