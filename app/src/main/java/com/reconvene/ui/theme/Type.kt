package com.reconvene.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.reconvene.R

// IBM Plex Sans for text and headings, IBM Plex Mono for times and small labels, so a list of
// sessions reads like a timetable.
val PlexSans = FontFamily(
    Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
)

val PlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
)

private val Default = Typography()

val Typography = Typography(
    displayLarge = Default.displayLarge.restyle(PlexSans, FontWeight.SemiBold, 48, 56, letterSpacing = -1.0),
    displayMedium = Default.displayMedium.restyle(PlexSans, FontWeight.SemiBold, 40, 48, letterSpacing = -0.8),
    // Screen titles: "Today", "Groups"
    displaySmall = Default.displaySmall.restyle(PlexSans, FontWeight.SemiBold, 30, 34, letterSpacing = -0.6),
    headlineLarge = Default.headlineLarge.restyle(PlexSans, FontWeight.SemiBold, 28, 34, letterSpacing = -0.5),
    headlineMedium = Default.headlineMedium.restyle(PlexSans, FontWeight.SemiBold, 24, 30, letterSpacing = -0.4),
    headlineSmall = Default.headlineSmall.restyle(PlexSans, FontWeight.SemiBold, 20, 26, letterSpacing = -0.2),
    titleLarge = Default.titleLarge.restyle(PlexSans, FontWeight.SemiBold, 20, 26),
    titleMedium = Default.titleMedium.restyle(PlexSans, FontWeight.SemiBold, 16, 22),
    titleSmall = Default.titleSmall.restyle(PlexSans, FontWeight.SemiBold, 14, 20),
    bodyLarge = Default.bodyLarge.restyle(PlexSans, FontWeight.Normal, 16, 24),
    bodyMedium = Default.bodyMedium.restyle(PlexSans, FontWeight.Normal, 14, 20),
    bodySmall = Default.bodySmall.restyle(PlexSans, FontWeight.Normal, 13, 18),
    labelLarge = Default.labelLarge.restyle(PlexSans, FontWeight.Medium, 15, 20),
    labelMedium = Default.labelMedium.restyle(PlexSans, FontWeight.Medium, 12, 16, letterSpacing = 0.2),
    // Small uppercase section labels ("LATER TODAY"), in mono.
    labelSmall = Default.labelSmall.restyle(PlexMono, FontWeight.Medium, 12, 16, letterSpacing = 1.0),
)

// Not part of Material's scale: the start and end time at the left of a session row.
object TimeStyles {
    val start = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 22.sp)
    val end = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp)

    // Course code and vibe above a title: "CS 330 · OPEN TO CHAT"
    val meta = TextStyle(fontFamily = PlexMono, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp)
}

private fun TextStyle.restyle(
    family: FontFamily,
    weight: FontWeight,
    size: Int,
    lineHeight: Int,
    letterSpacing: Double = 0.0,
) = copy(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
)
