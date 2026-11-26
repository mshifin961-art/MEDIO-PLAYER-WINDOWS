package com.mediop.win.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.mediop.win.core.model.MediaItem
import com.mediop.win.core.theme.*
import com.mediop.win.core.util.FormatUtils
import com.mediop.win.state.AppState
import com.mediop.win.ui.components.AudioListRow
import com.mediop.win.ui.components.MediaThumbnail

@Composable
fun PlaylistsScreen(appState: AppState) {
    val playlists by appState.playlists.collectAsState()
    val playlistItems by appState.playlistItems.collectAsState()
    val allMedia by appState.allMedia.collectAsState()
    val selectedId = appState.selectedPlaylistId
    val selectedPlaylist = playlists.firstOrNull { it.id == selectedId }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        if (selectedPlaylist == null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Playlists", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                    Text("${playlists.size} playlists", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                Button(
                    onClick = { appState.showCreatePlaylistDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan, contentColor = PureBlack)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("New Playlist")
                }
            }
            Spacer(Modifier.height(14.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(playlists, key = { it.id }) { pl ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { appState.openPlaylist(pl.id) },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, GlassBorder),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.QueueMusic, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(30.dp))
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(pl.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                Text("${pl.itemCount} items", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            }
                            IconButton(onClick = { appState.renamePlaylistTarget = pl }) {
                                Icon(Icons.Rounded.Edit, contentDescription = "Rename", tint = TextSecondary)
                            }
                            IconButton(onClick = { appState.deletePlaylist(pl.id) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = AccentCoral)
                            }
                        }
                    }
                }
            }
        } else {
            val mediaQueue = remember(playlistItems, allMedia) {
                playlistItems.map { pi ->
                    allMedia.firstOrNull { it.filePath == pi.filePath } ?: MediaItem(
                        filePath = pi.filePath,
                        title = pi.title,
                        displayName = pi.title,
                        artist = pi.artist,
                        album = pi.album,
                        durationMs = pi.durationMs,
                        mediaType = pi.mediaType
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { appState.selectedPlaylistId = null }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                    Column {
                        Text(selectedPlaylist.name, style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                        Text("${playlistItems.size} tracks", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                }
                if (mediaQueue.isNotEmpty()) {
                    Button(
                        onClick = { appState.playItem(mediaQueue.first(), mediaQueue) },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan, contentColor = PureBlack)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Play All")
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(playlistItems, key = { _, item -> item.id }) { index, item ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                val target = mediaQueue.getOrNull(index) ?: return@clickable
                                appState.playItem(target, mediaQueue)
                            },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, GlassBorder),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${index + 1}", color = TextTertiary, modifier = Modifier.width(28.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                Text(
                                    listOfNotNull(item.artist, FormatUtils.formatDuration(item.durationMs)).joinToString(" • "),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                            IconButton(
                                onClick = { if (index > 0) appState.movePlaylistItem(index, index - 1) },
                                enabled = index > 0
                            ) {
                                Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move Up", tint = TextSecondary)
                            }
                            IconButton(
                                onClick = { if (index < playlistItems.lastIndex) appState.movePlaylistItem(index, index + 1) },
                                enabled = index < playlistItems.lastIndex
                            ) {
                                Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move Down", tint = TextSecondary)
                            }
                            IconButton(onClick = { appState.removePlaylistItem(item.id) }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Remove", tint = AccentCoral)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FavoritesScreen(appState: AppState) {
    val allMedia by appState.allMedia.collectAsState()
    val favorites by appState.favoritePaths.collectAsState()
    val currentMedia by appState.engine.currentMedia.collectAsState()
    val favItems = remember(allMedia, favorites) { allMedia.filter { it.filePath in favorites } }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Favorites", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
        Text("${favItems.size} favorites", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Spacer(Modifier.height(14.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(favItems, key = { it.filePath }) { item ->
                AudioListRow(
                    audio = item,
                    appState = appState,
                    isPlaying = currentMedia?.filePath == item.filePath,
                    isFavorite = true,
                    onClick = { appState.playItem(item, favItems) },
                    onFavoriteClick = { appState.toggleFavorite(item) },
                    onAddToPlaylist = { appState.playlistPickerTarget = item }
                )
            }
        }
    }
}

@Composable
fun RecentlyPlayedScreen(appState: AppState) {
    val history by appState.history.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Recently Played", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                Text("${history.size} items", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
            if (history.isNotEmpty()) {
                OutlinedButton(onClick = { appState.clearHistory() }) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = null, tint = AccentCoral)
                    Spacer(Modifier.width(8.dp))
                    Text("Clear History", color = AccentCoral)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(history, key = { it.filePath }) { entry ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { appState.playFromHistory(entry) },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, GlassBorder),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MediaThumbnail(
                            item = MediaItem(
                                filePath = entry.filePath,
                                title = entry.title,
                                displayName = entry.title,
                                mediaType = entry.mediaType,
                                thumbnailPath = entry.thumbnailPath
                            ),
                            appState = appState,
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entry.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                            Text(
                                "${entry.mediaType.name} • ${FormatUtils.formatDuration(entry.lastPositionMs)} / ${FormatUtils.formatDuration(entry.durationMs)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                        Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = PrimaryCyan)
                    }
                }
            }
        }
    }
}
