package com.lrcstudio.app.ui.editor

import androidx.compose.runtime.Immutable
import com.lrcstudio.app.data.model.LrcLine
import com.lrcstudio.app.data.model.Song
import com.lrcstudio.app.data.model.WordTimestamp
import com.lrcstudio.app.data.repository.SongRepository
import com.lrcstudio.app.domain.usecase.Punctuation
import com.lrcstudio.app.domain.usecase.SyncUseCase
import com.lrcstudio.app.ui.player.AudioPlayer
import com.lrcstudio.app.ui.player.PlaybackState
import com.lrcstudio.app.util.extractAudioMetadata
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import com.lrcstudio.app.data.model.VocalRole
import com.lrcstudio.app.domain.usecase.Syllabifier
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
    val canRedo: Boolean = false,
    /** A text edit waiting for the user to choose what happens to the word timings. */
    val pendingTextEdit: PendingTextEdit? = null
)

/** New text for an already word-timed line, waiting for a timing decision. */
@Immutable
data class PendingTextEdit(
    val lineIndex: Int,
    val text: String
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
    private var nextUid = 0

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
        val changedIdx = changedLineIndex(current, previous)
        _state.value = _state.value.copy(lyrics = previous, selectedLineIndex = changedIdx)
        saveLyrics(previous)
        if (changedIdx >= 0) {
            val ts = nearestTimestamp(previous, changedIdx)
            audioPlayer.seekTo(ts)
        }
        _state.value = _state.value.copy(
            canUndo = undoStack.isNotEmpty(),
            canRedo = true
        )
    }

    fun redo() {
        val current = _state.value.lyrics
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.add(current)
        val changedIdx = changedLineIndex(current, next)
        _state.value = _state.value.copy(lyrics = next, selectedLineIndex = changedIdx)
        saveLyrics(next)
        if (changedIdx >= 0) {
            val ts = nearestTimestamp(next, changedIdx)
            audioPlayer.seekTo(ts)
        }
        _state.value = _state.value.copy(
            canUndo = true,
            canRedo = redoStack.isNotEmpty()
        )
    }

    private fun changedLineIndex(before: List<LrcLine>, after: List<LrcLine>): Int {
        val maxLen = maxOf(before.size, after.size)
        for (i in 0 until maxLen) {
            if (i >= before.size || i >= after.size || before[i] != after[i]) return i
        }
        return -1
    }

    private fun nearestTimestamp(lyrics: List<LrcLine>, at: Int): Long {
        if (at in lyrics.indices) {
            val ts = lyrics[at].timestamp
            if (ts > 0L) return ts
        }
        for (i in (at - 1) downTo 0) {
            val ts = lyrics[i].timestamp
            if (ts > 0L) return ts
        }
        for (i in at until lyrics.size) {
            val ts = lyrics[i].timestamp
            if (ts > 0L) return ts
        }
        return 0L
    }

    fun loadSong(songId: String) {
        val song = songRepository.getById(songId) ?: return
        undoStack.clear()
        redoStack.clear()
        nextUid = 0
        val lyrics = if (song.wordSyncEnabled) {
            song.lyrics.map { syncUseCase.splitLineIntoWords(it) }
        } else {
            song.lyrics
        }.map { it.copy(uid = nextUid++) }
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
                delay(10)
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
                val skipUntimed = s.wordSyncMode && index >= 0 && s.lyrics.getOrNull(index)
                    ?.let { it.words.isNotEmpty() && it.words.none { w -> w.startTime > 0L } } == true
                if (!skipUntimed && index != s.currentLineIndex) {
                    _state.value = _state.value.copy(
                        currentLineIndex = index,
                        currentWordIndex = wordIndex
                    )
                } else if (!skipUntimed && wordIndex != s.currentWordIndex) {
                    _state.value = _state.value.copy(currentWordIndex = wordIndex)
                }
            }
        }
    }

    fun selectLine(index: Int) {
        val lyrics = _state.value.lyrics
        if (index !in lyrics.indices) return
        val line = lyrics[index]
        // Land on the first real word, so the capture pill opens in its split state.
        val wordCursor = if (line.words.isNotEmpty()) {
            skipPunctuationForward(line.words, 0).coerceAtLeast(0)
        } else {
            -1
        }
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

    /**
     * [lineIndex] is the card the text field belongs to; passing it keeps a stale
     * shared editing index from applying the text to the wrong line.
     */
    fun finishEditing(lineIndex: Int = -1) {
        val state = _state.value
        val idx = if (lineIndex in state.lyrics.indices) lineIndex else state.editingLineIndex
        val text = state.editingText.trim()
        val line = state.lyrics.getOrNull(idx)
        if (idx < 0 || line == null || text.isBlank() || text == line.text) {
            _state.value = _state.value.copy(editingLineIndex = -1, editingText = "")
            return
        }
        // Any word-level markup is at risk here, not only a fully timed line:
        // the words would no longer match the text the user just typed.
        if (line.words.isNotEmpty()) {
            // A word-timed line cannot silently take new text: the words would no
            // longer match their timestamps, so ask what to do with the markup.
            _state.value = _state.value.copy(
                editingLineIndex = -1,
                editingText = "",
                pendingTextEdit = PendingTextEdit(idx, text)
            )
            return
        }
        pushUndo()
        // Untimed lines take the new text directly; any stale word split is dropped
        // so the line is not still showing the words of its previous text.
        val newLyrics = syncUseCase.retextLine(_state.value.lyrics, idx, text, keepTiming = false)
        _state.value = _state.value.copy(
            lyrics = newLyrics,
            editingLineIndex = -1,
            editingText = ""
        )
        saveLyrics(newLyrics)
    }

    /** Applies the pending text edit, keeping the old word timings where possible. */
    fun resolveTextEdit(keepTiming: Boolean) {
        val pending = _state.value.pendingTextEdit ?: return
        pushUndo()
        val newLyrics = syncUseCase.retextLine(
            _state.value.lyrics,
            pending.lineIndex,
            pending.text,
            keepTiming
        )
        _state.value = _state.value.copy(lyrics = newLyrics, pendingTextEdit = null)
        saveLyrics(newLyrics)
    }

    fun dismissTextEdit() {
        _state.value = _state.value.copy(pendingTextEdit = null)
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
        _state.value = _state.value.copy(lyrics = newLyrics, selectedLineIndex = 0)
        saveLyrics(newLyrics)
        audioPlayer.seekTo(0)
    }

    fun clearWordTimestamps(lineIndex: Int) {
        val lyrics = _state.value.lyrics
        val line = lyrics.getOrNull(lineIndex) ?: return
        pushUndo()
        val newLyrics = lyrics.toMutableList()
        val cleared = syncUseCase.clearAllWordTimestamps(line)
        newLyrics[lineIndex] = syncUseCase.splitLineIntoWords(cleared)
        _state.value = _state.value.copy(lyrics = newLyrics, selectedLineIndex = lineIndex)
        saveLyrics(newLyrics)
        val ts = newLyrics.getOrNull(lineIndex)?.timestamp ?: 0L
        audioPlayer.seekTo(ts)
    }

    fun clearAllWordTimestamps() {
        pushUndo()
        val newLyrics = _state.value.lyrics.map { syncUseCase.splitLineIntoWords(syncUseCase.clearAllWordTimestamps(it)) }
        _state.value = _state.value.copy(lyrics = newLyrics, selectedLineIndex = 0)
        saveLyrics(newLyrics)
        audioPlayer.seekTo(0)
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
        val uid = nextUid++
        val newLine = LrcLine(text = text, uid = uid)
        val newLyrics = (_state.value.lyrics.take(index) + newLine + _state.value.lyrics.drop(index))
            .mapIndexed { i, line -> line.copy(index = i) }
        _state.value = _state.value.copy(
            lyrics = newLyrics,
            editingLineIndex = index,
            editingText = text
        )
        saveLyrics(newLyrics)
    }

    fun insertLineAfter(index: Int, text: String = "") {
        pushUndo()
        val uid = nextUid++
        val insertAt = (index + 1).coerceAtMost(_state.value.lyrics.size)
        val newLine = LrcLine(text = text, uid = uid)
        val newLyrics = (_state.value.lyrics.take(insertAt) + newLine + _state.value.lyrics.drop(insertAt))
            .mapIndexed { i, line -> line.copy(index = i) }
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
        _state.value = _state.value.copy(selectedLineIndex = lineIndex)
        audioPlayer.seekTo(0)
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

    /** Assigns the vocal agent of a single line (1, 2, 1000 for group); 0 clears it. */
    fun setLineAgent(lineIndex: Int, agent: Int) {
        val lyrics = _state.value.lyrics
        if (lineIndex !in lyrics.indices) return
        val normalized = agent.coerceAtLeast(0)
        if (lyrics[lineIndex].agent == normalized) return
        pushUndo()
        val newLyrics = lyrics.toMutableList()
        newLyrics[lineIndex] = newLyrics[lineIndex].copy(agent = normalized)
        _state.value = _state.value.copy(lyrics = newLyrics)
        saveLyrics(newLyrics)
    }

    private inline fun updateLine(index: Int, transform: (LrcLine) -> LrcLine) {
        val lyrics = _state.value.lyrics
        val line = lyrics.getOrNull(index) ?: return
        val updated = transform(line)
        if (updated == line) return
        pushUndo()
        val next = lyrics.toMutableList().apply { set(index, updated) }
        _state.value = _state.value.copy(lyrics = next)
        saveLyrics(next)
    }

    /** Marks the whole line as a background / harmony vocal ([bg: ...] in ELRC). */
    fun setLineBackground(lineIndex: Int, isBackground: Boolean) {
        updateLine(lineIndex) { it.copy(isBackground = isBackground) }
    }

    /**
     * Makes a single word a background vocal, so it leaves the lead line and is
     * drawn as the smaller [bg: ...] block underneath with its own timing.
     */
    fun toggleWordBackground(lineIndex: Int, wordIndex: Int) {
        val line = _state.value.lyrics.getOrNull(lineIndex) ?: return
        val word = line.words.getOrNull(wordIndex) ?: return
        val role = if (word.role == VocalRole.MAIN) VocalRole.BACKGROUND else VocalRole.MAIN
        updateLine(lineIndex) { current ->
            val words = current.words.toMutableList()
            words[wordIndex] = words[wordIndex].copy(role = role)
            current.copy(words = words)
        }
    }

    /** Syllable pieces are glued together, so "melt"+"ing" reads as "melting". */
    private fun joinWordText(words: List<WordTimestamp>): String = buildString {
        words.forEachIndexed { index, word ->
            if (index > 0 && !word.joinsPrevious) append(' ')
            append(word.text)
        }
    }

    private fun syllablePieces(word: WordTimestamp, limit: Long): List<WordTimestamp> {
        val pieces = Syllabifier.split(word.text)
        if (pieces.size < 2) return listOf(word)
        val start = word.startTime
        // An explicit end wins; otherwise spread up to the next timed word so the
        // syllables keep the original timing shape instead of losing it.
        val end = when {
            word.endTime > start -> word.endTime
            limit > start -> limit
            else -> 0L
        }
        val span = if (end > start) end - start else 0L
        // Punctuation is not sung, so it stays untimed: the word cursor skips it
        // and the export leaves it interpolated into the word in front of it.
        val spokenCount = pieces.count { !Punctuation.isStandalone(it) }
        var spokenIndex = 0
        return pieces.mapIndexed { index, text ->
            val joinsPrevious = index > 0 || word.joinsPrevious
            if (Punctuation.isStandalone(text)) {
                word.copy(
                    text = text,
                    startTime = 0L,
                    endTime = 0L,
                    joinsPrevious = joinsPrevious
                )
            } else {
                val position = spokenIndex++
                val pieceStart = when {
                    position == 0 -> start
                    span > 0L && spokenCount > 1 -> start + span * position / spokenCount
                    else -> 0L
                }
                val pieceEnd = if (position == spokenCount - 1 && span > 0L) end else 0L
                word.copy(
                    text = text,
                    startTime = pieceStart,
                    endTime = pieceEnd,
                    joinsPrevious = joinsPrevious
                )
            }
        }
    }

    /** Splits every word of every line into syllables. */
    fun splitAllWordsIntoSyllables() {
        if (_state.value.lyrics.none { it.words.isNotEmpty() }) return
        pushUndo()
        val lyrics = _state.value.lyrics
        val updated = lyrics.map { line ->
            if (line.words.isEmpty()) {
                line
            } else {
                val words = mutableListOf<WordTimestamp>()
                line.words.forEachIndexed { index, word ->
                    val nextStart = line.words.drop(index + 1)
                        .firstOrNull { it.startTime > word.startTime }?.startTime ?: 0L
                    words.addAll(syllablePieces(word, nextStart))
                }
                line.copy(words = words, text = joinWordText(words))
            }
        }
        _state.value = _state.value.copy(lyrics = updated)
        saveLyrics(updated)
    }

    /** Glues syllable pieces back into whole words, keeping the outer timing. */
    fun mergeSyllables() {
        if (_state.value.lyrics.none { line -> line.words.any { it.joinsPrevious } }) return
        pushUndo()
        val lyrics = _state.value.lyrics
        val updated = lyrics.map { line ->
            if (line.words.isEmpty()) {
                line
            } else {
                val merged = mutableListOf<WordTimestamp>()
                for (word in line.words) {
                    val last = merged.lastOrNull()
                    if (word.joinsPrevious && last != null) {
                        merged[merged.lastIndex] = last.copy(
                            text = last.text + word.text,
                            startTime = if (last.startTime > 0L) last.startTime else word.startTime,
                            endTime = maxOf(last.endTime, word.endTime)
                        )
                    } else {
                        merged.add(word)
                    }
                }
                line.copy(words = merged, text = joinWordText(merged))
            }
        }
        _state.value = _state.value.copy(lyrics = updated)
        saveLyrics(updated)
    }

    /** Splits a single word into syllables (long-press on it). */
    fun splitWordIntoSyllables(lineIndex: Int, wordIndex: Int) {
        val line = _state.value.lyrics.getOrNull(lineIndex) ?: return
        if (wordIndex !in line.words.indices) return
        val word = line.words[wordIndex]
        val nextStart = line.words.drop(wordIndex + 1)
            .firstOrNull { it.startTime > word.startTime }?.startTime ?: 0L
        val pieces = syllablePieces(word, nextStart)
        if (pieces.size < 2) return
        pushUndo()
        val current = _state.value
        val words = line.words.toMutableList().apply {
            removeAt(wordIndex)
            addAll(wordIndex, pieces)
        }
        val updated = current.lyrics.toMutableList().apply {
            set(lineIndex, line.copy(words = words, text = joinWordText(words)))
        }
        _state.value = current.copy(lyrics = updated, wordCursorIndex = wordIndex)
        saveLyrics(updated)
    }

    /**
     * Closes the last already-started word before the cursor at the playback
     * position. Used while sitting on the start of a new line, so the tail of the
     * previous line is trimmed live instead of after the fact.
     */
    fun endPreviousWordAtPosition() {
        val s = _state.value
        if (!s.wordSyncMode) return
        val position = audioPlayer.state.value.currentPosition
        if (position <= 0L) return

        var lineIdx = s.selectedLineIndex
        while (lineIdx >= 0) {
            val line = s.lyrics.getOrNull(lineIdx) ?: return
            var wordIdx = if (lineIdx == s.selectedLineIndex) {
                minOf(s.wordCursorIndex, line.words.size) - 1
            } else {
                line.words.lastIndex
            }
            while (wordIdx >= 0) {
                val word = line.words[wordIdx]
                if (word.startTime > 0L) {
                    if (position <= word.startTime) return
                    pushUndo()
                    val words = line.words.toMutableList().apply {
                        set(wordIdx, word.copy(endTime = position))
                    }
                    val newLyrics = s.lyrics.toMutableList().apply {
                        set(lineIdx, line.copy(words = words))
                    }
                    _state.value = _state.value.copy(lyrics = newLyrics)
                    saveLyrics(newLyrics)
                    return
                }
                wordIdx--
            }
            lineIdx--
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

        pushUndo()
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
                    wordCursorIndex = cursorIdx
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

    private fun skipPunctuationForward(words: List<WordTimestamp>, from: Int): Int {
        var i = from
        while (i < words.size) {
            if (!Punctuation.isStandalone(words[i].text)) return i
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
        val idx = if (wordIndex in line.words.indices && !Punctuation.isStandalone(line.words[wordIndex].text))
            wordIndex
        else
            skipPunctuationForward(line.words, wordIndex).coerceAtLeast(0)
        _state.value = _state.value.copy(
            selectedLineIndex = lineIndex,
            wordCursorIndex = idx
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
