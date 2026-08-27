package com.ezhil.app.ui.screens.teacher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Delete
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
import com.ezhil.app.data.local.SecurePrefs
import com.ezhil.app.data.local.entity.LessonEntity
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
import javax.inject.Inject

@HiltViewModel
class LessonLibraryViewModel @Inject constructor(
    private val db: EzhilDatabase,
    private val prefs: SecurePrefs
) : ViewModel() {

    val allLessons: StateFlow<List<LessonEntity>> = flow {
        val tid = prefs.teacherId ?: run { emit(emptyList<LessonEntity>()); return@flow }
        db.lessonDao().observeByTeacher(tid).collect { emit(it) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun togglePublish(lesson: LessonEntity) {
        viewModelScope.launch {
            if (lesson.isPublished) db.lessonDao().unpublish(lesson.id)
            else db.lessonDao().publish(lesson.id)
        }
    }

    fun deleteLesson(lesson: LessonEntity) {
        viewModelScope.launch { db.lessonDao().delete(lesson) }
    }
}

private data class LibraryTab(val labelTamil: String, val labelEnglish: String, val filterPublished: Boolean?)

private val LIBRARY_TABS = listOf(
    LibraryTab("அனைத்தும்", "All",       null),
    LibraryTab("வெளியிடப்பட்டது", "Published", true),
    LibraryTab("வரைவு",    "Draft",     false),
)

@Composable
fun LessonLibraryScreen(
    navController: NavHostController,
    vm: LessonLibraryViewModel = hiltViewModel(),
    langVm: AppLanguageViewModel = hiltViewModel()
) {
    val language by langVm.language.collectAsState()
    val allLessons by vm.allLessons.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }

    val filtered = remember(allLessons, selectedTab) {
        val filter = LIBRARY_TABS[selectedTab].filterPublished
        if (filter == null) allLessons else allLessons.filter { it.isPublished == filter }
    }

    Scaffold(
        containerColor = BgDark,
        bottomBar = {
            TeacherBottomNavBar(navController = navController, currentRoute = Screen.LessonLibrary.route)
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Screen.LessonStudio.route) },
                containerColor = Amber,
                contentColor = TextOnAmber,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Lesson")
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
                        text = EzhilStrings.get(StringKey.TEACHER_LESSON_LIBRARY, language),
                        fontFamily = BaloTamizha2,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "LESSON LIBRARY",
                        fontFamily = DMSans,
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
                LanguageToggle(current = language, onToggle = { langVm.toggle() })
            }

            // Filter tabs
            LazyRow(
                contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                items(LIBRARY_TABS.size) { index ->
                    val active = index == selectedTab
                    val tab = LIBRARY_TABS[index]
                    val label = if (language == AppLanguage.TAMIL) tab.labelTamil else tab.labelEnglish
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (active) Amber else BgCard,
                                shape = RoundedCornerShape(20.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (active) Amber else Border,
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable { selectedTab = index }
                            .padding(horizontal = screenGutter(), vertical = Spacing.sm)
                    ) {
                        Text(
                            text = label,
                            color = if (active) TextOnAmber else TextMuted,
                            fontFamily = DMSans,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Empty state
            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Book,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(Spacing.md))
                        Text(
                            text = if (language == AppLanguage.TAMIL) "இன்னும் பாடங்கள் இல்லை" else "No lessons yet",
                            fontFamily = BaloTamizha2,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(Spacing.md))
                        EzhilButton(
                            label = "+ ${EzhilStrings.get(StringKey.TEACHER_NEW_LESSON, language)}",
                            onClick = { navController.navigate(Screen.LessonStudio.route) },
                            modifier = Modifier.padding(horizontal = screenGutter()),
                            backgroundColor = Amber,
                            textColor = TextOnAmber
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    items(filtered) { lesson ->
                        var showDeleteDialog by remember { mutableStateOf(false) }

                        if (showDeleteDialog) {
                            AlertDialog(
                                onDismissRequest = { showDeleteDialog = false },
                                containerColor = BgCard,
                                icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Error) },
                                title = { Text(if (language == AppLanguage.TAMIL) "பாடம் நீக்கவா?" else "Delete Lesson?", color = TextPrimary, fontFamily = BaloTamizha2) },
                                text  = { Text(if (language == AppLanguage.TAMIL) "இந்த செயல் மீளாது." else "This cannot be undone.", color = TextMuted, fontFamily = DMSans) },
                                confirmButton = {
                                    TextButton(onClick = { vm.deleteLesson(lesson); showDeleteDialog = false }) {
                                        Text(if (language == AppLanguage.TAMIL) "நீக்கு" else "Delete", color = Error, fontFamily = DMSans, fontWeight = FontWeight.Bold)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showDeleteDialog = false }) {
                                        Text(if (language == AppLanguage.TAMIL) "ரத்து" else "Cancel", color = TextMuted, fontFamily = DMSans)
                                    }
                                }
                            )
                        }

                        LessonLibraryCard(
                            lesson = lesson,
                            language = language,
                            onReview = { navController.navigate(Screen.LessonStudioReview.route(lesson.id)) },
                            onTogglePublish = { vm.togglePublish(lesson) },
                            onDelete = { showDeleteDialog = true }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LessonLibraryCard(
    lesson: LessonEntity,
    language: AppLanguage,
    onReview: () -> Unit,
    onTogglePublish: () -> Unit,
    onDelete: () -> Unit
) {
    // type → color: story=Cyan, vocab=Amber, comprehension=Purple, else=Cyan
    val typeColor = when (lesson.lessonType) {
        "story"         -> Cyan
        "vocab"         -> Amber
        "comprehension" -> Purple
        "listen"        -> RiskLow
        else            -> Cyan
    }
    val typeLabel = when (lesson.lessonType) {
        "story"         -> if (language == AppLanguage.TAMIL) "கதை" else "Story"
        "vocab"         -> if (language == AppLanguage.TAMIL) "சொல்" else "Vocab"
        "comprehension" -> if (language == AppLanguage.TAMIL) "புரிதல்" else "Comprehension"
        "listen"        -> if (language == AppLanguage.TAMIL) "கேட்டல்" else "Listen"
        else            -> lesson.lessonType
    }
    val diffLabel = when (lesson.difficulty) {
        1    -> EzhilStrings.get(StringKey.EASY, language)
        2    -> EzhilStrings.get(StringKey.MEDIUM, language)
        3    -> EzhilStrings.get(StringKey.HARD, language)
        else -> ""
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgCard, RoundedCornerShape(20.dp))
            .border(
                width = 1.dp,
                color = if (lesson.isPublished) typeColor.copy(alpha = 0.4f) else Border,
                shape = RoundedCornerShape(20.dp)
            )
            .padding(Spacing.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Type icon block
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(typeColor.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
                    .border(1.dp, typeColor.copy(alpha = 0.3f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(typeLabel.take(2), fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold,
                    fontSize = 14.sp, color = typeColor)
            }

            Spacer(Modifier.width(Spacing.md))

            Column(modifier = Modifier.weight(1f)) {
                // Status + difficulty chips
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    StatusChip(
                        text = if (lesson.isPublished) "LIVE" else "DRAFT",
                        textColor = if (lesson.isPublished) RiskLow else TextMuted,
                        bg = if (lesson.isPublished) RiskLowBg else BgCardElevated
                    )
                    if (diffLabel.isNotBlank()) {
                        StatusChip(text = diffLabel, textColor = typeColor, bg = typeColor.copy(alpha = 0.12f))
                    }
                    if (lesson.cacheHit) {
                        StatusChip(text = "CACHED", textColor = Cyan, bg = CyanDim)
                    }
                }
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = lesson.title,
                    fontFamily = BaloTamizha2,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = TextPrimary
                )
            }

            // Publish toggle switch
            Switch(
                checked = lesson.isPublished,
                onCheckedChange = { onTogglePublish() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor  = TextPrimary,
                    checkedTrackColor  = Amber,
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = BgCardElevated
                )
            )
        }

        Spacer(Modifier.height(Spacing.sm))

        // Action row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EzhilOutlinedButton(
                label = EzhilStrings.get(StringKey.REVIEW_LESSON, language),
                onClick = onReview,
                modifier = Modifier.weight(1f),
                borderColor = Amber.copy(alpha = 0.5f),
                textColor = Amber
            )
            EzhilButton(
                label = if (lesson.isPublished)
                    (if (language == AppLanguage.TAMIL) "வரைவாக்கு" else "Unpublish")
                else
                    EzhilStrings.get(StringKey.APPROVE_PUBLISH, language),
                onClick = onTogglePublish,
                modifier = Modifier.weight(1f),
                backgroundColor = if (lesson.isPublished) RiskMedium else Amber,
                textColor = if (lesson.isPublished) TextPrimary else TextOnAmber
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Error.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun StatusChip(
    text: String,
    textColor: androidx.compose.ui.graphics.Color,
    bg: androidx.compose.ui.graphics.Color
) {
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(4.dp))
            .padding(horizontal = Spacing.sm, vertical = 2.dp)
    ) {
        Text(
            text = text,
            fontFamily = DMSans,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}
