package com.lrcstudio.app.ui.editor

import androidx.compose.runtime.Immutable
import com.lrcstudio.app.data.model.LrcLine
import com.lrcstudio.app.data.model.Song
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
    val selectedLineIndex: Int = 0,
    val isRecording: Boolean = false,
    val newLyricText: String = "",
    val editingLineIndex: Int = -1,
    val editingText: String = "",
    val showPlaybackOptions: Boolean = false
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

    fun loadSong(songId: String) {
        val song = songRepository.getById(songId) ?: return
        _state.value = _state.value.copy(
            song = song,
            lyrics = song.lyrics,
            selectedLineIndex = 0
        )
        if (song.audioPath.isNotEmpty()) {
            audioPlayer.load(song.audioPath)
        }
        startPositionUpdates()
    }

    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = scope.launch {
            while (isActive) {
                delay(100)
                val playerState = audioPlayer.state.value
                val index = syncUseCase.getCurrentLineIndex(
                    _state.value.lyrics,
                    playerState.currentPosition
                )
                if (index != _state.value.currentLineIndex) {
                    val s = _state.value
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
                        selectedLineIndex = selIdx
                    )
                }
            }
        }
    }

    fun selectLine(index: Int) {
        val lyrics = _state.value.lyrics
        if (index !in lyrics.indices) return
        _state.value = _state.value.copy(selectedLineIndex = index)
        val ts = lyrics[index].timestamp
        if (ts > 0L) {
            audioPlayer.seekTo(ts)
        }
    }

    fun playPause() {
        val playerState = audioPlayer.state.value
        when (playerState.state) {
            PlaybackState.PLAYING -> audioPlayer.pause()
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
            val newLyrics = syncUseCase.updateText(_state.value.lyrics, idx, text)
            _state.value = _state.value.copy(lyrics = newLyrics)
            saveLyrics(newLyrics)
        }
        _state.value = _state.value.copy(editingLineIndex = -1, editingText = "")
    }

    fun deleteLine(lineIndex: Int) {
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
        val newLyrics = syncUseCase.shiftAllTimestamps(_state.value.lyrics, offsetMs)
        _state.value = _state.value.copy(lyrics = newLyrics)
        saveLyrics(newLyrics)
    }

    fun clearAllTimestamps() {
        val newLyrics = syncUseCase.clearAllTimestamps(_state.value.lyrics)
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
        val pos = audioPlayer.state.value.currentPosition
        updateTimestamp(lineIndex, pos)
        audioPlayer.seekTo(pos)
    }

    fun shiftSingleTimestamp(lineIndex: Int, offsetMs: Long) {
        val lyrics = _state.value.lyrics
        if (lineIndex !in lyrics.indices) return
        val old = lyrics[lineIndex].timestamp
        val newTs = (old + offsetMs).coerceAtLeast(0)
        updateTimestamp(lineIndex, newTs)
        audioPlayer.seekTo(newTs)
    }

    fun insertLineBefore(index: Int, text: String = "") {
        val newLyrics = syncUseCase.insertLine(_state.value.lyrics, index, text)
        _state.value = _state.value.copy(
            lyrics = newLyrics,
            editingLineIndex = index,
            editingText = text
        )
        saveLyrics(newLyrics)
    }

    fun insertLineAfter(index: Int, text: String = "") {
        val insertAt = (index + 1).coerceAtMost(_state.value.lyrics.size)
        val newLyrics = syncUseCase.insertLine(_state.value.lyrics, insertAt, text)
        _state.value = _state.value.copy(
            lyrics = newLyrics,
            editingLineIndex = insertAt,
            editingText = text
        )
        saveLyrics(newLyrics)
    }

    fun clearTimestamp(lineIndex: Int) {
        val lyrics = _state.value.lyrics
        if (lineIndex !in lyrics.indices) return
        updateTimestamp(lineIndex, 0L)
    }

    fun captureCurrentLineTimestamp() {
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
        val meta = extractAudioMetadata(audioPath)
        val updated = song.copy(
            audioPath = audioPath,
            title = if (meta.title.isNotBlank()) meta.title else song.title,
            artist = if (meta.artist.isNotBlank()) meta.artist else song.artist,
            album = if (meta.album.isNotBlank()) meta.album else song.album,
            composer = if (meta.composer.isNotBlank()) meta.composer else song.composer
        )
        scope.launch(Dispatchers.IO) {
            songRepository.update(updated)
        }
        _state.value = _state.value.copy(song = updated)
        audioPlayer.load(audioPath)
    }

    fun release() {
        positionUpdateJob?.cancel()
        scope.cancel()
        val s = _state.value
        s.song?.let { song ->
            kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                songRepository.update(
                    song.copy(lyrics = s.lyrics)
                )
            }
        }
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
}
