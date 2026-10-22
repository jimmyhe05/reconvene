package com.reconvene.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.reconvene.R

// Variable font: the weight axis has to be set explicitly, or every weight renders as Regular.
val DmSans = FontFamily(
    dmSans(FontWeight.Normal),
    dmSans(FontWeight.Medium),
    dmSans(FontWeight.SemiBold),
    dmSans(FontWeight.Bold),
)

@OptIn(ExperimentalTextApi::class)
private fun dmSans(weight: FontWeight) = Font(
    R.font.dm_sans,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val DmSerifDisplay = FontFamily(Font(R.font.dm_serif_display))

private val Default = Typography()

val Typography = Typography(
    displayLarge = Default.displayLarge.copy(fontFamily = DmSerifDisplay),
    displayMedium = Default.displayMedium.copy(fontFamily = DmSerifDisplay),
    displaySmall = Default.displaySmall.restyle(DmSerifDisplay, FontWeight.Normal, 36, 40, letterSpacing = -0.5),
    headlineLarge = Default.headlineLarge.restyle(DmSerifDisplay, FontWeight.Normal, 28, 34),
    headlineMedium = Default.headlineMedium.restyle(DmSerifDisplay, FontWeight.Normal, 24, 30),
    // Not in the design system; one step below headlineMedium
    headlineSmall = Default.headlineSmall.restyle(DmSerifDisplay, FontWeight.Normal, 20, 26),
    titleLarge = Default.titleLarge.restyle(DmSans, FontWeight.SemiBold, 20, 26),
    titleMedium = Default.titleMedium.restyle(DmSans, FontWeight.SemiBold, 16, 22),
    titleSmall = Default.titleSmall.restyle(DmSans, FontWeight.SemiBold, 14, 20),
    bodyLarge = Default.bodyLarge.restyle(DmSans, FontWeight.Normal, 16, 24),
    bodyMedium = Default.bodyMedium.restyle(DmSans, FontWeight.Normal, 14, 20),
    bodySmall = Default.bodySmall.restyle(DmSans, FontWeight.Normal, 12, 16),
    labelLarge = Default.labelLarge.restyle(DmSans, FontWeight.SemiBold, 14, 20, letterSpacing = 0.1),
    labelMedium = Default.labelMedium.restyle(DmSans, FontWeight.SemiBold, 12, 16, letterSpacing = 0.2),
    labelSmall = Default.labelSmall.restyle(DmSans, FontWeight.Bold, 11, 16, letterSpacing = 1.5),
)

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
