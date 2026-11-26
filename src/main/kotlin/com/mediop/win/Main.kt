package com.mediop.win

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import com.mediop.win.core.model.NavSection
import com.mediop.win.core.theme.DarkBackground
import com.mediop.win.core.theme.MedioPlayerTheme
import com.mediop.win.state.AppState
import com.mediop.win.ui.components.*
import com.mediop.win.ui.screens.*
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDropEvent
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

fun main(args: Array<String>) = application {
    val appState = remember { AppState() }
    val settings by appState.settings.collectAsState()
    val isVideoActive by appState.engine.isVideoActive.collectAsState()

    // Support opening a media file directly via CLI / Windows file association
    LaunchedEffect(Unit) {
        val initialFiles = args.map(::File).filter { it.exists() }
        if (initialFiles.isNotEmpty()) {
            appState.importFiles(initialFiles, playImmediately = true)
        }
    }

    val windowState = rememberWindowState(
        width = 1280.dp,
        height = 800.dp,
        placement = if (appState.isFullscreen) WindowPlacement.Fullscreen else WindowPlacement.Floating
    )

    LaunchedEffect(appState.isFullscreen) {
        windowState.placement = if (appState.isFullscreen) WindowPlacement.Fullscreen else WindowPlacement.Floating
    }

    val iconPainter = runCatching { painterResource("icons/medio_player.png") }.getOrNull()

    fun pickFolder() {
        val chooser = JFileChooser().apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            dialogTitle = "Select Media Folder"
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile?.let { appState.addFolderAndScan(it) }
        }
    }

    fun pickMediaFiles() {
        val chooser = JFileChooser().apply {
            fileSelectionMode = JFileChooser.FILES_ONLY
            isMultiSelectionEnabled = true
            dialogTitle = "Open Audio or Video Files"
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            val files = chooser.selectedFiles?.toList().orEmpty()
            if (files.isNotEmpty()) appState.importFiles(files, playImmediately = true)
        }
    }

    fun pickSubtitleFile() {
        val chooser = JFileChooser().apply {
            fileSelectionMode = JFileChooser.FILES_ONLY
            dialogTitle = "Load Subtitle File"
            fileFilter = FileNameExtensionFilter("Subtitles (*.srt, *.vtt, *.ass, *.ssa)", "srt", "vtt", "ass", "ssa")
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile?.let { file ->
                val ok = appState.engine.loadExternalSubtitle(file)
                appState.statusBanner = if (ok) "Loaded subtitle: ${file.name}" else "Failed to load subtitle"
            }
        }
    }

    if (appState.isMiniPlayerMode) {
        Window(
            onCloseRequest = {
                appState.close()
                exitApplication()
            },
            title = "MEDIO PLAYER — Mini",
            icon = iconPainter,
            alwaysOnTop = true,
            resizable = false,
            state = rememberWindowState(width = 420.dp, height = 165.dp)
        ) {
            MedioPlayerTheme(themeMode = settings.themeMode) {
                MiniPlayerContent(
                    appState = appState,
                    onExpand = { appState.isMiniPlayerMode = false }
                )
            }
        }
    } else {
        Window(
            onCloseRequest = {
                appState.close()
                exitApplication()
            },
            title = "MEDIO PLAYER — Windows Edition",
            icon = iconPainter,
            state = windowState,
            onKeyEvent = { event ->
                if (event.type == KeyEventType.KeyDown) {
                    handleGlobalShortcut(event, appState)
                } else false
            }
        ) {
            // Enable native Windows Drag-and-Drop for files and folders
            DisposableEffect(window) {
                val target = DropTarget(window, DnDConstants.ACTION_COPY, object : DropTargetAdapter() {
                    override fun drop(dtde: DropTargetDropEvent) {
                        try {
                            dtde.acceptDrop(DnDConstants.ACTION_COPY)
                            val transferable = dtde.transferable
                            if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                                @Suppress("UNCHECKED_CAST")
                                val dropped = transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
                                appState.importFiles(dropped, playImmediately = true)
                                dtde.dropComplete(true)
                            } else {
                                dtde.dropComplete(false)
                            }
                        } catch (_: Exception) {
                            dtde.dropComplete(false)
                        }
                    }
                })
                onDispose { target.isActive = false }
            }

            MedioPlayerTheme(themeMode = settings.themeMode) {
                val snackbarHostState = remember { SnackbarHostState() }
                var showSearch by remember { mutableStateOf(false) }

                LaunchedEffect(appState.statusBanner) {
                    appState.statusBanner?.let { msg ->
                        snackbarHostState.showSnackbar(msg)
                        appState.statusBanner = null
                    }
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    containerColor = DarkBackground
                ) { padding ->
                    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                        if (isVideoActive) {
                            VideoPlayerOverlay(
                                appState = appState,
                                onPickSubtitleFile = ::pickSubtitleFile,
                                onToggleFullscreen = { appState.isFullscreen = !appState.isFullscreen }
                            )
                        } else if (appState.showAudioPlayerOverlay) {
                            AudioPlayerOverlay(
                                appState = appState,
                                onClose = { appState.showAudioPlayerOverlay = false }
                            )
                        } else {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                    DesktopSidebar(
                                        currentSection = appState.currentSection,
                                        onSelectSection = { section ->
                                            showSearch = false
                                            appState.currentSection = section
                                            appState.selectedFolderPath = null
                                            appState.selectedAlbumName = null
                                            appState.selectedArtistName = null
                                            appState.selectedPlaylistId = null
                                        },
                                        onOpenSearch = { showSearch = !showSearch },
                                        onImportFiles = ::pickMediaFiles,
                                        onToggleMiniPlayer = { appState.isMiniPlayerMode = true }
                                    )

                                    Box(modifier = Modifier.weight(1f).fillMaxHeight().background(DarkBackground)) {
                                        if (showSearch) {
                                            SearchScreen(appState)
                                        } else {
                                            when (appState.currentSection) {
                                                NavSection.HOME -> HomeScreen(appState, ::pickFolder, ::pickMediaFiles)
                                                NavSection.MUSIC -> MusicLibraryScreen(appState)
                                                NavSection.VIDEOS -> VideoLibraryScreen(appState)
                                                NavSection.ALBUMS -> AlbumsScreen(appState)
                                                NavSection.ARTISTS -> ArtistsScreen(appState)
                                                NavSection.FOLDERS -> FoldersScreen(appState, ::pickFolder)
                                                NavSection.PLAYLISTS -> PlaylistsScreen(appState)
                                                NavSection.RECENTLY_PLAYED -> RecentlyPlayedScreen(appState)
                                                NavSection.FAVORITES -> FavoritesScreen(appState)
                                                NavSection.SETTINGS -> SettingsScreen(appState, ::pickFolder)
                                            }
                                        }
                                    }

                                    if (appState.showQueuePanel) {
                                        QueueSidePanel(appState, onClose = { appState.showQueuePanel = false })
                                    }
                                }

                                BottomPlayerBar(
                                    appState = appState,
                                    onOpenNowPlaying = { appState.showAudioPlayerOverlay = true },
                                    onToggleQueue = { appState.showQueuePanel = !appState.showQueuePanel }
                                )
                            }
                        }

                        // Dialogs
                        if (appState.showCreatePlaylistDialog) {
                            CreatePlaylistDialog(
                                onDismiss = { appState.showCreatePlaylistDialog = false },
                                onCreate = { appState.createPlaylist(it) }
                            )
                        }
                        appState.renamePlaylistTarget?.let { target ->
                            RenamePlaylistDialog(
                                playlist = target,
                                onDismiss = { appState.renamePlaylistTarget = null },
                                onRename = { appState.renamePlaylist(target.id, it) }
                            )
                        }
                        appState.playlistPickerTarget?.let { media ->
                            val playlists by appState.playlists.collectAsState()
                            PlaylistPickerDialog(
                                item = media,
                                playlists = playlists,
                                onSelectPlaylist = { pl -> appState.addItemToPlaylist(pl.id, media) },
                                onCreateNew = {
                                    appState.playlistPickerTarget = null
                                    appState.showCreatePlaylistDialog = true
                                },
                                onDismiss = { appState.playlistPickerTarget = null }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun handleGlobalShortcut(event: KeyEvent, appState: AppState): Boolean {
    val engine = appState.engine
    return when {
        event.isCtrlPressed && event.key == Key.M -> {
            appState.isMiniPlayerMode = !appState.isMiniPlayerMode
            true
        }
        event.key == Key.Spacebar -> {
            engine.togglePlayPause()
            true
        }
        event.key == Key.DirectionRight -> {
            engine.seekBy(10_000L)
            true
        }
        event.key == Key.DirectionLeft -> {
            engine.seekBy(-10_000L)
            true
        }
        event.key == Key.DirectionUp -> {
            engine.setVolume(engine.volume.value + 5)
            true
        }
        event.key == Key.DirectionDown -> {
            engine.setVolume(engine.volume.value - 5)
            true
        }
        event.key == Key.F -> {
            appState.isFullscreen = !appState.isFullscreen
            true
        }
        event.key == Key.M -> {
            engine.setVolume(if (engine.volume.value > 0) 0 else 80)
            true
        }
        event.key == Key.Escape -> {
            when {
                appState.isFullscreen -> appState.isFullscreen = false
                appState.engine.isVideoActive.value -> appState.engine.exitVideoPlayer()
                appState.showAudioPlayerOverlay -> appState.showAudioPlayerOverlay = false
            }
            true
        }
        else -> false
    }
}
