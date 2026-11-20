package com.lrcstudio.app.ui.editor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
                                animationSpec = tween(280),
                                label = "lineAlpha"
                            )
                            val lineScale by animateFloatAsState(
                                targetValue = when {
                                    distance == 0 -> 1f
                                    distance == 1 -> 0.95f
                                    else -> 0.9f
                                },
                                animationSpec = tween(280),
                                label = "lineScale"
                            )

                            if (index == activeIndex) {
                                ActiveLyricLine(
                                    line = line,
                                    romanizedWords = romanizedLines.getOrNull(index),
                                    currentPositionMs = currentPositionMs,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .graphicsLayer {
                                            scaleX = lineScale
                                            scaleY = lineScale
                                        }
                                )
                            } else {
                                Text(
                                    text = displayText(line, romanizedLines.getOrNull(index)),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
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

/** The active line lights its words as they are sung. */
@Composable
private fun ActiveLyricLine(
    line: LrcLine,
    romanizedWords: List<String>?,
    currentPositionMs: Long,
    modifier: Modifier = Modifier
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    // Everything before the word being sung is lit, that word glows, the rest waits.
    val singingIndex = line.words.indexOfLast { it.startTime > 0L && currentPositionMs >= it.startTime }
    val highlighted = buildAnnotatedString {
        line.words.forEachIndexed { index, word ->
            if (index > 0 && !word.joinsPrevious) append(' ')
            val wordText = romanizedWords?.getOrNull(index)?.ifBlank { null } ?: word.text
            val style = when {
                index == singingIndex -> SpanStyle(color = accent)
                index < singingIndex -> SpanStyle(color = textColor)
                word.startTime > 0L -> SpanStyle(color = textColor.copy(alpha = 0.38f))
                else -> SpanStyle(color = textColor.copy(alpha = 0.55f))
            }
            withStyle(style) { append(wordText) }
        }
    }
    val hasWords = line.words.isNotEmpty()
    Text(
        text = if (hasWords) highlighted else AnnotatedString(displayText(line, romanizedWords)),
        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp),
        fontWeight = FontWeight.Bold,
        color = if (hasWords) Color.Unspecified else textColor,
        textAlign = TextAlign.Center,
        modifier = modifier.padding(vertical = 4.dp)
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
