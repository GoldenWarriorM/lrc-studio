package com.lrcstudio.app.ui.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
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
    onSwitchTrack: () -> Unit = {},
    currentSpeed: Float = 1f,
    onSpeedChange: (Float) -> Unit = {},
    onSpeedClick: () -> Unit = {},
    compactControls: Boolean = false,
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
        targetValue = if (isPrevPressed) 60.dp else 48.dp,
        animationSpec = tween(durationMillis = 100),
    )
    val playWidth by animateDpAsState(
        targetValue = if (isPlayPressed) 72.dp else 52.dp,
        animationSpec = tween(durationMillis = 100),
    )
    val nextWidth by animateDpAsState(
        targetValue = if (isNextPressed) 60.dp else 48.dp,
        animationSpec = tween(durationMillis = 100),
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onSwitchTrack) {
                    Icon(Icons.Default.LibraryMusic, contentDescription = "Switch track")
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    FilledIconButton(
                        onClick = { onSeek((playerState.currentPosition - 5000).coerceAtLeast(0)) },
                        shape = RoundedCornerShape(22.dp),
                        interactionSource = prevInteractionSource,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = primary.copy(alpha = 0.1f),
                            contentColor = primary,
                        ),
                        modifier = Modifier
                            .width(prevWidth)
                            .height(44.dp),
                    ) {
                        Icon(
                            Icons.Default.SkipPrevious,
                            contentDescription = "-5s",
                            modifier = Modifier.size(22.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    FilledIconButton(
                        onClick = onPlayPause,
                        shape = RoundedCornerShape(22.dp),
                        interactionSource = playInteractionSource,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = primary,
                            contentColor = onPrimary,
                        ),
                        modifier = Modifier
                            .width(playWidth)
                            .height(44.dp),
                    ) {
                        Icon(
                            imageVector = if (playerState.state == PlaybackState.PLAYING)
                                Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playerState.state == PlaybackState.PLAYING)
                                "Pause" else "Play",
                            modifier = Modifier.size(26.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    FilledIconButton(
                        onClick = { onSeek((playerState.currentPosition + 5000).coerceAtMost(playerState.duration)) },
                        shape = RoundedCornerShape(22.dp),
                        interactionSource = nextInteractionSource,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = primary.copy(alpha = 0.1f),
                            contentColor = primary,
                        ),
                        modifier = Modifier
                            .width(nextWidth)
                            .height(44.dp),
                    ) {
                        Icon(
                            Icons.Default.SkipNext,
                            contentDescription = "+5s",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                if (compactControls) {
                    Column(
                        modifier = Modifier
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
                        modifier = Modifier
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
                        .padding(horizontal = 8.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = primary,
                        activeTrackColor = primary
                    )
                )

                Text(
                    text = formatDuration(playerState.duration),
                    style = MaterialTheme.typography.labelSmall,
                    color = onSurfaceVariant
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
