package com.lrcstudio.app.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

internal data class AccentSchemeColors(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
)

internal fun generateAccentScheme(seed: Color, isDark: Boolean): AccentSchemeColors {
    val (L, aStar, bStar) = seed.toLab()
    val hctHue = ((atan2(bStar, aStar).let { if (it < 0f) it + 2f * kotlin.math.PI.toFloat() else it }) * 180f / kotlin.math.PI.toFloat()) % 360f
    val hctChroma = sqrt(aStar * aStar + bStar * bStar)

    val hue = hctHue
    val seedChroma = hctChroma

    val primaryHue = hue
    val secondaryHue = (hue + 30f) % 360f
    val tertiaryHue = (hue + 60f) % 360f

    val primaryChroma = seedChroma.coerceIn(32f, 72f)
    val secondaryChroma = (seedChroma * 0.4f).coerceIn(12f, 36f)
    val tertiaryChroma = (seedChroma * 0.55f).coerceIn(16f, 48f)

    return if (isDark) {
        AccentSchemeColors(
            primary = tonalColor(primaryHue, primaryChroma, 80f),
            onPrimary = tonalColor(primaryHue, primaryChroma, 20f),
            primaryContainer = tonalColor(primaryHue, primaryChroma, 30f),
            onPrimaryContainer = tonalColor(primaryHue, primaryChroma, 90f),
            secondary = tonalColor(secondaryHue, secondaryChroma, 80f),
            onSecondary = tonalColor(secondaryHue, secondaryChroma, 20f),
            secondaryContainer = tonalColor(secondaryHue, secondaryChroma, 30f),
            onSecondaryContainer = tonalColor(secondaryHue, secondaryChroma, 90f),
            tertiary = tonalColor(tertiaryHue, tertiaryChroma, 80f),
            onTertiary = tonalColor(tertiaryHue, tertiaryChroma, 20f),
            tertiaryContainer = tonalColor(tertiaryHue, tertiaryChroma, 30f),
            onTertiaryContainer = tonalColor(tertiaryHue, tertiaryChroma, 90f),
            background = tonalColor(hue, 4f, 10f),
            onBackground = tonalColor(hue, 4f, 90f),
            surface = tonalColor(hue, 4f, 10f),
            onSurface = tonalColor(hue, 4f, 90f),
            surfaceVariant = tonalColor(hue, 8f, 30f),
            onSurfaceVariant = tonalColor(hue, 8f, 80f),
            outline = tonalColor(hue, 8f, 60f),
            outlineVariant = tonalColor(hue, 8f, 30f),
        )
    } else {
        AccentSchemeColors(
            primary = tonalColor(primaryHue, primaryChroma, 40f),
            onPrimary = tonalColor(primaryHue, primaryChroma, 100f),
            primaryContainer = tonalColor(primaryHue, primaryChroma, 90f),
            onPrimaryContainer = tonalColor(primaryHue, primaryChroma, 10f),
            secondary = tonalColor(secondaryHue, secondaryChroma, 40f),
            onSecondary = tonalColor(secondaryHue, secondaryChroma, 100f),
            secondaryContainer = tonalColor(secondaryHue, secondaryChroma, 90f),
            onSecondaryContainer = tonalColor(secondaryHue, secondaryChroma, 10f),
            tertiary = tonalColor(tertiaryHue, tertiaryChroma, 40f),
            onTertiary = tonalColor(tertiaryHue, tertiaryChroma, 100f),
            tertiaryContainer = tonalColor(tertiaryHue, tertiaryChroma, 90f),
            onTertiaryContainer = tonalColor(tertiaryHue, tertiaryChroma, 10f),
            background = tonalColor(hue, 4f, 98f),
            onBackground = tonalColor(hue, 4f, 10f),
            surface = tonalColor(hue, 4f, 98f),
            onSurface = tonalColor(hue, 4f, 10f),
            surfaceVariant = tonalColor(hue, 8f, 90f),
            onSurfaceVariant = tonalColor(hue, 8f, 30f),
            outline = tonalColor(hue, 8f, 50f),
            outlineVariant = tonalColor(hue, 8f, 80f),
        )
    }
}

private fun tonalColor(hue: Float, chroma: Float, tone: Float): Color {
    val fade = when {
        tone < 10f -> tone / 10f
        tone > 90f -> (100f - tone) / 10f
        else -> 1f
    }
    val adjustedChroma = (chroma * fade).coerceIn(0f, 200f)
    return hctToSrgb(hue, adjustedChroma, tone)
}

private fun hctToSrgb(hue: Float, chroma: Float, tone: Float): Color {
    val hueRad = (hue * kotlin.math.PI / 180f).toFloat()
    val a = chroma * cos(hueRad)
    val b = chroma * sin(hueRad)
    val L = tone

    val fy = (L + 16f) / 116f
    val fx = a / 500f + fy
    val fz = fy - b / 200f

    val delta = 6f / 29f
    val deltaSq = delta * delta

    fun invTransfer(t: Float): Float {
        val tCubed = t * t * t
        return if (tCubed > deltaSq * delta) tCubed
        else 3f * deltaSq * (t - 4f / 29f)
    }

    val X = 0.950456f * invTransfer(fx)
    val Y = invTransfer(fy)
    val Z = 1.088754f * invTransfer(fz)

    var rl = 3.2404542f * X - 1.5371385f * Y - 0.4985314f * Z
    var gl = -0.9692660f * X + 1.8760108f * Y + 0.0415560f * Z
    var bl = 0.0556434f * X - 0.2040259f * Y + 1.0572252f * Z

    fun linearToSrgb(c: Float): Float {
        return if (c <= 0.0031308f) 12.92f * c
        else 1.055f * c.pow(1f / 2.4f) - 0.055f
    }

    rl = linearToSrgb(rl).coerceIn(0f, 1f)
    gl = linearToSrgb(gl).coerceIn(0f, 1f)
    bl = linearToSrgb(bl).coerceIn(0f, 1f)

    return Color(rl, gl, bl)
}

private fun Color.toLab(): Triple<Float, Float, Float> {
    fun linear(c: Float) = if (c <= 0.04045f) c / 12.92f
        else ((c + 0.055f) / 1.055f).pow(2.4f)
    val rl = linear(red)
    val gl = linear(green)
    val bl = linear(blue)

    val X = 0.4124564f * rl + 0.3575761f * gl + 0.1804375f * bl
    val Y = 0.2126729f * rl + 0.7151522f * gl + 0.0721750f * bl
    val Z = 0.0193339f * rl + 0.1191920f * gl + 0.9503041f * bl

    val xr = X / 0.950456f
    val yr = Y / 1.0f
    val zr = Z / 1.088754f

    val delta = 6f / 29f
    val epsilon = delta * delta * delta

    fun fwd(t: Float): Float {
        return if (t > epsilon) t.pow(1f / 3f)
        else t / (3f * delta * delta) + 4f / 29f
    }

    val fx = fwd(xr)
    val fy = fwd(yr)
    val fz = fwd(zr)

    val L = 116f * fy - 16f
    val aStar = 500f * (fx - fy)
    val bStar = 200f * (fy - fz)

    return Triple(L, aStar, bStar)
}
