package com.ezhil.app.ui.screens.teacher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.ezhil.app.data.local.EzhilDatabase
import com.ezhil.app.data.local.SecurePrefs
import com.ezhil.app.data.local.entity.StudentEntity
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
class TeacherDashboardViewModel @Inject constructor(
    private val db: EzhilDatabase,
    private val prefs: SecurePrefs
) : ViewModel() {

    data class UiState(
        val teacherName: String = "",
        val students: List<StudentEntity> = emptyList()
    )

    val uiState: StateFlow<UiState> = flow {
        val tid = prefs.teacherId ?: return@flow
        combine(
            db.teacherDao().observeById(tid),
            db.studentDao().observeByTeacher(tid)
        ) { teacher, students ->
            UiState(teacherName = teacher?.name ?: "", students = students)
        }.collect { emit(it) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())
}

@Composable
fun TeacherDashboardScreen(
    navController: NavHostController,
    vm: TeacherDashboardViewModel = hiltViewModel(),
    langVm: AppLanguageViewModel = hiltViewModel(),
    syncVm: SyncViewModel = hiltViewModel()
) {
    val language by langVm.language.collectAsState()
    val uiState by vm.uiState.collectAsState()
    val syncState by syncVm.syncState.collectAsState()

    val high       = uiState.students.count { it.riskLevel == "high" }
    val medium     = uiState.students.count { it.riskLevel == "medium" }
    val low        = uiState.students.count { it.riskLevel == "low" }
    val unscreened = uiState.students.count { it.riskLevel == "unscreened" || it.riskLevel.isBlank() }

    Scaffold(
        containerColor = BgDark,
        bottomBar = {
            TeacherBottomNavBar(navController = navController, currentRoute = Screen.TeacherDashboard.route)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BgDark)
                .padding(padding)
        ) {
            SyncStatusBar(syncState = syncState, language = language)

            // Top bar — standard
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgCard)
                    .border(1.dp, Border)
                    .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { /* no back on dashboard */ }) {
                    Icon(Icons.Default.School, contentDescription = null, tint = Amber)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    EzhilText(
                        key = StringKey.DASHBOARD_TITLE,
                        language = language,
                        style = TextStyle(
                            fontFamily = BaloTamizha2,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = TextPrimary
                        )
                    )
                    Text(
                        text = "TEACHER MODE",
                        fontFamily = DMSans,
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
                LanguageToggle(current = language, onToggle = { langVm.toggle() })
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = Spacing.md)
            ) {
                // Hero card with amber gradient
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.md)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(AmberDim, BgCard)
                                )
                            )
                            .border(1.dp, Amber.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                            .padding(Spacing.lg)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Teacher avatar
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(Amber.copy(alpha = 0.2f), CircleShape)
                                    .border(2.dp, Amber, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = Amber,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(Modifier.width(Spacing.md))
                            Column {
                                ResponsiveText(
                                    text = "${EzhilStrings.get(StringKey.GREETING, language)}, ${uiState.teacherName}!",
                                    style = TextStyle(
                                        fontFamily = BaloTamizha2,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp,
                                        color = TextPrimary
                                    )
                                )
                                // The class size lives here rather than in its
                                // own tile. It is context for the greeting, not
                                // a number a teacher acts on, and giving it
                                // equal weight to the risk counts was part of
                                // what made this screen read as seven competing
                                // figures.
                                Text(
                                    text = "${EzhilStrings.get(StringKey.YOUR_CLASSROOM, language)} · " +
                                        "${uiState.students.size} " +
                                        EzhilStrings.get(StringKey.STUDENTS_COUNT, language),
                                    fontFamily = DMSans,
                                    fontSize = 13.sp,
                                    color = Amber
                                )
                            }
                        }
                    }
                }

                // One row of four, not three-over-four.
                //
                // There used to be a second row above this one carrying total
                // students, high risk and low risk — so high and low were each
                // rendered twice, with the same numbers, in two different card
                // styles, stacked on top of each other. Seven tiles in five
                // colours, and two of the figures were duplicates. The class
                // size has moved into the greeting card; what is left is the
                // risk breakdown, which is the thing a teacher acts on.
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .padding(horizontal = screenGutter(), vertical = Spacing.sm),
                        // IntrinsicSize.Min measures the tallest child and
                        // gives every card that height. Alignment alone only
                        // centres them; a minimum height only sets a floor.
                        // "Medium Risk" wraps to two lines where "Low Risk"
                        // does not, so without this the row stays ragged.
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        RiskSummaryCard(
                            count     = high,
                            color     = RiskHigh,
                            bg        = RiskHighBg,
                            label     = EzhilStrings.get(StringKey.RISK_HIGH, language),
                            modifier  = Modifier.weight(1f)
                        )
                        RiskSummaryCard(
                            count     = medium,
                            color     = RiskMedium,
                            bg        = RiskMediumBg,
                            label     = EzhilStrings.get(StringKey.RISK_MEDIUM, language),
                            modifier  = Modifier.weight(1f)
                        )
                        RiskSummaryCard(
                            count     = low,
                            color     = RiskLow,
                            bg        = RiskLowBg,
                            label     = EzhilStrings.get(StringKey.RISK_LOW, language),
                            modifier  = Modifier.weight(1f)
                        )
                        RiskSummaryCard(
                            count     = unscreened,
                            color     = RiskUnscreened,
                            bg        = RiskUnscreenedBg,
                            label     = EzhilStrings.get(StringKey.RISK_NONE, language),
                            modifier  = Modifier.weight(1f)
                        )
                    }
                }

                // Section header
                item {
                    EzhilText(
                        key = StringKey.TEACHER_STUDENTS,
                        language = language,
                        style = TextStyle(
                            fontFamily = BaloTamizha2,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = TextPrimary
                        ),
                        modifier = Modifier.padding(horizontal = screenGutter(), vertical = Spacing.xs)
                    )
                }

                // Empty state
                if (uiState.students.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = Spacing.xxl),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(Modifier.height(Spacing.md))
                                Text(
                                    text = EzhilStrings.get(StringKey.NO_STUDENTS_TEACHER, language),
                                    color = TextMuted,
                                    fontFamily = DMSans,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                // Student cards
                items(uiState.students) { student ->
                    DashboardStudentCard(
                        student  = student,
                        language = language,
                        onClick  = { navController.navigate(Screen.StudentDetail.route(student.id)) },
                        modifier = Modifier.padding(horizontal = screenGutter(), vertical = Spacing.xs)
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardStatTile(
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
        Text(value, fontFamily = DMSans, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = color)
        Text(label, fontFamily = DMSans, fontSize = 12.sp, color = TextMuted)
    }
}

@Composable
private fun RiskSummaryCard(
    count: Int,
    color: androidx.compose.ui.graphics.Color,
    bg: androidx.compose.ui.graphics.Color,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            // Two lines of label at 12sp plus the count, reserved whether or
            // not this particular label wraps. "அதிக கவனம்" needs two lines at
            // a quarter of a 720px screen and "இயல்பு" needs one, which left
            // the four cards at different heights and a ragged bottom edge.
            .fillMaxHeight()
            .background(bg, RoundedCornerShape(10.dp))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(vertical = Spacing.sm, horizontal = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("$count", fontFamily = DMSans, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = color)
        Text(
            label,
            fontFamily = DMSans,
            fontSize = 12.sp,
            color = color.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

@Composable
private fun DashboardStudentCard(
    student: StudentEntity,
    language: AppLanguage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val initial = student.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val avatarColors = listOf(Cyan, Amber, Purple, RiskLow, RiskMedium)
    val avatarColor = avatarColors[student.name.length % avatarColors.size]

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(BgCard, RoundedCornerShape(20.dp))
            .border(1.dp, Border, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(avatarColor.copy(alpha = 0.18f), CircleShape)
                .border(1.dp, avatarColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                color = avatarColor,
                fontFamily = BaloTamizha2,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
        Spacer(Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            ResponsiveText(
                text = student.name,
                style = TextStyle(
                    fontFamily = BaloTamizha2,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = TextPrimary
                )
            )
            if (student.streakDays > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val streakLabel = EzhilStrings.get(StringKey.STREAK_LABEL, language)
                    ResponsiveText(
                        text = "${student.streakDays} $streakLabel",
                        style = TextStyle(
                            color = Amber,
                            fontFamily = DMSans,
                            fontSize = 12.sp
                        )
                    )
                }
            }
        }
        RiskBadge(level = student.riskLevel.toRiskLevel(), language = language, size = BadgeSize.SMALL)
        Spacer(Modifier.width(Spacing.sm))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(14.dp)
        )
    }
}
