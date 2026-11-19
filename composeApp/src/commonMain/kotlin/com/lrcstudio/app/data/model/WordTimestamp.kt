package com.lrcstudio.app.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/** Kind of vocal a lyric fragment belongs to (enhanced LRC `bg:` / `h1:` / `ch:`). */
@Serializable
enum class VocalRole {
    MAIN,
    BACKGROUND,
    HARMONY,
    CHORUS
}

@Immutable
@Serializable
data class WordTimestamp(
    val startTime: Long = 0L,
    val text: String = "",
    /** Explicit end, written as a trailing empty `<>` tag; 0 means "unknown". */
    val endTime: Long = 0L,
    /** Main vocal by default; background / harmony / chorus fragments are tagged. */
    val role: VocalRole = VocalRole.MAIN,
    /** True for syllable pieces: no space is written between them and the previous word. */
    val joinsPrevious: Boolean = false
)
