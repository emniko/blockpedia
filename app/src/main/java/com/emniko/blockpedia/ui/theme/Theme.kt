package com.emniko.blockpedia.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Grass green, dirt brown and diamond cyan, tuned for readability.
private val DarkColors = darkColorScheme(
    primary = Color(0xFF8FD15A),
    onPrimary = Color(0xFF102400),
    primaryContainer = Color(0xFF2E5014),
    onPrimaryContainer = Color(0xFFC9F2A4),
    secondary = Color(0xFFD9B48A),
    onSecondary = Color(0xFF3A2410),
    secondaryContainer = Color(0xFF5A3D22),
    onSecondaryContainer = Color(0xFFF6DDBF),
    tertiary = Color(0xFF6FD3E0),
    onTertiary = Color(0xFF00363D),
    tertiaryContainer = Color(0xFF0F4E57),
    onTertiaryContainer = Color(0xFFBDF2F8),
    background = Color(0xFF121412),
    onBackground = Color(0xFFE3E4DD),
    surface = Color(0xFF121412),
    onSurface = Color(0xFFE3E4DD),
    surfaceVariant = Color(0xFF2B302A),
    onSurfaceVariant = Color(0xFFC2C9BB),
    outline = Color(0xFF8C9386),
    outlineVariant = Color(0xFF41493D),
    surfaceContainerLowest = Color(0xFF0D0F0D),
    surfaceContainerLow = Color(0xFF181A17),
    surfaceContainer = Color(0xFF1C1F1B),
    surfaceContainerHigh = Color(0xFF242823),
    surfaceContainerHighest = Color(0xFF2E322D),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF3F6F1C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC2EE9B),
    onPrimaryContainer = Color(0xFF0B2000),
    secondary = Color(0xFF7A5634),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDDBA),
    onSecondaryContainer = Color(0xFF2C1600),
    tertiary = Color(0xFF00687A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFAEECFF),
    onTertiaryContainer = Color(0xFF001F26),
    background = Color(0xFFF7F8F2),
    onBackground = Color(0xFF1A1C18),
    surface = Color(0xFFF7F8F2),
    onSurface = Color(0xFF1A1C18),
    surfaceVariant = Color(0xFFE0E4D6),
    onSurfaceVariant = Color(0xFF44483E),
    outline = Color(0xFF74796D),
    outlineVariant = Color(0xFFC4C8BA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F3EC),
    surfaceContainer = Color(0xFFEBEDE6),
    surfaceContainerHigh = Color(0xFFE6E8E0),
    surfaceContainerHighest = Color(0xFFE0E2DA),
)

@Composable
fun BlockpediaTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
