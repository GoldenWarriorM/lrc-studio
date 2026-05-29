package com.lrcstudio.app.util

import java.io.File

actual fun extractAudioMetadata(path: String): AudioMetadata {
    val file = File(path)
    val name = file.nameWithoutExtension

    val ffprobeResult = try {
        val process = ProcessBuilder(
            "ffprobe", "-v", "quiet",
            "-show_entries", "format_tags=title,artist,album,composer",
            "-of", "default=noprint_wrappers=1",
            path
        ).redirectErrorStream(true).start()
        val output = process.inputStream.readAllBytes().decodeToString()
        process.waitFor()
        if (process.exitValue() == 0) parseTagOutput(output) else null
    } catch (_: Exception) { null }

    if (ffprobeResult != null && (ffprobeResult.title.isNotBlank() || ffprobeResult.artist.isNotBlank())) {
        return ffprobeResult
    }

    val ffmpegResult = try {
        val process = ProcessBuilder(
            "ffmpeg", "-i", path, "-f", "ffmetadata", "-"
        ).redirectErrorStream(true).start()
        // ffmpeg outputs metadata to stderr before the actual metadata dump;
        // read everything and take the last block
        val output = process.inputStream.readAllBytes().decodeToString()
        process.waitFor()
        parseTagOutput(output)
    } catch (_: Exception) { null }

    if (ffmpegResult != null && (ffmpegResult.title.isNotBlank() || ffmpegResult.artist.isNotBlank())) {
        return ffmpegResult
    }

    val dashIdx = name.indexOf(" — ")
    if (dashIdx > 0) {
        return AudioMetadata(
            title = name.substring(dashIdx + 3).trim(),
            artist = name.substring(0, dashIdx).trim()
        )
    }
    val hyphenIdx = name.indexOf(" - ")
    if (hyphenIdx > 0) {
        return AudioMetadata(
            title = name.substring(hyphenIdx + 3).trim(),
            artist = name.substring(0, hyphenIdx).trim()
        )
    }
    return AudioMetadata(title = name)
}

private fun parseTagOutput(output: String): AudioMetadata {
    var title = ""
    var artist = ""
    var album = ""
    var composer = ""
    for (line in output.lines()) {
        val trimmed = line.trim()
        val eq = trimmed.indexOf('=')
        if (eq < 0) continue
        var key = trimmed.substring(0, eq)
        val value = trimmed.substring(eq + 1).trim()
        if (key.startsWith("TAG:")) key = key.substring(4)
        when (key) {
            "title" -> title = value
            "artist" -> artist = value
            "album" -> album = value
            "composer" -> composer = value
        }
    }
    return AudioMetadata(title, artist, album, composer)
}
