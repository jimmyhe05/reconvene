package com.reconvene.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Tokens from the Study Pod design system, named after their CSS variables.
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

val LightColors = ReconveneColors(
    canvas = Color(0xFFF2F0EA),
    surface = Color(0xFFFAF9F5),
    surfaceRaised = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEEECE5),
    surfaceDim = Color(0xFFE4E1D8),
    ink = Color(0xFF22231F),
    inkMuted = Color(0xFF4F5048),
    inkSubtle = Color(0xFF66675F),
    outline = Color(0xFFD6D3C8),
    outlineStrong = Color(0xFF8A877D),
    primary = Color(0xFF5A3FD6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE6E1FF),
    onPrimaryContainer = Color(0xFF35258F),
    hero = Color(0xFF252622),
    onHero = Color(0xFFFAF9F5),
    onHeroMuted = Color(0xFFC9C7BD),
    live = Color(0xFFD8FF73),
    onLive = Color(0xFF22231F),
    arrived = Color(0xFFDCEFC0),
    onArrived = Color(0xFF2B5208),
    late = Color(0xFFFFE2A6),
    onLate = Color(0xFF6A4300),
    full = Color(0xFFE4E1D8),
    onFull = Color(0xFF4A4B44),
    canceled = Color(0xFFFFD9CF),
    onCanceled = Color(0xFF8A2E1F),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    tintCoral = Color(0xFFFFC7B5),
    tintBlue = Color(0xFFBDD8FF),
    tintMint = Color(0xFFB9EBD0),
    tintViolet = Color(0xFFD5CCFF),
    tintAmber = Color(0xFFFFDC9C),
    tintLime = Color(0xFFD3EE84),
    onTint = Color(0xFF22231F),
)

val DarkColors = ReconveneColors(
    canvas = Color(0xFF151613),
    surface = Color(0xFF1E1F1B),
    surfaceRaised = Color(0xFF262722),
    surfaceVariant = Color(0xFF2C2D28),
    surfaceDim = Color(0xFF11120F),
    ink = Color(0xFFEEECE4),
    inkMuted = Color(0xFFC3C1B7),
    inkSubtle = Color(0xFFA3A198),
    outline = Color(0xFF3D3E38),
    outlineStrong = Color(0xFF8D8B81),
    primary = Color(0xFFBFB0FF),
    onPrimary = Color(0xFF24146E),
    primaryContainer = Color(0xFF3A2D8C),
    onPrimaryContainer = Color(0xFFE6E1FF),
    hero = Color(0xFF33342E),
    onHero = Color(0xFFEEECE4),
    onHeroMuted = Color(0xFFC3C1B7),
    live = Color(0xFFD8FF73),
    onLive = Color(0xFF22231F),
    arrived = Color(0xFF2F4A14),
    onArrived = Color(0xFFD3EDB0),
    late = Color(0xFF5A3D00),
    onLate = Color(0xFFFFE2A6),
    full = Color(0xFF34352F),
    onFull = Color(0xFFC3C1B7),
    canceled = Color(0xFF6B2619),
    onCanceled = Color(0xFFFFD9CF),
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
        // secondary: selected filter chips; secondaryContainer: nav pill and tonal buttons
        secondary = ink,
        onSecondary = canvas,
        secondaryContainer = primaryContainer,
        onSecondaryContainer = onPrimaryContainer,
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
