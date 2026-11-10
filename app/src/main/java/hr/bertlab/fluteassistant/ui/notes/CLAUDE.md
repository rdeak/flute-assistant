# Note reading game

This file holds only what must not be broken. How the tab works, why, and who decided what is in
`DESIGN.md` beside it (not loaded by itself: read the section named under "Read first" before you
change that part).

## Where things are

- `domain/notes/`: pure Kotlin. `StaffNotes` (the fifteen notes and their places on the staff),
  `NotesRound` (one round of either game and its rules, `open`, `tapped` and `next`; it is also the
  tab's state), `Phrases` (Hear's phrases and their staircase), `NoteProgress` (the sticker book),
  `NotesGame`.
- `NotesViewModel`: keeps the current round and does what a round cannot: the sound, the waits, the
  storage and the log.
- `NotesContent(state, actions)`; `NotesSideways.kt` arranges the same pieces for a phone on its
  side. `NoteStaff(state, ...)` is the one staff both draw. `PracticeNotePicker`, `RoundSummary`,
  `RoundProgress`, `Celebrations`.

## Rules

- No numbers on screen: the summary is a trophy, stars, flames and an animal, a sticker shows no
  count, and a screen reader hears none either.
- For a screen reader the staff's places are named by position ("Staff position N"), never by
  note, so it does not give the answer away.
- The two game pills stay on all three screens (asking, choosing notes, the summary). Do not move
  them off the other two to win back height.
- Tapping the chosen pill does nothing. Choosing the other game or switching a note in the picker
  starts a fresh round; opening Pick starts nothing; leaving the tab resets the round and keeps the
  game.
- A cancelled touch answers nothing (`change.isConsumed`, the gesture's `finally`).
- A Hear phrase is asked for only while the staff is on screen: `NotesContent` calls
  `onPhraseShown` above the sideways branch, and the view model plays each `phraseId` once.
  `stopSound()` means "the screen went away".
- The sticker book: a first-try find adds one, only the first miss for an answered note takes one,
  and a known note (8) loses nothing. Nothing resets the book.
- Stored by name, never rename: the `NotesGame` entries and the keys `notes_game`, `note_progress`
  and `practice_notes`.
- Sideways the tab never scrolls at 360 dp tall or more: scrolling would fight the slide.
- Whatever holds a `NoteKey` must not clip it: a known note's star hangs outside the outline.
- Turned down, do not add: a letter bubble for Hear's given note, numbers under the slots, dropping
  the given note, a tutorial overlay (2026-11-09); a sticker album screen, a path across rounds,
  auto-unlocking notes, daily streaks, mixing Find and Hear in one round (2026-11-07); a two-column
  split of Find's staff.
- "As built and not ruled" and "Known limitations" in `DESIGN.md` were seen and left: do not fix
  them without asking.
- Log event names under `FA.Notes` are stable: `notes.phrase`, `notes.round`, `notes.known`
  (`DESIGN.md`, "Logging").

## Read first (`DESIGN.md`)

| Before changing | Read |
|---|---|
| Find: the staff, the touch and slide, the round | the first section |
| What the tab shows, the hero row, text sizes | "The tab on screen" |
| The pills, switching games | "The two games" |
| Hear: phrases, the staircase, the slots, the pace, who starts a phrase | "Hear" |
| Progress per note, the picker's keys, the summary's keys and its fit | "The sticker book" |
| The burst, the perfect round | "Celebrations" |
| A phone on its side; a tablet | "Sideways", "Tablets" |
| Strings | "Strings" |

## Tests

- Rules, on the JVM with no dispatcher: `NotesRoundTest`, `PhrasesTest`, `NoteProgressTest`,
  `StaffNotesTest`, `NoteStaffColumnsTest`, `LetterDotsTest`, `RoundSummaryFitTest`.
- `NotesViewModelTest`: the sound, the waits, the saving and the log lines.
- On the emulator: `NotesScreenTest`, `NotesSidewaysTest` (what each pins is listed in
  `DESIGN.md`, "Tests").
