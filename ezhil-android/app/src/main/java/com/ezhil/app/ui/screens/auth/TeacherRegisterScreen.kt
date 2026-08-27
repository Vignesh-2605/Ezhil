package com.ezhil.app.ui.screens.auth

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.ezhil.app.data.local.entity.SchoolEntity
import com.ezhil.app.data.local.entity.TeacherEntity
import com.ezhil.app.data.local.hashPin
import com.ezhil.app.ui.components.EzhilButton
import com.ezhil.app.ui.navigation.Screen
import com.ezhil.app.ui.theme.*
import com.ezhil.app.ui.viewmodel.AppLanguageViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class TeacherRegisterViewModel @Inject constructor(
    private val db:    EzhilDatabase,
    private val prefs: SecurePrefs,
    private val api:   com.ezhil.app.data.remote.EzhilApiService,
) : ViewModel() {

    sealed class RegisterState {
        object Idle    : RegisterState()
        object Loading : RegisterState()
        object Success : RegisterState()
        data class Error(val message: String) : RegisterState()
    }

    private val _state = MutableStateFlow<RegisterState>(RegisterState.Idle)
    val state: StateFlow<RegisterState> = _state

    fun register(
        schoolName:  String,
        schoolCode:  String,
        teacherName: String,
        teacherCode: String,
        className:   String,
        pin:         String,
        pinConfirm:  String,
    ) {
        if (schoolName.isBlank() || schoolCode.isBlank() || teacherName.isBlank() ||
            teacherCode.isBlank() || className.isBlank() || pin.isBlank()) {
            _state.value = RegisterState.Error("Please fill in all fields.")
            return
        }
        if (pin.length < 4) {
            _state.value = RegisterState.Error("PIN must be at least 4 digits.")
            return
        }
        if (pin != pinConfirm) {
            _state.value = RegisterState.Error("PINs do not match.")
            return
        }
        viewModelScope.launch {
            _state.value = RegisterState.Loading
            try {
                val normSchoolCode  = schoolCode.trim().uppercase()
                val normTeacherCode = teacherCode.trim().uppercase()

                val existing = db.teacherDao().findBySchoolAndCode(normSchoolCode, normTeacherCode)
                if (existing != null) {
                    _state.value = RegisterState.Error(
                        "A teacher with this School Code and Teacher ID already exists."
                    )
                    return@launch
                }

                // Register on the server first so this account can log in from
                // the web app and use sync/OCR/lesson generation.
                try {
                    val response = api.register(com.ezhil.app.data.remote.dto.RegisterRequest(
                        schoolCode  = normSchoolCode,
                        schoolName  = schoolName.trim(),
                        district    = "",
                        teacherCode = normTeacherCode,
                        teacherName = teacherName.trim(),
                        className   = className.trim(),
                        pin         = pin,
                    ))
                    saveAccount(
                        schoolId    = response.schoolId,
                        teacherId   = response.teacherId,
                        schoolName  = response.schoolName,
                        schoolCode  = normSchoolCode,
                        teacherCode = normTeacherCode,
                        teacherName = response.teacherName,
                        className   = response.className,
                        district    = response.district,
                        pin         = pin,
                        token       = response.accessToken,
                        synced      = true,
                    )
                    _state.value = RegisterState.Success
                    return@launch
                } catch (e: retrofit2.HttpException) {
                    val msg = if (e.code() == 409) {
                        "This Teacher ID is already registered on the server. Try logging in instead."
                    } else {
                        "Server error (${e.code()}). Please try again."
                    }
                    _state.value = RegisterState.Error(msg)
                    return@launch
                } catch (e: Exception) {
                    // Offline — register locally; the account works on this
                    // device but has no server access until re-registered online.
                    Log.w("TeacherRegister", "Server unreachable, registering offline", e)
                }

                saveAccount(
                    schoolId    = UUID.randomUUID().toString(),
                    teacherId   = UUID.randomUUID().toString(),
                    schoolName  = schoolName.trim(),
                    schoolCode  = normSchoolCode,
                    teacherCode = normTeacherCode,
                    teacherName = teacherName.trim(),
                    className   = className.trim(),
                    district    = "",
                    pin         = pin,
                    token       = null,
                    synced      = false,
                )
                _state.value = RegisterState.Success
            } catch (e: Exception) {
                Log.e("TeacherRegister", "Registration failed", e)
                _state.value = RegisterState.Error(e.localizedMessage ?: "Registration failed")
            }
        }
    }

    private suspend fun saveAccount(
        schoolId: String,
        teacherId: String,
        schoolName: String,
        schoolCode: String,
        teacherCode: String,
        teacherName: String,
        className: String,
        district: String,
        pin: String,
        token: String?,
        synced: Boolean,
    ) {
        db.schoolDao().upsert(SchoolEntity(
            id         = schoolId,
            name       = schoolName,
            district   = district,
            schoolCode = schoolCode,
        ))
        db.teacherDao().upsert(TeacherEntity(
            id          = teacherId,
            schoolId    = schoolId,
            teacherCode = teacherCode,
            name        = teacherName,
            className   = className,
            schoolCode  = schoolCode,
            hashedPin   = hashPin(pin),
            syncStatus  = if (synced) "synced" else "pending",
        ))

        prefs.authToken   = token
        prefs.teacherId   = teacherId
        prefs.teacherName = teacherName
        prefs.schoolName  = schoolName
        prefs.className   = className
        prefs.district    = district
    }
}

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun TeacherRegisterScreen(
    navController: NavHostController,
    vm:     TeacherRegisterViewModel = hiltViewModel(),
    langVm: AppLanguageViewModel     = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    val isLoading = state is TeacherRegisterViewModel.RegisterState.Loading

    var schoolName  by remember { mutableStateOf("") }
    var schoolCode  by remember { mutableStateOf("") }
    var teacherName by remember { mutableStateOf("") }
    var teacherCode by remember { mutableStateOf("") }
    var className   by remember { mutableStateOf("") }
    var pin         by remember { mutableStateOf("") }
    var pinConfirm  by remember { mutableStateOf("") }

    LaunchedEffect(state) {
        if (state is TeacherRegisterViewModel.RegisterState.Success) {
            navController.navigate(Screen.TeacherDashboard.route) {
                popUpTo(Screen.RoleSelection.route) { inclusive = true }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(BgDark)) {

        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth().background(BgCard).border(1.dp, Border),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextSecondary)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("ஆசிரியர் பதிவு", fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextPrimary)
                Text("Teacher Registration", fontFamily = DMSans, fontSize = 12.sp, color = TextMuted)
            }
            Spacer(Modifier.width(48.dp))
        }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = screenGutter()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(Spacing.xl))

            Text("👩‍🏫", fontSize = 56.sp, textAlign = TextAlign.Center)

            Spacer(Modifier.height(Spacing.md))

            Text(
                text = "புதிய கணக்கை உருவாக்குங்கள்",
                fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold,
                fontSize = 22.sp, color = TextPrimary, textAlign = TextAlign.Center
            )
            Text(
                text = "Create your teacher account — no internet needed",
                fontFamily = DMSans, fontSize = 13.sp,
                color = TextMuted, textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(Spacing.xl))

            // Form card
            Column(
                modifier = Modifier.fillMaxWidth()
                    .background(BgCard, RoundedCornerShape(20.dp))
                    .border(1.dp, Border, RoundedCornerShape(20.dp))
                    .padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                RegField("பள்ளி பெயர் / School Name", schoolName, "e.g. Government Primary School", !isLoading) { schoolName = it }
                RegField("பள்ளி குறியீடு / School Code", schoolCode, "e.g. GOVT-TN-001", !isLoading) { schoolCode = it.uppercase() }
                RegField("ஆசிரியர் பெயர் / Teacher Name", teacherName, "Your full name", !isLoading) { teacherName = it }
                RegField("ஆசிரியர் ID / Teacher ID", teacherCode, "e.g. T-0042", !isLoading) { teacherCode = it.uppercase() }
                RegField("வகுப்பு / Class Name", className, "e.g. 5A", !isLoading) { className = it }
                RegField("PIN (4-6 இலக்கங்கள்)", pin, "• • • •", !isLoading, isPassword = true) { if (it.length <= 6) pin = it }
                RegField("PIN உறுதிப்படுத்து / Confirm PIN", pinConfirm, "• • • •", !isLoading, isPassword = true) { if (it.length <= 6) pinConfirm = it }
            }

            // Error
            if (state is TeacherRegisterViewModel.RegisterState.Error) {
                Spacer(Modifier.height(Spacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .background(ErrorBg, RoundedCornerShape(12.dp))
                        .border(1.dp, Error.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⚠", color = Error, fontSize = 16.sp)
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = (state as TeacherRegisterViewModel.RegisterState.Error).message,
                        color = Error, fontFamily = DMSans, fontSize = 13.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(Spacing.lg))

            EzhilButton(
                label     = "பதிவு செய் / Register",
                onClick   = { vm.register(schoolName, schoolCode, teacherName, teacherCode, className, pin, pinConfirm) },
                modifier  = Modifier.fillMaxWidth().height(54.dp),
                isLoading = isLoading,
            )

            Spacer(Modifier.height(Spacing.md))

            TextButton(onClick = { navController.popBackStack() }) {
                Text(
                    text = "ஏற்கனவே பதிவு செய்தீர்களா? உள்நுழையுங்கள் / Already registered? Log in",
                    color = Cyan, fontFamily = DMSans, fontSize = 13.sp, textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

// ── Reusable form field ────────────────────────────────────────────────────────

@Composable
private fun RegField(
    label: String,
    value: String,
    placeholder: String,
    enabled: Boolean,
    isPassword: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(label, color = TextMuted, fontFamily = DMSans, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = TextMuted.copy(alpha = 0.5f), fontFamily = DMSans) },
            singleLine = true,
            enabled = enabled,
            visualTransformation = if (isPassword)
                androidx.compose.ui.text.input.PasswordVisualTransformation()
            else
                androidx.compose.ui.text.input.VisualTransformation.None,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = Cyan,
                unfocusedBorderColor = Border,
                focusedTextColor     = TextPrimary,
                unfocusedTextColor   = TextPrimary,
                focusedContainerColor   = BgCardElevated,
                unfocusedContainerColor = BgCardElevated,
                cursorColor = Cyan
            )
        )
    }
}
