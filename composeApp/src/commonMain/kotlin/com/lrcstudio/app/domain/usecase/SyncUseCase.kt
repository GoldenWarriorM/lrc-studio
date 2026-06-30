package com.lrcstudio.app.domain.usecase

import com.lrcstudio.app.data.model.LrcLine
import com.lrcstudio.app.data.model.WordTimestamp

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

    fun getCurrentWordIndex(line: LrcLine, positionMs: Long): Int {
        if (line.words.isEmpty() || line.timestamp == 0L) return -1
        val punctRegex = Regex("[.,!?;:\\-–—()\\[\\]{}「」『』《》【】\"'«»…]+")
        for (i in line.words.indices.reversed()) {
            if (line.words[i].startTime > 0L && positionMs >= line.words[i].startTime) return i
        }
        for (i in line.words.indices.reversed()) {
            if (!punctRegex.matches(line.words[i].text)) return i
        }
        return -1
    }

    fun addWordTimestamp(line: LrcLine, positionMs: Long, wordText: String): LrcLine {
        val newWord = WordTimestamp(startTime = positionMs, text = wordText)
        val newWords = line.words + newWord
        val cleanText = newWords.joinToString(" ") { it.text }
        return line.copy(
            words = newWords,
            text = cleanText,
            timestamp = if (line.timestamp == 0L && line.words.isEmpty()) positionMs else line.timestamp
        )
    }

    fun splitLineIntoWords(line: LrcLine, skipPunctuation: Boolean = true): LrcLine {
        if (line.words.isNotEmpty()) return line
        val words = line.text.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return line
        return line.copy(
            words = words.map { WordTimestamp(startTime = 0L, text = it) }
        )
    }

    fun isPunctuationOnly(text: String): Boolean {
        val punctuation = Regex("[.,!?;:\\-–—()\\[\\]{}「」『』《》【】\"'«»…]+")
        return text.isNotBlank() && punctuation.matches(text)
    }

    fun removeWordTimestamp(line: LrcLine, wordIndex: Int): LrcLine {
        if (wordIndex !in line.words.indices) return line
        val newWords = line.words.filterIndexed { i, _ -> i != wordIndex }
        val cleanText = newWords.joinToString(" ") { it.text }
        return line.copy(words = newWords, text = cleanText)
    }

    fun clearAllWordTimestamps(line: LrcLine): LrcLine {
        return line.copy(words = emptyList())
    }

    fun clearWordTimestamp(line: LrcLine, wordIndex: Int): LrcLine {
        if (wordIndex !in line.words.indices) return line
        val newWords = line.words.toMutableList()
        newWords[wordIndex] = newWords[wordIndex].copy(startTime = 0L)
        return line.copy(words = newWords)
    }
}
