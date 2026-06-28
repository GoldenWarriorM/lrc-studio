package com.lrcstudio.app.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class LrcLine(
    val timestamp: Long = 0L,
    val text: String = "",
    val index: Int = 0,
    val words: List<WordTimestamp> = emptyList()
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
            val totalMs = timestamp
            val minutes = (totalMs / 60000).toInt()
            val seconds = ((totalMs % 60000) / 1000).toInt()
            val hundredths = ((totalMs % 1000) / 10).toInt()
            val minStr = minutes.toString().padStart(2, '0')
            val secStr = seconds.toString().padStart(2, '0')
            val fracStr = hundredths.toString().padStart(2, '0')
            return "$minStr:$secStr.$fracStr"
        }

    companion object {
        fun fromLrcString(line: String, index: Int = 0): LrcLine? {
            val regex = Regex("""\[(\d{2}):(\d{2})[\.:](\d{2,3})](.*)""")
            val match = regex.find(line.trim()) ?: return null
            val (minStr, secStr, fracStr, rawText) = match.destructured
            val minutes = minStr.toIntOrNull() ?: return null
            val seconds = secStr.toIntOrNull() ?: return null
            val frac = fracStr.toIntOrNull() ?: return null
            val millis = when (fracStr.length) {
                3 -> minutes * 60000L + seconds * 1000L + frac
                2 -> minutes * 60000L + seconds * 1000L + frac * 10
                else -> return null
            }

            val text = rawText.trim()
            val (cleanText, words) = parseWordTimestamps(text)
            return LrcLine(timestamp = millis, text = cleanText, index = index, words = words)
        }

        private fun parseWordTimestamps(text: String): Pair<String, List<WordTimestamp>> {
            val wordRegex = Regex("""<(\d{2}):(\d{2})[\.:](\d{2,3})>([^<]*)""")
            val matches = wordRegex.findAll(text).toList()
            if (matches.isEmpty()) return text to emptyList()

            val words = mutableListOf<WordTimestamp>()
            val cleanParts = mutableListOf<String>()
            var lastEnd = 0

            for (match in matches) {
                val (minStr, secStr, fracStr, wordText) = match.destructured
                val minutes = minStr.toIntOrNull() ?: continue
                val seconds = secStr.toIntOrNull() ?: continue
                val frac = fracStr.toIntOrNull() ?: continue
                val wordMillis = when (fracStr.length) {
                    3 -> minutes * 60000L + seconds * 1000L + frac
                    2 -> minutes * 60000L + seconds * 1000L + frac * 10
                    else -> continue
                }

                if (match.range.first > lastEnd) {
                    cleanParts.add(text.substring(lastEnd, match.range.first).trim())
                }

                words.add(WordTimestamp(startTime = wordMillis, text = wordText.trim()))
                cleanParts.add(wordText.trim())
                lastEnd = match.range.last + 1
            }

            if (lastEnd < text.length) {
                cleanParts.add(text.substring(lastEnd).trim())
            }

            return cleanParts.joinToString(" ").trim() to words
        }
    }
}
