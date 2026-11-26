package com.mediop.win.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mediop.win.core.model.ThemeMode
import com.mediop.win.core.theme.*
import com.mediop.win.state.AppState
import com.mediop.win.ui.components.AudioListRow

@Composable
fun SearchScreen(appState: AppState) {
    val allMedia by appState.allMedia.collectAsState()
    val favorites by appState.favoritePaths.collectAsState()
    val currentMedia by appState.engine.currentMedia.collectAsState()
    val query = appState.searchQuery

    val results = remember(allMedia, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) emptyList()
        else allMedia.filter {
            it.title.lowercase().contains(q) ||
                (it.artist?.lowercase()?.contains(q) == true) ||
                (it.album?.lowercase()?.contains(q) == true) ||
                it.folderName.lowercase().contains(q)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Search Library", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { appState.searchQuery = it },
            placeholder = { Text("Search songs, videos, artists, albums, folders...") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = PrimaryCyan) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { appState.searchQuery = "" }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(results, key = { it.filePath }) { item ->
                AudioListRow(
                    audio = item,
                    appState = appState,
                    isPlaying = currentMedia?.filePath == item.filePath,
                    isFavorite = item.filePath in favorites,
                    onClick = { appState.playItem(item, results) },
                    onFavoriteClick = { appState.toggleFavorite(item) },
                    onAddToPlaylist = { appState.playlistPickerTarget = item }
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(appState: AppState, onPickFolder: () -> Unit) {
    val settings by appState.settings.collectAsState()
    val watchedFolders by appState.watchedFolders.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            Text("Configure MEDIO PLAYER Windows Edition", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }

        item {
            SettingsCard("Appearance") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { appState.updateSettings { it.copy(themeMode = mode) } },
                            label = { Text(mode.label) }
                        )
                    }
                }
            }
        }

        item {
            SettingsCard("Playback") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Auto-Resume Videos", color = TextPrimary)
                        Text("Continue from last saved position", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                    Switch(
                        checked = settings.autoResume,
                        onCheckedChange = { enabled -> appState.updateSettings { it.copy(autoResume = enabled) } }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Auto-Play Next Track", color = TextPrimary)
                        Text("Automatically advance queue", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    }
                    Switch(
                        checked = settings.autoPlayNext,
                        onCheckedChange = { enabled -> appState.updateSettings { it.copy(autoPlayNext = enabled) } }
                    )
                }
            }
        }

        item {
            SettingsCard("Media Folders") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Watched Folders (${watchedFolders.size})", color = TextPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { appState.refreshLibrary() }) { Text("Rescan") }
                        Button(
                            onClick = onPickFolder,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan, contentColor = PureBlack)
                        ) { Text("Add Folder") }
                    }
                }
                Spacer(Modifier.height(8.dp))
                watchedFolders.forEach { folder ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Folder, contentDescription = null, tint = PrimaryCyan)
                        Spacer(Modifier.width(10.dp))
                        Text(folder, color = TextSecondary, modifier = Modifier.weight(1f))
                        IconButton(onClick = { appState.removeWatchedFolder(folder) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Remove", tint = AccentCoral)
                        }
                    }
                }
            }
        }

        item {
            SettingsCard("Engine & Cache") {
                Text("Media Engine: ${appState.engine.libVlcStatusMessage}", color = TextPrimary)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        appState.thumbnails.clearCache()
                        appState.statusBanner = "Thumbnail cache cleared"
                    }
                ) {
                    Text("Clear Thumbnail Cache")
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, GlassBorder),
        colors = CardDefaults.cardColors(containerColor = DarkSurface)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = PrimaryCyan)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
