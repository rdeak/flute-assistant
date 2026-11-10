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
snapped (never under 300), converted at the stored tempo; since 2026-11-04 the tune is one of the
songbook's six books (`SONGBOOK.md`), each stored as `"<bpm>;<time signature name>;<that string>"` under
`book_0` to `book_5`, and `SettingsStore.saveBook` keeps the open one across restarts (before that
the single tune lived under the key `tune`, which the migration there moves into the first book).
`Bars.write` (`domain/flute/Bars.kt`, since 2026-11-02) is how the
staff writes the tune in bars of the metronome tab's time signature (`FluteContent` hands `TuneStaff`
the metronome state's own, since 2026-11-10: the flute's state keeps no copy, and a book with a tune
in it sets the metronome's through `openBook()`'s pulse; a bar is `beatsPerBar * 8 / noteValue` half beats of the
flute's quarter-note beat, so 6/8 holds three quarters): `LineMark.Step` / `Breath` / `BarLine`, a
bar line after every full bar (the last too), a note or rest that runs past one split there into
`StepPart`s (the longest lengths that fit, a 2½-beat piece as a half and an eighth) that the staff
ties (a note) or just splits (a rest); the tune itself is never changed.

The stage (the user's canvas of 2026-11-02, boards `Flute*.dc.html`, then changed by the user the
same evening) holds `TuneStaff`, with the speed knob over its top left (described after this
paragraph) and the book button under its clef (`SONGBOOK.md`): the staff and, under each note in the place the canvas gave
the fingering, its name (`NameRow` / `NamePill`, `StaffGeometry.NAMES_TOP`): a pill in its letter's
colour as wide as the note's room on the staff less a 3 dp gap, the name in the first beat's room
under the head, a grey 💤 pill for a rest, no pill for a breath, the bar line drawn through the
names too, a note's pills tied under them. Each step is one `LazyRow` item holding its notes and
its names, so they line up and scroll as one (the user: "align note names with the notes, left to
right"), and the held step's band covers both ("a yellow rectangle covering notes and names at the
same time"). That replaced, the same evening, a 96 dp single-name bubble and then a separate line
of pills (`TuneLine`, deleted); the hold dots and, on this tab, the fingering are gone, and
the state's `shown` field, drawn by nothing since, went on 2026-11-10. A screen reader reads the row ("Your tune, N notes") and
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
touches the command queue once retired (when the child leaves the tab and comes straight back the
successor thread is already serving the keys), and a new `open()` meanwhile starts its own track at
once, so two tracks overlap for the fade rather than the first press after coming back waiting for it.
Turning the phone no longer closes the player: the Activity is not re-created (`configChanges`, see
"Sideways" below), so only leaving the tab, Home and what still re-creates the Activity do (the language change, and
a dark mode, text size or display size change in the phone's settings). `FluteViewModel` (Activity-scoped; specs:
`docs/superpowers/specs/2026-10-23-digital-flute-design.md`, rests and breaths
`docs/superpowers/specs/2026-11-02-flute-rest-and-breath-design.md`) opens the player in `enterTab()`
and closes it in `leaveTab()` (`FluteScreen`'s `LifecycleStartEffect`, so the first press is
never late and nothing plays off screen; a turn of the phone does neither, and Home or another tab
still logs `flute.stop reason=leave` for a tune that was running); `onKeyPressed(midi)` sounds the key at once
and appends it as an eighth (not at the cap, where it only sounds), then grows
it while the key stays down at `Tune.growAtMs` (the step's length in `tune`,
which the staff shows); `onKeyReleased(midi)` silences it and records the length the hold made from
`MonotonicClock` (`Tune.lengthOf`, the same rule, so the note never changes at lift-off), ignored
unless that key is the one held (the newest press wins; sliding off a key releases it); a key the
finger let go of and pressed again within `HOLD_REJOIN_MS` (60 ms), nothing else having happened
since (a new hold, a hold ended by another key, a breath, Play, the speed panel or leaving the tab,
or an erase all forget it), goes on with the same note from its first press, at the length the
whole hold has made and growing from there (`rejoin`, logged as `flute.rejoin`): since
2026-11-03, because a touch screen can report one hold as several pieces (its palm rejection
cancelling a flat finger and starting it over, or a bounce), and each piece became an eighth note
of its own, the user's "bunch of short notes" when holding a key; a deliberate repeat of a note is
far slower than the window, and Android itself takes an up and a down under 40 ms for jitter
(Compose turns a system cancel into a release too, `FluteScreenTest` checks); the rest key goes on
with its rest however long the pause, since 2026-11-04: on the user's phone a long hold of the
rest key still came out as many rests, the pieces further apart than the window, and more of the
rest key is only more of the same silence (two eighth rests are a quarter rest), so a press of it
while its rest is still the last thing done never starts a rest of its own: within the window the
gap counts as held, as for a note, past it the rest goes on from the length it has, the pause not
counted, and grows from there (a whole rest no further: a second rest takes a note or a breath
before it); a note past the window is a repeat, a new note;
`onKeyActivated(midi)` is the screen-reader path (one beat: a quarter note);
`onRestPressed` / `onRestReleased` / `onRestActivated` are the same for the rest key without a
sound (`restHeld`; a rest that takes over a held note silences it, since a note taking over a
note deliberately skips the note off), and `onBreath` ends a held key and appends a breath unless
the last step is one (a breath at the cap or after a breath adds nothing); `togglePlayback()`
plays the tune from the start at the metronome's tempo, each note for its length, lighting it
on the staff, on its name and on its key, silent for each rest's whole length (its symbol and
its pill ringed, the rest key sunk: `restLit`) and straight past each breath, and
stops at once on the pill, on a key press, on `openSpeed()`, on `openShelf()` or `openBook()` or on `eraseLast()`, which drops the
last step (the erase key repeats while held). The erase key (🧽, `EraseKey` in `FluteKeys.kt`) sinks while held and
repeats like a keyboard's backspace; it came to the top row on 2026-11-04, at the user's "move erase
button to right of rest button and extend play / listen buttons" (it was a round 84 dp button at the
end of the Play row), and Play and Your turn share the Play row's width. It is faded and inert while the
tune is empty, and its gesture is attached only while it is enabled, so a hold that empties the tune
stops repeating and lets the key up.

The keys (`FluteKeys.kt`): the fifteen keys in three octave rows, highest on top, each in its
letter's colour, a key sounding for as long as it is held. At the end of the top row, a clear gap
after c³, stand a 💤 rest key, a 😮‍💨 breath key and a 🧽 erase key, icon only (in that order since
2026-11-05, the user's "swap order of rest and breath button", the breath first before; the same day
each became one note key wide, standing over g², a² and h² with the row empty between c³ and them,
so a tool is never bigger than a note, the three read as one group apart from the notes and a slip
off c³ lands on nothing; before that they were two keys wide each and filled the row). The breath
and rest keys were asked for on 2026-11-02 in a brainstorm, not on the canvas.

The speed knob and its panel (the user's pick of 2026-11-03 from three mockups, spec
`docs/superpowers/specs/2026-11-03-flute-speed-knob-design.md`): the tune plays at the metronome
tab's tempo and in its time signature, and the flute now shows and sets both. `SpeedKnob` is the
ring's knob moved to the staff, where printed music writes the tempo: a `ChunkyButton` in `Sun`,
`CircleShape`, `SpeedKnobSize` (54 dp) across with a 5 dp shadow, laid by `FluteContent` over `TuneStaff`'s
box at its top left so it touches only the faint clef's tip (the clef's top is `TOP_LINE_Y -
LINE_GAP`, 57 dp, the signature stands clear of it below, and `NOTES_CLIP`, also 57 dp, keeps
every note, stem, ring and band from running under it); nothing on the staff moved for it, and it
shows on an empty tune too. Since the evening of 2026-11-04 it carries two pictures: the tab bar's drum
(`AppTab.METRONOME.emoji`, `StaffGeometry.KNOB_DRUM`, 26 dp, `KNOB_DRUM_SHIFT` up and left of the
middle), which says what it opens, and in `AnimalBadge` at its lower right the animal of the speed
(`tempoBand(bpm).emoji`, `KNOB_BADGE_ANIMAL`, 19 dp, `facingRight`), which says how fast. Before that it
carried the animal alone at 26 sp, and with the flute now the first tab a child met that animal before
ever seeing the metronome. The user asked for a drum; a drum alone would hide the speed while the child
writes, though a held key is counted at it and each book has its own, so true-size mockups
(`.superpowers/brainstorm/*/content/knob-drum.html`, `knob-drum-v2.html`) went from the animal with a drum
badge, which the user liked with the two swapped, to this: "E" of the second page, because on the first
swap's white 24 dp badge the near-white rabbit disappeared, so the badge is `Sun` and `KNOB_BADGE` (29 dp)
across with a `KNOB_BADGE_EDGE` outline. Both emoji are sized in dp (`emojiSizeFor`), so a large font
scale cannot push them out. The badge is the button's sibling, not its content, because `ChunkyButton`
clips to its disc and the badge reaches `KNOB_BADGE_OVERHANG` (3 dp) past the box, to `NOTES_CLIP` and
the clef's tip and no further (`StaffGeometryTest`); it has no gesture, so a tap on it goes through to
the knob, and it sinks with the knob through a shared `MutableInteractionSource` and `PressDepth`. The
emoji have no semantics, so `FluteScreenTest` checks them through the knob's pixels. Its bpm is `MetronomeUiState.config.bpm`, hoisted from `MainScreen`
through `FluteScreen` into `FluteContent`, so a change made on the metronome tab shows the
moment the child comes back; a screen reader hears "Speed: Rabbit, tap to change"
(`flute_speed_knob`), never a number. While the tune plays back the walking animal (below) hops once per click:
the playback loop itself adds one to `FluteUiState.beatPulse` on every click that falls inside the
step playing (the half beats `h` into it where `(start + h) % Pulse.halfBeatsPerClick == 0`: 2 in
x/4, 1 in x/8, 4 in x/2), timed from that step's own start, so a hop always lands with its note
(the first with the first note; in 6/8 a click is an eighth) and never drifts from the sound
(since the evening of 2026-11-04; before that a separate coroutine counted clicks on its own clock
and nothing said which beat of a long note a click fell on). Each click also sets
`FluteUiState.playingHalfBeat` (0 at every step's start), the beat the walking animal stands over
(below). An eighth starting off the click moves the animal on without a hop. A note's end, the
gap before the next step, is in the same timeline and always after its last click. `FluteContent`
turns the counter into the hop with `rememberBeatFlash(beatPulse, isPlaying)` and `hopOnBeat`
(`ui/common/BeatFlash.kt`, `HopDistance` 8 dp, `FLASH_DURATION_MS` 220, the ring's own hop). The
knob itself never hops: the walking animal does for the tune's own playback and the tab bar's drum for a
running metronome (`MainScreen`). `beatPulse` is not reset when playback ends, only the hopping stops.

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
`closeSpeed()`, which logs (the staff reads the metronome's time signature, so it is already
rewritten in the one chosen in the panel); a tempo change needs nothing more,
since lengths are kept in beats and the next press and Play read the new pulse. Leaving the flute
tab with the panel open drops it (`MainScreen` composes only the open tab, so `choosingSpeed` goes
with `FluteContent`, as the notes picker's does): the child comes back to the tune, and no
`flute.speed_done` is logged for it.

"Your turn" (since 2026-11-04, spec `docs/superpowers/specs/2026-11-04-flute-your-turn-design.md`,
the first right-or-wrong outside the notes game, kept the child's by being opt-in, child-paced and
score-free): the second big button beside Play (`flute_your_turn`, labelled "Listen to me" / "Poslušaj me" and
Play "Odsviraj" in Croatian since 2026-11-04: the child speaking to the phone, not "Sviraj" / "Ti
sviraj", the app telling the child; not "Prati me", which in music also means to accompany; a drawn `AppIcons.Microphone`
on `ActionListen` blue; the two buttons stack their icon over their word, `BigActionButton(stacked
= true)`, because two icon-beside-word pills do not fit a phone's width; faded while the tune
holds no note, `FluteUiState.canListen`). A tap (`FluteScreen` asks for RECORD_AUDIO first if
need be; a no shows `MicrophonePermissionCard` in place of everything, with its quiet "Not now"
(`mic_not_now`) the way back, `FluteUiState.micDenied`) calls `FluteViewModel.startListening()`:
it opens the tuner's `TunerEngine`, builds a `TuneCheck` (`domain/flute/TuneCheck.kt`) over the
tune and waits on its first `TuneNote` (`listenIndex`; rests and breaths are passed over). Frames
go through `NoteOnsetTracker` (`ui/tuner/`, the tuner's own rules plus "a new note began", an
`Onset` with its MIDI and whether it was `fresh`: after a gap, or the same note started again
after a tongued stop, as against a step to another note without a gap, a slur, a fingered
change or an octave pop; a slow sag past the halfway point moves the sounding note but is no new
note; the same note merely going on is not new). The rules were rewritten on the night of
2026-11-04 after the first child test with a repeated-note tune ("h1 h1 long h1": the tongued
second h1 was never heard, the long h1 was counted as the second, and the run sat waiting for a
third the child had already played; the child read it as "it does not hear my long note"), and
`ListenPathTest` (synthetic flute sound in a synthetic room through the real detector, tracker and
check, since the emulator has no microphone) now pins every case below: a note ends by a gap of
`ONSET_GAP_FRAMES` = 2 uncounted frames that also fell under half its loudness (`GAP_LEVEL_RATIO`;
the gate blinking on a breathy tone keeps the loudness and is not a gap) *or* back to the room's
own level (the tracker's `floor`, the quietest uncounted frame heard between notes, within
`FLOOR_MARGIN`, rising `FLOOR_RISE` a frame: for a note at -36 dBFS over a -46 dBFS room half the
level lies under the floor, so by the ratio alone such a note never ended and its repeat never
counted, even after two seconds), or by `HOLD_FRAMES` uncounted frames at any level (a pause, not
a blink); and a tongued repeat, 40 to 80 ms of stop that the window's level barely shows and the
pitch gate often misses (the smallest gap the two-frame rule could see was about 160 ms in a dry
room, 250 ms and more with the room ringing), is heard in `PitchFrame.minLevel`, the quietest 21 ms
stretch of the newest hop (`FrameLevels`): a stretch `STOP_DROP` (7 dB) under the note's last
`RECENT_FRAMES` levels, not in the note's first `ATTACK_FRAMES`, is a stop, and the tone back
`RESTART_RISE` (6 dB) over the stop's floor within `RESTART_FRAMES` is the note started again, a
fresh onset of whatever pitch locks. Each new note is judged by
`TuneCheck.judge(heard, fresh)`: **right** is the same letter in any octave (beginners overblow, and the detector
speaks the lower octave at an onset), earning `StepMark.FIRST_TRY` or `RETRY` after a stumble and
moving on; **ignored** is the letter of the step just judged, in any octave, unless that letter is
also the one waited on (a written repeat) *and* the onset was fresh: a repeat can only be played by
stopping and starting again, so an octave pop (a step) never counts it, however long after
(`REPEAT_GUARD_MS` is gone: it also ignored a real repeat that came within 300 ms, and an ignored
sustained note was never judged again); **wrong** is anything else, with one forgiveness
(`TuneCheck.forgiven`, logged `flute.repeat_forgiven`): when the step waited on repeats the note just
judged and the heard note is the one written after it (or after a run of such repeats), the repeat
was slurred on to or skipped, which the phone cannot tell apart, so it gets `RETRY` and the heard
note is judged for the step after, rather than the run dead-ending on a note the phone may not be
able to hear. A wrong note sets `FluteUiState.wrong` (a
`WrongNote`: the step and the MIDI heard), the note heard as a round bubble in its own letter
colour in the marks row under the step until the next note is judged (a right note clears it, another wrong
note replaces it, an ignored one leaves it; no timer, so the child has time to read "written G,
heard A" off the pill and the bubble; since the evening of 2026-11-04, in place of a ❌ for 700 ms,
which said nothing about what came out; the user had asked for note names in the stars' place,
green when right, and agreed to this instead: the right name already stands in the pill above,
green is F's colour in the letter code, and colour alone must not carry right and wrong), and the
step is remembered as stumbled. Since the night of 2026-11-04 the same bubble is also live: the
note the phone hears right now (`FluteUiState.heardNow`, the tracker's sounding note; null in
silence, and null while the note just counted goes on and something else is waited on, since that
tail is not what the next step hears, but shown when the next step is its written repeat, just
when the child must see it) stands under the step waited on, and in silence the grey listening
dots (`ListeningBubble`, the tuner's "…") do, so the child always sees the phone listening and
what it makes of their sound; the sticky wrong note shows only when nothing sounds. The marks row
of the waited step is live bubble, else wrong note, else dots; a screen reader hears none of the
live part. When the step waited on is a written repeat and the note just counted goes on for
`REPEAT_ADVICE_FRAMES` (about a second) with nothing new (`FluteUiState.repeatHeld`), the advice
line says "Stop, then play it again" (`advice_play_again`, `TunerAdvice.PLAY_AGAIN`, the flute's
only advice of its own, over the hearing ones), the one case the phone cannot hear turned into a
tonguing lesson; it goes when the sound stops or a note begins. The check waits for the right
note, which is what keeps every mark under its own note. Pitch only; nothing is timed. The last note
judged logs `flute.listen_done` at once but does not end the run at once: `FluteUiState.finishing`
keeps the microphone open and the animal over the last note with its star (`litIndex` and
`waiting` drop it, so nothing is ringed or "play this one") until that note's sound ends or
`FINISH_FRAMES` (about 1.5 s) pass, so the keys do not snap back while the child is still blowing
the last note; Stop (`stopRun`, one wide red pill where the Play row was), a key, erase,
the speed knob, the book button and leaving the tab end it early (`flute.listen_stop`); the marks stay in
`FluteUiState.marks` until a new run, a switch to another book or any write to the tune (`save()` clears them), are not
saved, and show no number anywhere. `HearingTracker` feeds `FluteUiState.hearing` for the
tuner's `AdviceLine` under the stage ("Come closer" / "Play one long, clear note", only while the
sound stays unclear). Play is refused while listening; the keys are off screen anyway. The engine's
frames are buffered, never conflated (`ui/tuner/CLAUDE.md`): the tracker counts frames, and the
main thread is busiest right after a judged note, when the staff scrolls and the animal hops, which
is just when the next note's gap frames arrive.

While the tune runs, in Play as in Your turn (`FluteUiState.isRunning`), the keys and the sponge
go away and `TuneStaff` is drawn at `RunningStaffScale` (4/3 upright: lines 24 dp apart, the canvas's
original size, for a phone on a music stand at arm's length; sideways see "Sideways" below; the geometry stays one table in dp
and a private `LocalStaffScale` multiplies every size and every `scale(density * s)`), with the
advice line in the keys' room and the wide Stop at the bottom; the knob stays in the
corner, its drum faded and inert and its badge gone (`SpeedKnob(running = true)`), because its animal is on the staff, walking
it at the knob's height (`WalkingAnimal` in `TuneStaff.kt`; since the evening of 2026-11-04, the
user's "move metronome animal to top so it moves on top line where it stands during notes entering
phase; when a note has longer duration the animal should not jump more than once in one place", and
their pick "A" of three moving true-size mockups, `.superpowers/brainstorm/*/content/animal-walk.html`:
B glided steadily with the music, C also let the staff stand still and turn a page at the right
edge; before, it bounced in place in the marks row under the names). `StaffAnimal` carries
`FluteUiState.animalIndex`, `animalHalfBeat` (the last click's `playingHalfBeat` in Play, 0 in Your
turn), the speed's emoji, the one `rememberBeatFlash` of the tab and `waiting` in Your turn. The
animal stands over its step's head when the step begins and, on each click inside it, over that
beat's place (`StaffGeometry.beatX`: the head plus half a beat's room, 14.5 dp, per half beat,
past a bar line inside a tied note), like the metronome's beat bubbles: a half note is two places
29 dp apart in 4/4, a whole note four, a quarter in 6/8 two places 14.5 apart, an eighth only its
head; it slides to each new place in `FLASH_DURATION_MS` with `FastOutSlowInEasing` while it hops,
so it lands as the hop ends, across bar lines and breaths too, and in Your turn from the note just
played to the next, which it reaches with a hop (`beatPulse` goes up on every advance and at the
start). It is one layer over the `LazyRow`, the size of the knob (`CORNER_BUTTON`), its middle
`StaffGeometry.ANIMAL_Y` (27 dp, the knob's middle, unscaled like the knob) from the box's top,
`ANIMAL_SIZE` (24 dp) times the scale tall, placed in `Modifier.offset {}` from the row's
`layoutInfo` (the first item shown, plus the dp from its start, which `StaffGeometry.markWidth`
sums), so it follows the row's scrolling with no recomposition, and clipped at the row's sides
only, so a staff the child drags back hides it rather than sliding it over the knob. Over the
stems of the notes from f² up it stands on the stems' tops, and over c³ it touches the top edge of
the head (`StaffGeometryTest` checks every other head stays under it). The staff still scrolls on
at every new note (`Bars.anchor`, below), so in a long tune the staff moves under the animal at
each note and the animal steps on along a long note. Under the names a marks row
(`StaffGeometry.MARKS_TOP`, 26 dp, in the first beat's room so it stands under the head and the
name; the staff grew 16 dp for it) holds, per step, the ⭐ it earned (`STAR_EMOJI`, faint at
`RETRY_STAR_ALPHA` after a retry, both the notes game's) or the heard note's bubble (`HeardBubble`:
a round bubble as tall as the row, `noteColor` fill, the ink outline, the name in `noteText` at up
to 14 sp times the scale, the tuner's way of showing another note). Emoji on the staff are sized in
dp (`emojiSizeFor`) like the staff's other text. A screen
reader hears a step's mark after its length ("G4, 1 beat, got it" / "got it after a retry" /
"play this one" / "heard A4"; `flute_mark_*`, `flute_mark_heard` taking the heard note's
`noteLabel`, `flute_listen_here`). The staff scrolls (`animateScrollToItem` in `TuneMarks`, keyed
on the marks so a growing note stays in view) to `Bars.anchor` (`domain/flute/Bars.kt`): since
2026-11-04 the last note before the lit step, so the note the child just played stays on the
staff with its star or heard bubble and the lit step is the second thing in view (the user: "last
played tone (success or fail) should be visible, that makes current tone as 2nd item"); before
that the lit step itself stood at the left edge and the mark just earned scrolled straight out.
Rests, breaths and bar lines between the two stay in view with them (a rest between them would
otherwise hide the star again), the lit step itself is the anchor when no note comes before it,
and the end of the line when nothing is lit; the same rule holds in Play and under a held key,
where the list's end clamps the scroll as before. `FluteScreenTest` checks a mid-tune lit note
with the note before it wholly on screen and the one before that not.

The songbook (since 2026-11-04: six tunes, each with its own speed and beat, and the book button and
shelf that switch between them) is in `SONGBOOK.md` in this folder; read it before changing books,
`Shelf.kt`, `FluteViewModel.openBook` or their storage. Three rules hold everywhere: every change to
the tune goes through `save()`, which writes the open book, and `FluteViewModel` and
`MetronomeViewModel` never reference each other, so `openBook` saves the book being left before
`FluteScreen` calls the metronome's setters. The migration of the old single tune is decided by the
`open_book` key being missing, never by the books being empty: a child who wipes all six must not
get the old tune back.

Log events (tag `FA.Flute`): `tone.track_open` (rate, block and
buffer frames) / `tone.track_closed` (underruns) / `tone.error`, `flute.note` (midi, heldMs,
length, added: false at the cap, per note ended), `flute.rest` (heldMs, length, added, per rest
ended), `flute.rejoin` (midi, `none` for the rest, gapMs: a key let go of and pressed again within
`HOLD_REJOIN_MS`, or the rest key at any time, went on with its note or rest, so the `flute.note`
or `flute.rest` before it was not the end of that step), `flute.breath` (added: false at the cap or right after a breath), `flute.play` (notes,
rests, bpm, time), `flute.play_done`, `flute.stop`
(reason: pill, key, erase, leave, speed or book, so every `flute.play` has a closing line), `flute.erase`
(left), `flute.speed_open` (bpm, time: the metronome's, when the knob opens the panel) and
`flute.speed_done` (bpm, time, when OK closes it; leaving the tab with the panel open logs none);
for the songbook, `flute.shelf_open` (book: the index of the open one, when the button opens the
shelf), `flute.book_open` (from, to, notes, bpm, time: `to` is the book opened and `notes` its steps,
rests and breaths counted, `bpm` and `time` what the metronome was set to, `none` when an empty
book left it alone) and `flute.shelf_close` (book, when the open book was picked again and nothing
changed), so every `flute.shelf_open` has a closing line but for a tab left with the shelf open;
for Your turn, `flute.listen` (notes: how many there are to check), `flute.heard` (index, expected,
heard, fresh: whether the note began after a gap or a stop rather than by a step, verdict: right,
retry, ignored or wrong, per new note the tracker reports), `flute.repeat_forgiven` (index: a
written repeat of the note just judged passed over with a faint star because the note after it
was heard, once per such step),
`flute.listen_summary` every `TunerViewModel.SUMMARY_FRAMES` frames and once more at the end (the
tuner's summary fields plus gate, expected and hearing: the line that says whether a child's tone
passed the gate at all), `flute.hearing` (hearing, levelMean, when it changes), `flute.listen_done`
(stars, retries, when the last note is judged), `flute.listen_stop` (reason: pill, key, erase,
leave, speed or book, so every `flute.listen` has a closing line), `flute.listen_error` (message, when
the microphone fails: listening ends cleanly) and `flute.mic` (granted, the phone's answer about the
microphone on this tab). The engine's `mic.open` / `mic.close` stay under `FA.Tuner`. A debug
build adds `session.open` (path: where this run's session folder is) and `session.error`
(message: a write failed, the session was dropped, the run went on), from the recorder (the `sessions` skill),
and inside the session log alone `session.reopened` (frame, rate, source: the microphone opened
again inside one run, which nothing in the app does today; the audio keeps the first opening's
rate so the rows still line up with it).

## Sideways

Since 2026-11-05 (spec `docs/superpowers/specs/2026-11-05-landscape-design.md`, "Flute" and "Turning the
phone"; what every tab shares sideways, and why the tab has one call site in `MainScreen`, is in
`../CLAUDE.md`). `FluteContent` builds the same pieces upright shows (the tune, the keys, the Play row)
as local lambdas and `FluteSideways.kt` only says where they stand. `choosingSpeed`, `choosingBook` and the
speed panel's `TempoWheelState` live above the branch, so an open panel or shelf survives a turn.

- **Writing** (`FluteWritingSideways`; mockup `fit-v2.html`, the user's pick: the same keys as upright):
  the rail, the staff's card, then the keys with Play and Listen to me under them. There is no right
  column, so no `SidewaysPage`: beside the keys it left the staff about three beats. The three key rows
  keep their upright order and contents (the rest, breath and sponge keys on top). Keys are 48 wide and
  `SidewaysGeometry.keyHeight` tall (85 at 411, 68 at 360, never under upright's 58) with gaps of 6 and
  12 (`FluteKeys(keyHeight, keyGap)`); under 630 dp of content they are 40 wide with 4 dp gaps, which is
  what upright gives on a phone that narrow. Play (left) and Listen to me share the keys' width, 84
  tall, in upright's order. The card takes the width left: 427 dp at 915, 11 beats at rest scale
  (upright shows 9), 8 beats at 800, 5 at 640, with the staff centred in its height; the knob and the book
  keep their corners. Under `WRITING_MIN_HEIGHT` (329 dp, shorter than any phone it was drawn for) the keys'
  column scrolls like upright's tab instead of overflowing onto Play. The empty staff's hint (`EmptyHint`)
  may take two lines sideways: at 640 it is about 92 dp wide and one line was cut.
- **Running, Play or Your turn** (`FluteRunningSideways`; the worst case, `frame.html` option A): the keys
  and the two buttons go as upright, the rail stays, and the card widens to leave the right column (683 dp
  at 915: 15 beats, 14 at 800, 9 at 640). The staff is drawn at `SidewaysGeometry.runningStaffScale`: 4/3 where
  the height allows it (343 dp plus the card's 38), below that the largest that fits (about 1.19 at 360 tall,
  lines 21 dp apart, still bigger than upright at rest). The advice line is at the top of the column, wrapping
  (`AdviceLine(wrap = true)`, 19 sp, up to four lines, shrinking before it would cut or split a word), and Stop
  is low in it, in Listen to me's corner, under the thumb that started Your turn. The walking animal, the faded
  knob and book and the heard-note bubble stay as they are.
- **Speed panel** (`FluteSpeedSideways`): the metronome's `MetronomeStageSideways` with OK in Play's place.
- **The shelf**: one row of six books, `SONGBOOK.md`.
- **The microphone card** is the shared one (`ui/tuner/CLAUDE.md`); Your turn adds "Not now" to its column.
- **Nothing stops when the phone turns.** Before this, a turn re-created the Activity and `FluteScreen`'s
  `LifecycleStartEffect` called `leaveTab()`: pressing Play and then setting the phone on the stand logged
  `flute.stop reason=leave`, which would lose the run of a child on a music stand. `MainActivity` now handles
  orientation and size changes itself, so a tune and Your turn (the microphone keeps listening, the marks stay) run on
  across a turn while the layout changes under them. The language change still
  re-creates the Activity, and Home still stops the flute.
- **A key held through a turn must stop its note.** When the layout changes (upright to sideways and
  back) the keys move, so the finger's gesture is cancelled with a `CancellationException` and nothing else
  would end the note. `FluteKey` and `RestKey` therefore call the release in a `finally` (logged once as
  `flute.note`, at the turn, with the finger still down; nothing more when it lifts). A half turn between
  the two sideways directions keeps the keys composed, so by the code only a move off the key or the lift should end
  the note there. On the emulator the injected press stays on the same key (the two layouts are identical), so what a real
  finger does, after the glass has turned under it, has not been seen. No automated test can drive this exactly:
  the Compose test dispatcher handles a cancel at once, so the release runs by the ordinary road with or
  without the `finally`. `FluteSidewaysTest` cancels the gesture by a density change instead (that fails without
  the `finally`), and the real rotation with a finger down is checked by hand (`adb shell input swipe X Y X Y
  4000` in the background, then turn).
- **The staff keeps its place.** `staffPlace`, the `LazyListState` of the staff's marks, is created in
  `FluteContent`'s writing and running branch and passed to `TuneStaff`, so a turn, Play and Stop do not
  restart the scroll from the start of the line. It is not created higher, so opening the speed panel or the
  shelf still gives a new book's tune a fresh state.

## Tablets

The one tab that adjusts anything on top of a tablet's zoom (`ui/CLAUDE.md`, "Tablets"; spec
`2026-11-06-tablet-design.md`), and only while `LocalTabZoom` is over 1. Upright the keys take a third of
what the height spares over `FitsHeight`, from 58 up to 72 (`TabletGeometry.uprightKeyHeight`), about 104 dp
on a 10-inch tablet: the phone's 58 left a band of nothing either side of them. Sideways, writing, the keys'
column is 336 rather than 372 and the resting staff 0.91 rather than 1 (`TabletGeometry.sidewaysKeys`,
`sidewaysStaffScale`), so that about 13 quarter notes show with their bar lines, against about 10; the staff
is still 1.3 times a phone's at 10 inches and 1.02 at 8, and a barely zoomed window keeps a phone's real sizes. The running staff keeps `runningStaffScale`. A tune
longer than that scrolls as it does on a phone. `FluteTabletTest` holds these.

## Sessions (debug builds only)

A debug build records every "Your turn" run on the phone: the audio, a per-frame table and the run's
log lines. How that works and how to pull, replay, sweep and pin a session is in the `sessions`
skill (`.claude/skills/sessions/SKILL.md`). The audio is a child's playing: it leaves the phone only
over the cable and never goes into git (the repository is public).
