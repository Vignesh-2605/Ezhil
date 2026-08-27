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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
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
import com.ezhil.app.ui.strings.AppLanguage
import com.ezhil.app.ui.strings.EzhilStrings
import com.ezhil.app.ui.strings.StringKey
import com.ezhil.app.ui.theme.*
import com.ezhil.app.ui.viewmodel.AppLanguageViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class RosterManagementViewModel @Inject constructor(
    private val db: EzhilDatabase,
    private val prefs: SecurePrefs
) : ViewModel() {

    val students: StateFlow<List<StudentEntity>> = flow {
        val tid = prefs.teacherId ?: return@flow
        emitAll(db.studentDao().observeByTeacher(tid))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Surfaced in the add-student sheet. Silently dropping a blank name or a
    // duplicate — which is what happened before — leaves the teacher believing
    // the child was added.
    private val _addError = MutableStateFlow<String?>(null)
    val addError: StateFlow<String?> = _addError

    fun clearAddError() { _addError.value = null }

    /** @return true when the student was saved. */
    suspend fun addStudent(name: String, dob: String?): Boolean {
        val teacherId = prefs.teacherId
        if (teacherId == null) {
            _addError.value = "No teacher session on this device."
            return false
        }
        val clean = name.trim()
        if (clean.isEmpty()) {
            _addError.value = "பெயர் தேவை / Name is required"
            return false
        }
        val duplicate = db.studentDao().countByTeacherAndName(teacherId, clean) > 0
        if (duplicate) {
            _addError.value = "பெயர் ஏற்கனவே உள்ளது / A student with this name already exists"
            return false
        }

        db.studentDao().upsert(
            StudentEntity(
                id = UUID.randomUUID().toString(),
                teacherId = teacherId,
                name = clean,
                dob = dob?.ifBlank { null },
                syncStatus = "pending"
            )
        )
        _addError.value = null
        return true
    }
}

@Composable
fun RosterManagementScreen(
    navController: NavHostController,
    vm: RosterManagementViewModel = hiltViewModel(),
    langVm: AppLanguageViewModel = hiltViewModel()
) {
    val language by langVm.language.collectAsState()
    val students by vm.students.collectAsState()
    val addError by vm.addError.collectAsState()
    val scope = rememberCoroutineScope()
    var showDialog by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var dob by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }

    val filtered = remember(students, searchQuery) {
        if (searchQuery.isBlank()) students
        else students.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Scaffold(
        containerColor = BgDark,
        bottomBar = {
            TeacherBottomNavBar(navController = navController, currentRoute = Screen.RosterManagement.route)
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showDialog = true },
                containerColor = Amber,
                contentColor = TextOnAmber,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add student")
            }
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
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextSecondary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = EzhilStrings.get(StringKey.ROSTER, language),
                        fontFamily = BaloTamizha2,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "STUDENT ROSTER",
                        fontFamily = DMSans,
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
                LanguageToggle(current = language, onToggle = { langVm.toggle() })
            }

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = screenGutter(), vertical = Spacing.sm),
                placeholder = {
                    EzhilText(
                        key = StringKey.SEARCH_HINT,
                        language = language,
                        style = TextStyle(color = TextMuted.copy(alpha = 0.6f), fontFamily = DMSans)
                    )
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted)
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor      = Amber,
                    unfocusedBorderColor    = Border,
                    focusedTextColor        = TextPrimary,
                    unfocusedTextColor      = TextPrimary,
                    focusedContainerColor   = BgCardElevated,
                    unfocusedContainerColor = BgCardElevated,
                    cursorColor             = Amber
                )
            )

            // Count badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = screenGutter(), vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(AmberDim, RoundedCornerShape(8.dp))
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                ) {
                    val studentsLabel = EzhilStrings.get(StringKey.STUDENTS_COUNT, language).lowercase()
                    ResponsiveText(
                        text = "${filtered.size} $studentsLabel",
                        style = TextStyle(
                            fontFamily = DMSans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = Amber
                        )
                    )
                }
            }

            // Student list
            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.People,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(Modifier.height(Spacing.md))
                        EzhilText(
                            key = StringKey.NO_STUDENTS_FOUND,
                            language = language,
                            style = TextStyle(
                                fontFamily = DMSans,
                                color = TextMuted,
                                fontSize = 14.sp
                            )
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    items(filtered) { student ->
                        RosterStudentCard(student = student, language = language)
                    }
                }
            }
        }
    }

    // Add student dialog
    if (showDialog) {
        AlertDialog(
            containerColor = BgCard,
            onDismissRequest = { showDialog = false; name = ""; dob = "" },
            title = {
                Text(
                    text = EzhilStrings.get(StringKey.ADD_STUDENT, language),
                    fontFamily = BaloTamizha2,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = TextPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = {
                            Text(
                                EzhilStrings.get(StringKey.STUDENT_NAME, language),
                                color = TextMuted
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor      = Amber,
                            unfocusedBorderColor    = Border,
                            focusedTextColor        = TextPrimary,
                            unfocusedTextColor      = TextPrimary,
                            focusedContainerColor   = BgCardElevated,
                            unfocusedContainerColor = BgCardElevated,
                            cursorColor             = Amber
                        )
                    )
                    addError?.let { msg ->
                        Text(
                            msg,
                            fontFamily = DMSans,
                            fontSize = 13.sp,
                            color = RiskHigh,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    OutlinedTextField(
                        value = dob,
                        onValueChange = { dob = it },
                        label = {
                            Text(
                                EzhilStrings.get(StringKey.DATE_OF_BIRTH, language),
                                color = TextMuted
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("YYYY-MM-DD", color = TextMuted.copy(0.5f)) },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor      = Amber,
                            unfocusedBorderColor    = Border,
                            focusedTextColor        = TextPrimary,
                            unfocusedTextColor      = TextPrimary,
                            focusedContainerColor   = BgCardElevated,
                            unfocusedContainerColor = BgCardElevated,
                            cursorColor             = Amber
                        )
                    )
                }
            },
            confirmButton = {
                EzhilButton(
                    label = EzhilStrings.get(StringKey.SAVE, language),
                    onClick = {
                        // Only close on success — dismissing regardless is how a
                        // rejected blank or duplicate name looked like it saved.
                        scope.launch {
                            if (vm.addStudent(name, dob)) {
                                showDialog = false; name = ""; dob = ""
                            }
                        }
                    },
                    // Deliberately always enabled. A disabled Save gives a
                    // teacher a dead button and no reason why; letting it
                    // through surfaces "Name is required" instead.
                    backgroundColor = Amber,
                    textColor = TextOnAmber
                )
            },
            dismissButton = {
                TextButton(onClick = { vm.clearAddError(); showDialog = false; name = ""; dob = "" }) {
                    EzhilText(
                        key = StringKey.CANCEL,
                        language = language,
                        style = TextStyle(color = TextMuted)
                    )
                }
            }
        )
    }
}

@Composable
private fun RosterStudentCard(student: StudentEntity, language: AppLanguage) {
    val initial = student.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val avatarColors = listOf(Cyan, Amber, Purple, RiskLow, RiskMedium)
    val avatarColor = avatarColors[student.name.length % avatarColors.size]

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgCard, RoundedCornerShape(20.dp))
            .border(1.dp, Border, RoundedCornerShape(20.dp))
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
            student.dob?.let { dobStr ->
                Text(
                    text = dobStr,
                    fontFamily = DMSans,
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
        }
        RiskBadge(level = student.riskLevel.toRiskLevel(), language = language, size = BadgeSize.SMALL)
    }
}
