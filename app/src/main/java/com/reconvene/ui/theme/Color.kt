package com.reconvene.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Color tokens. The values follow the Timetable direction (light and dark); the names are the
// original design system's.
@Immutable
data class ReconveneColors(
    // Surfaces
    val canvas: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceVariant: Color,
    val surfaceDim: Color,
    // Text
    val ink: Color,
    val inkMuted: Color,
    val inkSubtle: Color,
    // Borders
    val outline: Color,
    val outlineStrong: Color,
    // Accent
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    // Hero card and timer, dark in both themes
    val hero: Color,
    val onHero: Color,
    val onHeroMuted: Color,
    // Status
    val live: Color,
    val onLive: Color,
    val arrived: Color,
    val onArrived: Color,
    val late: Color,
    val onLate: Color,
    val full: Color,
    val onFull: Color,
    val canceled: Color,
    val onCanceled: Color,
    val error: Color,
    val onError: Color,
    // Avatar tints
    val tintCoral: Color,
    val tintBlue: Color,
    val tintMint: Color,
    val tintViolet: Color,
    val tintAmber: Color,
    val tintLime: Color,
    val onTint: Color,
)

// Timetable look: white and near-black, hairline rules, one orange accent for what's happening now.
// Main actions are filled with ink rather than a brand color.
val LightColors = ReconveneColors(
    canvas = Color(0xFFFFFFFF),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF4F4F4),
    surfaceDim = Color(0xFFECECEC),
    ink = Color(0xFF111111),
    inkMuted = Color(0xFF5B5E63),
    inkSubtle = Color(0xFF6B6E73),
    outline = Color(0xFFE4E4E4),
    outlineStrong = Color(0xFFB8BBC0),
    primary = Color(0xFF111111),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF1F1F1),
    onPrimaryContainer = Color(0xFF111111),
    hero = Color(0xFF111111),
    onHero = Color(0xFFFFFFFF),
    onHeroMuted = Color(0xFFB9BCC1),
    live = Color(0xFFE8590C),
    onLive = Color(0xFF111111),
    arrived = Color(0xFFE3F1E0),
    onArrived = Color(0xFF1E5B1A),
    late = Color(0xFFFFE8CC),
    onLate = Color(0xFF7A3C00),
    full = Color(0xFFF1F1F1),
    onFull = Color(0xFF5B5E63),
    canceled = Color(0xFFFDE2DD),
    onCanceled = Color(0xFF8A2E1F),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    tintCoral = Color(0xFFFFD8C7),
    tintBlue = Color(0xFFD6E4FF),
    tintMint = Color(0xFFD2F0E0),
    tintViolet = Color(0xFFE3DEFF),
    tintAmber = Color(0xFFFFE5B3),
    tintLime = Color(0xFFE2F2B8),
    onTint = Color(0xFF111111),
)

val DarkColors = ReconveneColors(
    canvas = Color(0xFF0E0F11),
    surface = Color(0xFF0E0F11),
    surfaceRaised = Color(0xFF17191C),
    surfaceVariant = Color(0xFF1A1C1F),
    surfaceDim = Color(0xFF08090A),
    ink = Color(0xFFEDEDED),
    inkMuted = Color(0xFF9EA2A8),
    inkSubtle = Color(0xFF8A8E94),
    outline = Color(0xFF25272B),
    outlineStrong = Color(0xFF4A4E54),
    primary = Color(0xFFEDEDED),
    onPrimary = Color(0xFF0E0F11),
    primaryContainer = Color(0xFF23262A),
    onPrimaryContainer = Color(0xFFEDEDED),
    hero = Color(0xFF1A1C1F),
    onHero = Color(0xFFEDEDED),
    onHeroMuted = Color(0xFF9EA2A8),
    live = Color(0xFFFF7A33),
    onLive = Color(0xFF0E0F11),
    arrived = Color(0xFF1D3A1B),
    onArrived = Color(0xFFBFE5B8),
    late = Color(0xFF4A2A06),
    onLate = Color(0xFFFFD3A8),
    full = Color(0xFF1E2023),
    onFull = Color(0xFF9EA2A8),
    canceled = Color(0xFF4A1C14),
    onCanceled = Color(0xFFFFC9BD),
    error = Color(0xFFFFB4A9),
    onError = Color(0xFF601410),
    tintCoral = Color(0xFF84321F),
    tintBlue = Color(0xFF1F4E91),
    tintMint = Color(0xFF1B6445),
    tintViolet = Color(0xFF4D3CA8),
    tintAmber = Color(0xFF7A5000),
    tintLime = Color(0xFF4B6610),
    onTint = Color(0xFFF4F2EA),
)

internal fun ReconveneColors.toColorScheme(darkTheme: Boolean): ColorScheme {
    val base = if (darkTheme) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        // The hero surface is dark in both themes
        inversePrimary = DarkColors.primary,
        // secondary and secondaryContainer: selected chips and tonal buttons, filled with ink like the
        // main buttons. The nav bar sets its own colors.
        secondary = ink,
        onSecondary = canvas,
        secondaryContainer = ink,
        onSecondaryContainer = canvas,
        tertiary = primary,
        onTertiary = onPrimary,
        tertiaryContainer = primaryContainer,
        onTertiaryContainer = onPrimaryContainer,
        background = canvas,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = inkMuted,
        surfaceTint = primary,
        inverseSurface = hero,
        inverseOnSurface = onHero,
        error = error,
        onError = onError,
        errorContainer = canceled,
        onErrorContainer = onCanceled,
        // Material's outline is the strong border, outlineVariant the subtle one
        outline = outlineStrong,
        outlineVariant = outline,
        surfaceBright = surfaceRaised,
        surfaceDim = surfaceDim,
        // Higher containers are darker in light mode and lighter in dark mode
        surfaceContainerLowest = if (darkTheme) surfaceDim else surfaceRaised,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = if (darkTheme) surfaceRaised else surfaceVariant,
        surfaceContainerHighest = if (darkTheme) surfaceVariant else surfaceDim,
    )
}
