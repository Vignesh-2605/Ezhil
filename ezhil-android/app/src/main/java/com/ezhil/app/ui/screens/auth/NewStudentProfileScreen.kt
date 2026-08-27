package com.ezhil.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.ezhil.app.data.local.entity.StudentEntity
import com.ezhil.app.ui.components.EzhilButton
import com.ezhil.app.ui.components.EzhilOutlinedButton
import com.ezhil.app.ui.components.EzhilanState
import com.ezhil.app.ui.components.EzhilanWidget
import com.ezhil.app.ui.components.LanguageToggle
import com.ezhil.app.ui.navigation.Screen
import com.ezhil.app.ui.strings.EzhilStrings
import com.ezhil.app.ui.strings.StringKey
import com.ezhil.app.ui.theme.*
import com.ezhil.app.ui.viewmodel.AppLanguageViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

// ── ViewModel (unchanged) ─────────────────────────────────────────────────────

@HiltViewModel
class NewStudentProfileViewModel @Inject constructor(
    private val db: EzhilDatabase,
    private val prefs: SecurePrefs
) : ViewModel() {

    fun saveStudent(name: String, dob: String?, gender: String, onDone: () -> Unit) {
        val teacherId = prefs.teacherId ?: return
        val student = StudentEntity(
            id = UUID.randomUUID().toString(),
            teacherId = teacherId,
            name = name.trim(),
            dob = dob?.takeIf { it.isNotBlank() },
            syncStatus = "pending"
        )
        viewModelScope.launch {
            db.studentDao().upsert(student)
            prefs.activeStudentId = student.id
            onDone()
        }
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun NewStudentProfileScreen(
    navController: NavHostController,
    vm: NewStudentProfileViewModel = hiltViewModel(),
    langVm: AppLanguageViewModel = hiltViewModel()
) {
    val language by langVm.language.collectAsState()
    var name by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("boy") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        // ── Top bar (design system standard) ─────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgCard)
                .border(1.dp, Border),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextSecondary
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "புதிய மாணவர்",
                    fontFamily = BaloTamizha2,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = TextPrimary
                )
                Text(
                    text = "Add New Student",
                    fontFamily = DMSans,
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
            LanguageToggle(current = language, onToggle = { langVm.toggle() })
        }

        // ── Scrollable form body ──────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = screenGutter()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(Spacing.xl))

            // Ezhilan mascot
            EzhilanWidget(
                state = EzhilanState.IDLE,
                size = 72.dp
            )

            Spacer(Modifier.height(Spacing.sm))

            Text(
                text = "மாணவர் சுயவிவரம் உருவாக்கு",
                fontFamily = BaloTamizha2,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Create Student Profile",
                fontFamily = DMSans,
                fontSize = 13.sp,
                color = TextMuted,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(Spacing.lg))

            // ── Avatar preview — shows emoji based on selected gender ─────────
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(BgCardElevated, CircleShape)
                    .border(3.dp, Cyan.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when (gender) {
                        "boy"   -> "👦"
                        "girl"  -> "👧"
                        else    -> "🧒"
                    },
                    fontSize = 50.sp
                )
            }

            Spacer(Modifier.height(Spacing.xl))

            // ── Form card ─────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgCard, RoundedCornerShape(20.dp))
                    .border(1.dp, Border, RoundedCornerShape(20.dp))
                    .padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // Name field
                FormField(
                    labelTamil = "மாணவர் பெயர்",
                    labelEnglish = "Student Name",
                    value = name,
                    placeholder = "Enter full name",
                    onValueChange = { name = it }
                )

                // DOB field
                FormField(
                    labelTamil = "பிறந்த தேதி",
                    labelEnglish = "Date of Birth",
                    value = dob,
                    placeholder = "mm/dd/yyyy",
                    onValueChange = { dob = it }
                )

                // Gender picker
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Row {
                        Text(
                            text = "பாலினம் / ",
                            color = TextMuted,
                            fontFamily = BaloTamizha2,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Gender",
                            color = TextMuted,
                            fontFamily = DMSans,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        GenderChip(
                            emoji = "👦",
                            label = "ஆண்",
                            subLabel = "Boy",
                            value = "boy",
                            selected = gender,
                            accentColor = Cyan,
                            modifier = Modifier.weight(1f),
                            onClick = { gender = "boy" }
                        )
                        GenderChip(
                            emoji = "👧",
                            label = "பெண்",
                            subLabel = "Girl",
                            value = "girl",
                            selected = gender,
                            accentColor = Purple,
                            modifier = Modifier.weight(1f),
                            onClick = { gender = "girl" }
                        )
                        GenderChip(
                            emoji = "🧒",
                            label = "மற்றது",
                            subLabel = "Other",
                            value = "other",
                            selected = gender,
                            accentColor = Amber,
                            modifier = Modifier.weight(1f),
                            onClick = { gender = "other" }
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.xl))

            // ── Save button ───────────────────────────────────────────────────
            EzhilButton(
                label = "சேமி & தொடர் / Save & Continue",
                onClick = {
                    vm.saveStudent(name, dob, gender) {
                        navController.navigate(Screen.StudentHome.route) {
                            popUpTo(Screen.StudentProfile.route) { inclusive = true }
                        }
                    }
                },
                enabled = name.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            )

            Spacer(Modifier.height(Spacing.sm))

            EzhilOutlinedButton(
                label = "ரத்து / Cancel",
                onClick = { navController.popBackStack() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                textColor = TextMuted
            )

            Spacer(Modifier.height(Spacing.md))

            // Sync status hint
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .background(
                            if (name.isNotBlank()) Cyan else TextMuted,
                            CircleShape
                        )
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    text = "தரவு ஒத்திசைக்கப்பட்டுள்ளன | DATA SYNCED",
                    color = TextMuted,
                    fontFamily = DMSans,
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

// ── Form text field ───────────────────────────────────────────────────────────

@Composable
private fun FormField(
    labelTamil: String,
    labelEnglish: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row {
            Text(
                text = "$labelTamil / ",
                color = TextMuted,
                fontFamily = BaloTamizha2,
                fontSize = 12.sp
            )
            Text(
                text = labelEnglish,
                color = TextMuted,
                fontFamily = DMSans,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = TextMuted.copy(0.5f), fontFamily = DMSans) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = darkTextFieldColors()
        )
    }
}

// ── Gender chip — colorful, playful ──────────────────────────────────────────

@Composable
private fun GenderChip(
    emoji: String,
    label: String,
    subLabel: String,
    value: String,
    selected: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val active = selected == value
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (active) accentColor.copy(alpha = 0.15f) else BgCardElevated,
                RoundedCornerShape(14.dp)
            )
            .border(
                width = if (active) 2.dp else 1.dp,
                color = if (active) accentColor else Border,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, fontSize = 24.sp)
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            color = if (active) accentColor else TextSecondary,
            fontFamily = BaloTamizha2,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center
        )
        Text(
            text = subLabel,
            color = if (active) accentColor.copy(alpha = 0.8f) else TextMuted,
            fontFamily = DMSans,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

// ── Shared text field colors (also used by other auth screens) ────────────────

@Composable
fun darkTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = Cyan,
    unfocusedBorderColor = Border,
    focusedTextColor     = TextPrimary,
    unfocusedTextColor   = TextPrimary,
    focusedContainerColor   = BgCardElevated,
    unfocusedContainerColor = BgCardElevated,
    cursorColor          = Cyan
)
