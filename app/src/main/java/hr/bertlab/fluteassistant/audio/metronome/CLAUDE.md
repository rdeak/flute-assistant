# Metronome audio path

Loaded when working in this folder; moved here from the root CLAUDE.md on 2026-11-02 so it is read only when needed.

`MetronomeConfig` -> `ClickScheduler` (sample-accurate click timeline; positions accumulate as
doubles so there is no drift, and `retime` moves the already-scheduled click when only the tempo
changes) -> `MetronomeRenderer` (mixes active click "voices" into PCM blocks from the two samples
`ClickSynth` makes, a woodblock and its accent for the bar's first beat, at the fixed `OUTPUT_GAIN`,
and passes everything through `SoftLimiter`) ->
`AudioTrackMetronomePlayer` (a dedicated URGENT_AUDIO thread doing blocking float-PCM writes to a
low-latency streaming `AudioTrack`, which it opens through `audio/OutputTracks.kt`'s `LowLatencyStream`, as the
flute's tone player does; stops on audio-focus loss and ACTION_AUDIO_BECOMING_NOISY).
Scheduler, renderer, synth and the thread's loop (`renderWhile` in `MetronomeRenderLoop.kt`) have no
Android dependencies and are unit-tested on the JVM.

Every rendered block reaches the track in full (since 2026-11-23): `renderWhile` writes through
`writeFully` (`audio/WriteFully.kt`, shared with the flute's tone), which finishes a short write and
waits through an empty one while the route is rebuilt. The renderer counts the frames it has rendered
and a click's audible time is that count against the track's own, so a block rendered and not written
loses its clicks and leaves every later beat flash late by its length. That is what the loop did
before on an empty write, a block for every millisecond of the wait. `metronome.track_closed` counts
the writes the track took less than all of as `shortWrites`, so a logcat session tells whether a
phone does this at all.
There is one click on every beat and one sound: subdivisions, the beep and bell sounds and the
volume setting went on 2026-11-09 (root CLAUDE.md), and `metronome.start` lost their three fields.

The player is an app-wide singleton that outlives any screen. Its contract (`MetronomePlayer`):
`start()` returns a session id (or `NO_SESSION` when focus is refused); every `BeatEvent` carries
that id plus `audibleAtNanos` derived from `AudioTrack.getTimestamp`, so the UI delays the beat
flash until the click is actually heard and ignores events from stale sessions; `playback` is a
StateFlow that a newly created ViewModel or the service adopts; `sessionEnded` is an event flow
rather than state because a session can start and fail within the same frame.

Log events (tag `FA.Metronome`, `FA.Playback`): Metronome: `metronome.start` / `stop` / `ended` / `focus_lost` / `noisy` / `track_open` / `track_closed`
(underruns, shortWrites) / `error`; playback: `playback.error`.
