package com.ezhil.app.ui.screens.teacher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.ezhil.app.data.local.EzhilDatabase
import com.ezhil.app.data.local.SecurePrefs
import com.ezhil.app.ui.components.EzhilButton
import com.ezhil.app.ui.components.LanguageToggle
import com.ezhil.app.ui.navigation.Screen
import com.ezhil.app.ui.strings.AppLanguage
import com.ezhil.app.ui.strings.EzhilStrings
import com.ezhil.app.ui.strings.StringKey
import com.ezhil.app.ui.theme.*
import com.ezhil.app.ui.viewmodel.AppLanguageViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TeacherProfileViewModel @Inject constructor(
    private val prefs: SecurePrefs,
    private val db: EzhilDatabase,
) : ViewModel() {

    val teacherName: String get() = prefs.teacherName ?: "Teacher"
    val teacherId:   String get() = prefs.teacherId   ?: "—"
    val schoolName:  String get() = prefs.schoolName  ?: "—"
    val className:   String get() = prefs.className   ?: "—"
    val district:    String get() = prefs.district    ?: "—"

    // Rows the server rejected during sync; they stop retrying on their own
    // and wait here for the teacher to retry or discard.
    val conflictCount: kotlinx.coroutines.flow.StateFlow<Int> =
        kotlinx.coroutines.flow.combine(
            db.studentDao().observeConflictCount(),
            db.assessmentDao().observeConflictCount(),
            db.gameSessionDao().observeConflictCount(),
            db.lessonProgressDao().observeConflictCount(),
        ) { a, b, c, d -> a + b + c + d }
            .stateIn(
                viewModelScope,
                kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
                0
            )

    fun retryConflicts() {
        viewModelScope.launch {
            db.studentDao().retryConflicts()
            db.assessmentDao().retryConflicts()
            db.gameSessionDao().retryConflicts()
            db.lessonProgressDao().retryConflicts()
        }
    }

    fun discardConflicts() {
        viewModelScope.launch {
            db.assessmentDao().deleteConflicts()
            db.gameSessionDao().deleteConflicts()
            db.lessonProgressDao().deleteConflicts()
            db.studentDao().deleteConflicts()
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            prefs.clear()
            onDone()
        }
    }
}

@Composable
fun TeacherProfileScreen(
    navController: NavHostController,
    vm:     TeacherProfileViewModel  = hiltViewModel(),
    langVm: AppLanguageViewModel     = hiltViewModel()
) {
    val language by langVm.language.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            containerColor = BgCard,
            icon  = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = Error) },
            title = {
                Text(
                    text = if (language == AppLanguage.TAMIL) "வெளியேற விரும்புகிறீர்களா?" else "Log out?",
                    color = TextPrimary, fontFamily = BaloTamizha2
                )
            },
            text = {
                Text(
                    text = if (language == AppLanguage.TAMIL)
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
                        navController.navigate(Screen.TeacherLogin.route)
                    }
                }) {
                    Text(
                        text = if (language == AppLanguage.TAMIL) "வெளியேறு" else "Log Out",
                        color = Error, fontFamily = DMSans, fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(
                        text = if (language == AppLanguage.TAMIL) "ரத்து" else "Cancel",
                        color = TextMuted, fontFamily = DMSans
                    )
                }
            }
        )
    }

    Scaffold(
        containerColor = BgDark,
        bottomBar = {
            TeacherBottomNavBar(navController = navController, currentRoute = Screen.TeacherProfile.route)
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BgDark)
                .padding(padding)
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgCard)
                    .border(1.dp, Border)
                    .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.width(48.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (language == AppLanguage.TAMIL) "சுயவிவரம்" else "Profile",
                        fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold,
                        fontSize = 18.sp, color = TextPrimary
                    )
                    Text(text = "TEACHER PROFILE", fontFamily = DMSans, fontSize = 12.sp, color = TextMuted)
                }
                LanguageToggle(current = language, onToggle = { langVm.toggle() })
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = screenGutter(), vertical = Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(Spacing.md))

                // Avatar
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(Purple.copy(alpha = 0.2f))
                        .border(2.dp, Purple, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.School, contentDescription = null, tint = Purple, modifier = Modifier.size(48.dp))
                }

                Spacer(Modifier.height(Spacing.md))

                Text(text = vm.teacherName, fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = TextPrimary)
                Text(text = vm.teacherId,   fontFamily = DMSans, fontSize = 13.sp, color = TextMuted)

                Spacer(Modifier.height(Spacing.lg))

                // Info rows
                ProfileInfoCard(
                    rows = listOf(
                        Triple(Icons.Default.School,         if (language == AppLanguage.TAMIL) "பள்ளி"    else "School",    vm.schoolName),
                        Triple(Icons.Default.Class,          if (language == AppLanguage.TAMIL) "வகுப்பு"  else "Class",     vm.className),
                        Triple(Icons.Default.LocationCity,   if (language == AppLanguage.TAMIL) "மாவட்டம்" else "District",  vm.district),
                        Triple(Icons.Default.Badge,          if (language == AppLanguage.TAMIL) "ஆசிரியர் ID" else "Teacher ID", vm.teacherId),
                    )
                )

                Spacer(Modifier.height(Spacing.lg))

                // Sync issues — server-rejected rows waiting for a decision
                val conflictCount by vm.conflictCount.collectAsState()
                if (conflictCount > 0) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BgCard, RoundedCornerShape(16.dp))
                            .border(1.dp, Amber.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                            .padding(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Amber)
                            Spacer(Modifier.width(Spacing.sm))
                            Text(
                                if (language == AppLanguage.TAMIL)
                                    "$conflictCount பதிவுகள் ஒத்திசைக்கப்படவில்லை"
                                else
                                    "$conflictCount record(s) failed to sync",
                                fontFamily = if (language == AppLanguage.TAMIL) NotoSansTamil else DMSans,
                                fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        }
                        Text(
                            if (language == AppLanguage.TAMIL)
                                "சேவையகம் இந்த பதிவுகளை ஏற்கவில்லை. மீண்டும் முயற்சிக்கவும் அல்லது நீக்கவும்."
                            else
                                "The server rejected these records. Retry, or discard them from this device.",
                            fontFamily = DMSans, fontSize = 12.sp, color = TextMuted
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            EzhilButton(
                                label = if (language == AppLanguage.TAMIL) "மீண்டும் முயற்சி" else "Retry Sync",
                                onClick = { vm.retryConflicts() },
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { vm.discardConflicts() }, modifier = Modifier.weight(1f)) {
                                Text(
                                    if (language == AppLanguage.TAMIL) "நீக்கு" else "Discard",
                                    color = Error, fontFamily = DMSans, fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(Spacing.lg))
                }

                // Language toggle row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BgCard, RoundedCornerShape(16.dp))
                        .border(1.dp, Border, RoundedCornerShape(16.dp))
                        .padding(Spacing.md),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        Icon(Icons.Default.Translate, contentDescription = null, tint = Cyan, modifier = Modifier.size(20.dp))
                        Text(
                            text = if (language == AppLanguage.TAMIL) "மொழி / Language" else "Language / மொழி",
                            fontFamily = NotoSansTamil, color = TextPrimary, fontSize = 14.sp
                        )
                    }
                    LanguageToggle(current = language, onToggle = { langVm.toggle() })
                }

                Spacer(Modifier.height(Spacing.xxl))

                // Logout button
                EzhilButton(
                    label = if (language == AppLanguage.TAMIL) "வெளியேறு / Log Out" else "Log Out / வெளியேறு",
                    onClick = { showLogoutDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = ErrorBg,
                    textColor = Error
                )

                Spacer(Modifier.height(Spacing.md))

                Text(
                    text = "Ezhil v1.0 · AI-Powered Tamil Dyslexia Support",
                    fontFamily = DMSans, fontSize = 12.sp, color = TextMuted
                )
                Spacer(Modifier.height(Spacing.md))
            }
        }
    }
}

@Composable
private fun ProfileInfoCard(rows: List<Triple<ImageVector, String, String>>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgCard, RoundedCornerShape(16.dp))
            .border(1.dp, Border, RoundedCornerShape(16.dp))
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        rows.forEachIndexed { i, (icon, label, value) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Box(
                    modifier = Modifier.size(36.dp).background(BgCardElevated, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = Cyan, modifier = Modifier.size(18.dp))
                }
                Column {
                    Text(text = label, fontFamily = DMSans, fontSize = 12.sp, color = TextMuted)
                    Text(text = value, fontFamily = DMSans, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                }
            }
            if (i < rows.lastIndex) HorizontalDivider(color = Border, modifier = Modifier.padding(start = 44.dp))
        }
    }
}
