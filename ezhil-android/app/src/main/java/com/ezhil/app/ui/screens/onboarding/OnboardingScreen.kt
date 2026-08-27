package com.ezhil.app.ui.screens.onboarding

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavHostController
import com.ezhil.app.data.local.SecurePrefs
import com.ezhil.app.ui.components.EzhilButton
import com.ezhil.app.ui.components.LanguageToggle
import com.ezhil.app.ui.navigation.Screen
import com.ezhil.app.ui.strings.EzhilStrings
import com.ezhil.app.ui.strings.StringKey
import com.ezhil.app.ui.theme.*
import com.ezhil.app.ui.viewmodel.AppLanguageViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── ViewModel (unchanged) ─────────────────────────────────────────────────────

@HiltViewModel
class OnboardingViewModel @Inject constructor(private val prefs: SecurePrefs) : ViewModel() {
    fun markDone() { prefs.onboardingDone = true }
}

// ── Slide data ────────────────────────────────────────────────────────────────

private data class Slide(
    val emoji: String,
    val titleKey: StringKey,
    val descKey: StringKey,
    val accentColor: Color,
    val bgTint: Color
)

private val SLIDES = listOf(
    Slide("📖", StringKey.ONBOARDING_1_TITLE, StringKey.ONBOARDING_1_DESC, Cyan,   CyanDim),
    Slide("⭐", StringKey.ONBOARDING_2_TITLE, StringKey.ONBOARDING_2_DESC, Amber,  AmberDim),
    Slide("🗺️", StringKey.ONBOARDING_3_TITLE, StringKey.ONBOARDING_3_DESC, Purple, Purple.copy(alpha = 0.1f)),
)

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun OnboardingScreen(
    navController: NavHostController,
    vm: OnboardingViewModel = hiltViewModel(),
    langVm: AppLanguageViewModel = hiltViewModel()
) {
    val language by langVm.language.collectAsState()
    val pagerState = rememberPagerState(pageCount = { SLIDES.size })
    val scope = rememberCoroutineScope()

    fun proceed() {
        vm.markDone()
        navController.navigate(Screen.RoleSelection.route) {
            popUpTo(Screen.Onboarding.route) { inclusive = true }
        }
    }

    val currentSlide = SLIDES[pagerState.currentPage]

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val slide = SLIDES[page]
            OnboardingPage(
                slide = slide,
                language = language
            )
        }

        // Top row: skip (left) + language toggle (right)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = screenGutter(), vertical = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (pagerState.currentPage < SLIDES.size - 1) {
                TextButton(onClick = { proceed() }) {
                    Text(
                        text = "தவிர் / Skip",
                        color = TextMuted,
                        fontFamily = DMSans,
                        fontSize = 14.sp
                    )
                }
            } else {
                Spacer(Modifier.width(80.dp))
            }
            LanguageToggle(current = language, onToggle = { langVm.toggle() })
        }

        // Bottom: dots + button
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, BgDark.copy(alpha = 0.95f), BgDark)
                    )
                )
                .padding(horizontal = screenGutter(), vertical = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Animated dot indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(SLIDES.size) { index ->
                    PageDot(
                        selected = pagerState.currentPage == index,
                        color = currentSlide.accentColor
                    )
                }
            }

            Spacer(Modifier.height(Spacing.lg))

            val isLast = pagerState.currentPage == SLIDES.size - 1
            EzhilButton(
                label = if (isLast)
                    EzhilStrings.get(StringKey.GET_STARTED, language)
                else
                    EzhilStrings.get(StringKey.NEXT, language),
                onClick = {
                    if (isLast) proceed()
                    else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                },
                backgroundColor = currentSlide.accentColor,
                textColor = TextOnCyan,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            )
        }
    }
}

// ── Single page layout ────────────────────────────────────────────────────────

@Composable
private fun OnboardingPage(slide: Slide, language: com.ezhil.app.ui.strings.AppLanguage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = screenGutter())
            .padding(top = 100.dp, bottom = 220.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Emoji in colored circle — 80dp per spec
        Box(
            modifier = Modifier
                .size(110.dp)
                .background(slide.bgTint, CircleShape)
                .border(2.dp, slide.accentColor.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = slide.emoji, fontSize = 52.sp)
        }

        Spacer(Modifier.height(Spacing.xl))

        // Tamil heading
        Text(
            text = EzhilStrings.get(slide.titleKey, language),
            fontFamily = BaloTamizha2,
            fontWeight = FontWeight.Bold,
            fontSize = 30.sp,
            color = slide.accentColor,
            textAlign = TextAlign.Center,
            lineHeight = 40.sp
        )

        Spacer(Modifier.height(Spacing.md))

        // Description (uses NotoSansTamil for bilingual body)
        Text(
            text = EzhilStrings.get(slide.descKey, language),
            fontFamily = NotoSansTamil,
            fontSize = 16.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 27.sp,
            modifier = Modifier.widthIn(max = 310.dp)
        )
    }
}

// ── Animated page dot ─────────────────────────────────────────────────────────

@Composable
private fun PageDot(selected: Boolean, color: Color) {
    val width: Dp by animateDpAsState(
        targetValue = if (selected) 28.dp else 8.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "dot_width"
    )
    Box(
        modifier = Modifier
            .height(8.dp)
            .width(width)
            .clip(RoundedCornerShape(4.dp))
            .background(if (selected) color else color.copy(alpha = 0.25f))
    )
}
