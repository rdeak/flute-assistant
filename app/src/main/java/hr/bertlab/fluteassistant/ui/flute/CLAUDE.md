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
songbook's six books (below), each stored as `"<bpm>;<time signature name>;<that string>"` under
`book_0` to `book_5`, and `SettingsStore.saveBook` keeps the open one across restarts (before that
the single tune lived under the key `tune`, which the migration below moves into the first book).
`Bars.write` (`domain/flute/Bars.kt`, since 2026-11-02) is how the
staff writes the tune in bars of the metronome tab's time signature (`FluteUiState.timeSignature`,
read at creation, in `enterTab()` and when OK closes the speed panel, all through
`syncTimeSignature()`, and set to the book's when a book with a tune in it is opened; a bar is `beatsPerBar * 8 / noteValue` half beats of the
flute's quarter-note beat, so 6/8 holds three quarters): `LineMark.Step` / `Breath` / `BarLine`, a
bar line after every full bar (the last too), a note or rest that runs past one split there into
`StepPart`s (the longest lengths that fit, a 2½-beat piece as a half and an eighth) that the staff
ties (a note) or just splits (a rest); the tune itself is never changed.

The stage (the user's canvas of 2026-11-02, boards `Flute*.dc.html`, then changed by the user the
same evening) holds `TuneStaff`, with the speed knob over its top left (described after this
paragraph) and the book button under its clef (see "The songbook"): the staff and, under each note in the place the canvas gave
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
the last step is one (a breath at the cap or after a breath is only shown); `togglePlayback()`
plays the tune from the start at the metronome's tempo, each note for its length, lighting it
on the staff, on its name and on its key, silent for each rest's whole length (its symbol and
its pill ringed, the rest key sunk: `restLit`) and straight past each breath, and
stops at once on the pill, on a key press, on `openSpeed()`, on `openShelf()` or `openBook()` or on `eraseLast()`, which drops the
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
go through `NoteOnsetTracker` (`ui/tuner/`, the tuner's own rules plus "a new note began": after
a gap of `ONSET_GAP_FRAMES` = 2 uncounted frames that also dropped under half the note's loudness
(`GAP_LEVEL_RATIO`: the gate blinking on a breathy tone keeps the loudness and is not a gap), or a
clear step to another note without a gap; a slow sag past the halfway point moves the sounding
note but is no new note; the same note going on is not new, so a repeated note needs a fresh
start, which a tongued note gives; when it does not, the animal keeps waiting and the child plays
it again; the first phone test with a repeated-note tune should watch `flute.heard` for a repeat
counted on one held note or a tongued repeat never counted, and tune `GAP_LEVEL_RATIO` from
`flute.listen_summary`'s levels) and each new note is judged by
`TuneCheck.judge`: **right** is the same letter in any octave (beginners overblow, and the detector
speaks the lower octave at an onset), earning `StepMark.FIRST_TRY` or `RETRY` after a stumble and
moving on; **ignored** is the letter of the step just judged, in any octave, unless that letter is
also the one waited on (a written repeat), which counts only after `REPEAT_GUARD_MS` (300) so an
octave pop never counts the repeat; **wrong** is anything else: `FluteUiState.wrong` (a
`WrongNote`: the step and the MIDI heard) shows the note heard as a round bubble in its own letter
colour in the animal's place until the next note is judged (a right note clears it, another wrong
note replaces it, an ignored one leaves it; no timer, so the child has time to read "written G,
heard A" off the pill and the bubble; since the evening of 2026-11-04, in place of a ❌ for 700 ms,
which said nothing about what came out; the user had asked for note names in the stars' place,
green when right, and agreed to this instead: the right name already stands in the pill above,
green is F's colour in the letter code, and colour alone must not carry right and wrong), and the
step is remembered as stumbled. The check waits for the right note, which is what keeps every mark
under its own note. Pitch only; nothing is timed but the guard. The last note judged ends it by itself
(`flute.listen_done`); Stop (`stopRun`, one wide red pill where the Play row was), a key, erase,
the speed knob, the book button and leaving the tab end it early (`flute.listen_stop`); the marks stay in
`FluteUiState.marks` until a new run, a switch to another book or any write to the tune (`save()` clears them), are not
saved, and show no number anywhere. `HearingTracker` feeds `FluteUiState.hearing` for the
tuner's `AdviceLine` under the stage ("Come closer" / "Play one long, clear note", only while the
sound stays unclear). Play is refused while listening; the keys are off screen anyway.

While the tune runs, in Play as in Your turn (`FluteUiState.isRunning`), the keys and the sponge
go away and `TuneStaff` is drawn at `RunningStaffScale` (4/3: lines 24 dp apart, the canvas's
original size, for a phone on a music stand at arm's length; the geometry stays one table in dp
and a private `LocalStaffScale` multiplies every size and every `scale(density * s)`), with the
advice line in the keys' room and the wide Stop at the bottom; the knob's disc stays in the
corner, empty and inert (`SpeedKnob(running = true)`), because its animal is on the staff: under
the names a marks row (`StaffGeometry.MARKS_TOP`, 26 dp, in the first beat's room so it stands
under the head and the name; the staff grew 16 dp for it) holds, per step, the ⭐ it earned
(`STAR_EMOJI`, faint at `RETRY_STAR_ALPHA` after a retry, both the notes game's), the heard note's
bubble (`HeardBubble`: a round bubble as tall as the row, `noteColor` fill, the ink outline, the
name in `noteText` at up to 14 sp times the scale, the tuner's way of showing another note), or the
animal (`StaffAnimal`: `FluteUiState.animalIndex`, the speed's emoji, the one `rememberBeatFlash`
of the tab, `waiting` in Your turn), which bounces on every click in Play (`beatPulse` as before,
in place on a long note, under a rest's 💤 pill too) and in Your turn jumps to the next note when
the child gets it (`beatPulse` goes up on every advance and at the start, so it lands with a
hop). Emoji in the row are sized in dp (`emojiSizeFor`) like the staff's other text. A screen
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

The songbook (since 2026-11-04, spec `docs/superpowers/specs/2026-11-04-flute-songbook-design.md`;
the user: "child needs to practice more than two tunes and dont want to type notes over again when
tune changes"): the flute keeps six tunes at once, so a child types each school tune in once and
afterwards only switches between them. A `Book` (`domain/flute/Songbook.kt`) is a tune (the same
`List<TuneItem>`, the same `MAX_NOTES` cap, sanitised on load) with its own bpm and time signature,
a `Shelf` is the six books and the index of the open one, and the books sit in six fixed places,
each with a fixed picture that never changes, so a child who cannot read yet learns "my school tune
is the cookie": `Sweet` (`Shelf.kt`) is 🍭 🍪 🧁 🍩 🍦 🍬 in that order, six sweets that share no
shape or colour (the user asked for "dedicated number of slots, eg 6 with pictures", then "sweets
and cookies"; the six themselves were chosen in the 2026-11-04 brainstorm and approved by the user). There are no names, no typing, no choice of
picture, no built-in songs, no delete button, no more than six places and no save step: every change
to the tune goes through `save()`, which writes the open book (`FluteUiState.openBook`; `books` is
the shelf as last stored, `shelf` the same with the open book's tune as the child sees it), exactly
as the single tune was kept before. A screen reader hears the sweets as Lollipop, Cookie, Cupcake,
Donut, Ice cream and Candy (the spec's table says Doughnut and Sweet; the strings follow the US
spellings, as the English flag did the same day) and in Croatian Lizalica, Keks, Muffin, Krafna,
Sladoled and Bombon (`book_*`, used for the screen reader only).

While a book is open its speed and beat are the metronome's: the speed knob, its panel and the
metronome tab change them as before, and `save()` writes the metronome's current bpm and time
signature with the tune, so a change made on the metronome tab while the cookie is open is the
cookie tune's new speed (intended, "yes, each tune keeps its own speed and beat"). Opening another
book is `FluteViewModel.openBook(index)`, and its order matters because `FluteViewModel` and
`MetronomeViewModel` never reference each other: `openBook` first reads the metronome's config from
`SettingsStore` and saves it with the tune of the book being left (also when that book is empty,
where the speed is never used), then switches `tune`, `openBook`, `timeSignature` (the staff is
written in the new book's beat; an empty book keeps the current one), clears the marks of the last
Your turn and forgets the key let go of, and only then returns the `Pulse` the metronome must take;
`FluteScreen`, which holds both view models as it does for the speed panel, calls
`MetronomeViewModel.setBpm` and `setTimeSignature` with it afterwards (never `SettingsStore`
directly: those calls update the metronome state the knob and the metronome tab draw, save the
config and pass it to a running metronome, so a drum clicking in the background jumps to the new
tempo, which is acceptable). Were the setters to run first, the book left would record the new
book's speed. The returned bpm is the book's brought onto the ring's 40 to 200 (`TempoBand.MIN_BPM`
and `MAX_BPM`). It is null, and the metronome stays as it is, for an empty book (the child writes the
new tune at whatever speed and beat the metronome has, and the book records them when it is left),
for the open book picked again (logged as `flute.shelf_close`, nothing else changes) and for an index
off the shelf. Wiping a tune with the sponge until nothing is left empties its book, which stays
open, the staff blank and the button showing its sweet; there is no other way to empty one, so with
all six full the child wipes one to reuse it. After a restart the stored open book is opened and its
speed and beat are already the metronome's, so nothing needs applying.

Storage is `SettingsStore.loadShelf`, `saveBook(index, book)` and `saveOpenBook(index)`
(`SharedPreferencesSettingsStore`): each book is one string `"<bpm>;<time signature name>;<tune>"`
(`Songbook.encode` / `decode`, the tune in `Tune.encode`'s format, which has no `;`) under `book_0` to
`book_5`, and `open_book` is the open index. Reading is lenient, so a damaged book never loses the
other five: what cannot be read becomes the metronome's bpm and time signature, or an empty book, and
the tune is decoded with the book's own bpm as `legacyBpm`, so a tune from before lengths were
snapped keeps its rhythm. The migration from the single tune is decided by `open_book` alone: when
the key is missing the shelf was never stored (an update from before the songbook, or a fresh
install), the old `tune` key becomes book 0 (🍭) at the metronome's stored bpm and time signature,
book 0 is opened, and `book_0` and `open_book` are written and `tune` removed in one edit, so it
runs once. It is never decided by whether the books are empty: a child who wipes all six must not
get the old tune back on the next start, and once `open_book` exists a `tune` key is ignored
(`Songbook.restore`, `SongbookTest`; `SharedPreferencesSettingsStoreTest` in `androidTest` runs the
store over a throwaway preferences file, the first test of the store, and its "fresh store" gets the
process's cached preferences, so it checks the key layout and encoding, not the disk write).

The button (`BookButton`, `Shelf.kt`) is the open book itself, drawn as a little book like the
shelf's (the user, later on 2026-11-04: "sweet icon that shows what slot is currently active should
have same book layout with sweet same as load screen has", and from three true-size mockups,
`book-button-shape.html`, "A is fine"): the shelf's `LittleBook` at the `CornerBook` sizes (a 7 dp
spine, page edges 6 dp out and 4 / 2 dp in from the cover's top and bottom, corners 4 dp at the
spine and 12 at the edge), `StaffGeometry.CORNER_BUTTON` (54 dp) wide with its page edges (the
cover 48) and `BOOK_BUTTON_HEIGHT` (64 dp) tall, with the knob's 5 dp shadow, the open book's sweet
at 26 dp (in dp, as on the shelf, so a large font scale cannot push it off the cover) and no label
(the tune is on the staff beside it; five note bubbles would be 5 dp dots at this size). The cover is
`Sun`, as the open book's is on the shelf (in dark mode too: `Sun` never changes), so the child sees
on the staff the very book they picked, and it pairs with the yellow speed knob one corner up: the
knob says how fast, the book under it says which tune. It replaced a round white button the same
day (white then because it was "not chosen"; the user's "same book" overrode that). It stands at
the staff's bottom left, under the clef, beside the names row, at
`StaffGeometry.bookButtonTop`: centred on the names' pills (`NAMES_TOP + PILL_HEIGHT / 2`), in the
column left of `NOTES_CLIP` (57 dp) that is empty from under the clef (`CLEF_BOTTOM`, 167 dp, the
hook's lowest control point, a bound the stroke never passes) down to the bottom of the marks row
(`TOTAL_HEIGHT`, 257 dp), so nothing moves and nothing is covered (`StaffGeometryTest` checks it at
both scales). At `RunningStaffScale` the cell grows and the button does not; only its position
follows the scaled geometry. It shows on an empty tune too, and while the tune runs it stays, faded
and not pressable, still showing its sweet (the knob is inert then too; the sweet stays because,
unlike the animal, it does not walk the staff). The book fades as one layer: `LittleBook` fades
its page edges and cover together and passes `fade = false` to its `ChunkyButton`, since a cover
faded on its own would show the page edges behind it through its open edge. Only the cover is
pressable and carries the description; `FluteScreenTest` checks the cover's 48 x 64 and that the
page edges, 6 dp further right, end before the notes. It was first sketched at the staff's top right
(approved from the sketch, "A is fine"), and turned down on the real staff for two reasons: the tune
is shown scrolled to its end with `NOTES_END_PADDING` at 4 dp, and every note from c² up has its stem
top inside the button's 0 to 59 dp, so it covered the newest notes' stems; keeping the staff's end
clear instead (about 60 dp of end padding) made every long tune show about two beats less. True-size
mockups of the three places (`button-fit.html`) went back to the user, who chose bottom left, under
the clef, beside the names, on 2026-11-04 ("C is fine"). A screen reader hears "Tune: Cookie, tap to
change" (`flute_book_button`), never a number. A tap calls `FluteViewModel.openShelf()`, which ends a
playback (`flute.stop`, reason `book`), a Your turn (`flute.listen_stop`, reason `book`) and a held
key as `openSpeed()` does and leaves the tone player open, and then `FluteContent` sets
`choosingBook`.

The shelf (`TuneShelf`, `Shelf.kt`): `FluteContent` keeps `choosingBook` in `rememberSaveable`, as it
keeps `choosingSpeed`, so rotation and the language switch keep it open, and while it is true the
shelf takes the place of the stage, the keys and the Play row (leaving the tab drops it, and logs no
closing line, as for the speed panel). Six `BookCover`s stand in two columns and three rows, always
in the sweets' order (🍭 🍪 / 🧁 🍩 / 🍦 🍬), each a little book (`LittleBook` at the `ShelfBook` sizes,
the drawing the book button shares): a cover square at the spine and round
at the edge that opens (`BookLook.shape`), a dark spine down its left edge, page edges (thin lines on
paper) showing past its right edge, the sweet large in the middle and under it a white label with
the tune's first five notes (`Book.coverNotes`, rests and breaths skipped) as small `NoteBubble`s in
their letters' colours, the label always five bubbles wide so every label is the same size (the
bubbles shrink on a 360 dp phone, where five of 22 dp do not fit). The open book's cover is `Sun`,
the others `colors.raised`, and dark mode follows `FluteColors` as every other card. An empty book
has its sweet faded and a blank label; there is no number and no word anywhere on the shelf. One tap
on a book is `onChoose(index)`, which closes the shelf and, in `FluteScreen`, calls `openBook` and
then the metronome's setters; the open book's tap only closes it; there is no OK and the back button
is not handled, as in the speed panel and the notes picker, and a stray tap loses nothing, since
every tune is kept as it is. The sweet is sized from its cover, not set: `sweetSize` is 0.32 of the
cover's height (the mockup's 36 px sweet on a 112 px cover, a third of its height), at least 40 dp
(`SweetMinSize`, on the shortest cover) and at most half the room between the spine and the cover's
other edge, in dp rather than sp so a large font scale cannot push it out or onto the label; a third
leaves the label and the gap under the sweet their room at every height from `BookMinHeight`
(116 dp) up. The column is `fitOrScroll` at `ShelfMinHeight` (three `BookMinHeight` rows, two 16 dp
gaps and the column's 20 dp), so it fits a 360 x 640 dp phone with large text and scrolls only below
that. A screen reader hears each cover as one node, "Cookie, 3 notes" (`flute_book_notes`, the
note-count plural), "Cupcake, empty" or "Lollipop, open now", and the bubbles on it stay silent
(the wording was chosen in the same brainstorm and approved by the user).

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
heard, verdict: right, retry, ignored or wrong, per new note the tracker reports),
`flute.listen_summary` every `TunerViewModel.SUMMARY_FRAMES` frames and once more at the end (the
tuner's summary fields plus gate, expected and hearing: the line that says whether a child's tone
passed the gate at all), `flute.hearing` (hearing, levelMean, when it changes), `flute.listen_done`
(stars, retries, when the last note is judged), `flute.listen_stop` (reason: pill, key, erase,
leave, speed or book, so every `flute.listen` has a closing line), `flute.listen_error` (message, when
the microphone fails: listening ends cleanly) and `flute.mic` (granted, the phone's answer about the
microphone on this tab). The engine's `mic.open` / `mic.close` stay under `FA.Tuner`.
