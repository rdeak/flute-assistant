# Tuner path

Loaded when working in this folder; moved here from the root CLAUDE.md on 2026-11-02 so it is read only when needed.

`AudioRecordTunerEngine` exposes a cold `Flow<PitchFrame>`: collecting opens the microphone
(UNPROCESSED source when available, 4096-sample window advanced by 2048, on Dispatchers.IO,
whose channel buffers the frames rather than conflating them: since the night of 2026-11-04 the
flute's "Your turn" counts frames, a gap being two in a row, and a busy main thread must delay a
frame, never lose one; the tuner processes every frame too, cheaply) and cancelling releases it; it
throws `SecurityException` without RECORD_AUDIO. Its `WINDOW_SIZE` (4096) and `HOP_SIZE` (2048)
are public: the replay of a recorded session frames audio with them (`ui/flute/CLAUDE.md`,
"Sessions"). It hands every hop to an `AudioTap` on its IO thread (`opened`, `samples`,
`closed`): the debug build's session recorder, which keeps the samples of a "Your turn" run;
the release build binds `AudioTap.None`. A hop whose blocking read the collector cancelled
during is dropped before the tap (`AudioRecordTunerEngineTest`, on the emulator): the recorder
may already be on the next run, and that run must not begin with a hop of this one. A frame's `level` and `minLevel` come from
`FrameLevels` (pure Kotlin, shared with the tests that frame synthetic sound the engine's way):
the window's RMS on the dB-style 0..1 scale, and the quietest 21 ms stretch of the newest hop,
which a tongued stop between two notes of one pitch drops where the window's level barely moves.
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
`identify`) and additionally says when a *new* note begins (an `Onset`, `fresh` or by a step):
after a gap, after a tongued stop inside the same pitch, or by a clear step to another note
without one (a slur, a fingered change). The rules there are the flute's own, from the review of
2026-11-04 and the first child test the same night (`ui/flute/CLAUDE.md` has the story and every
threshold): a gap is `ONSET_GAP_FRAMES` uncounted frames that also fell under `GAP_LEVEL_RATIO`
(half) of the sounding note's mean level or back to the room's floor the tracker learns between
notes, because the 0.85 gate blinks on a breathy tone and a blink that keeps the loudness must not
split one held note in two (it would count a written repeat as played), while `HOLD_FRAMES` of
them at any level are a pause; a tongued stop is heard in `PitchFrame.minLevel`; and a drift past
the halfway point that switches the sounding note after `SWITCH_FRAMES` (a long note sagging as
the breath runs out) is no new note, since nobody played one, where the tuner's ring simply moves.
All are read against `flute.heard` and `flute.listen_summary` on a phone and pinned by
`ListenPathTest` on synthetic sound. Since 2026-11-05 the tracker takes every threshold it
applies as a `Rules` value whose defaults are the constants (`NoteOnsetTracker.Rules`, with
`fields()` for the session log and `with(name, value)` for a sweep) and can be built with a
seeded room floor (`floor`, readable: the view model's tracker keeps it from one run to the
next, and a replay must start where the phone started); the app builds it with the defaults, so
nothing about the rules changed. The other piece is `FrameSummary` (its own file now, `internal`),
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

## The tab on screen

Moved here from the root CLAUDE.md on 2026-11-05.

The tuner has the two mode pills on top ("Which?" / "Tune"), then a stage with the pitch window
(since 2026-10-24, replacing a face meter): a dashed home ring holding the note to play on a dotted
up-down guide, and the child's sound as a bubble floating above it when too high and below when too
low, with an arrow pointing home; another note shows as that note filled in its colour, in tune the
bubble fills, sits in the ring and gets a tick and sparkles, and in "Which?" silence the ring is
grey with "…". In "Tune" the window sits between ‹ › with a "Hear" pill under it (spec:
`docs/superpowers/specs/2026-10-22-tuner-target-note-design.md`); then the fingering for the ring's
note, one status line and, under it, one quieter advice line that says what to change (roll the
flute, air, fingers, come closer, a clear note), never a number.

## Sideways

Since 2026-11-05 (spec `docs/superpowers/specs/2026-11-05-landscape-design.md`, "Tuner"; what every tab
shares sideways is in `../CLAUDE.md`). `TunerContent` branches on `LocalSideways` before its padded column:
the microphone card first (it branches itself, below), then `TunerSideways` (`TunerSideways.kt`). One layout,
as the height leaves little choice. The numbers come from `SidewaysGeometry`, which the unit tests pin.

- **The pitch window** stays vertical, because up means higher, and grows to fill the card's height:
  `pitchWindowScale` is the inside height over 300, so 1.19 at 411 tall (the home ring 157 dp across against
  132) and about 1.02 at 360. `PitchWindow` takes `maxScale` for this, where upright keeps its cap of 1. In Tune
  mode it stands between the ‹ › (52 dp); in Which? mode their room stays empty, so the ring does not move
  when the mode changes.
- **The 20 dp gap.** The arrows stand 20 dp from the window, upright's `ButtonGap`, and the window block is 20
  dp from the words (`SidewaysGeometry.ARROW_GAP`, `FINGERING_GAP`; `PITCH_WINDOW_SIDES` is derived from
  them, so the decision below and the layout use the same numbers). The first build had 14, from the mockup,
  and the sound bubble's arrow, which reaches 24 x scale dp past the window's edge (about 29 dp at 1.19), ran
  into the › button in the 2 to 4 cent band just outside "Perfect!", which is when the child is closest. Under
  the 750 dp edge the arrows are 44 dp with a 12 dp gap, as `fit-v2.html` draws.
- **The fingering's rule.** It stands beside the window, under the status and advice lines, in what the card
  leaves: `fingeringRoom` is the card's inside less the window with its arrows and the 20 dp gap, so 324 dp
  wide at 915 x 411 and 255 at 800 x 360. When that is under 205 (`fingeringBeside`) it moves under the window
  across the card, and the window shrinks by its height plus 12 dp to make room (237 dp tall; `fit-v2.html`).
  At 360 tall that is a window under 750 dp wide: the 640 wide phone, and a wider phone whose camera hole
  leaves it less, as a hole takes width from the tab (the emulator's takes 45 dp at 800 x 360). An 800 wide phone
  keeps the full-size ring with a hole up to 50 dp, a 780 wide one with a hole up to 30. The number was 240 first,
  which put the edge at 785 and sent a 780 wide phone, or an 800 wide one with a hole over 15 dp, to the
  small layout (the final review, 2026-11-06). 205 keeps it down to 750 with the fingering 205 wide and about 30 tall; `TunerSidewaysTest` covers 780 and 750. The status line (40 sp in a 48 dp box; 28 sp in 64 under the
  750 dp edge) and the advice line (22 sp in a fixed 58 dp box, two lines, 19 sp under that edge)
  have fixed room, so the fingering never moves when advice comes and goes. A failed microphone says one translated
  line (`micFailed`, `mic_failed`) in the advice's box, as nothing is heard then, so it moves nothing either
  (upright it stands under the advice). The cause goes to the log (`tuner.error`) and never to the screen: a child can do nothing with it. Both shrink before they cut a
  word (`StatusLine` and `AdviceLine` have floors and `autoSize` for large text).
- **The right column** holds the buttons, 116 wide with the emoji over the word: Hear at the top in Tune mode only
  (84 tall, faded while the note plays), and Which? over Tune as one child low (92 tall, 80 when the window is short), the
  chosen one yellow; one child, so the pair stays low whether or not Hear is above it. They are a
  `selectableGroup` with the `tuner_mode` description.
- **Asking for the microphone** (tuner and flute): `MicrophonePermissionCard` branches itself, so the flute
  gets it free. The disc on the left of the card (up to 0.38 of its width), "Can I listen?" and its line beside
  it; Yes low on the right, Settings at the top of the column, and the flute's Your turn adds its quiet Not
  now under Settings (it has no emoji).

