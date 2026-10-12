# LocalPlayer 1.0.0 — Phase 1 (SOS bug fixes & core UX)

Base: LocalPlayer 0.0.9 zip (build.gradle said 0.3.0 / code 5). Now versionName 1.0.0-phase1, versionCode 6.
Room schema NOT changed (still version 5), no migration needed.

## Verification levels
- JVM-tested (really run here, 29 tests green): NavRulesTest (13), QueueInteractionTest (9) + existing QueueOccurrenceTest/PlayNextOrderTest, ArtworkGradientTest (6).
- NOT build-verified, NOT device-verified: there is no Android SDK here and Maven/Google repos return 403. All Compose/Media3 code was only
  read through and parse-checked (no syntax errors). Run `gradlew assembleDebug testDebugUnitTest` on your machine first.

## Checklist (21 requirements)
| # | Requirement | Status |
|---|---|---|
| 1 | Swipe left = end of queue, swipe right = play next, in every TrackRow list; unique occurrence id on EVERY queue entry | Implemented, logic JVM-tested, gesture needs device |
| 2 | Edge-to-edge, no grey strip | Implemented (transparent bars, window background from app theme, icon colour from app theme, cutout short-edges, no nav scrim). Exact cause of the grey strip could not be reproduced here |
| 3 | Lyrics background = same artwork tone as Now Playing | Implemented |
| 4 | Lyrics navigation stack | Fixed at root cause, JVM-tested |
| 5 | Slower artwork->black transition | Implemented, curve JVM-tested |
| 6 | Search cleared when leaving Search | Implemented |
| 7 | Heart bigger, always #FF1744 | Implemented |
| 8 | Floating translucent navbar/mini-player | Implemented, needs device look |
| 9-19 | Phases 2 and 3 | Not started |
| 20-21 | Phase 4+ | Prompt file was cut off at "20. Your Mix — πραγματικ…": text not received |

## Root causes
1. Lyrics: Now Playing called `nav.push(Lyrics)` every time, even when Lyrics was already open below the Now Playing overlay
   (Lyrics -> mini player -> Now Playing -> Lyrics). Same for Queue. Fix: `NavRules` (pure, testable): Lyrics/Queue/Settings/Search/Recent are
   singletons (re-opening returns to the existing entry), pushing the top screen again is ignored. `Screen` moved to Screen.kt.
2. "Black too early" / grey top: gradients faded to `Color.Transparent`, which is transparent BLACK, so colours were darkened mid-way.
   Now every stop keeps the same hue and only the alpha follows an eased curve (`ArtworkGradient`). Tint is ~12 % at 2/5 of the height.
   Used by Now Playing, the window backdrop and (a gentler version) Lyrics.
3. Lyrics bottom block: the old Scaffold bottomBar made the content end above the bars and a 62 % black overlay covered the picture.
   Bars now float above full-height screens; lists get `LocalBottomBarInset` as bottom padding.

## Decisions to check
- Swipe right uses the existing "Play next" order: after the current song AND after earlier Play-next entries (A, X, Y, ...). If you prefer
  "always directly after the current song" (newest first) it is a one-line change in PlayNextOrder.
- Under Shuffle, Media3 places inserted items in its own shuffle order, so "next" is not guaranteed (pre-existing limitation of the menu's Play next too).
- Lyrics page is always dark (white text, light status icons), also in the light theme, to keep contrast on any cover.
- Grid view cards (Songs grid) have no swipe, as before.
- Closing Search also drops the saved UI state (chips) of every screen that left the back stack.

## Files
New: ui/nav/Screen.kt, ui/nav/NavRules.kt, ui/components/SwipeToQueue.kt (replaces SwipeToQueueEnd.kt), ui/components/BottomBarInset.kt,
ui/theme/{ArtworkGradient,ArtworkBackdrop,SystemBars}.kt, tests NavRulesTest, QueueInteractionTest, ArtworkGradientTest.
Changed: AppRoot, MainActivity, NavState, LibraryViewModel, PlayerConnection, MediaItemFactory, SongMenuController, LibraryComponents, LibraryUi,
AppNavigation, AppIcons, NowPlayingSheet, QueueList, LyricsScreen, LyricsComponents, HomeScreen, DetailScreens, PlaylistScreens, SearchScreen,
SettingsScreen, EditMetadataScreen, LyricsEditorScreen, Theme.kt, themes.xml (day/night), strings EN/EL.

## Please test on the device
Believer -> Lyrics -> mini player -> Now Playing -> Lyrics (x5) then ONE back press; swipe both ways in Songs/Album/Artist/Genre/Playlist/Favorites/Search/Recent
with Undo while playing; status bar in light/dark theme and with a camera cut-out; last song reachable above the bars on every tab; Search -> leave -> return.
