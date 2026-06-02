package com.lrcstudio.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import com.lrcstudio.app.ui.SystemBackHandler
import com.lrcstudio.app.ui.library.LibraryScreen
import com.lrcstudio.app.ui.library.LibraryViewModel
import com.lrcstudio.app.ui.picker.rememberAudioFilePickerLauncher
import com.lrcstudio.app.ui.picker.rememberLrcFilePickerLauncher
import com.lrcstudio.app.ui.player.AudioPlayer
import com.lrcstudio.app.ui.settings.SettingsScreen
import com.lrcstudio.app.util.rememberStorageDir

private enum class NavDirection { Forward, Back, Tab }

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
    var navDirection by remember { mutableStateOf(NavDirection.Forward) }

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

    LRCStudioTheme(darkTheme = settings.isDarkTheme) {

        SystemBackHandler(
            enabled = currentScreen is Screen.Editor,
            onBack = {
                editorViewModel?.release()
                editorViewModel = null
                editorSongId = null
                navDirection = NavDirection.Back
                screenName = "library"
            }
        )

        Scaffold(
            contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
            bottomBar = {
                if (currentScreen !is Screen.Editor) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = currentScreen is Screen.Library,
                            onClick = {
                                if (currentScreen !is Screen.Library) {
                                    navDirection = NavDirection.Tab
                                    screenName = "library"
                                    editorSongId = null
                                }
                            },
                            icon = { Icon(Icons.Default.LibraryMusic, contentDescription = null) },
                            label = { Text("Library") }
                        )
                        NavigationBarItem(
                            selected = currentScreen is Screen.Settings,
                            onClick = {
                                if (currentScreen !is Screen.Settings) {
                                    navDirection = NavDirection.Tab
                                    screenName = "settings"
                                    editorSongId = null
                                }
                            },
                            icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                            label = { Text("Settings") }
                        )
                    }
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = {
                        when (navDirection) {
                            NavDirection.Tab -> {
                                fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                            }
                            NavDirection.Forward -> {
                                (slideInHorizontally(
                                    animationSpec = tween(200),
                                    initialOffsetX = { it / 8 }
                                ) + fadeIn(tween(200))) togetherWith
                                    (slideOutHorizontally(
                                        animationSpec = tween(200),
                                        targetOffsetX = { -it / 8 }
                                    ) + fadeOut(tween(200)))
                            }
                            NavDirection.Back -> {
                                (slideInHorizontally(
                                    animationSpec = tween(200),
                                    initialOffsetX = { -it / 8 }
                                ) + fadeIn(tween(200))) togetherWith
                                    (slideOutHorizontally(
                                        animationSpec = tween(200),
                                        targetOffsetX = { it / 8 }
                                    ) + fadeOut(tween(200)))
                            }
                        }
                    },
                    label = "screenTransition"
                ) { screen ->
                    when (screen) {
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
                                    navDirection = NavDirection.Forward
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
                                        navDirection = NavDirection.Back
                                        screenName = "library"
                                    },
                                    onSave = { },
                                    onImportAudioFile = launchAudioPicker,
                                    compactControls = settings.compactControls,
                                    swipeDeleteThresholdDp = settings.swipeDeleteThresholdDp
                                )
                            }
                        }

                        is Screen.Settings -> {
                            SettingsScreen(
                                settingsRepository = settingsRepository
                            )
                        }
                    }
                }
            }
        }
    }
}
