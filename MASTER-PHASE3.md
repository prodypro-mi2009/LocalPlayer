# Phase 3 — Settings, Appearance, Colors & Fonts (items 15–19)

Status: code written and checked by reading + JVM unit tests of the pure logic. NOT compiled with the Android
toolchain and NOT run on a device (no Android SDK in the working environment).

## Checklist
| # | Item | State |
|---|------|-------|
| 15 | Settings sections Appearance / Library / Your Mix / About | Done in code. Old settings moved, none removed. Language, theme under Appearance; scanner, rescan, folders, hidden songs, tab order under Library. |
| 16 | Appearance → Colors | Done in code: own screen with dynamic color, album-art colors, background picture + dim (moved, same behaviour) and per-group button colors (HSV sliders, 12 ready colors, HEX entry, preview on the real background, Apply / default per group / reset all). Contrast rules in `ColorMath` (unit tested). Favorite heart stays fixed red (no group exists for it; test asserts it). |
| 17 | Appearance → Fonts | Done in code: one font per category (song titles, artists, album+genre titles, headings, general). 8 choices + imported file. Live preview in each option. Reset all. NDot NOT bundled (see below). |
| 18 | Tab order | Moved to Library section. Order feeds the bottom navigation (existing mechanism), saved, reset available, Search fixed on the left, Years not shown. Selected tab is stored by tab id, not position, so reordering keeps it. |
| 19 | Favorites vs Your Mix on Home | Done in code: Your Mix is a separate compact card above the Favorites block; Favorites keep their own title, collage, "favorite songs" header and list. Your Mix never touches Favorites. The Your Mix content itself is Phase 4. |

## Colors — what each group changes
Play/Pause (round button in Now Playing, icon in mini player); Previous/Next; Shuffle/Repeat/Timer/Lyrics (icon + label; "on" is also shown by bold label + dot); Now Playing text (title, artist, album, times, format chip, option labels); selected tab of the bottom navigation.
Contrast: icons/buttons must reach 3:1, text 4.5:1 against the surface they sit on. A saved color that fails (for example after switching light/dark theme) is lightened/darkened automatically at draw time; the dialog shows the ratio and the color that will really be used. Text on a user-colored button is black or white, whichever reads better.

## Fonts
System fonts (no files bundled, Greek + Latin): Default, Light, Medium, Black, Condensed, Serif, Monospace.
Doto (dot matrix, SIL Open Font License 1.1, license copy in `assets/licenses/Doto-OFL.txt`): bundled, **Latin only** — Greek letters fall back to the default font letter by letter.
NDot: the Nothing Phone font is not licensed for redistribution, so it is NOT in the app. Users who have the file and the right to use it can import a .ttf/.otf (Settings → Appearance → Fonts → Import). It is copied to private app storage, validated with `Typeface.Builder`, and used for any category set to "My font file". Greek coverage depends on the file.
Typography keeps all sizes and line heights; only the font family changes.

## Files
New: `data/personalization/Personalization.kt`, `data/artwork/CustomFontRepository.kt`, `ui/theme/Personalization.kt`, `ui/screens/AppearanceScreens.kt`, `res/font/doto.ttf`, `assets/licenses/Doto-OFL.txt`, `test/.../PersonalizationTest.kt`.
Changed: `AppSettings`, `AppContainer`, `LibraryViewModel`, `Theme`, `MainActivity`, `SettingsScreen` (rewritten layout), `HomeScreen`, `NowPlayingSheet`, `MiniPlayer`, `AppNavigation`, `LibraryComponents`, `LibraryUi`, `SearchScreen`, `QueueList`, `Screen`, `NavRules`, `AppRoot`, strings EN+EL, `NavRulesTest`, `app/build.gradle.kts` (version 1.0.0-phase3 / code 8).

## Verified / not verified
Verified (JVM, run here): ColorMath (hex, WCAG contrast, ensureContrast, HSV round-trip), ControlColors/FontChoices save+repair, Favorite heart has no color group, Screen route encode/decode + singleton rules. Earlier: metadata/M4A tests (33).
Verified by script: every `R.string.*` used exists in EN and EL; XML well-formed; no syntax errors from the Kotlin parser.
NOT verified: Android compile, any on-device behaviour, how Doto/system fonts look, whether `DeviceFontFamilyName` names exist on every OEM font stack (missing names fall back to the default font), TalkBack, the picker dialog layout on small screens.
Known limits: "Home" tab keeps its name (it holds Favorites + Your Mix); drag-and-drop for tab order not added (up/down buttons, as before).
