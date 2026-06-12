package com.lrcstudio.app

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.lrcstudio.app.ui.player.AudioPlayer

fun main() = application {
    val audioPlayer: AudioPlayer = run {
        val mpv = MpvAudioPlayer()
        if (mpv.isAvailable) mpv else {
            println("main: libmpv not available, falling back to DesktopAudioPlayer")
            DesktopAudioPlayer()
        }
    }

    Window(
        onCloseRequest = {
            audioPlayer.release()
            exitApplication()
        },
        title = "LRC Studio",
        icon = painterResource("icon.png")
    ) {
        App(audioPlayer = audioPlayer)
    }
}
