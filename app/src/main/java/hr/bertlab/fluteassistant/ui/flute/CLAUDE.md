# Digital flute

This file holds only what must not be broken. How the tab works, why, and who decided what is in
`DESIGN.md` beside it (not loaded by itself: read the part named under "Read first" before you
change that part). The songbook is in `SONGBOOK.md`.

## Where things are

- `domain/flute/`: the rules, pure Kotlin with no clock and no coroutine. `FluteNotes` (the flute's 37 notes,
  C4 to C7 since 2026-11-18, and the note a letter key writes in the chosen octave, `inOctave`), `Tune` (lengths, the
  stored string, `playSteps` for Play), `TuneWriter` (a key held makes a note: growth at the writing pace, the rejoin
  window, the cap, breath, the join, the sign, erase, the step chosen on the staff; which key drops a waiting join or sign is its rule too, it says what
  it dropped, `TuneWriter.Dropped`, and the view model only shows and logs that), `Bars` (how the staff writes bars, ties and
  signs), `Joins` (runs of
  joined notes: the arcs, and what Listen to me hears as one note), `TuneCheck` and `ListenRun` ("Your
  turn"; what the screen shows of one is `Listening`, in `Listening.kt`), `Songbook`.
- `domain/listen/`: what the microphone's frames become (`NoteOnsetTracker`, `HearingTracker`,
  `ListenRules`), shared with the tuner (`../tuner/CLAUDE.md`).
- `FluteViewModel`: gives the rules the clock, the sound, the microphone, the storage and the log.
  `FluteUiState` has one part per way the tab can be: `held`, `playing`, `listening`.
- `FluteContent(state, metronome, actions)` builds the tune, the keys and the Play row once, and
  `FluteSideways.kt` only says where they stand. `TuneStaff` draws from `StaffGeometry` (plain dp; where a
  step's heads, pills and bar lines stand is `StaffGeometry.layout`, the one place that knows a sign's room), its signs
  from `ui/common/StaffSigns.kt`; `FluteKeys` builds its keys from `ui/common/KeyLook.kt`'s `ChunkyKey` (shared with the
  Fingers tab since 2026-11-17); the ♯♭ key is that file's `SharpFlatKey`, drawn with the staff's own signs
  (`SharpFlatPicture`), and the ⌒ key its `TapKey`. Which black note a letter writes is `NoteSpelling.blackOf`, in
  the chosen octave (`FluteNotes.inOctave`).
- `audio/flute/`: the tone (`SustainedToneSynth`, `AudioTrackFluteTonePlayer`).

## Rules

- Every write to the tune goes through `FluteViewModel.save()`: it stores the open book and clears
  the marks of the last "Your turn".
- The metronome's tempo and beat have one owner, `MetronomeSettings` (`domain/settings`): read its
  `config`, change it through `update`, and never keep a copy or go to `SettingsStore` for them.
  `FluteViewModel` and `MetronomeViewModel` never reference each other and no screen is handed
  another tab's ViewModel: `openBook()` saves the book being left with the tempo as it is and then
  gives the owner the opened book's, and the metronome follows by itself.
- The old single tune is migrated by the `open_book` key being missing, never by the books being
  empty: a child who wipes all six must not get the old tune back (`SONGBOOK.md`).
- A note's length comes only from how long its key is held, counted at `TuneWriter.WRITING_PULSE`
  (`WRITING_BPM`): 130 quarter notes a minute whatever the speed knob and the time signature say (the user,
  2026-11-17: "set max tempo 200 just when child type notes and want to set duration, is just for note duration, do
  not make changes on tempo in metronom or flute view during the play / listen"; 2026-11-23: "reduce speed of adding
  notes to 130, just adding notes, not metronome"). A quarter is about 460 ms of holding, a whole note 1.6 s. It is
  shown nowhere and never written to `MetronomeSettings`; the knob sets Play and Listen to me only. The
  eighth/quarter line is at 347 ms: change the number only at the user's word or from a phone test (`flute.note`'s
  `heldMs`). The time between presses is
  never recorded. No record button, clear-all or length control (root CLAUDE.md, "Turned down").
- "Your turn" is opt-in and score-free, no number, marks not saved. The child decides when each
  note starts; the metronome decides how long it lasts: a note counts once it has sounded its
  written length less three quarters of a beat (`ListenRun.LENGTH_SLACK`, the user's pick of
  2026-11-13) and at most 3 s (`MAX_NEED_MS`), both changed only from recorded sessions (sweeps of
  `lengthSlack`, `maxNeedMs`). One let go too early gets ⏳ and the run moves on (since 2026-11-14,
  `ListenRun.Rules.shortMovesOn`; it was played again before): the animal stands where it got to for the
  rest of its written time, and the note that cut it is judged for the next step. Only a gap cuts a note: its own
  letter started again with no gap is the note going on (`ListenRun.Rules.restartCuts`: a child's
  breath dips like a tongued stop). The animal walks a note only while the child plays it and never hops twice in one
  place; it moves in silence only on from a note that was long enough or let go too early (⏳, where it stands
  still), through the rest of that note's
  written time and any rests after it (ringed and walked as in Play, `Walk.Phase.REST`,
  `ListenRun.Rules.walkRests`), to the head of the next note, where it waits; a note started during
  a rest counts at once. `ListenRun`'s own rules are
  one value (`ListenRun.Rules`: a session records it, a replay moves it): add a rule there, as a constructor parameter
  and a line in its table. What a run runs under (the tune, the pulse, both sets of rules, the room's floor) is
  `ListenRun.Setup`: the view model gives the same value to the run and to the recorder, so a session writes down what ran.
  `DESIGN.md`, "Each note held for its length". A miss never shows
  silence: the heard note, or the listening dots, stands under the step waited on.
- The room under the stage while it listens adds nothing while things go right: "Play B4" until the
  first note is judged, then silence; after a wrong note the wanted note's fingering over "Check
  your fingers" until the right one (the user's pick of 2026-11-10). Not the fingering under every
  note, and no fingering on a phone on its side: `DESIGN.md`, "The room under the stage". The line stays in the
  middle of the room, under the fingering: on 2026-11-18 it moved to the top of the room and the user put it back
  the same day ("reset to previous position"), the one place that keeps out of the other tabs' words-first order.
- The line says why, in a few words with an emoji, and a guess says "maybe" (the user's ask of
  2026-11-13): "⏳ Too short, hold longer" (⏳, the note's own mark; "Hold it until the ⭐" until the run moved
  on, the user's wording of 2026-11-15) for a note let go too early, "Maybe out
  of air?" for one that faded (`ListenRun.FADE_RATIO`). Only what the phone detects; a guess stated as
  fact is a bug. Since 2026-11-14 a note off its pitch has the tuner's own words, "A little lower" or
  "A little higher", while it sounds and after it is marked, until the next note is judged; never the
  fingering (the fingers were right). "Maybe too high? Gentler air" is gone.
- The room is not judged: a clear pitch under `NoteOnsetTracker.MIN_LEVEL` (0.45) is not a note
  (`FrameDecision.FAINT`) and can earn no mark; the child sees the dots and, after a second of it,
  "Come closer". Change the number only from recorded sessions (a sweep of `minLevel`), never by
  feel: `DESIGN.md`, "The room is not judged".
- The engine's frames are buffered, never conflated: `NoteOnsetTracker` counts frames.
- A turn of the phone stops nothing (`../CLAUDE.md`, "Rules"). `FluteKey` and `RestKey` release in a
  `finally`, so a key held through a turn ends its note. `staffPlace` is created in `TuneAndKeys` (`FluteContent`'s tab itself, beside its three panels), above its writing and
  running branch, so the staff keeps its scroll through a turn, Play and Stop.
- Text and emoji on the staff are sized in dp, so they stay in the staff at any text size.
- The running staff keeps the note before the lit one at its left (`Bars.anchor`) and scrolls on only
  as far as the walking animal needs to stay in view (`StaffGeometry.followScroll`).
- Feedback, not stars (the user, 2026-11-14: "it's about giving right feedback how far it's from
  correct tone and cadence"; spec `docs/superpowers/specs/2026-11-14-flute-your-turn-feedback-design.md`,
  `DESIGN.md`, "Feedback, not stars"). The right fingering played off gets a remark and the run moves on;
  a wrong fingering still waits. A note up to 75 cents above the written one is the written note blown
  high (`TuneCheck.HIGH_CENTS`, rule `highCents`); under it the semitone's 50 hold, so an F sharp
  fingered for G stays wrong. Off pitch is beyond 25 cents (`ListenRun.TUNE_CENTS`, rule `tuneCents`):
  an arrow beside the note's live bubble, which shows the written letter, and its mark is ⬇️ or ⬆️ in
  the star's place, the arrow at the moment it is long enough. Marks: ⭐, faint ⭐ (after a wrong note,
  or a repeat the phone could not hear), ⬇️, ⬆️, ⏳, ✋ (held too long, since 2026-11-16); a sign says more
  than the faint star, and a length sign more than an arrow. Change the numbers only from recorded
  sessions (sweeps of `tuneCents`, `highCents`). This overturned exact semitones (2026-11-13) and "a note
  cut short is played again" (2026-11-11).
- One set of rules, `ListenRun.Rules()` at its defaults, and no choice to make (the user, 2026-11-17, after a parent
  could not tell the levels apart: "remove exam / practice, keep just rules from practice and remove exam logic
  completely"; spec `docs/superpowers/specs/2026-11-17-flute-calmer-design.md`). Practice and Exam stood in the
  listening room from 2026-11-16 (`DESIGN.md`, "Practice and Exam", now history); do not bring a level, a badge or
  a view that explains one back. A note whose sound runs more than `longSlack` (1.5) half beats past its written
  length gets ✋ and "✋ Too long, let go sooner" at that moment, but never the tune's last note; a mark that changes
  after it was given is `ListenRun.Step.changed` / `ListenFrame.changed`. Change the numbers only from recorded
  sessions (sweeps of `tuneCents`, `lengthSlack`, `longSlack`). A session of 2026-11-16 names its level and
  `wholeNote`; the replay reads both and ignores them.
- Legato (the user, 2026-11-16: "legato is next thing child has learned and has notes with it"; spec
  `docs/superpowers/specs/2026-11-16-flute-legato-design.md`, `DESIGN.md`, "Legato"): the ⌒ key, left of 💤, joins the
  next note pressed to the last one (`TuneNote.joined`, stored as a third field `s`), one tap per pair; a tap while a
  key is held joins the held note and must not end it. A second tap, 💤, 😮‍💨, 🧽 (it cancels and erases nothing), Play,
  Listen to me, the book, the speed panel and leaving the tab cancel a waiting join; it is never saved. Faded with
  nothing to join from. A join to the same note is a tie: one sound in Play, one note to Listen to me, as is a slur to
  the same letter in another octave (`Joins.lastHeardWith`). Play has no gap before a joined note and glides into it.
  A note joined to another letter that starts after a gap or a stop at least `ListenRun.SLUR_STOP_DROP`
  deep gets ✂️ (`StepMark.TONGUED`) and "✂️ Join them, no tongue"; never after a wrong note; notes not joined are not
  checked for tonguing. The depth is measured under the quieter side of the stop (a softer joined note must not make a
  breath's dip a tongue's). An octave slur broken by a gap after its first note's own length: that note's own mark and
  ✂️ on the other, never ⏳ on both. Marks in order: ⏳/✋, ✂️, ⬇️/⬆️, faint ⭐, ⭐. Change the depth only from recorded sessions
  (sweeps of `slurStopDrop`; `slurCheck` turns it off).
- Sharps and flats (the user, 2026-11-16, the notes; 2026-11-17, the key: "reduce notes to fundemental and add #
  button that allow to create all notes we have now"; spec `docs/superpowers/specs/2026-11-17-flute-calmer-design.md`,
  `DESIGN.md`, "Sharps and flats"): every semitone of the flute's range (`FluteNotes`: C4 to C7, 37, since 2026-11-18;
  C4 to C6 before); the notes game keeps `StaffNotes`, the white notes, on purpose. The ♯♭ key stands first in the
  tools' row, under C. A tap on it makes the next letter key write its black note in the app's one spelling
  (`NoteSpelling.blackOf`: C♯ E♭ F♯ G♯ B♭), in the chosen octave, one tap per note, as ⌒: while it waits the keys show
  those names (cis¹, C♯4), D and A are faded and write nothing, and the key is down in its ring. A letter spends it; 💤
  and 😮‍💨 leave it; a second tap, 🧽 (with a waiting join, both, erasing nothing), Play, Listen to me, the book, the
  speed panel and leaving the tab drop it; an octave pill leaves it; never saved. A key always reports its letter, and
  its gesture is keyed on that alone: the name changes under the finger when a press spends the sign and when a pill
  is tapped, and a finger already on D when ♯♭ is tapped holds its note on (`Held.key`; the writer goes on with a
  bounce by the key, signed or not). Do not key a key's gesture on its name, its note or its faded state. The
  scrolling piano rows of 2026-11-16 and the three octave rows are gone (`DESIGN.md`, "Sharps and flats", "Octaves").
  The staff: a black note on its letter's place with its ♯ or ♭, and a ♮ on a
  white note whose place carried a ♯ or ♭ earlier in the bar (`Bars`); a signed step is `ACCIDENTAL_ROOM` (10 dp)
  wider. Listen to me keeps exact semitones; a semitone slurred on a flute off its pitch is a step, read from the
  note heard sounding (`NoteOnsetTracker.Rules.stepFromSounding`); F fingered for F♯ on a flute 60 cents sharp reads
  as F♯ played low, ⬆️ (pitch alone cannot tell them apart): a known limit, pinned in `ListenRunTest` and left.
- Octaves (the user, 2026-11-18: "support all octave covered by fingers", and after two tests with a child and an
  adult "go with b approach (with numbers 1, 2, 3, 4) and show d1, d2, d3, d4 depending which octave is selected";
  spec `docs/superpowers/specs/2026-11-18-flute-every-octave-design.md`, Revision 3; `DESIGN.md`, "Octaves"): the
  flute writes c¹ to c⁴. The keys, top to bottom: four octave pills, each naming its notes, c¹–h¹ c²–h² c³–h³ c⁴
  (the user, the same night: "replace 1, 2, 3, 4 labels with c1-h1, c2-h2, c3-h3, and c4"; C4–B4 to C7 in English; a
  screen reader hears "Octave 1"), 40 dp tall with the name at 22 (the user, 2026-11-19: "very hard to read", the
  room taken from the staff's box), the chosen one yellow and down; seven letter keys named for the note they write in that octave (c¹, d³;
  `FluteNotes.inOctave`, which `FluteKeys` asks for each letter); the tools in columns 1 2, 4 5 and 7 (♯♭ ⌒, 💤 😮‍💨, 🧽). A key
  writes exactly the note on it, and only a pill changes the octave (`TuneWriter.chooseOctave`): it starts at 1, stays
  through Play, Listen to me and the books, and is not stored; the chosen pill again does nothing at all. In octave 4
  the keys read c⁴ to h⁴ and only c⁴ is live: the flute's range ends there. A pill acts as the finger goes down; tapped
  while a key is held it leaves that note sounding (a key is its letter), ends the rejoin window as ♯♭ does, and drops
  no waiting ♯♭ or ⌒. Play lights a letter key only while the pills show the playing note's octave
  (`FluteUiState.litLetter`). Do not hide the octave figure, work the octave out for the child (the "nearest note" and
  the ⬇ ⬆ of the first build), or split the notes over rows that move (the high-notes key of the second): both were
  tested and failed. The staff: every released number in `StaffGeometry` stands; a tune above c³ needs room over the
  box (`roomOver`, 9 dp a step, 63 at c⁴) and is drawn that much lower (`TuneStaff`'s `atStaff`). Upright, while the
  child writes, the box is the released 257 dp and what the tab's height has over its least (618), up to 63
  (`FluteScreen`: 292 on an A55, 320 from 681 dp); it never changes, so the keys never move, and a tune is drawn at
  full size unless its highest note needs more room than the box has (`restingScale`). While the tune runs it has only
  the room its highest note needs and is no taller than before (`runningScale`), so a tune up to c³ runs exactly as
  before, upright; where the running staff is drawn smaller the animal walks higher (`animalY`, the user's "b,
  higher"), never over more than 4 dp of the top note's head. On its side the card's height sets the scale
  (`SidewaysGeometry.restingStaffScale`), and the keys' column has the pills and two rows of keys.
- Changing a note (the user, 2026-11-24: "tap on note, mark note as selected and change with note user tap, to unselect
  tap again and selection is gone, if note is selected and sponge is tapped remove selected note"; spec
  `docs/superpowers/specs/2026-11-24-flute-edit-a-note-design.md`, `DESIGN.md`, "Changing a note"): a tap on a step of
  the writing staff chooses it (`TuneWriter.select`), lit with Play's yellow ring; the next key writes in its place, the
  length from the hold as always, and lets the choice go when it is let go (the user, the same day: "if user tap on
  notes it should also deselect currently selected note"), 😮‍💨 at once; the same tap lets it go, a tap on another moves it,
  and a tap on the stage beside the notes lets it go (the user: "when user tap on white section with metronom and notes,
  but does not select any note it should deselct currently selected note"; `onStageTapped`, the stage of the writing
  tab only). A tapped note sounds for its written length at the knob's speed, a rest is silence (the user: "when user tap
  on note it should play that note also"); a note key takes the voice from it, and the rest key, Play, Listen to me
  (whose microphone must not hear it) and leaving the stage silence it first (`hushTap`); a tap while a key is held
  sounds nothing; 🧽 removes it
  once ("Erase the chosen note"), never repeating on into the end. Play, Listen to me, the book, the speed panel and
  leaving the tab let it go, through `FluteViewModel.dropWaiting`, the one change on the listening path; never saved;
  nothing while a key is held or the tune runs. A breath is chosen like a note (a yellow disc behind its face). A note
  joined to a step removed or turned into a rest or a breath loses its join; two breaths never stand side by side;
  choosing another step drops a waiting ⌒ (its note changed); 💤 pressed again later does not go on with the rest it
  wrote in the middle. While a
  step is chosen, and after it is let go or erased until the next key writes at the end, the staff does not follow on
  by itself (the user: "keep where it is until the next key writes at the end"). No adding a step in the middle (the user:
  "not in this feature"), and no new key, word or mode for it.
- A finished run played at another animal's speed than the knob's says so in one line, "🐢 Slower
  than the 🐇", the child's animal first (`Pace`, rules `paceMinPairs` 6 and `paceOff` 15%); its
  screen stays as the run ended, the microphone closed, until Stop. No other summary.
- Log event names under `FA.Flute` are stable. They are listed in `FluteViewModel`'s comment, and
  with what each means in `DESIGN.md` ("Log events").

## Read first (`DESIGN.md`)

| Before changing | Read |
|---|---|
| Lengths, the pulse, the stored string, bars and ties | the first paragraph |
| The staff's drawing, the names row, its sizes | the second paragraph ("The stage") |
| Key presses, the rejoin window, the rest key, Play | the same paragraph, from "`FluteViewModel` (Activity-scoped" |
| The keys; the speed knob and its panel | "The keys", "The speed knob and its panel" |
| Sharps and flats: the ♯♭ key, the signs on the staff | "Sharps and flats" |
| The octave pills, the letter keys, the range to c⁴, the staff's box and room, the running animal's line | "Octaves" |
| Your turn: the listening rules, the length rule and the walk, the loudness floor, the heard-note bubble, the advice, the room under the stage | "Your turn" |
| The ⌒ key, slurs and ties on the staff and in Play, ✂️ | "Legato" |
| The running staff, the walking animal, the marks row | "While the tune runs" |
| A phone on its side; a tablet | "Sideways", "Tablets" |
| Choosing a step on the staff, changing or removing it | "Changing a note" |
| Books and the shelf | `SONGBOOK.md` |

## Sessions (debug builds only)

A debug build records every "Your turn" run on the phone: the audio, a per-frame table and the
run's log lines. How to pull, replay, sweep and pin one is in the `sessions` skill
(`.claude/skills/sessions/SKILL.md`). The audio is a child's playing: it leaves the phone only over
the cable and never goes into git (the repository is public).

## Tests

- Rules, on the JVM with no dispatcher: `TuneWriterTest`, `ListenRunTest`, `PlayStepsTest`,
  `TuneTest`, `BarsTest`, `TuneCheckTest`, `SongbookTest`, `StaffGeometryTest`, `JoinsTest`, `FluteNotesTest`,
  and `NoteSpellingTest` in `domain/tuner`.
- `ListenPathTest`: synthetic flute sound through the real detector and `ListenRun` (the emulator
  has no microphone).
- `FluteViewModelTest`: the wiring, the saving and the log lines.
- On the emulator: `FluteScreenTest`, `FluteSidewaysTest`, `FluteTabletTest`.
