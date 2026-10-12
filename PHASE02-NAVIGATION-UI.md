# LocalPlayer — Final, Phase 2 (Navigation shell / global UI)

## NOT VERIFIED BY A BUILD
Written in an environment without Gradle/Android SDK. Nothing was compiled, linted, run or looked at on a
device or emulator. Only a bracket-balance check was run on the touched Kotlin files. The layout in particular
(pill width on small phones, mini-player look) has NOT been seen on a screen.

    .\gradlew.bat :app:compileDebugKotlin
    .\gradlew.bat test
    .\gradlew.bat lint

## Done
- Top ScrollableTabRow and the big "Library" title removed.
- `ui/components/AppNavigation.kt`: `LibraryBottomNav` (round Search button on the left + pill tabs
  Home / Songs / Albums / Artists / Playlists / Genres; pill scrolls horizontally on narrow widths and keeps
  the selected tab in view; 48 dp+ touch targets; selected/role semantics), `TopControls` (compact, status-bar
  aware, Settings top-right), `SettingsButton`, `LibraryTab`.
- Years removed from the visible UI (tab and Years list gone). `Screen.Year`, route, DAO and VM data are kept.
- Tab state lives in AppRoot (survives detail screens and rotation). Bottom nav is shown on the root level only.
- Settings button on Album / Artist / Genre / Year / Recent / Playlist screens (`DetailTopBar(onSettings=)`).
  Not on Settings, Edit tag, Lyrics, Lyrics editor, Queue, Add tracks. Now Playing keeps its own controls.
- Theme: true #000000 / #FFFFFF kept for System / Light / Dark with dynamic color ON and OFF; with dynamic OFF the
  surface-container steps are now explicit neutral values (visible on true black/white).
- Mini player moved into a rounded container above the navigation (interim look; full redesign is Phase 4).
- Home tab = real favourites list from the existing Room favourites (new `favoriteTracks` in the ViewModel),
  Shuffle, Recently played entry, empty state. It is the interim version.
- Test: `LibraryTabTest`. New strings EN + EL.

## Deliberately NOT done yet (later phases)
Favorites collage / Recently Played chip / LocalMix placeholder, rounded song rows, grid/list toggles,
fast scrollbar, album/artist/playlist/genre card layouts, search redesign, hero headers, bottom-left back,
full-screen Now Playing, mini-player previous button/floating, custom background changes, typography.
Search still has its old top field; bottom nav is hidden while Search is open.
