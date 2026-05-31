# Замена JFileChooser на FileDialog (нативный системный диалог)

## Файлы для изменения

### 1. `composeApp/src/jvmMain/kotlin/com/lrcstudio/app/ui/picker/AudioFilePicker.jvm.kt`

```kotlin
package com.lrcstudio.app.ui.picker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.FileDialog
import java.io.File
import java.io.FilenameFilter

@Composable
actual fun rememberAudioFilePickerLauncher(
    onFilePicked: (path: String) -> Unit
): () -> Unit {
    return remember {
        {
            val dialog = FileDialog(null as java.awt.Frame?, "Select audio file", FileDialog.LOAD).apply {
                filenameFilter = FilenameFilter { _, name ->
                    name.endsWith(".mp3") || name.endsWith(".flac") || name.endsWith(".ogg") ||
                    name.endsWith(".opus") || name.endsWith(".aac") || name.endsWith(".wav") ||
                    name.endsWith(".m4a")
                }
                isVisible = true
            }
            if (dialog.file != null) {
                onFilePicked(File(dialog.directory, dialog.file).absolutePath)
            }
        }
    }
}
```

### 2. `composeApp/src/jvmMain/kotlin/com/lrcstudio/app/ui/picker/LrcFilePicker.jvm.kt`

```kotlin
package com.lrcstudio.app.ui.picker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.FileDialog
import java.io.File
import java.io.FilenameFilter

@Composable
actual fun rememberLrcFilePickerLauncher(
    onContentLoaded: (String) -> Unit
): () -> Unit {
    return remember {
        {
            val dialog = FileDialog(null as java.awt.Frame?, "Select LRC file", FileDialog.LOAD).apply {
                filenameFilter = FilenameFilter { _, name ->
                    name.endsWith(".lrc") || name.endsWith(".txt")
                }
                isVisible = true
            }
            if (dialog.file != null) {
                val file = File(dialog.directory, dialog.file)
                val content = file.readText()
                onContentLoaded(content)
            }
        }
    }
}
```

### 3. `composeApp/src/jvmMain/kotlin/com/lrcstudio/app/ui/picker/LrcFileSaver.jvm.kt`

```kotlin
package com.lrcstudio.app.ui.picker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.FileDialog
import java.io.File

@Composable
actual fun rememberLrcFileSaveLauncher(): (content: String) -> Unit {
    return remember {
        { content: String ->
            val dialog = FileDialog(null as java.awt.Frame?, "Save LRC file", FileDialog.SAVE).apply {
                file = "lyrics.lrc"
                isVisible = true
            }
            if (dialog.file != null) {
                val file = if (dialog.file!!.endsWith(".lrc")) {
                    File(dialog.directory, dialog.file)
                } else {
                    File(dialog.directory, dialog.file + ".lrc")
                }
                file.writeText(content, Charsets.UTF_8)
            }
        }
    }
}
```

## Что меняется

- `javax.swing.JFileChooser` + `FileNameExtensionFilter` → `java.awt.FileDialog` + `FilenameFilter`
- На Linux показывает нативный GTK-диалог (GNOME/ KDE), а не Swing-стиль
- Родительское окно: `null` (как и было с JFileChooser)
- Режимы: `FileDialog.LOAD` для открытия, `FileDialog.SAVE` для сохранения
- В Save-режиме добавляется `.lrc` если пользователь его не указал
