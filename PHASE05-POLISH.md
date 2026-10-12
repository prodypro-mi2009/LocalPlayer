# Phase 5 — Background, Settings, header alignment

BUILD STATUS: NOT built or run (no Gradle/Android SDK/device in this environment). Everything below is unverified by compiler and device.

## Settings icon alignment (root cause)
Detail screens used Material `TopAppBar` (64dp tall, 4dp horizontal inset); main screens used `TopControls` (56dp, 8dp). The gear therefore sat lower and further right on detail screens.
Fix: one `AppTopBar` + `TopBarMetrics` (56dp height, 8dp padding, 48dp buttons, center-aligned, status-bar padded) in `ui/components/AppNavigation.kt`; `TopControls`, `SettingsButton` and `DetailTopBar` (`LibraryComponents.kt`) are all built on it.

## Background dim
- `AppSettings.backgroundDim` (persisted, default 0.78, clamped 0..1, NaN-safe) — test `BackgroundDimTest`.
- `AppRoot` veil uses the persisted value instead of a fixed 0.78.
- Settings: "Background dimming" slider with percentage; the preview shows the same veil. EN + EL strings.

## Not done / not audited (honest)
Lyrics (sync, editor, save-to-file, blurred background), sleep timer, queue shuffle/repeat/move re-audit, output-device control, mini-player swipe re-audit. Bug 4 (queue edits interrupting playback) is unproven on a device.
