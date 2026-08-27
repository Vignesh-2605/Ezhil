package com.ezhil.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
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
import com.ezhil.app.data.local.entity.StudentEntity
import com.ezhil.app.ui.components.*
import com.ezhil.app.ui.navigation.Screen
import com.ezhil.app.ui.strings.EzhilStrings
import com.ezhil.app.ui.strings.StringKey
import com.ezhil.app.ui.theme.*
import com.ezhil.app.ui.viewmodel.AppLanguageViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

// ── ViewModel (unchanged) ─────────────────────────────────────────────────────

@HiltViewModel
class StudentProfileViewModel @Inject constructor(
    private val db: EzhilDatabase,
    private val prefs: SecurePrefs
) : ViewModel() {

    val students: StateFlow<List<StudentEntity>> = flow {
        val teacherId = prefs.teacherId ?: return@flow
        emitAll(db.studentDao().observeByTeacher(teacherId))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var selectedId by mutableStateOf<String?>(null)

    fun selectStudent(studentId: String) {
        selectedId = studentId
    }

    fun confirm(studentId: String) {
        prefs.activeStudentId = studentId
        viewModelScope.launch {
            db.studentDao().incrementStreak(studentId, LocalDate.now().toString())
        }
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun StudentProfileScreen(
    navController: NavHostController,
    vm: StudentProfileViewModel = hiltViewModel(),
    langVm: AppLanguageViewModel = hiltViewModel(),
    syncVm: SyncViewModel = hiltViewModel()
) {
    val language by langVm.language.collectAsState()
    val students by vm.students.collectAsState()
    val syncState by syncVm.syncState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        // Sync status bar at very top
        SyncStatusBar(syncState = syncState, language = language)

        // ── Top bar (design system standard) ─────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgCard)
                .border(1.dp, Border),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // No back on this screen (it's a landing after login) — use spacer to balance
            Spacer(Modifier.width(48.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                EzhilText(
                    key = StringKey.TAP_YOUR_NAME,
                    language = language,
                    style = TextStyle(
                        fontFamily = BaloTamizha2,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                )
                EzhilText(
                    key = StringKey.PROFILE_TAP_HINT,
                    language = language,
                    style = TextStyle(
                        fontFamily = DMSans,
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                )
            }
            LanguageToggle(current = language, onToggle = { langVm.toggle() })
        }

        // ── Student list / empty state ────────────────────────────────────────
        if (students.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    // Empty mascot
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .background(BgCardElevated, CircleShape)
                            .border(2.dp, Border, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("👤", fontSize = 48.sp)
                    }
                    EzhilText(
                        key = StringKey.NO_STUDENTS_TEACHER,
                        language = language,
                        color = TextMuted,
                        style = TextStyle(
                            fontFamily = DMSans,
                            textAlign = TextAlign.Center,
                            fontSize = 16.sp
                        ),
                        modifier = Modifier.widthIn(max = 260.dp)
                    )
                    // Add first student CTA
                    EzhilButton(
                        label = EzhilStrings.get(StringKey.ADD_STUDENT, language),
                        onClick = { navController.navigate(Screen.NewStudentProfile.route) },
                        modifier = Modifier.height(50.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    horizontal = screenGutter(), vertical = Spacing.md
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                items(students) { student ->
                    StudentRow(
                        student = student,
                        isSelected = vm.selectedId == student.id,
                        onClick = { vm.selectStudent(student.id) }
                    )
                }

                // Add new student row at bottom of list
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(BgCard, RoundedCornerShape(14.dp))
                            .border(1.5.dp, Cyan.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                            .clickable { navController.navigate(Screen.NewStudentProfile.route) }
                            .padding(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(CyanDim, CircleShape)
                                .border(1.dp, Cyan.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add new student",
                                tint = Cyan,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.width(Spacing.md))
                        Column {
                            EzhilText(
                                key = StringKey.ADD_STUDENT,
                                language = language,
                                style = TextStyle(
                                    fontFamily = BaloTamizha2,
                                    fontSize = 16.sp,
                                    color = Cyan,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                            EzhilText(
                                key = StringKey.PROFILE_NEW_HINT,
                                language = language,
                                style = TextStyle(
                                    fontFamily = DMSans,
                                    fontSize = 12.sp,
                                    color = TextMuted
                                )
                            )
                        }
                    }
                }
            }
        }

        // ── Bottom action bar ─────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgCard)
                .border(width = 1.dp, color = Border, shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .padding(Spacing.md)
        ) {
            // Sync indicator
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(6.dp)
                        .background(Success, CircleShape)
                )
                Spacer(Modifier.width(Spacing.xs))
                EzhilText(
                    key = StringKey.SYNC_STATUS_ALL,
                    language = language,
                    style = TextStyle(
                        color = TextMuted,
                        fontFamily = DMSans,
                        fontSize = 12.sp
                    )
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            EzhilButton(
                label = EzhilStrings.get(StringKey.CONTINUE, language),
                onClick = {
                    val id = vm.selectedId ?: return@EzhilButton
                    vm.confirm(id)
                    navController.navigate(Screen.StudentHome.route) {
                        popUpTo(Screen.RoleSelection.route) { inclusive = true }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                enabled = vm.selectedId != null
            )
        }
    }
}

// ── Student row card ──────────────────────────────────────────────────────────

@Composable
private fun StudentRow(student: StudentEntity, isSelected: Boolean, onClick: () -> Unit) {
    val initial = student.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val avatarColors = listOf(Cyan, Amber, Purple, RiskLow, RiskMedium)
    val avatarColor = avatarColors[student.name.length % avatarColors.size]

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isSelected) CyanDim else BgCard,
                RoundedCornerShape(14.dp)
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) Cyan else Border,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar circle with initial
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(avatarColor.copy(alpha = 0.15f), CircleShape)
                .border(2.dp, avatarColor.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                color = avatarColor,
                fontFamily = BaloTamizha2,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }

        Spacer(Modifier.width(Spacing.md))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = student.name,
                fontFamily = BaloTamizha2,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = TextPrimary
            )
            if (student.streakDays > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔥", fontSize = 12.sp)
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = "${student.streakDays} நாட்கள்",
                        color = Amber,
                        fontFamily = DMSans,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Check icon or chevron
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(Cyan, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = TextOnCyan,
                    modifier = Modifier.size(16.dp)
                )
            }
        } else {
            Text("›", color = TextMuted, fontSize = 22.sp)
        }
    }
}

// ── SyncViewModel ─────────────────────────────────────────────────────────────

@HiltViewModel
class SyncViewModel @Inject constructor() : ViewModel() {
    val syncState: StateFlow<SyncState> = MutableStateFlow(SyncState.IDLE)
}
