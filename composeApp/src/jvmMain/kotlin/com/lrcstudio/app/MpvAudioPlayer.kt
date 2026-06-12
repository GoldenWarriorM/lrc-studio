package com.lrcstudio.app

import com.lrcstudio.app.ui.player.AudioPlayer
import com.lrcstudio.app.ui.player.PlaybackState
import com.lrcstudio.app.ui.player.PlayerState
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Timer
import java.util.TimerTask

private interface CLib : Library {
    fun setlocale(category: Int, locale: String): Pointer

    companion object {
        fun forceCNumeric() {
            try {
                val c = Native.load("c", CLib::class.java) as CLib
                c.setlocale(1, "C") // LC_NUMERIC = 1 on Linux
            } catch (_: Exception) {}
        }
    }
}

private interface MpvLib : Library {
    fun mpv_create(): Pointer
    fun mpv_initialize(ctx: Pointer): Int
    fun mpv_command(ctx: Pointer, args: Array<String>): Int
    fun mpv_set_option_string(ctx: Pointer, name: String, value: String): Int
    fun mpv_set_property_string(ctx: Pointer, name: String, value: String): Int
    fun mpv_get_property_string(ctx: Pointer, name: String): Pointer
    fun mpv_observe_property(ctx: Pointer, replyUserdata: Long, name: String, format: Int): Int
    fun mpv_wait_event(ctx: Pointer, timeout: Double): Pointer
    fun mpv_terminate_destroy(ctx: Pointer)
    fun mpv_free(data: Pointer)

    companion object {
        private var instance: MpvLib? = null
        fun get(): MpvLib? {
            if (instance == null) {
                try {
                    instance = Native.load("mpv", MpvLib::class.java) as MpvLib
                } catch (e: UnsatisfiedLinkError) {
                    System.err.println("MpvAudioPlayer: libmpv not found — ${e.message}")
                    return null
                }
            }
            return instance
        }
    }
}

class MpvAudioPlayer : AudioPlayer {
    private val _state = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = _state

    private var mpv: Pointer? = null
    private var eventThread: Thread? = null
    private var timer: Timer? = null
    private var running = false

    val isAvailable: Boolean

    init {
        val ctx = createMpv()
        if (ctx != null) {
            mpv = ctx
            isAvailable = true
            running = true
            startEventLoop()
        } else {
            isAvailable = false
        }
    }

    private fun createMpv(): Pointer? {
        CLib.forceCNumeric()
        val lib = MpvLib.get() ?: return null
        val ctx = lib.mpv_create()
        if (ctx == null || ctx == Pointer.NULL) {
            System.err.println("MpvAudioPlayer: mpv_create failed")
            return null
        }

        lib.mpv_set_option_string(ctx, "vo", "null")
        lib.mpv_set_option_string(ctx, "video", "no")
        lib.mpv_set_option_string(ctx, "terminal", "no")
        lib.mpv_set_option_string(ctx, "msg-level", "all=no")
        lib.mpv_set_option_string(ctx, "audio-exclusive", "no")
        lib.mpv_set_option_string(ctx, "audio-buffer", "0.5")
        lib.mpv_set_option_string(ctx, "pause", "yes")

        if (lib.mpv_initialize(ctx) < 0) {
            System.err.println("MpvAudioPlayer: mpv_initialize failed")
            lib.mpv_terminate_destroy(ctx)
            return null
        }

        return ctx
    }

    private fun lib(): MpvLib = MpvLib.get()!!

    override fun load(path: String) {
        val ctx = mpv ?: return
        releasePlayback()

        lib().mpv_command(ctx, arrayOf("loadfile", path, "replace"))
        lib().mpv_set_property_string(ctx, "pause", "yes")

        _state.value = PlayerState(
            state = PlaybackState.IDLE,
            audioPath = path,
            duration = 0L
        )

        startTimer()
    }

    override fun play() {
        val ctx = mpv ?: return
        val current = _state.value.state
        if (current == PlaybackState.PLAYING) return

        if (current == PlaybackState.FINISHED) {
            lib().mpv_set_property_string(ctx, "time-pos", "0")
        }

        lib().mpv_set_property_string(ctx, "pause", "no")
        _state.value = _state.value.copy(state = PlaybackState.PLAYING)
    }

    override fun pause() {
        val ctx = mpv ?: return
        lib().mpv_set_property_string(ctx, "pause", "yes")
        _state.value = _state.value.copy(state = PlaybackState.PAUSED)
    }

    override fun seekTo(positionMs: Long) {
        val ctx = mpv ?: return
        lib().mpv_set_property_string(ctx, "time-pos", (positionMs / 1000.0).toString())
        _state.value = _state.value.copy(currentPosition = positionMs)
    }

    override fun setSpeed(speed: Float) {
        val ctx = mpv ?: return
        lib().mpv_set_property_string(ctx, "speed", speed.toString())
    }

    override fun release() {
        running = false
        eventThread?.interrupt()
        eventThread = null
        timer?.cancel()
        timer = null
        mpv?.let { ctx ->
            try { lib().mpv_terminate_destroy(ctx) } catch (_: Exception) {}
        }
        mpv = null
        _state.value = PlayerState()
    }

    private fun releasePlayback() {
        timer?.cancel()
        timer = null
        _state.value = PlayerState()
    }

    private fun startTimer() {
        timer?.cancel()
        timer = Timer()
        timer?.schedule(object : TimerTask() {
            override fun run() {
                val ctx = mpv ?: return
                val lib = lib()

                val posPtr = lib.mpv_get_property_string(ctx, "time-pos")
                var pos = _state.value.currentPosition
                if (posPtr != null && posPtr != Pointer.NULL) {
                    try {
                        pos = (posPtr.getString(0).toDouble() * 1000).toLong().coerceAtLeast(0)
                    } catch (_: Exception) {}
                    lib.mpv_free(posPtr)
                }

                val s = _state.value
                if (pos != s.currentPosition) {
                    _state.value = s.copy(currentPosition = pos)
                }

                if (s.duration == 0L) {
                    val durPtr = lib.mpv_get_property_string(ctx, "duration")
                    if (durPtr != null && durPtr != Pointer.NULL) {
                        try {
                            val dur = (durPtr.getString(0).toDouble() * 1000).toLong().coerceAtLeast(0)
                            if (dur > 0) {
                                _state.value = _state.value.copy(duration = dur)
                            }
                        } catch (_: Exception) {}
                        lib.mpv_free(durPtr)
                    }
                }

                if (s.state == PlaybackState.PLAYING) {
                    val pausePtr = lib.mpv_get_property_string(ctx, "pause")
                    var isPaused = true
                    if (pausePtr != null && pausePtr != Pointer.NULL) {
                        try {
                            isPaused = pausePtr.getString(0) == "yes"
                        } catch (_: Exception) {}
                        lib.mpv_free(pausePtr)
                    }

                    if (isPaused && mpv != null) {
                        _state.value = _state.value.copy(state = PlaybackState.FINISHED)
                    }
                }
            }
        }, 0, 100)
    }

    private fun startEventLoop() {
        eventThread = Thread {
            val lib = MpvLib.get() ?: return@Thread
            val ctx = mpv ?: return@Thread

            lib.mpv_observe_property(ctx, 1, "eof-reached", 3)
            lib.mpv_observe_property(ctx, 2, "duration", 5)
            lib.mpv_observe_property(ctx, 3, "pause", 3)

            while (running && mpv != null) {
                val eventPtr = lib.mpv_wait_event(ctx, 0.3)
                if (eventPtr == null || eventPtr == Pointer.NULL) continue

                val eventId = eventPtr.getInt(0)

                when (eventId) {
                    7 -> {
                        if (_state.value.state == PlaybackState.PLAYING) {
                            _state.value = _state.value.copy(state = PlaybackState.FINISHED)
                        }
                    }
                    8 -> {
                        val durPtr = lib.mpv_get_property_string(ctx, "duration")
                        if (durPtr != null && durPtr != Pointer.NULL) {
                            try {
                                val dur = (durPtr.getString(0).toDouble() * 1000).toLong().coerceAtLeast(0)
                                if (dur > 0) {
                                    _state.value = _state.value.copy(duration = dur)
                                }
                            } catch (_: Exception) {}
                            lib.mpv_free(durPtr)
                        }
                    }
                }
            }
        }.apply {
            isDaemon = true
            name = "mpv-events"
            start()
        }
    }
}
