package com.lrcstudio.app

import com.lrcstudio.app.ui.player.AudioPlayer
import com.lrcstudio.app.ui.player.PlaybackState
import com.lrcstudio.app.ui.player.PlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.SequenceInputStream
import java.util.*
import javax.sound.sampled.*

class DesktopAudioPlayer : AudioPlayer {
    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state

    private var clip: Clip? = null
    private var timer: Timer? = null
    private var tempFiles = mutableListOf<File>()

    override fun load(path: String) {
        release()
        try {
            val file = resolveAudioFile(path)
            val ais = AudioSystem.getAudioInputStream(file)
            val newClip = AudioSystem.getClip()
            newClip.open(ais)
            newClip.addLineListener { e ->
                if (e.type == LineEvent.Type.STOP) {
                    if (newClip.microsecondPosition >= newClip.microsecondLength) {
                        _state.value = _state.value.copy(state = PlaybackState.IDLE)
                    }
                }
            }
            clip = newClip
            _state.value = PlayerState(
                state = PlaybackState.IDLE,
                audioPath = path,
                duration = (newClip.microsecondLength / 1000).coerceAtLeast(0)
            )
        } catch (e: Exception) {
            println("DesktopAudioPlayer: unsupported format: $path — ${e.message}")
        }
    }

    private fun resolveAudioFile(path: String): File {
        val original = File(path)
        try {
            AudioSystem.getAudioFileFormat(original)
            return original
        } catch (_: UnsupportedAudioFileException) {
            // try ffmpeg conversion
            return convertWithFfmpeg(original) ?: original
        }
    }

    private fun convertWithFfmpeg(input: File): File? {
        return try {
            val dest = File.createTempFile("lrc_", ".wav")
            tempFiles.add(dest)
            val process = ProcessBuilder(
                "ffmpeg", "-y", "-i", input.absolutePath,
                "-ar", "44100", "-ac", "2", "-sample_fmt", "s16",
                "-f", "wav", dest.absolutePath
            ).redirectErrorStream(false).start()
            val exitCode = process.waitFor()
            if (exitCode == 0 && dest.exists() && dest.length() > 0) {
                println("DesktopAudioPlayer: converted ${input.name} -> WAV via ffmpeg")
                dest
            } else {
                dest.delete()
                tempFiles.remove(dest)
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun play() {
        val c = clip ?: return
        val s = _state.value
        if (s.state == PlaybackState.PAUSED || s.state == PlaybackState.IDLE) {
            if (s.state == PlaybackState.IDLE) {
                c.framePosition = 0
            }
            c.start()
            _state.value = _state.value.copy(state = PlaybackState.PLAYING)
            startTimer()
        }
    }

    override fun pause() {
        if (clip != null && _state.value.state == PlaybackState.PLAYING) {
            clip?.stop()
            _state.value = _state.value.copy(state = PlaybackState.PAUSED)
        }
    }

    override fun seekTo(positionMs: Long) {
        clip?.microsecondPosition = positionMs * 1000
        _state.value = _state.value.copy(currentPosition = positionMs)
    }

    override fun setSpeed(speed: Float) {}

    override fun release() {
        timer?.cancel()
        timer = null
        clip?.close()
        clip = null
        _state.value = PlayerState()
        tempFiles.forEach { it.delete() }
        tempFiles.clear()
    }

    private fun startTimer() {
        timer?.cancel()
        timer = Timer()
        timer?.schedule(object : TimerTask() {
            override fun run() {
                val c = clip
                if (c != null && c.isRunning) {
                    _state.value = _state.value.copy(
                        currentPosition = (c.microsecondPosition / 1000).coerceAtLeast(0)
                    )
                }
            }
        }, 0, 100)
    }
}
