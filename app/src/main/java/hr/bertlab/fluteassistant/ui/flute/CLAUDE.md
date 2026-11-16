# Digital flute

This file holds only what must not be broken. How the tab works, why, and who decided what is in
`DESIGN.md` beside it (not loaded by itself: read the part named under "Read first" before you
change that part). The songbook is in `SONGBOOK.md`.

## Where things are

- `domain/flute/`: the rules, pure Kotlin with no clock and no coroutine. `FluteNotes` (the flute's 25 notes,
  C4 to C6, and its keys' rows), `Tune` (lengths, the
  stored string, `playSteps` for Play), `TuneWriter` (a key held makes a note: growth, the rejoin
  window, the cap, breath, the join, erase, a press taken back), `Bars` (how the staff writes bars, ties and
  signs), `Joins` (runs of
  joined notes: the arcs, and what Listen to me hears as one note), `TuneCheck` and `ListenRun` ("Your
  turn"), `Songbook`.
- `domain/listen/`: what the microphone's frames become (`NoteOnsetTracker`, `HearingTracker`,
  `ListenRules`), shared with the tuner (`../tuner/CLAUDE.md`).
- `FluteViewModel`: gives the rules the clock, the sound, the microphone, the storage and the log.
  `FluteUiState` has one part per way the tab can be: `held`, `playing`, `listening`.
- `FluteContent(state, metronome, actions)` builds the tune, the keys and the Play row once, and
  `FluteSideways.kt` only says where they stand. `TuneStaff` draws from `StaffGeometry` (plain dp), its signs
  from `StaffSigns.kt`; `FluteKeys` sizes from `KeysGeometry` (plain dp).
- `audio/flute/`: the tone (`SustainedToneSynth`, `AudioTrackFluteTonePlayer`).

## Rules

- Every write to the tune goes through `FluteViewModel.save()`: it stores the open book and clears
  the marks of the last "Your turn". The one exception is a press taken back (`onKeyScrolled`, since 2026-11-16):
  it stores the book as it was before the press and gives the marks back, as if the press had never been.
- The metronome's tempo and beat have one owner, `MetronomeSettings` (`domain/settings`): read its
  `config`, change it through `update`, and never keep a copy or go to `SettingsStore` for them.
  `FluteViewModel` and `MetronomeViewModel` never reference each other and no screen is handed
  another tab's ViewModel: `openBook()` saves the book being left with the tempo as it is and then
  gives the owner the opened book's, and the metronome follows by itself.
- The old single tune is migrated by the `open_book` key being missing, never by the books being
  empty: a child who wipes all six must not get the old tune back (`SONGBOOK.md`).
- A note's length comes only from how long its key is held, in beats of the metronome's `Pulse`.
  The time between presses is never recorded. No record button, clear-all or length control (root
  CLAUDE.md, "Turned down").
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
  one value (`ListenRun.Rules`: a session records it, a replay moves it): add a rule there.
  `DESIGN.md`, "Each note held for its length". A miss never shows
  silence: the heard note, or the listening dots, stands under the step waited on.
- The room under the stage while it listens adds nothing while things go right: "Play B4" until the
  first note is judged, then silence; after a wrong note the wanted note's fingering over "Check
  your fingers" until the right one (the user's pick of 2026-11-10). Not the fingering under every
  note, and no fingering on a phone on its side: `DESIGN.md`, "The room under the stage".
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
  `finally`, so a key held through a turn ends its note. `staffPlace` and `keysPlace` are created in
  `FluteContent`'s writing and running branch, so the staff and the key rows keep their scroll through a turn,
  Play and Stop.
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
- Two levels, the child's choice (the user, 2026-11-16: "at first users probably want to have loose
  rules but once user learn one expect more strict rule"; spec
  `docs/superpowers/specs/2026-11-16-flute-listen-levels-design.md`, `DESIGN.md`, "Practice and Exam"):
  🎵 Practice / Vježba and 🎓 Exam / Ispit, the app's own `ModeChoice` under "Play B4" in the listening
  room, from a run's start until its first note is judged (`FluteUiState.choosesLevel`; on a phone on
  its side stacked in the column above Stop); never on the writing screen. A change before the first
  note starts the run again at once. Remembered (`SettingsStore.loadListenLevel`), Practice on a new
  install. Each level is a preset of `ListenRun.Rules` (`ListenLevel.rules`): Practice is today's
  numbers and `longSlack` 1.5; Exam `tuneCents` 15, `lengthSlack` 1, `longSlack` 1 and `wholeNote`. In
  both a note whose sound runs more than `longSlack` half beats past its written length gets ✋ and "✋
  Too long, let go sooner" at that moment, but never the tune's last note; in Exam a note's mark is
  final only when its sound ends, its pitch read over the whole note (a star may become an arrow and
  back). A mark that changes after it was given is `ListenRun.Step.changed` / `ListenFrame.changed`.
  Change the numbers only from recorded sessions (sweeps of `tuneCents`, `lengthSlack`, `longSlack`).
- Legato (the user, 2026-11-16: "legato is next thing child has learned and has notes with it"; spec
  `docs/superpowers/specs/2026-11-16-flute-legato-design.md`, `DESIGN.md`, "Legato"): the ⌒ key, left of 💤, joins the
  next note pressed to the last one (`TuneNote.joined`, stored as a third field `s`), one tap per pair; a tap while a
  key is held joins the held note and must not end it. A second tap, 💤, 😮‍💨, 🧽 (it cancels and erases nothing), Play,
  Listen to me, the book, the speed panel and leaving the tab cancel a waiting join; it is never saved. Faded with
  nothing to join from. A join to the same note is a tie: one sound in Play, one note to Listen to me, as is a slur to
  the same letter in another octave (`Joins.lastHeardWith`). Play has no gap before a joined note and glides into it.
  In both levels a note joined to another letter that starts after a gap or a stop at least `ListenRun.SLUR_STOP_DROP`
  deep gets ✂️ (`StepMark.TONGUED`) and "✂️ Join them, no tongue"; never after a wrong note; notes not joined are not
  checked for tonguing. The depth is measured under the quieter side of the stop (a softer joined note must not make a
  breath's dip a tongue's). An octave slur broken by a gap after its first note's own length: that note's own mark and
  ✂️ on the other, never ⏳ on both. Marks in order: ⏳/✋, ✂️, ⬇️/⬆️, faint ⭐, ⭐. Change the depth only from recorded sessions
  (sweeps of `slurStopDrop`; `slurCheck` turns it off).
- Sharps and flats (the user, 2026-11-16: "if legato requires additional button reduce space with notes make them
  scrollable in that area that way we can also add even more notes to the app"; spec
  `docs/superpowers/specs/2026-11-16-flute-more-notes-design.md`, `DESIGN.md`, "Sharps and flats"): the flute's notes
  are C4 to C6 in every semitone (`FluteNotes`, 25); the notes game keeps `StaffNotes`, the white notes, on purpose.
  The keys (the user's layout A): the top row stays (C6, ⌒ 💤 😮‍💨 🧽); under it four rows scroll as one, the C5
  octave's black keys, its white keys, the C4 octave's black keys, its white keys, opening at the bottom; rows left
  at their bottom stay there when their height changes (`KeysPlace`). A black key is as wide as a white key, centred
  on the gap between its two, in its letter's colour. Sizes (`KeysGeometry`): never a white key under 58 dp or a black
  one under 48, and spare height shows more rows before it grows keys, up to 58 on a phone upright, 72 on a zoomed tab
  upright, 88 on its side; the keys' least room is the top row and the C4 octave's two rows (199). A press on a key in
  the rows sounds at once; if the rows take the finger for a scroll within `SCROLL_DECIDE_MS` (150) the press is taken
  back (`TuneWriter.takeBack`, `flute.press_scrolled`): not the tune, the stored book, the marks of the last run, a
  waiting join or the rejoin window changes. Held longer it is a note and the rows stand still until every key is up
  (C6 and 💤 held count too); a press that ended another key's hold is never taken back. The rows have no stretch at
  their ends (`overscrollEffect = null`): while one relaxes the rows take every touch, and a key tapped right after a
  flick made no sound. Change the 150 only from a phone test with the child
  (`flute.press_scrolled`'s ms and dy). The staff: a black note on its letter's place with its ♯ or ♭, and a ♮ on a
  white note whose place carried a ♯ or ♭ earlier in the bar (`Bars`); a signed step is `ACCIDENTAL_ROOM` (10 dp)
  wider. Listen to me keeps exact semitones; a semitone slurred on a flute off its pitch is a step, read from the
  note heard sounding (`NoteOnsetTracker.Rules.stepFromSounding`); F fingered for F♯ on a flute 60 cents sharp reads
  as F♯ played low, ⬆️ (pitch alone cannot tell them apart): a known limit, pinned in `ListenRunTest` and left.
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
| Sharps and flats: the black keys, their rows and scroll, the signs on the staff | "Sharps and flats" |
| Your turn: the listening rules, the length rule and the walk, the loudness floor, the heard-note bubble, the advice, the room under the stage | "Your turn" |
| The ⌒ key, slurs and ties on the staff and in Play, ✂️ | "Legato" |
| The running staff, the walking animal, the marks row | "While the tune runs" |
| A phone on its side; a tablet | "Sideways", "Tablets" |
| Books and the shelf | `SONGBOOK.md` |

## Sessions (debug builds only)

A debug build records every "Your turn" run on the phone: the audio, a per-frame table and the
run's log lines. How to pull, replay, sweep and pin one is in the `sessions` skill
(`.claude/skills/sessions/SKILL.md`). The audio is a child's playing: it leaves the phone only over
the cable and never goes into git (the repository is public).

## Tests

- Rules, on the JVM with no dispatcher: `TuneWriterTest`, `ListenRunTest`, `PlayStepsTest`,
  `TuneTest`, `BarsTest`, `TuneCheckTest`, `SongbookTest`, `StaffGeometryTest`, `JoinsTest`, `FluteNotesTest`,
  `KeysGeometryTest`, and `NoteSpellingTest` in `domain/tuner`.
- `ListenPathTest`: synthetic flute sound through the real detector and `ListenRun` (the emulator
  has no microphone).
- `FluteViewModelTest`: the wiring, the saving and the log lines.
- On the emulator: `FluteScreenTest`, `FluteSidewaysTest`, `FluteTabletTest`.
