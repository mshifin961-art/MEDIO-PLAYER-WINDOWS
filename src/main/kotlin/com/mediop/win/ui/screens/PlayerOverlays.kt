package com.mediop.win.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediop.win.core.model.AspectRatioMode
import com.mediop.win.core.model.RepeatMode
import com.mediop.win.core.theme.*
import com.mediop.win.core.util.FormatUtils
import com.mediop.win.state.AppState
import com.mediop.win.ui.components.MediaThumbnail

@Composable
fun VideoPlayerOverlay(
    appState: AppState,
    onPickSubtitleFile: () -> Unit,
    onToggleFullscreen: () -> Unit
) {
    val engine = appState.engine
    val current by engine.currentMedia.collectAsState()
    val isPlaying by engine.isPlaying.collectAsState()
    val positionMs by engine.positionMs.collectAsState()
    val durationMs by engine.durationMs.collectAsState()
    val volume by engine.volume.collectAsState()
    val speed by engine.playbackSpeed.collectAsState()
    val aspectRatio by engine.aspectRatio.collectAsState()
    val audioTracks by engine.audioTracks.collectAsState()
    val subtitleTracks by engine.subtitleTracks.collectAsState()
    val error by engine.playbackError.collectAsState()

    var showSpeedMenu by remember { mutableStateOf(false) }
    var showAudioMenu by remember { mutableStateOf(false) }
    var showSubMenu by remember { mutableStateOf(false) }
    var showAspectMenu by remember { mutableStateOf(false) }

    val videoScale = when (aspectRatio) {
        AspectRatioMode.FILL -> ContentScale.Crop
        else -> ContentScale.Fit
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
    ) {
        val frame = engine.currentVideoFrame
        if (frame != null) {
            Image(
                bitmap = frame,
                contentDescription = current?.title ?: "Video",
                contentScale = videoScale,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { engine.togglePlayPause() }
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (error != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = AccentCoral, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(error ?: "Playback Error", color = TextPrimary)
                    }
                } else {
                    CircularProgressIndicator(color = PrimaryCyan)
                }
            }
        }

        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(PlayerOverlayBackground.copy(alpha = 0.7f))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                if (appState.isFullscreen) onToggleFullscreen()
                engine.exitVideoPlayer()
            }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = current?.title ?: "Video Player",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Speed menu
            Box {
                TextButton(onClick = { showSpeedMenu = true }) {
                    Text("${speed}x", color = PrimaryCyan, fontWeight = FontWeight.Bold)
                }
                DropdownMenu(expanded = showSpeedMenu, onDismissRequest = { showSpeedMenu = false }) {
                    listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f).forEach { s ->
                        DropdownMenuItem(
                            text = { Text("${s}x") },
                            onClick = {
                                engine.setSpeed(s)
                                showSpeedMenu = false
                            }
                        )
                    }
                }
            }

            // Aspect Ratio
            Box {
                IconButton(onClick = { showAspectMenu = true }) {
                    Icon(Icons.Rounded.AspectRatio, contentDescription = "Aspect Ratio", tint = TextPrimary)
                }
                DropdownMenu(expanded = showAspectMenu, onDismissRequest = { showAspectMenu = false }) {
                    AspectRatioMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.label) },
                            onClick = {
                                engine.setAspectRatio(mode)
                                showAspectMenu = false
                            }
                        )
                    }
                }
            }

            // Audio Tracks
            Box {
                IconButton(onClick = { showAudioMenu = true }) {
                    Icon(Icons.Rounded.Audiotrack, contentDescription = "Audio Track", tint = TextPrimary)
                }
                DropdownMenu(expanded = showAudioMenu, onDismissRequest = { showAudioMenu = false }) {
                    if (audioTracks.isEmpty()) {
                        DropdownMenuItem(text = { Text("No audio tracks") }, onClick = { showAudioMenu = false })
                    } else {
                        audioTracks.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(if (t.isSelected) "✓ ${t.name}" else t.name) },
                                onClick = {
                                    engine.selectAudioTrack(t.id)
                                    showAudioMenu = false
                                }
                            )
                        }
                    }
                }
            }

            // Subtitles
            Box {
                IconButton(onClick = { showSubMenu = true }) {
                    Icon(Icons.Rounded.Subtitles, contentDescription = "Subtitles", tint = TextPrimary)
                }
                DropdownMenu(expanded = showSubMenu, onDismissRequest = { showSubMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Load Subtitle File (.srt/.vtt/.ass)...") },
                        onClick = {
                            showSubMenu = false
                            onPickSubtitleFile()
                        }
                    )
                    subtitleTracks.forEach { t ->
                        DropdownMenuItem(
                            text = { Text(if (t.isSelected) "✓ ${t.name}" else t.name) },
                            onClick = {
                                engine.selectSubtitleTrack(t.id)
                                showSubMenu = false
                            }
                        )
                    }
                }
            }

            IconButton(onClick = onToggleFullscreen) {
                Icon(
                    imageVector = if (appState.isFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                    contentDescription = "Fullscreen",
                    tint = TextPrimary
                )
            }
        }

        // Bottom Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(PlayerOverlayBackground.copy(alpha = 0.78f))
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            val total = durationMs.coerceAtLeast(1L)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(FormatUtils.formatDuration(positionMs), color = TextPrimary, style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = (positionMs.toFloat() / total.toFloat()).coerceIn(0f, 1f),
                    onValueChange = { engine.seekTo((it * total).toLong()) },
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = PrimaryCyan,
                        activeTrackColor = PrimaryCyan,
                        inactiveTrackColor = DarkSurfaceHighlight
                    )
                )
                Text(FormatUtils.formatDuration(durationMs), color = TextPrimary, style = MaterialTheme.typography.labelSmall)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { engine.skipPrevious() }) {
                        Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", tint = TextPrimary)
                    }
                    IconButton(onClick = { engine.seekBy(-10_000L) }) {
                        Icon(Icons.Rounded.Replay10, contentDescription = "Back 10s", tint = TextPrimary)
                    }
                    IconButton(
                        onClick = { engine.togglePlayPause() },
                        modifier = Modifier.size(48.dp).background(PrimaryCyan, CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = PureBlack
                        )
                    }
                    IconButton(onClick = { engine.seekBy(10_000L) }) {
                        Icon(Icons.Rounded.Forward10, contentDescription = "Forward 10s", tint = TextPrimary)
                    }
                    IconButton(onClick = { engine.skipNext() }) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = TextPrimary)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { engine.setVolume(if (volume > 0) 0 else 80) }) {
                        Icon(
                            imageVector = if (volume == 0) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                            contentDescription = "Mute",
                            tint = TextPrimary
                        )
                    }
                    Slider(
                        value = volume / 100f,
                        onValueChange = { engine.setVolume((it * 100).toInt()) },
                        modifier = Modifier.width(120.dp),
                        colors = SliderDefaults.colors(thumbColor = PrimaryCyan, activeTrackColor = PrimaryCyan)
                    )
                }
            }
        }
    }
}

@Composable
fun AudioPlayerOverlay(
    appState: AppState,
    onClose: () -> Unit
) {
    val engine = appState.engine
    val track by engine.currentMedia.collectAsState()
    val isPlaying by engine.isPlaying.collectAsState()
    val pos by engine.positionMs.collectAsState()
    val dur by engine.durationMs.collectAsState()
    val isShuffle by engine.isShuffle.collectAsState()
    val repeatMode by engine.repeatMode.collectAsState()
    val current = track ?: return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(32.dp)
    ) {
        IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Minimize", tint = TextPrimary, modifier = Modifier.size(32.dp))
        }

        Column(
            modifier = Modifier.align(Alignment.Center).widthIn(max = 520.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MediaThumbnail(
                item = current,
                appState = appState,
                modifier = Modifier.size(280.dp).clip(RoundedCornerShape(22.dp))
            )
            Spacer(Modifier.height(24.dp))
            Text(current.title, style = MaterialTheme.typography.headlineMedium, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(
                listOfNotNull(current.artist ?: "Unknown Artist", current.album).joinToString(" • "),
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(24.dp))

            val total = dur.coerceAtLeast(1L)
            Slider(
                value = (pos.toFloat() / total.toFloat()).coerceIn(0f, 1f),
                onValueChange = { engine.seekTo((it * total).toLong()) },
                colors = SliderDefaults.colors(thumbColor = PrimaryCyan, activeTrackColor = PrimaryCyan)
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(FormatUtils.formatDuration(pos), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                Text(FormatUtils.formatDuration(dur), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
            Spacer(Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                IconButton(onClick = { engine.toggleShuffle() }) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle", tint = if (isShuffle) PrimaryCyan else TextTertiary)
                }
                IconButton(onClick = { engine.skipPrevious() }) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", tint = TextPrimary, modifier = Modifier.size(34.dp))
                }
                IconButton(
                    onClick = { engine.togglePlayPause() },
                    modifier = Modifier.size(64.dp).background(PrimaryCyan, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = PureBlack,
                        modifier = Modifier.size(34.dp)
                    )
                }
                IconButton(onClick = { engine.skipNext() }) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = TextPrimary, modifier = Modifier.size(34.dp))
                }
                IconButton(onClick = { engine.cycleRepeatMode() }) {
                    val icon = if (repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat
                    Icon(icon, contentDescription = "Repeat", tint = if (repeatMode == RepeatMode.OFF) TextTertiary else PrimaryCyan)
                }
            }
        }
    }
}

@Composable
fun QueueSidePanel(appState: AppState, onClose: () -> Unit) {
    val queue by appState.engine.queue.collectAsState()
    val current by appState.engine.currentMedia.collectAsState()

    Column(
        modifier = Modifier
            .width(320.dp)
            .fillMaxHeight()
            .background(DarkSurface)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Playback Queue (${queue.size})", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = "Close Queue", tint = TextSecondary)
            }
        }
        HorizontalDivider(color = GlassBorder, modifier = Modifier.padding(vertical = 8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(queue) { _, item ->
                val active = current?.filePath == item.filePath
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { appState.playItem(item, queue) },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (active) PrimaryCyan else GlassBorder),
                    colors = CardDefaults.cardColors(containerColor = if (active) DarkSurfaceHighlight else DarkSurfaceVariant)
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.title, color = if (active) PrimaryCyan else TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(item.artist ?: item.folderName, color = TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                        if (item.durationMs > 0) {
                            Text(FormatUtils.formatDuration(item.durationMs), color = TextTertiary, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MiniPlayerContent(
    appState: AppState,
    onExpand: () -> Unit
) {
    val engine = appState.engine
    val track by engine.currentMedia.collectAsState()
    val isPlaying by engine.isPlaying.collectAsState()
    val pos by engine.positionMs.collectAsState()
    val dur by engine.durationMs.collectAsState()
    val total = dur.coerceAtLeast(1L)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkSurface)
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (track != null) {
                MediaThumbnail(
                    item = track!!,
                    appState = appState,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(track?.title ?: "No Media Playing", color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track?.artist ?: "MEDIO PLAYER Mini", color = TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            IconButton(onClick = onExpand) {
                Icon(Icons.Rounded.OpenInFull, contentDescription = "Expand", tint = PrimaryCyan)
            }
        }

        LinearProgressIndicator(
            progress = { (pos.toFloat() / total.toFloat()).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(3.dp),
            color = PrimaryCyan,
            trackColor = DarkSurfaceHighlight
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { engine.skipPrevious() }) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = "Prev", tint = TextPrimary)
            }
            IconButton(
                onClick = { engine.togglePlayPause() },
                modifier = Modifier.size(40.dp).background(PrimaryCyan, CircleShape)
            ) {
                Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = "Play/Pause", tint = PureBlack)
            }
            IconButton(onClick = { engine.skipNext() }) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = TextPrimary)
            }
        }
    }
}
