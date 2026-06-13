package com.lrcstudio.app.theme

import androidx.compose.ui.graphics.Color

val md_theme_light_primary = Color(0xFF6750A4)
val md_theme_light_onPrimary = Color(0xFFFFFFFF)
val md_theme_light_primaryContainer = Color(0xFFEADDFF)
val md_theme_light_onPrimaryContainer = Color(0xFF21005D)
val md_theme_light_secondary = Color(0xFF625B71)
val md_theme_light_onSecondary = Color(0xFFFFFFFF)
val md_theme_light_secondaryContainer = Color(0xFFE8DEF8)
val md_theme_light_onSecondaryContainer = Color(0xFF1D192B)
val md_theme_light_tertiary = Color(0xFF7D5260)
val md_theme_light_onTertiary = Color(0xFFFFFFFF)
val md_theme_light_tertiaryContainer = Color(0xFFFFD8E4)
val md_theme_light_onTertiaryContainer = Color(0xFF31111D)
val md_theme_light_error = Color(0xFFB3261E)
val md_theme_light_background = Color(0xFFFFFBFE)
val md_theme_light_onBackground = Color(0xFF1C1B1F)
val md_theme_light_surface = Color(0xFFFFFBFE)
val md_theme_light_onSurface = Color(0xFF1C1B1F)
val md_theme_light_surfaceVariant = Color(0xFFE7E0EC)
val md_theme_light_onSurfaceVariant = Color(0xFF49454F)
val md_theme_light_outline = Color(0xFF79747E)
val md_theme_light_outlineVariant = Color(0xFFCAC4D0)

val md_theme_dark_primary = Color(0xFFD0BCFF)
val md_theme_dark_onPrimary = Color(0xFF381E72)
val md_theme_dark_primaryContainer = Color(0xFF4F378B)
val md_theme_dark_onPrimaryContainer = Color(0xFFEADDFF)
val md_theme_dark_secondary = Color(0xFFCCC2DC)
val md_theme_dark_onSecondary = Color(0xFF332D41)
val md_theme_dark_secondaryContainer = Color(0xFF4A4458)
val md_theme_dark_onSecondaryContainer = Color(0xFFE8DEF8)
val md_theme_dark_tertiary = Color(0xFFEFB8C8)
val md_theme_dark_onTertiary = Color(0xFF492532)
val md_theme_dark_tertiaryContainer = Color(0xFF633B48)
val md_theme_dark_onTertiaryContainer = Color(0xFFFFD8E4)
val md_theme_dark_error = Color(0xFFF2B8B5)
val md_theme_dark_background = Color(0xFF1C1B1F)
val md_theme_dark_onBackground = Color(0xFFE6E1E5)
val md_theme_dark_surface = Color(0xFF1C1B1F)
val md_theme_dark_onSurface = Color(0xFFE6E1E5)
val md_theme_dark_surfaceVariant = Color(0xFF49454F)
val md_theme_dark_onSurfaceVariant = Color(0xFFCAC4D0)
val md_theme_dark_outline = Color(0xFF938F99)
val md_theme_dark_outlineVariant = Color(0xFF49454F)

val expressive_gradient_start = Color(0xFF6750A4)
val expressive_gradient_end = Color(0xFFE040FB)
val expressive_accent = Color(0xFFFF6D00)
val expressive_surface_dark = Color(0xFF1A1A2E)
val expressive_card_dark = Color(0xFF252540)
val expressive_card_light = Color(0xFFF5F0FF)

enum class AccentPreset(
    val label: String,
    val lightPrimary: Color,
    val lightOnPrimary: Color,
    val lightPrimaryContainer: Color,
    val lightOnPrimaryContainer: Color,
    val lightBackground: Color,
    val lightOnBackground: Color,
    val lightSurfaceVariant: Color,
    val lightOnSurfaceVariant: Color,
    val lightTertiaryContainer: Color,
    val lightOnTertiaryContainer: Color,
    val lightOutline: Color,
    val lightOutlineVariant: Color,
    val darkPrimary: Color,
    val darkOnPrimary: Color,
    val darkPrimaryContainer: Color,
    val darkOnPrimaryContainer: Color,
    val darkBackground: Color,
    val darkOnBackground: Color,
    val darkSurfaceVariant: Color,
    val darkOnSurfaceVariant: Color,
    val darkTertiaryContainer: Color,
    val darkOnTertiaryContainer: Color,
    val darkOutline: Color,
    val darkOutlineVariant: Color,
) {
    Purple(
        label = "Purple",
        lightPrimary = Color(0xFF6750A4),
        lightOnPrimary = Color(0xFFFFFFFF),
        lightPrimaryContainer = Color(0xFFEADDFF),
        lightOnPrimaryContainer = Color(0xFF21005D),
        lightBackground = Color(0xFFF8F4FF),
        lightOnBackground = Color(0xFF1C1B1F),
        lightSurfaceVariant = Color(0xFFE7E0EC),
        lightOnSurfaceVariant = Color(0xFF49454F),
        lightTertiaryContainer = Color(0xFFFFD8E4),
        lightOnTertiaryContainer = Color(0xFF31111D),
        lightOutline = Color(0xFF79747E),
        lightOutlineVariant = Color(0xFFCAC4D0),
        darkPrimary = Color(0xFFD0BCFF),
        darkOnPrimary = Color(0xFF381E72),
        darkPrimaryContainer = Color(0xFF4F378B),
        darkOnPrimaryContainer = Color(0xFFEADDFF),
        darkBackground = Color(0xFF1C1B1F),
        darkOnBackground = Color(0xFFE6E1E5),
        darkSurfaceVariant = Color(0xFF49454F),
        darkOnSurfaceVariant = Color(0xFFCAC4D0),
        darkTertiaryContainer = Color(0xFF633B48),
        darkOnTertiaryContainer = Color(0xFFFFD8E4),
        darkOutline = Color(0xFF938F99),
        darkOutlineVariant = Color(0xFF49454F),
    ),
    Blue(
        label = "Blue",
        lightPrimary = Color(0xFF0061A4),
        lightOnPrimary = Color(0xFFFFFFFF),
        lightPrimaryContainer = Color(0xFFCCE5FF),
        lightOnPrimaryContainer = Color(0xFF001D36),
        lightBackground = Color(0xFFF0F7FF),
        lightOnBackground = Color(0xFF1C1B1F),
        lightSurfaceVariant = Color(0xFFDFE3EB),
        lightOnSurfaceVariant = Color(0xFF46474E),
        lightTertiaryContainer = Color(0xFFFFD9DD),
        lightOnTertiaryContainer = Color(0xFF3F111C),
        lightOutline = Color(0xFF73747C),
        lightOutlineVariant = Color(0xFFC3C5CC),
        darkPrimary = Color(0xFF9ECAFF),
        darkOnPrimary = Color(0xFF003258),
        darkPrimaryContainer = Color(0xFF00497D),
        darkOnPrimaryContainer = Color(0xFFCCE5FF),
        darkBackground = Color(0xFF191C21),
        darkOnBackground = Color(0xFFE6E1E5),
        darkSurfaceVariant = Color(0xFF434750),
        darkOnSurfaceVariant = Color(0xFFC4C6CF),
        darkTertiaryContainer = Color(0xFF7D3C48),
        darkOnTertiaryContainer = Color(0xFFFFD9DD),
        darkOutline = Color(0xFF8D8F96),
        darkOutlineVariant = Color(0xFF43454B),
    ),
    Green(
        label = "Green",
        lightPrimary = Color(0xFF006D40),
        lightOnPrimary = Color(0xFFFFFFFF),
        lightPrimaryContainer = Color(0xFFA0F0B8),
        lightOnPrimaryContainer = Color(0xFF002110),
        lightBackground = Color(0xFFF0F9F2),
        lightOnBackground = Color(0xFF1C1B1F),
        lightSurfaceVariant = Color(0xFFDDE5DB),
        lightOnSurfaceVariant = Color(0xFF444E43),
        lightTertiaryContainer = Color(0xFFF3DBFF),
        lightOnTertiaryContainer = Color(0xFF311A36),
        lightOutline = Color(0xFF727570),
        lightOutlineVariant = Color(0xFFC3C7BE),
        darkPrimary = Color(0xFF6CDA8C),
        darkOnPrimary = Color(0xFF003921),
        darkPrimaryContainer = Color(0xFF005230),
        darkOnPrimaryContainer = Color(0xFFA0F0B8),
        darkBackground = Color(0xFF181D19),
        darkOnBackground = Color(0xFFE6E1E5),
        darkSurfaceVariant = Color(0xFF414E43),
        darkOnSurfaceVariant = Color(0xFFC2CCC2),
        darkTertiaryContainer = Color(0xFF633B7D),
        darkOnTertiaryContainer = Color(0xFFF3DBFF),
        darkOutline = Color(0xFF8C9089),
        darkOutlineVariant = Color(0xFF42473F),
    ),
    Orange(
        label = "Orange",
        lightPrimary = Color(0xFFB84A00),
        lightOnPrimary = Color(0xFFFFFFFF),
        lightPrimaryContainer = Color(0xFFFFDCC2),
        lightOnPrimaryContainer = Color(0xFF3B1500),
        lightBackground = Color(0xFFFFF3ED),
        lightOnBackground = Color(0xFF1C1B1F),
        lightSurfaceVariant = Color(0xFFEDE0D9),
        lightOnSurfaceVariant = Color(0xFF4E4644),
        lightTertiaryContainer = Color(0xFFD3E3FF),
        lightOnTertiaryContainer = Color(0xFF001B39),
        lightOutline = Color(0xFF7B7470),
        lightOutlineVariant = Color(0xFFCDC5BE),
        darkPrimary = Color(0xFFFFB786),
        darkOnPrimary = Color(0xFF5E2300),
        darkPrimaryContainer = Color(0xFF883600),
        darkOnPrimaryContainer = Color(0xFFFFDCC2),
        darkBackground = Color(0xFF1F1B18),
        darkOnBackground = Color(0xFFE6E1E5),
        darkSurfaceVariant = Color(0xFF4E4644),
        darkOnSurfaceVariant = Color(0xFFCDC5BE),
        darkTertiaryContainer = Color(0xFF00497D),
        darkOnTertiaryContainer = Color(0xFFD3E3FF),
        darkOutline = Color(0xFF928E89),
        darkOutlineVariant = Color(0xFF49433F),
    ),
    Pink(
        label = "Pink",
        lightPrimary = Color(0xFFB0006A),
        lightOnPrimary = Color(0xFFFFFFFF),
        lightPrimaryContainer = Color(0xFFFFD9E4),
        lightOnPrimaryContainer = Color(0xFF3A0022),
        lightBackground = Color(0xFFFFF0F5),
        lightOnBackground = Color(0xFF1C1B1F),
        lightSurfaceVariant = Color(0xFFEFE0E4),
        lightOnSurfaceVariant = Color(0xFF4E4549),
        lightTertiaryContainer = Color(0xFFC1F0ED),
        lightOnTertiaryContainer = Color(0xFF00211F),
        lightOutline = Color(0xFF7C7275),
        lightOutlineVariant = Color(0xFFCEC2C7),
        darkPrimary = Color(0xFFFFB0C8),
        darkOnPrimary = Color(0xFF5C0036),
        darkPrimaryContainer = Color(0xFF850050),
        darkOnPrimaryContainer = Color(0xFFFFD9E4),
        darkBackground = Color(0xFF1F181C),
        darkOnBackground = Color(0xFFE6E1E5),
        darkSurfaceVariant = Color(0xFF4E4549),
        darkOnSurfaceVariant = Color(0xFFCDC4CA),
        darkTertiaryContainer = Color(0xFF00504D),
        darkOnTertiaryContainer = Color(0xFFC1F0ED),
        darkOutline = Color(0xFF93898D),
        darkOutlineVariant = Color(0xFF4A4145),
    ),
    Teal(
        label = "Teal",
        lightPrimary = Color(0xFF006B6B),
        lightOnPrimary = Color(0xFFFFFFFF),
        lightPrimaryContainer = Color(0xFF9FF0F0),
        lightOnPrimaryContainer = Color(0xFF002020),
        lightBackground = Color(0xFFEDF7F7),
        lightOnBackground = Color(0xFF1C1B1F),
        lightSurfaceVariant = Color(0xFFDBE5E3),
        lightOnSurfaceVariant = Color(0xFF3F4948),
        lightTertiaryContainer = Color(0xFFFFDAD4),
        lightOnTertiaryContainer = Color(0xFF3A1515),
        lightOutline = Color(0xFF6F7977),
        lightOutlineVariant = Color(0xFFBFC9C6),
        darkPrimary = Color(0xFF4CDBDB),
        darkOnPrimary = Color(0xFF003838),
        darkPrimaryContainer = Color(0xFF005050),
        darkOnPrimaryContainer = Color(0xFF9FF0F0),
        darkBackground = Color(0xFF171F1E),
        darkOnBackground = Color(0xFFE6E1E5),
        darkSurfaceVariant = Color(0xFF3F4948),
        darkOnSurfaceVariant = Color(0xFFC0CBC9),
        darkTertiaryContainer = Color(0xFF7D3C38),
        darkOnTertiaryContainer = Color(0xFFFFDAD4),
        darkOutline = Color(0xFF899390),
        darkOutlineVariant = Color(0xFF3F4947),
    ),
    TestWhite(
        label = "TestWhite",
        lightPrimary = Color(0xFF6750A4),
        lightOnPrimary = Color(0xFFFFFFFF),
        lightPrimaryContainer = Color(0xFFEADDFF),
        lightOnPrimaryContainer = Color(0xFF21005D),
        lightBackground = Color(0xFFFFFFFF),
        lightOnBackground = Color(0xFF1C1B1F),
        lightSurfaceVariant = Color(0xFFE7E0EC),
        lightOnSurfaceVariant = Color(0xFF49454F),
        lightTertiaryContainer = Color(0xFFFFD8E4),
        lightOnTertiaryContainer = Color(0xFF31111D),
        lightOutline = Color(0xFF79747E),
        lightOutlineVariant = Color(0xFFCAC4D0),
        darkPrimary = Color(0xFFD0BCFF),
        darkOnPrimary = Color(0xFF381E72),
        darkPrimaryContainer = Color(0xFF4F378B),
        darkOnPrimaryContainer = Color(0xFFEADDFF),
        darkBackground = Color(0xFFFFFFFF),
        darkOnBackground = Color(0xFF1C1B1F),
        darkSurfaceVariant = Color(0xFF49454F),
        darkOnSurfaceVariant = Color(0xFFCAC4D0),
        darkTertiaryContainer = Color(0xFF633B48),
        darkOnTertiaryContainer = Color(0xFFFFD8E4),
        darkOutline = Color(0xFF938F99),
        darkOutlineVariant = Color(0xFF49454F),
    );

    companion object {
        fun fromName(name: String): AccentPreset =
            entries.find { it.label == name } ?: Purple
    }
}
