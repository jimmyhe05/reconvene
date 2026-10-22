package com.reconvene.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Corner radii from the prototype. Material components pick a size by role
// (text fields use extraSmall, bottom sheets extraLarge). Buttons and chips that
// should be pill-shaped use CircleShape instead.
val Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),  // --radius-xs
    small = RoundedCornerShape(12.dp),      // --radius-sm: availability cells
    medium = RoundedCornerShape(16.dp),     // --radius-md
    large = RoundedCornerShape(22.dp),      // --radius-lg: cards
    extraLarge = RoundedCornerShape(28.dp), // --radius-xl: bottom sheets
)
