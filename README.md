# MEDIO PLAYER — Windows Edition

A native, offline-first **Windows 10/11 (x64)** desktop media player built with **Kotlin**, **Compose Multiplatform Desktop**, **LibVLC (`vlcj`)**, and **SQLite**, designed as the desktop counterpart to the Android [MEDIO PLAYER](https://github.com/mshifin961-art/NexPlay-shifin-) app.

## Features

- **Music & Video Playback**: Powered by LibVLC (`vlcj`) with hardware-friendly frame rendering inside Compose Desktop.
- **Audio Library**: Queue management, shuffle, repeat (`Off`, `All`, `One`), volume, speed (`0.25x`–`2.0x`), embedded album art extraction (`jaudiotagger`), Albums & Artists grouping.
- **Video Player**: Fullscreen playback, seek (`±10s`), playback speed, multi-track audio switching, embedded & external subtitle loading (`.srt`, `.vtt`, `.ass`, `.ssa`), aspect ratio modes (`Fit`, `Fill`, `16:9`, `4:3`, `21:9`), and automatic resume from last position.
- **Media Library**: Watches Windows `Music` and `Videos` folders by default plus custom user folders, detects new/removed files, real-time search (`Ctrl+F`), and multi-criteria sorting.
- **Playlists & Favorites**: Create, rename, delete playlists; add, remove, and reorder tracks; persistent favorites and playback history.
- **Desktop Experience**: Resizable window, Mini Player (`Ctrl+M`), global keyboard shortcuts, drag-and-drop file/folder import, and CLI / Windows file association launch support.
- **Theme**: Cinematic Dark (`#0C0D11` + `#00E5FF` Cyan) and Pure AMOLED (`#000000`) glassmorphic UI.

## Documentation

- [Architecture](ARCHITECTURE.md)
- [Build & Packaging Guide](BUILD.md)
- [Feature Checklist](FEATURES.md)
- [Testing Report](TESTING.md)
