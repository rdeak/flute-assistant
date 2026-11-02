# Metronome audio path

Loaded when working in this folder; moved here from the root CLAUDE.md on 2026-11-02 so it is read only when needed.

`MetronomeConfig` -> `ClickScheduler` (sample-accurate click timeline; positions accumulate as
doubles so there is no drift, and `retime` moves the already-scheduled click when only the tempo
changes) -> `MetronomeRenderer` (mixes active click "voices" into PCM blocks, caches samples
synthesised by `ClickSynth`, passes everything through `SoftLimiter`) ->
`AudioTrackMetronomePlayer` (a dedicated URGENT_AUDIO thread doing blocking float-PCM writes to a
low-latency streaming `AudioTrack`; stops on audio-focus loss and ACTION_AUDIO_BECOMING_NOISY).
Scheduler, renderer and synth have no Android dependencies and are unit-tested on the JVM.

The player is an app-wide singleton that outlives any screen. Its contract (`MetronomePlayer`):
`start()` returns a session id (or `NO_SESSION` when focus is refused); every `BeatEvent` carries
that id plus `audibleAtNanos` derived from `AudioTrack.getTimestamp`, so the UI delays the beat
flash until the click is actually heard and ignores events from stale sessions; `playback` is a
StateFlow that a newly created ViewModel or the service adopts; `sessionEnded` is an event flow
rather than state because a session can start and fail within the same frame.

Log events (tag `FA.Metronome`, `FA.Playback`): Metronome: `metronome.start` / `stop` / `ended` / `focus_lost` / `noisy` / `track_open` / `track_closed`
(underruns) / `error`; playback: `playback.error`.
