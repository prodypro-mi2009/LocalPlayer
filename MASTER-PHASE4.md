# Phase 4 — Your Mix, Listening time, Top 20

- Room v6 -> v7 (MIGRATION_6_7): new table `listening_days(day, trackUri, listenedMs, plays, lastListenedAt)`; nothing else touched.
- Counting: `ListeningMeter` (pure) fed by `ListeningRecorder` in PlaybackService from the real Media3 player.
  Time only while isPlaying; saved every 10 s, on pause, track change, repeat and shutdown; additive writes.
  Play criterion (`PlayCriterion`): 30 s of real playback, or half of a song shorter than 60 s (min 3 s).
- Days = device-local calendar date; week starts on the locale's first weekday.
- Time Usage: today / week / month / year / previous month with the exact date range shown. Top 20: today/week/month/year.
- Your Mix: strategies Most played / Most forgotten / Random / Smart; settings size, period, recent weight, variety, avoid-recent.
  Saved Mix is reused until: settings change, 24 h, 10 new plays, or <70 % still visible, or manual Refresh. Hidden songs never enter.
- Home card opens the Mix; the cross hides it (Settings > Your Mix brings it back).
- NOT verified: Android compile, Room schema export/validation, behaviour on a device, real Media3 callbacks.
