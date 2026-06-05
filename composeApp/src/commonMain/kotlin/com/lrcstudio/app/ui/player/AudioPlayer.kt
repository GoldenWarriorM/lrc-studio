package com.lrcstudio.app.ui.player

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.StateFlow

enum class PlaybackState {
    IDLE, PLAYING, PAUSED, FINISHED
}

@Immutable
data class PlayerState(
    val state: PlaybackState = PlaybackState.IDLE,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val audioPath: String = ""
)

interface AudioPlayer {
    val state: StateFlow<PlayerState>

    fun load(path: String)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setSpeed(speed: Float)
    fun release()
}
