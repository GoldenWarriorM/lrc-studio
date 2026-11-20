package com.lrcstudio.app.ui.editor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lrcstudio.app.data.model.LrcLine
import kotlin.math.abs

/**
 * Beta preview renderer in the spirit of Metrolist's Braccato lyric view:
 * the sung line is centered, large and lit word by word, while the lines above
 * and below fade and shrink with distance. It is display-only; playback, timing
 * and every export keep using the editor's own state.
 */
@Composable
fun BraccatoLyricsPreview(
    title: String,
    lyrics: List<LrcLine>,
    currentLineIndex: Int,
    currentPositionMs: Long,
    isPlaying: Boolean,
    romanizedLines: List<List<String>?>,
    onPlayPause: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val activeIndex = if (currentLineIndex in lyrics.indices) currentLineIndex else 0
    val background = MaterialTheme.colorScheme.background
    val accent = MaterialTheme.colorScheme.primary

    LaunchedEffect(activeIndex, lyrics.size) {
        if (lyrics.isEmpty()) return@LaunchedEffect
        listState.animateScrollToItem(activeIndex.coerceIn(lyrics.indices))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        accent.copy(alpha = 0.30f),
                        background.copy(alpha = 0.94f),
                        background
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title.ifBlank { "Preview" },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Braccato preview (beta)",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent
                    )
                }
                FilledTonalIconButton(
                    onClick = onClose,
                    shape = RoundedCornerShape(12.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close preview")
                }
            }

            if (lyrics.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No lyrics yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                BoxWithConstraints(modifier = Modifier.weight(1f)) {
                    val topInset = maxHeight * 0.30f
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 24.dp,
                            end = 24.dp,
                            top = topInset,
                            bottom = maxHeight * 0.45f
                        ),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        itemsIndexed(lyrics, key = { index, line -> "${line.timestamp}-$index" }) { index, line ->
                            val distance = abs(index - activeIndex)
                            val lineAlpha by animateFloatAsState(
                                targetValue = when {
                                    distance == 0 -> 1f
                                    distance == 1 -> 0.7f
                                    distance == 2 -> 0.4f
                                    else -> 0.2f
                                },
                                animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f),
                                label = "lineAlpha"
                            )
                            val lineScale by animateFloatAsState(
                                targetValue = when {
                                    distance == 0 -> 1f
                                    distance == 1 -> 0.95f
                                    else -> 0.9f
                                },
                                animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f),
                                label = "lineScale"
                            )

                            val isActive = index == activeIndex
                            // The active line grows into place instead of being swapped
                            // for a different composable.
                            val fontSize by animateFloatAsState(
                                targetValue = if (isActive) 28f else 20f,
                                animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
                                label = "lineSize"
                            )
                            ActiveLyricLine(
                                line = line,
                                romanizedWords = romanizedLines.getOrNull(index),
                                currentPositionMs = currentPositionMs,
                                active = isActive,
                                fontSize = fontSize.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        alpha = lineAlpha
                                        scaleX = lineScale
                                        scaleY = lineScale
                                    }
                            )
                        }
                    }
                    // Lines fade into the background at the edges of the list.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    0f to background.copy(alpha = 0.85f),
                                    1f to Color.Transparent
                                )
                            )
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    1f to background
                                )
                            )
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                FilledIconButton(
                    onClick = onPlayPause,
                    shape = RoundedCornerShape(50),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = accent
                    )
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play"
                    )
                }
            }
        }

    }
}

/**
 * The active line lights its words as they are sung. The sweep, glow and wobble
 * follow Metrolist's Braccato renderer (words/Braccato.kt): the fill crosses a
 * word with a soft leading edge, the singing word lifts and swells through a
 * sine wave, and a glow follows it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveLyricLine(
    line: LrcLine,
    romanizedWords: List<String>?,
    currentPositionMs: Long,
    active: Boolean,
    fontSize: TextUnit,
    modifier: Modifier = Modifier
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    if (!active) {
        Text(
            text = displayText(line, romanizedWords),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = fontSize),
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            textAlign = TextAlign.Center,
            modifier = modifier.padding(vertical = 4.dp)
        )
        return
    }
    if (line.words.isEmpty()) {
        Text(
            text = line.text,
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = fontSize),
            fontWeight = FontWeight.Bold,
            color = textColor,
            textAlign = TextAlign.Center,
            modifier = modifier.padding(vertical = 4.dp)
        )
        return
    }

    val dim = textColor.copy(alpha = 0.32f)
    // The word currently being sung, and how far through it the audio is.
    val singingIndex = line.words.indexOfLast { it.startTime > 0L && currentPositionMs >= it.startTime }
    val singingProgress = if (singingIndex >= 0) {
        val word = line.words[singingIndex]
        val end = line.words.getOrNull(singingIndex + 1)
            ?.takeIf { it.startTime > word.startTime }?.startTime
            ?: word.endTime.takeIf { it > word.startTime }
            ?: (word.startTime + 350L)
        ((currentPositionMs - word.startTime).toFloat() / (end - word.startTime).toFloat())
            .coerceIn(0f, 1f)
    } else {
        0f
    }
    val fontSizePx = with(LocalDensity.current) { fontSize.toPx() }

    FlowRow(
        modifier = modifier.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        line.words.forEachIndexed { index, word ->
            val wordText = romanizedWords?.getOrNull(index)?.ifBlank { null } ?: word.text
            val trailing = if (index < line.words.lastIndex && !line.words[index + 1].joinsPrevious) " " else ""
            val progress = when {
                index < singingIndex -> 1f
                index == singingIndex -> singingProgress
                word.startTime == 0L -> if (index < singingIndex) 1f else 0f
                else -> 0f
            }
            // The wave peaks in the middle of the word, so it lifts and settles.
            val wave = if (index == singingIndex) kotlin.math.sin(Math.PI * progress).toFloat().coerceIn(0f, 1f) else 0f
            Text(
                text = wordText + trailing,
                style = TextStyle(
                    brush = swipeBrush(progress, accent, dim),
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    shadow = if (wave > 0.01f) {
                        Shadow(
                            color = accent.copy(alpha = 0.35f * wave),
                            offset = Offset.Zero,
                            blurRadius = fontSizePx * 0.8f
                        )
                    } else {
                        null
                    }
                ),
                modifier = Modifier.graphicsLayer {
                    translationY = -fontSizePx * 0.0375f * wave
                    scaleX = 1f + 0.025f * wave
                    scaleY = 1f + 0.06f * wave
                }
            )
        }
    }
}

/**
 * Braccato's fill: solid accent up to the progress point, then a soft edge into
 * the waiting colour, so the light crosses the word instead of switching on.
 */
private fun swipeBrush(progress: Float, accent: Color, dim: Color): Brush {
    val p = progress.coerceIn(0f, 1f)
    if (p >= 1f) return SolidColor(accent)
    if (p <= 0f) return SolidColor(dim)
    val edge = 0.12f
    val start = (p - edge).coerceIn(0.001f, 0.998f)
    val end = (p + edge).coerceIn(start + 0.001f, 0.999f)
    return Brush.horizontalGradient(
        0f to accent,
        start to accent,
        end to dim,
        1f to dim
    )
}

private fun displayText(line: LrcLine, romanizedWords: List<String>?): String {
    if (line.words.isEmpty()) return line.text
    return buildString {
        line.words.forEachIndexed { index, word ->
            if (index > 0 && !word.joinsPrevious) append(' ')
            append(romanizedWords?.getOrNull(index)?.ifBlank { null } ?: word.text)
        }
    }
}
