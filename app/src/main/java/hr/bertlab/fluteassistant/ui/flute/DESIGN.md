# Digital flute: design and history

The long text: how the flute tab works, why, and who decided what. It was this folder's CLAUDE.md
until 2026-11-10 (and part of the root one until 2026-11-02); `CLAUDE.md` beside it now holds only
the rules that must not be broken and says which part of this file to read before changing what.
It is not loaded by itself: open it when you change the flute.

Read with these names in mind (the refactor of 2026-11-10 moved the rules into `domain/flute` and
grouped the state; the behaviour described below did not change):

- The writing rules (a key held makes a note, the rejoin window `HOLD_REJOIN_MS`, the cap, breath and
  erase) are `TuneWriter`; `FluteViewModel`'s `onKeyPressed` and the rest call it and add the sound,
  the growing over time, the saving and the log.
- "Your turn" is `ListenRun` (with `REPEAT_ADVICE_FRAMES` and `FINISH_FRAMES`), fed by
  `FluteViewModel`; the playback's timeline is `Tune.playSteps`.
- `FluteUiState` has one part per way the tab can be. Where this text says `heldMidi`, `restHeld` or
  `heldIndex`, that is `held` (a `Held`); `playingIndex` and `playingHalfBeat` are `playing` (a
  `Playing`); `isListening`, `listenIndex`, `wrong`, `hearing`, `heardNow`, `repeatHeld` and
  `finishing` are `listening` (a `Listening`, null when not listening). `litIndex`, `litMidi`,
  `restLit`, `animalIndex`, `waiting`, `advice` and the like are still derived on `FluteUiState`.
- `FluteContent` takes the state, the metronome's state and one `FluteActions` object.
- The metronome's tempo and beat have one owner, `MetronomeSettings` (`domain/settings`). Where
  this text says the flute reads them from `SettingsStore.loadMetronomeConfig`, it reads the
  owner's `config`; where it says `FluteScreen` calls the metronome's `setBpm` and
  `setTimeSignature` (the speed panel, a book switch), `FluteViewModel` updates the owner
  (`setBpm`, `setTimeSignature`, `openBook`) and `MetronomeViewModel` follows it, a running
  metronome included. `FluteScreen` no longer holds the metronome's ViewModel and `openBook`
  returns nothing.

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
(`"60:2,r:2,b,67:4,69:2:s"`: a note's MIDI or `r` for a rest, then half beats, and `b` for a breath; a third field `s`
on a note joined to the one before since 2026-11-16, "Legato";
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
every height; with the names the stage fits a 412 x 915 phone without scrolling. The tab needs 642 dp
upright (`FitsHeight` in `FluteScreen.kt`: the stage, three key rows, the Play row, their gaps and the
column's padding), and whatever a phone has over that goes over and under the keys, so that Stop
stands where Play was; under it the keys keep their least height and the tab scrolls. Until 2026-11-10
`FitsHeight` counted 664, so the user's Galaxy A55, whose three navigation buttons leave the tab 653 dp
(the emulator's Medium Phone leaves 709), was laid out as a phone too short: the blocks at their least
gaps, the spare height under the Play row and Stop 100 dp over Play's place. Where the
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
whole hold has made and growing from there (`TuneWriter.press`, logged as `flute.rejoin`): since
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
box at its top left, `KNOB_LIFT` (8 dp) above the box's top, in the stage's padding
(`StaffGeometry.knobTop`), clear of the faint clef (the clef's tip is `TOP_LINE_Y - LINE_GAP`, 57 dp,
the signature stands lower still, and `NOTES_CLIP`, also 57 dp, keeps every note, stem, ring and band
from running under the knob); nothing on the staff moved for it, and it shows on an empty tune too.
The lift is of 2026-11-10: until then the knob's top was the box's, the rule was that the badge may
touch the clef's tip, and the knob's own shadow, which nobody had counted, lay 2.8 dp over the tip,
which the user saw on their phone ("G clef overlaps with metronome button"). Now the shadow and the
badge end 5 dp short of the clef's line at every size the staff is drawn
(`StaffGeometry.knobClefAir`, `StaffGeometryTest`): where the staff is drawn smaller than at rest (a
tablet on its side, 0.91) the clef's tip comes up and the knob, which keeps its size, goes up as far,
the staff being centred in a taller card there. Since the evening of 2026-11-04 it carries two pictures: the tab bar's drum
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
clips to its disc and the badge reaches `KNOB_BADGE_OVERHANG` (3 dp) past the knob's box, to `NOTES_CLIP`
and no further (`StaffGeometryTest`); it has no gesture, so a tap on it goes through to
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
no sponge, no sound (Play on the tune is the preview), and OK the way back (the phone's Back does the
same since 2026-11-10, closing an open wheel first: `ui/DESIGN.md`, "The phone's Back"). The panel's column is `fitOrScroll` at
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
score-free; child-paced it still is in when a note starts, but since 2026-11-11 a note must also last its
length, "Each note held for its length" below): the second big button beside Play (`flute_your_turn`, labelled "Listen to me" / "Poslušaj me" and
Play "Odsviraj" in Croatian since 2026-11-04: the child speaking to the phone, not "Sviraj" / "Ti
sviraj", the app telling the child; not "Prati me", which in music also means to accompany; a drawn `AppIcons.Microphone`
on `ActionListen` blue; the two buttons stack their icon over their word, `BigActionButton(stacked
= true)`, because two icon-beside-word pills do not fit a phone's width; faded while the tune
holds no note, `FluteUiState.canListen`). A tap (`FluteScreen` asks for RECORD_AUDIO first if
need be; a no shows `MicrophonePermissionCard` in place of everything, with its quiet "Not now"
(`mic_not_now`) the way back, `FluteUiState.micDenied`, and "Settings" on it only once the phone will not ask
again, `FluteUiState.micBlocked`, which `onMicResult` is told by `FluteScreen`: `ui/tuner/CLAUDE.md`, "Asking for the microphone") calls `FluteViewModel.startListening()`:
it opens the tuner's `TunerEngine`, builds a `TuneCheck` (`domain/flute/TuneCheck.kt`) over the
tune and waits on its first `TuneNote` (`listenIndex`; rests and breaths are passed over). Frames
go through `NoteOnsetTracker` (`domain/listen/`, the tuner's own rules plus "a new note began", an
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
note, which is what keeps every mark under its own note. All of this is the pitch, judged at a note's
onset; a right one is then walked and earned once long enough (the next paragraph). The last note
earned logs `flute.listen_done` at once but does not end the run at once: `FluteUiState.finishing`
keeps the microphone open and the animal over the last note with its star (`litIndex` and
`waiting` drop it, so nothing is ringed or "play this one") until that note's sound ends or
`FINISH_FRAMES` (about 1.5 s) pass, so the keys do not snap back while the child is still blowing
the last note (a rest after the last note is walked instead, ringed, and its end ends the run, sound or not, since
2026-11-13); Stop (`stopRun`, one wide red pill where the Play row was), a key, erase,
the speed knob, the book button and leaving the tab end it early (`flute.listen_stop`); the marks stay in
`FluteUiState.marks` until a new run, a switch to another book or any write to the tune (`save()` clears them), are not
saved, and show no number anywhere. `HearingTracker` feeds `FluteUiState.hearing` for the
tuner's `AdviceLine` under the stage ("Come closer" / "Play one long, clear note", only while the
sound stays unclear). Play is refused while listening; the keys are off screen anyway. The engine's
frames are buffered, never conflated (`ui/tuner/CLAUDE.md`): the tracker counts frames, and the
main thread is busiest right after a judged note, when the staff scrolls and the animal hops, which
is just when the next note's gap frames arrive.

Each note held for its length (since 2026-11-11, spec
`docs/superpowers/specs/2026-11-11-flute-your-turn-note-length-design.md`; the user, after watching a run: "app just
check if the tone is correct but does not check is it long enough played, on listen turtle should follow candence of
metronom, in case player stop playing stop the turtle"). Until then a half note played as a tap earned a full star, and
the recordings of 2026-11-05 showed it was the rule, not a slip: every half note was played for 0.8 to 1.0 s of its
2 s, the whole tune 1.6 to 1.8 times faster than the knob's 60. Now the child decides when each note starts and the
metronome how long it lasts: `ListenRun` takes the `Pulse` the knob had when the run started (the knob ends a run) and
walks each right note (`Walk`, on `Listening.walk`): from the moment its sound began, inside the first frame that
heard it, whose hop counts in full, with the frames that locked it before the onset (`NoteOnsetTracker.Result.leadFrames`:
the frames in a row before the onset that sounded within `NEAR_CENTS` of the pitch that locked, two as a rule, after
silence and after another note alike; none for a tongued restart of the same note; an attack that scoops into the note
is the note's, as in the session `17-04-38`, whose g begins 55 cents sharp, and a wobble of other notes or a slide
before it is not, which the second review of 2026-11-11 found counted: eleven frames of other notes had earned a
quarter on its onset frame; nor is a frame the tracker showed as the note before going on, however near, as a b held
35 cents sharp and slurred to c lies inside `NEAR_CENTS` of it and gave the c every one of its frames, the review of
2026-11-14), the animal steps to each click inside the note at the half beats Play puts them on
(`Tune.playSteps`), with a hop, and only to another place: a note beginning where the animal already stands is no hop
(the user's rule of 2026-11-04 for Play, never two hops in one place; the first build hopped at the onset too, on
the spot, 128 ms after landing there). What counts as the note sounding is every frame the tracker shows it and at
most two more after the last such (`ListenRun.Rules.graceFrames`, the frames a gap takes to be seen and a rule of its own
since 2026-11-13, so a sweep of the tracker's `onsetGapFrames` no longer moves note lengths; so a note's thinning end still
counts, as the recorded quarters need); the tracker holds a note on through loud frames with no pitch for most of a
second (`HOLD_FRAMES`: a pause is not a blink), and that is not the note's time (the same review: air noise after
128 ms of tone had earned a quarter), while a breathy stretch inside a note counts in full once the tone is back. The
note is earned once it has sounded its written length less
`ListenRun.LENGTH_SLACK` (three quarters of a beat since 2026-11-13, half a beat before: a quarter needs a quarter of a
beat, a half note a beat and a quarter, a whole note three and a quarter; an eighth needs nothing past being heard), and never more than `ListenRun.MAX_NEED_MS`, 3 s: a whole note
at 40 a minute would need 5.25 s in 4/4 and 10.5 s in 6/8, beyond a child's breath, and the run would wait on it until
Stop (the final review found it; the user's "ok" to a recommended 3 s; swept as `maxNeedMs`). The animal still walks
such a note's written time, and the next note takes it there at once. Time is the frames' own lengths added up (`PitchFrame.hopMs`,
42.7 ms at 48 kHz and 46.4 at 44.1), never a clock, so the replay of a session hears it the same. The slack is the
user's "ok" to a recommendation, checked on the three children's runs of 2026-11-05: replayed, the two that reached a
half note earn every quarter before it and stop on it (played about 1 s of its 1.5 needed), and a slack of a whole
beat earns the same; the final review's two measuring fixes (the lead after another note, the first frame in full)
are what made the quarters pass, each a frame or two from the line. Those children followed no beat, so the number is
to be swept again (`-Psweep=lengthSlack:...`) on the first sessions recorded with the walk, and changed only from them.
That sweep moved it (below, "The first runs with the walk").
A note stops (silence, or an onset of another letter; an octave pop by a step, a slow sag into the next letter, the
tracker's drift, and since 2026-11-13 its own letter started again with no gap, `ListenRun.Rules.restartCuts`, are the
same note going on, so a child blowing a long note out is never told it stopped) before it is long enough: it is cut short (`Walk.Phase.SHORT`, `flute.short`), the
animal stays on the click it reached, so the child sees how far the note got, the line says "🌬️ Hold it until the ⭐" /
"🌬️ Drži notu do ⭐" (`advice_hold_longer`, `TunerAdvice.HOLD_LONGER`, under the microphone's advices and a held
repeat, over "Check your fingers"; no fingering, the fingers were right; "drži notu" is the keys' own hint; the star
is what shows the moment the note is long enough, so the line says how long, since 2026-11-13: it said "Hold it
longer" before), or, when the note faded away before it stopped, "😮‍💨 Maybe out of air? Big breath first" / "😮‍💨 Možda
nema zraka? Udahni" (`TunerAdvice.OUT_OF_AIR`, `Listening.faded`: its last frames before the gap had
fallen under `ListenRun.FADE_RATIO`, 85%, of its own level; fewer than eight frames walked is no guess, nor is a note that went on into another one, still blown; a guess, so
"maybe"; the Croatian is "Udahni" alone, to fit one line upright on a 360 dp phone at 1.3 times the text size: "Your turn: say why", spec `2026-11-13-flute-your-turn-why-lines-design.md`, the user's ask after the child
"played it right" and was held back; the recordings of 2026-11-12 and 13 had 42 cuts stopped while strong and 3
faded), and the
run waits on that note until it is played again, from its head, and long enough, when it earns the faint star (until
2026-11-14, `ListenRun.Rules.shortMovesOn` off; since then the note is marked ⏳ and the run moves on, "Feedback, not
stars" below, at the user's own new pick). The user chose this, "wait", over "mark and move on" (the short note gets the faint star and the next note moves the run
on) after seeing what the recordings would have done: every child there went on through the tune after a short note,
so the run will often stop at the first long note until the child slows to the animal's pace, which is the lesson.
Any other note heard while a note cut short waits shows in the live bubble but is no wrong note: the child went on,
and "Check your fingers" would be false. `TuneCheck` judges it ignored (since 2026-11-13; before, it was logged "wrong"
while the screen showed nothing, which misled the reading of sessions, and the replay takes that old "wrong" as the
ignored of today: `Replay.sameVerdict`); nor does the note written after a repeat that was heard and cut short forgive
that repeat (`TuneCheck.forgiven` is for a repeat the phone never heard; forgiving this one would be "mark and move
on"), and that repeat's own letter coming back by a step walks again: the run tells the check the step was begun
(`TuneCheck.judge`'s `begun`, the walk standing there cut short; the one owner of that fact), since the note before
it ended when the repeat began (before, such a note was ignored as that note's tail, and the run sat on "Hold it
longer" while the child held the right note). While a note is walked its step is still the one waited on (`waiting`), so the live bubble under it shows the
note being played, the phone saying it hears it, and a screen reader still hears "play this one": keep playing it. An earned note's written time is finished on the clicks, sound or not
(`Walk.Phase.DONE`; its star shows, the ring stays with the animal, `waiting` is false), and then the animal hops to
the next note and stands still there; a note begun in that last half beat is judged for the next step and takes the
animal there at once. The animal moves in silence only there and on the rests after it (below) (Claude's call in the spec, which the user let stand): a child lets go a little early to
breathe or to tongue the next note, and a still animal must keep meaning one of two things, "hold it longer" or "your
turn to start the next note". A written repeat's "Stop, then play it again" counts its second from the end of the
note's written length, when holding it stops being right (with a rest between the two, from the end of the rest: the
animal walks the rest first, and nothing is asked while it does). Rests behave as in Play since 2026-11-13 (the user, after
the phone tests: the child follows the animal above the notes, and it skipped them; spec
`2026-11-13-flute-your-turn-rests-design.md`): when an earned note's written time is over and a rest follows (breaths
passed over), the rest is ringed and the animal walks it on its clicks (`Walk.Phase.REST`), from the beat the note's
written time ended on, then the next rest after it, then the next note's head, where it waits; a rest opening the tune
is walked from the start, and one ending it after the last note's time, the run then over (the keys come back when it
ends, and a note heard during it is not judged). Nothing stands under a rest (`waiting` is false) and it earns no
mark. A note the child starts during a rest is judged at once for the next note and the animal jumps to it (the user's
pick "A" over "wait until the rest is over": a rest punished for being short is harder for a child to understand than
a note held too briefly; the three runs through the 80 a minute quarter rest left 0.85 to 0.98 s of its 0.75). A run
rule, `walkRests`, is 0 for the sessions recorded before it (`Replay.BEFORE`), which passed rests over. Breaths take
no time, as in Play; holding a note too long is fine, and the time between notes is not judged. A metronome left running on its own tab keeps clicking, and its
clicks will not line up with the animal's hops, which are timed from the child's note; the two tabs' view models never
reference each other, so that stays. The pinned sessions of 2026-11-05 and 2026-11-10 earn other marks under the walk and
were pinned again at the user's "ok" the same day: `17-01-39` and `17-04-38` steps 0 to 5 (they stop on the first half
note), `19-02-37` (the laptop's speaker) step 0, `17-03-03` unchanged. Tests: `ListenRunTest` (the walk, from "Each note held for its length"), `TuneCheckTest`
(`earn`, `stumble`), `NoteOnsetTrackerTest` (the lead), `ListenPathTest` (on sound, at 120 and at 40 a minute),
`FluteViewModelTest` (the hops, `flute.held`, `flute.short`, the tempo read at the start), `FluteUiStateTest`,
`FluteScreenTest` and `FluteSidewaysTest` (the line in both languages); `ReplayTest` (a session recorded before the
walk replays pitch only, and the sweeps). The run's own rules are one value, `ListenRun.Rules` (`lengthSlack`,
`maxNeedMs`, `repeatAdviceFrames`, `finishFrames`, `restartCuts`, `graceFrames`), made like the tracker's: a session writes it
down beside them, `Replay.recordedRunRules` reads it back (a rule a session does not name did not act on the phone, and
takes what the builds before it did from one table for both sets, `Replay.BEFORE`), and a sweep moves one by its name, the audio
detected once (`Session.frames`) for every value. A session that recorded the walk (`walk,halfBeat,phase` in
`frames.csv`) has it compared in the replay's diff, so "diff: none" also says the notes were earned and cut short on
the phone's frames; `tools/session-report.py` draws ♪ where a right pitch began a note, ★ or ☆ where it was long
enough and ✂ where it was cut short (an older table keeps the star at the onset, where it was earned then).

The first runs with the walk (2026-11-12, issue #4's phone test: one child, twenty runs over a cable, a scale of whole
notes and two tunes of quarters and half notes at 60, 80 and 100 a minute; the user: the notes took "much more time than
when played back", the animal went off the screen on a long note, and the child is learning and was tired by the later
runs). Every run replayed with no diff, so the rules did all of it, and three things changed on 2026-11-13. A child's
breath dips about 10 dB for a moment inside a held note (the quietest 21 ms fell from 0.69 to 0.49 to 0.52, where a
tongued stop in the runs of 2026-11-05 fell to 0.16 or under), which the tracker's `STOP_DROP` hears as a tongued stop:
a whole note was cut and walked again from its head while the child never stopped, so an F needed 5.3 s of blowing for
its 3 s and an E held 2.8 s was told "Hold it longer". Now a restart of the note walked with no gap goes on
(`restartCuts` 0; only a gap, sound that ends, cuts a note), which also lets a half note tongued as two quarters
count, a learner's slip the user asked to allow ("count in that the child is learning"). `STOP_DROP` itself stays: at
0.2 the synthetic 40 ms tongue in a lively room (`ListenPathTest`) is no longer heard, so the tracker's repeats are
untouched. The child played every length in proportion but at their own pace, about 85 a minute whatever the knob
said: quarters 0.7 s and half notes 1.2 to 1.7 s at 60, where half a beat's slack needed 1.5 s and cut 5 of 12 half
notes by 50 to 300 ms, just after the animal's second hop; the user picked three quarters of a beat (1.25 s, about one in twelve
cut), and the four pinned sessions keep their marks. And the animal on a whole note's last beat after a whole note was
past the phone's edge: the staff now follows it ("While the tune runs"). Not changed, at the user's word: 17 of the 52
wrong notes were the right fingering played 50 to 70 cents sharp (the child's flute read +15 to +60 cents on every
note, a headjoint pushed in or a hard blow), judged as the next semitone; the user kept exact semitones, so the fix
is tuning the flute (the headjoint out a few millimetres, checked on the tuner tab), not a wider net. The other 35
wrong notes were real. The flute was tuned on 2026-11-13, and it was not enough: the tuner's log shows steady A's at
about +5 cents (15:14:30, 15:15:56 to 15:16:15) and, minutes apart on the same flute, at +25 to +40 (15:16:26 to 37,
15:17:14 to 18), and the 18 runs after it (old build, every one "diff: none") played A at +31, G +24 and B +38, so 24
of their 57 wrong notes were a B 46 to 66 cents sharp heard as C. The 12 runs on the new build after more tuning
(15:47 to 15:57) still played G +31, A +34, B +42, the same shift on every note as a headjoint about 7 mm too far in
would give, and louder was sharper still. They also showed why a wider net is wrong on such a flute: a G fingered as
F# came out 52 to 72 cents under the G, so accepting 70 cents either way would have starred real wrong notes. The
flute's tuning is the fix. `14-59-04` and `14-59-30` are pinned locally (whole notes with the breath dips).

The room under the stage (since 2026-11-10, the user's "3C" of four drawn options: A as it was, B a line to start,
C this, D the fingering under every note; the page is https://claude.ai/artifact/PbsKoRyeJvf17VzucDwKDz). While the tune
runs the keys are gone and their room held only the advice line, so a "Your turn" began with a ringed note, the grey
dots and no word of what to do, and a wrong note showed what came out but not what to do about it (the review of that
day; the lesson of the Hear game's child test, a waiting screen says what it waits for, had not reached this screen).
Now, `ListenRoom` in `FluteScreen.kt`: from the start of a run until its first note is judged, right or wrong, the
line says "🪈 Play B4" / "🪈 Odsviraj h¹" (`flute_play_prompt`, the note with its raised octave as its pill has it;
`FluteUiState.promptMidi`; the flute and not the tuner's ear, because the picture shows what the child does), and once
a note was judged it says nothing, as before: the ring and the dots do, and a line changing under every note would
pull the eye off the staff. After a wrong note the advice is "👀 Check your fingers" (`Listening.advice` gives
`CHECK_FINGERS` while a wrong note stands, after the microphone's own advices and a held repeat, which keep their
place), or, from 2026-11-13 until 2026-11-14 (when such a note became the right note blown high, with the tuner's
"⬇️ A little lower" and no fingering: "Feedback, not stars" below), for a wrong note one semitone above the written one
that locked within `NEAR_CENTS` (70) of it, "🍃 Maybe too high? Gentler air" / "🍃 Možda previsoko? Nježniji zrak"
(`WrongNote.tooHigh`, gone since, and `TunerAdvice.TOO_HIGH`: the right fingers blown high sound like that, as the sharp flute of 2026-11-13 did on all 30
of its b's heard as c; a guess, so "maybe"; "gentler air" is the tuner's own phrase; no "too low", since on that
flute an f-sharp fingered for g came out just under the g and would have blamed the air), and over either stands the fingering of the note that was wanted (`FluteUiState.fingeringMidi`, the tuner's
`FingeringDiagram`, which moved to `ui/common` for this, as wide as the stage), until the right note is played: the
heard note says what came out, this says how to get the other. Nothing is added while things go right, which is why
the user chose it over the fingering under every note (the child would read the picture and not the notes). Playing
the tune back shows nothing there. On a short screen, where the room is not spread by weight, it grows to hold the
fingering and the tab scrolls. A phone on its side has the line alone, at the top of its column where the advice
stands (`FluteRunningSideways`'s `prompt`): the column is 116 dp wide and the diagram would be about 17 dp tall, and
from a music stand's distance its keys could not be read anyway. Since 2026-11-16 the room also holds the level's pair,
🎵 Practice and 🎓 Exam, under the prompt until the first note is judged ("Practice and Exam"), stacked in the column
on a phone on its side. Tests: `ListenRunTest` (the advice and its order),
`FluteUiStateTest` (the two values), `FluteScreenTest` ("The room under the stage") and `FluteSidewaysTest` (the line
in the column, no fingering).

Feedback, not stars (2026-11-14; spec `docs/superpowers/specs/2026-11-14-flute-your-turn-feedback-design.md`, plan
`docs/superpowers/plans/2026-11-14-flute-your-turn-feedback.md`, both local). After the evening's 20 runs (`sessions/
2026-11-14_*`) the user turned down counting sharp notes as right with: "child still learns how to play, it's not about
making stars correct it's about giving right feedback how far it's from correct tone and cadence." What those runs
showed: 25 of the 66 wrong marks were the right fingering blown 48 to 71 cents high and named the next note up (a B as
C 19 times), and 12 more followed from them, the run waiting while the child played on (one run sat on note 15 for ten
notes); a B 40 cents high earned a full star and one 52 cents high was "wrong", nothing between; whole runs sat in tune
(B +1 to +9) and whole runs sharp (+40 to +53) on the same flute, one second apart, so the head joint could not fix
it and only the child, told, can; and the child kept about 65 a minute with the knob on 60 and on 100. The user's
picks, from questions and true-size mockups (`.superpowers/brainstorm/57720-1794689123/`): when the fingering is right
and the playing is off, remark and move on (a wrong fingering still waits); for timing, the lengths as before plus one
line about speed when the run ends; the sign instead of the star under the note ("2C": ⬇️ ⬆️ ⏳, a faint ⭐ only after a
wrong note or for a repeat the phone could not hear); the hourglass for a note let go too early (the 🌬️ of the "Hold
it" line is too pale at mark size, so the line took ⏳ too; on 2026-11-15 the user took the wording "⏳ Too short, hold
longer" / "⏳ Prekratko, drži dulje": "Hold it until the ⭐" was untrue once the note is marked and the run has moved on,
and a child who held the note again got nothing); and the speed line standing until Stop ("3A"). This
overturns exact semitones (2026-11-13) and "a note cut short is played again" (2026-11-11). The rules, each a named
rule a session records and a replay moves: a note the semitone above the written one, locked within 75 cents of it,
is the written note blown high (`TuneCheck.HIGH_CENTS`, `highCents`; the 25 locked 48 to 71, one at 71.04, and no wrong
mark lay between 75 and 100), in the nearest octave like the letter in any octave, and under the written note the
semitone's 50 hold (an F sharp fingered for G sounded 52 to 72 under the G on 2026-11-13); a note walked is off its
pitch beyond 25 cents (`ListenRun.TUNE_CENTS`, `tuneCents`: 46% of the evening's 265 right fingerings, 79 to 95% in the
sharp runs and 0 to 4% in the in-tune ones; 70% at 15, 17% at 40), read from the median of its last five shown frames,
the arrow starting where the note locked and moving after three readings in a row on another side (`TUNE_FRAMES`;
without it 22 of the 265 flickered), and its mark is the arrow at the moment it is long enough (90% the same as the
whole note's own reading); a note let go too early is marked ⏳ and the run moves on (`shortMovesOn`), the animal
standing where it got to for the rest of its written time (`Walk.Phase.CUT`) and the note that cut it judged for the
next step; the child's speed is the median time between the starts of two notes in a row, the second begun with no
wrong note, per click of the knob written between them (rests included; a click is a quarter in x/4, an eighth in x/8,
a half in x/2, so it compares with the knob's own number: the final review caught it counted in quarters, which made
6/8 played in time "slower"), said only with 6 such pairs (`paceMinPairs`), another animal than the
knob's and more than 15% off (`paceOff`). On the screen: the live bubble shows the written letter, never its
neighbour's, with the arrow just right of it while the note sounds off (`PitchArrow`, `PITCH_ARROW_SIZE`); the line
says the tuner's "⬇️ A little lower" / "⬆️ A little higher", and keeps saying it after the note is marked, until the next
note is judged (`Listening.remark`); no fingering (the fingers were right). The tail of a note just earned that sounds
as another letter (a B blown high is C to the tracker) is still that note's tail (`ListenRun`'s `tailOpen`): no C
bubble under the next step, and a sharp B held into its written repeat asks to stop and play it again. When the run
ends at another animal's speed its screen stays, the microphone closed and the session ended, with "🐢 Slower than the
🐇" (the child's animal first; no past tense, which Croatian would give a gender) until Stop or anything else that ends
a run (`flute.listen_stop` over=true). What the 20 runs make of it under today's rules: wrong marks 66 to 28, the
semitone-above wrong notes 29 to 5 (each a B flat 113 to 135 cents over an A, a real wrong fingering), and the run
that sat on note 15 earns 21 ⬇️. Under the rules they were recorded with all 72 sessions until then replay as before
(`Replay.BEFORE`: `highCents` 0, `tuneCents` infinite, `shortMovesOn` 0; the eight of 5 and 7 November too). Whether
a note in the band is the note going on or the next one is decided by its pitch (the final review of 2026-11-14): an
onset that locked within `LOCK_CENTS` of where the walked note sounds (an octave pop, a breath dip of a b blown high)
is the note going on, whatever is written next, and one that moved is the next note if that is its letter; and once a
note is earned, the same note started again at its own pitch while its sound goes on is its tail (`TuneCheck.judge`'s
`sameAsLast`), unless its written repeat is waited on, so a sharp b dipping before a written c never marks the c. While
an ended run's speed line stands the phone may sleep (`FluteUiState.keepsScreenOn`), and a running row's end keeps room
for the arrow beside the last note's bubble (`StaffGeometry.endPadding`). Tests: `TuneCheckTest`, `ListenRunTest` ("Feedback,
not stars", "A note let go too early", "The speed of a finished run"), `ListenPathTest` (b's blown 60 high, a note 60
under), `FluteUiStateTest`, `FluteViewModelTest`, `FluteScreenTest`, `FluteSidewaysTest`, `LabelsTest`, `ReplayTest`.

Practice and Exam (2026-11-16; spec `docs/superpowers/specs/2026-11-16-flute-listen-levels-design.md`, plan
`docs/superpowers/plans/2026-11-16-flute-listen-levels.md`, both local). The child played on 2026-11-15 and found the
rules very loose: a tone she heard as wrong got a star, and a note held too long was never told (those runs were lost:
the app was reinstalled before they were pulled). The 70 runs of 12 to 14 November replayed under the rules then showed
both: of 224 starred notes 22% lay 20 to 25 cents off over the whole note and 10% went past 25 after the star (a mark
was final once the note was long enough), and 30 of 771 notes ran more than half a beat past their length with nothing
said. The user: "at first users probably want to have loose rules but once user learn one expect more strict rule, to
cover that add levels or skills that actually control gate". Against each run's own centre her notes lay within 10
cents on 71%, 15 on 86%, 25 on 99%; one held note wobbles 12 to 15 by itself; the run as a whole sat 34 cents sharp.
So two levels, not 25-20-10: Practice is the rules as they were, and Exam 15 cents, half a beat of room either way and
a mark that follows the whole note; 10 is the tuner's "Perfect!" for one held note, beyond a child through a tune. The
user's picks: the child sets the level (not the app by itself, nor an adult behind the logo), in the listening room
under "Play B4" until the first note is judged ("1C"; not in the Play row, where "Listen to me" no longer fit a 360 dp
phone, nor on its corner); Exam's mark follows the whole note and a note held too long gets a sign; then, on reading
the spec, "on practice also check when held is too long", so ✋ is in both levels, three quarters of a beat over in
Practice (the room the short rule gives the other way) and half a beat in Exam; ✋ with "Too long, let go sooner" /
"Predugo, pusti ranije" (over 🛑 and ✂️). The words took four rounds of true-size mockups
(`.superpowers/brainstorm/94099-1794824437/`): a single wordless picture button (a seedling and a tree, two faces, a
target) was "still unclear this set skills expectations (loose / strict)", then "Gentle / Strict" and the marks
"Good / Very good / Excellent" were passed over for "language that is used by flute teacher when they test skill":
"Practice / Exam" (Vježba / Ispit), with 🎵 and 🎓 (the user combined two drawn sets), as both pills of the app's own
`ModeChoice`, the chosen one lit. The rules, each a named rule a session records and a replay moves: `longSlack` (1.5
in Practice, 1 in Exam; infinite before), `wholeNote` (Exam), and Exam's `tuneCents` 15 and `lengthSlack` 1
(`ListenLevel.rules`). How: the note just earned stays open while its tail sounds (`ListenRun`'s `OpenNote`, with its
own start and the last frame the tracker showed it: the walk's time is replaced by a rest or the next note), and its
mark may change on a frame (`Step.changed`, the row's `changed`): ✋ the moment its sure time runs past the written
length and the room, never for the tune's last note; in Exam, when it closes (silence, another note judged, or the
run's end, read before the frame's row so the change is on it), the arrow if the median of every reading of the whole
note lies beyond 15 cents, else the star it earned or would have (`TuneCheck.star`), and ✋ stays. A mark changed by
silence keeps its line until the next note is judged; one changed because the next note was judged has none. A level
chosen before the first judged note starts the run again at once (the session ended with no marks, the microphone
closed and opened again so the new session has its own audio, the room never blanking); after that it waits for the
next run. What the 70 runs make of it: Practice ✋ on 20 of 818 marks (15 half notes; 15 with the knob on 100 while the
child played near 65: the speed line's message) and no other mark moved; Exam ★ 76 (Practice 206), ⬇️ 602, ⏳ 88, ✋ 24.
Tests: `ListenRunTest` ("A note held too long", "Exam: the whole note", the levels' rules), `TuneCheckTest`,
`ListenPathTest` (the hand and the drift on sound), `FluteUiStateTest`, `FluteViewModelTest`,
`SharedPreferencesSettingsStoreTest`, `FluteScreenTest`, `FluteSidewaysTest`, `FluteTabletTest`, `LabelsTest`,
`ReplayTest`, `FileSessionRecorderTest`.

The room is not judged (since 2026-11-10; `NoteOnsetTracker.MIN_LEVEL`, the rule `minLevel`). In the review of that
day, on the user's phone, a run waited about twenty seconds before the first test note, and in that time the room
(a voice or a television would fit the pitches) earned the first note its star and the second five wrong marks: a
clear pitch passes the probability gate however quiet it is. The recorded session (`2026-11-10_19-02-37`, pinned
locally) replayed with no diff, so the rules did it. Its room's counted frames were never louder than 0.36; every
note of the real flute sessions of 2026-11-05 was judged at 0.67 or over (median 0.79); the test notes from the
laptop's speaker were 0.47 to 0.66. A sweep of `minLevel` over that run hears exactly the eleven test notes and
nothing of the room at every value from 0.30 to 0.45, and starts losing the test notes at 0.50; the three real
sessions earn the same marks at every value up to 0.60. So the floor is 0.45: a frame with a clear pitch under it
does not count (`FrameDecision.FAINT`), as one under the gate does not, which means a note goes on through such
frames and ends by the gap rules, and a note whose swell passes through the floor is heard a frame later. What the
child sees for sound left out is what the screen already had: no heard bubble, the listening dots, and after about a
second of sound from `HearingTracker.SOUND_LEVEL` up, "Come closer to the phone" (0.45 is where `HearingTracker`
stops calling a sound close: `CLOSE_LEVEL` less its margin). So a miss still never shows silence, and a sound nobody
played at the phone can earn no mark. What it costs: a child playing very softly or far from the phone is asked to
come closer instead of being judged; one room and one sitting of real playing stand behind the number, so a session
where that happened is the thing to sweep (`-Psweep=minLevel:0,0.3,0.4,0.45`). A voice right at the phone is as loud
as a flute and is still judged; no level can tell them apart. The 20 runs of 2026-11-14 had such talk in most of
them (somebody speaking before, between and after the notes, at 0.4 to 0.7), and showed one thing that was the
detector's and not the room's: a voice a little under the flute's range came out as exactly the lowest pitch the
detector searches (246.15 Hz, a low B; 73 frames that evening, 22 past the gate), because a dip still falling where
the search ended was reported as a period. `TuneCheck` takes a note in any octave, so in `2026-11-14_20-24-41` talk
was taken for the tune's B, two written repeats before it were passed over and the B's walk began with nobody
playing. Such a dip gives no pitch since that evening (`YinPitchDetector.detect`, step 4; `YinPitchDetectorTest`):
replayed, that run gives the two notes their full stars back, and no other mark changes in the 72 sessions recorded
until then. A voice inside the range (twice that evening a low C at about 260 Hz between two notes, each costing a
right note its full star) is still a pitch and still judged. The tuner does not apply the floor. Tests:
`NoteOnsetTrackerTest` ("too faint to be the flute at the phone"), `ListenRunTest` (the advice), `ListenPathTest`
(synthetic room sound before the child plays, with the floor and without). The synthetic tests' notes moved from
0.4 to 0.7 with it: 0.4 was a guess from before sessions were recorded, and is the room's side of the floor now.

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
`FluteUiState.animalIndex`, `animalHalfBeat` (the last click's `playingHalfBeat` in Play, the walk's
`halfBeat` in Your turn, 0 while it waits at a head), the speed's emoji, the one `rememberBeatFlash` of the tab and `waiting` in Your turn. The
animal stands over its step's head when the step begins and, on each click inside it, over that
beat's place (`StaffGeometry.beatX`: the head plus half a beat's room, 14.5 dp, per half beat,
past a bar line inside a tied note), like the metronome's beat bubbles: a half note is two places
29 dp apart in 4/4, a whole note four, a quarter in 6/8 two places 14.5 apart, an eighth only its
head; it slides to each new place in `FLASH_DURATION_MS` with `FastOutSlowInEasing` while it hops,
so it lands as the hop ends, across bar lines and breaths too. In Your turn it walks the note the child
plays the same way and then goes on to the next, and `ListenRun` says when it hops (`ListenRun.Step.hop`: a click
inside the note or rest walked, a rest's head, the move to the next note, back to the head of a note played again, never a note beginning
where it stands; `beatPulse` goes up on each and at the start; "Each note held for its length" above). It is one layer over the `LazyRow`, the size of the knob (`CORNER_BUTTON`), its middle
`StaffGeometry.ANIMAL_Y` (27 dp, unscaled like the knob: the knob's middle until the knob was lifted
8 dp on 2026-11-10, the animal's line staying where its hop keeps inside the box) from the box's top,
`ANIMAL_SIZE` (24 dp) times the scale tall, placed in `Modifier.offset {}` from the row's
`layoutInfo` (the first item shown, plus the dp from its start, which `StaffGeometry.markWidth`
sums), so it follows the row's scrolling with no recomposition, and clipped at the row's sides
only, so a staff the child drags back hides it rather than sliding it over the knob. Over the
stems of the notes from f² up it stands on the stems' tops, and over c³ it touches the top edge of
the head (`StaffGeometryTest` checks every other head stays under it). The staff still scrolls on
at every new note (`Bars.anchor`, below), so in a long tune the staff moves under the animal at
each note and the animal steps on along a long note; and since 2026-11-13 on from there by just enough to keep the
animal's box in the row (`StaffGeometry.followScroll`, keyed on that amount, so hops inside the row scroll nothing):
on a whole note after a whole note its last beat lay past a phone's edge (the child's report of 2026-11-12), in Play and
Your turn alike, and the note before then slides partly out. Under the names a marks row
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

Legato (2026-11-16; spec `docs/superpowers/specs/2026-11-16-flute-legato-design.md`, plan
`docs/superpowers/plans/2026-11-16-flute-legato.md`, both local). The child learned slurs and has sheet music with them
(the user: "legato is next thing child has learned and has notes with it", and not to wait for recordings: "this is new
feature"). Of three ways the user picked a join key, one tap per pair (A); a slur switch (B) stays on and joins the wrong
notes when tapped a note late, and pressing the next key before letting go (C) is invisible and needs two fingers;
sliding across the keys was out, each row being an octave. The ⌒ key stands left of 💤 (the top row had three empty
columns; the user's offer to shrink and scroll the keys is kept for the next feature, more notes). From true-size
mockups (`.superpowers/brainstorm/9597-1794833435/`) the user took 1C, the key's picture the slur it draws (the staff's
own crescent, `slurPath`); 2A, the slur under the heads in ink, where the sheet music has it with every stem up, and one
muted arc under the pills, as ties are drawn; 3A, ✂️ in the star's place (a cut arc sat half hidden at the running
staff's left edge, where the note before the lit one stands). Writing: a tap after a note, or while its key is held
(which goes on sounding: `onJoin` never ends a held key), waits, the key down in its ring, read as selected, and a
dashed arc running on from the note; the next note pressed is `TuneNote.joined`. A second tap, 💤, 😮‍💨, 🧽 (which
cancels and erases nothing), Play, Listen to me, the book, the speed panel and leaving the tab cancel it
(`flute.join_cancel`); with nothing to join from (an empty tune, after a rest or a breath, the cap) the key is faded. A
join to the same note is a tie, drawn as a tie at a bar line is and played as one sound, so a dotted quarter can be
written as a quarter tied to an eighth. Play drops the gap before a joined note and glides into it
(`FluteTonePlayer.glide`: the pitch moves phase-continuous, the level held); a key pressed over a held key keeps its
short dip, since a smooth change there would sound joined without being written joined. The staff draws every arc in
one layer over the row (`SlurLayer`), placed from the row's layout as the walking animal is; a slur passes under the
heads and ties between its ends (`StaffGeometry.slurCurve`) but never comes within `SLUR_PILL_AIR` of the pills, which
win under the lowest notes. Listen to me, in both levels: a note joined to another letter that starts after a gap, or
after a stop at least `ListenRun.SLUR_STOP_DROP` (0.25, 15 dB) deep, is ✂️ (`StepMark.TONGUED`) with "✂️ Join them, no
tongue" until the next note is judged; never after a wrong note, which already broke the join. The depth lies between a
child's breath dip (about 10 dB) and a tongue in a dry room; the tracker's own `STOP_DROP` (7 dB) hears breath dips too,
which is why `Onset.fresh` alone is not a tongue (`NoteOnsetTracker.Result.stopDrop`), and a tongue in a ringing room
may keep its star, the safe side. The depth is measured under the quieter side of the stop, the note before or the note
now sounding (the final review: against the note before alone, a joined note 9 dB softer turned a breath's 10 dB dip
into 0.28 and ✂️; `ListenPathTest`, "a softer joined note after a breath-sized dip is no tongue"), and `flute.heard`
logs it (`stopDrop`). A length sign wins the mark over ✂️ and ✂️ over an arrow; in Exam the whole note's
pitch keeps ✂️. The line ranks under ✋ and under the live pitch line while the note sounds, over the marked arrow,
"Too short" and "Check your fingers". A tie, or a slur to the same letter in another octave (Listen to me hears a letter
in any octave, so not that step), is one note (`Joins.lastHeardWith`): walked across all its notes on their clicks, the
animal stepping onto the second (`animalX`), its length theirs together, both pills the same mark, no ✂️ inside it (a
breath dips like a tongue), never ✋ when it ends the tune. An octave slur broken by a real gap after its first note
sounded its own length is not "too short" on two notes held their length (the final review): its first note earns its
own mark and the later one ✂️ with the join line (`ListenRun.breakSlur`, `TuneCheck.earnBroken`), a gap being a tongue;
let go before that, or a tie broken so (one written sound), it is ⏳ as any note let go too early. Notes not joined are not checked for tonguing. Rules
`slurCheck` and `slurStopDrop`; `Replay.BEFORE` has `slurCheck` 0, and all 80 recordings replayed exactly as before.
The Croatian line is to be checked with a flute teacher. Tests: `JoinsTest`, `TuneWriterTest`, `PlayStepsTest`,
`SustainedToneSynthTest`, `NoteOnsetTrackerTest` (the stop's depth), `TuneCheckTest` and `ListenRunTest` (ties, ✂️),
`ListenPathTest` (legato on sound), `StaffGeometryTest` (the slur, the animal on a tie), `FluteViewModelTest` (the key,
the cancels, the glide), `FluteScreenTest` (the key, the words, the arcs' pixels on the emulator).

Log events (tag `FA.Flute`): `tone.track_open` (rate, block and
buffer frames) / `tone.track_closed` (underruns) / `tone.error`, `flute.note` (midi, heldMs,
length, added: false at the cap, joined: joined to the note before, since 2026-11-16, per note ended), `flute.join`
(waiting: true when the ⌒ key set a join, false when its second tap cancelled one or there was nothing to join from;
since 2026-11-16), `flute.join_cancel` (reason: rest, breath, erase, play, listen, book, speed or leave: a waiting join
dropped; since 2026-11-16), `flute.rest` (heldMs, length, added, per rest
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
for Your turn, `flute.listen` (notes: how many there are to check; listenLevel: practice or exam, since 2026-11-16,
not `level`, which a debug build's session log stamps on every line), `flute.level` (listenLevel, restarted: the run on started again under it, since nothing of it was judged yet; since
2026-11-16), `flute.changed` (index, mark: long or, in Exam, the whole note's high, low, first_try or retry; soundedMs,
writtenMs, cents: a note's mark changed after it was given; since 2026-11-16), `flute.heard` (index, expected,
heard, fresh: whether the note began after a gap or a stop rather than by a step, verdict: right,
retry, ignored or wrong, per new note the tracker reports: the pitch, at the onset), `flute.held` (index, heldMs:
the note sounded long enough and earned its mark; since 2026-11-11; mark: first_try, retry, high or low (or tongued,
since 2026-11-16), and cents: the
median of its readings off the written pitch, since 2026-11-14), `flute.short` (index, heldMs, needMs: it
stopped before that, "⏳ Too short, hold longer"; faded: it had faded away first, "Maybe out of air?"; since 2026-11-13;
mark: short when the run moved on, none when it waited, since 2026-11-14),
`flute.heard` also carries cents (where the note locked, from expected; since 2026-11-13), `flute.repeat_forgiven` (index: a
written repeat of the note just judged passed over with a faint star because the note after it
was heard, once per such step),
`flute.listen_summary` every `ListenRules.SUMMARY_FRAMES` frames and once more at the end (the
tuner's summary fields plus gate, expected and hearing: the line that says whether a child's tone
passed the gate at all, and since 2026-11-10, in `faint`, how many frames had a clear pitch but were under the
level of a flute at the phone), `flute.hearing` (hearing, levelMean, when it changes), `flute.listen_done`
(stars, retries, when the last note is earned; since 2026-11-14 also high, low and short, the marks of each kind (and
long and tongued, since 2026-11-16), and
bpm, pairs, knob and speed: the child's speed, the note pairs it rests on, the knob's, and whether the line about it
shows), `flute.listen_stop` (reason: pill, key, erase, leave, speed or book, so every `flute.listen` has a closing line,
or a `flute.level` with restarted true when the run started again at another level before its first note;
over: the run had ended and only its screen with the speed line stood, since 2026-11-14), `flute.listen_error` (message, when
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
`../DESIGN.md`). `FluteContent` builds the same pieces upright shows (the tune, the keys, the Play row)
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

The one tab that adjusts anything on top of a tablet's zoom (`ui/DESIGN.md`, "Tablets"; spec
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
