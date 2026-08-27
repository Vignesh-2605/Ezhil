package com.ezhil.app.ui.screens.teacher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
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
import com.ezhil.app.ui.navigation.Screen
import com.ezhil.app.ui.strings.AppLanguage
import com.ezhil.app.ui.strings.EzhilStrings
import com.ezhil.app.ui.strings.StringKey
import com.ezhil.app.ui.theme.*
import com.ezhil.app.ui.viewmodel.AppLanguageViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val db: EzhilDatabase,
    private val prefs: SecurePrefs
) : ViewModel() {

    data class UiState(
        val totalStudents: Int = 0,
        val highRisk: Int = 0,
        val mediumRisk: Int = 0,
        val lowRisk: Int = 0,
        val unscreened: Int = 0,
        val avgReadingWpm: Float = 0f,
        val totalLessons: Int = 0,
        val totalAssessments: Int = 0
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<UiState> = db.studentDao()
        .observeByTeacher(prefs.teacherId ?: "")
        .flatMapLatest { students ->
            val ids = students.map { it.id }
            val assessmentFlow: Flow<List<AssessmentEntity>> =
                if (ids.isEmpty()) flowOf(emptyList())
                else db.assessmentDao().observeByStudentIds(ids)
            combine(
                db.lessonDao().observePublished(),
                assessmentFlow
            ) { lessons, assessments ->
                val wpmValues = assessments.mapNotNull { it.readingSpeedWpm }
                UiState(
                    totalStudents    = students.size,
                    highRisk         = students.count { it.riskLevel == "high" },
                    mediumRisk       = students.count { it.riskLevel == "medium" },
                    lowRisk          = students.count { it.riskLevel == "low" },
                    unscreened       = students.count { it.riskLevel == "unscreened" || it.riskLevel.isBlank() },
                    avgReadingWpm    = if (wpmValues.isEmpty()) 0f else wpmValues.average().toFloat(),
                    totalLessons     = lessons.size,
                    totalAssessments = assessments.size
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())
}

@Composable
fun ReportsScreen(
    navController: NavHostController,
    vm: ReportsViewModel = hiltViewModel(),
    langVm: AppLanguageViewModel = hiltViewModel()
) {
    val language by langVm.language.collectAsState()
    val uiState by vm.uiState.collectAsState()

    Scaffold(
        containerColor = BgDark,
        bottomBar = {
            TeacherBottomNavBar(navController = navController, currentRoute = Screen.Reports.route)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BgDark)
                .padding(padding)
        ) {
            // Standard top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgCard)
                    .border(1.dp, Border)
                    .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { /* root tab, no back */ }) {
                    Icon(Icons.Default.Analytics, contentDescription = null, tint = Amber)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = EzhilStrings.get(StringKey.TEACHER_REPORTS, language),
                        fontFamily = BaloTamizha2,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "CLASS ANALYTICS",
                        fontFamily = DMSans,
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
                LanguageToggle(current = language, onToggle = { langVm.toggle() })
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // Overview section header
                ReportsSectionHeader(
                    label = EzhilStrings.get(StringKey.TEACHER_CLASS_STATUS, language)
                )

                // Stats grid — row 1
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    ReportStatCard(
                        icon    = Icons.Default.People,
                        value   = "${uiState.totalStudents}",
                        label   = EzhilStrings.get(StringKey.TEACHER_STUDENTS, language),
                        color   = Amber,
                        modifier = Modifier.weight(1f)
                    )
                    ReportStatCard(
                        icon    = Icons.Default.Analytics,
                        value   = "${uiState.totalAssessments}",
                        label   = if (language == AppLanguage.TAMIL) "மதிப்பீடுகள்" else "Assessments",
                        color   = Cyan,
                        modifier = Modifier.weight(1f)
                    )
                    ReportStatCard(
                        icon    = Icons.Default.Book,
                        value   = "${uiState.totalLessons}",
                        label   = EzhilStrings.get(StringKey.TEACHER_LESSONS, language),
                        color   = Purple,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Stats grid — row 2
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    ReportStatCard(
                        icon    = Icons.Default.Speed,
                        value   = "${uiState.avgReadingWpm.toInt()} WPM",
                        label   = EzhilStrings.get(StringKey.TEACHER_AVG_READING, language),
                        color   = Gold,
                        modifier = Modifier.weight(1f)
                    )
                    ReportStatCard(
                        icon    = Icons.Default.Analytics,
                        value   = if (uiState.totalStudents == 0) "—"
                                  else "${((uiState.lowRisk.toFloat() / uiState.totalStudents) * 100).toInt()}%",
                        label   = EzhilStrings.get(StringKey.TEACHER_FLUENCY_RATE, language),
                        color   = RiskLow,
                        modifier = Modifier.weight(1f)
                    )
                    ReportStatCard(
                        icon    = Icons.Default.People,
                        value   = "${uiState.highRisk}",
                        label   = EzhilStrings.get(StringKey.TEACHER_NEED_ATTENTION, language),
                        color   = RiskHigh,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Risk breakdown section
                ReportsSectionHeader(
                    label = EzhilStrings.get(StringKey.TEACHER_PROGRESS, language)
                )

                // Visual risk bar
                RiskBreakdownBar(
                    high       = uiState.highRisk,
                    medium     = uiState.mediumRisk,
                    low        = uiState.lowRisk,
                    unscreened = uiState.unscreened,
                    total      = uiState.totalStudents
                )

                // Risk legend rows
                listOf(
                    Triple(RiskHigh,       RiskHighBg,       "${uiState.highRisk} " + EzhilStrings.get(StringKey.TEACHER_AT_RISK, language)),
                    Triple(RiskMedium,     RiskMediumBg,     "${uiState.mediumRisk} " + EzhilStrings.get(StringKey.TEACHER_CAUTION, language)),
                    Triple(RiskLow,        RiskLowBg,        "${uiState.lowRisk} " + EzhilStrings.get(StringKey.TEACHER_STABLE, language)),
                    Triple(RiskUnscreened, RiskUnscreenedBg, "${uiState.unscreened} " + if (language == AppLanguage.TAMIL) "சோதிக்கப்படவில்லை" else "Unscreened"),
                ).forEach { (color, bg, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bg, RoundedCornerShape(12.dp))
                            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(horizontal = screenGutter(), vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .background(color, RoundedCornerShape(5.dp))
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            text = label,
                            fontFamily = DMSans,
                            fontSize = 14.sp,
                            color = color,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Empty state when no students
                if (uiState.totalStudents == 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = Spacing.lg),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Analytics,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(Modifier.height(Spacing.sm))
                            Text(
                                text = if (language == AppLanguage.TAMIL)
                                    "இன்னும் தரவு இல்லை"
                                else
                                    "No data yet",
                                fontFamily = DMSans,
                                color = TextMuted,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                // Quick actions section
                ReportsSectionHeader(
                    label = if (language == AppLanguage.TAMIL) "விரைவு செயல்கள்" else "Quick Actions"
                )

                EzhilButton(
                    label = EzhilStrings.get(StringKey.TEACHER_RUN_ASSESSMENT, language),
                    onClick = { navController.navigate(Screen.StudentProfile.route) },
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = Amber,
                    textColor = TextOnAmber
                )

                EzhilOutlinedButton(
                    label = "${EzhilStrings.get(StringKey.TEACHER_VIEW_ALL, language)} ›",
                    onClick = { navController.navigate(Screen.RosterManagement.route) },
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = Amber.copy(alpha = 0.4f),
                    textColor = Amber
                )

                Spacer(Modifier.height(Spacing.xl))
            }
        }
    }
}

@Composable
private fun ReportsSectionHeader(label: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(20.dp)
                .background(Amber, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = label,
            fontFamily = BaloTamizha2,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = TextPrimary
        )
    }
}

@Composable
private fun ReportStatCard(
    icon: ImageVector,
    value: String,
    label: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(BgCard, RoundedCornerShape(14.dp))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(Spacing.xs))
        Text(value, fontFamily = DMSans, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = color)
        Text(label, fontFamily = DMSans, fontSize = 12.sp, color = TextMuted)
    }
}

@Composable
private fun RiskBreakdownBar(high: Int, medium: Int, low: Int, unscreened: Int, total: Int) {
    if (total == 0) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
            .background(Border, RoundedCornerShape(7.dp))
    ) {
        val segments = listOf(
            high.toFloat() / total       to RiskHigh,
            medium.toFloat() / total     to RiskMedium,
            low.toFloat() / total        to RiskLow,
            unscreened.toFloat() / total to RiskUnscreened
        )
        segments.forEach { (fraction, color) ->
            if (fraction > 0) {
                Box(
                    modifier = Modifier
                        .weight(fraction)
                        .fillMaxHeight()
                        .background(color, RoundedCornerShape(7.dp))
                )
            }
        }
    }
}
