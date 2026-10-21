package com.example.couchto5k.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    primaryContainer = LightBlue,
    onPrimaryContainer = DarkBlue,
    secondary = LightBlue,
    onSecondary = PrimaryTextColor,
    secondaryContainer = VeryLightBlue,
    onSecondaryContainer = DarkBlue,
    background = BackgroundColor,
    onBackground = PrimaryTextColor,
    surface = SurfaceColor,
    onSurface = PrimaryTextColor,
    surfaceVariant = SurfaceVariantColor,
    onSurfaceVariant = SecondaryTextColor,
    outline = OutlineColor,
    error = ErrorColor,
    onError = Color.White,
)

@Composable
fun SafeStride5KTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
