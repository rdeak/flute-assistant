# Digital flute

This file holds only what must not be broken. How the tab works, why, and who decided what is in
`DESIGN.md` beside it (not loaded by itself: read the part named under "Read first" before you
change that part). The songbook is in `SONGBOOK.md`.

## Where things are

- `domain/flute/`: the rules, pure Kotlin with no clock and no coroutine. `Tune` (lengths, the
  stored string, `playSteps` for Play), `TuneWriter` (a key held makes a note: growth, the rejoin
  window, the cap, breath, erase), `Bars` (how the staff writes bars and ties), `TuneCheck` and
  `ListenRun` ("Your turn"), `Songbook`.
- `domain/listen/`: what the microphone's frames become (`NoteOnsetTracker`, `HearingTracker`,
  `ListenRules`), shared with the tuner (`../tuner/CLAUDE.md`).
- `FluteViewModel`: gives the rules the clock, the sound, the microphone, the storage and the log.
  `FluteUiState` has one part per way the tab can be: `held`, `playing`, `listening`.
- `FluteContent(state, metronome, actions)` builds the tune, the keys and the Play row once, and
  `FluteSideways.kt` only says where they stand. `TuneStaff` draws from `StaffGeometry` (plain dp).
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
  `finally`, so a key held through a turn ends its note. `staffPlace` is created in `FluteContent`'s
  writing and running branch, so the staff keeps its scroll through a turn, Play and Stop.
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
  or a repeat the phone could not hear), ⬇️, ⬆️, ⏳; a sign says more than the faint star. Change the
  numbers only from recorded sessions (sweeps of `tuneCents`, `highCents`). This overturned exact
  semitones (2026-11-13) and "a note cut short is played again" (2026-11-11).
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
| Your turn: the listening rules, the length rule and the walk, the loudness floor, the heard-note bubble, the advice, the room under the stage | "Your turn" |
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
  `TuneTest`, `BarsTest`, `TuneCheckTest`, `SongbookTest`, `StaffGeometryTest`.
- `ListenPathTest`: synthetic flute sound through the real detector and `ListenRun` (the emulator
  has no microphone).
- `FluteViewModelTest`: the wiring, the saving and the log lines.
- On the emulator: `FluteScreenTest`, `FluteSidewaysTest`, `FluteTabletTest`.
