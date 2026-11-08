package com.example.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

@Composable
private fun animateThemeColor(target: Color, label: String): Color {
    val animated by animateColorAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        label = label
    )
    return animated
}

/**
 * A VPN Liquid Glass Theme with silky-smooth animated Dark/Light mode transitions.
 */
@Composable
fun AetherNodeTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val target = if (darkTheme) DarkColorScheme else LightColorScheme

    val animatedScheme: ColorScheme = target.copy(
        primary = animateThemeColor(target.primary, "primary"),
        onPrimary = animateThemeColor(target.onPrimary, "onPrimary"),
        primaryContainer = animateThemeColor(target.primaryContainer, "primaryContainer"),
        onPrimaryContainer = animateThemeColor(target.onPrimaryContainer, "onPrimaryContainer"),
        secondary = animateThemeColor(target.secondary, "secondary"),
        onSecondary = animateThemeColor(target.onSecondary, "onSecondary"),
        secondaryContainer = animateThemeColor(target.secondaryContainer, "secondaryContainer"),
        onSecondaryContainer = animateThemeColor(target.onSecondaryContainer, "onSecondaryContainer"),
        tertiary = animateThemeColor(target.tertiary, "tertiary"),
        background = animateThemeColor(target.background, "background"),
        onBackground = animateThemeColor(target.onBackground, "onBackground"),
        surface = animateThemeColor(target.surface, "surface"),
        onSurface = animateThemeColor(target.onSurface, "onSurface"),
        surfaceVariant = animateThemeColor(target.surfaceVariant, "surfaceVariant"),
        onSurfaceVariant = animateThemeColor(target.onSurfaceVariant, "onSurfaceVariant"),
        outline = animateThemeColor(target.outline, "outline"),
        error = animateThemeColor(target.error, "error")
    )

    MaterialTheme(
        colorScheme = animatedScheme,
        typography = Typography,
        content = content
    )
}
