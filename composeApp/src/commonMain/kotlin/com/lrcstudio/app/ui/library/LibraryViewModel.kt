package com.lrcstudio.app.ui.library

import com.lrcstudio.app.data.model.LrcLine
import com.lrcstudio.app.data.model.Song
import com.lrcstudio.app.data.repository.SongRepository
import com.lrcstudio.app.data.parser.LrcParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class LibraryState(
    val songs: List<Song> = emptyList(),
    val isLoading: Boolean = false
)

class LibraryViewModel(
    private val songRepository: SongRepository
) {
    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state

    init {
        refresh()
    }

    fun refresh() {
        _state.value = LibraryState(
            songs = songRepository.getAll()
        )
    }

    fun createSongFromPastedText(text: String): Song {
        val lines = text.lines()
            .filter { it.isNotBlank() }
            .mapIndexed { i, line -> LrcLine(text = line.trim(), index = i) }

        val song = Song(
            id = "",
            title = "Untitled",
            artist = "Unknown",
            lyrics = lines
        )
        val saved = songRepository.add(song)
        refresh()
        return saved
    }

    fun createSongFromAudio(audioPath: String): Song {
        val fileName = audioPath.substringAfterLast('/').substringBeforeLast('.')
            .replaceFirst(Regex("^[a-zA-Z]+:\\d+%2F"), "")
            .let { if (it.length > 60) it.take(60) else it }
        val song = Song(
            id = "",
            title = fileName.ifBlank { "Untitled" },
            artist = "Unknown",
            audioPath = audioPath
        )
        val saved = songRepository.add(song)
        refresh()
        return saved
    }

    fun createSongFromLrcContent(lrcContent: String): Song {
        val lyrics = LrcParser.parse(lrcContent)
        val song = Song(
            id = "",
            title = "Untitled",
            artist = "Unknown",
            lyrics = lyrics
        )
        val saved = songRepository.add(song)
        refresh()
        return saved
    }

    fun deleteSong(id: String) {
        songRepository.remove(id)
        refresh()
    }
}
