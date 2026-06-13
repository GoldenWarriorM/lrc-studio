package com.lrcstudio.app.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

val LocalSnapSurface = staticCompositionLocalOf { Color.Unspecified }

private val baseLightColorScheme = lightColorScheme(
    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    onPrimaryContainer = md_theme_light_onPrimaryContainer,
    secondary = md_theme_light_secondary,
    onSecondary = md_theme_light_onSecondary,
    secondaryContainer = md_theme_light_secondaryContainer,
    onSecondaryContainer = md_theme_light_onSecondaryContainer,
    tertiary = md_theme_light_tertiary,
    onTertiary = md_theme_light_onTertiary,
    tertiaryContainer = md_theme_light_tertiaryContainer,
    onTertiaryContainer = md_theme_light_onTertiaryContainer,
    error = md_theme_light_error,
    background = md_theme_light_background,
    onBackground = md_theme_light_onBackground,
    surface = md_theme_light_surface,
    onSurface = md_theme_light_onSurface,
    surfaceVariant = md_theme_light_surfaceVariant,
    onSurfaceVariant = md_theme_light_onSurfaceVariant,
    outline = md_theme_light_outline,
    outlineVariant = md_theme_light_outlineVariant,
)

private val baseDarkColorScheme = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,
    tertiary = md_theme_dark_tertiary,
    onTertiary = md_theme_dark_onTertiary,
    tertiaryContainer = md_theme_dark_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_onTertiaryContainer,
    error = md_theme_dark_error,
    background = md_theme_dark_background,
    onBackground = md_theme_dark_onBackground,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    outline = md_theme_dark_outline,
    outlineVariant = md_theme_dark_outlineVariant,
)

@Composable
fun LRCStudioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: AccentPreset = AccentPreset.Purple,
    content: @Composable () -> Unit
) {
    val targetScheme = if (darkTheme) {
        val bg = accent.darkBackground
        val sv = accent.darkSurfaceVariant
        baseDarkColorScheme.copy(
            primary = accent.darkPrimary,
            onPrimary = accent.darkOnPrimary,
            primaryContainer = accent.darkPrimaryContainer,
            onPrimaryContainer = accent.darkOnPrimaryContainer,
            background = bg,
            onBackground = accent.darkOnBackground,
            surface = bg,
            onSurface = accent.darkOnBackground,
            surfaceVariant = sv,
            onSurfaceVariant = accent.darkOnSurfaceVariant,
            tertiaryContainer = accent.darkTertiaryContainer,
            onTertiaryContainer = accent.darkOnTertiaryContainer,
            outline = accent.darkOutline,
            outlineVariant = accent.darkOutlineVariant,
            surfaceTint = accent.darkPrimary,
            surfaceContainerLow = lerp(bg, sv, 0.17f),
            surfaceContainer = lerp(bg, sv, 0.25f),
            surfaceContainerHigh = lerp(bg, sv, 0.46f),
            surfaceContainerHighest = lerp(bg, sv, 0.67f),
            surfaceBright = lerp(bg, sv, 0.75f),
            surfaceDim = bg,
        )
    } else {
        val bg = accent.lightBackground
        val sv = accent.lightSurfaceVariant
        baseLightColorScheme.copy(
            primary = accent.lightPrimary,
            onPrimary = accent.lightOnPrimary,
            primaryContainer = accent.lightPrimaryContainer,
            onPrimaryContainer = accent.lightOnPrimaryContainer,
            background = bg,
            onBackground = accent.lightOnBackground,
            surface = bg,
            onSurface = accent.lightOnBackground,
            surfaceVariant = sv,
            onSurfaceVariant = accent.lightOnSurfaceVariant,
            tertiaryContainer = accent.lightTertiaryContainer,
            onTertiaryContainer = accent.lightOnTertiaryContainer,
            outline = accent.lightOutline,
            outlineVariant = accent.lightOutlineVariant,
            surfaceTint = accent.lightPrimary,
            surfaceContainerLow = lerp(bg, sv, 0.25f),
            surfaceContainer = lerp(bg, sv, 0.5f),
            surfaceContainerHigh = lerp(bg, sv, 0.75f),
            surfaceContainerHighest = sv,
            surfaceBright = bg,
            surfaceDim = lerp(bg, sv, 0.85f),
        )
    }

    val normalSpec = tween<Color>(durationMillis = 400)
    val snapSpec = tween<Color>(durationMillis = 0)
    val bg by animateColorAsState(targetScheme.background, normalSpec, label = "bg")
    val onBg by animateColorAsState(targetScheme.onBackground, snapSpec, label = "onBg")
    val primary by animateColorAsState(targetScheme.primary, snapSpec, label = "primary")
    val onPrimary by animateColorAsState(targetScheme.onPrimary, snapSpec, label = "onPrimary")
    val primaryContainer by animateColorAsState(targetScheme.primaryContainer, normalSpec, label = "primaryContainer")
    val onPrimaryContainer by animateColorAsState(targetScheme.onPrimaryContainer, snapSpec, label = "onPrimaryContainer")
    val surface by animateColorAsState(targetScheme.surface, normalSpec, label = "surface")
    val snapSurface by animateColorAsState(targetScheme.surface, snapSpec, label = "snapSurface")
    val onSurface by animateColorAsState(targetScheme.onSurface, snapSpec, label = "onSurface")
    val surfaceVariant by animateColorAsState(targetScheme.surfaceVariant, normalSpec, label = "surfaceVariant")
    val onSurfaceVariant by animateColorAsState(targetScheme.onSurfaceVariant, snapSpec, label = "onSurfaceVariant")
    val surfaceTint by animateColorAsState(targetScheme.surfaceTint, normalSpec, label = "surfaceTint")
    val tertiaryContainer by animateColorAsState(targetScheme.tertiaryContainer, normalSpec, label = "tertiaryContainer")
    val onTertiaryContainer by animateColorAsState(targetScheme.onTertiaryContainer, snapSpec, label = "onTertiaryContainer")
    val outline by animateColorAsState(targetScheme.outline, snapSpec, label = "outline")
    val outlineVariant by animateColorAsState(targetScheme.outlineVariant, snapSpec, label = "outlineVariant")

    CompositionLocalProvider(LocalSnapSurface provides snapSurface) {
        MaterialTheme(
            colorScheme = targetScheme.copy(
                primary = primary,
                onPrimary = onPrimary,
                primaryContainer = primaryContainer,
                onPrimaryContainer = onPrimaryContainer,
                background = bg,
                onBackground = onBg,
                surface = surface,
                onSurface = onSurface,
                surfaceVariant = surfaceVariant,
                onSurfaceVariant = onSurfaceVariant,
                tertiaryContainer = tertiaryContainer,
                onTertiaryContainer = onTertiaryContainer,
                outline = outline,
                outlineVariant = outlineVariant,
                surfaceTint = surfaceTint,
            ),
            typography = AppTypography,
            content = content
        )
    }
}
