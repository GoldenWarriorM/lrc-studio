package com.lrcstudio.app.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class LrcLine(
    val timestamp: Long = 0L,
    val text: String = "",
    val index: Int = 0,
    val words: List<WordTimestamp> = emptyList(),
    val uid: Int = -1,
    /** Vocal agent tag of this line: 1 = v1, 2 = v2, 1000 = group, 0 = no tag. */
    val agent: Int = 0,
    /** Whole line is a background vocal (`[bg: ...]` in enhanced LRC). */
    val isBackground: Boolean = false
) {
    val timestampFormatted: String
        get() {
            if (timestamp == 0L) return "-:--:--"
            val totalMs = timestamp
            val minutes = (totalMs / 60000).toInt()
            val seconds = ((totalMs % 60000) / 1000).toInt()
            val hundredths = ((totalMs % 1000) / 10).toInt()
            val minStr = minutes.toString()
            val secStr = seconds.toString().padStart(2, '0')
            val fracStr = hundredths.toString().padStart(2, '0')
            return "$minStr:$secStr:$fracStr"
        }

    val timestampLrcFormatted: String
        get() {
            if (timestamp == 0L) return "00:00.00"
            return formatLrcTimestamp(timestamp)
        }

    companion object {
        private val LINE_STAMP = Regex("""\[(\d{1,3}):(\d{2})[.:](\d{2,3})]""")
        private val WORD_TAG = Regex("""<(\d{1,3}):(\d{2})[.:](\d{2,3})>""")
        private val SENTINEL = Regex("""[\u0001](\d+)[\u0002]""")
        private val AGENT_PREFIX = Regex("""^v(\d{1,4}):""")
        private val ROLE_PREFIX = Regex("""^(bg|h1|ch):""")
        private val INLINE_BLOCK = Regex("""\[(bg|h1|ch):""")
        private val BG_WHOLE_LINE = Regex("""^\[bg:(.*)]$""", RegexOption.DOT_MATCHES_ALL)

        /** `MM:ss.hh` — the shape used by line stamps and word tags. */
        fun formatLrcTimestamp(ms: Long): String {
            val total = if (ms > 0L) ms else 0L
            val minutes = (total / 60000).toInt()
            val seconds = ((total % 60000) / 1000).toInt()
            val hundredths = ((total % 1000) / 10).toInt()
            return minutes.toString().padStart(2, '0') +
                ":" + seconds.toString().padStart(2, '0') +
                "." + hundredths.toString().padStart(2, '0')
        }

        /** Role keyword used in enhanced LRC for a fragment kind. */
        fun roleKeyword(role: VocalRole): String = when (role) {
            VocalRole.BACKGROUND -> "bg"
            VocalRole.HARMONY -> "h1"
            VocalRole.CHORUS -> "ch"
            VocalRole.MAIN -> ""
        }

        fun roleFromKeyword(keyword: String): VocalRole = when (keyword) {
            "bg" -> VocalRole.BACKGROUND
            "h1" -> VocalRole.HARMONY
            "ch" -> VocalRole.CHORUS
            else -> VocalRole.MAIN
        }

        fun fromLrcString(line: String, index: Int = 0): LrcLine? {
            val trimmedLine = line.trim()
            val stamp = LINE_STAMP.matchAt(trimmedLine, 0)
            var rest = if (stamp != null) trimmedLine.substring(stamp.range.last + 1) else trimmedLine
            val timestamp = stamp?.let { stampToMs(it) }

            var body = rest.trim()
            var isBackground = false

            // A whole background-vocal line: [bg:<00:12.00>ooh <00:13.00>]
            val wholeBg = BG_WHOLE_LINE.matchEntire(body)
            if (wholeBg != null) {
                isBackground = true
                body = wholeBg.groupValues[1].trim()
            }
            // Enhanced LRC puts the singer ("v1:"/"v2:"/"v1000:") and an optional
            // fragment kind ("bg:"/"h1:"/"ch:") before the word tags, in any order.
            var agent = 0
            var lineRole = VocalRole.MAIN
            repeat(2) {
                val rolePrefix = ROLE_PREFIX.find(body)
                if (rolePrefix != null) {
                    lineRole = roleFromKeyword(rolePrefix.groupValues[1])
                    body = body.removeRange(rolePrefix.range).trim()
                    return@repeat
                }
                val agentMatch = AGENT_PREFIX.find(body)
                if (agentMatch != null) {
                    agent = agentMatch.groupValues[1].toIntOrNull() ?: 0
                    body = body.removeRange(agentMatch.range).trim()
                }
            }
            if (lineRole == VocalRole.BACKGROUND) isBackground = true

            if (timestamp == null && !isBackground) return null
            if (body.isEmpty() && timestamp == null) return null

            val (parsedWords, text) = parseBody(body)
            val words = when {
                isBackground -> parsedWords.map {
                    if (it.role == VocalRole.MAIN) it.copy(role = VocalRole.BACKGROUND) else it
                }
                lineRole != VocalRole.MAIN -> parsedWords.map {
                    if (it.role == VocalRole.MAIN) it.copy(role = lineRole) else it
                }
                else -> parsedWords
            }
            val lineTimestamp = timestamp
                ?: words.firstOrNull { it.startTime > 0L }?.startTime
                ?: 0L

            return LrcLine(
                timestamp = lineTimestamp,
                text = text,
                index = index,
                words = words,
                agent = agent,
                isBackground = isBackground
            )
        }

        private fun stampToMs(match: MatchResult): Long? {
            val minutes = match.groupValues[1].toIntOrNull() ?: return null
            val seconds = match.groupValues[2].toIntOrNull() ?: return null
            val fracStr = match.groupValues[3]
            val frac = fracStr.toIntOrNull() ?: return null
            return when (fracStr.length) {
                1 -> minutes * 60000L + seconds * 1000L + frac * 100
                2 -> minutes * 60000L + seconds * 1000L + frac * 10
                3 -> minutes * 60000L + seconds * 1000L + frac
                else -> null
            }
        }

        /**
         * Splits a line body into words, honouring inline `[bg: ...]`, `[h1: ...]`
         * and `[ch: ...]` fragments and trailing empty tags that close the word
         * before them.
         */
        private fun parseBody(raw: String): Pair<List<WordTimestamp>, String> {
            val body = raw.trim()
            if (body.isEmpty()) return emptyList<WordTimestamp>() to ""

            // Lift inline fragments out so their words can be spliced back in place.
            val blocks = mutableListOf<Pair<String, VocalRole>>()
            val flatBuilder = StringBuilder()
            var cursor = 0
            while (cursor < body.length) {
                val open = INLINE_BLOCK.find(body, cursor)
                if (open == null) {
                    flatBuilder.append(body, cursor, body.length)
                    break
                }
                val close = body.indexOf(']', open.range.first)
                if (close < 0) {
                    flatBuilder.append(body, cursor, body.length)
                    break
                }
                flatBuilder.append(body, cursor, open.range.first)
                val content = body.substring(open.range.last + 1, close)
                blocks.add(content to roleFromKeyword(open.groupValues[1]))
                flatBuilder.append('\u0001').append(blocks.lastIndex).append('\u0002')
                cursor = close + 1
            }
            val flat = flatBuilder.toString()

            if (!WORD_TAG.containsMatchIn(flat) && blocks.isEmpty()) {
                return emptyList<WordTimestamp>() to flat.trim()
            }

            val words = mutableListOf<WordTimestamp>()
            var pendingTime: Long? = null
            // A word tag glued to the previous text (no space) starts a syllable.
            var nextJoinsPrevious = false
            var index = 0
            while (index <= flat.length) {
                val tag = WORD_TAG.find(flat, index)
                val textEnd = tag?.range?.first ?: flat.length
                if (textEnd > index) {
                    val fragment = flat.substring(index, textEnd)
                    val added = addFragment(fragment, pendingTime, blocks, words, nextJoinsPrevious)
                    if (added) pendingTime = null
                    nextJoinsPrevious = fragment.isNotEmpty() &&
                        !fragment.last().isWhitespace() &&
                        words.isNotEmpty()
                }
                if (tag == null) break
                val tagTime = stampToMs(tag)
                if (tagTime != null) {
                    // A tag with no text after it closes the word before it.
                    val pending = pendingTime
                    if (pending != null && words.isNotEmpty()) {
                        words[words.lastIndex] = words.last().copy(endTime = pending)
                    }
                    pendingTime = tagTime
                }
                index = tag.range.last + 1
            }
            val trailing = pendingTime
            if (trailing != null && words.isNotEmpty()) {
                words[words.lastIndex] = words.last().copy(endTime = trailing)
            }

            val text = if (words.isEmpty()) {
                flat.trim()
            } else {
                buildString {
                    words.forEachIndexed { i, word ->
                        if (i > 0 && !word.joinsPrevious) append(' ')
                        append(word.text)
                    }
                }
            }
            return words to text
        }

        /** Appends the plain text and the inline fragments of one fragment, in order. */
        private fun addFragment(
            fragment: String,
            time: Long?,
            blocks: List<Pair<String, VocalRole>>,
            out: MutableList<WordTimestamp>,
            joinsPrevious: Boolean
        ): Boolean {
            var added = false
            var cursor = 0
            var start = time
            var pendingJoin = joinsPrevious

            fun push(word: WordTimestamp) {
                out.add(if (pendingJoin) word.copy(joinsPrevious = true) else word)
                pendingJoin = false
            }
            for (match in SENTINEL.findAll(fragment)) {
                val piece = fragment.substring(cursor, match.range.first).trim()
                if (piece.isNotEmpty()) {
                    push(WordTimestamp(startTime = start ?: 0L, text = piece))
                    added = true
                    start = null
                }
                val blockIndex = match.groupValues[1].toIntOrNull()
                val block = blockIndex?.let { blocks.getOrNull(it) }
                if (block != null) {
                    val (blockWords, blockText) = parseBody(block.first)
                    if (blockWords.isEmpty()) {
                        if (blockText.isNotBlank()) {
                            push(
                                WordTimestamp(
                                    startTime = start ?: 0L,
                                    text = blockText,
                                    role = block.second
                                )
                            )
                            added = true
                            start = null
                        }
                    } else {
                        blockWords.forEach { push(it.copy(role = block.second)) }
                        added = true
                        start = null
                    }
                }
                cursor = match.range.last + 1
            }
            val tail = fragment.substring(cursor).trim()
            if (tail.isNotEmpty()) {
                push(WordTimestamp(startTime = start ?: 0L, text = tail))
                added = true
            }
            return added
        }
    }
}
