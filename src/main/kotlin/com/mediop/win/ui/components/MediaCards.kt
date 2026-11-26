package com.mediop.win.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediop.win.core.model.MediaItem
import com.mediop.win.core.model.MediaType
import com.mediop.win.core.model.PlaybackHistory
import com.mediop.win.core.theme.*
import com.mediop.win.core.util.FormatUtils
import com.mediop.win.state.AppState

@Composable
fun MediaThumbnail(
    item: MediaItem,
    appState: AppState,
    modifier: Modifier = Modifier,
    fallbackIcon: ImageVector = if (item.mediaType == MediaType.AUDIO) Icons.Rounded.MusicNote else Icons.Rounded.Movie
) {
    var bitmap by remember(item.filePath, item.thumbnailPath) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(item.filePath, item.thumbnailPath) {
        bitmap = appState.loadThumbnail(item)
    }

    Box(
        modifier = modifier.background(DarkSurfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = null,
                tint = PrimaryCyan.copy(alpha = 0.7f),
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun VideoGridCard(
    video: MediaItem,
    appState: AppState,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, GlassBorder),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            ) {
                MediaThumbnail(
                    item = video,
                    appState = appState,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, PlayerOverlayBackground),
                                startY = 80f
                            )
                        )
                )
                val resBadge = FormatUtils.getResolutionBadge(video.width, video.height)
                if (resBadge.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                            .background(PrimaryCyan.copy(alpha = 0.9f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = resBadge,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = PureBlack,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (video.durationMs > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .background(PureBlack.copy(alpha = 0.78f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = FormatUtils.formatDuration(video.durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onFavoriteClick, modifier = Modifier.size(26.dp)) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (isFavorite) AccentCoral else TextTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(onClick = onAddToPlaylist, modifier = Modifier.size(26.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.PlaylistAdd,
                            contentDescription = "Add to Playlist",
                            tint = TextTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = FormatUtils.formatFileSize(video.sizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Text(
                        text = video.folderName,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun AudioListRow(
    audio: MediaItem,
    appState: AppState,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (isPlaying) PrimaryCyan.copy(alpha = 0.6f) else GlassBorder),
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) DarkSurfaceHighlight else DarkSurface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MediaThumbnail(
                item = audio,
                appState = appState,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = audio.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                    color = if (isPlaying) PrimaryCyan else TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(
                        audio.artist ?: "Unknown Artist",
                        audio.album
                    ).joinToString(" • "),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (audio.durationMs > 0) {
                Text(
                    text = FormatUtils.formatDuration(audio.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
            IconButton(onClick = onFavoriteClick, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (isFavorite) AccentCoral else TextTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(onClick = onAddToPlaylist, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Rounded.PlaylistAdd,
                    contentDescription = "Add to Playlist",
                    tint = TextTertiary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun ContinueCard(
    history: PlaybackHistory,
    appState: AppState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = if (history.durationMs > 0) {
        (history.lastPositionMs.toFloat() / history.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Card(
        modifier = modifier
            .width(230.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, GlassBorder),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            ) {
                MediaThumbnail(
                    item = MediaItem(
                        filePath = history.filePath,
                        title = history.title,
                        displayName = history.title,
                        mediaType = history.mediaType,
                        thumbnailPath = history.thumbnailPath
                    ),
                    appState = appState,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(40.dp)
                        .background(PureBlack.copy(alpha = 0.65f), RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Resume",
                        tint = PrimaryCyan
                    )
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = PrimaryCyan,
                trackColor = DarkSurfaceHighlight
            )
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = history.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp),
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${FormatUtils.formatDuration(history.lastPositionMs)} / ${FormatUtils.formatDuration(history.durationMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(text = actionLabel, color = PrimaryCyan)
            }
        }
    }
}
