# Testing Documentation

## Automated Test Suite (`MedioPlayerTests.kt`)

- `format utils formats duration, size, and resolution badges correctly`
- `database migrations and media deduplication work`
- `playlist CRUD and reordering persist across restarts`
- `favorites, playback history, and settings persist`
- `media scanner imports supported files and removes missing files`
- `thumbnail service handles missing or corrupt files safely`

Run with:
```powershell
gradle test
```
