package com.lrcstudio.app.data.parser

import com.lrcstudio.app.data.model.LrcLine

object LrcParser {

    fun parse(content: String): List<LrcLine> {
        val lines = content.lines().filter { it.isNotBlank() }
        val result = mutableListOf<LrcLine>()

        for ((_, line) in lines.withIndex()) {
            val parsed = LrcLine.fromLrcString(line)
            if (parsed != null) {
                result.add(parsed)
            }
        }

        return result.sortedBy { it.timestamp }
            .mapIndexed { i, line -> line.copy(index = i) }
    }

    fun generate(
        lyrics: List<LrcLine>,
        title: String = "",
        artist: String = "",
        album: String = "",
        composer: String = "",
        creator: String = ""
    ): String {
        val sb = StringBuilder()
        if (artist.isNotBlank()) sb.appendLine("[ar: $artist]")
        if (album.isNotBlank()) sb.appendLine("[al: $album]")
        if (title.isNotBlank()) sb.appendLine("[ti: $title]")
        if (composer.isNotBlank()) sb.appendLine("[au: $composer]")
        if (creator.isNotBlank()) sb.appendLine("[by: $creator]")
        sb.appendLine()
        sb.appendLine("[re: LRC Studio - Cross-platform LRC editor]")
        sb.appendLine()
        val sorted = lyrics.sortedBy { it.timestamp }
        for (line in sorted) {
            if (line.timestamp == 0L) continue
            val ts = line.timestampLrcFormatted
            sb.appendLine("[$ts]${formatLineText(line)}")
        }
        return sb.toString().trimEnd()
    }

    fun generatePlain(
        lyrics: List<LrcLine>,
    ): String {
        val sb = StringBuilder()
        val sorted = lyrics.sortedBy { it.timestamp }
        for (line in sorted) {
            if (line.timestamp == 0L) continue
            val ts = line.timestampLrcFormatted
            sb.appendLine("[$ts]${formatLineText(line)}")
        }
        return sb.toString().trimEnd()
    }

    private fun formatLineText(line: LrcLine): String {
        if (line.words.isEmpty()) return line.text
        val sb = StringBuilder()
        for ((i, word) in line.words.withIndex()) {
            if (i == 0 && word.startTime == line.timestamp) {
                sb.append(" <${formatWordTime(word.startTime)}>${word.text}")
            } else {
                sb.append(" <${formatWordTime(word.startTime)}>${word.text}")
            }
        }
        return sb.toString().trimStart()
    }

    private fun formatWordTime(ms: Long): String {
        val minutes = (ms / 60000).toInt()
        val seconds = ((ms % 60000) / 1000).toInt()
        val hundredths = ((ms % 1000) / 10).toInt()
        val minStr = minutes.toString().padStart(2, '0')
        val secStr = seconds.toString().padStart(2, '0')
        val fracStr = hundredths.toString().padStart(2, '0')
        return "$minStr:$secStr.$fracStr"
    }
}
