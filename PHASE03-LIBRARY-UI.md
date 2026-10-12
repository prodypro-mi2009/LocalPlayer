# LocalPlayer — Final, Phase 3 (Main library UI)

## NOT VERIFIED BY A BUILD
No Gradle / Android SDK in the authoring environment. Nothing was compiled, linted, run or seen on a screen.
Only bracket balance and XML well-formedness were checked. The sandbox was also reset mid-phase; the project
was restored from the Phase 2 ZIP and the Phase 3 work was redone from there.

    .\gradlew.bat :app:compileDebugKotlin
    .\gradlew.bat test
    .\gradlew.bat lint

## Done
- Home = Favorites: large heavy italic "Favorites" title, Shuffle favorites, Recently played chip, asymmetric
  local-artwork collage (1 large + 2 small), LocalMix = empty placeholder card only, strong empty state,
  favourites list (real Room favourites).
- Songs: rounded rows (current song highlighted), action row Shuffle / Locate current / Grid-List / Sort
  (all six sort orders), fast scrollbar (Latin A–Z, Greek Α–Ω, #; shown for Title/Artist/Album sorts only),
  grid mode (2 columns), swipe-left to queue end kept (list mode; library list is not reordered).
- Albums: exactly 2 columns, tall 0.72 cards with bottom fade, title / artist / "N songs"; Shuffle, Grid/List, Sort.
- Artists: list by default with circular local artwork, grid mode with circles; Shuffle, Grid/List, Sort.
- Playlists: 2-column grid, 2x2 local-artwork collage, real "New playlist" card; Shuffle, Grid/List, Sort.
  Create / rename / delete / add / remove / reorder unchanged and still wired.
- Genres: 2-column heavy italic cards, translucent decorative icon, song count, theme tonal colours; Grid/List.
- Search: FTS/debounce/Greek logic untouched; UI is now a bottom pill with a round back button and clear button.
- Detail screens (Album, Artist, Genre, Year, Recent, Playlist): `DetailHero` (large artwork, heavy title, Play,
  Shuffle), rounded rows, bottom-left back button, Settings button, mini player still available.
  Recently Played keeps Clear history with confirmation.
- Track menu: already exactly the 8 required entries in the required order and groups; unchanged.
- Pure logic tests: `LibrarySortingTest` (alphabet sections incl. Greek, sorting).

## Not verified / known risks
- Never compiled: expect possible import or type errors in HomeScreen.kt, LibraryUi.kt, DetailScreens.kt.
- Shuffle on Albums / Artists / Genres filters the song list by group key; Genres compares the normalised genre
  text with `GenreItem.key`, which should be checked on a real library.
- Search pill uses `imePadding()` inside a Scaffold that already has a bottom bar: it may float a little high
  with the keyboard open.
- Fast scrollbar and swipe thresholds were not exercised on a device.
- Genre card "artwork" is theme tonal colour only (no artwork on the card).
- Home collage tiles use at most 3 favourite artworks.
- Artist/album artwork still comes from the existing local group artwork (no remote images).
- `PlayShuffleRow` and `PlaylistCover` are now unused leftovers.
