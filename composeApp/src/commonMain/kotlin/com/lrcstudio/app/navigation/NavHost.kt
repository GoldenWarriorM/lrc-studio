package com.lrcstudio.app.navigation

sealed class Screen {
    data object Library : Screen()
    data object Settings : Screen()
    data object DeveloperSettings : Screen()
    data class Editor(val songId: String) : Screen()
}
