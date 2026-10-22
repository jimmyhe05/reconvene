package com.reconvene.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

// Passes the Reconvene colors down the UI tree. "static" because they only change
// when the whole app switches between light and dark.
private val LocalReconveneColors = staticCompositionLocalOf { LightColors }

@Composable
fun ReconveneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // No dynamic (wallpaper-based) color: the app always uses the Reconvene palette.
    val colors = if (darkTheme) DarkColors else LightColors
    CompositionLocalProvider(LocalReconveneColors provides colors) {
        MaterialTheme(
            colorScheme = colors.toColorScheme(darkTheme),
            typography = Typography,
            shapes = Shapes,
            content = content,
        )
    }
}

// Lets composables read ReconveneTheme.colors.live, the same way as MaterialTheme.colorScheme.
object ReconveneTheme {
    val colors: ReconveneColors
        @Composable
        @ReadOnlyComposable
        get() = LocalReconveneColors.current
}
