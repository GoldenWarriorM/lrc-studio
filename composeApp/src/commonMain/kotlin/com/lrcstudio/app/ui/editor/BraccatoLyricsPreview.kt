package com.lrcstudio.app.ui.editor

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lrcstudio.app.data.model.LrcLine
import com.lrcstudio.app.data.model.VocalRole
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Metrolist's lyric metrics: 36sp text, 1.3 line height, 12dp insets, 16dp gap. */
private const val LYRIC_TEXT_SIZE = 36f
private const val LYRIC_ALPHA_MS = 750
private const val LYRIC_SCALE_MS = 800

/** Insets for lines on opposite sides when more than one singer is present. */
private const val MULTI_SINGER_PADDING_RATIO = 0.10f

// Metrolist's scroll timing: 750ms apple duration * 0.65, spring zeta 0.78.
private const val LYRIC_SCROLL_MS = 487.5f
private const val SPRING_ZETA_DEFAULT = 0.78f
private const val LYRICS_FAR_JUMP_VIEWPORTS = 2f

private val LyPlusScrollEasing = CubicBezierEasing(0.41f, 0f, 0.12f, 0.99f)

/** Port of Metrolist's LyPlusSpring from LyricsScrollAnimation.kt. */
private class LyPlusSpring(
    private val peakMs: Float,
    zeta: Float = SPRING_ZETA_DEFAULT,
    settleRatio: Float = 1.5f
) {
    private val tailMs = peakMs * settleRatio
    private val peakValue: Float
    val totalMs: Float = peakMs + tailMs

    init {
        val peakS = peakMs / 1000.0
        val omegaD = PI / peakS
        val omega0 = omegaD / sqrt(1.0 - zeta * zeta)
        val decayRate = zeta * omega0
        val dt = 1.0 / 120.0
        var pv = 1.0
        var t = 0.0
        while (t <= peakS) {
            pv = 1.0 - exp(-decayRate * t) * (cos(omegaD * t) + (decayRate / omegaD) * sin(omegaD * t))
            t += dt
        }
        peakValue = pv.toFloat()
    }

    fun value(tMs: Float): Float =
        when {
            tMs <= 0f -> 0f
            tMs >= totalMs -> 1f
            tMs < peakMs -> peakValue * LyPlusScrollEasing.transform(tMs / peakMs)
            else -> {
                val u = (tMs - peakMs) / tailMs
                1f + (peakValue - 1f) * 0.5f * (1f + cos(PI.toFloat() * u))
            }
        }
}

/**
 * Metrolist's LyricsUtils.assignLineAlignments: the first singer takes the left
 * side, a different singer alternates to the other side, groups stay centred,
 * and a list that is almost entirely right-aligned is mirrored.
 */
private fun assignLineAlignments(lyrics: List<LrcLine>): List<TextAlign> {
    val result = ArrayList<TextAlign>(lyrics.size)
    var currentSideIsLeft = true
    var lastPersonId: String? = null
    var rightCount = 0
    var totalCount = 0

    lyrics.forEach { line ->
        val singerId = when {
            line.isBackground -> null
            line.agent == 1000 -> "v1000"
            line.agent == 0 -> null
            else -> "v${line.agent}"
        }
        val align = if (singerId != null) {
            val isGroup = singerId == "v1000" || singerId.startsWith("g")
            when {
                isGroup -> TextAlign.Center
                lastPersonId == null -> {
                    currentSideIsLeft = true
                    if (currentSideIsLeft) TextAlign.Start else TextAlign.End
                }
                singerId != lastPersonId -> {
                    currentSideIsLeft = !currentSideIsLeft
                    lastPersonId = singerId
                    if (currentSideIsLeft) TextAlign.Start else TextAlign.End
                }
                else -> if (currentSideIsLeft) TextAlign.Start else TextAlign.End
            }.also { if (!isGroup) lastPersonId = singerId }
        } else {
            TextAlign.Center
        }
        if (align != TextAlign.Center) {
            totalCount++
            if (align == TextAlign.End) rightCount++
        }
        result += align
    }

    if (totalCount > 0 && rightCount.toFloat() / totalCount >= 0.85f) {
        for (i in result.indices) {
            result[i] = when (result[i]) {
                TextAlign.Start -> TextAlign.End
                TextAlign.End -> TextAlign.Start
                else -> result[i]
            }
        }
    }
    return result
}

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
    onSeek: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scrollSpring = remember { LyPlusSpring(LYRIC_SCROLL_MS) }
    val activeIndex = if (currentLineIndex in lyrics.indices) currentLineIndex else 0
    val background = MaterialTheme.colorScheme.background
    val accent = MaterialTheme.colorScheme.primary
    val lineAlignments = remember(lyrics) { assignLineAlignments(lyrics) }
    val hasMultipleSingers = remember(lyrics, lineAlignments) {
        lineAlignments.distinct().size > 1 ||
            lyrics.mapNotNull { it.agent.takeIf { agent -> agent != 0 } }.distinct().size > 1
    }

    LaunchedEffect(activeIndex, lyrics.size) {
        if (lyrics.isEmpty()) return@LaunchedEffect
        val target = activeIndex.coerceIn(lyrics.indices)
        // Metrolist puts the active line's centre on the anchor: the list jumps
        // by the full distance and the rows slide into place with LyPlusSpring,
        // which keeps one continuous motion instead of a list-state animation.
        fun centreDelta(): Float? {
            val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target }
                ?: return null
            val viewportCentre = listState.layoutInfo.viewportSize.height / 2f
            return info.offset + info.size / 2f - viewportCentre
        }
        var delta = centreDelta()
        if (delta == null) {
            // The target is outside the composed window: place it, wait for the
            // layout to report its real position, then centre it.
            listState.scrollToItem(target)
            withFrameNanos { }
            delta = centreDelta()
        }
        if (delta == null || abs(delta) < 0.5f) return@LaunchedEffect
        val viewportHeight = listState.layoutInfo.viewportSize.height
        if (abs(delta) > viewportHeight * LYRICS_FAR_JUMP_VIEWPORTS) {
            // A seek far away should not slide through every line in between.
            listState.scrollBy(delta)
            return@LaunchedEffect
        }
        // Animate the list itself with Metrolist's spring curve. Its own renderer
        // moves each row instead, but the curve is the same and a scroll animation
        // can never leave the rows stuck displaced off-screen.
        listState.animateScrollBy(
            value = delta,
            animationSpec = tween(
                durationMillis = scrollSpring.totalMs.roundToInt(),
                easing = Easing { fraction -> scrollSpring.value(fraction * scrollSpring.totalMs) }
            )
        )
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
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 0.dp,
                            end = 0.dp,
                            top = maxHeight / 2,
                            bottom = maxHeight / 2
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        itemsIndexed(lyrics, key = { index, line -> "${line.timestamp}-$index" }) { index, line ->
                            val distance = abs(index - activeIndex)
                            val isActive = index == activeIndex
                            // Metrolist keeps one text size for every line and only
                            // dims and shrinks the ones around the active line.
                            val lineAlpha by animateFloatAsState(
                                targetValue = if (isActive) 1f else 0.35f,
                                animationSpec = tween(
                                    durationMillis = LYRIC_ALPHA_MS,
                                    easing = FastOutSlowInEasing
                                ),
                                label = "lineAlpha"
                            )
                            val lineScale by animateFloatAsState(
                                targetValue = when {
                                    isActive -> 1f
                                    distance == 1 -> 0.97f
                                    distance == 2 || distance == 3 -> 0.93f
                                    distance == 4 -> 0.90f
                                    else -> 0.85f
                                },
                                animationSpec = tween(
                                    durationMillis = LYRIC_SCALE_MS,
                                    easing = FastOutSlowInEasing
                                ),
                                label = "lineScale"
                            )
                            val alignment = lineAlignments.getOrElse(index) { TextAlign.Center }
                            // Metrolist pulls background fragments out of the line and
                            // draws them as a smaller block next to the main text.
                            val mainWords = remember(line) {
                                line.words.filter { it.role == VocalRole.MAIN }
                            }
                            val bgWords = remember(line) {
                                line.words.filter { it.role != VocalRole.MAIN }
                            }
                            val romanized = romanizedLines.getOrNull(index)
                            val romanizedBg = remember(romanized) {
                                romanized?.filterIndexed { i, _ ->
                                    line.words.getOrNull(i)?.role != VocalRole.MAIN
                                }
                            }
                            val romanizedMain = remember(romanized) {
                                romanized?.filterIndexed { i, _ ->
                                    line.words.getOrNull(i)?.role == VocalRole.MAIN
                                }
                            }
                            // Singers on opposite sides get an inset so the two
                            // voices do not overlap (MULTI_SINGER_PADDING_RATIO).
                            val sideInset = if (hasMultipleSingers) {
                                maxWidth * MULTI_SINGER_PADDING_RATIO
                            } else {
                                0.dp
                            }
                            val startInset = when (alignment) {
                                TextAlign.End -> sideInset
                                TextAlign.Center -> sideInset / 2
                                else -> 0.dp
                            }
                            val endInset = when (alignment) {
                                TextAlign.Start -> sideInset
                                TextAlign.Center -> sideInset / 2
                                else -> 0.dp
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    // Tapping a line seeks to it, as in Metrolist's lyric list.
                                    .clickable(enabled = line.timestamp > 0L) { onSeek(line.timestamp) }
                                    .padding(start = startInset, end = endInset)
                                    .padding(horizontal = 12.dp, vertical = 12.dp)
                                    .graphicsLayer {
                                        alpha = lineAlpha
                                        scaleX = lineScale
                                        scaleY = lineScale
                                    }
                            ) {
                                if (bgWords.isNotEmpty()) {
                                    ActiveLyricLine(
                                        line = line.copy(words = bgWords),
                                        romanizedWords = romanizedBg,
                                        currentPositionMs = currentPositionMs,
                                        isPlaying = isPlaying,
                                        active = isActive,
                                        fontSize = (LYRIC_TEXT_SIZE * 0.62f).sp,
                                        alignment = alignment,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            // LyricsBgReveal: 0.8 while focused, never below 0.65.
                                            .graphicsLayer { alpha = if (isActive) 0.8f else 0.65f }
                                            .padding(bottom = 4.dp)
                                    )
                                }
                                if (mainWords.isNotEmpty() || (bgWords.isEmpty() && line.text.isNotBlank())) {
                                    ActiveLyricLine(
                                        line = if (mainWords.isEmpty()) {
                                            line.copy(words = emptyList())
                                        } else {
                                            line.copy(words = mainWords)
                                        },
                                        romanizedWords = romanizedMain,
                                        currentPositionMs = currentPositionMs,
                                        isPlaying = isPlaying,
                                        active = isActive,
                                        fontSize = LYRIC_TEXT_SIZE.sp,
                                        alignment = alignment,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                    // Lines fade into the background at the edges of the list.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
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
                            .height(160.dp)
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
 * A lyric row: the active line (and a line fading out after it stops being
 * active) is drawn by the Braccato renderer ported from Metrolist; a line that
 * has not been sung yet is drawn by the same renderer in its inactive state.
 */
@Composable
private fun ActiveLyricLine(
    line: LrcLine,
    romanizedWords: List<String>?,
    currentPositionMs: Long,
    isPlaying: Boolean,
    active: Boolean,
    fontSize: TextUnit,
    alignment: TextAlign = TextAlign.Center,
    modifier: Modifier = Modifier
) {
    BraccatoWordLine(
        line = line,
        romanizedWords = romanizedWords,
        currentPositionMs = currentPositionMs,
        isPlaying = isPlaying,
        active = active,
        fontSize = fontSize,
        textColor = MaterialTheme.colorScheme.onSurface,
        accent = MaterialTheme.colorScheme.primary,
        bgAccent = MaterialTheme.colorScheme.tertiary,
        alignment = alignment,
        modifier = modifier
    )
}
