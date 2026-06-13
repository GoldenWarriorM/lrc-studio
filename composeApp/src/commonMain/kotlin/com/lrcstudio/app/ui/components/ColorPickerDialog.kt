package com.lrcstudio.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.Canvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lrcstudio.app.theme.generateAccentScheme
import kotlin.math.roundToInt

private fun Color.toHsv(): FloatArray {
    val r = red; val g = green; val b = blue
    val max = maxOf(r, g, b); val min = minOf(r, g, b)
    val delta = max - min
    var h = 0f
    val s = if (max != 0f) delta / max else 0f
    val v = max
    if (delta != 0f) {
        h = when (max) {
            r -> ((g - b) / delta) % 6f
            g -> ((b - r) / delta) + 2f
            else -> ((r - g) / delta) + 4f
        }
        h *= 60f
        if (h < 0f) h += 360f
    }
    return floatArrayOf(h, s, v)
}

private fun hsvToRgb(h: Float, s: Float, v: Float): FloatArray {
    val hi = (h / 60f).toInt() % 6
    val f = h / 60f - (h / 60f).toInt()
    val p = v * (1f - s)
    val q = v * (1f - f * s)
    val t = v * (1f - (1f - f) * s)
    return when (hi) {
        0 -> floatArrayOf(v, t, p)
        1 -> floatArrayOf(q, v, p)
        2 -> floatArrayOf(p, v, t)
        3 -> floatArrayOf(p, q, v)
        4 -> floatArrayOf(t, p, v)
        else -> floatArrayOf(v, p, q)
    }
}

private fun Color.Companion.fromHsv(h: Float, s: Float, v: Float): Color {
    val rgb = hsvToRgb(h, s, v)
    return Color(rgb[0], rgb[1], rgb[2])
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog(
    initialColor: Color,
    onColorSelected: (Color) -> Unit,
    onDismiss: () -> Unit
) {
    val initialHsv = remember { initialColor.toHsv() }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var hexInput by remember { mutableStateOf(initialColor.toHex()) }

    val currentColor = remember(hue, saturation) {
        Color.fromHsv(hue, saturation, 1f)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom accent color") },
        text = {
            val bg = MaterialTheme.colorScheme.background
            val isDark = 0.2126f * bg.red + 0.7152f * bg.green + 0.0722f * bg.blue < 0.5f
            val scheme = remember(currentColor) {
                generateAccentScheme(currentColor, isDark)
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(currentColor)
                            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Preview", style = MaterialTheme.typography.labelMedium)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SchemeSwatch("Primary", scheme.primary)
                            SchemeSwatch("Secondary", scheme.secondary)
                            SchemeSwatch("Tertiary", scheme.tertiary)
                            SchemeSwatch("Bg", scheme.background)
                            SchemeSwatch("Surface", scheme.surface)
                            SchemeSwatch("S.Var", scheme.surfaceVariant)
                        }
                    }
                }

                HueSlider(
                    hue = hue,
                    onHueChanged = { h ->
                        hue = h
                        hexInput = Color.fromHsv(h, saturation, 1f).toHex()
                    },
                    modifier = Modifier.fillMaxWidth().height(28.dp)
                )

                SaturationPicker(
                    hue = hue,
                    saturation = saturation,
                    onSaturationChanged = { s ->
                        saturation = s
                        hexInput = Color.fromHsv(hue, s, 1f).toHex()
                    },
                    modifier = Modifier.fillMaxWidth().height(28.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = hexInput,
                            onValueChange = { input ->
                                hexInput = input
                                val parsed = parseHexColor(input)
                                if (parsed != null) {
                                    val hsv = parsed.toHsv()
                                    hue = hsv[0]; saturation = hsv[1]
                                }
                            },
                        label = { Text("Hex") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace
                        )
                    )
                        val clipboard = LocalClipboardManager.current
                    IconButton(onClick = {
                        val text = clipboard.getText()?.text ?: return@IconButton
                        hexInput = text
                        val parsed = parseHexColor(text)
                        if (parsed != null) {
                            val hsv = parsed.toHsv()
                            hue = hsv[0]; saturation = hsv[1]
                        }
                    }) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onColorSelected(currentColor) }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SchemeSwatch(label: String, bg: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(bg)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
        )
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp))
        Text(
            bg.toHex(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp
            )
        )
    }
}

@Composable
private fun HueSlider(
    hue: Float,
    onHueChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var width by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val dragProgress by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f),
        label = "hueThumb"
    )
    val density = LocalDensity.current
    val outerBase = with(density) { 10.dp.toPx() }
    val innerBase = with(density) { 8.dp.toPx() }

    Box(
        modifier = modifier
            .onSizeChanged { width = it.width.toFloat() }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        val h = ((offset.x / width.coerceAtLeast(1f)) * 360f).coerceIn(0f, 360f)
                        onHueChanged(h)
                    },
                    onDrag = { change, _ ->
                        val h = ((change.position.x / width.coerceAtLeast(1f)) * 360f).coerceIn(0f, 360f)
                        onHueChanged(h)
                    },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false }
                )
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
        ) {
            val steps = 360
            val stepWidth = size.width / steps
            for (i in 0 until steps) {
                drawRect(
                    color = Color.fromHsv(i.toFloat(), 1f, 1f),
                    topLeft = Offset(i * stepWidth, 0f),
                    size = Size(stepWidth + 1f, size.height)
                )
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val ix = (hue / 360f) * size.width
            val cy = size.height / 2f - size.height * dragProgress
            val outerR = outerBase + outerBase * dragProgress
            val innerR = innerBase + innerBase * dragProgress
            drawCircle(color = Color.Black.copy(alpha = 0.4f), radius = outerR, center = Offset(ix, cy))
            drawCircle(color = Color.fromHsv(hue, 1f, 1f), radius = innerR, center = Offset(ix, cy))
        }
    }
}

@Composable
private fun SaturationPicker(
    hue: Float,
    saturation: Float,
    onSaturationChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var width by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val dragProgress by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f),
        label = "satThumb"
    )
    val density = LocalDensity.current
    val outerBase = with(density) { 10.dp.toPx() }
    val innerBase = with(density) { 8.dp.toPx() }

    Box(
        modifier = modifier
            .onSizeChanged { width = it.width.toFloat() }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        val s = (offset.x / width.coerceAtLeast(1f)).coerceIn(0f, 1f)
                        onSaturationChanged(s)
                    },
                    onDrag = { change, _ ->
                        val s = (change.position.x / width.coerceAtLeast(1f)).coerceIn(0f, 1f)
                        onSaturationChanged(s)
                    },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false }
                )
            }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
        ) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.White, Color.fromHsv(hue, 1f, 1f))
                )
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val ix = saturation * size.width
            val cy = size.height / 2f - size.height * dragProgress
            val outerR = outerBase + outerBase * dragProgress
            val innerR = innerBase + innerBase * dragProgress
            drawCircle(color = Color.Black.copy(alpha = 0.4f), radius = outerR, center = Offset(ix, cy))
            drawCircle(color = Color.fromHsv(hue, saturation, 1f), radius = innerR, center = Offset(ix, cy))
        }
    }
}

internal fun Color.toHex(): String {
    val r = (red * 255).roundToInt().coerceIn(0, 255)
    val g = (green * 255).roundToInt().coerceIn(0, 255)
    val b = (blue * 255).roundToInt().coerceIn(0, 255)
    return "#${r.toString(16).padStart(2, '0')}${g.toString(16).padStart(2, '0')}${b.toString(16).padStart(2, '0')}"
}

internal fun parseHexColor(hex: String): Color? {
    return try {
        val clean = hex.trimStart('#')
        if (clean.length == 6) {
            val r = clean.substring(0, 2).toInt(16)
            val g = clean.substring(2, 4).toInt(16)
            val b = clean.substring(4, 6).toInt(16)
            Color(r / 255f, g / 255f, b / 255f)
        } else null
    } catch (_: Exception) { null }
}
