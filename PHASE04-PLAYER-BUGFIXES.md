# LocalPlayer — Final, Phase 4 (Player / queue) + the four bug fixes

## NOT VERIFIED BY A BUILD
No Gradle / Android SDK / device in the authoring environment. Nothing was compiled, linted, run or seen.
Only bracket balance and string-XML checks ran. Bug 4 in particular needs a real device (listening test).

    .\gradlew.bat :app:compileDebugKotlin
    .\gradlew.bat test
    .\gradlew.bat lint

## Root causes
1. Search layout: the screen never applied the status-bar inset itself (edge-to-edge, scaffold insets are 0), so
   its content started under the status bar; its LazyColumn state was saveable, so the navigation's saveable
   state holder restored an old scroll position; and the previous bottom-pill layout combined `imePadding()` with
   a Scaffold that already pads for the bottom bar. Fix: top field + chips + results, `statusBarsPadding()` on
   the screen, a plain `remember`ed list state reset per query/filter, `imePadding()` only on the results area.
2. Now Playing squeeze/bounce: it was a wrap-content ModalBottomSheet and its whole content was inside
   `key(now.mediaId)`, so every Next/Previous destroyed and rebuilt all content for a frame; the sheet height
   collapsed and re-grew. Also a plain Slider as progress. Fix: fixed full-screen container; only the
   artwork+metadata block animates (horizontal slide + fade, `SizeTransform` snapped, fixed-height rows);
   spec-chip slot has a fixed height; wavy progress (`WaveSeekBar`).
3. Black strip at the top: the tonal/dynamic colour lived only in individual screen bodies (e.g. the hero
   gradient) and the top controls row had no backdrop, so the strip behind Settings showed the pure #000/#FFF
   window. Fix: one window-level backdrop gradient (theme `primaryContainer`, or the artwork colour when
   "Album-art colors" is on), drawn behind the Scaffold including the status bar; the hero's own gradient was removed.
4. Queue edits interrupting playback: **no code path that restarts the player was found.** Add / remove / move /
   play-next already used only `addMediaItems`, `removeMediaItem`, `moveMediaItem` (no prepare/stop/seek/
   playWhenReady). What WAS found and fixed: tapping the CURRENT song in the queue called
   `seekToDefaultPosition` (restarted it). New operations are pure queue edits (`removeMediaItems` for Clear
   upcoming, `addMediaItem(s)` for Undo). If you still hear a gap after Add/Remove, it is below this code
   (ExoPlayer re-preparing the neighbouring item) and needs a device trace; it could not be reproduced here.

## Files changed (this step)
- ui/screens/SearchScreen.kt — rewritten layout (field, [Artists][Song][Album] chips, results).
- ui/player/NowPlayingSheet.kt — `NowPlayingScreen` (full screen) replaces the bottom sheet; sleep dialog and FavoriteButton kept.
- ui/player/WaveSeekBar.kt — new wavy seek bar (tap / drag, one seek on release).
- ui/theme/ArtworkTone.kt — artwork colour extraction (16x16 sample, cached per track, failure cached); `rememberArtworkTone`.
- ui/AppRoot.kt — window backdrop, artwork tone, full-screen player overlay, mini-player tint, QueueScreen feedback.
- ui/player/MiniPlayer.kt — previous button.
- ui/components/LibraryUi.kt — hero no longer draws its own gradient.
- playback/PlayerConnection.kt — current-track tap no longer restarts; `removeQueueItem`, `reinsert`, `clearUpcoming`, `reinsertAll`.
- ui/LibraryViewModel.kt, ui/screens/QueueScreen.kt, ui/player/QueueList.kt — Remove + Clear upcoming with Undo.
- strings (EN + EL); test `ArtworkToneTest`.

## Phase 4 status
Done (unverified): full-screen Now Playing (collapse, "Now Playing", queue, squircle art, title/artist/album/heart,
wavy progress from real Media3 position, audio-spec chip via existing formatter, prev/play/next, segmented
Shuffle/Repeat/Sleep/Lyrics); artwork colours (setting now used: backdrop, glow, mini-player tint, wave colour,
500 ms crossfade, theme fallback); mini-player previous; queue Clear upcoming + Undo on remove.
Kept as they were, NOT re-audited in this step: queue move/clear/shuffle/repeat, snapshot restore, mini-player
swipe-dismiss + Undo, sleep timer (presets, end of song, custom, cancel, fade, stop service, close app),
all lyrics features and the lyrics editor.
Not done: output-device control (no reliable API), blurred-artwork lyrics background, any lyrics change.
Not verified: wave seek bar touch handling, the slide/fade transition feel, back gesture handling, insets on a real device.
