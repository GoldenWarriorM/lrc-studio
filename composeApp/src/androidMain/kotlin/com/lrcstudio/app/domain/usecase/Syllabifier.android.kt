package com.lrcstudio.app.domain.usecase

internal actual fun splitWithPatterns(word: String): List<String> = PatternSyllabifier.split(word)
