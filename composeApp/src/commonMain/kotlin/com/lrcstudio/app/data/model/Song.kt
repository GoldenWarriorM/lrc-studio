package com.lrcstudio.app.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class Song(
    val id: String,
    val title: String = "Untitled",
    val artist: String = "Unknown",
    val album: String = "",
    val composer: String = "",
    val creator: String = "",
    val audioPath: String = "",
    val lrcPath: String = "",
    val duration: Long = 0L,
    val lyrics: List<LrcLine> = emptyList(),
    val wordSyncEnabled: Boolean = false
)
