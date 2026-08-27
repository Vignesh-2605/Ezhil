package com.ezhil.app.ui.screens

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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.ezhil.app.data.local.SecurePrefs
import com.ezhil.app.ui.components.EzhilanOwl
import com.ezhil.app.ui.components.EzhilanState
import com.ezhil.app.ui.navigation.Screen
import com.ezhil.app.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(navController: NavHostController, securePrefs: SecurePrefs) {
    // Pulsing scale animation for the mascot circle
    val infiniteTransition = rememberInfiniteTransition(label = "mascot_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.94f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            tween(1400, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "scale"
    )
    // Glow ring pulse (alpha)
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            tween(1400, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    var progress by remember { mutableFloatStateOf(0f) }
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 1400, easing = LinearEasing),
        label = "progress"
    )

    LaunchedEffect(Unit) {
        progress = 1f
        delay(1700)
        val destination = when {
            !securePrefs.onboardingDone          -> Screen.Onboarding.route
            securePrefs.activeStudentId != null  -> Screen.StudentHome.route
            // A teacher session may be offline-only (no server token) —
            // teacherId alone is enough to restore it.
            securePrefs.authToken != null ||
                securePrefs.teacherId != null    -> Screen.TeacherDashboard.route
            else                                 -> Screen.RoleSelection.route
        }
        navController.navigate(destination) {
            popUpTo(Screen.Splash.route) { inclusive = true }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark),
        contentAlignment = Alignment.Center
    ) {
        // Subtle radial glow behind mascot
        Box(
            modifier = Modifier
                .size(280.dp)
                .align(Alignment.Center)
                .background(
                    Brush.radialGradient(listOf(CyanDim, BgDark)),
                    CircleShape
                )
                .blur(60.dp)
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Mascot circle — 120dp with cyan glow border
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .graphicsLayer(scaleX = scale, scaleY = scale)
                    .background(BgCardElevated, CircleShape)
                    .border(3.dp, Cyan.copy(alpha = glowAlpha), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                // Inner glow ring (thinner, more transparent)
                Box(
                    modifier = Modifier
                        .size(108.dp)
                        .border(1.dp, CyanDim, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    EzhilanOwl(state = EzhilanState.IDLE, size = 86.dp)
                }
            }

            Spacer(Modifier.height(Spacing.lg))

            // App name — bilingual stacked
            Text(
                text = "எழில்",
                fontFamily = BaloTamizha2,
                fontWeight = FontWeight.Bold,
                fontSize = 44.sp,
                color = TextPrimary
            )
            Text(
                text = "Ezhil",
                fontFamily = DMSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                color = Cyan,
                letterSpacing = 4.sp
            )

            Spacer(Modifier.height(Spacing.sm))

            // Tagline bilingual
            Text(
                text = "எழுத்து கற்று, தன்னம்பிக்கை வளர்",
                fontFamily = NotoSansTamil,
                fontSize = 13.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Screen • Learn • Grow",
                fontFamily = DMSans,
                fontSize = 12.sp,
                color = TextMuted,
                letterSpacing = 1.sp
            )

            Spacer(Modifier.height(Spacing.xxl))

            // Animated progress bar
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                Box(
                    modifier = Modifier
                        .width(220.dp)
                        .height(4.dp)
                        .background(Border, RoundedCornerShape(2.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedProgress)
                            .background(
                                Brush.horizontalGradient(listOf(Cyan, CyanDark)),
                                RoundedCornerShape(2.dp)
                            )
                    )
                }
                Text(
                    text = "தயாராகிறது... / Loading...",
                    fontFamily = DMSans,
                    fontSize = 12.sp,
                    color = TextMuted
                )
            }
        }

        // Version footer
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = Spacing.xl)
        ) {
            Text(
                text = "v1.0.0 • Powered by AI  |  © 2024 Ezhil Education",
                fontFamily = DMSans,
                fontSize = 12.sp,
                color = TextMuted.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
        }
    }
}
