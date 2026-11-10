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
- "Your turn" is opt-in, child-paced and score-free: pitch only, nothing timed, no number, marks
  not saved. A miss never shows silence: the heard note, or the listening dots, stands under the
  step waited on.
- The engine's frames are buffered, never conflated: `NoteOnsetTracker` counts frames.
- A turn of the phone stops nothing (`../CLAUDE.md`, "Rules"). `FluteKey` and `RestKey` release in a
  `finally`, so a key held through a turn ends its note. `staffPlace` is created in `FluteContent`'s
  writing and running branch, so the staff keeps its scroll through a turn, Play and Stop.
- Text and emoji on the staff are sized in dp, so they stay in the staff at any text size.
- Log event names under `FA.Flute` are stable. They are listed in `FluteViewModel`'s comment, and
  with what each means in `DESIGN.md` ("Log events").

## Read first (`DESIGN.md`)

| Before changing | Read |
|---|---|
| Lengths, the pulse, the stored string, bars and ties | the first paragraph |
| The staff's drawing, the names row, its sizes | the second paragraph ("The stage") |
| Key presses, the rejoin window, the rest key, Play | the same paragraph, from "`FluteViewModel` (Activity-scoped" |
| The keys; the speed knob and its panel | "The keys", "The speed knob and its panel" |
| Your turn: the listening rules, the heard-note bubble, the advice | "Your turn" |
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
