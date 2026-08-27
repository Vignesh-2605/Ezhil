package com.ezhil.app.ui.screens.teacher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Person
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
import com.ezhil.app.data.local.entity.AssessmentEntity
import com.ezhil.app.data.local.entity.StudentEntity
import com.ezhil.app.ui.components.*
import com.ezhil.app.ui.strings.AppLanguage
import com.ezhil.app.ui.strings.EzhilStrings
import com.ezhil.app.ui.strings.StringKey
import com.ezhil.app.ui.theme.*
import com.ezhil.app.ui.viewmodel.AppLanguageViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StudentDetailViewModel @Inject constructor(
    private val db: EzhilDatabase
) : ViewModel() {

    data class UiState(
        val student: StudentEntity? = null,
        val assessments: List<AssessmentEntity> = emptyList()
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    fun load(studentId: String) {
        viewModelScope.launch {
            combine(
                db.studentDao().observeById(studentId),
                db.assessmentDao().observeByStudent(studentId)
            ) { student, assessments ->
                UiState(student = student, assessments = assessments)
            }.collect { _uiState.value = it }
        }
    }
}

@Composable
fun StudentDetailScreen(
    navController: NavHostController,
    studentId: String,
    vm: StudentDetailViewModel = hiltViewModel(),
    langVm: AppLanguageViewModel = hiltViewModel()
) {
    val language by langVm.language.collectAsState()
    val uiState by vm.uiState.collectAsState()

    LaunchedEffect(studentId) { vm.load(studentId) }

    val student = uiState.student ?: return

    val initial = student.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val avatarColors = listOf(Cyan, Amber, Purple, RiskLow, RiskMedium)
    val avatarColor = avatarColors[student.name.length % avatarColors.size]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
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
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextSecondary
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = student.name,
                    fontFamily = BaloTamizha2,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = TextPrimary
                )
                Text(
                    text = if (language == AppLanguage.TAMIL) "மாணவர் விவரம்" else "Student Detail",
                    fontFamily = DMSans,
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
            LanguageToggle(current = language, onToggle = { langVm.toggle() })
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = Spacing.xl)
        ) {
            // Large avatar hero section
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BgCard)
                        .border(width = 0.dp, color = Border)
                        .padding(Spacing.lg),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .background(avatarColor.copy(alpha = 0.18f), CircleShape)
                            .border(3.dp, avatarColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initial,
                            color = avatarColor,
                            fontFamily = BaloTamizha2,
                            fontWeight = FontWeight.Bold,
                            fontSize = 36.sp
                        )
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        text = student.name,
                        fontFamily = BaloTamizha2,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    RiskBadge(
                        level = student.riskLevel.toRiskLevel(),
                        language = language,
                        size = BadgeSize.LARGE
                    )
                }
            }

            // Stats row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = screenGutter(), vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    StudentStatCard(
                        icon = Icons.Default.Analytics,
                        value = "${student.streakDays}",
                        label = if (language == AppLanguage.TAMIL) "தொடர் நாட்கள்" else "Streak Days",
                        color = Amber,
                        modifier = Modifier.weight(1f)
                    )
                    StudentStatCard(
                        icon = Icons.Default.Person,
                        value = "${uiState.assessments.size}",
                        label = if (language == AppLanguage.TAMIL) "மதிப்பீடுகள்" else "Assessments",
                        color = Cyan,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Assessment history section header
            item {
                Spacer(Modifier.height(Spacing.xs))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = screenGutter(), vertical = Spacing.xs),
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
                        text = if (language == AppLanguage.TAMIL)
                            "கடந்த மதிப்பீடுகள்"
                        else
                            "Recent Assessments",
                        fontFamily = BaloTamizha2,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextPrimary
                    )
                }
            }

            // Empty state for assessments
            if (uiState.assessments.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.xl),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Analytics,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(Spacing.sm))
                            Text(
                                text = EzhilStrings.get(StringKey.NO_ASSESSMENTS, language),
                                fontFamily = DMSans,
                                color = TextMuted,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            } else {
                items(uiState.assessments) { assessment ->
                    AssessmentHistoryCard(
                        assessment = assessment,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = screenGutter(), vertical = Spacing.xs)
                    )
                }
            }
        }
    }
}

@Composable
private fun StudentStatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(BgCard, RoundedCornerShape(14.dp))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(Spacing.xs))
        Text(value, fontFamily = DMSans, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = color)
        Text(label, fontFamily = DMSans, fontSize = 12.sp, color = TextMuted)
    }
}

@Composable
private fun AssessmentHistoryCard(
    assessment: AssessmentEntity,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(BgCard, RoundedCornerShape(14.dp))
            .border(1.dp, Border, RoundedCornerShape(14.dp))
            .padding(Spacing.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = assessment.conductedAt.take(10),
                fontFamily = DMSans,
                fontSize = 12.sp,
                color = TextMuted
            )
            // Risk trend indicator
            val riskColor = when {
                (assessment.phonemeErrorRate ?: 0f) > 0.4f -> RiskHigh
                (assessment.phonemeErrorRate ?: 0f) > 0.2f -> RiskMedium
                else -> RiskLow
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(riskColor, CircleShape)
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            assessment.readingSpeedWpm?.let { wpm ->
                AssessmentPill("${wpm.toInt()} WPM", Cyan)
            }
            assessment.phonemeErrorRate?.let { err ->
                val errColor = if (err > 0.3f) RiskHigh else RiskLow
                AssessmentPill("Err: ${(err * 100).toInt()}%", errColor)
            }
            // Heuristic results are screening prompts, not clinical readings —
            // make that visible to the teacher.
            if (assessment.modelVersion.startsWith("heuristic")) {
                AssessmentPill("Estimate", Amber)
            }
        }
    }
}

@Composable
private fun AssessmentPill(text: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
    ) {
        Text(text, fontFamily = DMSans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = color)
    }
}
