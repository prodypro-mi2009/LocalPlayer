# LocalPlayer — Final, Phase 1 (Corrections)

## NOT VERIFIED BY A BUILD
This phase was done in an environment without Gradle, the Android SDK or network access.
**Nothing here has been compiled, linted or run.** The new unit tests were written but not executed.
Only the file-layout parsing behind `AudioPayload` was sanity-checked against the bundled
fixtures with a throw-away script. Run on your machine before trusting this checkpoint:

    .\gradlew.bat :app:compileDebugKotlin
    .\gradlew.bat test
    .\gradlew.bat lint

## Audit scope for this phase
Read in full: PlayerConnection, MediaItemFactory, ScanCoordinator, ScanState, LibraryScanner,
MediaStoreFastScanner, MediaStoreAudio, MetadataReader, LibraryRepository, ArtworkRepository,
MetadataRepository, FileTagEngine, MetadataModels, Entities, relevant parts of Daos, AppContainer,
HomeScreen/SettingsScreen (scan UI). The remaining files named in Phase 0 (UI, lyrics, sleep timer,
theme, androidTests, ...) are audited in the phase that changes them.

## Changes
1. Play next order — `PlayNextOrder` + items flagged via `MediaItemFactory.markPlayNext`;
   a new batch goes after the current item AND after earlier Play-next items. Test: `PlayNextOrderTest`.
2. Scanner errors — `ScanState.error` (`ScanError`), `ScanCoordinator.fail()`. A failed MediaStore query or
   an unexpected exception keeps the index, shows a banner (Home, Settings) with Retry; EN + EL strings.
   Unreadable files no longer delete their existing row (counted in `ScanState.unreadable`).
3. MediaStore scanner — verified, unchanged: ON = MediaStore.Audio only, OFF = normal scanner reading real files,
   fast rows (`INDEX_VERSION_FAST = -1`) are re-read when OFF; mp3/flac/m4a/aac only; no video; no broad permissions.
4. Artwork retries — read errors are retried with bounded backoff (`ArtworkRetryPolicy`: 15 s, 2 min, 15 min,
   max 4 attempts); only a successful read with no picture is cached as "no artwork". Test: `ArtworkRetryPolicyTest`.
5. Artwork groups — fast scanner still stores "unknown, assume yes"; when the loader proves a file has no picture,
   `clearUnverifiedArtwork` corrects the row (fast rows only) so album/artist artwork moves to a track that has art.
6. Free space — `StorageEstimate`: 4x file + 48 MB before the edit (backup + working + verify + rewrite),
   re-checked against the real size after the backup, plus a check on the media volume. Test: `StorageEstimateTest`.
7. Metadata safety — workflow unchanged; added `AudioPayload` (hash of audio data only; MP3/FLAC/MP4) compared
   between backup and working copy BEFORE the original is touched. Artwork SHA-256, lyrics and duration were already
   verified by `FileTagEngine.verify`. Test: `AudioPayloadTest`.

## Known limits / not done
- Scanner: the track upsert and the search-index upsert are two separate writes; a crash between them can leave a
  row missing from search until the file changes. Not changed in this phase.
- Not verified: behaviour on a real device (no device was available).
