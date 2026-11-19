// SPDX-License-Identifier: GPL-3.0-only
// Metrolist Project (C) 2026, contributors including l6t9. See NOTICE.md.
package meow.nyx.glyphmind

data class WordTimestamp(
    val text: String,
    val startTime: Double,
    val endTime: Double,
    val hasTrailingSpace: Boolean = true,
    val isBackground: Boolean = false,
    val isLineTimed: Boolean = false,
)
