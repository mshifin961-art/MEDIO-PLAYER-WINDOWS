package com.mediop.win.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediop.win.core.model.RepeatMode
import com.mediop.win.core.theme.*
import com.mediop.win.core.util.FormatUtils
import com.mediop.win.state.AppState

@Composable
fun BottomPlayerBar(
    appState: AppState,
    onOpenNowPlaying: () -> Unit,
    onToggleQueue: () -> Unit
) {
    val engine = appState.engine
    val track by engine.currentMedia.collectAsState()
    val isPlaying by engine.isPlaying.collectAsState()
    val positionMs by engine.positionMs.collectAsState()
    val durationMs by engine.durationMs.collectAsState()
    val volume by engine.volume.collectAsState()
    val isShuffle by engine.isShuffle.collectAsState()
    val repeatMode by engine.repeatMode.collectAsState()

    val current = track ?: return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSurfaceVariant)
            .border(1.dp, GlassBorder)
    ) {
        // Seek bar along the top edge
        val total = durationMs.coerceAtLeast(1L)
        Slider(
            value = (positionMs.toFloat() / total.toFloat()).coerceIn(0f, 1f),
            onValueChange = { fraction -> engine.seekTo((fraction * total).toLong()) },
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .padding(horizontal = 12.dp),
            colors = SliderDefaults.colors(
                thumbColor = PrimaryCyan,
                activeTrackColor = PrimaryCyan,
                inactiveTrackColor = DarkSurfaceHighlight
            )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Artwork + Title/Artist
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onOpenNowPlaying)
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MediaThumbnail(
                    item = current,
                    appState = appState,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = current.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = current.artist ?: current.album ?: current.folderName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Center: Playback controls
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(onClick = { engine.toggleShuffle() }) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffle) PrimaryCyan else TextTertiary
                    )
                }
                IconButton(onClick = { engine.skipPrevious() }) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", tint = TextPrimary)
                }
                IconButton(
                    onClick = { engine.togglePlayPause() },
                    modifier = Modifier
                        .size(44.dp)
                        .background(PrimaryCyan, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = PureBlack
                    )
                }
                IconButton(onClick = { engine.skipNext() }) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = TextPrimary)
                }
                IconButton(onClick = { engine.cycleRepeatMode() }) {
                    val icon = if (repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat
                    Icon(
                        imageVector = icon,
                        contentDescription = "Repeat",
                        tint = if (repeatMode == RepeatMode.OFF) TextTertiary else PrimaryCyan
                    )
                }
            }

            // Right: Time + Volume + Queue
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${FormatUtils.formatDuration(positionMs)} / ${FormatUtils.formatDuration(durationMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.width(12.dp))
                IconButton(onClick = { engine.setVolume(if (volume > 0) 0 else 80) }) {
                    Icon(
                        imageVector = if (volume == 0) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = "Mute",
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Slider(
                    value = volume / 100f,
                    onValueChange = { engine.setVolume((it * 100).toInt()) },
                    modifier = Modifier.width(95.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = PrimaryCyan,
                        activeTrackColor = PrimaryCyan,
                        inactiveTrackColor = DarkSurfaceHighlight
                    )
                )
                IconButton(onClick = onToggleQueue) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = "Queue",
                        tint = TextPrimary
                    )
                }
                IconButton(onClick = { engine.stop() }) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Stop",
                        tint = TextSecondary
                    )
                }
            }
        }
    }
}
