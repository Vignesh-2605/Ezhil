package com.ezhil.app.ui.screens.auth

import android.util.Log
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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WifiOff
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
import com.ezhil.app.data.local.entity.SchoolEntity
import com.ezhil.app.data.local.entity.TeacherEntity
import com.ezhil.app.data.local.hashPin
import com.ezhil.app.data.remote.EzhilApiService
import com.ezhil.app.data.remote.dto.LoginRequest
import com.ezhil.app.ui.components.*
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

// ── ViewModel (unchanged) ─────────────────────────────────────────────────────

@HiltViewModel
class TeacherLoginViewModel @Inject constructor(
    private val api:   EzhilApiService,
    private val db:    EzhilDatabase,
    private val prefs: SecurePrefs,
) : ViewModel() {

    sealed class LoginState {
        object Idle    : LoginState()
        object Loading : LoginState()
        object Offline : LoginState()
        data class Error(val message: String) : LoginState()
        object Success : LoginState()
    }

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState: StateFlow<LoginState> = _loginState

    /** Current server override, empty when the build default is in use. */
    fun currentServerUrl(): String = prefs.serverUrl.orEmpty()

    fun setServerUrl(value: String) {
        prefs.serverUrl = value.trim().ifBlank { null }
        Log.i("TeacherLogin", "server address set to ${prefs.serverUrl ?: "(build default)"}")
    }

    fun login(schoolCode: String, teacherId: String, pin: String) {
        // Name what is actually missing. The server answers every auth
        // failure with one generic message by design, so an empty field and a
        // typo are indistinguishable unless the client says which is which.
        val missing = buildList {
            if (schoolCode.isBlank()) add("School code")
            if (teacherId.isBlank())  add("Teacher ID")
            if (pin.isBlank())        add("PIN")
        }
        if (missing.isNotEmpty()) {
            _loginState.value = LoginState.Error(
                if (missing.size == 1) "${missing[0]} is required."
                else missing.dropLast(1).joinToString(", ") + " and ${missing.last()} are required."
            )
            return
        }
        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            try {
                // 1. Authenticate with server to get JWT
                val response = api.login(LoginRequest(
                    schoolCode = schoolCode.trim().uppercase(),
                    teacherId  = teacherId.trim().uppercase(),
                    pin        = pin.trim()
                ))

                // 2. Save/Update local data for offline use
                db.schoolDao().upsert(SchoolEntity(
                    id         = response.schoolId,
                    schoolCode = schoolCode.trim().uppercase(),
                    name       = response.schoolName,
                    district   = response.district
                ))
                // A demo-seeded row can already hold this teacherCode under a
                // different id. The column is UNIQUE, so that insert would
                // throw and silently drop us into the offline-login fallback,
                // leaving no teacher row for the roster's foreign key.
                db.teacherDao().deleteStaleDuplicates(
                    code   = teacherId.trim().uppercase(),
                    keepId = response.teacherId,
                )
                db.teacherDao().upsert(TeacherEntity(
                    id          = response.teacherId,
                    schoolId    = response.schoolId,
                    teacherCode = teacherId.trim().uppercase(),
                    name        = response.teacherName,
                    className   = response.className,
                    schoolCode  = schoolCode.trim().uppercase(),
                    // Cache the PIN hash so this teacher can log in offline later.
                    hashedPin   = hashPin(pin.trim()),
                    syncStatus  = "synced"
                ))

                // 3. Save session
                prefs.authToken   = response.accessToken
                prefs.teacherId   = response.teacherId
                prefs.schoolId    = response.schoolId
                prefs.schoolCode  = schoolCode.trim().uppercase()
                prefs.teacherCode = teacherId.trim().uppercase()
                prefs.teacherName = response.teacherName
                prefs.schoolName  = response.schoolName
                prefs.className   = response.className
                prefs.district    = response.district

                _loginState.value = LoginState.Success
            } catch (e: retrofit2.HttpException) {
                Log.e("TeacherLogin", "Server error: ${e.code()}", e)
                _loginState.value = LoginState.Error(
                    if (e.code() == 401) "That school code, teacher ID or PIN is not correct. Check them and try again."
                    else "Server error (${e.code()}). Please check your backend."
                )
            } catch (e: java.io.IOException) {
                // Genuinely offline — fall back to the locally cached account
                // so a teacher in a no-signal school can still work.
                Log.w("TeacherLogin", "Server unreachable, trying offline login", e)
                loginOffline(schoolCode, teacherId, pin)
            } catch (e: Exception) {
                // Anything else (a Room constraint failure, a parse error) is a
                // real bug. Falling back to offline login here would hide it and
                // leave the session half-written, which is exactly how the
                // missing-teacher-row defect went unnoticed.
                Log.e("TeacherLogin", "Login failed locally", e)
                _loginState.value = LoginState.Error(
                    "Could not save your account on this device: ${e.message}"
                )
            }
        }
    }

    private suspend fun loginOffline(schoolCode: String, teacherId: String, pin: String) {
        val teacher = db.teacherDao().findBySchoolAndCode(
            schoolCode.trim().uppercase(),
            teacherId.trim().uppercase()
        )
        if (teacher == null || teacher.hashedPin == null) {
            _loginState.value = LoginState.Error(
                "No internet, and no offline account found on this device. " +
                "Connect once to log in for the first time."
            )
            return
        }
        if (teacher.hashedPin != hashPin(pin.trim())) {
            _loginState.value = LoginState.Error("Invalid PIN.")
            return
        }

        val school = db.schoolDao().findById(teacher.schoolId)
        prefs.teacherId   = teacher.id
        prefs.schoolId    = teacher.schoolId
        prefs.schoolCode  = teacher.schoolCode
        prefs.teacherCode = teacher.teacherCode
        prefs.teacherName = teacher.name
        prefs.schoolName  = school?.name ?: teacher.schoolCode
        prefs.className   = teacher.className
        prefs.district    = school?.district ?: ""
        // No token: server features stay disabled until the next online login.
        _loginState.value = LoginState.Success
    }

    fun retryOnline() { _loginState.value = LoginState.Idle }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun TeacherLoginScreen(
    navController: NavHostController,
    vm:     TeacherLoginViewModel = hiltViewModel(),
    langVm: AppLanguageViewModel  = hiltViewModel()
) {
    val language   by langVm.language.collectAsState()
    val loginState by vm.loginState.collectAsState()
    var schoolCode   by remember { mutableStateOf("") }
    var teacherId    by remember { mutableStateOf("") }
    var pin          by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showServerDialog by remember { mutableStateOf(false) }
    var serverUrl by remember { mutableStateOf(vm.currentServerUrl()) }
    val isLoading = loginState is TeacherLoginViewModel.LoginState.Loading

    LaunchedEffect(loginState) {
        if (loginState is TeacherLoginViewModel.LoginState.Success) {
            navController.navigate(Screen.TeacherDashboard.route) {
                popUpTo(Screen.RoleSelection.route) { inclusive = true }
            }
        }
    }

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
                    text = "ஆசிரியர் உள்நுழைவு",
                    fontFamily = BaloTamizha2,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = TextPrimary
                )
                Text(
                    text = "Teacher Login",
                    fontFamily = DMSans,
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
            LanguageToggle(current = language, onToggle = { langVm.toggle() })
        }

        // ── Scrollable content ────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = screenGutter()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(Spacing.xl))

            // Ezhilan mascot at top
            EzhilanWidget(
                state = EzhilanState.IDLE,
                size = 80.dp
            )

            Spacer(Modifier.height(Spacing.md))

            Text(
                text = "எழில் | Ezhil",
                fontFamily = BaloTamizha2,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                color = Cyan
            )
            Text(
                text = "ஆசிரியர்களை வலுப்படுத்துகிறோம்",
                fontFamily = NotoSansTamil,
                fontSize = 13.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Empowering Teachers Through Data",
                fontFamily = DMSans,
                fontSize = 12.sp,
                color = TextMuted,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(Spacing.xl))

            // ── Login form card ───────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgCard, RoundedCornerShape(20.dp))
                    .border(1.dp, Border, RoundedCornerShape(20.dp))
                    .padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                // School code field
                LoginField(
                    label = "${EzhilStrings.get(StringKey.SCHOOL_CODE_LABEL, language)} / பள்ளி குறியீடு",
                    value = schoolCode,
                    placeholder = "SCH-XXXX",
                    onValueChange = { schoolCode = it.uppercase() },
                    enabled = !isLoading,
                    isError = loginState is TeacherLoginViewModel.LoginState.Error,
                    isPassword = false,
                    showPassword = false,
                    onTogglePassword = {}
                )

                // Teacher ID field
                LoginField(
                    label = "${EzhilStrings.get(StringKey.TEACHER_ID_LABEL, language)} / ஆசிரியர் எண்",
                    value = teacherId,
                    placeholder = "1001",
                    onValueChange = { teacherId = it },
                    enabled = !isLoading,
                    isError = loginState is TeacherLoginViewModel.LoginState.Error,
                    isPassword = false,
                    showPassword = false,
                    onTogglePassword = {}
                )

                // PIN field
                LoginField(
                    label = "PIN / கடவுச்சொல்",
                    value = pin,
                    placeholder = "4-digit PIN",
                    onValueChange = { if (it.length <= 6) pin = it },
                    enabled = !isLoading,
                    isError = loginState is TeacherLoginViewModel.LoginState.Error,
                    isPassword = true,
                    showPassword = showPassword,
                    onTogglePassword = { showPassword = !showPassword }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Weighted so the long bilingual label yields space instead
                    // of claiming its full desired width. Without this it
                    // measured first and left "Server" about 96dp, which wrapped
                    // it to one character per line down the right edge — on the
                    // first screen anyone sees.
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(6.dp).background(Success, CircleShape))
                        Spacer(Modifier.width(Spacing.xs))
                        Text(
                            text = "இணையம் தேவையில்லை / Works fully offline",
                            color = TextMuted, fontFamily = DMSans, fontSize = 12.sp
                        )
                    }
                    Spacer(Modifier.width(Spacing.sm))
                    // The server address is set here rather than baked in at
                    // build time, so one APK works on any network.
                    Text(
                        text = "Server",
                        color = Cyan, fontFamily = DMSans, fontSize = 12.sp,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.clickable { showServerDialog = true }
                    )
                }

                if (showServerDialog) {
                    AlertDialog(
                        onDismissRequest = { showServerDialog = false },
                        containerColor = BgCard,
                        title = {
                            Text("Server address", color = TextPrimary,
                                fontFamily = DMSans, fontWeight = FontWeight.Bold)
                        },
                        text = {
                            Column {
                                Text(
                                    "Where the Ezhil backend is running. Leave blank to use " +
                                    "the address this build was made with.",
                                    color = TextMuted, fontFamily = DMSans, fontSize = 12.sp
                                )
                                Spacer(Modifier.height(Spacing.sm))
                                OutlinedTextField(
                                    value = serverUrl,
                                    onValueChange = { serverUrl = it },
                                    singleLine = true,
                                    placeholder = {
                                        Text("ezhil.example.com", color = TextMuted.copy(0.5f))
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor      = Cyan,
                                        unfocusedBorderColor    = Border,
                                        focusedTextColor        = TextPrimary,
                                        unfocusedTextColor      = TextPrimary,
                                        focusedContainerColor   = BgCardElevated,
                                        unfocusedContainerColor = BgCardElevated,
                                        cursorColor             = Cyan
                                    )
                                )
                                Spacer(Modifier.height(Spacing.xs))
                                Text(
                                    "https:// is assumed when no scheme is given.",
                                    color = TextMuted, fontFamily = DMSans, fontSize = 12.sp
                                )
                            }
                        },
                        confirmButton = {
                            EzhilButton(
                                label = "Save",
                                onClick = { vm.setServerUrl(serverUrl); showServerDialog = false },
                                backgroundColor = Cyan,
                                textColor = TextOnCyan
                            )
                        },
                        dismissButton = {
                            TextButton(onClick = { showServerDialog = false }) {
                                Text("Cancel", color = TextMuted, fontFamily = DMSans)
                            }
                        }
                    )
                }
            }

            // ── Error message ─────────────────────────────────────────────────
            if (loginState is TeacherLoginViewModel.LoginState.Error) {
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
                        text = (loginState as TeacherLoginViewModel.LoginState.Error).message,
                        color = Error,
                        fontFamily = DMSans,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(Spacing.lg))

            // ── Submit button ─────────────────────────────────────────────────
            EzhilButton(
                label = EzhilStrings.get(StringKey.LOGIN_BUTTON, language),
                onClick = { vm.login(schoolCode, teacherId, pin) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                isLoading = isLoading,
                enabled = true
            )

            Spacer(Modifier.height(Spacing.md))

            TextButton(onClick = { navController.navigate(Screen.TeacherRegister.route) }) {
                Text(
                    text = "புதிய ஆசிரியரா? இங்கே பதிவு செய்யுங்கள் / New here? Register",
                    color = Cyan,
                    fontFamily = DMSans,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(Spacing.lg))
        }
    }
}

// ── Form field with optional show/hide toggle ─────────────────────────────────

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
                            imageVector = if (showPassword)
                                Icons.Default.VisibilityOff
                            else
                                Icons.Default.Visibility,
                            contentDescription = if (showPassword) "Hide password" else "Show password",
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

// ── OfflineBanner (kept for backward compat) ──────────────────────────────────

@Composable
fun OfflineBanner(language: AppLanguage, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ErrorBg)
            .padding(horizontal = screenGutter(), vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(Icons.Default.WifiOff, contentDescription = null, tint = Error, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = EzhilStrings.get(StringKey.OFFLINE_LOGIN_HINT, language),
                fontFamily = DMSans,
                fontSize = 13.sp,
                color = Error
            )
        }
        TextButton(onClick = onRetry) {
            Text(EzhilStrings.get(StringKey.RETRY, language), color = Cyan, fontSize = 13.sp)
        }
    }
}
