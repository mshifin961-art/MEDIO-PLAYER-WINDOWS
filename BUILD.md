# Build & Packaging Guide

## Prerequisites

- **Windows 10 / 11 (x64)**
- **JDK 21** (Eclipse Temurin 21 x64 recommended, includes `jpackage`)
- **Gradle 8.10.2+** (or Gradle Wrapper)
- **VLC 64-bit** (`C:\Program Files\VideoLAN\VLC`) for runtime LibVLC libraries (bundled into the distributable/installer during packaging)
- **Inno Setup 6** (`iscc.exe`) to build the `.exe` Windows Installer

## Build Commands

```powershell
# Compile and run unit/integration tests
gradle clean test

# Run the desktop application directly
gradle run

# Create standalone Windows x64 app image (bundled JRE + app)
gradle createDistributable

# Build Windows x64 Installer (.exe) via Inno Setup
iscc installer\medio_player.iss
```

The generated installer is output to `installer\Output\MEDIO-PLAYER-Windows-x64-Setup-1.0.0.exe`.
