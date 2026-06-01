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

    private val convertedCache = mutableMapOf<String, File>()

    private var pcmData: ByteArray? = null
    private var activePcm: ByteArray? = null
    private var totalFrames: Long = 0
    private var activeTotalFrames: Long = 0

    private var playheadFrames: Long = 0
    private var currentSpeed = 1.0f
    private var playing = false
    private var paused = false
    private var stopRequested = false
    private var stretchVersion = 0

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
        val cached = convertedCache[input.absolutePath]
        if (cached != null && cached.exists()) return cached
        return try {
            val dest = File.createTempFile("lrc_", ".wav")
            val process = ProcessBuilder(
                "ffmpeg", "-y", "-i", input.absolutePath,
                "-ar", "44100", "-ac", "2", "-sample_fmt", "s16",
                "-f", "wav", dest.absolutePath
            ).redirectErrorStream(false).start()
            val exitCode = process.waitFor()
            if (exitCode == 0 && dest.exists() && dest.length() > 0) {
                println("DesktopAudioPlayer: converted ${input.name} -> WAV via ffmpeg")
                convertedCache[input.absolutePath] = dest
                dest
            } else {
                dest.delete()
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
        stretchVersion++

        if (speed == 1.0f) {
            applyStretched(original, totalFrames, playing && !paused)
        } else {
            val version = stretchVersion
            val wasPlaying = playing && !paused
            Thread {
                try {
                    val stretched = timeStretch(original, speed)
                    synchronized(this@DesktopAudioPlayer) {
                        if (version == stretchVersion) {
                            applyStretched(stretched, stretched.size / 4L, wasPlaying)
                        }
                    }
                } catch (e: Exception) {
                }
            }.apply { isDaemon = true; start() }
        }
    }

    private fun applyStretched(data: ByteArray, frames: Long, wasPlaying: Boolean) {
        val oldActiveFrames = if (activeTotalFrames > 0) activeTotalFrames else 1
        activePcm = data
        activeTotalFrames = frames
        playheadFrames = (playheadFrames * activeTotalFrames / oldActiveFrames).coerceAtMost(activeTotalFrames)

        val posMs = ((playheadFrames * currentSpeed * 1000 / 44100).toLong()).coerceAtLeast(0L)
        _state.value = _state.value.copy(currentPosition = posMs)

        if (wasPlaying) {
            stopLine()
            stopRequested = false
            createAndStartLine(data)
        }
    }

    private fun timeStretch(input: ByteArray, speed: Float): ByteArray {
        val totalInputFrames = input.size / 4
        val totalOutputFrames = (totalInputFrames / speed).toInt()
        if (totalOutputFrames <= 0) return input

        val window = 2048
        val hopOut = window / 4
        val searchRange = 128
        val corrLen = 256

        val output = ByteArray(totalOutputFrames * 4)
        val accum = FloatArray(totalOutputFrames * 2)
        val weight = FloatArray(totalOutputFrames)

        val hann = FloatArray(window) { i ->
            0.5f * (1f - cos(2.0 * PI * i / (window - 1))).toFloat()
        }

        fun readFrame(inPos: Int): Pair<Float, Float> {
            val idx = inPos * 4
            val l = ((input[idx + 1].toInt() shl 8) or (input[idx].toInt() and 0xFF)).toShort().toFloat()
            val r = ((input[idx + 3].toInt() shl 8) or (input[idx + 2].toInt() and 0xFF)).toShort().toFloat()
            return l to r
        }

        fun addWindow(inStart: Int, outStart: Int) {
            for (i in 0 until window) {
                val outPos = outStart + i
                if (outPos >= totalOutputFrames) break
                val inPos = inStart + i
                if (inPos >= totalInputFrames) break
                val w = hann[i]
                val (l, r) = readFrame(inPos)
                accum[outPos * 2] += l * w
                accum[outPos * 2 + 1] += r * w
                weight[outPos] += w
            }
        }

        var outStart = 0
        var prevInStart = 0

        addWindow(0, 0)
        outStart += hopOut

        while (outStart + window <= totalOutputFrames) {
            val idealInStart = (outStart.toFloat() * speed).toInt()
            val low = (idealInStart - searchRange).coerceAtLeast(0)
            val high = (idealInStart + searchRange).coerceAtMost(totalInputFrames - window)

            var bestInStart = idealInStart
            var bestCorr = -1e30f
            val refStart = prevInStart + hopOut

            for (candidate in low..high) {
                var corr = 0f
                for (i in 0 until corrLen) {
                    val cPos = candidate + i
                    val rPos = refStart + i
                    if (cPos >= totalInputFrames || rPos >= totalInputFrames) break
                    val (cL, cR) = readFrame(cPos)
                    val (rL, rR) = readFrame(rPos)
                    corr += cL * rL + cR * rR
                }
                if (corr > bestCorr) {
                    bestCorr = corr
                    bestInStart = candidate
                }
            }

            addWindow(bestInStart, outStart)
            prevInStart = bestInStart
            outStart += hopOut
        }

        for (frame in 0 until totalOutputFrames) {
            val w = weight[frame]
            if (w > 0.001f) {
                val l = (accum[frame * 2] / w).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                val r = (accum[frame * 2 + 1] / w).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                val idx = frame * 4
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
