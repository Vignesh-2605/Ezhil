package com.ezhil.app.ui.screens.student

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.ezhil.app.ui.navigation.Screen
import com.ezhil.app.ui.components.*
import com.ezhil.app.ui.strings.AppLanguage
import com.ezhil.app.ui.strings.EzhilStrings
import com.ezhil.app.ui.strings.StringKey
import com.ezhil.app.ui.theme.*
import com.ezhil.app.ui.viewmodel.AppLanguageViewModel
import kotlin.math.cos
import kotlin.math.sin

enum class AchievementType { LESSON_COMPLETE, STREAK, READER_BADGE, WELL_DONE }

@Composable
fun AchievementScreen(
    navController: NavHostController,
    achievementType: AchievementType = AchievementType.WELL_DONE,
    starsEarned: Int = 3,
    langVm: AppLanguageViewModel = hiltViewModel()
) {
    val language by langVm.language.collectAsState()

    // Config driven by achievement type
    val config = when (achievementType) {
        AchievementType.LESSON_COMPLETE -> AchievementConfig(
            badgeEmoji = "🏆",
            accentColor = Gold,
            accentBg = GoldDim,
            glowColor = Gold.copy(alpha = 0.25f),
            titleKey = StringKey.ACHIEVE_SUPER,
            subtitleKey = StringKey.LESSON_COMPLETE,
            rewardLabel = if (language == AppLanguage.TAMIL) "பரிசு பெறு / Claim Reward" else "பரிசு பெறு / Claim Reward",
            rewardColor = Gold,
            dotColors = listOf(Gold, Amber, Gold.copy(alpha = 0.5f), Amber.copy(alpha = 0.6f))
        )
        AchievementType.STREAK -> AchievementConfig(
            badgeEmoji = "🔥",
            accentColor = Amber,
            accentBg = AmberDim,
            glowColor = Amber.copy(alpha = 0.2f),
            titleKey = StringKey.ACHIEVE_STREAK,
            subtitleKey = StringKey.ACHIEVE_LANDMARK,
            rewardLabel = if (language == AppLanguage.TAMIL) "பரிசு பெறு / Claim Reward" else "பரிசு பெறு / Claim Reward",
            rewardColor = Amber,
            dotColors = listOf(Amber, Gold, RiskMedium.copy(alpha = 0.5f), Amber.copy(alpha = 0.4f))
        )
        AchievementType.READER_BADGE -> AchievementConfig(
            badgeEmoji = "📖",
            accentColor = Cyan,
            accentBg = CyanDim,
            glowColor = Cyan.copy(alpha = 0.2f),
            titleKey = StringKey.ACHIEVE_READER,
            subtitleKey = StringKey.ACHIEVE_NEW_BADGE,
            rewardLabel = if (language == AppLanguage.TAMIL) "பரிசு பெறு / Claim Reward" else "பரிசு பெறு / Claim Reward",
            rewardColor = Cyan,
            dotColors = listOf(Cyan, CyanDark, Cyan.copy(alpha = 0.4f), Purple.copy(alpha = 0.4f))
        )
        AchievementType.WELL_DONE -> AchievementConfig(
            badgeEmoji = "⭐",
            accentColor = Purple,
            accentBg = Purple.copy(alpha = 0.15f),
            glowColor = Purple.copy(alpha = 0.2f),
            titleKey = StringKey.ACHIEVE_WELL_DONE,
            subtitleKey = StringKey.ACHIEVE_LANDMARK,
            rewardLabel = if (language == AppLanguage.TAMIL) "பரிசு பெறு / Claim Reward" else "பரிசு பெறு / Claim Reward",
            rewardColor = Purple,
            dotColors = listOf(Purple, Cyan.copy(alpha = 0.5f), Gold.copy(alpha = 0.4f), Purple.copy(alpha = 0.3f))
        )
    }

    // Animation: badge pulse scale
    val infiniteTransition = rememberInfiniteTransition(label = "achievement")
    val badgeScale by infiniteTransition.animateFloat(
        initialValue = 0.92f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            tween(900, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "badge_scale"
    )
    // Animation: confetti dot rotation angle
    val dotAngle by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(12000, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "dot_angle"
    )
    // Animation: glow pulse
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            tween(1400, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    // Confetti dot positions — 12 dots at different radii and offsets
    val dotSpec = remember {
        listOf(
            ConfettiDot(radius = 130f, angleOffset = 0f, size = 8f, colorIdx = 0),
            ConfettiDot(radius = 160f, angleOffset = 30f, size = 5f, colorIdx = 1),
            ConfettiDot(radius = 145f, angleOffset = 60f, size = 9f, colorIdx = 2),
            ConfettiDot(radius = 170f, angleOffset = 90f, size = 5f, colorIdx = 3),
            ConfettiDot(radius = 135f, angleOffset = 120f, size = 7f, colorIdx = 0),
            ConfettiDot(radius = 155f, angleOffset = 150f, size = 6f, colorIdx = 1),
            ConfettiDot(radius = 140f, angleOffset = 180f, size = 9f, colorIdx = 2),
            ConfettiDot(radius = 165f, angleOffset = 210f, size = 5f, colorIdx = 3),
            ConfettiDot(radius = 130f, angleOffset = 240f, size = 8f, colorIdx = 0),
            ConfettiDot(radius = 155f, angleOffset = 270f, size = 6f, colorIdx = 1),
            ConfettiDot(radius = 145f, angleOffset = 300f, size = 7f, colorIdx = 2),
            ConfettiDot(radius = 170f, angleOffset = 330f, size = 5f, colorIdx = 3),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark),
        contentAlignment = Alignment.Center
    ) {
        // ── Confetti-feel background dots ─────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    dotSpec.forEachIndexed { i, dot ->
                        val angleRad = Math.toRadians(
                            (dot.angleOffset + dotAngle * if (i % 2 == 0) 1f else -0.7f).toDouble()
                        ).toFloat()
                        val x = cx + dot.radius * cos(angleRad)
                        val y = cy + dot.radius * sin(angleRad)
                        val c = config.dotColors[dot.colorIdx % config.dotColors.size]
                        drawCircle(
                            color = c.copy(alpha = 0.35f),
                            radius = dot.size,
                            center = Offset(x, y)
                        )
                    }
                    // A few static scatter dots for texture
                    listOf(
                        Offset(cx * 0.2f, cy * 0.4f) to 4f,
                        Offset(cx * 1.8f, cy * 0.6f) to 6f,
                        Offset(cx * 0.3f, cy * 1.6f) to 5f,
                        Offset(cx * 1.7f, cy * 1.4f) to 4f,
                        Offset(cx * 0.5f, cy * 0.9f) to 3f,
                        Offset(cx * 1.5f, cy * 1.1f) to 3f,
                    ).forEachIndexed { i, (off, r) ->
                        drawCircle(
                            color = config.dotColors[i % config.dotColors.size].copy(alpha = 0.18f),
                            radius = r,
                            center = off
                        )
                    }
                }
        )

        // ── Main content ──────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = screenGutter()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            // Ezhilan mascot — CELEBRATING state, big
            EzhilanWidget(state = EzhilanState.CELEBRATING, size = 120.dp)

            // Stars with gold glow
            Box(contentAlignment = Alignment.Center) {
                // Glow halo behind stars
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(
                            Gold.copy(alpha = glowAlpha * 0.3f),
                            CircleShape
                        )
                )
                StarRow(filled = starsEarned.coerceIn(0, 3), total = 3, size = 36.dp)
            }

            // Badge circle — pulsing
            Box(
                modifier = Modifier
                    .size(128.dp)
                    .graphicsLayer(scaleX = badgeScale, scaleY = badgeScale)
                    .background(config.accentBg, CircleShape)
                    .border(3.dp, config.accentColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Inner glow ring
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .background(
                            config.glowColor.copy(alpha = glowAlpha),
                            CircleShape
                        )
                )
                Text(config.badgeEmoji, fontSize = 52.sp)
            }

            // Achievement title (bilingual)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text(
                    EzhilStrings.get(config.titleKey, language),
                    fontFamily = BaloTamizha2,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    color = config.accentColor,
                    textAlign = TextAlign.Center
                )
                Text(
                    EzhilStrings.get(config.subtitleKey, language),
                    fontFamily = DMSans,
                    fontSize = 14.sp,
                    color = TextMuted,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(Spacing.xs))

            // New badge pill
            Row(
                modifier = Modifier
                    .background(config.accentBg, ShapeFull)
                    .border(1.dp, config.accentColor.copy(alpha = 0.4f), ShapeFull)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                Text("🏅", fontSize = 16.sp)
                Text(
                    EzhilStrings.get(StringKey.ACHIEVE_NEW_BADGE, language),
                    fontFamily = BaloTamizha2,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = config.accentColor
                )
            }

            Spacer(Modifier.height(Spacing.xs))

            // Primary CTA — the reward is already earned; this continues to My Journey
            // so the child sees their new badge on the map.
            EzhilButton(
                label = config.rewardLabel,
                onClick = {
                    navController.navigate(Screen.MyJourney.route) {
                        popUpTo(Screen.StudentHome.route) { inclusive = false }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = config.rewardColor,
                textColor = if (config.rewardColor == Cyan || config.rewardColor == Gold) TextOnCyan else TextPrimary
            )

            // Secondary CTA: Go Home — outlined
            EzhilOutlinedButton(
                label = EzhilStrings.get(StringKey.ACHIEVE_GO_HOME, language),
                onClick = { navController.popBackStack() },
                modifier = Modifier.fillMaxWidth(),
                borderColor = config.accentColor.copy(alpha = 0.5f),
                textColor = config.accentColor
            )
        }
    }
}

// ── Data classes ──────────────────────────────────────────────────────────────

private data class AchievementConfig(
    val badgeEmoji: String,
    val accentColor: Color,
    val accentBg: Color,
    val glowColor: Color,
    val titleKey: StringKey,
    val subtitleKey: StringKey,
    val rewardLabel: String,
    val rewardColor: Color,
    val dotColors: List<Color>
)

private data class ConfettiDot(
    val radius: Float,
    val angleOffset: Float,
    val size: Float,
    val colorIdx: Int
)
