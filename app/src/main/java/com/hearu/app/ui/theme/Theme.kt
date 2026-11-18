package com.hearu.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = HearUDarkPrimary,
    onPrimary = HearUDarkOnPrimary,
    primaryContainer = HearUDarkPrimaryContainer,
    onPrimaryContainer = HearUDarkOnPrimaryContainer,
    secondary = HearUDarkSecondary,
    onSecondary = HearUDarkOnSecondary,
    secondaryContainer = HearUDarkSecondaryContainer,
    onSecondaryContainer = HearUDarkOnSecondaryContainer,
    tertiary = HearUDarkTertiary,
    onTertiary = HearUDarkOnTertiary,
    tertiaryContainer = HearUDarkTertiaryContainer,
    onTertiaryContainer = HearUDarkOnTertiaryContainer,
    background = HearUDarkBackground,
    onBackground = HearUDarkOnBackground,
    surface = HearUDarkSurface,
    onSurface = HearUDarkOnSurface,
    surfaceVariant = HearUDarkSurfaceVariant,
    onSurfaceVariant = HearUDarkOnSurfaceVariant,
    outline = HearUDarkOutline,
    error = HearUDarkError,
    onError = HearUDarkOnError,
    errorContainer = HearUDarkErrorContainer,
    onErrorContainer = HearUDarkOnErrorContainer
)

private val LightColorScheme = lightColorScheme(
    primary = HearUPrimary,
    onPrimary = HearUOnPrimary,
    primaryContainer = HearUPrimaryContainer,
    onPrimaryContainer = HearUOnPrimaryContainer,
    secondary = HearUSecondary,
    onSecondary = HearUOnSecondary,
    secondaryContainer = HearUSecondaryContainer,
    onSecondaryContainer = HearUOnSecondaryContainer,
    tertiary = HearUTertiary,
    onTertiary = HearUOnTertiary,
    tertiaryContainer = HearUTertiaryContainer,
    onTertiaryContainer = HearUOnTertiaryContainer,
    background = HearUBackground,
    onBackground = HearUOnBackground,
    surface = HearUSurface,
    onSurface = HearUOnSurface,
    surfaceVariant = HearUSurfaceVariant,
    onSurfaceVariant = HearUOnSurfaceVariant,
    outline = HearUOutline,
    error = HearUError,
    onError = HearUOnError,
    errorContainer = HearUErrorContainer,
    onErrorContainer = HearUOnErrorContainer
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun HearUTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
