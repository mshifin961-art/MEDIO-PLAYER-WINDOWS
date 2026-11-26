package com.mediop.win.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import com.mediop.win.core.model.MediaType
import com.mediop.win.core.model.SortMode
import com.mediop.win.core.theme.*
import com.mediop.win.state.AppState
import com.mediop.win.ui.components.AudioListRow
import com.mediop.win.ui.components.MediaThumbnail
import com.mediop.win.ui.components.VideoGridCard

@Composable
fun MusicLibraryScreen(appState: AppState) {
    val allMedia by appState.allMedia.collectAsState()
    val favorites by appState.favoritePaths.collectAsState()
    val settings by appState.settings.collectAsState()
    val currentMedia by appState.engine.currentMedia.collectAsState()
    var showSortMenu by remember { mutableStateOf(false) }

    val tracks = remember(allMedia, settings.audioSortMode) {
        appState.sortedAudio(allMedia.filter { it.mediaType == MediaType.AUDIO })
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Music Library", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                Text("${tracks.size} tracks", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
            Box {
                OutlinedButton(onClick = { showSortMenu = true }) {
                    Icon(Icons.Rounded.Sort, contentDescription = null, tint = PrimaryCyan)
                    Spacer(Modifier.width(8.dp))
                    Text(settings.audioSortMode.label, color = TextPrimary)
                }
                DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                    listOf(
                        SortMode.NAME_ASC, SortMode.NAME_DESC, SortMode.ARTIST_ASC,
                        SortMode.ALBUM_ASC, SortMode.DURATION_DESC, SortMode.DATE_DESC
                    ).forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.label) },
                            onClick = {
                                appState.updateSettings { it.copy(audioSortMode = mode) }
                                showSortMenu = false
                            }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(tracks, key = { it.filePath }) { audio ->
                AudioListRow(
                    audio = audio,
                    appState = appState,
                    isPlaying = currentMedia?.filePath == audio.filePath,
                    isFavorite = audio.filePath in favorites,
                    onClick = { appState.playItem(audio, tracks) },
                    onFavoriteClick = { appState.toggleFavorite(audio) },
                    onAddToPlaylist = { appState.playlistPickerTarget = audio }
                )
            }
        }
    }
}

@Composable
fun VideoLibraryScreen(appState: AppState) {
    val allMedia by appState.allMedia.collectAsState()
    val favorites by appState.favoritePaths.collectAsState()
    val settings by appState.settings.collectAsState()
    var showSortMenu by remember { mutableStateOf(false) }

    val videos = remember(allMedia, settings.videoSortMode) {
        appState.sortedVideos(allMedia.filter { it.mediaType == MediaType.VIDEO })
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Video Library", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                Text("${videos.size} videos", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
            Box {
                OutlinedButton(onClick = { showSortMenu = true }) {
                    Icon(Icons.Rounded.Sort, contentDescription = null, tint = PrimaryCyan)
                    Spacer(Modifier.width(8.dp))
                    Text(settings.videoSortMode.label, color = TextPrimary)
                }
                DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                    listOf(
                        SortMode.DATE_DESC, SortMode.DATE_ASC, SortMode.NAME_ASC,
                        SortMode.NAME_DESC, SortMode.SIZE_DESC, SortMode.DURATION_DESC
                    ).forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.label) },
                            onClick = {
                                appState.updateSettings { it.copy(videoSortMode = mode) }
                                showSortMenu = false
                            }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 230.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(videos, key = { it.filePath }) { video ->
                VideoGridCard(
                    video = video,
                    appState = appState,
                    isFavorite = video.filePath in favorites,
                    onClick = { appState.playItem(video, videos) },
                    onFavoriteClick = { appState.toggleFavorite(video) },
                    onAddToPlaylist = { appState.playlistPickerTarget = video }
                )
            }
        }
    }
}

@Composable
fun AlbumsScreen(appState: AppState) {
    val allMedia by appState.allMedia.collectAsState()
    val favorites by appState.favoritePaths.collectAsState()
    val currentMedia by appState.engine.currentMedia.collectAsState()
    val albums = remember(allMedia) { appState.getAlbums() }
    val selectedAlbum = appState.selectedAlbumName?.let { name -> albums.firstOrNull { it.name == name } }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        if (selectedAlbum == null) {
            Text("Albums", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            Text("${albums.size} albums", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Spacer(Modifier.height(14.dp))
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 190.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(albums, key = { it.name }) { album ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { appState.selectedAlbumName = album.name },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, GlassBorder),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface)
                    ) {
                        Column {
                            MediaThumbnail(
                                item = album.tracks.first(),
                                appState = appState,
                                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                            )
                            Column(Modifier.padding(10.dp)) {
                                Text(album.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary, maxLines = 1)
                                Text("${album.artist} • ${album.tracks.size} tracks", style = MaterialTheme.typography.bodyMedium, color = TextSecondary, maxLines = 1)
                            }
                        }
                    }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { appState.selectedAlbumName = null }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Column {
                    Text(selectedAlbum.name, style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                    Text("${selectedAlbum.artist} • ${selectedAlbum.tracks.size} tracks", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
            }
            Spacer(Modifier.height(14.dp))
            LazyColumn {
                items(selectedAlbum.tracks, key = { it.filePath }) { audio ->
                    AudioListRow(
                        audio = audio,
                        appState = appState,
                        isPlaying = currentMedia?.filePath == audio.filePath,
                        isFavorite = audio.filePath in favorites,
                        onClick = { appState.playItem(audio, selectedAlbum.tracks) },
                        onFavoriteClick = { appState.toggleFavorite(audio) },
                        onAddToPlaylist = { appState.playlistPickerTarget = audio }
                    )
                }
            }
        }
    }
}

@Composable
fun ArtistsScreen(appState: AppState) {
    val allMedia by appState.allMedia.collectAsState()
    val favorites by appState.favoritePaths.collectAsState()
    val currentMedia by appState.engine.currentMedia.collectAsState()
    val artists = remember(allMedia) { appState.getArtists() }
    val selectedArtist = appState.selectedArtistName?.let { name -> artists.firstOrNull { it.name == name } }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        if (selectedArtist == null) {
            Text("Artists", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
            Text("${artists.size} artists", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Spacer(Modifier.height(14.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(artists, key = { it.name }) { artist ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { appState.selectedArtistName = artist.name },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, GlassBorder),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Person, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(artist.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                Text("${artist.tracks.size} tracks • ${artist.albumCount} albums", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            }
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = TextTertiary)
                        }
                    }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { appState.selectedArtistName = null }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Column {
                    Text(selectedArtist.name, style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                    Text("${selectedArtist.tracks.size} tracks", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
            }
            Spacer(Modifier.height(14.dp))
            LazyColumn {
                items(selectedArtist.tracks, key = { it.filePath }) { audio ->
                    AudioListRow(
                        audio = audio,
                        appState = appState,
                        isPlaying = currentMedia?.filePath == audio.filePath,
                        isFavorite = audio.filePath in favorites,
                        onClick = { appState.playItem(audio, selectedArtist.tracks) },
                        onFavoriteClick = { appState.toggleFavorite(audio) },
                        onAddToPlaylist = { appState.playlistPickerTarget = audio }
                    )
                }
            }
        }
    }
}

@Composable
fun FoldersScreen(appState: AppState, onPickFolder: () -> Unit) {
    val allMedia by appState.allMedia.collectAsState()
    val favorites by appState.favoritePaths.collectAsState()
    val currentMedia by appState.engine.currentMedia.collectAsState()
    val folders = remember(allMedia) { appState.getFolders() }
    val selectedFolder = appState.selectedFolderPath

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        if (selectedFolder == null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Folders", style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                    Text("${folders.size} folders", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
                Button(
                    onClick = onPickFolder,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryCyan, contentColor = PureBlack)
                ) {
                    Icon(Icons.Rounded.CreateNewFolder, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add Folder")
                }
            }
            Spacer(Modifier.height(14.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(folders, key = { it.path }) { folder ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { appState.selectedFolderPath = folder.path },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, GlassBorder),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Folder, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(folder.name, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                                Text("${folder.videoCount} videos • ${folder.audioCount} audio • ${folder.path}", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            }
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = TextTertiary)
                        }
                    }
                }
            }
        } else {
            val folderItems = remember(allMedia, selectedFolder) {
                allMedia.filter { it.folderPath == selectedFolder }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { appState.selectedFolderPath = null }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Column {
                    Text(folderItems.firstOrNull()?.folderName ?: selectedFolder, style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
                    Text("${folderItems.size} items", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
            }
            Spacer(Modifier.height(14.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(folderItems, key = { it.filePath }) { item ->
                    AudioListRow(
                        audio = item,
                        appState = appState,
                        isPlaying = currentMedia?.filePath == item.filePath,
                        isFavorite = item.filePath in favorites,
                        onClick = { appState.playItem(item, folderItems) },
                        onFavoriteClick = { appState.toggleFavorite(item) },
                        onAddToPlaylist = { appState.playlistPickerTarget = item }
                    )
                }
            }
        }
    }
}
