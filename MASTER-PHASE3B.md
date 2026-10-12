# Phase 3b — Settings restructure, pure black/white, Your Mix dismiss, lyrics settings

- Settings = menu of cards (Appearance, Language, Library, Lyrics, Your Mix, About); each opens its own sub-screen.
  Appearance holds Theme + Colors + Fonts entries.
- Colors: under dynamic colours: Pure black / Pure white / Off. While on: uniform background, controls exact opposite,
  dynamic/artwork colours, background picture and custom button colours are not applied. Favorite heart stays red.
- Home: Your Mix card hides on a short tap (persisted); Settings > Your Mix switch shows it again.
- Lyrics: alignment (left/center/right) and font (also listed under Fonts as "Lyrics").
- Tests (JVM, run): PersonalizationTest, PureModeTest, NavRulesTest, SettingsRoutesTest, AppearanceRoutesTest = 28 OK.
- NOT verified: Android compile, on-device look of pure mode (sliders, dialogs, Now Playing), lyrics alignment on device.
