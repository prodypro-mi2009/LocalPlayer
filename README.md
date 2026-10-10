# LocalPlayer

**LocalPlayer is an offline, audio-only music player for Android**, built with Kotlin, Jetpack Compose, Material 3, AndroidX Media3, and Room.

It is designed for people who want to listen to music stored on their device, manage their personal music library, and control playback without depending on a streaming service. Music files remain on the device, and the app's core playback functionality does not require an internet connection.

## Features

### Music playback and library
- Local audio playback for MP3, FLAC, M4A, and AAC files.
- Music library browsing and search.
- Playback queue management.
- Playlists and favorite tracks.
- Persistent playback history and library data.
- Optional fast library scanning using Android MediaStore.

### Metadata, artwork, and lyrics
- Editing track metadata and embedded album artwork.
- Support for synchronized LRC lyrics and plain text lyrics.
- Support for storing supported metadata and lyrics in the music files themselves.

### Playback and appearance
- Sleep timer with configurable playback stopping.
- Light, dark, and system theme options.
- Optional dynamic colors for a more personalized interface.

## Data and privacy

LocalPlayer is designed around a local music library rather than a cloud-based streaming catalog.

- **Local files:** Music files on the device are the source of truth for their embedded metadata, lyrics, and artwork.
- **Local database:** Room stores the library index and app data, including playlists, favorites, and playback history.
- **Offline playback:** Core music playback does not require an internet connection.
- **No streaming catalog required:** The app is intended for music stored locally on your device.

Some optional functionality may use an internet connection if implemented or enabled. Offline playback does not imply that every optional feature is available offline.

## Installation

To install a released version:

1. Open the repository's **Releases** page.
2. Download the release APK, such as `LocalPlayer-v1.0.2-release.apk`, if it is available.
3. Open the downloaded APK on your Android device and follow the installation instructions.
4. If Android asks for permission to install apps from that source, review the source and enable the permission only if you trust the APK.

Only install APK files from sources you trust.

## Technology

- **Kotlin** — application programming language.
- **Jetpack Compose** — declarative Android UI toolkit.
- **Material 3** — interface design system.
- **AndroidX Media3** — media playback infrastructure.
- **Room** — local database persistence.
- **Gradle** — build system.

## Building from source

### Requirements

- Android Studio.
- A compatible Android SDK.
- A compatible JDK configured for the project's Gradle version.
- The project source code.

Open the project in Android Studio and allow Gradle to synchronize before building.

### Build a debug APK on Windows

From the project root, run:

```powershell
.\gradlew.bat assembleDebug
```

### Run local unit tests

```powershell
.\gradlew.bat testDebugUnitTest
```

### Run connected Android tests

Connect a compatible Android device or start an emulator, then run:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

The availability and results of connected tests depend on the configured device or emulator.

## Project status

LocalPlayer is under active development. Features, compatibility, and test coverage may change between versions. Not every feature or device configuration is necessarily covered by automated tests.

## License

Check the repository for license information before redistributing or modifying this project. If no license has been provided, the source code is not automatically granted an open-source license.

This project is under active development. Features and test coverage may change between versions.
