package com.lrcstudio.app.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class WordTimestamp(
    val startTime: Long = 0L,
    val text: String = ""
)
