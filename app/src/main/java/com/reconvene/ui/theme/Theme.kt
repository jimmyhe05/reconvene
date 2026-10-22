package com.reconvene.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalReconveneColors = staticCompositionLocalOf { LightColors }

@Composable
fun ReconveneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
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

object ReconveneTheme {
    val colors: ReconveneColors
        @Composable
        @ReadOnlyComposable
        get() = LocalReconveneColors.current
}
