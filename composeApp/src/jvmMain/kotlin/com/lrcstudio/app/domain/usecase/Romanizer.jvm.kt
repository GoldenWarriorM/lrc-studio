package com.lrcstudio.app.domain.usecase

internal actual suspend fun romanizeLyricWords(
    storageDir: String,
    lines: List<List<String>>,
    downloadPacks: Boolean
): RomanizationOutcome = RomanizerEngine.romanize(storageDir, lines, downloadPacks)
