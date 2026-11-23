# Tuner path

Loaded when working in this folder; moved here from the root CLAUDE.md on 2026-11-02 so it is read only when needed.

`AudioRecordTunerEngine` exposes a cold `Flow<PitchFrame>`: collecting opens the microphone
(UNPROCESSED source when available, 4096-sample window advanced by 2048, on Dispatchers.IO,
whose channel buffers the frames rather than conflating them: since the night of 2026-11-04 the
flute's "Your turn" counts frames, a gap being two in a row, and a busy main thread must delay a
frame, never lose one; the tuner processes every frame too, cheaply) and cancelling releases it; it
throws `SecurityException` without RECORD_AUDIO. Its `WINDOW_SIZE` (4096) and `HOP_SIZE` (2048)
are public: the replay of a recorded session frames audio with them (`ui/flute/DESIGN.md`,
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
A dip still falling where the search ends gives no pitch (since 2026-11-14): it is the near side
of a period longer than the range, and until then the end of the search was reported as that
period, so a voice at 230 to 246 Hz read as a steady low B (246.15 Hz at 48 kHz) and "Your turn",
which takes a note in any octave, took talk for the tune's B (`ui/flute/DESIGN.md`, "The room is
not judged"). A real dip that bottoms out on the last lag is still a pitch.
It reads the newest half of the window (since 2026-11-10; `differenceAt` sums over the stretch
that ends on the window's last sample), so a frame's pitch and its `minLevel` describe the same
hop: it read the oldest half before and heard every note one frame, 43 ms, late, which the
phone's recorded sessions of 2026-11-05 showed (60 of 66 notes moved a frame or two earlier, no
mark changed; a comparison with TarsosDSP's YIN and McLeod on the same sessions found nothing
else worth having, so the library was turned down). `YinPitchDetectorTest` pins both ends: a note
that began inside the newest hop is heard, and a frame whose newest hop is silent has no pitch.
`ReadingTracker` (`domain/listen`, the tuner's reading rules since 2026-11-23; `TunerViewModel` feeds it every frame
and mirrors its reading and identified note into the state) gates on probability (`MIN_PROBABILITY` 0.85 on top of YIN's own 0.2
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
point to a neighbour, still within `ListenRules.NEAR_CENTS` of the old note, moves it only after
`ListenRules.SWITCH_FRAMES`, about 0.5 s, so it never flickers; the first note after silence
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
`ListenRules.NEAR_CENTS` (70) of the chosen note or of its octaves is still that note or that octave
(`TargetMismatch.NONE`, `OCTAVE_UP` / `OCTAVE_DOWN`), so a1 played sixty cents flat reads
"A little higher", not "That's gis1". `HearingTracker` turns level plus
`FrameDecision` into `Hearing` (sound from `SOUND_LEVEL`, quiet or loud around `CLOSE_LEVEL`,
reported after `UNCLEAR_FRAMES` sound frames, a gap of `UNCLEAR_GAP_FRAMES` tolerated; since
2026-11-04, at the user's "make the advice line steadier" after a phone test with breathy flute
recordings showed it switching come closer / clear note / nothing within seconds: once shown, a
pause of up to `ADVICE_HOLD_FRAMES`, about 2 s, keeps the advice and the sound after it goes on
at once, and quiet and loud swap only `CLOSE_MARGIN`, about 3 dB, past `CLOSE_LEVEL`; a shown note
still clears it at once);
`TunerUiState.advice` (`TunerAdvice`) is derived from status, mismatch and hearing and shown on
the one line, in place of the status, since 2026-11-18 (until then on an advice line of its own under the status;
spec: revision 5 of `docs/superpowers/specs/2026-10-22-tuner-target-note-design.md`). What the line says is one
value, `TunerUiState.line` (`TunerLine`: the failed microphone, the advice, listen, the cheer, a held fix, the prompt,
the first that holds), and the screen only turns it into words. A fix on the flute (`TunerAdvice.isFluteFix`: the head
joint, rolling) stays on it about 5 s after the sound stops, so the guide can still be opened from it: `FixHold`
(`domain/listen`) counts `FixHold.HOLD_FRAMES` silent frames, the view model feeds it every frame and keeps the answer
in `TunerUiState.heldFix`, so a turn keeps it (it was a timer inside the line's composable until the review of
2026-11-18, and a turn dropped it). Other words, a new sound, the note playing, opening the guide and leaving the tab
drop it at once. Unsteady frames count towards
`HOLD_FRAMES` like silent ones. `noteLabel` follows the locale's octave convention
(`bool/note_octaves_helmholtz`: scientific C4 in English, music-school c1 in Croatian).
`TunerViewModel.playTargetNote` sounds the chosen note for two seconds through
`ReferenceTonePlayer` (`ReferenceToneSynth` renders it, `AudioTrackReferenceTonePlayer` plays
it) and ignores microphone frames meanwhile; it does nothing unless a tap on the window plays the note
(`TunerUiState.tapPlaysNote`: Tune mode, in silence). `FluteFingerings.keysFor(midi)` is the C-foot
Boehm chart (Woodwind Fingering Guide basic fingerings, checked again on 2026-11-17; every note from MIDI 60 to
96 has one since then, B♭6 and B6 with a trill key; B♭ stays "one and one") that `FingeringDiagram` draws
(`ui/common` since 2026-11-10: the flute's listening room shows it too, after a wrong note, and since 2026-11-17
the Fingers tab). Since 2026-11-17 it is the flute as the player sees it, the fifteen keys a finger touches each
where it sits, a teacher's ask ("not how they mark keys"; spec `2026-11-17-fingers-tab-design.md`); its numbers
are `FingeringGeometry`, 340 x 66, so it is 66 dp tall at the stage's 340 where the strip was 49. The tuner draws
it in both modes, for the chosen or the heard note, and with no note every key open. The tab's least height grew
with it, 440 to 457 on a 412 dp phone: below that the window, at its least, would stand on the flute (it squeezed Hear
while Hear stood under the window, until 2026-11-18). The picture fills the stage and its height
follows the stage's width, so the least height does too (`TunerMinHeightLessFingering` and the picture's own); it was
the one number 457 until the review of 2026-11-17. Since the calmer Tune view (2026-11-18: no Hear and How?, one line)
it is 395 on a 412 dp phone, 385 at 360 dp wide, 430 in a pane 590 wide, measured so the window keeps about 124 dp.

Two pieces of this path are shared with the flute's "Your turn" (`ui/flute/DESIGN.md`, since
2026-11-04): `NoteOnsetTracker`, a pure class that applies the same frame rules as this view
model's "Which?" mode (the probability gate, the lock, the median, the drift-and-switch of
`identify`) and additionally says when a *new* note begins (an `Onset`, `fresh` or by a step):
after a gap, after a tongued stop inside the same pitch, or by a clear step to another note
without one (a slur, a fingered change). The rules there are the flute's own, from the review of
2026-11-04 and the first child test the same night (`ui/flute/DESIGN.md` has the story and every
threshold): a gap is `ONSET_GAP_FRAMES` uncounted frames that also fell under `GAP_LEVEL_RATIO`
(half) of the sounding note's mean level or back to the room's floor the tracker learns between
notes, because the 0.85 gate blinks on a breathy tone and a blink that keeps the loudness must not
split one held note in two (it would count a written repeat as played), while `HOLD_FRAMES` of
them at any level are a pause; a frame with a clear pitch under `MIN_LEVEL` (0.45, since 2026-11-10) does not
count at all, so the room's voices are not judged (`FrameDecision.FAINT`; the tuner's own path has no such floor and
its ring may show a quiet note); a tongued stop is heard in `PitchFrame.minLevel`; and a drift past
the halfway point that switches the sounding note after `SWITCH_FRAMES` (a long note sagging as
the breath runs out) is no new note, since nobody played one, where the tuner's ring simply moves.
Since 2026-11-16 the tracker also measures a step from the note it hears sounding, not only from the
written pitch (`Rules.stepFromSounding`), so a semitone slurred on a flute off its pitch is a new note
and not drift (`ui/flute/DESIGN.md`, "Sharps and flats"); that is the flute's rule, and the ring's own
`identify`, in `ReadingTracker`, has none and needs none.
All are read against `flute.heard` and `flute.listen_summary` on a phone and pinned by
`ListenPathTest` on synthetic sound. Since 2026-11-05 the tracker takes every threshold it
applies as a `Rules` value whose defaults are the constants (`NoteOnsetTracker.Rules`, with
`fields()` for the session log and `with(name, value)` for a sweep) and can be built with a
seeded room floor (`floor`, readable: the view model's tracker keeps it from one run to the
next, and a replay must start where the phone started); the app builds it with the defaults, so
nothing about the rules changed. The other piece is `FrameSummary` (its own file now, `internal`),
the body of `tuner.summary` and of the flute's `flute.listen_summary`. The tuner's own rules are `ReadingTracker`'s
(since 2026-11-23; they were in `TunerViewModel` until then): it and `NoteOnsetTracker` share the frequency window
(`SmoothingWindow`: the lock and the median) and nothing else, as the flute's floor, gaps and `MIN_LEVEL` are not the
tuner's, and
the constants (`MIN_PROBABILITY`, `LOCK_FRAMES`, `LOCK_CENTS`, `SMOOTHING_WINDOW`,
`SWITCH_FRAMES`, `NEAR_CENTS`, all in `domain/listen/ListenRules`) are referenced, never copied. The microphone card
(`ui/common/MicrophonePermissionCard`) and the advice line (`ui/common/AdviceLine`) moved out of
`TunerScreen` for the same reason; the tuner shows them unchanged. Only the open tab holds the
microphone, so the tuner and the flute never listen at once.

Log events (tag `FA.Tuner`): `mic.open` (source, rate, buffer), `mic.close`, `tuner.permission`,
`tuner.frame` (verbose: f, p, level, `FrameDecision`), `tuner.summary` every
`ListenRules.SUMMARY_FRAMES` frames (counts per decision, `faint` among them since 2026-11-10 and always 0 here, as only the flute's tracker decides it; pMin/pMax/pMean against `gate`,
levelMean, reading, target, mode, hearing), `tuner.reading` when the shown note changes,
`tuner.hearing` (hearing, levelMean) when it changes, `tuner.target`, `tuner.mode`,
`tuner.reference`, `tuner.error`, `reference.error`, `tuner.guide` (action `open`, `page` or `close`; `page`,
the card's index; `via` `line` on open, the line being its one way in since 2026-11-18, and `ok`, `close`, `back`
or `fingers` on close: a flute tapped on the Fingers tab, `TunerViewModel.tune`).

## The tab on screen

Moved here from the root CLAUDE.md on 2026-11-05.

The tuner has the two mode pills on top ("Which?" / "Tune"), one line of words, then a stage, the card, with the
pitch window (since 2026-10-24, replacing a face meter): a dashed home ring holding the note to play on a dotted
up-down guide, and the child's sound as a bubble floating above it when too high and below when too
low, with an arrow pointing home; another note shows as that note filled in its colour (a black note in its
letter's, named as the whole app names it since 2026-11-16, E♭ and B♭ rather than D♯ and A♯, `NoteSpelling`), in tune the
bubble fills, sits in the ring and gets a tick and sparkles, and in "Which?" silence the ring is
grey with "…". In "Tune" the window sits between ‹ › (spec: `docs/superpowers/specs/2026-10-22-tuner-target-note-design.md`);
in "Which?" their room stays empty, so the window keeps its size and place. Under it, the fingering for the ring's
note. Never a number.

**The calmer Tune view** (since 2026-11-18, the user: "tunner tune view is packed, all elements are figthing for
attention and overlapping, missing structure ... drop buttons hear how from view completly"; spec
`docs/superpowers/specs/2026-11-18-tuner-calmer-tune-design.md`, option B of three boards rendered by the app,
`.superpowers/brainstorm/86742-1794990954/`). Until then the view held six chunky buttons, two lines of words and two
coloured pictures, and on the A55's 653 dp tab the window ended where the flute began. Now:
- **No Hear and How? buttons.** In Tune mode a tap on the window plays the chosen note (`PitchWindow`'s `onTap`; a
  screen reader hears a button, "Hear"; only in silence, `TunerUiState.tapPlaysNote`: not while the note plays, not
  while the child plays, as a finger on the bubble would start the note and blank the reading, the user's call of the
  same day; never in Which?). A line that names a fix on the
  flute opens the guide on its card (`TunerAdvice.guideCard`: the head joint on "Tune on a1", rolling on "One note still
  off?"; a screen reader hears "How to tune"); every other line opens nothing. No ripple on either: the sound and the
  guide answer the tap. Do not bring the buttons back, nor a hint or badge that explains the taps (the 2026-11-17
  lesson: when a screen is too much, take things out); the guide's "Check each note" card says to tap the note.
- **One line of words** (`StatusLine`), under the pills in both modes: the advice when there is one, else the status;
  a failed microphone takes it in the error colour. A flat or sharp reading always carries advice, so "⬆️ A little
  higher", "⬆️ That's E4" and the octave words stay off the screen: the window's bubble and arrow say up or down and name
  another note, and the line says what to do. It was the status line, on top since 2026-11-17 (the user: "move ...
  label to top so it does not overlap with flute"); do not put it back under the flute. The separate advice line under
  the flute went (`AdviceLine` stays for the flute's listening room). It shrinks to `WordFloor`, a floor in dp, so the
  longest advice stays whole at twice the text on a 360 dp phone.
- **The window grows** into the room the buttons and the second line left, up to `WINDOW_MAX_SCALE` (1.5, 450 dp
  tall) and no wider than the arrows leave it (`TargetNote`); 382 dp tall on the A55's tab, 16 dp clear of the flute.
  The card's free height goes round the window and the flute, not between them.
- **The flute lights up when it helps** (`TunerUiState.fingeringLit`, `TunerFingering`): its pressed keys are in the
  note's colour before a sound (how to play it) and on another note or octave, and grey (`quietKeys`, halfway from an
  open key to the muted words, readable at night) while the
  right note sounds, so the eyes go to the window. The colour fades; nothing moves. In Which? it is grey while a note
  sounds.
The tab reads, from the top: the mode pills, the line, the card (`../CLAUDE.md`, "One order"). `TunerContent`
takes the state and one `TunerActions`, as the flute and the notes tab take theirs. The pieces both layouts show are
written once and given their sizes: `StatusLine` (the line), `TunerWindow` (the window, what a screen reader hears of
it and its tap), `StepArrows` (the ‹ › round it, or their empty room in Which?) and `TunerFingering`. The line and the
flute take only what they show (`TunerLine`; the note and whether it is lit), so a frame the microphone hears redraws
the window and not them. The card is tagged
`tuner_stage` for the tests. The day before, Hear and How? had moved over the window, then over the card, at the
user's asks of that morning and evening: they are gone now. The canvas's tuner board still draws the old view.

**From the Fingers tab** (since 2026-11-18, the user's ask): a tap on a flute there opens its note here in Tune mode
(`TunerViewModel.tune`: the mode and the note set, saved and logged as the child's own, and an open guide closed
with `via` `fingers`); `MainScreen` calls it and then shows this tab. The phone's Back then goes back to the Fingers
tab (the user, the same evening: "go back from tuner to fingering when user land on tuner from fingering"; an open
guide closes first), until a tab is chosen on the bar or the rail: then it leaves the app, as on every tab
(`MainScreen`'s `BackToTab`, `BackToTabTest`).

**How to tune** (2026-11-14, at the user's request: "does not tell what to change on flute"; spec
`docs/superpowers/specs/2026-11-14-tuning-guide-design.md`, mockups in the brainstorm folder it names).
- **The head-joint hint.** In Tune mode with `FluteRange.TUNING_NOTE` (a1 / A4) chosen, a flat or sharp
  reading of that note says "🔧 Push the head joint in a little" / "Pull … out" (`TunerAdvice.HEAD_JOINT_IN`,
  `HEAD_JOINT_OUT`) where every other note, and Which? mode on A4 too, still says to roll the flute. The head
  joint is set once, on one note, warmed up and at the usual loudness; afterwards single notes drift by
  themselves (low flat, high sharp) and the lips fix them, so naming the head joint on any other note would
  have a child move it for one note and spoil the rest. Revision 5 had left the head joint to the teacher;
  the child's flute of 2026-11-12/13, sharp by about 30 cents on every note, is why it changed.
- **The way in** is the line, since 2026-11-18: a line naming the head joint or rolling opens the guide on that card
  (`TunerViewModel.openGuide(card)`). From 2026-11-14 it was a 🔧 How? pill beside Hear; both went with the calmer
  Tune view.
- **The guide** (`TuningGuide`, pictures in `TuningGuidePictures`) takes the tab's place: four cards
  (`GuideCard`: warm up, the head joint on A4, check each note, one note still off: leave the head joint and
  roll the whole flute), each a picture over a title and a few words, ‹ › and dots, ✕ to leave, a green OK
  in place of the dots and › on the last card (upright; sideways a ✓ named OK, as "U redu" does not fit half
  the column). The phone's Back closes it (`BackHandler` in `TuningGuide`). Its card is
  `TunerUiState.guideCard`, so a turn and a tab switch keep it; it opens on the card the line names (until
  2026-11-18 always on the first; ‹ still leads back to "Warm up first"), and
  the microphone stays on underneath. The words give way before the picture: they shrink to 12 dp and the
  picture keeps at least 40% of the card, upright and sideways, at 2x text (`TuningGuideTest`). The pictures
  hold the text size at 1x and scale as a whole, and are silent to a screen reader: the card's words say it. Card 2's
  picture is the two moves of the head joint named "push in" and "pull out", the words of the tuner's line; their chips
  with "⬆️ A little higher" / "⬇️ A little lower" went on 2026-11-18, when the line began to say the move itself (the
  user's pick). Card 3's shows 👆 🔊 for "tap the note to hear it".

**One direction on screen** (2026-11-10, found on the user's phone with real sound). Off the note, the status line
says which way to go and its arrow points the way the window's own arrow does: "⬆️ A little higher" / "⬆️ Malo više"
under a bubble that floats below its ring with ↑ beside it, "⬇️ An octave lower" / "⬇️ Oktavu niže", "⬆️ That's E4"
(the arrow is the way to the chosen note). Until then it said what was wrong, "⬇️ Too low", under the window's ↑: two
arrows pointing opposite ways for one thing, and a child had to turn "too low" into "go up". The string names
(`too_low`, `too_high`) still say what is wrong, as `TuningStatus` does; only the words
changed. Do not put an arrow on that line that points against the window's. Since 2026-11-18 the tuner's line says the
advice whenever a reading is off (above), so those words are not on it: "A little higher" and "A little lower" are the
flute's Listen to me's now, and the octave and "That's E4" strings, which nothing could show any more, went with the
review of the same day (git has them). The rule holds if such words come back. The screen reader's sentences
(`pitch_low` ...) already said both. The Play listing and its copy in `docs/release/play-console.md` still quote
"Too high" and "Too low": the user changes the listing.

## Sideways

Since 2026-11-05 (spec `docs/superpowers/specs/2026-11-05-landscape-design.md`, "Tuner"; what every tab
shares sideways is in `../DESIGN.md`). `TunerContent` branches on `LocalSideways` before its padded column:
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
- **The fingering's rule.** It stands beside the window, under the line of words, in what the card
  leaves: `fingeringRoom` is the card's inside less the window with its arrows and the 20 dp gap, so 324 dp
  wide at 915 x 411 and 255 at 800 x 360. When that is under 205 (`fingeringBeside`) it moves under the window
  across the card, and the window shrinks by its height plus 12 dp to make room (237 dp tall; `fit-v2.html`).
  At 360 tall that is a window under 750 dp wide: the 640 wide phone, and a wider phone whose camera hole
  leaves it less, as a hole takes width from the tab (the emulator's takes 45 dp at 800 x 360). An 800 wide phone
  keeps the full-size ring with a hole up to 50 dp, a 780 wide one with a hole up to 30. The number was 240 first,
  which put the edge at 785 and sent a 780 wide phone, or an 800 wide one with a hole over 15 dp, to the
  small layout (the final review, 2026-11-06). 205 keeps it down to 750 with the fingering 205 wide and about 40 tall
  (30 with the strip before 2026-11-17); under the window it is at most 300 dp wide (`UnderFingeringWidth`), so the
  taller flute takes no more of the window than the strip did across the whole card; `TunerSidewaysTest` covers 780 and 750. The one
  line (since 2026-11-18, the calmer Tune view; until then a status line, 40 sp in a 48 dp box, over an advice line in
  a fixed 58 dp box) takes the room both had, 108 dp, up to two lines at 40 sp (124 dp and three lines at 28 sp under
  the 750 dp edge), so the fingering stands where it stood and never moves whatever the line says. A failed microphone
  says one translated line (`micFailed`, `mic_failed`) in it, as nothing is heard then. The cause goes to the log
  (`tuner.error`) and never to the screen: a child can do nothing with it. It shrinks before it cuts a word
  (`StatusLine`'s floor and `autoSize`).
- **The right column** holds the buttons, 116 wide with the emoji over the word: Which? over Tune as one child low
  (92 tall, 80 when the window is short), the chosen one yellow, in both modes. They are a `selectableGroup` with the
  `tuner_mode` description. Hear over How? stood at the top in Tune mode until 2026-11-18 (the calmer Tune view: the
  window is Hear and the line opens the guide, as upright).
- **Asking for the microphone** (tuner and flute): `MicrophonePermissionCard` branches itself, so the flute
  gets it free. The disc on the left of the card (up to 0.38 of its width), "Can I listen?" and its line beside
  it; Yes low on the right, Settings at the top of the column, and the flute's Your turn adds its quiet Not
  now under Settings (it has no emoji). **Settings shows only once the phone will not ask again** (2026-11-10):
  it is the one button in the app that leaves it, and until then a child met it on the tuner's very first
  opening. The card takes `onOpenSettings` as null until then; the screens' `rememberMicrophonePermission` (`ui/common`, shared by both since 2026-11-23) asks
  `shouldShowRequestPermissionRationale` after a no and tells the view model (`TunerViewModel.onPermissionAnswer`,
  `FluteViewModel.onMicResult`), which keeps `micBlocked` in its state, so it survives a tab switch; a grant ends it.
  After the app restarts the pill is back at the first "Yes" the phone refuses to ask for. A question dismissed
  without an answer counts as blocked too (the phone gives the same answers), which only shows the pill early.

## The microphone in split screen

Since 2026-11-06 (spec `2026-11-06-tablet-design.md`, "Google's basics"), accepted as it is. On Android 10 and
later, when two apps record, the one on top gets the audio and the other silence, with no error: the tuner goes
quiet and hears again when its pane is back on top. `AudioRecordTunerEngine` does not look at `isClientSilenced`, so
`micFailed` stays false and the flute's Your turn just waits. Showing the failure line would need plumbing through
both ViewModels and new words on the flute; giving the microphone up on losing focus would stop Your turn when the
child touches the other pane. Android 7 to 9 stays first come, first served. None of this has been run on a device:
the emulator needs `-no-audio`, and there is no tablet.
