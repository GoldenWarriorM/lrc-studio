package com.lrcstudio.app.domain.usecase

/**
 * One word of a line. [joinsPrevious] marks a syllable piece of the word before
 * it, so the engine reads the whole word instead of each piece on its own.
 */
data class RomanizationWord(
    val text: String,
    val joinsPrevious: Boolean = false
)

/** Language data the romanizer needs before it can convert a script's characters. */
data class RomanizationPack(
    val id: String,
    val label: String,
    val sizeBytes: Long
)

/**
 * Result of one romanization pass.
 *
 * [lines] is aligned with the requested lines: a `null` entry means "keep the
 * original text" (already Latin, or nothing the engine can convert yet).
 * [pendingPacks] is non-empty when language data has to be downloaded first.
 */
data class RomanizationOutcome(
    val lines: List<List<String>?>,
    val pendingPacks: List<RomanizationPack> = emptyList(),
    val error: String? = null
)

/**
 * Romanizes lyric words for display only. The lyrics themselves, and every
 * export built from them, keep the original characters.
 *
 * The actual work is done by the vendored Glyphmind engine (GPL-3.0-only) in
 * the JVM source set; see `meow/nyx/glyphmind/README.md`.
 */
internal expect suspend fun romanizeLyricWords(
    storageDir: String,
    lines: List<List<RomanizationWord>>,
    downloadPacks: Boolean
): RomanizationOutcome
