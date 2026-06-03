package com.lrcstudio.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
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
import com.lrcstudio.app.ui.settings.DeveloperSettingsScreen
import com.lrcstudio.app.util.rememberStorageDir

private enum class NavDirection { Forward, Back, Tab, Immediate }

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
        "developer" -> Screen.DeveloperSettings
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

    LaunchedEffect(currentScreen) {
        val duration = if (settings.slowAnimations) 600L else 200L
        delay(duration)
        navDirection = NavDirection.Tab
    }

    LRCStudioTheme(darkTheme = settings.isDarkTheme) {

        SystemBackHandler(
            enabled = currentScreen is Screen.Editor || currentScreen is Screen.DeveloperSettings,
            onBack = {
                when (currentScreen) {
                    is Screen.Editor -> {
                        editorSongId = null
                        navDirection = if (navDirection == NavDirection.Forward) NavDirection.Immediate else NavDirection.Back
                        screenName = "library"
                    }
                    is Screen.DeveloperSettings -> {
                        navDirection = NavDirection.Back
                        screenName = "settings"
                    }
                    else -> {}
                }
            }
        )

        Scaffold(
            contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
            bottomBar = {}
        ) { padding ->
            val showBottomBar = currentScreen !is Screen.Editor && currentScreen !is Screen.DeveloperSettings
            val animDuration = if (settings.slowAnimations) 600 else 200

            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = {
                        val duration = if (settings.slowAnimations) 600 else 200
                        when (navDirection) {
                            NavDirection.Tab -> {
                                fadeIn(tween(duration)) togetherWith fadeOut(tween(duration))
                            }
                            NavDirection.Forward -> {
                                (slideInHorizontally(
                                    animationSpec = tween(duration),
                                    initialOffsetX = { it / 8 }
                                ) + fadeIn(tween(duration))) togetherWith
                                    (slideOutHorizontally(
                                        animationSpec = tween(duration),
                                        targetOffsetX = { -it / 8 }
                                    ) + fadeOut(tween(duration)))
                            }
                            NavDirection.Back -> {
                                (slideInHorizontally(
                                    animationSpec = tween(duration),
                                    initialOffsetX = { -it / 8 }
                                ) + fadeIn(tween(duration))) togetherWith
                                    (slideOutHorizontally(
                                        animationSpec = tween(duration),
                                        targetOffsetX = { it / 8 }
                                    ) + fadeOut(tween(duration)))
                            }
                            NavDirection.Immediate -> {
                                fadeIn(tween(0)) togetherWith fadeOut(tween(0))
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
                                DisposableEffect(Unit) {
                                    onDispose {
                                        vm.release()
                                        editorViewModel = null
                                    }
                                }
                                EditorScreen(
                                    viewModel = vm,
                                    onBack = {
                                        editorSongId = null
                                        navDirection = if (navDirection == NavDirection.Forward) NavDirection.Immediate else NavDirection.Back
                                        screenName = "library"
                                    },
                                    onSave = { },
                                    onImportAudioFile = launchAudioPicker,
                                    compactControls = settings.compactControls,
                                    swipeDeleteThresholdDp = settings.swipeDeleteThresholdDp,
                                    swipeGesturesEnabled = settings.swipeGesturesEnabled,
                                    showSnapButton = settings.showSnapButton,
                                    showClearDeleteButton = settings.showClearDeleteButton,
                                    swipeInstantDelete = settings.swipeInstantDelete,
                                    showDebugBorders = settings.showDebugBorders
                                )
                            }
                        }

                        is Screen.Settings -> {
                            SettingsScreen(
                                settingsRepository = settingsRepository,
                                onNavigateToDeveloper = {
                                    navDirection = NavDirection.Forward
                                    screenName = "developer"
                                }
                            )
                        }

                        is Screen.DeveloperSettings -> {
                            DeveloperSettingsScreen(
                                settingsRepository = settingsRepository,
                                onBack = {
                                    navDirection = NavDirection.Back
                                    screenName = "settings"
                                }
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = showBottomBar,
                    enter = slideInVertically(animationSpec = tween(animDuration), initialOffsetY = { it }) + fadeIn(tween(animDuration)),
                    exit = slideOutVertically(animationSpec = tween(animDuration), targetOffsetY = { it }) + fadeOut(tween(animDuration)),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
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
        }
    }
}
