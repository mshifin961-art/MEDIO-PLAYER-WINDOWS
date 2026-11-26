package com.mediop.win.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.mediop.win.core.model.*
import com.mediop.win.data.db.DatabaseManager
import com.sun.jna.NativeLibrary
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.CallbackVideoSurface
import uk.co.caprica.vlcj.player.embedded.videosurface.VideoSurfaceAdapters
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallbackAdapter
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.io.File
import java.nio.ByteBuffer
import kotlin.random.Random

/**
 * Unified LibVLC-backed Audio & Video playback engine for Windows.
 * Renders video frames via CallbackVideoSurface -> Skia ImageBitmap so Compose
 * glassmorphic overlays, controls, and dialogs layer cleanly over video.
 */
class MediaEngine(
    private val db: DatabaseManager
) : AutoCloseable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var isLibVlcAvailable by mutableStateOf(false)
        private set
    var libVlcStatusMessage by mutableStateOf("Initializing media engine...")
        private set

    private var factory: MediaPlayerFactory? = null
    private var mediaPlayer: EmbeddedMediaPlayer? = null

    private val _currentMedia = MutableStateFlow<MediaItem?>(null)
    val currentMedia: StateFlow<MediaItem?> = _currentMedia.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isVideoActive = MutableStateFlow(false)
    val isVideoActive: StateFlow<Boolean> = _isVideoActive.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _volume = MutableStateFlow(80)
    val volume: StateFlow<Int> = _volume.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _aspectRatio = MutableStateFlow(AspectRatioMode.FIT)
    val aspectRatio: StateFlow<AspectRatioMode> = _aspectRatio.asStateFlow()

    private val _queue = MutableStateFlow<List<MediaItem>>(emptyList())
    val queue: StateFlow<List<MediaItem>> = _queue.asStateFlow()

    private val _audioTracks = MutableStateFlow<List<TrackInfo>>(emptyList())
    val audioTracks: StateFlow<List<TrackInfo>> = _audioTracks.asStateFlow()

    private val _subtitleTracks = MutableStateFlow<List<TrackInfo>>(emptyList())
    val subtitleTracks: StateFlow<List<TrackInfo>> = _subtitleTracks.asStateFlow()

    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()

    var currentVideoFrame by mutableStateOf<ImageBitmap?>(null)
        private set

    private var frameBuffer: ByteArray = ByteArray(0)
    private var skiaBitmap: Bitmap? = null
    private var frameWidth = 0
    private var frameHeight = 0

    private var tickerJob: Job? = null
    private var lastHistorySaveMs = 0L

    init {
        initializeVlc()
    }

    private fun initializeVlc() {
        try {
            val discoveredDir = findVlcDirectory()
            if (discoveredDir != null) {
                NativeLibrary.addSearchPath("libvlc", discoveredDir.absolutePath)
                NativeLibrary.addSearchPath("libvlccore", discoveredDir.absolutePath)
            } else {
                NativeDiscovery().discover()
            }

            val vlcArgs = mutableListOf(
                "--no-video-title-show",
                "--quiet"
            )
            if (discoveredDir != null) {
                val plugins = File(discoveredDir, "plugins")
                if (plugins.exists()) {
                    vlcArgs.add("--plugin-path=${plugins.absolutePath}")
                }
            }

            val vlcFactory = MediaPlayerFactory(*vlcArgs.toTypedArray())
            val player = vlcFactory.mediaPlayers().newEmbeddedMediaPlayer()

            val bufferFormatCallback = object : BufferFormatCallbackAdapter() {
                override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
                    val w = sourceWidth.coerceAtLeast(1)
                    val h = sourceHeight.coerceAtLeast(1)
                    return RV32BufferFormat(w, h)
                }

                override fun allocatedBuffers(buffers: Array<ByteBuffer>) {
                    // Allocated per frame callback
                }
            }

            val renderCallback = RenderCallback { _, nativeBuffers, bufferFormat ->
                val w = bufferFormat.width
                val h = bufferFormat.height
                if (w <= 0 || h <= 0 || nativeBuffers.isEmpty()) return@RenderCallback
                val buf = nativeBuffers[0]
                val needed = w * h * 4
                if (frameBuffer.size != needed) {
                    frameBuffer = ByteArray(needed)
                }
                buf.rewind()
                buf.get(frameBuffer, 0, minOf(needed, buf.remaining()))

                val bmp = skiaBitmap?.takeIf { frameWidth == w && frameHeight == h }
                    ?: Bitmap().apply {
                        allocPixels(ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.PREMUL))
                        frameWidth = w
                        frameHeight = h
                        skiaBitmap = this
                    }
                bmp.installPixels(frameBuffer)
                val composeBmp = bmp.asImageBitmap()
                scope.launch {
                    currentVideoFrame = composeBmp
                }
            }

            val videoSurface = CallbackVideoSurface(
                bufferFormatCallback,
                renderCallback,
                true,
                VideoSurfaceAdapters.getVideoSurfaceAdapter()
            )
            player.videoSurface().set(videoSurface)

            player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
                override fun playing(mediaPlayer: MediaPlayer) {
                    scope.launch {
                        _isPlaying.value = true
                        _playbackError.value = null
                        val len = mediaPlayer.status().length().coerceAtLeast(0L)
                        if (len > 0) _durationMs.value = len
                        refreshTracks()
                        startTicker()
                    }
                }

                override fun paused(mediaPlayer: MediaPlayer) {
                    scope.launch {
                        _isPlaying.value = false
                        saveCurrentProgress(force = true)
                    }
                }

                override fun stopped(mediaPlayer: MediaPlayer) {
                    scope.launch {
                        _isPlaying.value = false
                        stopTicker()
                    }
                }

                override fun finished(mediaPlayer: MediaPlayer) {
                    scope.launch {
                        _isPlaying.value = false
                        onTrackFinished()
                    }
                }

                override fun error(mediaPlayer: MediaPlayer) {
                    scope.launch {
                        _isPlaying.value = false
                        _playbackError.value = "Unable to play media file (unsupported format or corrupted file)."
                    }
                }
            })

            factory = vlcFactory
            mediaPlayer = player
            isLibVlcAvailable = true
            libVlcStatusMessage = "LibVLC ready"
        } catch (t: Throwable) {
            isLibVlcAvailable = false
            libVlcStatusMessage = "LibVLC not found: ${t.message ?: "Install VLC 64-bit"}"
        }
    }

    private fun findVlcDirectory(): File? {
        val candidates = listOfNotNull(
            System.getProperty("compose.application.resources.dir")?.let { File(it, "vlc") },
            File("vlc"),
            File("app/vlc"),
            File("C:\\Program Files\\VideoLAN\\VLC"),
            File("C:\\Program Files (x86)\\VideoLAN\\VLC")
        )
        return candidates.firstOrNull { dir ->
            dir.exists() && File(dir, "libvlc.dll").exists()
        }
    }

    fun playMedia(
        item: MediaItem,
        newQueue: List<MediaItem> = listOf(item),
        resumeFromHistory: Boolean = false
    ) {
        val player = mediaPlayer
        if (player == null || !isLibVlcAvailable) {
            _playbackError.value = libVlcStatusMessage
            return
        }

        val file = File(item.filePath)
        if (!file.exists()) {
            _playbackError.value = "File not found: ${item.filePath}"
            return
        }

        saveCurrentProgress(force = true)

        _currentMedia.value = item
        _queue.value = if (newQueue.isNotEmpty()) newQueue else listOf(item)
        _isVideoActive.value = item.mediaType == MediaType.VIDEO
        _playbackError.value = null
        _positionMs.value = 0L
        _durationMs.value = item.durationMs
        if (item.mediaType == MediaType.AUDIO) {
            currentVideoFrame = null
        }

        val startPos = if (resumeFromHistory) {
            db.getLastPosition(item.filePath).takeIf { it > 2000L } ?: 0L
        } else 0L

        val started = player.media().play(file.absolutePath)
        if (!started) {
            _playbackError.value = "Failed to start playback for ${item.title}"
            return
        }

        player.audio().setVolume(_volume.value)
        player.controls().setRate(_playbackSpeed.value)
        if (startPos > 0L) {
            scope.launch {
                delay(250)
                player.controls().setTime(startPos)
                _positionMs.value = startPos
            }
        }

        db.upsertPlaybackHistory(
            PlaybackHistory(
                filePath = item.filePath,
                title = item.title,
                lastPositionMs = startPos,
                durationMs = item.durationMs,
                lastPlayedTimestamp = System.currentTimeMillis(),
                mediaType = item.mediaType,
                thumbnailPath = item.thumbnailPath
            )
        )
    }

    fun togglePlayPause() {
        val player = mediaPlayer ?: return
        if (player.status().isPlaying) {
            player.controls().pause()
        } else {
            player.controls().play()
        }
    }

    fun seekTo(positionMs: Long) {
        val player = mediaPlayer ?: return
        val clamped = positionMs.coerceIn(0L, _durationMs.value.coerceAtLeast(1L))
        player.controls().setTime(clamped)
        _positionMs.value = clamped
    }

    fun seekBy(deltaMs: Long) {
        seekTo(_positionMs.value + deltaMs)
    }

    fun setVolume(vol: Int) {
        val clamped = vol.coerceIn(0, 100)
        _volume.value = clamped
        mediaPlayer?.audio()?.setVolume(clamped)
    }

    fun setSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.25f, 2.0f)
        _playbackSpeed.value = clamped
        mediaPlayer?.controls()?.setRate(clamped)
    }

    fun toggleShuffle() {
        _isShuffle.value = !_isShuffle.value
    }

    fun cycleRepeatMode() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
    }

    fun setAspectRatio(mode: AspectRatioMode) {
        _aspectRatio.value = mode
        mediaPlayer?.video()?.setAspectRatio(mode.vlcRatio)
    }

    fun selectAudioTrack(trackId: Int) {
        mediaPlayer?.audio()?.setTrack(trackId)
        refreshTracks()
    }

    fun selectSubtitleTrack(trackId: Int) {
        mediaPlayer?.subpictures()?.setTrack(trackId)
        refreshTracks()
    }

    fun loadExternalSubtitle(file: File): Boolean {
        val player = mediaPlayer ?: return false
        if (!file.exists()) return false
        val added = player.subpictures().setSubTitleFile(file)
        if (added) refreshTracks()
        return added
    }

    fun skipNext() {
        val q = _queue.value
        val cur = _currentMedia.value ?: return
        if (q.isEmpty()) return

        val nextItem = if (_isShuffle.value && q.size > 1) {
            val others = q.filter { it.filePath != cur.filePath }
            others[Random.nextInt(others.size)]
        } else {
            val idx = q.indexOfFirst { it.filePath == cur.filePath }
            val nextIdx = if (idx == -1) 0 else (idx + 1) % q.size
            q[nextIdx]
        }
        playMedia(nextItem, q, resumeFromHistory = false)
    }

    fun skipPrevious() {
        if (_positionMs.value > 3000L) {
            seekTo(0L)
            return
        }
        val q = _queue.value
        val cur = _currentMedia.value ?: return
        if (q.isEmpty()) return
        val idx = q.indexOfFirst { it.filePath == cur.filePath }
        val prevIdx = if (idx <= 0) q.lastIndex else idx - 1
        playMedia(q[prevIdx], q, resumeFromHistory = false)
    }

    private fun onTrackFinished() {
        when (_repeatMode.value) {
            RepeatMode.ONE -> {
                _currentMedia.value?.let { playMedia(it, _queue.value, resumeFromHistory = false) }
            }
            RepeatMode.ALL -> skipNext()
            RepeatMode.OFF -> {
                val q = _queue.value
                val cur = _currentMedia.value
                val idx = if (cur != null) q.indexOfFirst { it.filePath == cur.filePath } else -1
                if (idx in 0 until q.lastIndex) {
                    playMedia(q[idx + 1], q, resumeFromHistory = false)
                } else {
                    saveCurrentProgress(force = true)
                }
            }
        }
    }

    fun stop() {
        saveCurrentProgress(force = true)
        stopTicker()
        mediaPlayer?.controls()?.stop()
        _isPlaying.value = false
        _isVideoActive.value = false
        _currentMedia.value = null
        _positionMs.value = 0L
        _durationMs.value = 0L
        currentVideoFrame = null
    }

    fun exitVideoPlayer() {
        saveCurrentProgress(force = true)
        stopTicker()
        mediaPlayer?.controls()?.stop()
        _isPlaying.value = false
        _isVideoActive.value = false
        currentVideoFrame = null
        if (_currentMedia.value?.mediaType == MediaType.VIDEO) {
            _currentMedia.value = null
        }
    }

    private fun refreshTracks() {
        val player = mediaPlayer ?: return
        try {
            val selectedAudio = player.audio().track()
            _audioTracks.value = player.audio().trackDescriptions().map { desc ->
                TrackInfo(id = desc.id(), name = desc.description() ?: "Track ${desc.id()}", isSelected = desc.id() == selectedAudio)
            }
            val selectedSub = player.subpictures().track()
            _subtitleTracks.value = player.subpictures().trackDescriptions().map { desc ->
                TrackInfo(id = desc.id(), name = desc.description() ?: "Subtitle ${desc.id()}", isSelected = desc.id() == selectedSub)
            }
        } catch (_: Throwable) {
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                val player = mediaPlayer ?: break
                if (player.status().isPlaying) {
                    val pos = player.status().time().coerceAtLeast(0L)
                    val len = player.status().length().coerceAtLeast(0L)
                    _positionMs.value = pos
                    if (len > 0) _durationMs.value = len

                    val now = System.currentTimeMillis()
                    if (now - lastHistorySaveMs >= 3000L) {
                        saveCurrentProgress(force = false)
                        lastHistorySaveMs = now
                    }
                }
                delay(250)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    fun saveCurrentProgress(force: Boolean) {
        val media = _currentMedia.value ?: return
        val pos = _positionMs.value
        val dur = _durationMs.value.coerceAtLeast(media.durationMs)
        if (!force && pos <= 0L) return
        db.upsertPlaybackHistory(
            PlaybackHistory(
                filePath = media.filePath,
                title = media.title,
                lastPositionMs = pos,
                durationMs = dur,
                lastPlayedTimestamp = System.currentTimeMillis(),
                mediaType = media.mediaType,
                thumbnailPath = media.thumbnailPath
            )
        )
    }

    private fun Bitmap.asImageBitmap(): ImageBitmap {
        return org.jetbrains.skia.Image.makeFromBitmap(this).toComposeImageBitmap()
    }

    override fun close() {
        saveCurrentProgress(force = true)
        stopTicker()
        scope.cancel()
        try {
            mediaPlayer?.controls()?.stop()
            mediaPlayer?.release()
            factory?.release()
        } catch (_: Throwable) {
        }
        mediaPlayer = null
        factory = null
    }
}
