package com.ezhil.app.ui.screens.auth

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.ezhil.app.data.local.EzhilDatabase
import com.ezhil.app.data.local.SecurePrefs
import com.ezhil.app.data.local.dobToPin
import com.ezhil.app.data.local.hashPin
import com.ezhil.app.ui.components.EzhilButton
import com.ezhil.app.ui.components.LanguageToggle
import com.ezhil.app.ui.navigation.Screen
import com.ezhil.app.ui.strings.AppLanguage
import com.ezhil.app.ui.strings.EzhilStrings
import com.ezhil.app.ui.strings.StringKey
import com.ezhil.app.ui.theme.*
import com.ezhil.app.ui.viewmodel.AppLanguageViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class StudentLoginViewModel @Inject constructor(
    private val db:    EzhilDatabase,
    private val prefs: SecurePrefs,
) : ViewModel() {

    sealed class LoginState {
        object Idle    : LoginState()
        object Loading : LoginState()
        object Offline : LoginState()
        data class Success(val studentName: String) : LoginState()
        data class Error(val message: String)       : LoginState()
    }

    private val _state = MutableStateFlow<LoginState>(LoginState.Idle)
    val state: StateFlow<LoginState> = _state

    fun login(schoolName: String, studentCode: String, pin: String) {
        val missing = buildList {
            if (studentCode.isBlank()) add("your name")
            if (pin.isBlank())         add("your PIN")
        }
        if (missing.isNotEmpty()) {
            val what = missing.joinToString(" and ")
            _state.value = LoginState.Error("Please enter $what.")
            return
        }
        viewModelScope.launch {
            _state.value = LoginState.Loading
            try {
                // First name, matching the web client. Falls back to a full-name
                // match so anyone who types "Kavin S." still gets in.
                val typed = studentCode.trim()
                val student = prefs.teacherId?.let { db.studentDao().findByFirstName(it, typed) }
                    ?: db.studentDao().findByName(typed)
                if (student == null) {
                    _state.value = LoginState.Error(
                        "We could not find that name. Type your first name, for example Kavin."
                    )
                    return@launch
                }

                // Verify PIN: explicit hashedPin takes priority, else fall back to DOB → MMDD
                val pinValid = when {
                    student.hashedPin != null -> hashPin(pin.trim()) == student.hashedPin
                    else -> pin.trim() == dobToPin(student.dob)
                }

                if (!pinValid) {
                    _state.value = LoginState.Error("Invalid PIN. (Hint: birthday MMDD, e.g. May 12 → 0512)")
                    return@launch
                }

                prefs.activeStudentId = student.id
                prefs.teacherName     = student.name
                prefs.studentDob      = student.dob
                _state.value = LoginState.Success(student.name)
            } catch (e: Exception) {
                Log.e("StudentLogin", "Login failed", e)
                _state.value = LoginState.Error(e.localizedMessage ?: "Unknown error")
            }
        }
    }

    fun retryOnline() { _state.value = LoginState.Idle }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun StudentLoginScreen(
    navController: NavHostController,
    vm:     StudentLoginViewModel = hiltViewModel(),
    langVm: AppLanguageViewModel  = hiltViewModel()
) {
    val language = langVm.language.collectAsState().value
    val state    = vm.state.collectAsState().value
    var school   by remember { mutableStateOf("") }
    var code     by remember { mutableStateOf("") }
    var pin      by remember { mutableStateOf("") }
    var showPin  by remember { mutableStateOf(false) }

    val isLoading = state is StudentLoginViewModel.LoginState.Loading
    val isOffline = state is StudentLoginViewModel.LoginState.Offline

    LaunchedEffect(state) {
        if (state is StudentLoginViewModel.LoginState.Success) {
            navController.navigate(Screen.StudentHome.route) {
                popUpTo(Screen.RoleSelection.route) { inclusive = true }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgCard)
                .border(1.dp, Border),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextSecondary)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text       = if (language == AppLanguage.TAMIL) "மாணவர் உள்நுழைவு" else "Student Login",
                    fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold,
                    fontSize   = 17.sp, color = TextPrimary
                )
                Text(text = "STUDENT LOGIN", fontFamily = DMSans, fontSize = 12.sp, color = TextMuted)
            }
            LanguageToggle(current = language, onToggle = { langVm.toggle() })
        }

        // Offline banner
        if (isOffline) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ErrorBg)
                    .padding(horizontal = screenGutter(), vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("📡 இணைப்பு இல்லை / No network", color = Error, fontFamily = DMSans, fontSize = 13.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.retryOnline() }) {
                    Text(EzhilStrings.get(StringKey.TEACHER_RETRY, language), color = Error, fontSize = 13.sp)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = screenGutter()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(Spacing.xl))

            // Avatar
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(CyanDim, CircleShape)
                    .border(2.dp, Cyan, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("🎒", fontSize = 40.sp)
            }

            Spacer(Modifier.height(Spacing.md))

            Text(
                text       = if (language == AppLanguage.TAMIL) "மாணவர் உள்நுழைவு" else "Student Login",
                fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold,
                fontSize   = 24.sp, color = TextPrimary, textAlign = TextAlign.Center
            )
            Text(
                text       = if (language == AppLanguage.TAMIL)
                    "உங்கள் பள்ளி பெயர், மாணவர் குறியீடு மற்றும் PIN ஐ உள்ளிடவும்"
                else "Enter your school name, student code and PIN",
                fontFamily = NotoSansTamil, fontSize = 13.sp,
                color      = TextMuted, textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(Spacing.xl))

            // Form card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgCard, RoundedCornerShape(20.dp))
                    .border(1.dp, Border, RoundedCornerShape(20.dp))
                    .padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // School name (optional — not used for local auth)
                LoginField(
                    label         = if (language == AppLanguage.TAMIL) "பள்ளி பெயர் (விருப்பம்)" else "School Name (optional)",
                    value         = school,
                    placeholder   = "e.g. Govt. Primary School",
                    onValueChange = { school = it },
                    enabled       = !isLoading,
                    isError       = false,
                    isPassword    = false, showPassword = false, onTogglePassword = {}
                )

                // Student code
                LoginField(
                    label         = if (language == AppLanguage.TAMIL) "மாணவர் குறியீடு" else "Student Code",
                    value         = code,
                    placeholder   = "e.g. ANBU",
                    onValueChange = { code = it.uppercase() },
                    enabled       = !isLoading && !isOffline,
                    isError       = state is StudentLoginViewModel.LoginState.Error,
                    isPassword    = false, showPassword = false, onTogglePassword = {}
                )

                // PIN
                LoginField(
                    label         = "PIN",
                    value         = pin,
                    placeholder   = "4 digits (your birthday MM/DD)",
                    onValueChange = { if (it.length <= 4) pin = it },
                    enabled       = !isLoading && !isOffline,
                    isError       = state is StudentLoginViewModel.LoginState.Error,
                    isPassword    = true, showPassword = showPin,
                    onTogglePassword = { showPin = !showPin }
                )

                // Hint
                Text(
                    text       = if (language == AppLanguage.TAMIL)
                        "உங்கள் பிறந்த மாதம் + நாள் = PIN (எ.கா. மே 12 → 0512)"
                    else "PIN = birthday month+day (e.g. May 12 → 0512)",
                    fontFamily = DMSans, fontSize = 12.sp, color = Cyan
                )
            }

            // Error
            if (state is StudentLoginViewModel.LoginState.Error) {
                Spacer(Modifier.height(Spacing.sm))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ErrorBg, RoundedCornerShape(12.dp))
                        .border(1.dp, Error.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⚠", color = Error, fontSize = 16.sp)
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text       = (state as StudentLoginViewModel.LoginState.Error).message,
                        color      = Error, fontFamily = DMSans, fontSize = 13.sp,
                        modifier   = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(Spacing.lg))

            EzhilButton(
                label     = if (isOffline)
                    EzhilStrings.get(StringKey.TEACHER_RETRY, language)
                else if (language == AppLanguage.TAMIL) "உள்நுழை" else "Login",
                onClick   = {
                    if (isOffline) vm.retryOnline() else vm.login(school, code, pin)
                },
                modifier  = Modifier.fillMaxWidth().height(54.dp),
                isLoading = isLoading,
                backgroundColor = Cyan,
                textColor = BgDark
            )

            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

@Composable
private fun LoginField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    isError: Boolean,
    isPassword: Boolean,
    showPassword: Boolean,
    onTogglePassword: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = label,
            color = TextMuted,
            fontFamily = DMSans,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(placeholder, color = TextMuted.copy(alpha = 0.5f), fontFamily = DMSans)
            },
            singleLine = true,
            enabled = enabled,
            isError = isError,
            visualTransformation = if (isPassword && !showPassword)
                PasswordVisualTransformation()
            else
                VisualTransformation.None,
            trailingIcon = if (isPassword) {
                {
                    IconButton(onClick = onTogglePassword) {
                        Icon(
                            imageVector = if (showPassword) Icons.Default.VisibilityOff
                                          else Icons.Default.Visibility,
                            contentDescription = if (showPassword) "Hide" else "Show",
                            tint = TextMuted
                        )
                    }
                }
            } else null,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Cyan,
                unfocusedBorderColor = Border,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedContainerColor = BgCardElevated,
                unfocusedContainerColor = BgCardElevated,
                cursorColor = Cyan
            )
        )
    }
}
