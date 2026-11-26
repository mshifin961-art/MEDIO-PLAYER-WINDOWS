package com.mediop.win.data.scanner

import com.mediop.win.core.model.MediaItem
import com.mediop.win.core.model.MediaType
import com.mediop.win.data.db.DatabaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Scans local Windows directories for supported audio and video files,
 * extracts embedded metadata with jaudiotagger, and keeps SQLite in sync.
 */
class MediaScanner(private val db: DatabaseManager) {

    companion object {
        val AUDIO_EXTENSIONS = setOf("mp3", "flac", "wav", "aac", "m4a", "ogg", "opus", "wma", "alac")
        val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "avi", "mov", "webm", "wmv", "flv", "m4v", "ts", "3gp")

        init {
            // Silence verbose jaudiotagger JUL logging
            Logger.getLogger("org.jaudiotagger").level = Level.OFF
        }

        fun defaultWindowsMediaFolders(): List<File> {
            val home = File(System.getProperty("user.home"))
            return listOf(File(home, "Music"), File(home, "Videos"))
                .filter { it.exists() && it.isDirectory }
        }
    }

    fun ensureDefaultFolders() {
        if (db.getWatchedFolders().isEmpty()) {
            defaultWindowsMediaFolders().forEach { db.addWatchedFolder(it.absolutePath) }
        }
    }

    suspend fun scanAllWatchedFolders(): List<MediaItem> = withContext(Dispatchers.IO) {
        ensureDefaultFolders()
        val folders = db.getWatchedFolders()
        val discovered = mutableListOf<MediaItem>()
        val existingPaths = HashSet<String>()

        for (folderPath in folders) {
            val dir = File(folderPath)
            if (!dir.exists() || !dir.isDirectory) continue
            scanDirectory(dir, discovered, existingPaths)
        }

        if (discovered.isNotEmpty()) {
            db.upsertMediaItems(discovered)
        }
        db.removeMissingFiles(existingPaths)
        db.getAllMedia()
    }

    suspend fun importFiles(files: List<File>): List<MediaItem> = withContext(Dispatchers.IO) {
        val imported = mutableListOf<MediaItem>()
        for (file in files) {
            if (!file.exists()) continue
            if (file.isDirectory) {
                db.addWatchedFolder(file.absolutePath)
                scanDirectory(file, imported, HashSet())
            } else {
                parseMediaFile(file)?.let { imported.add(it) }
            }
        }
        if (imported.isNotEmpty()) {
            db.upsertMediaItems(imported)
        }
        imported
    }

    private fun scanDirectory(
        root: File,
        out: MutableList<MediaItem>,
        seenPaths: MutableSet<String>
    ) {
        root.walkTopDown()
            .onEnter { dir -> !dir.isHidden && !dir.name.startsWith(".") }
            .filter { it.isFile }
            .forEach { file ->
                val item = parseMediaFile(file)
                if (item != null) {
                    seenPaths.add(item.filePath)
                    out.add(item)
                }
            }
    }

    fun parseMediaFile(file: File): MediaItem? {
        val ext = file.extension.lowercase()
        val mediaType = when {
            ext in AUDIO_EXTENSIONS -> MediaType.AUDIO
            ext in VIDEO_EXTENSIONS -> MediaType.VIDEO
            else -> return null
        }

        val canonical = file.absolutePath
        val parent = file.parentFile
        var title = file.nameWithoutExtension.ifBlank { file.name }
        var artist: String? = null
        var album: String? = null
        var durationMs = 0L

        if (mediaType == MediaType.AUDIO) {
            try {
                val audioFile = AudioFileIO.read(file)
                val header = audioFile.audioHeader
                if (header != null && header.trackLength > 0) {
                    durationMs = header.trackLength * 1000L
                }
                val tag = audioFile.tag
                if (tag != null) {
                    tag.getFirst(FieldKey.TITLE)?.takeIf { it.isNotBlank() }?.let { title = it.trim() }
                    tag.getFirst(FieldKey.ARTIST)?.takeIf { it.isNotBlank() }?.let { artist = it.trim() }
                    tag.getFirst(FieldKey.ALBUM)?.takeIf { it.isNotBlank() }?.let { album = it.trim() }
                }
            } catch (_: Exception) {
                // Unsupported or corrupt tag; keep filename-based metadata
            }
        }

        return MediaItem(
            filePath = canonical,
            title = title,
            displayName = file.name,
            artist = artist,
            album = album,
            durationMs = durationMs,
            sizeBytes = file.length(),
            dateAdded = file.lastModified(),
            dateModified = file.lastModified(),
            mimeType = "${mediaType.name.lowercase()}/$ext",
            folderPath = parent?.absolutePath ?: "",
            folderName = parent?.name ?: "",
            mediaType = mediaType
        )
    }
}
