package com.ezhil.app.ui.screens.teacher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezhil.app.sync.SyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.navigation.NavHostController
import com.ezhil.app.data.local.EzhilDatabase
import com.ezhil.app.data.local.entity.LessonEntity
import com.ezhil.app.data.remote.dto.LessonContent
import com.ezhil.app.ui.components.*
import com.ezhil.app.ui.navigation.Screen
import com.ezhil.app.ui.strings.AppLanguage
import com.ezhil.app.ui.strings.EzhilStrings
import com.ezhil.app.ui.strings.StringKey
import com.ezhil.app.ui.theme.*
import com.ezhil.app.ui.viewmodel.AppLanguageViewModel
import com.squareup.moshi.Moshi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LessonStudioReviewViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val db: EzhilDatabase,
    private val moshi: Moshi
) : ViewModel() {

    private val _lesson = MutableStateFlow<LessonEntity?>(null)
    val lesson: StateFlow<LessonEntity?> = _lesson

    private val _content = MutableStateFlow<LessonContent?>(null)
    val content: StateFlow<LessonContent?> = _content

    private val _published = MutableStateFlow(false)
    val published: StateFlow<Boolean> = _published

    fun load(lessonId: String) {
        viewModelScope.launch {
            val l = db.lessonDao().getById(lessonId)
            _lesson.value = l
            l?.let {
                try {
                    _content.value = moshi.adapter(LessonContent::class.java).fromJson(it.contentJson)
                } catch (e: Exception) {
                    _content.value = LessonContent(
                        title = it.title,
                        passage = com.ezhil.app.data.remote.dto.LessonPassage(lines = it.contentJson.split("\n"))
                    )
                }
            }
        }
    }

    fun publish() {
        val id = _lesson.value?.id ?: return
        viewModelScope.launch {
            db.lessonDao().publish(id)
            _published.value = true
            // Push straight away. The periodic worker runs every 15 minutes,
            // and a teacher who taps Publish expects students to get it now.
            SyncWorker.syncNow(appContext)
        }
    }
}

@Composable
fun LessonStudioReviewScreen(
    navController: NavHostController,
    lessonId: String,
    vm: LessonStudioReviewViewModel = hiltViewModel(),
    langVm: AppLanguageViewModel = hiltViewModel()
) {
    val language by langVm.language.collectAsState()
    val lesson by vm.lesson.collectAsState()
    val content by vm.content.collectAsState()
    val published by vm.published.collectAsState()

    LaunchedEffect(lessonId) { vm.load(lessonId) }

    // Published success screen
    if (published) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BgDark),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(Spacing.xl)
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .background(SuccessBg, RoundedCornerShape(48.dp))
                        .border(2.dp, Success.copy(alpha = 0.4f), RoundedCornerShape(48.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Success,
                        modifier = Modifier.size(52.dp)
                    )
                }
                Spacer(Modifier.height(Spacing.lg))
                Text(
                    text = "பாடம் வெளியிடப்பட்டது!",
                    fontFamily = BaloTamizha2,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = Success
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = "Lesson Published!",
                    fontFamily = DMSans,
                    fontSize = 14.sp,
                    color = TextMuted
                )
                Spacer(Modifier.height(Spacing.xl))
                EzhilButton(
                    label = EzhilStrings.get(StringKey.DASHBOARD, language),
                    onClick = {
                        navController.navigate(Screen.TeacherDashboard.route) {
                            popUpTo(Screen.TeacherDashboard.route) { inclusive = true }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = Amber,
                    textColor = TextOnAmber
                )
            }
        }
        return
    }

    lesson?.let { l ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BgDark)
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
                        text = EzhilStrings.get(StringKey.REVIEW_LESSON, language),
                        fontFamily = BaloTamizha2,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = l.title.take(28),
                        fontFamily = DMSans,
                        fontSize = 12.sp,
                        color = TextMuted
                    )
                }
                LanguageToggle(current = language, onToggle = { langVm.toggle() })
            }

            // Meta info row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgCard)
                    .padding(horizontal = screenGutter(), vertical = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Lesson type chip
                val typeColor = when (l.lessonType) {
                    "story" -> Cyan; "vocab" -> Amber; "comprehension" -> Purple; else -> Cyan
                }
                MetaChip(l.lessonType.uppercase(), typeColor)

                // Difficulty chip
                val diffLabel = when (l.difficulty) {
                    1 -> EzhilStrings.get(StringKey.EASY, language)
                    2 -> EzhilStrings.get(StringKey.MEDIUM, language)
                    3 -> EzhilStrings.get(StringKey.HARD, language)
                    else -> ""
                }
                if (diffLabel.isNotBlank()) MetaChip(diffLabel, Amber)

                // Cache chip
                if (l.cacheHit) MetaChip("CACHED", Cyan)

                Spacer(Modifier.weight(1f))
                Text(
                    text = if (language == AppLanguage.TAMIL) "மதிப்பாய்வு" else "Preview",
                    fontFamily = DMSans,
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }

            // Reader area — dyslexia spec
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(Spacing.md)
                    .background(BgReader, RoundedCornerShape(20.dp))
                    .border(1.dp, Border.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                    .padding(Spacing.lg)
                    .verticalScroll(rememberScrollState())
            ) {
                // Lesson title in reader
                Text(
                    text = l.title,
                    style = TextStyle(
                        fontFamily    = ReaderConstraints.FontFamily,
                        fontSize      = 20.sp,
                        fontWeight    = FontWeight.Bold,
                        color         = ReaderConstraints.TextColor
                    )
                )
                Spacer(Modifier.height(Spacing.md))
                // Passage lines
                content?.passage?.lines?.forEach { line ->
                    Text(
                        text = line,
                        style = TextStyle(
                            fontFamily    = ReaderConstraints.FontFamily,
                            fontSize      = ReaderConstraints.FontSize,
                            lineHeight    = ReaderConstraints.LineHeight,
                            letterSpacing = ReaderConstraints.LetterSpacing,
                            color         = ReaderConstraints.TextColor
                        )
                    )
                    Spacer(Modifier.height(Spacing.sm))
                }
            }

            // Action bar at bottom
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgCard)
                    .border(width = 1.dp, color = Border)
                    .padding(Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                EzhilOutlinedButton(
                    label = EzhilStrings.get(StringKey.SAVE_DRAFT, language),
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.weight(1f)
                )
                EzhilButton(
                    label = EzhilStrings.get(StringKey.APPROVE_PUBLISH, language),
                    onClick = { vm.publish() },
                    modifier = Modifier.weight(1f),
                    backgroundColor = Amber,
                    textColor = TextOnAmber
                )
            }
        }
    } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Amber)
    }
}

@Composable
private fun MetaChip(text: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .padding(horizontal = Spacing.sm, vertical = 3.dp)
    ) {
        Text(
            text = text,
            fontFamily = DMSans,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            color = color
        )
    }
}
