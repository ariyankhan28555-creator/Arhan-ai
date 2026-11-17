package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val NovaDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = ObsidianVoid,
    primaryContainer = Color(0xFF0E2A47),
    onPrimaryContainer = Color(0xFFB8EAFF),
    secondary = NeonViolet,
    onSecondary = Color(0xFF1A0536),
    secondaryContainer = Color(0xFF2D1654),
    onSecondaryContainer = Color(0xFFE9D5FF),
    tertiary = LuminousTeal,
    onTertiary = ObsidianVoid,
    tertiaryContainer = Color(0xFF0C3633),
    onTertiaryContainer = Color(0xFFA7F3D0),
    background = ObsidianVoid,
    onBackground = TextPrimary,
    surface = MidnightSurface,
    onSurface = TextPrimary,
    surfaceVariant = ElevatedCard,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianVoid,
    surfaceContainerLow = Color(0xFF0A0F1F),
    surfaceContainer = MidnightSurface,
    surfaceContainerHigh = ElevatedCard,
    surfaceContainerHighest = Color(0xFF1A2547),
    outline = GlassBorder,
    outlineVariant = Color(0xFF1B2545),
    error = CoralAlert,
    onError = Color(0xFF2B040D),
    errorContainer = Color(0xFF4C0D1D),
    onErrorContainer = Color(0xFFFFD9E0)
)

val NovaShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun NovaTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = NovaDarkColorScheme,
        typography = NovaTypography,
        shapes = NovaShapes,
        content = content
    )
}
