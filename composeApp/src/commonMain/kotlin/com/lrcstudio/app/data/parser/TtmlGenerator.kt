package com.lrcstudio.app.data.parser

import com.lrcstudio.app.data.model.LrcLine
import com.lrcstudio.app.data.model.VocalRole
import com.lrcstudio.app.data.model.WordTimestamp
import com.lrcstudio.app.domain.usecase.Punctuation

/**
 * Exports lyrics as TTML, the format Unison (better-lyrics/unison) stores as
 * `syncType = richsync`. Output follows the Apple Music lyric TTML shape:
 *
 * ```
 * <tt ... itunes:timing="Word"><head><metadata>...
 * <body dur="1:24.000"><div begin="12.000" end="27.600">
 * <p begin="12.000" end="15.600" itunes:key="L1" ttm:agent="v1"><span begin="12.000" end="12.600">Amazing</span> <span ...>grace!</span></p>
 * ```
 *
 * Two rules matter for the server-side validator:
 *  - spans are separated by a plain space on a single line (a newline between
 *    spans, or padding inside a span, is rejected as "pretty printed" TTML);
 *  - every timed word must have `end > begin`, otherwise the file is treated
 *    as degenerate richsync.
 */
object TtmlGenerator {

    private const val NS_TT = "http://www.w3.org/ns/ttml"
    private const val NS_ITUNES = "http://music.apple.com/lyric-ttml-internal"
    private const val NS_TTM = "http://www.w3.org/ns/ttml#metadata"

    /** Smallest allowed span duration, so timings never collapse to zero. */
    private const val MIN_SPAN_MS = 50L

    /** Cap for the last word of a line when the next line starts much later. */
    private const val MAX_LAST_SPAN_MS = 4000L

    /** Duration used for a line-level span when no next line exists. */
    private const val DEFAULT_LINE_MS = 4000L

    private data class Span(
        val begin: Long?,
        val end: Long?,
        val text: String,
        val background: Boolean = false,
        val joinsPrevious: Boolean = false
    )

    private data class Paragraph(
        val begin: Long,
        val end: Long,
        val spans: List<Span>,
        val agent: String,
        val background: Boolean = false
    )

    fun generate(
        lyrics: List<LrcLine>,
        language: String = "",
        songwriters: List<String> = emptyList()
    ): String {
        val timed = lyrics
            .filter { it.timestamp > 0L && (it.text.isNotBlank() || it.words.isNotEmpty()) }
            .sortedBy { it.timestamp }

        val paragraphs = timed.mapIndexed { index, line ->
            buildParagraph(line, timed.getOrNull(index + 1)?.timestamp)
        }

        val hasWords = timed.any { isWordTimed(it) }

        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<tt xmlns=\"$NS_TT\" xmlns:itunes=\"$NS_ITUNES\" xmlns:ttm=\"$NS_TTM\"")
        sb.append(" itunes:timing=\"${if (hasWords) "Word" else "Line"}\"")
        if (language.isNotBlank()) sb.append(" xml:lang=\"${escape(language)}\"")
        sb.append(">")

        sb.append("<head><metadata>")
        for (agent in timed.map { agentIdOf(it) }.distinct()) {
            val type = when (agent) {
                "v1000" -> "group"
                "v2000" -> "other"
                else -> "person"
            }
            sb.append("<ttm:agent type=\"$type\" xml:id=\"$agent\"/>")
        }
        val writers = songwriters.map { it.trim() }.filter { it.isNotEmpty() }
        if (writers.isNotEmpty()) {
            sb.append("<iTunesMetadata xmlns=\"$NS_ITUNES\"><songwriters>")
            for (writer in writers) sb.append("<songwriter>${escape(writer)}</songwriter>")
            sb.append("</songwriters></iTunesMetadata>")
        }
        sb.append("</metadata></head>")

        val bodyEnd = paragraphs.maxOfOrNull { it.end } ?: 0L
        sb.append("<body dur=\"${formatTime(bodyEnd)}\">")

        if (paragraphs.isNotEmpty()) {
            val divBegin = paragraphs.first().begin
            val divEnd = paragraphs.maxOf { it.end }
            sb.append("<div begin=\"${formatTime(divBegin)}\" end=\"${formatTime(divEnd)}\">")
            paragraphs.forEachIndexed { index, paragraph ->
                sb.append("<p begin=\"${formatTime(paragraph.begin)}\"")
                sb.append(" end=\"${formatTime(paragraph.end)}\"")
                sb.append(" itunes:key=\"L${index + 1}\"")
                sb.append(" ttm:agent=\"${paragraph.agent}\"")
                if (paragraph.background) sb.append(" ttm:role=\"x-bg\"")
                sb.append(">")
                paragraph.spans.forEachIndexed { spanIndex, span ->
                    // Syllable pieces stay glued to the previous span.
                    if (spanIndex > 0 && !span.joinsPrevious) sb.append(' ')
                    sb.append("<span")
                    if (span.begin != null && span.end != null) {
                        sb.append(" begin=\"${formatTime(span.begin)}\"")
                        sb.append(" end=\"${formatTime(span.end)}\"")
                    }
                    if (span.background) sb.append(" ttm:role=\"x-bg\"")
                    sb.append(">")
                    sb.append(escape(span.text))
                    sb.append("</span>")
                }
                sb.append("</p>")
            }
            sb.append("</div>")
        }
        sb.append("</body></tt>")

        return sb.toString()
    }

    /** Each line carries its own vocal agent; unassigned lines are the lead voice. */
    private fun agentIdOf(line: LrcLine): String =
        if (line.agent > 0) "v${line.agent}" else "v1"

    private fun isWordTimed(line: LrcLine): Boolean {
        // Untimed punctuation does not make the line line-synced: it is folded
        // into the word before it below.
        val words = line.words.filter {
            it.text.isNotBlank() && (it.startTime > 0L || !Punctuation.isStandalone(it.text))
        }
        return words.isNotEmpty() && words.all { it.startTime > 0L }
    }

    /**
     * Standalone punctuation has no timing of its own, so it is appended to the
     * word in front of it and inherits that word's span.
     */
    private fun sungWords(line: LrcLine): List<WordTimestamp> {
        val words = mutableListOf<WordTimestamp>()
        for (word in line.words.filter { it.text.isNotBlank() }) {
            val last = words.lastOrNull()
            if (last != null && word.startTime <= 0L && Punctuation.isStandalone(word.text)) {
                words[words.lastIndex] = last.copy(text = last.text + word.text)
            } else {
                words.add(word)
            }
        }
        return words
    }

    /**
     * Word-timed lines become one span per word (the app's word-sync mode);
     * everything else becomes a single line-level span, which Unison accepts
     * as linesync TTML instead of pretending to be word-synced.
     */
    private fun buildParagraph(line: LrcLine, nextStart: Long?): Paragraph {
        val begin = line.timestamp
        val words = sungWords(line)
        val agent = agentIdOf(line)

        if (!isWordTimed(line)) {
            // Untimed span: Unison reads a timed span as a word and would label
            // a line-only file as richsync, so the timing stays on <p> here.
            val end = lineLevelEnd(begin, nextStart)
            val text = line.text.ifBlank { words.joinToString(" ") { it.text } }
            return Paragraph(
                begin = begin,
                end = end,
                spans = listOf(Span(null, null, text, line.isBackground)),
                agent = agent,
                background = line.isBackground
            )
        }

        val starts = LongArray(words.size)
        var cursor = begin
        for (i in words.indices) {
            starts[i] = maxOf(words[i].startTime, cursor)
            cursor = starts[i] + MIN_SPAN_MS
        }

        val spans = words.mapIndexed { i, word ->
            val spanBegin = starts[i]
            val naturalEnd = when {
                i < words.lastIndex -> starts[i + 1]
                nextStart != null && nextStart > spanBegin ->
                    minOf(nextStart, spanBegin + MAX_LAST_SPAN_MS)
                else -> spanBegin + MIN_SPAN_MS
            }
            // An explicit end (set with the End half of the capture button) wins,
            // but never overlaps the next word, so spans stay valid.
            val explicit = if (word.endTime > spanBegin) word.endTime else 0L
            val spanEnd = if (explicit > 0L) {
                explicit.coerceAtMost(maxOf(naturalEnd, spanBegin + MIN_SPAN_MS))
            } else {
                naturalEnd
            }.coerceAtLeast(spanBegin + 1)
            Span(
                begin = spanBegin,
                end = spanEnd,
                text = word.text.trim(),
                // TTML only knows background-vocal spans; harmony rides along as
                // background, a chorus is sung by everyone and stays main.
                background = line.isBackground ||
                    word.role == VocalRole.BACKGROUND ||
                    word.role == VocalRole.HARMONY,
                joinsPrevious = word.joinsPrevious
            )
        }

        val end = spans.last().end ?: (begin + MIN_SPAN_MS)
        return Paragraph(
            begin = begin,
            end = maxOf(end, begin + MIN_SPAN_MS),
            spans = spans,
            agent = agent,
            background = line.isBackground
        )
    }

    private fun lineLevelEnd(begin: Long, nextStart: Long?): Long =
        if (nextStart != null && nextStart > begin) nextStart else begin + DEFAULT_LINE_MS

    private fun formatTime(ms: Long): String {
        val total = if (ms > 0L) ms else 0L
        val minutes = total / 60_000L
        val seconds = (total % 60_000L) / 1000L
        val millis = total % 1000L
        return "$minutes:${seconds.toString().padStart(2, '0')}.${millis.toString().padStart(3, '0')}"
    }

    private fun escape(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            when {
                // Invalid in XML 1.0 and rejected by the server validator.
                ch.code < 0x20 && ch != '\t' && ch != '\n' && ch != '\r' -> sb.append(' ')
                ch == '&' -> sb.append("&amp;")
                ch == '<' -> sb.append("&lt;")
                ch == '>' -> sb.append("&gt;")
                ch == '"' -> sb.append("&quot;")
                ch == '\'' -> sb.append("&apos;")
                ch == '\n' || ch == '\r' || ch == '\t' -> sb.append(' ')
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}
