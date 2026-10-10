# LocalPlayer

An offline, audio-only Android music player built with Kotlin, Jetpack Compose, Material 3, Media3, and Room.

## Features

- Local audio library supporting MP3, FLAC, M4A, and AAC.
- Music playback, queue management, playlists, and favorites.
- Search across the local music library.
- Track metadata and embedded artwork editing.
- Synchronized LRC lyrics and plain lyrics.
- Sleep timer with configurable playback stopping.
- Light, dark, and system themes, with optional dynamic colors.
- Optional MediaStore-based fast library scanning.
- Local playback history and persistent library data.

## Data and privacy

Local music files are the source of truth for embedded metadata, lyrics, and artwork. Room stores the library index and app data, including playlists, favorites, and playback history.

The app is designed for offline music playback and does not require an internet connection for its core functionality.

## Technology

- Kotlin
- Jetpack Compose
- Material 3
- AndroidX Media3
- Room
- Gradle

## Build

Open the project in Android Studio with a compatible Android SDK and JDK configured.

Build a debug APK on Windows:

```powershell
.\gradlew.bat assembleDebug
```

Run local unit tests:

```powershell
.\gradlew.bat testDebugUnitTest
```

Run connected Android tests on a configured device or emulator:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

## Project status

This project is under active development. Features and test coverage may change between versions.
