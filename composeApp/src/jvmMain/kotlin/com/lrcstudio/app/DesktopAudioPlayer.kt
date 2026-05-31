package com.lrcstudio.app

import com.lrcstudio.app.ui.player.AudioPlayer
import com.lrcstudio.app.ui.player.PlaybackState
import com.lrcstudio.app.ui.player.PlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import javax.sound.sampled.*
import java.util.Timer
import java.util.TimerTask
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

class DesktopAudioPlayer : AudioPlayer {
    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state

    private var line: SourceDataLine? = null
    private var thread: Thread? = null
    private var timer: Timer? = null
    private var tempFiles = mutableListOf<File>()

    private var pcmData: ByteArray? = null
    private var activePcm: ByteArray? = null
    private var totalFrames: Long = 0
    private var activeTotalFrames: Long = 0

    private var playheadFrames: Long = 0
    private var currentSpeed = 1.0f
    private var playing = false
    private var paused = false
    private var stopRequested = false

    override fun load(path: String) {
        release()
        try {
            val file = resolveAudioFile(path)
            val ais = AudioSystem.getAudioInputStream(file)
            val decoded = AudioSystem.getAudioInputStream(
                AudioFormat(44100f, 16, 2, true, false), ais
            )
            val data = decoded.readAllBytes()
            decoded.close()
            ais.close()

            totalFrames = data.size / 4L
            pcmData = data
            activePcm = data
            activeTotalFrames = totalFrames

            _state.value = PlayerState(
                state = PlaybackState.IDLE,
                audioPath = path,
                duration = (totalFrames * 1000 / 44100).coerceAtLeast(0)
            )
        } catch (e: Exception) {
            println("DesktopAudioPlayer: failed to load $path — ${e.message}")
            e.printStackTrace()
        }
    }

    private fun resolveAudioFile(path: String): File {
        val original = File(path)
        return try {
            AudioSystem.getAudioFileFormat(original)
            original
        } catch (_: UnsupportedAudioFileException) {
            convertWithFfmpeg(original) ?: original
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
        val data = activePcm ?: return
        if (paused) {
            paused = false
            playing = true
            startLine()
            _state.value = _state.value.copy(state = PlaybackState.PLAYING)
            startTimer()
            return
        }

        playheadFrames = playheadFrames.coerceAtMost(activeTotalFrames)
        if (playheadFrames >= activeTotalFrames) {
            playheadFrames = 0
        }

        playing = true
        paused = false

        stopLine()
        stopRequested = false
        createAndStartLine(data)

        _state.value = _state.value.copy(state = PlaybackState.PLAYING)
        startTimer()
    }

    private fun createAndStartLine(data: ByteArray) {
        val outFormat = AudioFormat(44100f, 16, 2, true, false)
        val info = DataLine.Info(SourceDataLine::class.java, outFormat)
        val newLine = AudioSystem.getLine(info) as SourceDataLine
        newLine.open(outFormat, 4096)
        newLine.start()
        line = newLine

        thread = Thread {
            val buf = ShortArray(4096)
            val writeBuf = ByteArray(4096 * 4)
            var lastReportedPosition = -1L

            while (!stopRequested) {
                val framesToRead = buf.size / 2
                val actualRead = readInterleaved(data, playheadFrames, buf, framesToRead)
                if (actualRead <= 0) {
                    playing = false
                    _state.value = _state.value.copy(state = PlaybackState.IDLE)
                    break
                }

                for (i in 0 until actualRead) {
                    val s = buf[i].coerceIn(Short.MIN_VALUE, Short.MAX_VALUE)
                    writeBuf[i * 2] = (s.toInt() and 0xFF).toByte()
                    writeBuf[i * 2 + 1] = ((s.toInt() shr 8) and 0xFF).toByte()
                }

                var written = 0
                while (written < actualRead && !stopRequested) {
                    if (paused) {
                        Thread.sleep(50)
                        continue
                    }
                    val remainingShorts = actualRead - written
                    val n = newLine.write(writeBuf, written * 2, remainingShorts * 2)
                    written += n / 2
                }

                playheadFrames += actualRead / 2

                    val pos = ((playheadFrames * currentSpeed * 1000 / 44100).toLong()).coerceAtLeast(0L)
                if (pos != lastReportedPosition) {
                    lastReportedPosition = pos
                    _state.value = _state.value.copy(currentPosition = pos)
                }
            }

            if (!stopRequested) {
                newLine.drain()
            }
        }.apply { isDaemon = true; start() }
    }

    private fun readInterleaved(data: ByteArray, frameOffset: Long, out: ShortArray, frames: Int): Int {
        val totalFrames = data.size / 4L
        var written = 0
        var i = 0
        while (i < frames && frameOffset + i < totalFrames) {
            val idx = ((frameOffset + i) * 4).toInt()
            val l = ((data[idx + 1].toInt() shl 8) or (data[idx].toInt() and 0xFF)).toShort()
            val r = ((data[idx + 3].toInt() shl 8) or (data[idx + 2].toInt() and 0xFF)).toShort()
            out[written++] = l
            out[written++] = r
            i++
        }
        return written
    }

    private fun startLine() {
        line?.start()
    }

    private fun stopLine() {
        thread?.let { t ->
            stopRequested = true
            t.interrupt()
            try { t.join(500) } catch (_: InterruptedException) {}
        }
        thread = null
        line?.stop()
        line?.drain()
        line?.close()
        line = null
    }

    override fun pause() {
        if (playing) {
            paused = true
            playing = false
            _state.value = _state.value.copy(state = PlaybackState.PAUSED)
        }
    }

    override fun seekTo(positionMs: Long) {
        playheadFrames = (positionMs * 44100 / 1000 / currentSpeed).toLong().coerceAtLeast(0)
        _state.value = _state.value.copy(currentPosition = positionMs)
    }

    override fun setSpeed(speed: Float) {
        currentSpeed = speed
        val original = pcmData ?: return

        val wasPlaying = playing && !paused
        val oldActiveFrames = activeTotalFrames

        if (speed == 1.0f) {
            activePcm = original
            activeTotalFrames = totalFrames
        } else {
            val stretched = timeStretch(original, speed)
            activePcm = stretched
            activeTotalFrames = stretched.size / 4L
        }
        playheadFrames = (playheadFrames * activeTotalFrames / oldActiveFrames).coerceAtMost(activeTotalFrames)

        val posMs = ((playheadFrames * currentSpeed * 1000 / 44100).toLong()).coerceAtLeast(0L)
        _state.value = _state.value.copy(currentPosition = posMs)

        if (wasPlaying) {
            stopLine()
            stopRequested = false
            createAndStartLine(activePcm!!)
        }
    }

    private fun timeStretch(input: ByteArray, speed: Float): ByteArray {
        val frameBytes = 4
        val totalInputFrames = input.size / frameBytes
        val totalOutputFrames = (totalInputFrames / speed).toInt()
        if (totalOutputFrames <= 0) return input

        val window = 2048
        val hopIn = (window / 4 * speed).toInt().coerceAtLeast(1)
        val hopOut = window / 4

        val output = ByteArray(totalOutputFrames * frameBytes)
        val accum = FloatArray(totalOutputFrames * 2)
        val weight = FloatArray(totalOutputFrames)

        val hann = FloatArray(window) { i ->
            0.5f * (1f - cos(2.0 * PI * i / (window - 1))).toFloat()
        }

        var inStart = 0
        var outStart = 0
        while (inStart + window <= totalInputFrames && outStart + window <= totalOutputFrames) {
            for (i in 0 until window) {
                val w = hann[i]
                val inIdx = (inStart + i) * frameBytes
                val outIdx = (outStart + i) * 2

                val l = ((input[inIdx + 1].toInt() shl 8) or (input[inIdx].toInt() and 0xFF)).toShort()
                val r = ((input[inIdx + 3].toInt() shl 8) or (input[inIdx + 2].toInt() and 0xFF)).toShort()

                accum[outIdx] += l * w
                accum[outIdx + 1] += r * w
                weight[outStart + i] += w
            }
            inStart += hopIn
            outStart += hopOut
        }

        for (frame in 0 until totalOutputFrames) {
            val w = weight[frame]
            if (w > 0.001f) {
                val l = (accum[frame * 2] / w).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                val r = (accum[frame * 2 + 1] / w).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                val idx = frame * frameBytes
                output[idx] = (l and 0xFF).toByte()
                output[idx + 1] = ((l shr 8) and 0xFF).toByte()
                output[idx + 2] = (r and 0xFF).toByte()
                output[idx + 3] = ((r shr 8) and 0xFF).toByte()
            }
        }
        return output
    }

    override fun release() {
        stopRequested = true
        stopLine()
        timer?.cancel()
        timer = null
        playing = false
        paused = false
        pcmData = null
        activePcm = null
        totalFrames = 0
        activeTotalFrames = 0
        playheadFrames = 0
        _state.value = PlayerState()
        tempFiles.forEach { it.delete() }
        tempFiles.clear()
    }

    private fun startTimer() {
        timer?.cancel()
        timer = Timer()
        timer?.schedule(object : TimerTask() {
            override fun run() {
                if (playing && !paused && line?.isRunning == true) {
                val pos = ((playheadFrames * currentSpeed * 1000 / 44100).toLong()).coerceAtLeast(0L)
                    _state.value = _state.value.copy(currentPosition = pos)
                }
            }
        }, 0, 100)
    }
}
