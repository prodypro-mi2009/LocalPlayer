🎧 LocalPlayer

A local-first, audio-only Android music player built with Kotlin, Jetpack Compose, Material 3, Media3 and Room.
Download the latest staple version here: https://github.com/prodypro-mi2009/LocalPlayer/releases/download/v.1.4.2/v.1.4.2.apk

«🚧 Current snapshot: "1.5.0-beta3" — pre-release software. Features and migration paths should be tested on real devices before treating this snapshot as a stable release.Download here: https://github.com/prodypro-mi2009/LocalPlayer/releases/download/v.1.5.0-beta3/v.1.5.0-beta3.apk»

---

✨ Highlights

- 🎵 Local music library — MP3, FLAC, M4A and AAC files, with no video library or video scanning.
- 🎛️ Queue-first playback controls — Play next, Add to queue, drag-to-reorder, Shuffle, Repeat, Clear upcoming, Clear queue and Undo.
- 🎤 Lyrics inside your files — plain or synchronized LRC lyrics, an editor, timestamps from playback position and optional LRCLIB search.
- 🏷️ Metadata editing — edit supported tags and artwork in the audio file, then re-read and verify the result.
- 🧠 Your Mix — a local listening-history-based mix with configurable strategies and variety.
- 📊 Listening statistics — listening time and play history stored locally.
- 🎨 Personalization — themes, artwork-based colors, custom colors, fonts, background image and tab order.
- 📥 YouTube / YouTube Music downloads — user-started downloads to MP3 with editable metadata and background progress notifications.
- 💤 Sleep timer, 🔊 optional ReplayGain and equalizer, 🧩 optional home-screen widgets, and 📦 settings backup/restore.
- 🛡️ No intentional ads, analytics or app-tracking SDKs. See "Privacy & network use" (#-privacy--network-use) for the important distinction between tracking and the network features the app uses.

🎼 Library & audio files

Supported library formats

The library indexes these audio extensions:

- ".mp3"
- ".flac"
- ".m4a"
- ".aac"

Audio only: ".mp4" is not imported, even if it contains audio. The app does not build a video library and explicitly removes the Android video-read permission.

Two library scanning modes

The scanner can be changed in Settings → Library → Scan:

- Normal scanner: MediaStore finds the audio files; the app reads supported metadata from the actual files.
- Fast MediaStore scanner: reads the audio rows and tags provided by Android's MediaStore without opening each file. Switching back to the normal scanner lets the app refresh file-derived metadata.

The audio file is the source of truth

For supported operations, tags, lyrics and embedded artwork live in the audio file. Room stores the library index/cache and app-owned data such as playlists, favorites, hidden tracks, listening history and statistics.

When editing supported metadata, the app uses a temporary working copy, writes the requested changes, re-reads the result and verifies it before updating its cache. The workflow is designed to avoid reporting a save as successful when verification fails.

«Format note: MP3, FLAC and M4A have tag-writing support in the current implementation. AAC-ADTS files can be indexed/played, but tag-writing features are not supported for every format equally. Lyrics and ReplayGain availability also depends on the file format and the tags it contains.»

▶️ Player, queue & navigation

- Background playback through a Media3 playback service, with Android media notification/session controls.
- Expand the mini-player into Now Playing with an animated sheet transition.
- Wave-style seek bar and smoothed playback progress.
- Queue sheet with drag-to-reorder and per-track removal.
- Play next and Add to queue are separate actions: Play next places a track immediately after the current one; Add to queue places it at the end.
- Shuffle changes the order used for playback; the visible queue is intended to reflect the actual playback order.
- Repeat Off / Repeat All / Repeat One.
- Clear upcoming or clear the queue, with guarded Undo actions where applicable.
- Song swipe gestures: left to send a song to the end of the queue, right to play it next. A per-song queue button is available in Settings when swipe gestures are turned off.
- Reorder the bottom navigation tabs in Settings.
- Reorder playlist tracks using the playlist's Edit order mode.
- Track menus provide shortcuts to playlists, albums, artists, queue actions, tag editing and lyrics editing.

🎤 Lyrics

- Read plain lyrics and synchronized ".lrc" timestamps from supported audio-file tags.
- Follow the real playback position while synchronized lyrics are displayed.
- Edit lyrics and add timestamps using the current playback position.
- Search LRCLIB over HTTPS from the lyrics screen, review candidate matches and preview the text before choosing to save it.
- A search result is not written into a file until the user chooses the save action.

Lyrics search is a network feature: the search request can include track title, artist, album and duration so that LRCLIB can find a suitable match.

🏷️ Tags, artwork & playlists

- Edit supported metadata tags and embedded artwork.
- Edit playlist order and remove items from a playlist.
- Set a custom playlist cover using Android's system photo picker; covers are stored in the app's private storage.
- Create folder-linked playlists and manage library folders.
- Hide individual songs or exclude folders from the library.
- Favorite tracks and retain library/history data across normal rescans where the stored identifiers still match.

Custom playlist covers are not currently included in settings backups.

🧠 Your Mix & 📊 listening statistics

Your Mix is generated locally from the library and listening history. Available strategies:

- Smart — balances listening history, recency and variety.
- Most played — prioritizes tracks played most often in the selected time window.
- Forgotten — favors tracks that have not been heard recently.
- Random — makes a randomized selection.

Settings include mix size, a listening-history window (7, 30, 90 or 365 days, or all time), recent-track avoidance, recent-listening weight and variety controls. When there is not enough history, the Mix uses fallback selections and indicates that limitation.

Listening history and statistics are kept on the device for these features. They are not an analytics service and the app code does not intentionally upload them.

🎚️ Audio options & 💤 sleep timer

- ReplayGain: applies existing ReplayGain tags to help even out loudness. Off by default.
- Equalizer: uses the device's available Android equalizer effect, presented through five reference-frequency sliders. Off by default; availability depends on the device.
- Sleep timer: choose a duration or end-of-track behavior; playback can fade out and stop through the playback service.

These options are optional. Their actual CPU/battery impact depends on the device, audio stack and chosen settings.

🎨 Appearance & personalization

- System, light and dark themes.
- Optional Android dynamic colors and artwork-derived colors.
- Pure/background display modes, a user-selected background image and adjustable dimming.
- Custom color settings for supported UI controls and custom font choices, including importing a font.
- Greek and English resources.
- Reorderable bottom tabs and separate settings pages for appearance, library, navigation/gestures, audio and Your Mix.
- Animated Now Playing and queue-sheet transitions, with a Baseline Profile intended to help startup and frequently used code paths.

The Baseline Profile is a performance optimization, not a published benchmark. No measured RAM, CPU, GPU, battery or startup-time figures are claimed by this README.

📥 Download from YouTube / YouTube Music

Available from Settings → Download from YouTube:

1. Paste a YouTube or YouTube Music link and inspect the tracks found.
2. Choose a destination folder.
3. Edit track names and artists; fill album, year, genre and track number where available.
4. Apply selected metadata fields to selected tracks or to the whole list.
5. Start, stop and retry downloads; progress and errors are shown in the UI/notification.

Downloads are saved as MP3, using the best available source audio and MP3 VBR 0 encoding; this is not lossless audio. Embedded artwork and supported tags are written where available. Downloads can continue while the app is in the background, using a foreground service.

The downloader uses yt-dlp and FFmpeg components and adds significant native/runtime dependencies. While a download is running, CPU, memory, network and battery use can be higher than during ordinary local playback. The download feature is optional and only does work when used. The configured package ABI is ARM64 ("arm64-v8a").

Only download content you have the right to keep and follow the applicable platform terms and laws.

🔄 Updates, widgets & backups

App updates

The About screen can check the project's GitHub Releases and download an update APK. The automatic update check is enabled by default, is rate-limited (at most once per 24 hours), and can be turned off in Settings. Installation is handed to Android's package installer and still requires system/user confirmation. Download validation uses published size/checksum information when available.

Home-screen widgets

Optional 2×1 and 2×2 music widgets control the same player. Widgets are disabled by default; when disabled, an already-placed widget remains on the launcher as an inactive tile until the user removes it.

Settings backup & restore

- Portable JSON export/import for supported preferences.
- Full ".lpbackup" archive for settings plus the custom background image and imported font.
- Import preview before applying changes, validation, and rollback behavior for many failed imports.

Backups contain settings, not music files, playlists, favorites, history or listening statistics. JSON backups cannot carry images/fonts. Playlist cover images are not included in the current backup format.

🛡️ Privacy & network use

LocalPlayer is designed to be local-first:

- The library, playlists, favorites, listening history, statistics and ordinary local playback are handled on the device.
- The application source/declared dependencies in this snapshot contain no intentional ads, analytics SDK or app-tracking SDK, and no app telemetry calls were found in the source scan.
- No account, sign-in or app-specific tracking token is required.
- On Android 13+, library access uses "READ_MEDIA_AUDIO"; "READ_MEDIA_IMAGES" and "READ_MEDIA_VIDEO" are removed from manifest merging. Android 12 and older use legacy "READ_EXTERNAL_STORAGE" for compatibility. Images are selected through Android's system photo picker rather than a broad image-reading permission.

The app is not completely offline-only. Its network behavior is:

Feature| Network use
Lyrics internet search| Sends the search metadata needed to request matching lyrics from LRCLIB, only when that feature is opened by the user.
Automatic/manual update check| Contacts the GitHub API for release information. Automatic checking is on by default but can be disabled. The app's custom User-Agent identifies the app version, not a generated device ID.
YouTube / YouTube Music download| Contacts YouTube and services used by yt-dlp after the user starts a link check/download.
Ordinary local playback and library browsing| Do not require a network connection.

As with any network service, the external provider receives the request and may observe ordinary connection information such as the IP address. The absence of an app analytics SDK is not a claim that third-party servers never log their own requests.

⚡ Performance & resource use

Performance is a design goal, not a promise of identical results on every phone:

- A Baseline Profile and ProfileInstaller are included to help ART optimize app startup and frequently used code paths.
- Widgets, ReplayGain and the equalizer are off by default.
- Ordinary local playback does not need to stream music from a server.
- YouTube downloads and update/lyrics requests are separate network operations; downloads in particular can temporarily use more CPU, memory and battery.

No controlled benchmark results are included in this snapshot. Real RAM usage, battery drain, GPU work and launch speed should be measured on target devices before making numerical performance claims.

🧰 Tech stack

- Kotlin 2.2.20
- Jetpack Compose / Material 3
- AndroidX Media3 for playback and media sessions
- Room 2.8.3 for local database/index and app-owned data
- KSP for Room code generation
- Android fork of jaudiotagger for supported embedded metadata editing
- yt-dlp Android and bundled FFmpeg components for the optional downloader
- Baseline Profile / ProfileInstaller

🧪 Build & test

Requirements

- JDK 17 or newer compatible with the Android Gradle Plugin
- Android SDK with API 36 installed
- Gradle Wrapper (Gradle 8.13 is configured in this repository)

Commands

Linux/macOS:

./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest

Windows PowerShell:

.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest

"connectedDebugAndroidTest" requires a compatible connected Android device or emulator. The generated debug APK is normally under "app/build/outputs/apk/debug/".

⚠️ Known beta validation gaps

- The Room database is currently version 8, and the migration chain in code includes "3 → 4 → 5 → 6 → 7 → 8".
- This source archive includes Room schema exports only for versions 3, 4 and 5. Schemas "6.json", "7.json" and "8.json" are missing, even though the migration test attempts to validate "5 → 6". Therefore the complete migration chain is not fully verifiable from this archive and the relevant migration tests must be repaired/run before a stable release.
- The release build uses a machine-specific keystore path in "app/build.gradle.kts" and a local, Git-ignored "keystore.properties". Configure signing for your own environment; never commit keystores or passwords.
- Release lint checks are configured not to abort the build in this snapshot. A successful package task alone should not be treated as a complete quality/security review.
- This README is based on source inspection of "1.5.0-beta3". A successful Android build, instrumentation run and on-device performance test were not verified as part of this review.

📌 Project status

LocalPlayer is evolving toward a feature-rich, local-first Android audio player. Please treat "1.5.0-beta3" as a beta and validate playback, metadata edits, downloads, migration from older installations and performance on your own target devices before relying on it as a stable release.
