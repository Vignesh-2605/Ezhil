package com.ezhil.app.ui.screens.student

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import javax.inject.Inject

@HiltViewModel
class MyLessonsViewModel @Inject constructor(
    private val db: EzhilDatabase,
    private val prefs: SecurePrefs
) : ViewModel() {

    data class UiState(
        val lessons: List<LessonEntity> = emptyList(),
        val completedLessonIds: Set<String> = emptySet()
    )

    val uiState: StateFlow<UiState> = flow {
        val studentId = prefs.activeStudentId
        val progressFlow = if (studentId != null)
            db.lessonProgressDao().observeByStudent(studentId)
        else
            flowOf(emptyList())

        combine(
            db.lessonDao().observePublished(),
            progressFlow
        ) { lessons, progress ->
            UiState(
                lessons = lessons,
                completedLessonIds = progress
                    .filter { it.completedAt != null }
                    .map { it.lessonId }
                    .toSet()
            )
        }.collect { emit(it) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())
}

private val LESSON_TABS = listOf(
    StringKey.LESSON_TAB_ALL    to null,
    StringKey.LESSON_TAB_STORY  to "story",
    StringKey.LESSON_TAB_VOCAB  to "vocab",
    StringKey.LESSON_TAB_QUIZ   to "comprehension",
    StringKey.LESSON_TAB_LISTEN  to "listen",
)

@Composable
fun MyLessonsScreen(
    navController: NavHostController,
    vm: MyLessonsViewModel = hiltViewModel(),
    langVm: AppLanguageViewModel = hiltViewModel()
) {
    val language by langVm.language.collectAsState()
    val uiState  by vm.uiState.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }

    val filtered = remember(uiState.lessons, selectedTab) {
        val filter = LESSON_TABS[selectedTab].second
        if (filter == null) uiState.lessons else uiState.lessons.filter { it.lessonType == filter }
    }

    Column(modifier = Modifier.fillMaxSize().background(BgDark)) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = screenGutter(), vertical = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = TextMuted)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                EzhilText(
                    key = StringKey.MY_LESSONS,
                    language = language,
                    style = TextStyle(fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold,
                        fontSize = 18.sp, color = TextPrimary)
                )
                Text("MY LESSONS", fontFamily = DMSans, fontSize = 12.sp, color = TextMuted)
            }
            LanguageToggle(current = language, onToggle = { langVm.toggle() })
        }

        // Filter tab strip
        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier.padding(vertical = Spacing.sm)
        ) {
            items(LESSON_TABS.size) { index ->
                val active = index == selectedTab
                Box(
                    modifier = Modifier
                        .background(if (active) Cyan else BgCard, RoundedCornerShape(20.dp))
                        .border(1.dp, if (active) Cyan else Border, RoundedCornerShape(20.dp))
                        .padding(horizontal = screenGutter(), vertical = Spacing.sm)
                        .clickable { selectedTab = index }
                ) {
                    ResponsiveText(
                        text = EzhilStrings.get(LESSON_TABS[index].first, language),
                        style = TextStyle(color = if (active) TextOnCyan else TextMuted,
                        fontFamily = DMSans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    )
                }
            }
        }

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    Box(
                        modifier = Modifier.size(80.dp).background(BgCard, RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) { Text("📖", fontSize = 36.sp) }
                    EzhilText(
                        key = StringKey.MY_ASSESSMENTS_EMPTY,
                        language = language,
                        style = TextStyle(fontFamily = BaloTamizha2,
                        fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextPrimary)
                    )
                    EzhilText(
                        key = StringKey.NO_LESSONS_STUDENT,
                        language = language,
                        textAlign = TextAlign.Center,
                        style = TextStyle(color = TextMuted, fontFamily = NotoSansTamil, fontSize = 14.sp),
                        modifier = Modifier.widthIn(max = 260.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                items(filtered) { lesson ->
                    LessonCard(
                        lesson = lesson,
                        isCompleted = lesson.id in uiState.completedLessonIds,
                        language = language,
                        onClick = { navController.navigate(Screen.LessonPlayer.route(lesson.id)) }
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(6.dp).background(Success, CircleShape))
                        Spacer(Modifier.width(Spacing.xs))
                        EzhilText(
                            key = StringKey.ALL_SYNCED,
                            language = language,
                            style = TextStyle(color = TextMuted, fontFamily = DMSans, fontSize = 12.sp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LessonCard(lesson: LessonEntity, isCompleted: Boolean, language: AppLanguage, onClick: () -> Unit) {
    val typeIcon  = when (lesson.lessonType) {
        "story" -> "📖"; "vocab" -> "🔤"; "listen" -> "🎧"; "comprehension" -> "💡"; else -> "📚"
    }
    val typeKey = when (lesson.lessonType) {
        "story" -> StringKey.LESSON_TAB_STORY; "vocab" -> StringKey.LESSON_TAB_VOCAB
        "comprehension" -> StringKey.LESSON_TAB_QUIZ; "listen" -> StringKey.LESSON_TAB_LISTEN; else -> StringKey.MY_LESSONS
    }
    val typeColor = when (lesson.lessonType) {
        "story" -> Cyan; "vocab" -> Amber; "comprehension" -> Purple; "listen" -> RiskLow; else -> Cyan
    }
    val accentColor = if (isCompleted) Success else typeColor

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) { Text(if (isCompleted) "✓" else typeIcon, fontSize = 24.sp) }

            Spacer(Modifier.width(Spacing.md))

            Column(modifier = Modifier.weight(1f)) {
                val label = if (isCompleted) EzhilStrings.get(StringKey.COMPLETED, language)
                            else EzhilStrings.get(typeKey, language)
                ResponsiveText(
                    text = label,
                    style = TextStyle(color = accentColor, fontFamily = DMSans,
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                )
                ResponsiveText(
                    text = lesson.title,
                    style = TextStyle(fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold,
                    fontSize = 16.sp, color = TextPrimary)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text("⏱ ${lesson.difficulty * 5} min",
                        color = TextMuted, fontFamily = DMSans, fontSize = 12.sp)
                    Text("★ ${lesson.difficulty * 50} pts",
                        color = Gold, fontFamily = DMSans, fontSize = 12.sp)
                }
            }

            ProgressRing(
                progress = if (isCompleted) 1f else 0f,
                size = 36.dp,
                color = accentColor
            )
        }
    }
}
