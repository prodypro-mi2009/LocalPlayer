# LocalPlayer — A Modern Offline Music Player for Android

**LocalPlayer** is a native Android music player designed for people who want full control over their local music library, a clean modern interface, and useful playback features without the overhead of a streaming platform.

Built with **Kotlin, Jetpack Compose, Material 3, Room, and Android Media3**, LocalPlayer focuses on local music playback, library organization, personalization, listening statistics, and a customizable user experience.

The goal is simple: deliver a polished, lightweight music player that keeps your music and listening experience under your control.

## ✨ Features

### 🎵 Local Music Library
- Browse and play music stored on your Android device.
- Support for MP3, FLAC, M4A, and AAC audio files.
- Organize your collection through songs, albums, artists, favorites, and playlists.
- Search your library with support for Greek text and accent-insensitive matching.
- Scan and index your music library for convenient access.
- Hide unwanted tracks from relevant library views.

### ▶️ Playback Experience
- Background playback powered by Android Media3 and ExoPlayer.
- Media session integration for Android playback controls.
- Queue management with support for repeated tracks.
- Recently played history and playback navigation.
- Mini-player and expanded Now Playing interface.
- Playback progress and queue controls designed for everyday use.

### 🎨 Modern Interface
- Native Jetpack Compose interface using Material 3.
- Clean, artwork-focused music screens.
- Dark interface with a true-black visual direction.
- Artwork-aware colors and customizable local background options.
- Dedicated settings and metadata editing interfaces.
- A mobile-first design inspired by modern Android music players.

### ❤️ Favorites, Playlists & Queue
- Mark tracks as favorites for quick access.
- Create and manage playlists.
- Control the playback queue.
- Preserve individual queue entries when the same track appears more than once.
- Undo supported queue changes without unnecessarily restarting the current track.

### 📊 Listening Statistics
- Track actual playback time through the playback service.
- View listening time across daily, weekly, monthly, and yearly periods.
- Explore your most-played tracks with Top 20 lists.
- Maintain listening statistics locally using a Room database.
- Separate listening duration from qualifying play counts.

### 🎧 Your Mix
Your Mix creates personalized music selections from your own local library and listening activity.

Available selection strategies include:
- **Most Played** — prioritize frequently played tracks.
- **Most Forgotten** — rediscover tracks that have not been played recently.
- **Random** — generate a random selection.
- **Smart Mix** — combine selection signals for a more varied listening session.

Additional controls include mix size, listening history period, recency weighting, variety, and avoidance of recently played tracks. When listening history is limited, the mix can fall back to newer or randomly selected tracks.

### 📝 Lyrics & Music Metadata
The project includes functionality for managing music metadata and lyrics, with planned or implemented integrations depending on the current release:
- Embedded metadata editing.
- Album artwork management.
- Lyrics lookup through LRCLIB.
- Support for synchronized LRC lyrics where available.
- Preference for local, embedded music information where supported.

Actual support depends on the file format, available metadata, and the functionality included in the specific release.

### ⏱️ Additional Features
- Sleep timer options.
- Customizable playback and interface settings.
- Library scanning controls.
- Accessibility improvements for supported interactive controls.
- Local database storage for library indexing, playlists, history, and listening statistics.

## 🔒 Privacy & Local-First Design

LocalPlayer is designed around a local music collection rather than a cloud streaming library.

- Music playback uses audio files stored on your device.
- Library data, playlists, and listening statistics are stored locally.
- No streaming subscription is required to play your own music.
- No account is required for core local playback.
- External services, such as LRCLIB for lyrics, may require an internet connection when used.

## 🛠️ Technology Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose
- **Design system:** Material 3
- **Database:** Room
- **Playback:** Android Media3 / ExoPlayer
- **Architecture:** Single-Activity Android application with ViewModels and a playback service

## 📱 Platform

- Android
- Designed for on-device music libraries
- Supports compatible local MP3, FLAC, M4A, and AAC files

## 🚧 Development Status

LocalPlayer is actively developed. Features and behavior may evolve between releases.

Builds, migrations, playback behavior, and device-specific functionality should be verified for each release before being considered production-ready.

## 🏷️ Topics

`android` `music-player` `offline-music` `local-music` `kotlin` `jetpack-compose` `material3` `media3` `exoplayer` `room-database` `lyrics` `lrc` `music-library` `your-mix` `android-app`

---

**LocalPlayer — Your music. Your library. Your control.**
