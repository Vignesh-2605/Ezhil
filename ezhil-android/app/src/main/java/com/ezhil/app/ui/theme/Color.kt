package com.ezhil.app.ui.theme

import androidx.compose.ui.graphics.Color

// ── DARK BASE PALETTE ─────────────────────────────────────────────────────────
val BgDark          = Color(0xFF0A0D12)   // Main background
val BgCard          = Color(0xFF141820)   // Card / surface
val BgCardElevated  = Color(0xFF1C2230)   // Elevated card, input background
val BgNavBar        = Color(0xFF101420)   // Bottom nav bar

// ── ACCENT COLOURS — unified with web (docs/design-brief.md R1) ──────────────
val Cyan            = Color(0xFF62F9EE)   // Brand cyan — buttons, active icons
val CyanDark        = Color(0xFF4DD9CF)
val CyanDim         = Color(0x3362F9EE)   // 20% opacity cyan for subtle fills
val Amber           = Color(0xFFFFB955)   // Streak, rewards only
val AmberDark       = Color(0xFFD99B45)
val AmberDim        = Color(0x33FFB955)
val Gold            = Color(0xFFFFD700)   // Stars — filled
val GoldDim         = Color(0xFF3A3F4E)   // Stars — empty track
val Purple          = Color(0xFF7C3AED)   // Lesson category accent

// ── TEXT ─────────────────────────────────────────────────────────────────────
val TextPrimary     = Color(0xFFFFFFFF)
val TextSecondary   = Color(0xFFB0B8C8)
val TextMuted       = Color(0xFF6B7C93)
val TextOnCyan      = Color(0xFF0A0D12)   // Dark text on cyan buttons
val TextOnAmber     = Color(0xFF0A0D12)
val TextReader      = Color(0xFF1A1200)   // Warm black for lesson reader

// ── BORDERS / DIVIDERS ────────────────────────────────────────────────────────
val Border          = Color(0xFF2A3142)
val BorderFocus     = Cyan
val Divider         = Color(0xFF1C2230)

// ── STATE ─────────────────────────────────────────────────────────────────────
val Success         = Color(0xFF4CAF82)
val SuccessBg       = Color(0xFF152D1F)
val Error           = Color(0xFFE53935)
val ErrorBg         = Color(0xFF2D1515)
val InputBg         = BgCardElevated

// ── RISK SYSTEM — always paired with icon + text label ────────────────────────
val RiskHigh        = Color(0xFFE53935)
val RiskHighBg      = Color(0xFF2D1515)
val RiskMedium      = Color(0xFFFB8C00)
val RiskMediumBg    = Color(0xFF2D1F00)
val RiskLow         = Color(0xFF43A047)
val RiskLowBg       = Color(0xFF152D15)
val RiskUnscreened  = Color(0xFF6B7C93)
val RiskUnscreenedBg= BgCardElevated

// ── LESSON READER — dyslexia spec, NEVER override ────────────────────────────
val BgReader        = Color(0xFFFFFDF5)   // Cream — reader background only
