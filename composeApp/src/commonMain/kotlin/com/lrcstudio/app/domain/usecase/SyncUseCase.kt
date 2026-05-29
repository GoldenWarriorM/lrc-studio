package com.lrcstudio.app.domain.usecase

import com.lrcstudio.app.data.model.LrcLine

class SyncUseCase {

    fun addTimestamp(lyrics: List<LrcLine>, positionMs: Long, text: String): List<LrcLine> {
        val newLine = LrcLine(
            timestamp = positionMs,
            text = text,
            index = lyrics.size
        )
        return (lyrics + newLine)
            .mapIndexed { i, line -> line.copy(index = i) }
    }

    fun updateTimestamp(lyrics: List<LrcLine>, index: Int, newTimestamp: Long): List<LrcLine> {
        return lyrics.mapIndexed { i, line ->
            if (i == index) line.copy(timestamp = newTimestamp) else line
        }.mapIndexed { i, line -> line.copy(index = i) }
    }

    fun updateText(lyrics: List<LrcLine>, index: Int, newText: String): List<LrcLine> {
        return lyrics.mapIndexed { i, line ->
            if (i == index) line.copy(text = newText) else line
        }
    }

    fun removeLine(lyrics: List<LrcLine>, index: Int): List<LrcLine> {
        return lyrics.filterIndexed { i, _ -> i != index }
            .mapIndexed { i, line -> line.copy(index = i) }
    }

    fun insertLine(lyrics: List<LrcLine>, index: Int, text: String = ""): List<LrcLine> {
        val newLine = LrcLine(timestamp = 0L, text = text, index = index)
        return (lyrics.take(index) + newLine + lyrics.drop(index))
            .mapIndexed { i, line -> line.copy(index = i) }
    }

    fun clearAllTimestamps(lyrics: List<LrcLine>): List<LrcLine> {
        return lyrics.map { it.copy(timestamp = 0L) }
    }

    fun shiftAllTimestamps(lyrics: List<LrcLine>, offsetMs: Long): List<LrcLine> {
        return lyrics.map { line ->
            line.copy(timestamp = (line.timestamp + offsetMs).coerceAtLeast(0))
        }
    }

    fun getCurrentLineIndex(lyrics: List<LrcLine>, positionMs: Long): Int {
        if (lyrics.isEmpty()) return -1
        val sorted = lyrics.filter { it.timestamp > 0L }.sortedBy { it.timestamp }
        if (sorted.isEmpty()) return -1
        for (i in sorted.indices.reversed()) {
            if (positionMs >= sorted[i].timestamp) return sorted[i].index
        }
        return -1
    }
}
