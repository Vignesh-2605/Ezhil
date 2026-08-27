package com.ezhil.app.ui.screens.student

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
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
import com.ezhil.app.data.local.entity.GameSessionEntity
import com.ezhil.app.ui.components.*
import com.ezhil.app.ui.navigation.Screen
import com.ezhil.app.ui.strings.AppLanguage
import com.ezhil.app.ui.theme.*
import com.ezhil.app.ui.viewmodel.AppLanguageViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StudentProfileViewViewModel @Inject constructor(
    private val db: EzhilDatabase,
    private val prefs: SecurePrefs
) : ViewModel() {

    data class Profile(
        val name: String = "",
        val schoolName: String = "",
        val dob: String = "",
        val teacherName: String = "",
        val className: String = "",
        val streakDays: Int = 0,
        val lessonsCompleted: Int = 0,
        val gamesPlayed: Int = 0,
        val totalStars: Int = 0,
        val level: String = "Beginner",
        val levelTa: String = "தொடக்கநிலை",
        val levelColor: Color = Border,
        val recentSessions: List<GameSessionEntity> = emptyList()
    )

    val profile: StateFlow<Profile> = flow {
        val studentId = prefs.activeStudentId ?: return@flow
        combine(
            db.lessonProgressDao().observeByStudent(studentId),
            db.gameSessionDao().observeByStudent(studentId)
        ) { progress, sessions ->
            val totalStars = sessions.sumOf { it.starsEarned }
            val (level, levelTa, levelColor) = when {
                totalStars >= 100 -> Triple("Champion",  "சாம்பியன்",    Gold)
                totalStars >= 50  -> Triple("Expert",    "நிபுணர்",      Purple)
                totalStars >= 25  -> Triple("Explorer",  "ஆராய்வாளர்",  Amber)
                totalStars >= 10  -> Triple("Learner",   "கற்பவர்",      Cyan)
                else              -> Triple("Beginner",  "தொடக்கநிலை",  Border)
            }
            Profile(
                name             = prefs.teacherName ?: "",
                schoolName       = prefs.schoolName ?: "",
                dob              = prefs.studentDob ?: "",
                teacherName      = prefs.studentTeacherName ?: "",
                className        = prefs.className ?: "",
                streakDays       = 0,
                lessonsCompleted = progress.count { it.completedAt != null },
                gamesPlayed      = sessions.size,
                totalStars       = totalStars,
                level            = level,
                levelTa          = levelTa,
                levelColor       = levelColor,
                recentSessions   = sessions.takeLast(3).reversed()
            )
        }.collect { emit(it) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Profile())

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            prefs.clear()
            onDone()
        }
    }
}

@Composable
fun StudentProfileViewScreen(
    navController: NavHostController,
    vm: StudentProfileViewViewModel = hiltViewModel(),
    langVm: AppLanguageViewModel = hiltViewModel()
) {
    val language by langVm.language.collectAsState()
    val profile  by vm.profile.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = BgCard,
            icon  = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = Error) },
            title = {
                Text(
                    if (language == AppLanguage.TAMIL) "வெளியேற விரும்புகிறீர்களா?" else "Log out?",
                    color = TextPrimary, fontFamily = BaloTamizha2
                )
            },
            text = {
                Text(
                    if (language == AppLanguage.TAMIL)
                        "உங்கள் கணக்கிலிருந்து வெளியேறுவீர்கள்."
                    else "You will be signed out of your account.",
                    color = TextMuted, fontFamily = NotoSansTamil
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.logout {
                        navController.navigate(Screen.RoleSelection.route) {
                            popUpTo(0) { inclusive = true }
                        }
                        navController.navigate(Screen.StudentLogin.route)
                    }
                }) {
                    Text(
                        if (language == AppLanguage.TAMIL) "வெளியேறு" else "Log Out",
                        color = Error, fontFamily = DMSans, fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(
                        if (language == AppLanguage.TAMIL) "ரத்து" else "Cancel",
                        color = TextMuted, fontFamily = DMSans
                    )
                }
            }
        )
    }

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
                    if (language == AppLanguage.TAMIL) "என் சுயவிவரம்" else "My Profile",
                    fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold,
                    fontSize = 18.sp, color = TextPrimary
                )
                Text("PROFILE", fontFamily = DMSans, fontSize = 12.sp, color = TextMuted)
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
            // ── Hero card ─────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgCard, RoundedCornerShape(20.dp))
                    .border(1.dp, profile.levelColor.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                    .padding(Spacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                EzhilanWidget(
                    state = if (profile.totalStars >= 10) EzhilanState.CELEBRATING else EzhilanState.IDLE,
                    size  = 72.dp
                )
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(profile.levelColor.copy(alpha = 0.15f), CircleShape)
                            .border(2.dp, profile.levelColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            profile.name.take(1).uppercase().ifEmpty { "?" },
                            fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold,
                            fontSize = 32.sp, color = profile.levelColor
                        )
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    Text(
                        profile.name.ifEmpty { "—" },
                        fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold,
                        fontSize = 20.sp, color = TextPrimary
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Box(
                        modifier = Modifier
                            .background(profile.levelColor.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                            .border(1.dp, profile.levelColor.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                            .padding(horizontal = screenGutter(), vertical = Spacing.xs)
                    ) {
                        Text(
                            if (language == AppLanguage.TAMIL) "🏅 ${profile.levelTa}" else "🏅 ${profile.level}",
                            fontFamily = DMSans, fontWeight = FontWeight.Bold,
                            fontSize = 12.sp, color = profile.levelColor
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔥", fontSize = 28.sp)
                    Text(
                        "${profile.streakDays}",
                        fontFamily = DMSans, fontWeight = FontWeight.Bold,
                        fontSize = 22.sp, color = Amber
                    )
                    Text(
                        if (language == AppLanguage.TAMIL) "நாட்கள்" else "days",
                        fontFamily = DMSans, fontSize = 12.sp, color = TextMuted
                    )
                }
            }

            // ── Account details card ──────────────────────────────────────────
            StudentInfoCard(
                rows = listOf(
                    Triple(Icons.Default.Badge,  if (language == AppLanguage.TAMIL) "பெயர்"      else "Name",          profile.name.ifEmpty { "—" }),
                    Triple(Icons.Default.School, if (language == AppLanguage.TAMIL) "பள்ளி"       else "School",        profile.schoolName.ifEmpty { "—" }),
                    Triple(Icons.Default.Cake,   if (language == AppLanguage.TAMIL) "பிறந்த நாள்" else "Date of Birth", profile.dob.ifEmpty { "—" }),
                    Triple(Icons.Default.Person, if (language == AppLanguage.TAMIL) "ஆசிரியர்"   else "Teacher",       profile.teacherName.ifEmpty { "—" }),
                    Triple(Icons.Default.Class,  if (language == AppLanguage.TAMIL) "வகுப்பு"    else "Class",         profile.className.ifEmpty { "—" }),
                )
            )

            // ── Achievement stats ─────────────────────────────────────────────
            Text(
                if (language == AppLanguage.TAMIL) "என் சாதனைகள்" else "My Achievements",
                fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold,
                fontSize = 16.sp, color = TextPrimary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ProfileStatCard("⭐", "${profile.totalStars}",
                    if (language == AppLanguage.TAMIL) "நட்சத்திரங்கள்" else "Stars", Gold, Modifier.weight(1f))
                ProfileStatCard("📖", "${profile.lessonsCompleted}",
                    if (language == AppLanguage.TAMIL) "பாடங்கள் முடிந்தது" else "Lessons Done", Cyan, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                ProfileStatCard("🎮", "${profile.gamesPlayed}",
                    if (language == AppLanguage.TAMIL) "விளையாட்டுகள்" else "Games Played", Purple, Modifier.weight(1f))
                ProfileStatCard("🔥", "${profile.streakDays}",
                    if (language == AppLanguage.TAMIL) "நாட்கள் தொடர்ச்சி" else "Day Streak", Amber, Modifier.weight(1f))
            }

            // ── Recent activity ───────────────────────────────────────────────
            if (profile.recentSessions.isNotEmpty()) {
                Text(
                    if (language == AppLanguage.TAMIL) "சமீபத்திய செயல்பாடு" else "Recent Activity",
                    fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold,
                    fontSize = 16.sp, color = TextPrimary
                )
                profile.recentSessions.forEach { session ->
                    val (emoji, label, color) = when (session.gameType) {
                        "match_sound"        -> Triple("🔊", "Match Sound",   Cyan)
                        "spot_letter"        -> Triple("🔍", "Spot Letter",   Amber)
                        "build_word"         -> Triple("🔤", "Build Word",    Purple)
                        "phonics_quiz"       -> Triple("🎮", "Phonics Quiz",  RiskLow)
                        "comprehension_quiz" -> Triple("💡", "Quiz",          Gold)
                        else                 -> Triple("🎯", session.gameType, Border)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BgCard, RoundedCornerShape(12.dp))
                            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                            .padding(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(color.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) { Text(emoji, fontSize = 20.sp) }
                        Spacer(Modifier.width(Spacing.md))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(label, fontFamily = DMSans, fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp, color = TextPrimary)
                            // Stars to the right already say how it went. A
                            // child never sees the raw count — it is kept for
                            // the teacher's reports, not shown back to them.
                            Text(runCatching { session.playedAt.take(10) }.getOrElse { "" },
                                fontFamily = DMSans, fontSize = 12.sp, color = TextMuted)
                        }
                        repeat(session.starsEarned) { Text("⭐", fontSize = 14.sp) }
                        repeat((3 - session.starsEarned).coerceAtLeast(0)) {
                            Text("☆", fontSize = 14.sp, color = GoldDim)
                        }
                    }
                }
            }

            // ── Logout ────────────────────────────────────────────────────────
            Spacer(Modifier.height(Spacing.sm))
            EzhilButton(
                label = if (language == AppLanguage.TAMIL) "வெளியேறு / Log Out" else "Log Out / வெளியேறு",
                onClick = { showLogoutDialog = true },
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = ErrorBg,
                textColor = Error
            )

            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

@Composable
private fun StudentInfoCard(rows: List<Triple<ImageVector, String, String>>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgCard, RoundedCornerShape(16.dp))
            .border(1.dp, Border, RoundedCornerShape(16.dp))
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        rows.forEachIndexed { i, (icon, label, value) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(BgCardElevated, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = Cyan, modifier = Modifier.size(18.dp))
                }
                Column {
                    Text(label, fontFamily = DMSans, fontSize = 12.sp, color = TextMuted)
                    Text(value, fontFamily = DMSans, fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold, color = TextPrimary)
                }
            }
            if (i < rows.lastIndex) HorizontalDivider(color = Border, modifier = Modifier.padding(start = 44.dp))
        }
    }
}

@Composable
private fun ProfileStatCard(
    icon: String,
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(BgCard, RoundedCornerShape(14.dp))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(icon, fontSize = 24.sp)
        Spacer(Modifier.height(Spacing.xs))
        Text(value, fontFamily = DMSans, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = color)
        Text(label, fontFamily = DMSans, fontSize = 12.sp, color = TextMuted,
            textAlign = TextAlign.Center)
    }
}
