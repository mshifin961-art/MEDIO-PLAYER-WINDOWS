package com.mediop.win.data.thumbnail

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.mediop.win.core.model.MediaItem
import com.mediop.win.core.model.MediaType
import com.mediop.win.core.util.FormatUtils
import com.mediop.win.data.db.DatabaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jetbrains.skia.Image as SkiaImage
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

/**
 * Persistent disk-backed and in-memory LRU thumbnail & album artwork service.
 * - Audio: extracts embedded artwork via jaudiotagger, scales, and caches as PNG.
 * - Video: captures frame via headless LibVLC media player, scales, and caches as PNG.
 */
class ThumbnailService(
    private val cacheDir: File = File(DatabaseManager.defaultDataDir(), "thumbnails"),
    private val db: DatabaseManager? = null
) : AutoCloseable {

    private val memoryCache: MutableMap<String, ImageBitmap> = Collections.synchronizedMap(
        object : LinkedHashMap<String, ImageBitmap>(128, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean {
                return size > 200
            }
        }
    )

    private val failedPaths = Collections.synchronizedSet(HashSet<String>())
    private val extractionSemaphore = Semaphore(2)
    private var headlessVlcFactory: MediaPlayerFactory? = null

    init {
        cacheDir.mkdirs()
    }

    fun getCachedThumbnailFile(filePath: String): File {
        val hash = FormatUtils.sha256(filePath)
        return File(cacheDir, "$hash.png")
    }

    suspend fun getThumbnail(item: MediaItem, libVlcAvailable: Boolean): ImageBitmap? {
        memoryCache[item.filePath]?.let { return it }
        if (item.filePath in failedPaths) return null

        return withContext(Dispatchers.IO) {
            memoryCache[item.filePath]?.let { return@withContext it }

            val diskFile = item.thumbnailPath?.let(::File)?.takeIf { it.exists() && it.length() > 0 }
                ?: getCachedThumbnailFile(item.filePath).takeIf { it.exists() && it.length() > 0 }

            if (diskFile != null) {
                loadBitmapFromFile(diskFile)?.let { bmp ->
                    memoryCache[item.filePath] = bmp
                    return@withContext bmp
                }
            }

            extractionSemaphore.withPermit {
                // Double check after acquiring permit
                val existing = getCachedThumbnailFile(item.filePath)
                if (existing.exists() && existing.length() > 0) {
                    loadBitmapFromFile(existing)?.let { bmp ->
                        memoryCache[item.filePath] = bmp
                        return@withPermit bmp
                    }
                }

                val targetFile = getCachedThumbnailFile(item.filePath)
                val extracted = when (item.mediaType) {
                    MediaType.AUDIO -> extractAudioArtwork(File(item.filePath), targetFile)
                    MediaType.VIDEO -> if (libVlcAvailable) {
                        extractVideoThumbnail(File(item.filePath), targetFile)
                    } else {
                        false
                    }
                }

                if (extracted && targetFile.exists() && targetFile.length() > 0) {
                    db?.updateMediaThumbnailAndDuration(
                        filePath = item.filePath,
                        thumbnailPath = targetFile.absolutePath,
                        durationMs = 0L
                    )
                    loadBitmapFromFile(targetFile)?.let { bmp ->
                        memoryCache[item.filePath] = bmp
                        return@withPermit bmp
                    }
                }

                failedPaths.add(item.filePath)
                null
            }
        }
    }

    fun extractAudioArtwork(audioFile: File, outputPng: File): Boolean {
        if (!audioFile.exists()) return false
        return try {
            val parsed = AudioFileIO.read(audioFile)
            val artwork = parsed.tag?.firstArtwork ?: return false
            val bytes = artwork.binaryData ?: return false
            val image = ImageIO.read(ByteArrayInputStream(bytes)) ?: return false
            val scaled = scaleImage(image, 320, 320)
            ImageIO.write(scaled, "png", outputPng)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun getOrCreateHeadlessFactory(): MediaPlayerFactory? {
        if (headlessVlcFactory != null) return headlessVlcFactory
        return try {
            headlessVlcFactory = MediaPlayerFactory(
                "--intf", "dummy",
                "--vout", "dummy",
                "--no-audio",
                "--no-video-title-show",
                "--quiet"
            )
            headlessVlcFactory
        } catch (_: Throwable) {
            null
        }
    }

    fun extractVideoThumbnail(videoFile: File, outputPng: File): Boolean {
        if (!videoFile.exists()) return false
        val factory = getOrCreateHeadlessFactory() ?: return false
        var player: MediaPlayer? = null
        return try {
            player = factory.mediaPlayers().newMediaPlayer()
            val readyLatch = CountDownLatch(1)
            val snapLatch = CountDownLatch(1)
            var durationMs = 0L
            var width = 0
            var height = 0

            player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
                override fun playing(mediaPlayer: MediaPlayer) {
                    readyLatch.countDown()
                }

                override fun error(mediaPlayer: MediaPlayer) {
                    readyLatch.countDown()
                    snapLatch.countDown()
                }

                override fun snapshotTaken(mediaPlayer: MediaPlayer, filename: String) {
                    snapLatch.countDown()
                }
            })

            if (!player.media().play(videoFile.absolutePath)) {
                return false
            }

            if (!readyLatch.await(4, TimeUnit.SECONDS)) {
                return false
            }

            Thread.sleep(250)
            durationMs = player.status().length().coerceAtLeast(0L)
            val dim = player.video().videoDimension()
            if (dim != null) {
                width = dim.width
                height = dim.height
            }

            if (durationMs > 4000L) {
                player.controls().setPosition(0.15f)
                Thread.sleep(300)
            }

            val saved = player.snapshots().save(outputPng, 480, 0)
            if (saved) {
                snapLatch.await(3, TimeUnit.SECONDS)
            }

            if (durationMs > 0 || width > 0) {
                db?.updateMediaThumbnailAndDuration(
                    filePath = videoFile.absolutePath,
                    thumbnailPath = if (outputPng.exists()) outputPng.absolutePath else null,
                    durationMs = durationMs,
                    width = width,
                    height = height
                )
            }

            outputPng.exists() && outputPng.length() > 0
        } catch (_: Throwable) {
            false
        } finally {
            try {
                player?.controls()?.stop()
                player?.release()
            } catch (_: Throwable) {
            }
        }
    }

    private fun loadBitmapFromFile(file: File): ImageBitmap? = try {
        val bytes = file.readBytes()
        SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
    } catch (_: Exception) {
        null
    }

    private fun scaleImage(src: BufferedImage, maxW: Int, maxH: Int): BufferedImage {
        val ratio = minOf(maxW.toDouble() / src.width, maxH.toDouble() / src.height, 1.0)
        val w = (src.width * ratio).toInt().coerceAtLeast(1)
        val h = (src.height * ratio).toInt().coerceAtLeast(1)
        val dst = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        val g = dst.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(src, 0, 0, w, h, null)
        g.dispose()
        return dst
    }

    fun clearCache() {
        memoryCache.clear()
        failedPaths.clear()
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    override fun close() {
        try {
            headlessVlcFactory?.release()
            headlessVlcFactory = null
        } catch (_: Throwable) {
        }
    }
}
