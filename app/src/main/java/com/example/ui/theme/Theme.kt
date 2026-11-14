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
    onTertiary = Color.White,
    tertiaryContainer = AmberHandshakingContainer,
    onTertiaryContainer = Color(0xFF78350F),
    background = AlabasterBg,
    onBackground = InkTextPrimary,
    surface = PureWhiteSurface,
    onSurface = InkTextPrimary,
    surfaceVariant = SoftSlateSurfaceVariant,
    onSurfaceVariant = SlateTextSecondary,
    outline = CrispBorderSlate,
    outlineVariant = Color(0xFFE2E8F0),
    error = RoseAlert,
    onError = Color.White,
    errorContainer = RoseAlertContainer,
    onErrorContainer = Color(0xFF881337)
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
    onTertiary = ObsidianBg,
    tertiaryContainer = Color(0xFF78350F),
    onTertiaryContainer = Color(0xFFFEF3C7),
    background = ObsidianBg,
    onBackground = Color(0xFFF8FAFC),
    surface = ObsidianSurface,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = ObsidianBorder,
    outlineVariant = Color(0xFF1E293B),
    error = Color(0xFFFB7185),
    onError = ObsidianBg,
    errorContainer = Color(0xFF4C0519),
    onErrorContainer = Color(0xFFFFE4E6)
)

/**
 * Clean Material 3 Theme for **A VPN** with seamless, instantaneous Day and Night switching
 * (zero per-property color tween animations).
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
