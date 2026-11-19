package io.github.cyarrow.ao3reader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The whole app shares the reader's "paper" world: warm neutral surfaces and AO3's deep red as the accent,
 * instead of Material's default lavender or wallpaper-derived colors.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF8E1B1B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF6DAD3),
    onPrimaryContainer = Color(0xFF3B0907),
    secondary = Color(0xFF6E5B4A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEFE2D0),
    onSecondaryContainer = Color(0xFF2A1F14),
    tertiary = Color(0xFF55633F),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFAF6EE),
    onBackground = Color(0xFF221C16),
    surface = Color(0xFFFAF6EE),
    onSurface = Color(0xFF221C16),
    surfaceVariant = Color(0xFFEDE4D6),
    onSurfaceVariant = Color(0xFF51463A),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5EFE4),
    surfaceContainer = Color(0xFFF0E9DD),
    surfaceContainerHigh = Color(0xFFEBE3D5),
    surfaceContainerHighest = Color(0xFFE5DCCD),
    outline = Color(0xFF85796B),
    outlineVariant = Color(0xFFD8CDBC),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF0A79B),
    onPrimary = Color(0xFF561410),
    primaryContainer = Color(0xFF722420),
    onPrimaryContainer = Color(0xFFFFDAD4),
    secondary = Color(0xFFD9C4AE),
    onSecondary = Color(0xFF3C2E21),
    secondaryContainer = Color(0xFF4A3D31),
    onSecondaryContainer = Color(0xFFF2E1CC),
    tertiary = Color(0xFFBCCBA0),
    background = Color(0xFF1B1916),
    onBackground = Color(0xFFE9E1D6),
    surface = Color(0xFF1B1916),
    onSurface = Color(0xFFE9E1D6),
    surfaceVariant = Color(0xFF3A342D),
    onSurfaceVariant = Color(0xFFCFC4B6),
    surfaceContainerLowest = Color(0xFF151311),
    surfaceContainerLow = Color(0xFF221F1B),
    surfaceContainer = Color(0xFF26231F),
    surfaceContainerHigh = Color(0xFF312D28),
    surfaceContainerHighest = Color(0xFF3C3832),
    outline = Color(0xFF998E80),
    outlineVariant = Color(0xFF4A433B),
    error = Color(0xFFF2B8B5),
)

@Composable
fun AO3ReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    @Suppress("UNUSED_PARAMETER") dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
