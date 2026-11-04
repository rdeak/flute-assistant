# Digital flute path

Loaded when working in this folder; moved here from the root CLAUDE.md on 2026-11-02 so it is read only when needed.

`Tune` (`domain/flute/`) is the child's tune, a list of `TuneItem`s: `TuneNote(midi, length)`,
`TuneRest(length)` and `TuneBreath` (both since 2026-11-02: a rest is a silence the child holds
the rest key for, a breath only a sign for the player, no sound and no time), at most `MAX_NOTES`
(200, rests and breaths included). Since the canvas of 2026-10-24 a note's `NoteLength` (and a rest's) is how long its key was held,
snapped to the metronome tab's tempo (read from `SettingsStore.loadMetronomeConfig`, clamped to
the ring's 40 to 200): the nearest of an eighth, a quarter, a half, a dotted half and a whole
note (`NoteLength.ofBeats`, split halfway: ¾, 1½, 2½, 3½ beats; longer stays a whole note).
Lengths are kept in beats, so the tempo applies only at playback and changing it later keeps
the rhythm. Time is counted in the metronome's `Pulse` (`domain/flute/Tune.kt`: its bpm and time
signature, read at each press and at Play): one click is one beat of the signature's note value,
so in 6/8 a click is an eighth note and a quarter lasts two clicks (since 2026-11-02, at the
user's request, so a flute bar lasts as long as the metronome's; the hold dots that counted those
clicks under the bubble went with the bubble in the canvas of 2026-11-02, see below). `Tune.durationMs` turns a length into time, with `gapMs` (80 ms, at most a quarter
of the note) taken out of its end so a repeated note is heard twice and the tempo stays exact.
The time between presses is not recorded (a child hunting for the next key would make the tune
halting): a silence is only ever a rest the child put there. The stored string
(`"60:2,r:2,b,67:4"`: a note's MIDI or `r` for a rest, then half beats, and `b` for a breath;
`decode` is lenient and ignores a third field, `sanitize` keeps only `StaffNotes.ALL` notes and
never two breaths in a row, older builds drop `r` and `b`) reads a note value above 8 as a held time in ms from before lengths were
snapped (never under 300), converted at the stored tempo; `SettingsStore.saveTune` keeps it
under `tune` across restarts. `Bars.write` (`domain/flute/Bars.kt`, since 2026-11-02) is how the
staff writes the tune in bars of the metronome tab's time signature (`FluteUiState.timeSignature`,
read at creation, in `enterTab()` and when OK closes the speed panel, all through
`syncTimeSignature()`; a bar is `beatsPerBar * 8 / noteValue` half beats of the
flute's quarter-note beat, so 6/8 holds three quarters): `LineMark.Step` / `Breath` / `BarLine`, a
bar line after every full bar (the last too), a note or rest that runs past one split there into
`StepPart`s (the longest lengths that fit, a 2½-beat piece as a half and an eighth) that the staff
ties (a note) or just splits (a rest); the tune itself is never changed.

The stage (the user's canvas of 2026-11-02, boards `Flute*.dc.html`, then changed by the user the
same evening) holds `TuneStaff`, with the speed knob over its top left (described after this
paragraph): the staff and, under each note in the place the canvas gave
the fingering, its name (`NameRow` / `NamePill`, `StaffGeometry.NAMES_TOP`): a pill in its letter's
colour as wide as the note's room on the staff less a 3 dp gap, the name in the first beat's room
under the head, a grey 💤 pill for a rest, no pill for a breath, the bar line drawn through the
names too, a note's pills tied under them. Each step is one `LazyRow` item holding its notes and
its names, so they line up and scroll as one (the user: "align note names with the notes, left to
right"), and the held step's band covers both ("a yellow rectangle covering notes and names at the
same time"). That replaced, the same evening, a 96 dp single-name bubble and then a separate line
of pills (`TuneLine`, deleted); the hold dots and, on this tab, the fingering are gone, and
`FluteUiState.shown` is no longer drawn. A screen reader reads the row ("Your tune, N notes") and
each step once ("G4, 2 beats", "Breathe here"). `TuneStaff` draws a real five-line staff from `StaffGeometry` (pure
Kotlin, in dp: lines 18 apart, three quarters of the canvas's 24, to leave room for the bubble and
for the stems, a note step 9, `StaffNotes.positionOf` for the height, every stem up on the right
of the head as the user asked, ledger lines from `StaffNotes.ledgerLinesFor`): the faint treble clef
(`ui/common/drawTrebleClef`, shared with the notes game) and the time signature fixed at the left,
then a `LazyRow` of the `Bars.write` marks, each a `Spacer` that draws itself in a dp-scaled
`drawBehind` (a step's width is its pieces' `slotWidth`, 29 dp a beat; a bar line 10, a breath 18). Each note is drawn as printed music draws it, all of it in
its letter's colour (the user's call: "same colour as the note name, shape by the rules"): a
tilted oval head (`HEAD_TILT`) with a thin ink edge so a yellow e¹ stays visible on white and on
the yellow ring, no letter in it (the line under it names the note), filled for an eighth (plus a
flag) and a quarter, a ring for a half and a dotted half (plus a dot, moved up off a line), a wider
ring and no stem for a whole note; the stem, flag, dot and tie in the note's colour too; a rest is its symbol in `mutedText`
over its grey 💤 pill; a breath the comma with 😮‍💨 over the staff; ties go under the heads. The step at `litIndex` gets a yellow ring per head, the one at `heldIndex` also a yellow
band across its room, and is drawn under its neighbours (`zIndex(-1)`) so the ring never covers the
head beside it; it grows in place because the held step's length in `tune` is updated as it
grows. Text on the staff (the time signature, the emoji) is measured at a fixed dp size, so it
stays in the staff at any font scale. The staff with its names is `TuneStaffHeight` (241 dp, the staff 175) at every tune, so
the keys never move; c³'s stem (87 dp above the top line) may reach 12 dp into the stage's padding
and c¹'s ring and tie 8 dp into the gap above the names, which `StaffGeometryTest` checks for
every height; with the names the stage fits a 412 x 915 phone without scrolling. Where the
drawing differs from the canvas: `.superpowers/design/flute-assistant-screens/README.md`. Sound:
`FluteTimbre` (`audio/flute/`, the harmonics the tuner's `ReferenceToneSynth` now shares)
under `SustainedToneSynth`, a phase-continuous oscillator with an attack (30 ms) / sustain /
release (120 ms) envelope and a retrigger fade (10 ms), so a note on, a note off or a takeover
never clicks; `AudioTrackFluteTonePlayer` (`FluteTonePlayer`) streams it from an URGENT_AUDIO
thread like the metronome (float PCM, low latency, media attributes, no audio focus), writing
silence while nothing is held and every block written in full (`writeFully`, retried through
a route rebuild so a sustained tone never skips samples); on `close()` the thread
(`ToneRenderLoop`, the loop body kept pure Kotlin so the close path is unit-tested) lets a
sounding note release and plays the track buffer out before it gives the track back, never
touches the command queue once retired (after a rotation the successor thread is already
serving the keys), and a new `open()` meanwhile starts its own track at once, so two tracks
overlap for the fade rather than the first press after a rotation waiting for it. `FluteViewModel` (Activity-scoped; specs:
`docs/superpowers/specs/2026-10-23-digital-flute-design.md`, rests and breaths
`docs/superpowers/specs/2026-11-02-flute-rest-and-breath-design.md`) opens the player in `enterTab()`
and closes it in `leaveTab()` (`FluteScreen`'s `LifecycleStartEffect`, so the first press is
never late and nothing plays off screen); `onKeyPressed(midi)` sounds the key at once, shows
it (`FluteUiState.shown`, a `Shown.Note`, `Rest` or `Breath`) and appends it as an eighth (not at the cap, where it only sounds), then grows
it while the key stays down at `Tune.growAtMs` (`heldLength`, and the step's length in `tune`,
which the staff shows); `onKeyReleased(midi)` silences it and records the length the hold made from
`MonotonicClock` (`Tune.lengthOf`, the same rule, so the note never changes at lift-off), ignored
unless that key is the one held (the newest press wins; sliding off a key releases it); a key the
finger let go of and pressed again within `HOLD_REJOIN_MS` (60 ms), nothing else having happened
since (a new hold, a hold ended by another key, a breath, Play, the speed panel or leaving the tab,
or an erase all forget it), goes on with the same note from its first press, at the length the
whole hold has made and growing from there (`rejoin`, logged as `flute.rejoin`; the rest key the same): since
2026-11-03, because a touch screen can report one hold as several pieces (its palm rejection
cancelling a flat finger and starting it over, or a bounce), and each piece became an eighth note
of its own, the user's "bunch of short notes" when holding a key; a deliberate repeat of a note is
far slower than the window, and Android itself takes an up and a down under 40 ms for jitter;
`onKeyActivated(midi)` is the screen-reader path (one beat: a quarter note);
`onRestPressed` / `onRestReleased` / `onRestActivated` are the same for the rest key without a
sound (`restHeld`; a rest that takes over a held note silences it, since a note taking over a
note deliberately skips the note off), and `onBreath` ends a held key and appends a breath unless
the last step is one (a breath at the cap or after a breath is only shown); `togglePlayback()`
plays the tune from the start at the metronome's tempo, each note for its length, lighting it
on the staff, on its name and on its key, silent for each rest's whole length (its symbol and
its pill ringed, the rest key sunk: `restLit`) and straight past each breath, and
stops at once on the pill, on a key press, on `openSpeed()` or on `eraseLast()`, which drops the
last step (the erase button repeats while held).

The speed knob and its panel (the user's pick of 2026-11-03 from three mockups, spec
`docs/superpowers/specs/2026-11-03-flute-speed-knob-design.md`): the tune plays at the metronome
tab's tempo and in its time signature, and the flute now shows and sets both. `SpeedKnob` is the
ring's knob moved to the staff, where printed music writes the tempo: a `ChunkyButton` in `Sun`,
`CircleShape`, `SpeedKnobSize` (54 dp) across with a 5 dp shadow, carrying the animal of the
speed (`tempoBand(bpm).emoji` at 26 sp, `facingRight`), laid by `FluteContent` over `TuneStaff`'s
box at its top left so it touches only the faint clef's tip (the clef's top is `TOP_LINE_Y -
LINE_GAP`, 57 dp, the signature stands clear of it below, and `NOTES_CLIP`, also 57 dp, keeps
every note, stem, ring and band from running under it); nothing on the staff moved for it, and it
shows on an empty tune too. Its bpm is `MetronomeUiState.config.bpm`, hoisted from `MainScreen`
through `FluteScreen` into `FluteContent`, so a change made on the metronome tab shows the
moment the child comes back; a screen reader hears "Speed: Rabbit, tap to change"
(`flute_speed_knob`), never a number. While the tune plays back the animal hops once per click:
`togglePlayback()` starts a child coroutine of the playback job that adds one to
`FluteUiState.beatPulse` at the absolute times `click * Pulse.clickMs` (so the rounding never
adds up; the first lands with the first note, and in 6/8 a click is an eighth), cancelled with
the playback, and `FluteContent` turns the counter into the hop with `rememberBeatFlash(beatPulse,
isPlaying)` and `hopOnBeat` (`ui/common/BeatFlash.kt`, `HopDistance` 8 dp, `FLASH_DURATION_MS`
220, the ring's own hop). The knob never hops for a running metronome, only for the tune's own
playback; the tab bar's drum does that (`MainScreen`). `beatPulse` is not reset when playback
ends, only the hopping stops.

A tap on the knob opens the panel. `FluteContent` keeps `choosingSpeed` in `rememberSaveable`, as
`NotesContent` keeps `choosingNotes`, so rotation and the language switch keep it open, and while
it is true shows, in place of the stage, the keys and the Play row, the metronome tab's own
`MetronomeStage` (`ui/metronome/`: the beat bubbles with − / + over the ring and its in-place
wheel, one composable shared with `MetronomeContent`, so the flute never redraws it) fed the live
metronome state and `MetronomeViewModel`'s `setBpm` / `setTimeSignature` (every change is saved
by the metronome as before and passed on to a running one), and one
`BigActionButton` OK (`speed_done`, `AppIcons.Done`, `ActionGo`) alone where Play was: no keys,
no sponge, no sound (Play on the tune is the preview), and OK the only way back (the back button
is not handled, as in the notes picker). The panel's column is `fitOrScroll` at
`MetronomeMinHeight` rather than `FitsHeight`, which describes the keys, so the ring shrinks on a
short screen as on the metronome tab, and it carries `closesTempoWheel` with its own
`rememberTempoWheelState()`. The knob's `onClick` calls `FluteViewModel.openSpeed()` and then sets
`choosingSpeed`: it stops a tune playing back (`flute.stop`, reason `speed`) and ends a held key
as `leaveTab()` does, and leaves the tone player open. OK clears `choosingSpeed` and calls
`closeSpeed()`, which runs `syncTimeSignature()` (the read `enterTab()` makes: the staff is
rewritten in the time signature chosen in the panel) and logs; a tempo change needs nothing more,
since lengths are kept in beats and the next press and Play read the new pulse. Leaving the flute
tab with the panel open drops it (`MainScreen` composes only the open tab, so `choosingSpeed` goes
with `FluteContent`, as the notes picker's does): the child comes back to the tune, and no
`flute.speed_done` is logged for it.

Log events (tag `FA.Flute`): `tone.track_open` (rate, block and
buffer frames) / `tone.track_closed` (underruns) / `tone.error`, `flute.note` (midi, heldMs,
length, added: false at the cap, per note ended), `flute.rest` (heldMs, length, added, per rest
ended), `flute.rejoin` (midi, `none` for the rest, gapMs: a key let go of and pressed again within
`HOLD_REJOIN_MS` went on with its note or rest, so the `flute.note` before it was not the end of
that note), `flute.breath` (added: false at the cap or right after a breath), `flute.play` (notes,
rests, bpm, time), `flute.play_done`, `flute.stop`
(reason: pill, key, erase, leave or speed, so every `flute.play` has a closing line), `flute.erase`
(left), `flute.speed_open` (bpm, time: the metronome's, when the knob opens the panel) and
`flute.speed_done` (bpm, time, when OK closes it; leaving the tab with the panel open logs none).
