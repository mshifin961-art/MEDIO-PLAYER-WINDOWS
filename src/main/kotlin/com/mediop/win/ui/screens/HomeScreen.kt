package com.mediop.win.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mediop.win.core.model.MediaType
import com.mediop.win.core.model.NavSection
import com.mediop.win.core.theme.*
import com.mediop.win.state.AppState
import com.mediop.win.ui.components.*

@Composable
fun HomeScreen(
    appState: AppState,
    onPickFolder: () -> Unit,
    onPickMediaFile: () -> Unit
) {
    val allMedia by appState.allMedia.collectAsState()
    val history by appState.history.collectAsState()
    val favorites by appState.favoritePaths.collectAsState()
    val currentMedia by appState.engine.currentMedia.collectAsState()
    val isScanning by appState.isScanning.collectAsState()

    val recentVideos = remember(allMedia) {
        allMedia.filter { it.mediaType == MediaType.VIDEO }.take(12)
    }
    val recentAudio = remember(allMedia) {
        allMedia.filter { it.mediaType == MediaType.AUDIO }.take(12)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentPadding = PaddingValues(top = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Welcome to MEDIO PLAYER",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                    Text(
                        text = "${allMedia.count { it.mediaType == MediaType.VIDEO }} videos • ${allMedia.count { it.mediaType == MediaType.AUDIO }} audio tracks",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickActionChip(
                        icon = Icons.Rounded.Refresh,
                        label = if (isScanning) "Scanning..." else "Scan Library",
                        onClick = { appState.refreshLibrary() }
                    )
                    QuickActionChip(
                        icon = Icons.Rounded.CreateNewFolder,
                        label = "Add Folder",
                        onClick = onPickFolder
                    )
                    QuickActionChip(
                        icon = Icons.Rounded.FileOpen,
                        label = "Open File",
                        onClick = onPickMediaFile
                    )
                }
            }
        }

        if (history.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Continue Listening & Watching",
                    actionLabel = "View All",
                    onAction = { appState.currentSection = NavSection.RECENTLY_PLAYED }
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(history.take(10), key = { it.filePath }) { entry ->
                        ContinueCard(
                            history = entry,
                            appState = appState,
                            onClick = { appState.playFromHistory(entry) }
                        )
                    }
                }
            }
        }

        if (recentVideos.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Recently Added Videos",
                    actionLabel = "All Videos",
                    onAction = { appState.currentSection = NavSection.VIDEOS }
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(recentVideos, key = { it.filePath }) { video ->
                        VideoGridCard(
                            video = video,
                            appState = appState,
                            isFavorite = video.filePath in favorites,
                            onClick = { appState.playItem(video, recentVideos) },
                            onFavoriteClick = { appState.toggleFavorite(video) },
                            onAddToPlaylist = { appState.playlistPickerTarget = video },
                            modifier = Modifier.width(240.dp)
                        )
                    }
                }
            }
        }

        if (recentAudio.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Recently Added Music",
                    actionLabel = "All Music",
                    onAction = { appState.currentSection = NavSection.MUSIC }
                )
            }
            items(recentAudio.take(8), key = { it.filePath }) { audio ->
                AudioListRow(
                    audio = audio,
                    appState = appState,
                    isPlaying = currentMedia?.filePath == audio.filePath,
                    isFavorite = audio.filePath in favorites,
                    onClick = { appState.playItem(audio, recentAudio) },
                    onFavoriteClick = { appState.toggleFavorite(audio) },
                    onAddToPlaylist = { appState.playlistPickerTarget = audio }
                )
            }
        }

        if (allMedia.isEmpty() && !isScanning) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, GlassBorder),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LibraryMusic,
                            contentDescription = null,
                            tint = PrimaryCyan,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No local media found yet",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Add a folder or drag & drop audio/video files anywhere into this window.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onPickFolder,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan, contentColor = PureBlack)
                        ) {
                            Icon(Icons.Rounded.CreateNewFolder, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Select Media Folder", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, GlassBorder),
        color = DarkSurfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}
