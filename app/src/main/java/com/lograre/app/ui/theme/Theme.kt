package com.lograre.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1E3A5F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD5E3F7),
    onPrimaryContainer = Color(0xFF0A1E36),
    secondary = Color(0xFF4A627A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD6E4F0),
    onSecondaryContainer = Color(0xFF15283B),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE3E8EF),
    onSurfaceVariant = Color(0xFF444B54)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9ECAFF),
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF1E3A5F),
    onPrimaryContainer = Color(0xFFD5E3F7),
    secondary = Color(0xFFB7C9DC),
    onSecondary = Color(0xFF223244),
    secondaryContainer = Color(0xFF37495B),
    onSecondaryContainer = Color(0xFFD6E4F0),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111418),
    onBackground = Color(0xFFE2E6EC),
    surface = Color(0xFF181C22),
    onSurface = Color(0xFFE2E6EC),
    surfaceVariant = Color(0xFF28303A),
    onSurfaceVariant = Color(0xFFC2C8D0)
)

@Composable
fun LograreTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
