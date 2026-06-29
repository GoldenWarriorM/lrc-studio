package com.lrcstudio.app.ui.editor

import androidx.compose.runtime.Immutable
import com.lrcstudio.app.data.model.LrcLine
import com.lrcstudio.app.data.model.Song
import com.lrcstudio.app.data.model.WordTimestamp
import com.lrcstudio.app.data.repository.SongRepository
import com.lrcstudio.app.domain.usecase.SyncUseCase
import com.lrcstudio.app.ui.player.AudioPlayer
import com.lrcstudio.app.ui.player.PlaybackState
import com.lrcstudio.app.util.extractAudioMetadata
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs

@Immutable
data class EditorState(
    val song: Song? = null,
    val lyrics: List<LrcLine> = emptyList(),
    val currentLineIndex: Int = -1,
    val currentWordIndex: Int = -1,
    val selectedLineIndex: Int = 0,
    val wordCursorIndex: Int = -1,
    val wordSyncMode: Boolean = false,
    val isRecording: Boolean = false,
    val newLyricText: String = "",
    val editingLineIndex: Int = -1,
    val editingText: String = "",
    val showPlaybackOptions: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false
)

class EditorViewModel(
    private val songRepository: SongRepository,
    private val syncUseCase: SyncUseCase,
    val audioPlayer: AudioPlayer
) {
    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var positionUpdateJob: Job? = null

    private val undoStack = mutableListOf<List<LrcLine>>()
    private val redoStack = mutableListOf<List<LrcLine>>()

    private fun pushUndo() {
        undoStack.add(_state.value.lyrics)
        if (undoStack.size > MAX_UNDO) undoStack.removeFirst()
        redoStack.clear()
        _state.value = _state.value.copy(canUndo = true, canRedo = false)
    }

    fun undo() {
        val current = _state.value.lyrics
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.add(current)
        _state.value = _state.value.copy(lyrics = previous)
        saveLyrics(previous)
        _state.value = _state.value.copy(
            canUndo = undoStack.isNotEmpty(),
            canRedo = true
        )
    }

    fun redo() {
        val current = _state.value.lyrics
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.add(current)
        _state.value = _state.value.copy(lyrics = next)
        saveLyrics(next)
        _state.value = _state.value.copy(
            canUndo = true,
            canRedo = redoStack.isNotEmpty()
        )
    }

    fun loadSong(songId: String) {
        val song = songRepository.getById(songId) ?: return
        undoStack.clear()
        redoStack.clear()
        val lyrics = if (song.wordSyncEnabled) {
            song.lyrics.map { syncUseCase.splitLineIntoWords(it) }
        } else {
            song.lyrics
        }
        val firstLineWithWords = lyrics.indexOfFirst { it.words.isNotEmpty() }
        val firstWordIdx = if (firstLineWithWords >= 0 && song.wordSyncEnabled)
            skipPunctuationForward(lyrics[firstLineWithWords].words, 0).coerceAtLeast(0)
        else -1
        _state.value = _state.value.copy(
            song = song,
            lyrics = lyrics,
            wordSyncMode = song.wordSyncEnabled,
            wordCursorIndex = firstWordIdx,
            selectedLineIndex = firstLineWithWords.coerceAtLeast(0),
            canUndo = false,
            canRedo = false
        )
        if (song.audioPath.isNotEmpty()) {
            val playerState = audioPlayer.state.value
            if (playerState.audioPath != song.audioPath || playerState.state == PlaybackState.IDLE) {
                scope.launch(Dispatchers.Main) {
                    audioPlayer.load(song.audioPath)
                }
            }
        }
        startPositionUpdates()
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = scope.launch {
            while (isActive) {
                delay(100)
                val playerState = audioPlayer.state.value
                val s = _state.value
                val index = syncUseCase.getCurrentLineIndex(
                    s.lyrics,
                    playerState.currentPosition
                )
                var wordIndex = -1
                if (index >= 0) {
                    val line = s.lyrics.getOrNull(index)
                    if (line != null) {
                        wordIndex = syncUseCase.getCurrentWordIndex(line, playerState.currentPosition)
                    }
                }
                if (index != s.currentLineIndex) {
                    val jumpedBack = index < s.currentLineIndex
                    val selIdx = if (jumpedBack) {
                        val selLine = s.lyrics.getOrNull(s.selectedLineIndex)
                        val selTs = selLine?.timestamp ?: 0L
                        if (selTs > 0L && abs(selTs - playerState.currentPosition) <= 500L) {
                            s.selectedLineIndex
                        } else {
                            val firstUntimed = s.lyrics.indexOfFirst { it.timestamp == 0L }
                            if (firstUntimed >= 0) firstUntimed else index.coerceAtLeast(0)
                        }
                    } else {
                        s.selectedLineIndex
                    }
                    _state.value = _state.value.copy(
                        currentLineIndex = index,
                        currentWordIndex = wordIndex,
                        selectedLineIndex = selIdx
                    )
                } else if (wordIndex != s.currentWordIndex) {
                    _state.value = _state.value.copy(currentWordIndex = wordIndex)
                }
            }
        }
    }

    fun selectLine(index: Int) {
        val lyrics = _state.value.lyrics
        if (index !in lyrics.indices) return
        val line = lyrics[index]
        val wordCursor = if (line.words.isNotEmpty()) 0 else -1
        _state.value = _state.value.copy(
            selectedLineIndex = index,
            wordCursorIndex = wordCursor
        )
        val wordSyncMode = _state.value.wordSyncMode
        val ts = line.timestamp
        if (ts > 0L) {
            val hasWordTimestamps = line.words.any { it.startTime > 0L }
            if (!wordSyncMode || hasWordTimestamps) {
                audioPlayer.seekTo(ts)
            }
        }
    }

    fun playPause() {
        val playerState = audioPlayer.state.value
        when (playerState.state) {
            PlaybackState.PLAYING -> audioPlayer.pause()
            PlaybackState.FINISHED -> {
                audioPlayer.seekTo(0)
                audioPlayer.play()
            }
            PlaybackState.PAUSED, PlaybackState.IDLE -> audioPlayer.play()
        }
    }

    fun seekTo(positionMs: Long) {
        audioPlayer.seekTo(positionMs)
    }

    fun toggleRecording() {
        _state.value = _state.value.copy(
            isRecording = !_state.value.isRecording
        )
    }

    fun captureTimestamp() {
        if (!_state.value.isRecording) return
        val text = _state.value.newLyricText
        if (text.isBlank()) return
        pushUndo()
        val position = audioPlayer.state.value.currentPosition
        val newLyrics = syncUseCase.addTimestamp(
            _state.value.lyrics, position, text
        )
        _state.value = _state.value.copy(
            lyrics = newLyrics, newLyricText = ""
        )
        saveLyrics(newLyrics)
    }

    fun addLineAtPosition(text: String) {
        if (text.isBlank()) return
        pushUndo()
        val position = audioPlayer.state.value.currentPosition
        val newLyrics = syncUseCase.addTimestamp(
            _state.value.lyrics, position, text
        )
        _state.value = _state.value.copy(lyrics = newLyrics)
        saveLyrics(newLyrics)
    }

    fun updateNewLyricText(text: String) {
        _state.value = _state.value.copy(newLyricText = text)
    }

    fun startEditing(lineIndex: Int) {
        val lyrics = _state.value.lyrics
        if (lineIndex !in lyrics.indices) return
        _state.value = _state.value.copy(
            editingLineIndex = lineIndex,
            editingText = lyrics[lineIndex].text
        )
    }

    fun updateEditingText(text: String) {
        _state.value = _state.value.copy(editingText = text)
    }

    fun finishEditing() {
        val idx = _state.value.editingLineIndex
        val text = _state.value.editingText
        if (idx >= 0 && text.isNotBlank()) {
            pushUndo()
            val newLyrics = syncUseCase.updateText(_state.value.lyrics, idx, text)
            _state.value = _state.value.copy(lyrics = newLyrics)
            saveLyrics(newLyrics)
        }
        _state.value = _state.value.copy(editingLineIndex = -1, editingText = "")
    }

    fun deleteLine(lineIndex: Int) {
        pushUndo()
        val newLyrics = syncUseCase.removeLine(_state.value.lyrics, lineIndex)
        _state.value = _state.value.copy(lyrics = newLyrics)
        saveLyrics(newLyrics)
    }

    fun updateTimestamp(lineIndex: Int, newTimestamp: Long) {
        val newLyrics = syncUseCase.updateTimestamp(_state.value.lyrics, lineIndex, newTimestamp)
        _state.value = _state.value.copy(lyrics = newLyrics)
        saveLyrics(newLyrics)
    }

    fun shiftAllTimestamps(offsetMs: Long) {
        pushUndo()
        val newLyrics = syncUseCase.shiftAllTimestamps(_state.value.lyrics, offsetMs)
        _state.value = _state.value.copy(lyrics = newLyrics)
        saveLyrics(newLyrics)
    }

    fun clearAllTimestamps() {
        pushUndo()
        val newLyrics = syncUseCase.clearAllTimestamps(_state.value.lyrics)
        _state.value = _state.value.copy(lyrics = newLyrics)
        saveLyrics(newLyrics)
    }

    fun clearWordTimestamps(lineIndex: Int) {
        val lyrics = _state.value.lyrics
        val line = lyrics.getOrNull(lineIndex) ?: return
        pushUndo()
        val newLyrics = lyrics.toMutableList()
        val cleared = syncUseCase.clearAllWordTimestamps(line)
        newLyrics[lineIndex] = syncUseCase.splitLineIntoWords(cleared)
        _state.value = _state.value.copy(lyrics = newLyrics)
        saveLyrics(newLyrics)
    }

    fun clearAllWordTimestamps() {
        pushUndo()
        val newLyrics = _state.value.lyrics.map { syncUseCase.splitLineIntoWords(syncUseCase.clearAllWordTimestamps(it)) }
        _state.value = _state.value.copy(lyrics = newLyrics)
        saveLyrics(newLyrics)
    }

    fun togglePlaybackOptions() {
        _state.value = _state.value.copy(
            showPlaybackOptions = !_state.value.showPlaybackOptions
        )
    }

    fun snapToCurrentPosition(lineIndex: Int) {
        val lyrics = _state.value.lyrics
        if (lineIndex !in lyrics.indices) return
        pushUndo()
        val pos = audioPlayer.state.value.currentPosition
        updateTimestamp(lineIndex, pos)
        audioPlayer.seekTo(pos)
    }

    fun shiftSingleTimestamp(lineIndex: Int, offsetMs: Long) {
        val lyrics = _state.value.lyrics
        if (lineIndex !in lyrics.indices) return
        pushUndo()
        val old = lyrics[lineIndex].timestamp
        val newTs = (old + offsetMs).coerceAtLeast(0)
        updateTimestamp(lineIndex, newTs)
        audioPlayer.seekTo(newTs)
    }

    fun insertLineBefore(index: Int, text: String = "") {
        pushUndo()
        val newLyrics = syncUseCase.insertLine(_state.value.lyrics, index, text)
        _state.value = _state.value.copy(
            lyrics = newLyrics,
            editingLineIndex = index,
            editingText = text
        )
        saveLyrics(newLyrics)
    }

    fun insertLineAfter(index: Int, text: String = "") {
        pushUndo()
        val insertAt = (index + 1).coerceAtMost(_state.value.lyrics.size)
        val newLyrics = syncUseCase.insertLine(_state.value.lyrics, insertAt, text)
        _state.value = _state.value.copy(
            lyrics = newLyrics,
            editingLineIndex = insertAt,
            editingText = text
        )
        saveLyrics(newLyrics)
    }

    fun setTimestamp(lineIndex: Int, timestampMs: Long) {
        val lyrics = _state.value.lyrics
        if (lineIndex !in lyrics.indices) return
        pushUndo()
        updateTimestamp(lineIndex, timestampMs)
    }

    fun clearTimestamp(lineIndex: Int) {
        val lyrics = _state.value.lyrics
        if (lineIndex !in lyrics.indices) return
        pushUndo()
        updateTimestamp(lineIndex, 0L)
    }

    fun captureCurrentLineTimestamp() {
        pushUndo()
        val state = _state.value
        val pos = audioPlayer.state.value.currentPosition
        val lyrics = state.lyrics
        val idx = state.selectedLineIndex
        if (lyrics.isEmpty() || idx !in lyrics.indices) return
        val newLyrics = syncUseCase.updateTimestamp(lyrics, idx, pos)
        val next = (idx + 1).coerceAtMost(lyrics.size - 1)
        _state.value = _state.value.copy(
            lyrics = newLyrics,
            selectedLineIndex = next
        )
        saveLyrics(newLyrics)
    }

    fun importAudio(audioPath: String) {
        val song = _state.value.song ?: return
        scope.launch(Dispatchers.IO) {
            val meta = extractAudioMetadata(audioPath)
            val updated = song.copy(
                audioPath = audioPath,
                title = if (meta.title.isNotBlank()) meta.title else song.title,
                artist = if (meta.artist.isNotBlank()) meta.artist else song.artist,
                album = if (meta.album.isNotBlank()) meta.album else song.album,
                composer = if (meta.composer.isNotBlank()) meta.composer else song.composer
            )
            songRepository.update(updated)
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(song = updated)
                audioPlayer.load(audioPath)
            }
        }
    }

    fun toggleWordSyncMode(skipPunctuation: Boolean = true) {
        val turningOn = !_state.value.wordSyncMode
        if (turningOn) {
            val lyrics = _state.value.lyrics
            val newLyrics = lyrics.map { syncUseCase.splitLineIntoWords(it, skipPunctuation) }
            val firstLineWithWords = newLyrics.indexOfFirst { it.words.isNotEmpty() }
            val firstWordIdx = if (firstLineWithWords >= 0)
                skipPunctuationForward(newLyrics[firstLineWithWords].words, 0).coerceAtLeast(0)
            else 0
            pushUndo()
            _state.value = _state.value.copy(
                lyrics = newLyrics,
                wordSyncMode = true,
                selectedLineIndex = firstLineWithWords.coerceAtLeast(0),
                wordCursorIndex = firstWordIdx
            )
            saveLyrics(newLyrics)
            saveWordSyncMode(true)
        } else {
            _state.value = _state.value.copy(
                wordSyncMode = false,
                wordCursorIndex = -1
            )
            saveWordSyncMode(false)
        }
    }

    fun captureWordTimestamp() {
        val s = _state.value
        if (!s.wordSyncMode) return
        val lineIdx = s.selectedLineIndex
        val line = s.lyrics.getOrNull(lineIdx) ?: return
        var cursorIdx = s.wordCursorIndex
        if (cursorIdx < 0 || cursorIdx >= line.words.size) return

        cursorIdx = skipPunctuationForward(line.words, cursorIdx)
        if (cursorIdx < 0) return

        val position = audioPlayer.state.value.currentPosition
        val currentWord = line.words[cursorIdx]
        val newWord = currentWord.copy(startTime = position)
        val newWords = line.words.toMutableList().apply { set(cursorIdx, newWord) }
        val newLineTimestamp = if (cursorIdx == 0) position else line.timestamp
        val newLyrics = s.lyrics.toMutableList()
        newLyrics[lineIdx] = line.copy(words = newWords, timestamp = newLineTimestamp)

        val nextIdx = skipPunctuationForward(newWords, cursorIdx + 1)
        if (nextIdx < 0) {
            val nextLineIdx = findNextLineWithWords(newLyrics, lineIdx)
            if (nextLineIdx >= 0) {
                val nextLine = newLyrics[nextLineIdx]
                val nextWordIdx = skipPunctuationForward(nextLine.words, 0)
                _state.value = _state.value.copy(
                    lyrics = newLyrics,
                    selectedLineIndex = nextLineIdx,
                    wordCursorIndex = nextWordIdx.coerceAtLeast(0)
                )
            } else {
                _state.value = _state.value.copy(
                    lyrics = newLyrics,
                    wordSyncMode = false,
                    wordCursorIndex = -1
                )
            }
        } else {
            _state.value = _state.value.copy(
                lyrics = newLyrics,
                wordCursorIndex = nextIdx
            )
        }
        saveLyrics(newLyrics)
    }

    private val punctRegex = Regex("[.,!?;:\\-–—()\\[\\]{}「」『』《》【】\"'«»…]+")

    private fun skipPunctuationForward(words: List<WordTimestamp>, from: Int): Int {
        var i = from
        while (i < words.size) {
            if (!punctRegex.matches(words[i].text)) return i
            i++
        }
        return -1
    }

    private fun findNextLineWithWords(lyrics: List<LrcLine>, fromIndex: Int): Int {
        for (i in (fromIndex + 1) until lyrics.size) {
            if (lyrics[i].words.isNotEmpty()) return i
        }
        return -1
    }

    fun seekToWord(lineIndex: Int, wordIndex: Int, beforeMs: Long = 1500L) {
        val line = _state.value.lyrics.getOrNull(lineIndex) ?: return
        val word = line.words.getOrNull(wordIndex) ?: return
        if (word.startTime > 0L) {
            val seekPos = (word.startTime - beforeMs).coerceAtLeast(0L)
            audioPlayer.seekTo(seekPos)
        }
    }

    fun setWordCursor(lineIndex: Int, wordIndex: Int) {
        val line = _state.value.lyrics.getOrNull(lineIndex) ?: return
        val idx = if (wordIndex in line.words.indices && !punctRegex.matches(line.words[wordIndex].text))
            wordIndex
        else
            skipPunctuationForward(line.words, wordIndex).coerceAtLeast(0)
        _state.value = _state.value.copy(
            selectedLineIndex = lineIndex,
            wordCursorIndex = idx,
            currentLineIndex = lineIndex,
            currentWordIndex = idx
        )
    }

    fun clearWordTimestamp(lineIndex: Int, wordIndex: Int) {
        val lyrics = _state.value.lyrics
        val line = lyrics.getOrNull(lineIndex) ?: return
        val word = line.words.getOrNull(wordIndex) ?: return
        pushUndo()
        val newWords = line.words.toMutableList()
        newWords[wordIndex] = word.copy(startTime = 0L)
        val newLyrics = lyrics.toMutableList()
        newLyrics[lineIndex] = line.copy(words = newWords)
        _state.value = _state.value.copy(lyrics = newLyrics)
        saveLyrics(newLyrics)
    }

    fun splitLineIntoWords(lineIndex: Int) {
        val lyrics = _state.value.lyrics
        val line = lyrics.getOrNull(lineIndex) ?: return
        if (line.words.isNotEmpty()) return
        pushUndo()
        val updated = syncUseCase.splitLineIntoWords(line)
        val newLyrics = lyrics.toMutableList()
        newLyrics[lineIndex] = updated
        _state.value = _state.value.copy(
            lyrics = newLyrics,
            wordCursorIndex = 0,
            wordSyncMode = true
        )
        saveLyrics(newLyrics)
    }

    fun mergeWordsIntoLine(lineIndex: Int) {
        val lyrics = _state.value.lyrics
        val line = lyrics.getOrNull(lineIndex) ?: return
        pushUndo()
        val updated = syncUseCase.clearAllWordTimestamps(line)
        val newLyrics = lyrics.toMutableList()
        newLyrics[lineIndex] = updated
        _state.value = _state.value.copy(lyrics = newLyrics)
        saveLyrics(newLyrics)
    }

    fun release() {
        positionUpdateJob?.cancel()
        scope.cancel()
        audioPlayer.pause()
        val s = _state.value
        s.song?.let { song ->
            kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                songRepository.update(
                    song.copy(lyrics = s.lyrics)
                )
            }
        }
    }

    companion object {
        private const val MAX_UNDO = 100
    }

    private fun saveLyrics(lyrics: List<LrcLine>) {
        _state.value.song?.let { song ->
            val updated = song.copy(lyrics = lyrics)
            scope.launch(Dispatchers.IO) {
                songRepository.update(updated)
            }
            _state.value = _state.value.copy(song = updated)
        }
    }

    private fun saveWordSyncMode(enabled: Boolean) {
        _state.value.song?.let { song ->
            val updated = song.copy(wordSyncEnabled = enabled)
            scope.launch(Dispatchers.IO) {
                songRepository.update(updated)
            }
            _state.value = _state.value.copy(song = updated)
        }
    }
}
