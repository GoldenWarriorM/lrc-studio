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
            val ts = line.timestampFormatted
            sb.appendLine("[$ts]${line.text}")
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
            val ts = line.timestampFormatted
            sb.appendLine("[$ts]${line.text}")
        }
        return sb.toString().trimEnd()
    }
}
