package com.lrcstudio.app.ui.picker

import java.io.File

object NativeFileDialog {
    fun showOpenDialog(
        title: String,
        extensions: List<String>,
        isDark: Boolean
    ): String? = tryZenity(title, extensions, isDark)

    fun showSaveDialog(
        title: String,
        defaultName: String,
        isDark: Boolean
    ): String? = tryZenitySave(title, defaultName, isDark)

    private fun tryZenity(title: String, extensions: List<String>, isDark: Boolean): String? {
        return try {
            val filter = if (extensions.isNotEmpty()) {
                val extList = extensions.joinToString(" ") { "*.$it" }
                listOf("--file-filter=${extensions.joinToString("/")} files | $extList")
            } else emptyList()
            val pb = ProcessBuilder(
                listOf("zenity", "--file-selection", "--title=$title") + filter
            )
            pb.environment()["GTK_THEME"] = if (isDark) "Adwaita:dark" else "Adwaita"
            val process = pb.start()
            if (process.waitFor() == 0) {
                process.inputStream.bufferedReader().readText().trim().ifEmpty { null }
            } else null
        } catch (_: Exception) { null }
    }

    private fun tryZenitySave(title: String, defaultName: String, isDark: Boolean): String? {
        return try {
            val pb = ProcessBuilder(
                "zenity", "--file-selection", "--save",
                "--title=$title", "--filename=$defaultName"
            )
            pb.environment()["GTK_THEME"] = if (isDark) "Adwaita:dark" else "Adwaita"
            val process = pb.start()
            if (process.waitFor() == 0) {
                process.inputStream.bufferedReader().readText().trim().ifEmpty { null }
            } else null
        } catch (_: Exception) { null }
    }
}
