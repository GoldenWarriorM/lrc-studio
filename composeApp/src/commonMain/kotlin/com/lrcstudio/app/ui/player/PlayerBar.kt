package com.lrcstudio.app.ui.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@Composable
fun PlayerBar(
    playerState: PlayerState,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    currentSpeed: Float = 1f,
    onSpeedChange: (Float) -> Unit = {},
    onSpeedClick: () -> Unit = {},
    compactControls: Boolean = false,
    speedBelowSeek: Boolean = false,
    modifier: Modifier = Modifier
) {
    val speeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f)

    val playInteractionSource = remember { MutableInteractionSource() }
    val prevInteractionSource = remember { MutableInteractionSource() }
    val nextInteractionSource = remember { MutableInteractionSource() }

    val isPlayPressed by playInteractionSource.collectIsPressedAsState()
    val isPrevPressed by prevInteractionSource.collectIsPressedAsState()
    val isNextPressed by nextInteractionSource.collectIsPressedAsState()

    val prevWidth by animateDpAsState(
        targetValue = when {
            isPlayPressed -> 52.dp
            isPrevPressed -> 80.dp
            else -> 66.dp
        },
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
    )
    val nextWidth by animateDpAsState(
        targetValue = when {
            isPlayPressed -> 52.dp
            isNextPressed -> 80.dp
            else -> 66.dp
        },
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
    )

    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = surfaceVariant.copy(alpha = 0.7f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(prevWidth)
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(primary.copy(alpha = 0.1f))
                            .clickable(
                                interactionSource = prevInteractionSource,
                                indication = null,
                                onClick = { onSeek((playerState.currentPosition - 5000).coerceAtLeast(0)) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            contentDescription = "-5s",
                            tint = primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(primary)
                            .clickable(
                                interactionSource = playInteractionSource,
                                indication = null,
                                onClick = onPlayPause,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = when (playerState.state) {
                                PlaybackState.PLAYING -> Icons.Default.Pause
                                PlaybackState.FINISHED -> Icons.Default.Replay
                                else -> Icons.Default.PlayArrow
                            },
                            contentDescription = when (playerState.state) {
                                PlaybackState.PLAYING -> "Pause"
                                PlaybackState.FINISHED -> "Restart"
                                else -> "Play"
                            },
                            tint = onPrimary,
                            modifier = Modifier.size(26.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .width(nextWidth)
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(primary.copy(alpha = 0.1f))
                            .clickable(
                                interactionSource = nextInteractionSource,
                                indication = null,
                                onClick = { onSeek((playerState.currentPosition + 5000).coerceAtMost(playerState.duration)) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.SkipNext,
                            contentDescription = "+5s",
                            tint = primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                if (!speedBelowSeek) {
                    SpeedControl(
                        currentSpeed = currentSpeed,
                        onSpeedChange = onSpeedChange,
                        onSpeedClick = onSpeedClick,
                        compactControls = compactControls,
                        primary = primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDuration(playerState.currentPosition),
                    style = MaterialTheme.typography.labelSmall,
                    color = onSurfaceVariant
                )

                Slider(
                    value = playerState.currentPosition.toFloat(),
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..playerState.duration.coerceAtLeast(1).toFloat(),
                    modifier = Modifier
                        .weight(1f)
            .padding(horizontal = 4.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = primary,
                        activeTrackColor = primary,
                        inactiveTrackColor = surfaceVariant
                    )
                )

                Text(
                    text = formatDuration(playerState.duration),
                    style = MaterialTheme.typography.labelSmall,
                    color = onSurfaceVariant
                )
            }

            if (speedBelowSeek) {
                Spacer(modifier = Modifier.height(8.dp))
                SpeedPresets(
                    currentSpeed = currentSpeed,
                    onSpeedChange = onSpeedChange,
                    onSpeedClick = onSpeedClick,
                    primary = primary
                )
            }
        }
    }
}

@Composable
private fun SpeedPresets(
    currentSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    onSpeedClick: () -> Unit,
    primary: androidx.compose.ui.graphics.Color
) {
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val presets = listOf(0.25f, 0.5f, 1f, 1.5f, 2f)
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(scrollState)
                .pointerInput(scrollState) {
                    awaitEachGesture {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        if (event.type == PointerEventType.Scroll) {
                            val delta = event.changes.firstOrNull()?.scrollDelta ?: return@awaitEachGesture
                            if (delta.y != 0f) {
                                scrollState.dispatchRawDelta(-delta.y * 10f)
                            }
                        }
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            presets.forEach { preset ->
                val isActive = currentSpeed == preset
                SuggestionChip(
                    onClick = { onSpeedChange(preset) },
                    label = {
                        Text(
                            text = "%.2gx".format(preset),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp
                            ),
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = if (isActive) primary.copy(alpha = 0.2f) else primary.copy(alpha = 0.08f),
                        labelColor = if (isActive) primary else onSurfaceVariant
                    ),
                    border = if (isActive) SuggestionChipDefaults.suggestionChipBorder(
                        borderColor = primary,
                        enabled = true,
                        borderWidth = 1.dp
                    ) else null
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(primary.copy(alpha = 0.1f))
                .clickable { onSpeedClick() }
                .heightIn(min = 32.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = "%.2fx".format(currentSpeed),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = primary,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
fun SpeedControl(
    currentSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    onSpeedClick: () -> Unit,
    compactControls: Boolean,
    primary: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val speeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f)

    if (compactControls) {
        Column(
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(primary.copy(alpha = 0.1f))
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "%.2fx".format(currentSpeed),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = primary,
                modifier = Modifier
                    .clickable { onSpeedClick() }
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = {
                        val idx = speeds.indexOf(currentSpeed)
                        if (idx > 0) onSpeedChange(speeds[idx - 1])
                    },
                    modifier = Modifier.size(28.dp),
                    enabled = speeds.indexOf(currentSpeed) > 0
                ) {
                    Icon(
                        Icons.Default.Remove, contentDescription = "Decrease speed",
                        modifier = Modifier.size(14.dp),
                        tint = primary
                    )
                }
                IconButton(
                    onClick = {
                        val idx = speeds.indexOf(currentSpeed)
                        if (idx < speeds.lastIndex) onSpeedChange(speeds[idx + 1])
                    },
                    modifier = Modifier.size(28.dp),
                    enabled = speeds.indexOf(currentSpeed) < speeds.lastIndex
                ) {
                    Icon(
                        Icons.Default.Add, contentDescription = "Increase speed",
                        modifier = Modifier.size(14.dp),
                        tint = primary
                    )
                }
            }
        }
    } else {
        Row(
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(primary.copy(alpha = 0.1f)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    val idx = speeds.indexOf(currentSpeed)
                    if (idx > 0) onSpeedChange(speeds[idx - 1])
                },
                modifier = Modifier.size(32.dp),
                enabled = speeds.indexOf(currentSpeed) > 0
            ) {
                Icon(
                    Icons.Default.Remove, contentDescription = "Decrease speed",
                    modifier = Modifier.size(16.dp),
                    tint = primary
                )
            }

            Box(
                modifier = Modifier
                    .height(32.dp)
                    .wrapContentWidth()
                    .clickable { onSpeedClick() }
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "%.2fx".format(currentSpeed),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = primary
                )
            }

            IconButton(
                onClick = {
                    val idx = speeds.indexOf(currentSpeed)
                    if (idx < speeds.lastIndex) onSpeedChange(speeds[idx + 1])
                },
                modifier = Modifier.size(32.dp),
                enabled = speeds.indexOf(currentSpeed) < speeds.lastIndex
            ) {
                Icon(
                    Icons.Default.Add, contentDescription = "Increase speed",
                    modifier = Modifier.size(16.dp),
                    tint = primary
                )
            }
        }
    }
}

fun formatDuration(millis: Long): String {
    if (millis < 0) return "00:00"
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val minStr = minutes.toString().padStart(2, '0')
    val secStr = seconds.toString().padStart(2, '0')
    return "$minStr:$secStr"
}
