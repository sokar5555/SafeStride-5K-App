package com.example.couchto5k.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.couchto5k.domain.model.ThemeMode

val LocalAppDarkTheme = staticCompositionLocalOf { false }

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
    errorContainer = ErrorContainerColor,
    onErrorContainer = OnErrorContainerColor,
    tertiary = SuccessColor,
    onTertiary = Color.White,
    tertiaryContainer = SuccessContainerColor,
    onTertiaryContainer = OnSuccessContainerColor,
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimaryBlue,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    background = DarkBackgroundColor,
    onBackground = DarkPrimaryTextColor,
    surface = DarkSurfaceColor,
    onSurface = DarkPrimaryTextColor,
    surfaceVariant = DarkSurfaceVariantColor,
    onSurfaceVariant = DarkSecondaryTextColor,
    outline = DarkOutlineColor,
    error = DarkErrorColor,
    onError = DarkOnErrorColor,
    errorContainer = DarkErrorContainerColor,
    onErrorContainer = DarkOnErrorContainerColor,
    tertiary = DarkSuccessColor,
    onTertiary = DarkOnSuccessContainerColor,
    tertiaryContainer = DarkSuccessContainerColor,
    onTertiaryContainer = DarkOnSuccessContainerColor,
)

@Composable
fun SafeStride5KTheme(
    themeMode: ThemeMode = ThemeMode.LIGHT,
    content: @Composable () -> Unit,
) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colorScheme = if (useDarkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !useDarkTheme
            insetsController.isAppearanceLightNavigationBars = !useDarkTheme
        }
    }

    CompositionLocalProvider(LocalAppDarkTheme provides useDarkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}
