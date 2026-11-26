# Architecture — MEDIO PLAYER Windows Edition

## Overview

```
Presentation Layer (Compose Multiplatform Desktop + Material 3)
  ├── Main.kt (Window, Mini Player, Shortcuts, Drag & Drop, File Args)
  ├── ui/screens/* (Home, Music, Videos, Albums, Artists, Folders, Playlists, Favorites, History, Search, Settings, Overlays)
  └── ui/components/* (Sidebar, BottomPlayerBar, MediaCards, Dialogs)
        ↓
State Layer
  └── state/AppState.kt (StateFlows, sorting, grouping, library orchestration)
        ↓
Media & Data Layers
  ├── player/MediaEngine.kt (vlcj + LibVLC CallbackVideoSurface -> Skia ImageBitmap)
  ├── data/thumbnail/ThumbnailService.kt (jaudiotagger album art + headless LibVLC video frame cache)
  ├── data/scanner/MediaScanner.kt (folder walker + jaudiotagger metadata + missing file cleanup)
  └── data/db/DatabaseManager.kt (SQLite WAL + versioned migrations at %LOCALAPPDATA%\MedioPlayer\medio_player.db)
```

## Database Schema (SQLite v1)

- `schema_version(version)`
- `watched_folders(path, name, added_at)`
- `media_items(id, file_path UNIQUE, title, display_name, artist, album, duration_ms, size_bytes, date_added, date_modified, mime_type, width, height, folder_path, folder_name, media_type, thumbnail_path)`
- `favorites(file_path, date_added)`
- `playlists(id, name, created_at, modified_at)`
- `playlist_items(id, playlist_id FK CASCADE, file_path, title, artist, album, duration_ms, media_type, order_index)`
- `playback_history(file_path, title, last_position_ms, duration_ms, last_played_ts, media_type, thumbnail_path)`
- `app_settings(key, value)`
