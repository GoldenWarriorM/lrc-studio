/*
 * Word animation ported from Metrolist's words/Braccato.kt (GPL-3.0), which in
 * turn ports Braccato (https://github.com/better-lyrics/braccato, MIT).
 *
 * The constants, the swipe edge/coverage maths, the wobble timing table, the
 * glow scheduling, the fade-in/fade-out times and the exit handling below are
 * kept as they are there; only the cluster model is adapted to our
 * LrcLine/WordTimestamp (a "cluster" is one syllable/word fragment, and a
 * syllable written with several characters is split into one cluster per
 * character, which is what makes the light cross the word letter by letter).
 */

package com.lrcstudio.app.ui.editor

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lrcstudio.app.data.model.LrcLine
import com.lrcstudio.app.data.model.WordTimestamp
import kotlin.math.max
import kotlin.math.min

private val BraccatoEase = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
private val BraccatoEaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
private val BraccatoEaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)

private val GlowFillBrush = SolidColor(Color.White.copy(alpha = 0f))

private const val SWIPE_LEAD_RATIO = 0.1f
private const val SWIPE_DURATION_RATIO = 1.6f
private const val SWIPE_START_FROM = -0.2f
private const val SWIPE_END_FROM = -0.1f
private const val SWIPE_START_TO = 1.4f
private const val SWIPE_END_TO = 1.5f
private const val SWIPE_EDGE = 0.1f

private const val GLOW_RADIUS_REM = 0.8f
private const val GLOW_DURATION_RATIO = 1.2f
private const val GLOW_MIN_MS = 1200f
private const val GLOW_COLOR_ALPHA = 0.5f

private const val FADE_IN_MS = 330f
private const val FADE_OUT_MS = 500f

private const val WOBBLE_MS = 1000f
private const val WOBBLE_PEAK_OFFSET = 0.125f
private const val WOBBLE_SETTLE_OFFSET = 0.75f
private const val WOBBLE_TRANSLATE_EM = 0.05f
private const val WOBBLE_SCALE_X = 1.025f

private const val WOBBLE_MODE = 1

// Skia/Android turn a text Shadow's blurRadius into a Gaussian sigma differently
// from CSS (sigma = radius / 2).
private const val GLOW_BLUR_SCALE = 0.85f

private const val FINISH_MS = 180f
private const val MAX_EXIT_MS = 15_000L

private const val WAVE_MS = 900f
private const val WAVE_CREST = 0.4f
private const val WAVE_PEAK_EM = -0.05f
private const val WAVE_SETTLE_EM = -0.02f
private const val WAVE_EMPHASIS_SCALE = 1.11f

private const val LONG_WORD_MS = 1500f
private const val LONG_GROUP_CHARS = 20

private const val KIND_NONE = 0
private const val KIND_LETTER = 1
private const val KIND_WORD = 2

private const val MIN_WORD_ANIMATION_DURATION_MS = 100L

private fun lyricsWordAnimationDurationMs(durationMs: Long): Long =
    durationMs.coerceAtLeast(MIN_WORD_ANIMATION_DURATION_MS)

private class BraccatoStatics(
    n: Int
) {
    val kind = IntArray(n)
    val startMs = FloatArray(n)
    val durMs = FloatArray(n)
    val letterIdx = IntArray(n)
    val letterCount = IntArray(n) { 1 }
    val swipeDur = FloatArray(n)
    val lead = FloatArray(n)
    val begin = FloatArray(n)
    val finish = FloatArray(n)
    val waveDelay = FloatArray(n)
    val waveDur = FloatArray(n) { WAVE_MS }
    val emphasise = BooleanArray(n)
    val longWord = BooleanArray(n)
    var lineStartMs = Long.MAX_VALUE
    var lineEndMs = 0L
    var fxEndMs = 0L
    val lineSynced = BooleanArray(n)
    val glowDur = FloatArray(n)
    val groupStartMs = FloatArray(n)
    val groupWobble = BooleanArray(n)
}

/** One draw unit: a whole word, or a single character of a syllable. */
private class BraccatoCluster(
    val wordIndex: Int,
    val text: String,
    val letterIdx: Int,
    val letterCount: Int,
    val trailingSpace: Boolean
)

private class BraccatoLine(
    val clusters: List<BraccatoCluster>,
    val statics: BraccatoStatics,
    val layouts: List<TextLayoutResult>,
    val xs: FloatArray,
    val ys: FloatArray,
    val widths: FloatArray,
    val groupCx: FloatArray,
    val totalHeight: Float,
    val lineHeight: Float
)

private class BraccatoRuntime {
    var hadActive = false
    var lastPos = 0L
    var exitPos = -1L
    var exitWall = -1L
}

/** Joining scripts are laid out letter by letter, the way Braccato does it. */
private fun isJoiningChar(ch: Char): Boolean {
    val code = ch.code
    return code in 0x3040..0x30FF || // hiragana + katakana
        code in 0x3400..0x4DBF || // CJK extension A
        code in 0x4E00..0x9FFF || // CJK unified ideographs
        code in 0xFF65..0xFF9F // halfwidth katakana
}

private fun braccatoEdge(
    b: BraccatoStatics,
    i: Int,
    elapsedMs: Float
): Float {
    val sd = b.swipeDur[i]
    if (sd <= 0f) return (b.letterCount[i] + 2) / 2f
    val tt = (elapsedMs + b.lead[i]).coerceIn(b.begin[i], b.finish[i])
    val start = SWIPE_START_FROM + (SWIPE_START_TO - SWIPE_START_FROM) * tt / sd
    return start * b.letterCount[i] - b.letterIdx[i]
}

private fun braccatoCoverage(e0: Float, f: Float): Float {
    var area = e0.coerceIn(0f, 1f)
    val rampStart = max(e0, 0f)
    val rampEnd = min(e0 + f, 1f)
    if (rampEnd > rampStart && f > 0f) {
        val a0 = 1f - (rampStart - e0) / f
        val a1 = 1f - (rampEnd - e0) / f
        area += (rampEnd - rampStart) * (a0 + a1) / 2f
    }
    return area.coerceIn(0f, 1f)
}

private fun braccatoExitOpacity(elapsedMs: Float, completionMs: Float = 0f): Float =
    1f - BraccatoEase.transform(((elapsedMs - completionMs) / FADE_OUT_MS).coerceIn(0f, 1f))

private fun buildBraccatoStatics(
    words: List<WordTimestamp>,
    clusters: List<BraccatoCluster>
): BraccatoStatics {
    val n = clusters.size
    val out = BraccatoStatics(n)

    // A "group" is a run of fragments where every next one joins the previous:
    // the wobble is applied to the whole group, as Braccato does per word.
    val groupOfWord = IntArray(words.size)
    var groups = -1
    for (i in words.indices) {
        if (i == 0 || !words[i].joinsPrevious) groups++
        groupOfWord[i] = groups
    }
    val groupStart = LongArray(groups + 1) { Long.MAX_VALUE }
    val groupChars = IntArray(groups + 1)
    for (i in words.indices) {
        val g = groupOfWord[i]
        groupStart[g] = min(groupStart[g], words[i].startTime)
        groupChars[g] += words[i].text.trim().length
    }

    for (i in clusters.indices) {
        val vc = clusters[i]
        val word = words[vc.wordIndex]
        val text = vc.text
        if (text.isBlank()) {
            out.kind[i] = KIND_NONE
            continue
        }
        out.kind[i] = if (vc.letterCount > 1) KIND_LETTER else KIND_WORD

        val nextWordStart = words.getOrNull(vc.wordIndex + 1)
            ?.startTime?.takeIf { it > word.startTime }
        val ownEnd = word.endTime.takeIf { it > word.startTime }
        val end = when (out.kind[i]) {
            // A syllable's letters share its timing; a word uses its explicit end.
            KIND_WORD -> ownEnd ?: nextWordStart ?: (word.startTime + 350L)
            else -> nextWordStart ?: ownEnd ?: (word.startTime + 350L)
        }
        val startMs = word.startTime.toFloat()
        val rawDur = (end - word.startTime).toFloat()
        val timedDur = max(0f, rawDur)
        val animationDur = lyricsWordAnimationDurationMs(timedDur.toLong()).toFloat()

        out.startMs[i] = startMs
        out.durMs[i] = timedDur
        out.lineSynced[i] = rawDur <= 0f
        out.longWord[i] = timedDur > LONG_WORD_MS
        out.emphasise[i] = out.kind[i] == KIND_LETTER && out.longWord[i]
        out.lineStartMs = min(out.lineStartMs, word.startTime)
        out.lineEndMs = max(out.lineEndMs, max(word.startTime, end))
        out.swipeDur[i] = animationDur * SWIPE_DURATION_RATIO
        out.lead[i] = animationDur * SWIPE_LEAD_RATIO
        out.glowDur[i] =
            if (out.lineSynced[i]) GLOW_MIN_MS else max(timedDur * GLOW_DURATION_RATIO, GLOW_MIN_MS)

        val letters = vc.letterCount.coerceAtLeast(1)
        val index = vc.letterIdx
        out.letterCount[i] = letters
        out.letterIdx[i] = index

        val sd = out.swipeDur[i]
        if (sd > 0f) {
            val timeWhereEnd = { v: Float -> sd * (v - SWIPE_END_FROM) / (SWIPE_END_TO - SWIPE_END_FROM) }
            val timeWhereStart = { v: Float -> sd * (v - SWIPE_START_FROM) / (SWIPE_START_TO - SWIPE_START_FROM) }
            out.begin[i] = timeWhereEnd(index.toFloat() / letters).coerceIn(0f, sd)
            out.finish[i] = timeWhereStart((index + 1f) / letters).coerceIn(0f, sd)
        }

        if (out.kind[i] == KIND_LETTER) {
            if (sd > 0f) {
                val windowDur = max(out.finish[i] - out.begin[i], 1f)
                val touched = out.begin[i] - out.lead[i]
                val lit = touched + windowDur
                val rise = WAVE_MS * WAVE_CREST
                if (lit - rise > touched) {
                    out.waveDelay[i] = touched
                    out.waveDur[i] = (lit - touched) / WAVE_CREST
                } else {
                    out.waveDelay[i] = lit - rise
                    out.waveDur[i] = WAVE_MS
                }
            } else {
                out.waveDelay[i] = timedDur * index / letters
                out.waveDur[i] = WAVE_MS
            }
        }

        val g = groupOfWord[vc.wordIndex]
        if (g >= 0) {
            out.groupStartMs[i] = groupStart[g].toFloat()
            out.groupWobble[i] = groupChars[g] <= LONG_GROUP_CHARS
        } else {
            out.groupStartMs[i] = startMs
            out.groupWobble[i] = false
        }

        var fxEnd = if (out.longWord[i]) startMs + out.glowDur[i] else startMs
        if (out.kind[i] == KIND_LETTER) fxEnd = max(fxEnd, startMs + out.waveDelay[i] + out.waveDur[i])
        if (out.groupWobble[i]) fxEnd = max(fxEnd, out.groupStartMs[i] + WOBBLE_MS)
        out.fxEndMs = max(out.fxEndMs, fxEnd.toLong())
    }
    return out
}

private fun buildLayout(
    words: List<WordTimestamp>,
    texts: List<String>,
    measurer: TextMeasurer,
    style: TextStyle,
    maxWidth: Float
): BraccatoLine {
    val clusters = ArrayList<BraccatoCluster>(words.size)
    for (i in words.indices) {
        val t = texts[i].trim()
        val perLetter = t.length > 1 && t.all { isJoiningChar(it) }
        val space = i < words.lastIndex && !words[i + 1].joinsPrevious
        if (perLetter) {
            t.forEachIndexed { idx, ch ->
                clusters += BraccatoCluster(
                    wordIndex = i,
                    text = ch.toString(),
                    letterIdx = idx,
                    letterCount = t.length,
                    trailingSpace = space && idx == t.lastIndex
                )
            }
        } else {
            clusters += BraccatoCluster(
                wordIndex = i,
                text = if (t.isEmpty()) texts[i] else t,
                letterIdx = 0,
                letterCount = 1,
                trailingSpace = space
            )
        }
    }

    val layouts = clusters.map { c ->
        measurer.measure(
            text = AnnotatedString(c.text),
            style = style,
            softWrap = false,
            constraints = Constraints()
        )
    }
    val widths = FloatArray(clusters.size) { layouts[it].size.width.toFloat() }
    val spaceWidth = measurer.measure(
        text = AnnotatedString(" "),
        style = style,
        softWrap = false,
        constraints = Constraints()
    ).size.width.toFloat()
    val advances = FloatArray(clusters.size) { widths[it] + if (clusters[it].trailingSpace) spaceWidth else 0f }
    val heights = FloatArray(clusters.size) { layouts[it].size.height.toFloat() }

    // Wrap at word boundaries, then center every row.
    val wordAdvance = HashMap<Int, Float>()
    clusters.forEachIndexed { i, c -> wordAdvance[c.wordIndex] = (wordAdvance[c.wordIndex] ?: 0f) + advances[i] }
    val xs = FloatArray(clusters.size)
    val ys = FloatArray(clusters.size)
    var rowTop = 0f
    var rowWidth = 0f
    var rowStart = 0
    var cursorX = 0f
    val rows = ArrayList<Triple<Int, Int, Float>>() // start, end, top
    fun closeRow(endExclusive: Int) {
        rows += Triple(rowStart, endExclusive, rowTop)
        rowTop += layouts.getOrNull(rowStart)?.size?.height?.toFloat() ?: 0f
        rowStart = endExclusive
        rowWidth = 0f
        cursorX = 0f
    }
    for (i in clusters.indices) {
        val c = clusters[i]
        if (c.letterIdx == 0 && cursorX > 0f && cursorX + (wordAdvance[c.wordIndex] ?: 0f) > maxWidth) {
            closeRow(i)
        }
        val layout = layouts[i]
        xs[i] = cursorX
        ys[i] = rowTop
        cursorX += advances[i]
        rowWidth = cursorX
        if (i == clusters.lastIndex) closeRow(i + 1)
    }
    val totalHeight = rowTop
    val lineHeight = layouts.maxOfOrNull { it.size.height.toFloat() } ?: 0f
    for ((start, end, top) in rows) {
        val shift = (maxWidth - (if (end > start) xs[end - 1] + advances[end - 1] else 0f)) / 2f
        for (i in start until end) {
            xs[i] += shift
            ys[i] = top
        }
    }

    val statics = buildBraccatoStatics(words, clusters)
    // Horizontal center of the whole group each cluster belongs to, used as the
    // wobble pivot.
    val groupCx = FloatArray(clusters.size)
    run {
        val extents = HashMap<Int, FloatArray>()
        clusters.forEachIndexed { i, c ->
            val e = extents.getOrPut(c.wordIndex) { floatArrayOf(Float.MAX_VALUE, -Float.MAX_VALUE) }
            e[0] = min(e[0], xs[i])
            e[1] = max(e[1], xs[i] + widths[i])
        }
        clusters.forEachIndexed { i, c ->
            val e = extents[c.wordIndex]!!
            groupCx[i] = (e[0] + e[1]) / 2f
        }
    }

    return BraccatoLine(
        clusters = clusters,
        statics = statics,
        layouts = layouts,
        xs = xs,
        ys = ys,
        widths = widths,
        groupCx = groupCx,
        totalHeight = totalHeight,
        lineHeight = lineHeight
    )
}

/**
 * The Braccato word renderer: one canvas per line, every cluster lit through a
 * travelling gradient edge, with the wobble, glow, fade-in/out and exit of the
 * original implementation.
 */
@Composable
internal fun BraccatoWordLine(
    line: LrcLine,
    romanizedWords: List<String>?,
    currentPositionMs: Long,
    isPlaying: Boolean,
    active: Boolean,
    fontSize: TextUnit,
    textColor: Color,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val words = line.words
    if (words.isEmpty()) {
        Text(
            text = line.text,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = fontSize,
                lineHeight = fontSize * 1.3f,
                fontWeight = FontWeight.Bold
            ),
            color = textColor,
            textAlign = TextAlign.Center,
            modifier = modifier
        )
        return
    }

    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val texts = remember(words, romanizedWords) {
        words.mapIndexed { i, w -> romanizedWords?.getOrNull(i)?.ifBlank { null } ?: w.text }
    }
    // Metrolist's lyric style: bold body text, 1.3 line height, and no extra
    // tracking for joining scripts.
    val hasJoiningScript = remember(words) { words.any { w -> w.text.any { isJoiningChar(it) } } }
    val fontFamily = MaterialTheme.typography.bodyLarge.fontFamily
    val style = remember(fontSize, hasJoiningScript, fontFamily) {
        TextStyle(
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            lineHeight = fontSize * 1.3f,
            letterSpacing = if (hasJoiningScript) 0.sp else (-0.5).sp,
            fontFamily = fontFamily
        )
    }

    val runtime = remember { BraccatoRuntime() }
    var frameWallMs by remember { mutableLongStateOf(0L) }
    var anchorPos by remember { mutableLongStateOf(currentPositionMs) }
    var anchorWall by remember { mutableLongStateOf(-1L) }

    LaunchedEffect(currentPositionMs) {
        if (frameWallMs > 0L) {
            anchorPos = currentPositionMs
            anchorWall = frameWallMs
        }
    }

    val latestPosition by rememberUpdatedState(currentPositionMs)
    LaunchedEffect(active) {
        if (active) {
            if (isPlaying) {
                withFrameNanos { frameWallMs = it / 1_000_000L }
                if (anchorWall <= 0L) {
                    anchorWall = frameWallMs
                    anchorPos = latestPosition
                }
                while (true) withFrameNanos { frameWallMs = it / 1_000_000L }
            }
        } else if (runtime.hadActive) {
            withFrameNanos { frameWallMs = it / 1_000_000L }
            if (runtime.exitWall <= 0L) {
                runtime.exitWall = frameWallMs
                runtime.exitPos = runtime.lastPos
            }
            val startWall = frameWallMs
            while (runtime.hadActive && frameWallMs - startWall < MAX_EXIT_MS) {
                withFrameNanos { frameWallMs = it / 1_000_000L }
            }
        }
    }

    // Playback position between two position updates, so the sweep stays smooth.
    val anchor = if (isPlaying && anchorWall > 0L) {
        anchorPos + (frameWallMs - anchorWall)
    } else {
        currentPositionMs
    }

    var mode = 0
    var pos = anchor
    var fxPos = anchor
    var fadeOpacity = 1f

    BoxWithConstraints(modifier = modifier) {
        val widthPx = constraints.maxWidth
        if (widthPx <= 0) return@BoxWithConstraints
        val layout = remember(words, texts, style, widthPx) {
            buildLayout(words, texts, measurer, style, widthPx.toFloat())
        }
        val bst = layout.statics

        if (active) {
            runtime.hadActive = true
            runtime.exitPos = -1L
            runtime.exitWall = -1L
            pos = anchor
            runtime.lastPos = pos
            fxPos = pos
            mode = 1
        } else if (runtime.hadActive && runtime.exitWall > 0L) {
            val elapsed = (frameWallMs - runtime.exitWall).toFloat().coerceAtLeast(0f)
            val maxExpectedPos = runtime.exitPos + (if (isPlaying) elapsed.toLong() else 0L) + 500L
            if (anchor < runtime.lastPos - 80L || anchor > maxExpectedPos) {
                runtime.hadActive = false
            } else {
                val exitPos = runtime.exitPos
                val started = exitPos > bst.lineStartMs
                val needsFinish = started && exitPos < bst.lineEndMs
                val finishMs = if (needsFinish) FINISH_MS else 0f
                val fxRemainingMs = if (started) max(0f, bst.fxEndMs - exitPos.toFloat()) else 0f
                val holdMs = max(finishMs, fxRemainingMs)
                fadeOpacity = braccatoExitOpacity(elapsed, finishMs)
                if (elapsed >= max(holdMs, finishMs + FADE_OUT_MS)) {
                    runtime.hadActive = false
                } else if (elapsed < holdMs) {
                    mode = 1
                    fxPos = exitPos + elapsed.toLong()
                    pos = if (elapsed < finishMs) {
                        val t = LinearOutSlowInEasing.transform(elapsed / finishMs)
                        exitPos + ((bst.lineEndMs - exitPos) * t).toLong()
                    } else {
                        max(exitPos, bst.lineEndMs)
                    }
                } else {
                    mode = 2
                    pos = if (needsFinish) bst.lineEndMs else exitPos
                }
            }
        }

        val fontSizePx = with(density) { fontSize.toPx() }
        val glowRadiusPx = with(density) { (GLOW_RADIUS_REM * 16f).dp.toPx() }
        // Room for the lifted wave and the glow above the first row.
        val topPad = fontSizePx * 0.25f
        val heightDp = with(density) { (layout.totalHeight + topPad * 2f).toDp() }
        val drawMode = mode
        val drawPos = pos
        val drawFxPos = fxPos
        val drawFade = fadeOpacity

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(heightDp)
        ) {
            val litColor = accent
            val litTransparent = litColor.copy(alpha = 0f)
            val unlitColor = accent.copy(alpha = 0.38f)
            val baseColor = if (drawMode == 0) textColor else unlitColor
            val n = layout.clusters.size

            for (pass in 0..2) {
                if (pass > 0 && drawMode == 0) continue

                for (i in 0 until n) {
                    val kind = bst.kind[i]
                    if (kind == KIND_NONE) continue
                    val vc = layout.clusters[i]
                    val textLayout = layout.layouts[i]
                    val w = layout.widths[i]
                    val h = textLayout.size.height.toFloat()
                    val letters = bst.letterCount[i]
                    val edgeLen = SWIPE_EDGE * letters

                    var litOpacity = 0f
                    var e0 = 0f
                    var glowStrength = 0f
                    var glowR = 0f
                    var waveTy = 0f
                    var waveScale = 1f
                    var wobbleTx = 0f
                    var wobbleSx = 1f
                    var wobbleTy = 0f

                    if (drawMode == 1) {
                        val e = drawPos - bst.startMs[i]
                        litOpacity = when {
                            e < 0f -> 0f
                            bst.lineSynced[i] && active -> BraccatoEase.transform((e / FADE_IN_MS).coerceIn(0f, 1f))
                            else -> 1f
                        } * drawFade
                        e0 = if (bst.lineSynced[i]) (letters + 2) / 2f else braccatoEdge(bst, i, e)

                        val ef = drawFxPos - bst.startMs[i]
                        if (bst.longWord[i] && ef >= 0f && ef < bst.glowDur[i]) {
                            val gp = BraccatoEase.transform(ef / bst.glowDur[i])
                            glowR = glowRadiusPx * (1f - gp)
                            glowStrength = 1f
                        }

                        if (kind == KIND_LETTER) {
                            val local = ef - bst.waveDelay[i]
                            if (local > 0f) {
                                val p = local / bst.waveDur[i]
                                val peakScale = if (bst.emphasise[i]) WAVE_EMPHASIS_SCALE else 1f
                                if (p >= 1f) {
                                    waveTy = WAVE_SETTLE_EM * fontSizePx
                                } else if (p < WAVE_CREST) {
                                    val v = BraccatoEaseInOut.transform(p / WAVE_CREST)
                                    waveTy = WAVE_PEAK_EM * fontSizePx * v
                                    waveScale = 1f + (peakScale - 1f) * v
                                } else {
                                    val v = BraccatoEaseOut.transform((p - WAVE_CREST) / (1f - WAVE_CREST))
                                    waveTy = (WAVE_PEAK_EM + (WAVE_SETTLE_EM - WAVE_PEAK_EM) * v) * fontSizePx
                                    waveScale = peakScale + (1f - peakScale) * v
                                }
                            }
                        }

                        if (bst.groupWobble[i] && WOBBLE_MODE != 2) {
                            val eg = drawFxPos - bst.groupStartMs[i]
                            if (eg > 0f) {
                                val p = BraccatoEase.transform((eg / WOBBLE_MS).coerceIn(0f, 1f))
                                if (WOBBLE_MODE == 0) {
                                    val em = if (p < WOBBLE_PEAK_OFFSET) {
                                        -0.005f * (p / WOBBLE_PEAK_OFFSET)
                                    } else if (p < WOBBLE_SETTLE_OFFSET) {
                                        val v = BraccatoEaseInOut.transform(
                                            (p - WOBBLE_PEAK_OFFSET) / (WOBBLE_SETTLE_OFFSET - WOBBLE_PEAK_OFFSET)
                                        )
                                        -0.005f + (-0.028f + 0.005f) * v
                                    } else {
                                        val u = (p - WOBBLE_SETTLE_OFFSET) / (1f - WOBBLE_SETTLE_OFFSET)
                                        -0.028f + (-0.0375f + 0.028f) * u
                                    }
                                    wobbleTy = em * fontSizePx
                                } else if (eg < WOBBLE_MS) {
                                    if (p < WOBBLE_PEAK_OFFSET) {
                                        val u = p / WOBBLE_PEAK_OFFSET
                                        wobbleTx = WOBBLE_TRANSLATE_EM * fontSizePx * u
                                        wobbleSx = 1f + (WOBBLE_SCALE_X - 1f) * u
                                    } else if (p < WOBBLE_SETTLE_OFFSET) {
                                        val v = BraccatoEaseInOut.transform(
                                            (p - WOBBLE_PEAK_OFFSET) / (WOBBLE_SETTLE_OFFSET - WOBBLE_PEAK_OFFSET)
                                        )
                                        wobbleTx = WOBBLE_TRANSLATE_EM * fontSizePx * (1f - v)
                                        wobbleSx = WOBBLE_SCALE_X + (1f - WOBBLE_SCALE_X) * v
                                    }
                                }
                            }
                        }
                    } else if (drawMode == 2 && drawPos >= bst.startMs[i]) {
                        litOpacity = drawFade
                        e0 = (letters + 2) / 2f
                    }

                    if (pass > 0 && litOpacity <= 0.001f) continue
                    val cov = if (pass == 1) braccatoCoverage(e0, edgeLen) else 0f
                    if (pass == 1) {
                        val eligible = glowStrength > 0f && glowR > 0.05f && cov > 0f
                        if (!eligible) continue
                    }
                    if (pass == 2 && e0 + edgeLen <= 0f) continue

                    val posX = layout.xs[i]
                    val posY = layout.ys[i] + topPad

                    withTransform({
                        if (wobbleTy != 0f) translate(left = 0f, top = wobbleTy)
                        if (wobbleSx != 1f || wobbleTx != 0f) {
                            translate(left = wobbleTx, top = 0f)
                            scale(wobbleSx, 1f, Offset(layout.groupCx[i], 0f))
                        }
                        translate(left = posX, top = posY)
                        if (waveTy != 0f || waveScale != 1f) {
                            translate(left = 0f, top = waveTy)
                            if (waveScale != 1f) scale(waveScale, waveScale, Offset(w / 2f, h / 2f))
                        }
                    }) {
                        if (pass == 0) {
                            drawText(
                                textLayoutResult = textLayout,
                                color = baseColor
                            )
                        } else if (pass == 1) {
                            drawText(
                                textLayoutResult = textLayout,
                                brush = GlowFillBrush,
                                shadow = Shadow(
                                    color = accent.copy(alpha = (cov * GLOW_COLOR_ALPHA * litOpacity).coerceIn(0f, 1f)),
                                    offset = Offset.Zero,
                                    blurRadius = glowR * GLOW_BLUR_SCALE
                                )
                            )
                        } else {
                            val x0 = e0 * w
                            val x1 = (e0 + edgeLen) * w
                            val brush = Brush.horizontalGradient(
                                listOf(litColor, litTransparent),
                                startX = x0,
                                endX = max(x1, x0 + 0.01f)
                            )
                            val fullyLit = e0 >= 1f
                            drawText(
                                textLayoutResult = textLayout,
                                brush = if (fullyLit) SolidColor(litColor) else brush,
                                alpha = litOpacity
                            )
                        }
                    }
                }
            }
        }
    }
}
