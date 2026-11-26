package com.mediop.win.core.model

enum class MediaType {
    AUDIO,
    VIDEO
}

enum class ThemeMode(val label: String) {
    DARK("Cinematic Dark"),
    AMOLED("Pure AMOLED"),
    SYSTEM("System Default")
}

enum class AspectRatioMode(val label: String, val vlcRatio: String?) {
    FIT("Fit", null),
    FILL("Fill", "16:9"),
    RATIO_16_9("16:9", "16:9"),
    RATIO_4_3("4:3", "4:3"),
    RATIO_21_9("21:9", "21:9")
}

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

enum class SortMode(val label: String) {
    NAME_ASC("Name (A to Z)"),
    NAME_DESC("Name (Z to A)"),
    DATE_DESC("Newest First"),
    DATE_ASC("Oldest First"),
    SIZE_DESC("Largest First"),
    SIZE_ASC("Smallest First"),
    DURATION_DESC("Longest First"),
    DURATION_ASC("Shortest First"),
    ARTIST_ASC("Artist (A to Z)"),
    ALBUM_ASC("Album (A to Z)")
}

enum class NavSection(val title: String) {
    HOME("Home"),
    MUSIC("Music Library"),
    VIDEOS("Video Library"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    FOLDERS("Folders"),
    PLAYLISTS("Playlists"),
    RECENTLY_PLAYED("Recently Played"),
    FAVORITES("Favorites"),
    SETTINGS("Settings")
}

data class MediaItem(
    val id: Long = 0L,
    val filePath: String,
    val title: String,
    val displayName: String,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val dateAdded: Long = 0L,
    val dateModified: Long = 0L,
    val mimeType: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val folderPath: String = "",
    val folderName: String = "",
    val mediaType: MediaType = MediaType.VIDEO,
    val thumbnailPath: String? = null
) {
    val resolutionFormatted: String
        get() = if (width > 0 && height > 0) "${width}x$height" else ""
}

data class MediaFolder(
    val path: String,
    val name: String,
    val itemCount: Int,
    val videoCount: Int,
    val audioCount: Int
)

data class AlbumGroup(
    val name: String,
    val artist: String,
    val tracks: List<MediaItem>
)

data class ArtistGroup(
    val name: String,
    val tracks: List<MediaItem>,
    val albumCount: Int
)

data class Playlist(
    val id: Long = 0L,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val itemCount: Int = 0
)

data class PlaylistItem(
    val id: Long = 0L,
    val playlistId: Long,
    val filePath: String,
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long = 0L,
    val mediaType: MediaType = MediaType.AUDIO,
    val orderIndex: Int = 0
)

data class PlaybackHistory(
    val filePath: String,
    val title: String,
    val lastPositionMs: Long,
    val durationMs: Long,
    val lastPlayedTimestamp: Long,
    val mediaType: MediaType,
    val thumbnailPath: String? = null
)

data class TrackInfo(
    val id: Int,
    val name: String,
    val isSelected: Boolean = false
)

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val autoResume: Boolean = true,
    val defaultSpeed: Float = 1.0f,
    val rememberSpeed: Boolean = false,
    val autoPlayNext: Boolean = true,
    val volume: Int = 80,
    val videoViewMode: String = "GRID",
    val videoSortMode: SortMode = SortMode.DATE_DESC,
    val audioSortMode: SortMode = SortMode.NAME_ASC
)
