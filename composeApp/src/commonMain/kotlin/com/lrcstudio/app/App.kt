package com.lrcstudio.app

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lrcstudio.app.data.repository.SettingsRepository
import com.lrcstudio.app.data.repository.SongRepository
import com.lrcstudio.app.domain.usecase.SyncUseCase
import com.lrcstudio.app.navigation.Screen
import com.lrcstudio.app.theme.LRCStudioTheme
import com.lrcstudio.app.ui.editor.EditorScreen
import com.lrcstudio.app.ui.editor.EditorViewModel
import com.lrcstudio.app.ui.library.LibraryScreen
import com.lrcstudio.app.ui.library.LibraryViewModel
import com.lrcstudio.app.ui.picker.rememberAudioFilePickerLauncher
import com.lrcstudio.app.ui.picker.rememberLrcFilePickerLauncher
import com.lrcstudio.app.ui.picker.rememberLrcFileSaveLauncher
import com.lrcstudio.app.ui.player.AudioPlayer
import com.lrcstudio.app.ui.settings.SettingsScreen
import com.lrcstudio.app.util.rememberStorageDir

@Composable
fun App(audioPlayer: AudioPlayer) {
    val storageDir = rememberStorageDir()
    val songRepository = remember { SongRepository(storageDir) }
    val settingsRepository = remember { SettingsRepository(storageDir) }
    val libraryViewModel = remember { LibraryViewModel(songRepository) }
    val settings by settingsRepository.settings.collectAsState()

    var screenName by rememberSaveable { mutableStateOf("library") }
    var editorSongId by rememberSaveable { mutableStateOf<String?>(null) }
    var editorViewModel by remember { mutableStateOf<EditorViewModel?>(null) }

    val currentScreen: Screen = when (screenName) {
        "editor" -> Screen.Editor(editorSongId.orEmpty())
        "settings" -> Screen.Settings
        else -> Screen.Library
    }

    if (currentScreen is Screen.Editor && editorViewModel == null) {
        val songId = (currentScreen as Screen.Editor).songId
        val song = songRepository.getById(songId)
        if (song != null) {
            val vm = remember(songId) {
                EditorViewModel(songRepository, SyncUseCase(), audioPlayer)
            }
            LaunchedEffect(songId) {
                vm.loadSong(songId)
            }
            editorViewModel = vm
        }
    }

    val launchLrcPicker = rememberLrcFilePickerLauncher { content ->
        libraryViewModel.createSongFromLrcContent(content)
    }

    val launchAudioPicker = rememberAudioFilePickerLauncher { path ->
        editorViewModel?.importAudio(path)
    }

    val saveLrcFile = rememberLrcFileSaveLauncher()

    LRCStudioTheme(darkTheme = settings.isDarkTheme) {

        Scaffold(
            contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
            bottomBar = {
                if (currentScreen is Screen.Library) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = true,
                            onClick = {},
                            icon = { Icon(Icons.Default.LibraryMusic, contentDescription = null) },
                            label = { Text("Library") }
                        )
                        NavigationBarItem(
                            selected = false,
                            onClick = {
                                screenName = "settings"
                                editorSongId = null
                            },
                            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            label = { Text("Settings") }
                        )
                    }
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                when (currentScreen) {
                    is Screen.Library -> {
                        LibraryScreen(
                            viewModel = libraryViewModel,
                            onSongClick = { song ->
                                val vm = EditorViewModel(
                                    songRepository = songRepository,
                                    syncUseCase = SyncUseCase(),
                                    audioPlayer = audioPlayer
                                )
                                vm.loadSong(song.id)
                                editorViewModel = vm
                                screenName = "editor"
                                editorSongId = song.id
                            },
                            onImportLrcFile = launchLrcPicker
                        )
                    }

                    is Screen.Editor -> {
                        val vm = editorViewModel
                        if (vm != null) {
                            EditorScreen(
                                viewModel = vm,
                                onBack = {
                                    vm.release()
                                    editorViewModel = null
                                    editorSongId = null
                                    screenName = "library"
                                },
                                onSave = { },
                                onImportAudioFile = launchAudioPicker,
                                onSaveLrcFile = saveLrcFile
                            )
                        }
                    }

                    is Screen.Settings -> {
                        SettingsScreen(
                            settingsRepository = settingsRepository,
                            onBack = {
                                screenName = "library"
                                editorSongId = null
                            }
                        )
                    }
                }
            }
        }
    }
}
