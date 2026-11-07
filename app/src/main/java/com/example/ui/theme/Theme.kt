package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = SkyBluePrimary,
    onPrimary = Color.White,
    primaryContainer = SkyBluePrimaryContainer,
    onPrimaryContainer = OnSkyBluePrimaryContainer,
    secondary = EmeraldConnected,
    onSecondary = Color.White,
    secondaryContainer = EmeraldConnectedContainer,
    onSecondaryContainer = OnEmeraldConnectedContainer,
    tertiary = AmberHandshaking,
    tertiaryContainer = AmberHandshakingContainer,
    background = AlabasterBg,
    onBackground = InkTextPrimary,
    surface = PureWhiteSurface,
    onSurface = InkTextPrimary,
    surfaceVariant = SoftSlateSurfaceVariant,
    onSurfaceVariant = SlateTextSecondary,
    outline = CrispBorderSlate,
    error = RoseAlert,
    errorContainer = RoseAlertContainer
)

private val DarkColorScheme = darkColorScheme(
    primary = CyanDarkPrimary,
    onPrimary = ObsidianBg,
    primaryContainer = CyanDarkContainer,
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = EmeraldDarkConnected,
    onSecondary = ObsidianBg,
    secondaryContainer = Color(0xFF064E3B),
    onSecondaryContainer = Color(0xFFD1FAE5),
    tertiary = Color(0xFFFBBF24),
    background = ObsidianBg,
    onBackground = Color(0xFFF8FAFC),
    surface = ObsidianSurface,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = ObsidianBorder,
    error = Color(0xFFFB7185)
)

/**
 * AetherNode Theme: Defaults to a featherweight, high-contrast Daylight palette
 * ("very light UI") while supporting an optional dark mode toggle.
 */
@Composable
fun AetherNodeTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
