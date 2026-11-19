package com.lrcstudio.app.data.parser

import com.lrcstudio.app.data.model.LrcLine

/**
 * Mirrors the rules enhanced-LRC / richsync validators apply, so problems
 * surface in the editor instead of as a rejected upload.
 */
object RichsyncValidator {

    data class Issue(val lineIndex: Int, val message: String)

    fun validate(lyrics: List<LrcLine>): List<Issue> {
        val issues = mutableListOf<Issue>()

        lyrics.forEachIndexed { index, line ->
            val number = index + 1
            val words = line.words.filter { it.text.isNotBlank() }

            var previousStart = line.timestamp
            words.forEach { word ->
                if (word.startTime <= 0L) return@forEach
                if (line.timestamp > 0L && word.startTime < line.timestamp) {
                    issues += Issue(
                        index,
                        "Line $number: \"${word.text}\" starts before the line " +
                            "(${stamp(word.startTime)} < ${stamp(line.timestamp)})"
                    )
                }
                if (word.startTime < previousStart) {
                    issues += Issue(index, "Line $number: \"${word.text}\" goes back in time")
                }
                if (word.endTime > 0L && word.endTime <= word.startTime) {
                    issues += Issue(index, "Line $number: \"${word.text}\" ends before it starts")
                }
                previousStart = maxOf(previousStart, word.startTime)
            }
        }

        return issues
    }

    private fun stamp(ms: Long): String = LrcLine.formatLrcTimestamp(ms)
}
