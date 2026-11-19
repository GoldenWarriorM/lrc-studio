package com.lrcstudio.app.data.parser

import com.lrcstudio.app.data.model.LrcLine
import com.lrcstudio.app.data.model.VocalRole

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
        for (line in lyrics.sortedBy { it.timestamp }) {
            if (line.timestamp == 0L) continue
            sb.appendLine(formatLine(line))
        }
        return sb.toString().trimEnd()
    }

    /** Plain text export: no tags at all, just the readable lyrics. */
    fun generatePlain(
        lyrics: List<LrcLine>,
    ): String {
        val sb = StringBuilder()
        for (line in lyrics.sortedBy { it.timestamp }) {
            if (line.timestamp == 0L) continue
            sb.appendLine("[${line.timestampLrcFormatted}]${line.text}")
        }
        return sb.toString().trimEnd()
    }

    private fun formatLine(line: LrcLine): String {
        if (line.isBackground) return "[bg:${formatBody(line, allowFragments = false)}]"
        val agentPrefix = if (line.agent > 0) "v${line.agent}:" else ""
        val stamp = "[${line.timestampLrcFormatted}]"
        return "$stamp$agentPrefix${formatBody(line, allowFragments = true)}"
    }

    private fun formatBody(line: LrcLine, allowFragments: Boolean): String {
        val words = line.words.filter { it.text.isNotBlank() }
        if (words.isEmpty()) return line.text

        val sb = StringBuilder()
        var currentRole: VocalRole? = null
        for (word in words) {
            val role = if (allowFragments) word.role else VocalRole.MAIN
            if (role != currentRole) {
                if (currentRole != null && currentRole != VocalRole.MAIN) sb.append(']')
                if (role != VocalRole.MAIN) {
                    sb.append(if (sb.isEmpty()) "[" else " [")
                        .append(LrcLine.roleKeyword(role)).append(':')
                }
                currentRole = role
            }
            val last = sb.lastOrNull()
            val needsSpace = !word.joinsPrevious && sb.isNotEmpty() &&
                last != ':' && last != ' ' && last != '['
            if (needsSpace) sb.append(' ')
            if (word.startTime > 0L) {
                sb.append('<').append(LrcLine.formatLrcTimestamp(word.startTime)).append('>')
            }
            sb.append(word.text)
            // A trailing empty tag closes this word (keeps pauses between words).
            if (word.endTime > word.startTime) {
                sb.append(" <").append(LrcLine.formatLrcTimestamp(word.endTime)).append('>')
            }
        }
        if (currentRole != null && currentRole != VocalRole.MAIN) sb.append(']')
        return sb.toString().trimStart()
    }
}
