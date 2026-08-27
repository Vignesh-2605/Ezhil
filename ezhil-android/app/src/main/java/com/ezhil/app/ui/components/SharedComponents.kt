package com.ezhil.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ezhil.app.ui.strings.AppLanguage
import com.ezhil.app.ui.strings.EzhilStrings
import com.ezhil.app.ui.strings.StringKey
import com.ezhil.app.ui.theme.*

// ── EzhilanWidget ─────────────────────────────────────────────────────────────

enum class EzhilanState { IDLE, PROCESSING, CELEBRATING }

@Composable
fun EzhilanWidget(
    state: EzhilanState = EzhilanState.IDLE,
    size: Dp = 80.dp,
    modifier: Modifier = Modifier
) {
    // The rigged Canvas owl (see EzhilanOwl.kt) inside the familiar glowing
    // ring. Same API as the old emoji widget — every call site upgraded.
    val borderColor = when (state) {
        EzhilanState.IDLE        -> CyanDim
        EzhilanState.PROCESSING  -> Amber.copy(alpha = 0.4f)
        EzhilanState.CELEBRATING -> Gold.copy(alpha = 0.6f)
    }
    Box(
        modifier = modifier
            .size(size)
            .background(BgCardElevated, CircleShape)
            .border(2.dp, borderColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        EzhilanOwl(state = state, size = size * 0.86f)
    }
}

// ── StarRow ───────────────────────────────────────────────────────────────────

@Composable
fun StarRow(filled: Int, total: Int = 3, size: Dp = 28.dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        repeat(total) { index ->
            Text(
                text = if (index < filled) "★" else "☆",
                fontSize = size.value.sp,
                color = if (index < filled) Gold else GoldDim
            )
        }
    }
}

// ── RiskBadge ─────────────────────────────────────────────────────────────────

enum class RiskLevel { HIGH, MEDIUM, LOW, UNSCREENED }
enum class BadgeSize { SMALL, MEDIUM, LARGE }

data class RiskConfig(val icon: String, val bg: Color, val text: Color, val labelKey: StringKey)

val RISK_CONFIG = mapOf(
    RiskLevel.HIGH       to RiskConfig("!", RiskHighBg,        RiskHigh,       StringKey.RISK_HIGH),
    RiskLevel.MEDIUM     to RiskConfig("△", RiskMediumBg,     RiskMedium,     StringKey.RISK_MEDIUM),
    RiskLevel.LOW        to RiskConfig("✓", RiskLowBg,         RiskLow,        StringKey.RISK_LOW),
    RiskLevel.UNSCREENED to RiskConfig("○", RiskUnscreenedBg, RiskUnscreened, StringKey.RISK_UNSCREENED),
)

fun String.toRiskLevel() = when (this.lowercase()) {
    "high"   -> RiskLevel.HIGH
    "medium" -> RiskLevel.MEDIUM
    "low"    -> RiskLevel.LOW
    else     -> RiskLevel.UNSCREENED
}

@Composable
fun RiskBadge(level: RiskLevel, language: AppLanguage, size: BadgeSize = BadgeSize.MEDIUM) {
    val config = RISK_CONFIG[level]!!
    val label = EzhilStrings.get(config.labelKey, language)
    val fontSize = when (size) {
        BadgeSize.SMALL  -> 11.sp
        BadgeSize.MEDIUM -> 13.sp
        BadgeSize.LARGE  -> 15.sp
    }
    Row(
        modifier = Modifier
            .background(config.bg, ShapeFull)
            .border(1.dp, config.text.copy(alpha = 0.3f), ShapeFull)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        Text(config.icon, fontSize = fontSize, color = config.text, fontFamily = DMSans, fontWeight = FontWeight.Bold)
        Text(label, color = config.text, fontSize = fontSize, fontFamily = DMSans, fontWeight = FontWeight.SemiBold)
    }
}

// ── ProgressRing ─────────────────────────────────────────────────────────────

@Composable
fun ProgressRing(
    progress: Float,
    size: Dp = 48.dp,
    color: Color = Cyan,
    trackColor: Color = Border,
    strokeWidth: Dp = 4.dp,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "progress_ring"
    )
    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "${(progress * 100).toInt()}% completed" }
    ) {
        val stroke = strokeWidth.toPx()
        val diameter = this.size.minDimension - stroke
        val topLeft = Offset(stroke / 2, stroke / 2)
        val arcSize = Size(diameter, diameter)
        drawArc(color = trackColor, startAngle = 0f, sweepAngle = 360f, useCenter = false,
            topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        drawArc(color = color, startAngle = -90f, sweepAngle = animatedProgress * 360f, useCenter = false,
            topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

// ── SyncStatusBar ────────────────────────────────────────────────────────────

enum class SyncState { IDLE, PENDING, SYNCING, ERROR, OFFLINE }

@Composable
fun SyncStatusBar(
    syncState: SyncState,
    pendingCount: Int = 0,
    language: AppLanguage,
    onRetry: () -> Unit = {}
) {
    if (syncState == SyncState.IDLE) return
    val (bg, textColor, message) = when (syncState) {
        SyncState.PENDING -> Triple(AmberDim, Amber,
            "☁ $pendingCount ${EzhilStrings.get(StringKey.SYNC_PENDING, language)}")
        SyncState.SYNCING -> Triple(CyanDim, Cyan,
            EzhilStrings.get(StringKey.SYNC_IN_PROGRESS, language))
        SyncState.ERROR   -> Triple(ErrorBg, Error,
            EzhilStrings.get(StringKey.SYNC_ERROR, language))
        SyncState.OFFLINE -> Triple(BgCardElevated, TextMuted,
            "⚡ ${EzhilStrings.get(StringKey.OFFLINE_MODE, language)}")
        SyncState.IDLE    -> Triple(Color.Transparent, Color.Transparent, "")
    }
    Row(
        modifier = Modifier.fillMaxWidth().height(36.dp).background(bg),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (syncState == SyncState.SYNCING) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = textColor, strokeWidth = 2.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(message, color = textColor, fontSize = 12.sp, fontFamily = DMSans)
        if (syncState == SyncState.ERROR) {
            TextButton(onClick = onRetry) {
                Text(EzhilStrings.get(StringKey.SYNC_NOW, language), color = textColor, fontSize = 12.sp)
            }
        }
    }
}

// ── EzhilButton ──────────────────────────────────────────────────────────────

@Composable
fun EzhilButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backgroundColor: Color = Cyan,
    textColor: Color = TextOnCyan,
    isLoading: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "btn_scale"
    )
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    Button(
        onClick = {
            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
            onClick()
        },
        enabled = enabled && !isLoading,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            disabledContainerColor = backgroundColor.copy(alpha = 0.35f)
        ),
        interactionSource = interactionSource,
        modifier = modifier.heightIn(min = TouchTarget.student).scale(scale)
    ) {
        if (isLoading) {
            CircularProgressIndicator(color = textColor, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        } else {
            Text(label, color = textColor, fontFamily = DMSans, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

// ── EzhilOutlinedButton ───────────────────────────────────────────────────────

@Composable
fun EzhilOutlinedButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    borderColor: Color = Cyan.copy(alpha = 0.5f),
    textColor: Color = Cyan
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "outline_scale"
    )
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    OutlinedButton(
        onClick = {
            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
            onClick()
        },
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = textColor),
        interactionSource = interactionSource,
        modifier = modifier.heightIn(min = TouchTarget.student).scale(scale)
    ) {
        Text(label, fontFamily = DMSans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = textColor)
    }
}

// ── LanguageToggle ────────────────────────────────────────────────────────────

@Composable
fun LanguageToggle(current: AppLanguage, onToggle: (AppLanguage) -> Unit) {
    val tamActive = current == AppLanguage.TAMIL
    Row(
        modifier = Modifier
            .background(BgCardElevated, ShapeFull)
            .border(1.dp, Border, ShapeFull)
            .padding(Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LangChip("தமிழ்", tamActive) { if (!tamActive) onToggle(AppLanguage.TAMIL) }
        LangChip("EN", !tamActive) { if (tamActive) onToggle(AppLanguage.ENGLISH) }
    }
}

@Composable
private fun LangChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(if (active) Cyan else Color.Transparent, ShapeFull)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontFamily = DMSans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
            color = if (active) TextOnCyan else TextMuted
        )
    }
}

// ── EzhilTopBar ───────────────────────────────────────────────────────────────

@Composable
fun EzhilTopBar(
    title: String,
    subtitle: String? = null,
    language: AppLanguage,
    onToggleLanguage: (AppLanguage) -> Unit,
    onMenuClick: (() -> Unit)? = null,
    onBackClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgCard)
            .border(width = 1.dp, color = Border)
            .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left action
        when {
            onBackClick != null -> IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextSecondary)
            }
            onMenuClick != null -> IconButton(onClick = onMenuClick) {
                Icon(Icons.Default.Menu, contentDescription = "Menu", tint = TextSecondary)
            }
            else -> Spacer(Modifier.size(48.dp))
        }

        // Center title
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
            Text(
                title, fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold,
                fontSize = 17.sp, color = TextPrimary, textAlign = TextAlign.Center
            )
            if (subtitle != null) {
                Text(subtitle, fontFamily = DMSans, fontSize = 10.sp, color = TextMuted,
                    textAlign = TextAlign.Center)
            }
        }

        // Right: extra actions + language toggle
        Row(verticalAlignment = Alignment.CenterVertically) {
            actions()
            LanguageToggle(current = language, onToggle = onToggleLanguage)
        }
    }
}

// ── EzhilBottomNavBar ─────────────────────────────────────────────────────────

enum class NavTab { HOME, LESSONS, LEADERBOARD, PROFILE }

private data class NavTabSpec(
    val tab: NavTab,
    val icon: ImageVector,
    val labelTa: String,
    val labelEn: String
)

private val NAV_TABS = listOf(
    NavTabSpec(NavTab.HOME,        Icons.Default.Home,   "முகப்பு",   "Home"),
    NavTabSpec(NavTab.LESSONS,     Icons.Default.Book,   "பாடங்கள்",  "Lessons"),
    NavTabSpec(NavTab.LEADERBOARD, Icons.Default.Star,   "தரவரிசை",  "Rankings"),
    NavTabSpec(NavTab.PROFILE,     Icons.Default.Person, "சுயவிவரம்", "Profile"),
)

@Composable
fun EzhilBottomNavBar(selected: NavTab, onSelect: (NavTab) -> Unit, language: AppLanguage = AppLanguage.TAMIL) {
    NavigationBar(
        containerColor = BgNavBar,
        tonalElevation = 0.dp,
        modifier = Modifier.border(1.dp, Border, RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp))
    ) {
        NAV_TABS.forEach { spec ->
            val active = spec.tab == selected
            NavigationBarItem(
                selected = active,
                onClick = { onSelect(spec.tab) },
                icon = {
                    Icon(spec.icon, contentDescription = spec.labelEn, modifier = Modifier.size(22.dp))
                },
                label = {
                    Text(
                        if (language == AppLanguage.TAMIL) spec.labelTa else spec.labelEn,
                        fontFamily = BaloTamizha2, fontSize = 10.sp
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor   = Cyan,
                    selectedTextColor   = Cyan,
                    unselectedIconColor = TextMuted,
                    unselectedTextColor = TextMuted,
                    indicatorColor      = CyanDim
                )
            )
        }
    }
}

// ── DarkCard ──────────────────────────────────────────────────────────────────

@Composable
fun DarkCard(
    modifier: Modifier = Modifier,
    accentColor: Color = Cyan,
    showAccentBar: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border)
    ) {
        if (showAccentBar) {
            Box(modifier = Modifier.fillMaxWidth().height(3.dp).background(accentColor))
        }
        Column(modifier = Modifier.padding(Spacing.md), content = content)
    }
}

// ── EzhilGradientCard ─────────────────────────────────────────────────────────

@Composable
fun EzhilGradientCard(
    accentColor: Color = Cyan,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(accentColor.copy(alpha = 0.18f), BgCard),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                ),
                shape = RoundedCornerShape(20.dp)
            )
            .border(1.dp, accentColor.copy(alpha = 0.3f), RoundedCornerShape(20.dp)),
        content = content
    )
}

// ── EzhilSectionLabel ─────────────────────────────────────────────────────────

@Composable
fun EzhilSectionLabel(text: String, color: Color = TextMuted) {
    Text(
        text.uppercase(),
        fontFamily = DMSans,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        color = color,
        letterSpacing = 1.sp
    )
}

// ── EzhilEmptyState ───────────────────────────────────────────────────────────

@Composable
fun EzhilEmptyState(
    emoji: String,
    titleTa: String,
    titleEn: String,
    bodyTa: String = "",
    bodyEn: String = "",
    language: AppLanguage,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(BgCard, RoundedCornerShape(20.dp))
                .border(1.dp, Border, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) { Text(emoji, fontSize = 32.sp) }
        Text(
            if (language == AppLanguage.TAMIL) titleTa else titleEn,
            fontFamily = BaloTamizha2, fontWeight = FontWeight.Bold,
            fontSize = 18.sp, color = TextPrimary, textAlign = TextAlign.Center
        )
        if (bodyTa.isNotEmpty() || bodyEn.isNotEmpty()) {
            Text(
                if (language == AppLanguage.TAMIL) bodyTa else bodyEn,
                fontFamily = DMSans, fontSize = 13.sp, color = TextMuted, textAlign = TextAlign.Center
            )
        }
    }
}

// ── EzhilStatChip ─────────────────────────────────────────────────────────────

@Composable
fun EzhilStatChip(label: String, value: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
    ) {
        Text(value, fontFamily = DMSans, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = color)
        Text(label, fontFamily = DMSans, fontSize = 10.sp, color = TextMuted)
    }
}

// ── GameOption ────────────────────────────────────────────────────────────────

@Composable
fun GameOption(
    text: String,
    isWrong: Boolean,
    isCorrect: Boolean = false,
    onClick: () -> Unit
) {
    val shakeOffset by animateFloatAsState(
        targetValue = if (isWrong) 1f else 0f,
        animationSpec = keyframes {
            durationMillis = 400
            0f at 0; -10f at 50; 10f at 100; -10f at 150; 10f at 200; -5f at 300; 0f at 400
        },
        label = "shake"
    )
    val bgColor     = when { isCorrect -> SuccessBg; isWrong -> ErrorBg; else -> BgCardElevated }
    val borderColor = when { isCorrect -> RiskLow;   isWrong -> Error;   else -> Border }
    val accentBar   = when { isCorrect -> RiskLow;   isWrong -> Error;   else -> Color.Transparent }

    Row(
        modifier = Modifier
            .offset(x = shakeOffset.dp)
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(14.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .heightIn(min = TouchTarget.game),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left accent bar
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(48.dp)
                .background(accentBar, RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
        )
        Spacer(Modifier.width(Spacing.md))
        ResponsiveText(
            text = text,
            style = TextStyle(fontFamily = BaloTamizha2, fontSize = 20.sp, color = TextPrimary),
            modifier = Modifier.padding(top = Spacing.md, bottom = Spacing.md, end = Spacing.md)
        )
    }
}

// ── ResponsiveText (Auto-fitting) ─────────────────────────────────────────────

@Composable
fun ResponsiveText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Start,
    maxLines: Int = 1,
    minFontSize: androidx.compose.ui.unit.TextUnit = 10.sp
) {
    var fontSize by remember { mutableStateOf(style.fontSize) }
    var readyToDraw by remember { mutableStateOf(false) }

    Text(
        text = text,
        style = style.copy(fontSize = fontSize),
        maxLines = maxLines,
        textAlign = textAlign,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        modifier = modifier.scale(if (readyToDraw) 1f else 0f),
        onTextLayout = { result ->
            if (result.hasVisualOverflow && fontSize > minFontSize) {
                fontSize = (fontSize.value * 0.9f).sp
            } else {
                readyToDraw = true
            }
        }
    )
}

@Composable
fun EzhilText(
    key: StringKey,
    language: AppLanguage,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign = TextAlign.Start,
    maxLines: Int = 1
) {
    ResponsiveText(
        text = EzhilStrings.get(key, language),
        style = if (color != Color.Unspecified) style.copy(color = color) else style,
        modifier = modifier,
        textAlign = textAlign,
        maxLines = maxLines
    )
}
