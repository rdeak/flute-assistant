# Tuner path

Loaded when working in this folder; moved here from the root CLAUDE.md on 2026-11-02 so it is read only when needed.

`AudioRecordTunerEngine` exposes a cold `Flow<PitchFrame>`: collecting opens the microphone
(UNPROCESSED source when available, 4096-sample window advanced by 2048, conflated on
Dispatchers.IO) and cancelling releases it; it throws `SecurityException` without RECORD_AUDIO.
`YinPitchDetector` (pure Kotlin: YIN plus a silence gate and period refinement for high notes)
produces the estimate; the engine uses `YinPitchDetector.forFlute`, which searches only one
semitone beyond `FluteRange` (so voices and mains hum below the flute cannot register as low
notes) and gates at `QUIET_ROOM_RMS` (about -60 dBFS, a value only a real phone can confirm).
`TunerViewModel` gates on probability (`MIN_PROBABILITY` 0.85 on top of YIN's own 0.2
threshold: two stacked confidence gates whose combined cliff sits at about 9 dB breath-noise
signal-to-noise, right where a beginner's airy tone lives; the KDoc on `MIN_PROBABILITY`
explains how to read `tuner.summary` to confirm it on a phone and what the evidence-backed
relaxation is), publishes a reading only once the last `LOCK_FRAMES` (3,
about 130 ms) agree within `LOCK_CENTS` (30) so a syllable or a bump never shows a note and a
disagreeing frame freezes the reading instead of moving it, median-smooths a 5-frame window and
holds the last reading for about 0.9 s of silence or unsteadiness before clearing;
`NoteMath.frequencyToNote` turns the median into a `NoteReading`. Two modes (`TunerMode`,
remembered through `SettingsStore.saveTunerMode`, default `IDENTIFY`): `IDENTIFY` names the
nearest note (`TunerUiState.identifiedMidi`: the reading's note, but a drift just past the halfway
point to a neighbour, still within `NEAR_TARGET_CENTS` of the old note, moves it only after
`TunerViewModel.SWITCH_FRAMES`, about 0.5 s, so it never flickers; the first note after silence
and a clear step to another note move it at once) and measures against it; `TARGET` lets the
child pick the note to play with the
‹ › half-step buttons of `TargetNote` (`FluteRange`: c1 to c4, MIDI 60 to 96, default a1 / A4, remembered through
`SettingsStore.saveTargetNote` and clamped on load) and measures against it with
`TunerUiState.centsOffTarget` (`NoteMath.centsBetween`), so a wrong note is pinned at an end of
the pitch window instead of being relabelled. `TunerUiState.homeMidi` (the chosen or identified
note) is the pitch window's home ring, `heardMidi` its sound bubble (the home note, or the other
note or octave played in target mode) and `gaugeCents` (cents from `homeMidi`) what
`PitchWindow` and the status line show; `TunerUiState.status` judges it against
`IN_TUNE_CENTS` (10, a child's wobble, deliberately wider than a professional tuner's 5), the one
place that threshold lives (the bubble sits in its ring exactly when the status says
"Perfect!" and jumps clear of it as soon as it does not). There is no tolerance
setting on purpose: one good default beats a control. In target mode a reading within
`NEAR_TARGET_CENTS` (70) of the chosen note or of its octaves is still that note or that octave
(`TargetMismatch.NONE`, `OCTAVE_UP` / `OCTAVE_DOWN`), so a1 played sixty cents flat reads
"Too low", not "That's gis1". `HearingTracker` turns level plus
`FrameDecision` into `Hearing` (sound from `SOUND_LEVEL`, quiet or loud around `CLOSE_LEVEL`,
reported after `UNCLEAR_FRAMES` sound frames, a gap of `UNCLEAR_GAP_FRAMES` tolerated; since
2026-11-04, at the user's "make the advice line steadier" after a phone test with breathy flute
recordings showed it switching come closer / clear note / nothing within seconds: once shown, a
pause of up to `ADVICE_HOLD_FRAMES`, about 2 s, keeps the advice and the sound after it goes on
at once, and quiet and loud swap only `CLOSE_MARGIN`, about 3 dB, past `CLOSE_LEVEL`; a shown note
still clears it at once);
`TunerUiState.advice` (`TunerAdvice`) is derived from status, mismatch and hearing and shown on
one advice line under the status (spec: revision 5 of
`docs/superpowers/specs/2026-10-22-tuner-target-note-design.md`). Unsteady frames count towards
`HOLD_FRAMES` like silent ones. `noteLabel` follows the locale's octave convention
(`bool/note_octaves_helmholtz`: scientific C4 in English, music-school c1 in Croatian).
`TunerViewModel.playTargetNote` sounds the chosen note for two seconds through
`ReferenceTonePlayer` (`ReferenceToneSynth` renders it, `AudioTrackReferenceTonePlayer` plays
it) and ignores microphone frames meanwhile. `FluteFingerings.keysFor(midi)` is the C-foot
Boehm chart (Woodwind Fingering Guide basic fingerings; no entry for MIDI 94 and 95) that
`FingeringDiagram` draws.

Two pieces of this path are shared with the flute's "Your turn" (`ui/flute/CLAUDE.md`, since
2026-11-04): `NoteOnsetTracker`, a pure class that applies the same frame rules as this view
model's "Which?" mode (the probability gate, the lock, the median, the drift-and-switch of
`identify`) and additionally says when a *new* note begins: after a gap, or by a clear step to
another note without one (a slur, a fingered change). Two rules there are the flute's own, from the
review of 2026-11-04: a gap is `ONSET_GAP_FRAMES` uncounted frames that also fell under
`GAP_LEVEL_RATIO` (half) of the sounding note's mean level, because the 0.85 gate blinks on a
breathy tone and a blink that keeps the loudness must not split one held note in two (it would
count a written repeat as played); and a drift past the halfway point that switches the sounding
note after `SWITCH_FRAMES` (a long note sagging as the breath runs out) is no new note, since
nobody played one, where the tuner's ring simply moves. Both are starting values to be read
against `flute.heard` and `flute.listen_summary` on a phone. The other piece is `FrameSummary` (its own file now, `internal`),
the body of `tuner.summary` and of the flute's `flute.listen_summary`. `TunerViewModel` still
runs its own copy of those rules; moving it onto the tracker is a refactor for another day, and
the constants (`MIN_PROBABILITY`, `LOCK_FRAMES`, `LOCK_CENTS`, `SMOOTHING_WINDOW`,
`SWITCH_FRAMES`, `NEAR_TARGET_CENTS`) are referenced, never copied. The microphone card
(`ui/common/MicrophonePermissionCard`) and the advice line (`ui/common/AdviceLine`) moved out of
`TunerScreen` for the same reason; the tuner shows them unchanged. Only the open tab holds the
microphone, so the tuner and the flute never listen at once.

Log events (tag `FA.Tuner`): `mic.open` (source, rate, buffer), `mic.close`, `tuner.permission`,
`tuner.frame` (verbose: f, p, level, `FrameDecision`), `tuner.summary` every
`TunerViewModel.SUMMARY_FRAMES` frames (counts per decision, pMin/pMax/pMean against `gate`,
levelMean, reading, target, mode, hearing), `tuner.reading` when the shown note changes,
`tuner.hearing` (hearing, levelMean) when it changes, `tuner.target`, `tuner.mode`,
`tuner.reference`, `tuner.error`, `reference.error`.
