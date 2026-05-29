package com.lrcstudio.app.data.repository

import com.lrcstudio.app.data.model.LrcLine
import com.lrcstudio.app.data.model.Song
import com.lrcstudio.app.data.parser.LrcParser
import com.lrcstudio.app.util.readTextFile
import com.lrcstudio.app.util.writeTextFile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SongRepository(private val storageDir: String) {
    private val songs = mutableListOf<Song>()
    private var nextId = 1
    private val jsonLibrary = Json { ignoreUnknownKeys = true; prettyPrint = true }

    init {
        loadFromDisk()
    }

    fun getAll(): List<Song> = songs.toList()

    fun getById(id: String): Song? = songs.find { it.id == id }

    fun add(song: Song): Song {
        val withId = song.copy(id = nextId.toString())
        songs.add(withId)
        nextId++
        saveToDisk()
        return withId
    }

    fun update(song: Song) {
        val index = songs.indexOfFirst { it.id == song.id }
        if (index != -1) {
            songs[index] = song
            saveToDisk()
        }
    }

    fun remove(id: String) {
        songs.removeAll { it.id == id }
        saveToDisk()
    }

    fun parseAndUpdateLyrics(songId: String, content: String): List<LrcLine> {
        val lyrics = LrcParser.parse(content)
        val index = songs.indexOfFirst { it.id == songId }
        if (index != -1) {
            songs[index] = songs[index].copy(lyrics = lyrics)
            saveToDisk()
        }
        return lyrics
    }

    fun generateLrcContent(songId: String): String {
        val song = getById(songId) ?: return ""
        return LrcParser.generate(song.lyrics)
    }

    private fun songsFile() = "$storageDir/songs.json"

    private fun saveToDisk() {
        try {
            val json = jsonLibrary.encodeToString(songs)
            writeTextFile(songsFile(), json)
        } catch (e: Exception) {
            println("SongRepository: failed to save — ${e.message}")
        }
    }

    private fun loadFromDisk() {
        try {
            val json = readTextFile(songsFile()) ?: return
            val loaded: List<Song> = jsonLibrary.decodeFromString(json)
            songs.clear()
            songs.addAll(loaded)
            nextId = (songs.maxOfOrNull { it.id.toIntOrNull() ?: 0 } ?: 0) + 1
        } catch (e: Exception) {
            println("SongRepository: failed to load — ${e.message}")
        }
    }
}
