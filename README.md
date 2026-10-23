# Local Player v0.3.0 (foundation)

Offline, audio-only Android music player (Kotlin, Compose, Material 3, Media3, Room). No Internet permission.

## Library: AUDIO ONLY
- Imports `.mp3 .flac .m4a .aac`. `.mp4` is never imported (even audio-only), there is no video library, no
  video scanning and no `READ_MEDIA_VIDEO`. Android 13+ asks for `READ_MEDIA_AUDIO` only.
- Two scanners, chosen in Settings (**Use MediaStore scanner**, persistent, Android 11+):
  - OFF (normal): MediaStore lists the audio files, the tags of every new/changed file are read FROM THE FILE.
  - ON (fast): ONLY `MediaStore.Audio.Media` - files and tags come from MediaStore, no file is opened, no fallback.
    Rows made this way are re-read from the files by the normal scanner when you switch back.
- Rows left from older video-aware versions are removed on the next scan.

## The audio file is the source of truth
Tags, lyrics and artwork live in the file. Room is cache/index + app data (playlists, history, favorites, primary-artist
classification). Saving tags or lyrics: working copy -> edit only what changed -> verify -> replace -> re-read the real
file (tags, lyrics and the SHA-256 of every embedded picture must match) -> only then update the cache. Backup restored on failure.

## Features in this version
- Favorites (Room, keyed by track URI: survive rescans, tag edits and updates). Heart in Now Playing.
- Synchronized LRC lyrics (parser, offset, multiple timestamps) following the real Media3 position; plain lyrics; editor with
  timestamps from the playback position; written into the file (MP3 USLT, FLAC LYRICS, M4A lyrics atom).
- Audio specs chip (sample rate / bitrate / format) from the file.
- Sleep timer (5-60 min, end of song, custom), lives in the playback service, fades out, stops service, closes app.
- Queue snapshot/undo: swipe a song left = move to end of queue (Undo); swipe the mini player left = clear queue (Undo, 5 s).
- Track menu bottom sheet (fixed order): Play next, Add to queue, Add to playlist | Go to queue, Go to album, Go to artist |
  Edit tag, Edit lyrics.
- Themes: System / Light (#FFFFFF) / Dark (#000000), dynamic color on/off; background picture from the photo picker (+ button),
  stored privately, dimmed.
- Years has no UI entry (the route and data remain).

## Database
Room version 5. Migrations 3->4 and 4->5 keep all data (no destructive path from 3). 4->5 adds `favorites`.

## Build / tests
`.\gradlew assembleDebug`, `.\gradlew testDebugUnitTest` (JVM: real tag round trips on MP3/FLAC/M4A fixtures, LRC, parsers),
`.\gradlew connectedDebugAndroidTest` (migrations, playlists, favorites, history, search).
