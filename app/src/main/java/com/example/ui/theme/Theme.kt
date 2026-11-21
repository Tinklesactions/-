package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = OxfordIndigo,
    onPrimary = Color.White,
    primaryContainer = OxfordIndigoLight,
    onPrimaryContainer = OxfordIndigoDark,
    secondary = VermilionCoral,
    onSecondary = Color.White,
    secondaryContainer = VermilionCoralLight,
    onSecondaryContainer = Color(0xFF6B1E14),
    tertiary = SageTeal,
    onTertiary = Color.White,
    tertiaryContainer = SageTealLight,
    onTertiaryContainer = Color(0xFF0E3B30),
    background = WarmIvoryBackground,
    onBackground = InkPrimary,
    surface = WarmPaperSurface,
    onSurface = InkPrimary,
    surfaceVariant = WarmPaperSurfaceVariant,
    onSurfaceVariant = InkSecondary,
    outline = WarmOutline,
    outlineVariant = Color(0xFFE7E1D8),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF9AC0F5),
    onPrimary = OxfordIndigoDark,
    primaryContainer = OxfordIndigoContainerDark,
    onPrimaryContainer = Color(0xFFD6E4F7),
    secondary = VermilionCoralDark,
    onSecondary = Color(0xFF56180F),
    secondaryContainer = VermilionContainerDark,
    onSecondaryContainer = Color(0xFFFFDAD4),
    tertiary = SageTealDark,
    onTertiary = Color(0xFF0B372C),
    tertiaryContainer = Color(0xFF194B3F),
    onTertiaryContainer = SageTealLight,
    background = SlateDarkBackground,
    onBackground = DarkTextPrimary,
    surface = SlateDarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = SlateDarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = SlateDarkOutline,
    outlineVariant = Color(0xFF283245),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun CourseReminderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
