package com.mediop.win.data.db

import com.mediop.win.core.model.*
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Statement

/**
 * Thread-safe SQLite database manager for MEDIO PLAYER Windows Edition.
 * Handles schema creation, versioned migrations, and CRUD operations.
 */
class DatabaseManager(
    dbFile: File = defaultDbFile()
) : AutoCloseable {

    companion object {
        private const val CURRENT_SCHEMA_VERSION = 1

        fun defaultDataDir(): File {
            val localAppData = System.getenv("LOCALAPPDATA")
                ?: System.getProperty("user.home")
            return File(localAppData, "MedioPlayer").apply { mkdirs() }
        }

        fun defaultDbFile(): File = File(defaultDataDir(), "medio_player.db")
    }

    private val lock = Any()
    private val connection: Connection

    init {
        dbFile.parentFile?.mkdirs()
        connection = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
        connection.createStatement().use { stmt ->
            stmt.execute("PRAGMA foreign_keys = ON;")
            stmt.execute("PRAGMA journal_mode = WAL;")
            stmt.execute("PRAGMA synchronous = NORMAL;")
        }
        runMigrations()
    }

    private fun runMigrations() = synchronized(lock) {
        connection.createStatement().use { stmt ->
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS schema_version (
                    version INTEGER PRIMARY KEY
                );
                """.trimIndent()
            )
            val currentVersion = stmt.executeQuery("SELECT MAX(version) FROM schema_version;").use { rs ->
                if (rs.next()) rs.getInt(1) else 0
            }

            if (currentVersion < 1) {
                migrateToV1(stmt)
                stmt.execute("INSERT OR REPLACE INTO schema_version(version) VALUES (1);")
            }
        }
    }

    private fun migrateToV1(stmt: Statement) {
        stmt.execute(
            """
            CREATE TABLE IF NOT EXISTS watched_folders (
                path TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                added_at INTEGER NOT NULL
            );
            """.trimIndent()
        )
        stmt.execute(
            """
            CREATE TABLE IF NOT EXISTS media_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                file_path TEXT NOT NULL UNIQUE,
                title TEXT NOT NULL,
                display_name TEXT NOT NULL,
                artist TEXT,
                album TEXT,
                duration_ms INTEGER NOT NULL DEFAULT 0,
                size_bytes INTEGER NOT NULL DEFAULT 0,
                date_added INTEGER NOT NULL DEFAULT 0,
                date_modified INTEGER NOT NULL DEFAULT 0,
                mime_type TEXT,
                width INTEGER NOT NULL DEFAULT 0,
                height INTEGER NOT NULL DEFAULT 0,
                folder_path TEXT NOT NULL DEFAULT '',
                folder_name TEXT NOT NULL DEFAULT '',
                media_type TEXT NOT NULL,
                thumbnail_path TEXT
            );
            """.trimIndent()
        )
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_media_type ON media_items(media_type);")
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_media_folder ON media_items(folder_path);")

        stmt.execute(
            """
            CREATE TABLE IF NOT EXISTS favorites (
                file_path TEXT PRIMARY KEY,
                date_added INTEGER NOT NULL
            );
            """.trimIndent()
        )

        stmt.execute(
            """
            CREATE TABLE IF NOT EXISTS playlists (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                modified_at INTEGER NOT NULL
            );
            """.trimIndent()
        )

        stmt.execute(
            """
            CREATE TABLE IF NOT EXISTS playlist_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                playlist_id INTEGER NOT NULL,
                file_path TEXT NOT NULL,
                title TEXT NOT NULL,
                artist TEXT,
                album TEXT,
                duration_ms INTEGER NOT NULL DEFAULT 0,
                media_type TEXT NOT NULL,
                order_index INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(playlist_id) REFERENCES playlists(id) ON DELETE CASCADE
            );
            """.trimIndent()
        )
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_playlist_items_pid ON playlist_items(playlist_id);")

        stmt.execute(
            """
            CREATE TABLE IF NOT EXISTS playback_history (
                file_path TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                last_position_ms INTEGER NOT NULL DEFAULT 0,
                duration_ms INTEGER NOT NULL DEFAULT 0,
                last_played_ts INTEGER NOT NULL,
                media_type TEXT NOT NULL,
                thumbnail_path TEXT
            );
            """.trimIndent()
        )

        stmt.execute(
            """
            CREATE TABLE IF NOT EXISTS app_settings (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL
            );
            """.trimIndent()
        )
    }

    fun getSchemaVersion(): Int = synchronized(lock) {
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT MAX(version) FROM schema_version;").use { rs ->
                if (rs.next()) rs.getInt(1) else 0
            }
        }
    }

    // --- Watched Folders ---

    fun addWatchedFolder(path: String) = synchronized(lock) {
        val file = File(path)
        val sql = "INSERT OR IGNORE INTO watched_folders(path, name, added_at) VALUES (?, ?, ?);"
        connection.prepareStatement(sql).use { ps ->
            ps.setString(1, file.absolutePath)
            ps.setString(2, file.name.ifBlank { file.absolutePath })
            ps.setLong(3, System.currentTimeMillis())
            ps.executeUpdate()
        }
    }

    fun removeWatchedFolder(path: String) = synchronized(lock) {
        connection.prepareStatement("DELETE FROM watched_folders WHERE path = ?;").use { ps ->
            ps.setString(1, path)
            ps.executeUpdate()
        }
    }

    fun getWatchedFolders(): List<String> = synchronized(lock) {
        val list = mutableListOf<String>()
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT path FROM watched_folders ORDER BY added_at ASC;").use { rs ->
                while (rs.next()) list.add(rs.getString(1))
            }
        }
        list
    }

    // --- Media Items ---

    fun upsertMediaItems(items: List<MediaItem>) = synchronized(lock) {
        if (items.isEmpty()) return@synchronized
        val prevAutoCommit = connection.autoCommit
        connection.autoCommit = false
        try {
            val sql = """
                INSERT INTO media_items(
                    file_path, title, display_name, artist, album, duration_ms, size_bytes,
                    date_added, date_modified, mime_type, width, height, folder_path, folder_name,
                    media_type, thumbnail_path
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(file_path) DO UPDATE SET
                    title = excluded.title,
                    display_name = excluded.display_name,
                    artist = COALESCE(excluded.artist, media_items.artist),
                    album = COALESCE(excluded.album, media_items.album),
                    duration_ms = CASE WHEN excluded.duration_ms > 0 THEN excluded.duration_ms ELSE media_items.duration_ms END,
                    size_bytes = excluded.size_bytes,
                    date_modified = excluded.date_modified,
                    mime_type = excluded.mime_type,
                    width = CASE WHEN excluded.width > 0 THEN excluded.width ELSE media_items.width END,
                    height = CASE WHEN excluded.height > 0 THEN excluded.height ELSE media_items.height END,
                    folder_path = excluded.folder_path,
                    folder_name = excluded.folder_name,
                    media_type = excluded.media_type,
                    thumbnail_path = COALESCE(excluded.thumbnail_path, media_items.thumbnail_path);
            """.trimIndent()
            connection.prepareStatement(sql).use { ps ->
                for (item in items) {
                    ps.setString(1, item.filePath)
                    ps.setString(2, item.title)
                    ps.setString(3, item.displayName)
                    ps.setString(4, item.artist)
                    ps.setString(5, item.album)
                    ps.setLong(6, item.durationMs)
                    ps.setLong(7, item.sizeBytes)
                    ps.setLong(8, item.dateAdded)
                    ps.setLong(9, item.dateModified)
                    ps.setString(10, item.mimeType)
                    ps.setInt(11, item.width)
                    ps.setInt(12, item.height)
                    ps.setString(13, item.folderPath)
                    ps.setString(14, item.folderName)
                    ps.setString(15, item.mediaType.name)
                    ps.setString(16, item.thumbnailPath)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
            connection.commit()
        } catch (e: Exception) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = prevAutoCommit
        }
    }

    fun updateMediaThumbnailAndDuration(
        filePath: String,
        thumbnailPath: String?,
        durationMs: Long,
        width: Int = 0,
        height: Int = 0
    ) = synchronized(lock) {
        val sql = """
            UPDATE media_items SET
                thumbnail_path = COALESCE(?, thumbnail_path),
                duration_ms = CASE WHEN ? > 0 THEN ? ELSE duration_ms END,
                width = CASE WHEN ? > 0 THEN ? ELSE width END,
                height = CASE WHEN ? > 0 THEN ? ELSE height END
            WHERE file_path = ?;
        """.trimIndent()
        connection.prepareStatement(sql).use { ps ->
            ps.setString(1, thumbnailPath)
            ps.setLong(2, durationMs)
            ps.setLong(3, durationMs)
            ps.setInt(4, width)
            ps.setInt(5, width)
            ps.setInt(6, height)
            ps.setInt(7, height)
            ps.setString(8, filePath)
            ps.executeUpdate()
        }
    }

    fun removeMissingFiles(existingPaths: Set<String>) = synchronized(lock) {
        val allPaths = mutableListOf<String>()
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT file_path FROM media_items;").use { rs ->
                while (rs.next()) allPaths.add(rs.getString(1))
            }
        }
        val missing = allPaths.filter { it !in existingPaths && !File(it).exists() }
        if (missing.isEmpty()) return@synchronized
        val prevAutoCommit = connection.autoCommit
        connection.autoCommit = false
        try {
            connection.prepareStatement("DELETE FROM media_items WHERE file_path = ?;").use { ps ->
                for (path in missing) {
                    ps.setString(1, path)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
            connection.commit()
        } finally {
            connection.autoCommit = prevAutoCommit
        }
    }

    fun deleteMediaByPath(filePath: String) = synchronized(lock) {
        connection.prepareStatement("DELETE FROM media_items WHERE file_path = ?;").use { ps ->
            ps.setString(1, filePath)
            ps.executeUpdate()
        }
    }

    fun getAllMedia(): List<MediaItem> = synchronized(lock) {
        val result = mutableListOf<MediaItem>()
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT * FROM media_items ORDER BY date_added DESC;").use { rs ->
                while (rs.next()) result.add(rs.toMediaItem())
            }
        }
        result
    }

    fun getMediaByType(type: MediaType): List<MediaItem> = synchronized(lock) {
        val result = mutableListOf<MediaItem>()
        connection.prepareStatement("SELECT * FROM media_items WHERE media_type = ? ORDER BY date_added DESC;").use { ps ->
            ps.setString(1, type.name)
            ps.executeQuery().use { rs ->
                while (rs.next()) result.add(rs.toMediaItem())
            }
        }
        result
    }

    fun getMediaByPath(filePath: String): MediaItem? = synchronized(lock) {
        connection.prepareStatement("SELECT * FROM media_items WHERE file_path = ?;").use { ps ->
            ps.setString(1, filePath)
            ps.executeQuery().use { rs ->
                if (rs.next()) rs.toMediaItem() else null
            }
        }
    }

    private fun ResultSet.toMediaItem(): MediaItem = MediaItem(
        id = getLong("id"),
        filePath = getString("file_path"),
        title = getString("title"),
        displayName = getString("display_name"),
        artist = getString("artist"),
        album = getString("album"),
        durationMs = getLong("duration_ms"),
        sizeBytes = getLong("size_bytes"),
        dateAdded = getLong("date_added"),
        dateModified = getLong("date_modified"),
        mimeType = getString("mime_type"),
        width = getInt("width"),
        height = getInt("height"),
        folderPath = getString("folder_path"),
        folderName = getString("folder_name"),
        mediaType = if (getString("media_type") == MediaType.AUDIO.name) MediaType.AUDIO else MediaType.VIDEO,
        thumbnailPath = getString("thumbnail_path")
    )

    // --- Favorites ---

    fun getFavoritePaths(): Set<String> = synchronized(lock) {
        val set = linkedSetOf<String>()
        connection.createStatement().use { stmt ->
            stmt.executeQuery("SELECT file_path FROM favorites ORDER BY date_added DESC;").use { rs ->
                while (rs.next()) set.add(rs.getString(1))
            }
        }
        set
    }

    fun toggleFavorite(filePath: String): Boolean = synchronized(lock) {
        val exists = connection.prepareStatement("SELECT 1 FROM favorites WHERE file_path = ?;").use { ps ->
            ps.setString(1, filePath)
            ps.executeQuery().use { it.next() }
        }
        if (exists) {
            connection.prepareStatement("DELETE FROM favorites WHERE file_path = ?;").use { ps ->
                ps.setString(1, filePath)
                ps.executeUpdate()
            }
            false
        } else {
            connection.prepareStatement("INSERT INTO favorites(file_path, date_added) VALUES (?, ?);").use { ps ->
                ps.setString(1, filePath)
                ps.setLong(2, System.currentTimeMillis())
                ps.executeUpdate()
            }
            true
        }
    }

    // --- Playlists ---

    fun createPlaylist(name: String): Long = synchronized(lock) {
        val now = System.currentTimeMillis()
        val sql = "INSERT INTO playlists(name, created_at, modified_at) VALUES (?, ?, ?);"
        connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { ps ->
            ps.setString(1, name.trim())
            ps.setLong(2, now)
            ps.setLong(3, now)
            ps.executeUpdate()
            ps.generatedKeys.use { keys ->
                if (keys.next()) keys.getLong(1) else 0L
            }
        }
    }

    fun renamePlaylist(id: Long, newName: String) = synchronized(lock) {
        val sql = "UPDATE playlists SET name = ?, modified_at = ? WHERE id = ?;"
        connection.prepareStatement(sql).use { ps ->
            ps.setString(1, newName.trim())
            ps.setLong(2, System.currentTimeMillis())
            ps.setLong(3, id)
            ps.executeUpdate()
        }
    }

    fun deletePlaylist(id: Long) = synchronized(lock) {
        connection.prepareStatement("DELETE FROM playlists WHERE id = ?;").use { ps ->
            ps.setLong(1, id)
            ps.executeUpdate()
        }
    }

    fun getPlaylists(): List<Playlist> = synchronized(lock) {
        val sql = """
            SELECT p.id, p.name, p.created_at, p.modified_at, COUNT(pi.id) AS item_count
            FROM playlists p
            LEFT JOIN playlist_items pi ON p.id = pi.playlist_id
            GROUP BY p.id
            ORDER BY p.modified_at DESC;
        """.trimIndent()
        val list = mutableListOf<Playlist>()
        connection.createStatement().use { stmt ->
            stmt.executeQuery(sql).use { rs ->
                while (rs.next()) {
                    list.add(
                        Playlist(
                            id = rs.getLong("id"),
                            name = rs.getString("name"),
                            createdAt = rs.getLong("created_at"),
                            modifiedAt = rs.getLong("modified_at"),
                            itemCount = rs.getInt("item_count")
                        )
                    )
                }
            }
        }
        list
    }

    fun addItemToPlaylist(playlistId: Long, item: MediaItem) = synchronized(lock) {
        val nextIndex = connection.prepareStatement("SELECT COALESCE(MAX(order_index), -1) + 1 FROM playlist_items WHERE playlist_id = ?;").use { ps ->
            ps.setLong(1, playlistId)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
        }
        val sql = """
            INSERT INTO playlist_items(playlist_id, file_path, title, artist, album, duration_ms, media_type, order_index)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?);
        """.trimIndent()
        connection.prepareStatement(sql).use { ps ->
            ps.setLong(1, playlistId)
            ps.setString(2, item.filePath)
            ps.setString(3, item.title)
            ps.setString(4, item.artist)
            ps.setString(5, item.album)
            ps.setLong(6, item.durationMs)
            ps.setString(7, item.mediaType.name)
            ps.setInt(8, nextIndex)
            ps.executeUpdate()
        }
        connection.prepareStatement("UPDATE playlists SET modified_at = ? WHERE id = ?;").use { ps ->
            ps.setLong(1, System.currentTimeMillis())
            ps.setLong(2, playlistId)
            ps.executeUpdate()
        }
    }

    fun removeItemFromPlaylist(playlistItemId: Long) = synchronized(lock) {
        connection.prepareStatement("DELETE FROM playlist_items WHERE id = ?;").use { ps ->
            ps.setLong(1, playlistItemId)
            ps.executeUpdate()
        }
    }

    fun reorderPlaylistItem(playlistId: Long, fromIndex: Int, toIndex: Int) = synchronized(lock) {
        val items = getPlaylistItems(playlistId).toMutableList()
        if (fromIndex !in items.indices || toIndex !in items.indices || fromIndex == toIndex) return@synchronized
        val moved = items.removeAt(fromIndex)
        items.add(toIndex, moved)
        val prevAutoCommit = connection.autoCommit
        connection.autoCommit = false
        try {
            connection.prepareStatement("UPDATE playlist_items SET order_index = ? WHERE id = ?;").use { ps ->
                items.forEachIndexed { index, item ->
                    ps.setInt(1, index)
                    ps.setLong(2, item.id)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
            connection.commit()
        } finally {
            connection.autoCommit = prevAutoCommit
        }
    }

    fun getPlaylistItems(playlistId: Long): List<PlaylistItem> = synchronized(lock) {
        val list = mutableListOf<PlaylistItem>()
        val sql = "SELECT * FROM playlist_items WHERE playlist_id = ? ORDER BY order_index ASC, id ASC;"
        connection.prepareStatement(sql).use { ps ->
            ps.setLong(1, playlistId)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    list.add(
                        PlaylistItem(
                            id = rs.getLong("id"),
                            playlistId = rs.getLong("playlist_id"),
                            filePath = rs.getString("file_path"),
                            title = rs.getString("title"),
                            artist = rs.getString("artist"),
                            album = rs.getString("album"),
                            durationMs = rs.getLong("duration_ms"),
                            mediaType = if (rs.getString("media_type") == MediaType.AUDIO.name) MediaType.AUDIO else MediaType.VIDEO,
                            orderIndex = rs.getInt("order_index")
                        )
                    )
                }
            }
        }
        list
    }

    // --- Playback History ---

    fun upsertPlaybackHistory(history: PlaybackHistory) = synchronized(lock) {
        val sql = """
            INSERT INTO playback_history(file_path, title, last_position_ms, duration_ms, last_played_ts, media_type, thumbnail_path)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(file_path) DO UPDATE SET
                title = excluded.title,
                last_position_ms = excluded.last_position_ms,
                duration_ms = CASE WHEN excluded.duration_ms > 0 THEN excluded.duration_ms ELSE playback_history.duration_ms END,
                last_played_ts = excluded.last_played_ts,
                media_type = excluded.media_type,
                thumbnail_path = COALESCE(excluded.thumbnail_path, playback_history.thumbnail_path);
        """.trimIndent()
        connection.prepareStatement(sql).use { ps ->
            ps.setString(1, history.filePath)
            ps.setString(2, history.title)
            ps.setLong(3, history.lastPositionMs)
            ps.setLong(4, history.durationMs)
            ps.setLong(5, history.lastPlayedTimestamp)
            ps.setString(6, history.mediaType.name)
            ps.setString(7, history.thumbnailPath)
            ps.executeUpdate()
        }
    }

    fun getPlaybackHistory(limit: Int = 50): List<PlaybackHistory> = synchronized(lock) {
        val list = mutableListOf<PlaybackHistory>()
        val sql = "SELECT * FROM playback_history ORDER BY last_played_ts DESC LIMIT ?;"
        connection.prepareStatement(sql).use { ps ->
            ps.setInt(1, limit)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    list.add(
                        PlaybackHistory(
                            filePath = rs.getString("file_path"),
                            title = rs.getString("title"),
                            lastPositionMs = rs.getLong("last_position_ms"),
                            durationMs = rs.getLong("duration_ms"),
                            lastPlayedTimestamp = rs.getLong("last_played_ts"),
                            mediaType = if (rs.getString("media_type") == MediaType.AUDIO.name) MediaType.AUDIO else MediaType.VIDEO,
                            thumbnailPath = rs.getString("thumbnail_path")
                        )
                    )
                }
            }
        }
        list
    }

    fun getLastPosition(filePath: String): Long = synchronized(lock) {
        connection.prepareStatement("SELECT last_position_ms FROM playback_history WHERE file_path = ?;").use { ps ->
            ps.setString(1, filePath)
            ps.executeQuery().use { rs ->
                if (rs.next()) rs.getLong(1) else 0L
            }
        }
    }

    fun clearPlaybackHistory() = synchronized(lock) {
        connection.createStatement().use { it.execute("DELETE FROM playback_history;") }
    }

    // --- Settings ---

    fun setSetting(key: String, value: String) = synchronized(lock) {
        connection.prepareStatement("INSERT OR REPLACE INTO app_settings(key, value) VALUES (?, ?);").use { ps ->
            ps.setString(1, key)
            ps.setString(2, value)
            ps.executeUpdate()
        }
    }

    fun getSetting(key: String): String? = synchronized(lock) {
        connection.prepareStatement("SELECT value FROM app_settings WHERE key = ?;").use { ps ->
            ps.setString(1, key)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }
    }

    fun loadSettings(): AppSettings = synchronized(lock) {
        AppSettings(
            themeMode = getSetting("theme_mode")?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.DARK,
            autoResume = getSetting("auto_resume")?.toBooleanStrictOrNull() ?: true,
            defaultSpeed = getSetting("default_speed")?.toFloatOrNull() ?: 1.0f,
            rememberSpeed = getSetting("remember_speed")?.toBooleanStrictOrNull() ?: false,
            autoPlayNext = getSetting("auto_play_next")?.toBooleanStrictOrNull() ?: true,
            volume = getSetting("volume")?.toIntOrNull() ?: 80,
            videoViewMode = getSetting("video_view_mode") ?: "GRID",
            videoSortMode = getSetting("video_sort_mode")?.let { runCatching { SortMode.valueOf(it) }.getOrNull() } ?: SortMode.DATE_DESC,
            audioSortMode = getSetting("audio_sort_mode")?.let { runCatching { SortMode.valueOf(it) }.getOrNull() } ?: SortMode.NAME_ASC
        )
    }

    fun saveSettings(settings: AppSettings) {
        setSetting("theme_mode", settings.themeMode.name)
        setSetting("auto_resume", settings.autoResume.toString())
        setSetting("default_speed", settings.defaultSpeed.toString())
        setSetting("remember_speed", settings.rememberSpeed.toString())
        setSetting("auto_play_next", settings.autoPlayNext.toString())
        setSetting("volume", settings.volume.toString())
        setSetting("video_view_mode", settings.videoViewMode)
        setSetting("video_sort_mode", settings.videoSortMode.name)
        setSetting("audio_sort_mode", settings.audioSortMode.name)
    }

    override fun close() {
        synchronized(lock) {
            if (!connection.isClosed) {
                connection.close()
            }
        }
    }
}
