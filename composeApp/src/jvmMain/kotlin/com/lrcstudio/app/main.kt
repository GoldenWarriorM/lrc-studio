package com.lrcstudio.app

import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    val audioPlayer = DesktopAudioPlayer()

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
