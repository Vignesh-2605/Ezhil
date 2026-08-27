package com.ezhil.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.ezhil.app.ui.components.*
import com.ezhil.app.ui.navigation.Screen
import com.ezhil.app.ui.strings.EzhilStrings
import com.ezhil.app.ui.strings.StringKey
import com.ezhil.app.ui.theme.*
import com.ezhil.app.ui.viewmodel.AppLanguageViewModel

@Composable
fun RoleSelectionScreen(
    navController: NavHostController,
    langVm: AppLanguageViewModel = hiltViewModel()
) {
    val language by langVm.language.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = screenGutter()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Mascot / greeting
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(BgCardElevated, CircleShape)
                    .border(2.dp, CyanDim, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                EzhilanOwl(state = EzhilanState.IDLE, size = 64.dp)
            }

            Spacer(Modifier.height(Spacing.lg))

            // Heading — bilingual
            EzhilText(
                key = StringKey.WHO_ARE_YOU,
                language = language,
                style = TextStyle(
                    fontFamily = BaloTamizha2,
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )
            )
            EzhilText(
                key = StringKey.ROLE_TITLE_HINT,
                language = language,
                style = TextStyle(
                    fontFamily = DMSans,
                    fontSize = 16.sp,
                    color = TextMuted,
                    textAlign = TextAlign.Center
                )
            )

            Spacer(Modifier.height(Spacing.xxl))

            // Student card — Cyan accent
            val studentLabel = EzhilStrings.get(StringKey.STUDENT_ROLE, language)
            val studentDesc  = EzhilStrings.get(StringKey.ROLE_STUDENT_DESC, language)
            RoleCard(
                emoji = "🎒",
                label = studentLabel,
                description = studentDesc,
                accentColor = Cyan,
                accentBg = CyanDim,
                onClick = { navController.navigate(Screen.StudentLogin.route) }
            )

            Spacer(Modifier.height(Spacing.lg))

            // Teacher card — Amber accent
            val teacherLabel = EzhilStrings.get(StringKey.TEACHER_ROLE, language)
            val teacherDesc  = EzhilStrings.get(StringKey.ROLE_TEACHER_DESC, language)
            RoleCard(
                emoji = "📋",
                label = teacherLabel,
                description = teacherDesc,
                accentColor = Amber,
                accentBg = AmberDim,
                onClick = { navController.navigate(Screen.TeacherLogin.route) }
            )
        }

        // Language toggle — bottom center
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = Spacing.xl),
            contentAlignment = Alignment.Center
        ) {
            LanguageToggle(current = language, onToggle = { langVm.toggle() })
        }
    }
}

@Composable
private fun RoleCard(
    emoji: String,
    label: String,
    description: String,
    accentColor: Color,
    accentBg: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(BgCard, RoundedCornerShape(20.dp))
            .border(1.5.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(Spacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon container with accent background
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(accentBg, RoundedCornerShape(16.dp))
                .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = 32.sp)
        }

        Spacer(Modifier.width(Spacing.md))

        Column(modifier = Modifier.weight(1f)) {
            ResponsiveText(
                text = label,
                style = TextStyle(
                    fontFamily = BaloTamizha2,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = accentColor
                )
            )
            Spacer(Modifier.height(2.dp))
            ResponsiveText(
                text = description,
                style = TextStyle(
                    fontFamily = DMSans,
                    fontSize = 13.sp,
                    color = TextSecondary
                ),
                maxLines = 2
            )
        }

        Spacer(Modifier.width(Spacing.sm))

        // Arrow icon
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(accentBg, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Tap to select $label",
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
