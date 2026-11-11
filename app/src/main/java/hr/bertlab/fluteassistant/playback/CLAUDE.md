# Background playback

Loaded when working in this folder; moved here from the root CLAUDE.md on 2026-11-02 so it is read only when needed.

`MetronomeViewModel` calls `BackgroundPlayback.start()` after a successful start and never stops
playback in `onCleared`. `MetronomeService` owns nothing: it promotes the process with a
media-playback notification, mirrors `player.playback` into that notification, stops itself when
the flow goes null, and calls `player.stop()` from the notification's Stop action and from
`onTaskRemoved`. It returns START_NOT_STICKY on purpose.

The player contract it relies on is in `../audio/metronome/CLAUDE.md`.
