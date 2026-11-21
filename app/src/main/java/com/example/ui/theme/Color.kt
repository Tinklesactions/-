package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Academic Editorial Palette — Warm Ivory, Oxford Indigo, Vermilion Coral, Sage Teal
val OxfordIndigo = Color(0xFF1E3A5F)
val OxfordIndigoDark = Color(0xFF132742)
val OxfordIndigoLight = Color(0xFFD6E4F7)
val OxfordIndigoContainerDark = Color(0xFF233E63)

val VermilionCoral = Color(0xFFD94F3D)
val VermilionCoralLight = Color(0xFFFFE5E0)
val VermilionCoralDark = Color(0xFFFFB4A8)
val VermilionContainerDark = Color(0xFF5D2118)

val SageTeal = Color(0xFF2E7D6B)
val SageTealLight = Color(0xFFD7F0E8)
val SageTealDark = Color(0xFF8CD5C3)

val WarmIvoryBackground = Color(0xFFFAF8F4)
val WarmPaperSurface = Color(0xFFFFFFFF)
val WarmPaperSurfaceVariant = Color(0xFFF1ECE3)
val WarmOutline = Color(0xFFD7D0C5)
val InkPrimary = Color(0xFF171C26)
val InkSecondary = Color(0xFF535B6B)

val SlateDarkBackground = Color(0xFF10151E)
val SlateDarkSurface = Color(0xFF18202D)
val SlateDarkSurfaceVariant = Color(0xFF222C3D)
val SlateDarkOutline = Color(0xFF394459)
val DarkTextPrimary = Color(0xFFF2F4F8)
val DarkTextSecondary = Color(0xFFB3BCCB)

data class CourseAccentPalette(
    val accent: Color,
    val lightContainer: Color,
    val darkContainer: Color,
    val lightOnContainer: Color,
    val darkOnContainer: Color
)

val CoursePalettes: List<CourseAccentPalette> = listOf(
    CourseAccentPalette(
        accent = Color(0xFF1E4E8C),
        lightContainer = Color(0xFFE6EFFB),
        darkContainer = Color(0xFF1B304D),
        lightOnContainer = Color(0xFF102A4C),
        darkOnContainer = Color(0xFFD8E8FF)
    ),
    CourseAccentPalette(
        accent = Color(0xFFD94F3D),
        lightContainer = Color(0xFFFEECE9),
        darkContainer = Color(0xFF4A201B),
        lightOnContainer = Color(0xFF6B1E14),
        darkOnContainer = Color(0xFFFFD9D3)
    ),
    CourseAccentPalette(
        accent = Color(0xFF2E7D6B),
        lightContainer = Color(0xFFE4F4EF),
        darkContainer = Color(0xFF173A32),
        lightOnContainer = Color(0xFF124237),
        darkOnContainer = Color(0xFFC9F0E6)
    ),
    CourseAccentPalette(
        accent = Color(0xFFC97B16),
        lightContainer = Color(0xFFFEF3DF),
        darkContainer = Color(0xFF473011),
        lightOnContainer = Color(0xFF5C3604),
        darkOnContainer = Color(0xFFFFE5B8)
    ),
    CourseAccentPalette(
        accent = Color(0xFF6D4C9E),
        lightContainer = Color(0xFFF1EBFB),
        darkContainer = Color(0xFF32234A),
        lightOnContainer = Color(0xFF371F5C),
        darkOnContainer = Color(0xFFE5D8FF)
    ),
    CourseAccentPalette(
        accent = Color(0xFF0E7C94),
        lightContainer = Color(0xFFE2F4F8),
        darkContainer = Color(0xFF133842),
        lightOnContainer = Color(0xFF07414E),
        darkOnContainer = Color(0xFFC8EFF7)
    ),
    CourseAccentPalette(
        accent = Color(0xFFB83B6A),
        lightContainer = Color(0xFFFCE9F0),
        darkContainer = Color(0xFF471B2C),
        lightOnContainer = Color(0xFF5E1431),
        darkOnContainer = Color(0xFFFFD6E5)
    ),
    CourseAccentPalette(
        accent = Color(0xFF5D7A2A),
        lightContainer = Color(0xFFEEF4E2),
        darkContainer = Color(0xFF293814),
        lightOnContainer = Color(0xFF30440F),
        darkOnContainer = Color(0xFFDDF0BF)
    )
)

fun coursePaletteFor(index: Int): CourseAccentPalette {
    val safeIndex = ((index % CoursePalettes.size) + CoursePalettes.size) % CoursePalettes.size
    return CoursePalettes[safeIndex]
}
