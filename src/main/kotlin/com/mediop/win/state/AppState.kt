package com.mediop.win.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.mediop.win.core.model.*
import com.mediop.win.data.db.DatabaseManager
import com.mediop.win.data.scanner.MediaScanner
import com.mediop.win.data.thumbnail.ThumbnailService
import com.mediop.win.player.MediaEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class AppState(
    val db: DatabaseManager = DatabaseManager(),
    val scanner: MediaScanner = MediaScanner(db),
    val thumbnails: ThumbnailService = ThumbnailService(db = db),
    val engine: MediaEngine = MediaEngine(db)
) : AutoCloseable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var currentSection by mutableStateOf(NavSection.HOME)
    var selectedFolderPath by mutableStateOf<String?>(null)
    var selectedAlbumName by mutableStateOf<String?>(null)
    var selectedArtistName by mutableStateOf<String?>(null)
    var selectedPlaylistId by mutableStateOf<Long?>(null)
    var searchQuery by mutableStateOf("")

    var showAudioPlayerOverlay by mutableStateOf(false)
    var showQueuePanel by mutableStateOf(false)
    var isMiniPlayerMode by mutableStateOf(false)
    var isFullscreen by mutableStateOf(false)

    var playlistPickerTarget by mutableStateOf<MediaItem?>(null)
    var showCreatePlaylistDialog by mutableStateOf(false)
    var renamePlaylistTarget by mutableStateOf<Playlist?>(null)
    var statusBanner by mutableStateOf<String?>(null)

    private val _allMedia = MutableStateFlow<List<MediaItem>>(emptyList())
    val allMedia: StateFlow<List<MediaItem>> = _allMedia.asStateFlow()

    private val _favoritePaths = MutableStateFlow<Set<String>>(emptySet())
    val favoritePaths: StateFlow<Set<String>> = _favoritePaths.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _playlistItems = MutableStateFlow<List<PlaylistItem>>(emptyList())
    val playlistItems: StateFlow<List<PlaylistItem>> = _playlistItems.asStateFlow()

    private val _history = MutableStateFlow<List<PlaybackHistory>>(emptyList())
    val history: StateFlow<List<PlaybackHistory>> = _history.asStateFlow()

    private val _watchedFolders = MutableStateFlow<List<String>>(emptyList())
    val watchedFolders: StateFlow<List<String>> = _watchedFolders.asStateFlow()

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    init {
        loadInitialState()
    }

    private fun loadInitialState() {
        val loadedSettings = db.loadSettings()
        _settings.value = loadedSettings
        engine.setVolume(loadedSettings.volume)
        engine.setSpeed(loadedSettings.defaultSpeed)

        scanner.ensureDefaultFolders()
        reloadFromDb()
        refreshLibrary()
    }

    fun reloadFromDb() {
        _allMedia.value = db.getAllMedia()
        _favoritePaths.value = db.getFavoritePaths()
        _playlists.value = db.getPlaylists()
        _history.value = db.getPlaybackHistory(60)
        _watchedFolders.value = db.getWatchedFolders()
        selectedPlaylistId?.let { pid ->
            _playlistItems.value = db.getPlaylistItems(pid)
        }
    }

    fun refreshLibrary() {
        if (_isScanning.value) return
        scope.launch {
            _isScanning.value = true
            try {
                val scanned = scanner.scanAllWatchedFolders()
                _allMedia.value = scanned
                _watchedFolders.value = db.getWatchedFolders()
            } catch (e: Exception) {
                statusBanner = "Scan error: ${e.message}"
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun addFolderAndScan(folder: File) {
        if (!folder.exists() || !folder.isDirectory) return
        db.addWatchedFolder(folder.absolutePath)
        refreshLibrary()
    }

    fun removeWatchedFolder(path: String) {
        db.removeWatchedFolder(path)
        refreshLibrary()
    }

    fun importFiles(files: List<File>, playImmediately: Boolean = false) {
        scope.launch {
            val imported = scanner.importFiles(files)
            reloadFromDb()
            if (imported.isNotEmpty()) {
                statusBanner = "Imported ${imported.size} media file(s)"
                if (playImmediately) {
                    playItem(imported.first(), imported)
                }
            }
        }
    }

    fun playItem(item: MediaItem, queue: List<MediaItem> = listOf(item)) {
        val resume = _settings.value.autoResume && item.mediaType == MediaType.VIDEO
        engine.playMedia(item, queue, resumeFromHistory = resume)
        scope.launch {
            delay(400)
            _history.value = db.getPlaybackHistory(60)
        }
    }

    fun playFromHistory(entry: PlaybackHistory) {
        val existing = _allMedia.value.firstOrNull { it.filePath == entry.filePath }
            ?: File(entry.filePath).takeIf { it.exists() }?.let { scanner.parseMediaFile(it) }
        if (existing != null) {
            playItem(existing, listOf(existing))
        } else {
            statusBanner = "Media file no longer exists on disk"
        }
    }

    fun toggleFavorite(item: MediaItem) {
        db.toggleFavorite(item.filePath)
        _favoritePaths.value = db.getFavoritePaths()
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        db.createPlaylist(name)
        _playlists.value = db.getPlaylists()
    }

    fun renamePlaylist(id: Long, newName: String) {
        if (newName.isBlank()) return
        db.renamePlaylist(id, newName)
        _playlists.value = db.getPlaylists()
    }

    fun deletePlaylist(id: Long) {
        db.deletePlaylist(id)
        if (selectedPlaylistId == id) {
            selectedPlaylistId = null
            _playlistItems.value = emptyList()
        }
        _playlists.value = db.getPlaylists()
    }

    fun openPlaylist(id: Long) {
        selectedPlaylistId = id
        _playlistItems.value = db.getPlaylistItems(id)
    }

    fun addItemToPlaylist(playlistId: Long, item: MediaItem) {
        db.addItemToPlaylist(playlistId, item)
        _playlists.value = db.getPlaylists()
        if (selectedPlaylistId == playlistId) {
            _playlistItems.value = db.getPlaylistItems(playlistId)
        }
        statusBanner = "Added '${item.title}' to playlist"
    }

    fun removePlaylistItem(playlistItemId: Long) {
        db.removeItemFromPlaylist(playlistItemId)
        selectedPlaylistId?.let { _playlistItems.value = db.getPlaylistItems(it) }
        _playlists.value = db.getPlaylists()
    }

    fun movePlaylistItem(fromIndex: Int, toIndex: Int) {
        val pid = selectedPlaylistId ?: return
        db.reorderPlaylistItem(pid, fromIndex, toIndex)
        _playlistItems.value = db.getPlaylistItems(pid)
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        db.saveSettings(updated)
    }

    fun clearHistory() {
        db.clearPlaybackHistory()
        _history.value = emptyList()
    }

    suspend fun loadThumbnail(item: MediaItem): ImageBitmap? {
        return thumbnails.getThumbnail(item, engine.isLibVlcAvailable)
    }

    fun sortedAudio(items: List<MediaItem>): List<MediaItem> = sortMedia(items, _settings.value.audioSortMode)
    fun sortedVideos(items: List<MediaItem>): List<MediaItem> = sortMedia(items, _settings.value.videoSortMode)

    private fun sortMedia(items: List<MediaItem>, mode: SortMode): List<MediaItem> = when (mode) {
        SortMode.NAME_ASC -> items.sortedBy { it.title.lowercase() }
        SortMode.NAME_DESC -> items.sortedByDescending { it.title.lowercase() }
        SortMode.DATE_DESC -> items.sortedByDescending { it.dateModified }
        SortMode.DATE_ASC -> items.sortedBy { it.dateModified }
        SortMode.SIZE_DESC -> items.sortedByDescending { it.sizeBytes }
        SortMode.SIZE_ASC -> items.sortedBy { it.sizeBytes }
        SortMode.DURATION_DESC -> items.sortedByDescending { it.durationMs }
        SortMode.DURATION_ASC -> items.sortedBy { it.durationMs }
        SortMode.ARTIST_ASC -> items.sortedBy { (it.artist ?: "Unknown").lowercase() }
        SortMode.ALBUM_ASC -> items.sortedBy { (it.album ?: "Unknown").lowercase() }
    }

    fun getAlbums(): List<AlbumGroup> {
        return _allMedia.value
            .filter { it.mediaType == MediaType.AUDIO }
            .groupBy { it.album?.takeIf { a -> a.isNotBlank() } ?: "Unknown Album" }
            .map { (album, tracks) ->
                AlbumGroup(
                    name = album,
                    artist = tracks.firstNotNullOfOrNull { it.artist } ?: "Unknown Artist",
                    tracks = tracks.sortedBy { it.title.lowercase() }
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    fun getArtists(): List<ArtistGroup> {
        return _allMedia.value
            .filter { it.mediaType == MediaType.AUDIO }
            .groupBy { it.artist?.takeIf { a -> a.isNotBlank() } ?: "Unknown Artist" }
            .map { (artist, tracks) ->
                ArtistGroup(
                    name = artist,
                    tracks = tracks.sortedBy { it.title.lowercase() },
                    albumCount = tracks.mapNotNull { it.album }.distinct().size.coerceAtLeast(1)
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    fun getFolders(): List<MediaFolder> {
        return _allMedia.value
            .groupBy { it.folderPath }
            .map { (path, items) ->
                MediaFolder(
                    path = path,
                    name = items.firstOrNull()?.folderName?.ifBlank { path } ?: path,
                    itemCount = items.size,
                    videoCount = items.count { it.mediaType == MediaType.VIDEO },
                    audioCount = items.count { it.mediaType == MediaType.AUDIO }
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    override fun close() {
        updateSettings { it.copy(volume = engine.volume.value) }
        engine.close()
        thumbnails.close()
        db.close()
        scope.cancel()
    }
}
