package com.mediop.win

import com.mediop.win.core.model.*
import com.mediop.win.core.util.FormatUtils
import com.mediop.win.data.db.DatabaseManager
import com.mediop.win.data.scanner.MediaScanner
import com.mediop.win.data.thumbnail.ThumbnailService
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class MedioPlayerTests {

    @Test
    fun `format utils formats duration, size, and resolution badges correctly`() {
        assertEquals("0:00", FormatUtils.formatDuration(0))
        assertEquals("1:05", FormatUtils.formatDuration(65_000))
        assertEquals("1:01:05", FormatUtils.formatDuration(3_665_000))
        assertEquals("512 B", FormatUtils.formatFileSize(512))
        assertEquals("2.0 MB", FormatUtils.formatFileSize(2 * 1024 * 1024))
        assertEquals("4K", FormatUtils.getResolutionBadge(3840, 2160))
        assertEquals("1080p", FormatUtils.getResolutionBadge(1920, 1080))
        assertEquals("720p", FormatUtils.getResolutionBadge(1280, 720))
        assertEquals("", FormatUtils.getResolutionBadge(320, 240))
    }

    @Test
    fun `database migrations and media deduplication work`(@TempDir tempDir: Path) {
        val dbFile = tempDir.resolve("test_medio.db").toFile()
        DatabaseManager(dbFile).use { db ->
            assertEquals(1, db.getSchemaVersion())

            val item1 = MediaItem(
                filePath = "C:\\Media\\song1.mp3",
                title = "Song One",
                displayName = "song1.mp3",
                artist = "Artist A",
                album = "Album X",
                durationMs = 180_000L,
                sizeBytes = 5_000_000L,
                folderPath = "C:\\Media",
                folderName = "Media",
                mediaType = MediaType.AUDIO
            )
            db.upsertMediaItems(listOf(item1))
            db.upsertMediaItems(listOf(item1.copy(title = "Song One Updated")))

            val all = db.getAllMedia()
            assertEquals(1, all.size, "Duplicate file_path should be updated, not duplicated")
            assertEquals("Song One Updated", all.first().title)
        }
    }

    @Test
    fun `playlist CRUD and reordering persist across restarts`(@TempDir tempDir: Path) {
        val dbFile = tempDir.resolve("playlist_test.db").toFile()
        val itemA = MediaItem(filePath = "C:\\A.mp3", title = "A", displayName = "A.mp3", mediaType = MediaType.AUDIO)
        val itemB = MediaItem(filePath = "C:\\B.mp3", title = "B", displayName = "B.mp3", mediaType = MediaType.AUDIO)

        val playlistId: Long
        DatabaseManager(dbFile).use { db ->
            playlistId = db.createPlaylist("Road Trip")
            assertTrue(playlistId > 0)
            db.addItemToPlaylist(playlistId, itemA)
            db.addItemToPlaylist(playlistId, itemB)
            db.renamePlaylist(playlistId, "Chill Mix")
            db.reorderPlaylistItem(playlistId, 0, 1)
        }

        // Reopen database to verify persistence
        DatabaseManager(dbFile).use { db ->
            val playlists = db.getPlaylists()
            assertEquals(1, playlists.size)
            assertEquals("Chill Mix", playlists.first().name)
            assertEquals(2, playlists.first().itemCount)

            val items = db.getPlaylistItems(playlistId)
            assertEquals(listOf("B", "A"), items.map { it.title })

            db.removeItemFromPlaylist(items.first().id)
            assertEquals(1, db.getPlaylistItems(playlistId).size)

            db.deletePlaylist(playlistId)
            assertTrue(db.getPlaylists().isEmpty())
        }
    }

    @Test
    fun `favorites, playback history, and settings persist`(@TempDir tempDir: Path) {
        val dbFile = tempDir.resolve("state_test.db").toFile()
        DatabaseManager(dbFile).use { db ->
            assertTrue(db.toggleFavorite("C:\\video.mp4"))
            assertTrue("C:\\video.mp4" in db.getFavoritePaths())
            assertFalse(db.toggleFavorite("C:\\video.mp4"))
            assertTrue(db.getFavoritePaths().isEmpty())

            db.upsertPlaybackHistory(
                PlaybackHistory(
                    filePath = "C:\\movie.mkv",
                    title = "Movie",
                    lastPositionMs = 45_000L,
                    durationMs = 120_000L,
                    lastPlayedTimestamp = 1000L,
                    mediaType = MediaType.VIDEO
                )
            )
            assertEquals(45_000L, db.getLastPosition("C:\\movie.mkv"))

            db.saveSettings(AppSettings(themeMode = ThemeMode.AMOLED, autoResume = false, volume = 65))
            val loaded = db.loadSettings()
            assertEquals(ThemeMode.AMOLED, loaded.themeMode)
            assertFalse(loaded.autoResume)
            assertEquals(65, loaded.volume)
        }
    }

    @Test
    fun `media scanner imports supported files and removes missing files`(@TempDir tempDir: Path) = runTest {
        val dbFile = tempDir.resolve("scan_test.db").toFile()
        val mediaDir = tempDir.resolve("media").toFile().apply { mkdirs() }
        val mp3 = File(mediaDir, "track.mp3").apply { writeBytes(ByteArray(32)) }
        val mp4 = File(mediaDir, "clip.mp4").apply { writeBytes(ByteArray(32)) }
        File(mediaDir, "notes.txt").apply { writeText("ignore me") }

        DatabaseManager(dbFile).use { db ->
            val scanner = MediaScanner(db)
            db.addWatchedFolder(mediaDir.absolutePath)
            val scanned = scanner.scanAllWatchedFolders()
            assertEquals(2, scanned.size)
            assertTrue(scanned.any { it.mediaType == MediaType.AUDIO && it.displayName == "track.mp3" })
            assertTrue(scanned.any { it.mediaType == MediaType.VIDEO && it.displayName == "clip.mp4" })

            // Delete mp4 and rescan
            assertTrue(mp4.delete())
            val afterDelete = scanner.scanAllWatchedFolders()
            assertEquals(1, afterDelete.size)
            assertEquals(mp3.absolutePath, afterDelete.first().filePath)
        }
    }

    @Test
    fun `thumbnail service handles missing or corrupt files safely`(@TempDir tempDir: Path) {
        val cacheDir = tempDir.resolve("thumbs").toFile()
        ThumbnailService(cacheDir = cacheDir).use { svc ->
            val corruptAudio = tempDir.resolve("bad.mp3").toFile().apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val outPng = svc.getCachedThumbnailFile(corruptAudio.absolutePath)
            assertFalse(svc.extractAudioArtwork(corruptAudio, outPng))
            assertFalse(outPng.exists())
        }
    }
}
