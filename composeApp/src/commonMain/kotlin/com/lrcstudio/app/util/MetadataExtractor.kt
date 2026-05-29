package com.lrcstudio.app.util

data class AudioMetadata(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val composer: String = ""
)

expect fun extractAudioMetadata(path: String): AudioMetadata
