package com.lrcstudio.app.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class LrcLine(
    val timestamp: Long = 0L,
    val text: String = "",
    val index: Int = 0
) {
    val timestampFormatted: String
        get() {
            if (timestamp == 0L) return "-:--:--"
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
            val (minStr, secStr, fracStr, text) = match.destructured
            val minutes = minStr.toIntOrNull() ?: return null
            val seconds = secStr.toIntOrNull() ?: return null
            val frac = fracStr.toIntOrNull() ?: return null
            val millis = when (fracStr.length) {
                3 -> minutes * 60000L + seconds * 1000L + frac
                2 -> minutes * 60000L + seconds * 1000L + frac * 10
                else -> return null
            }
            return LrcLine(timestamp = millis, text = text.trim(), index = index)
        }
    }
}
