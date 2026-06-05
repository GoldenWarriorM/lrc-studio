package com.lrcstudio.app

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.lrcstudio.app.ui.player.AudioPlayer
import com.lrcstudio.app.ui.player.PlaybackState
import com.lrcstudio.app.ui.player.PlayerState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AndroidAudioPlayer(context: Context) : AudioPlayer {
    private val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build()
    private val _state = MutableStateFlow(PlayerState())
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var positionJob: Job? = null

    override val state: StateFlow<PlayerState> = _state

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(
                    state = when {
                        isPlaying -> PlaybackState.PLAYING
                        exoPlayer.playbackState == Player.STATE_ENDED -> PlaybackState.FINISHED
                        else -> PlaybackState.PAUSED
                    }
                )
                updatePositionTracking()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _state.value = _state.value.copy(
                    state = when (playbackState) {
                        Player.STATE_READY -> if (exoPlayer.isPlaying) PlaybackState.PLAYING else PlaybackState.PAUSED
                        Player.STATE_ENDED -> PlaybackState.FINISHED
                        else -> _state.value.state
                    },
                    duration = exoPlayer.duration.coerceAtLeast(0)
                )
                updatePositionTracking()
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e("AndroidAudioPlayer", "Player error: ${error.message}", error)
            }
        })
    }

    private fun updatePositionTracking() {
        positionJob?.cancel()
        if (exoPlayer.isPlaying) {
            positionJob = scope.launch {
                while (isActive) {
                    _state.value = _state.value.copy(
                        currentPosition = exoPlayer.currentPosition.coerceAtLeast(0)
                    )
                    delay(100)
                }
            }
        }
    }

    override fun load(path: String) {
        val mediaItem = MediaItem.fromUri(path)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        _state.value = PlayerState(
            state = PlaybackState.IDLE,
            audioPath = path
        )
    }

    override fun play() {
        exoPlayer.play()
    }

    override fun pause() {
        exoPlayer.pause()
    }

    override fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs)
        _state.value = _state.value.copy(currentPosition = positionMs)
    }

    override fun setSpeed(speed: Float) {
        exoPlayer.setPlaybackSpeed(speed)
    }

    override fun release() {
        positionJob?.cancel()
        scope.cancel()
        exoPlayer.release()
    }
}
