# Note reading path

Loaded when working in this folder; moved here from the root CLAUDE.md on 2026-11-02 so it is read only when needed.

`StaffNotes` (`domain/notes/`) is the pool: the naturals c1 to c3 (MIDI 60 to 84), fifteen
notes whose index in `ALL` is their place on the treble staff (0 = the ledger line below,
`LINE_POSITIONS` 2, 4, 6, 8, 10 are the lines, 14 = the second ledger line above);
`ledgerLinesFor` and `sanitize` (unknown or empty stored sets fall back to `DEFAULT_PRACTICE`,
c1 to g1) live there too. `NotesViewModel` (Activity-scoped; spec:
`docs/superpowers/specs/2026-10-22-note-reading-game-design.md`) runs one round of
`NotesUiState.ROUND_LENGTH` (10) notes: `onNoteTapped(midi)` judges an answer (ignored while
`solved` or `roundOver`), a right tap sets `solved`, appends to `results` (true = first tap),
plays the note through the tuner's `ReferenceTonePlayer` at `REFERENCE_TONE_VOLUME` and after
`FEEDBACK_MS` (1200) draws the next note (never the same twice in a row unless only one is
chosen) or ends the round; a wrong tap joins `wrongTaps`, becomes `lastWrongTap` (the cross that
gets the arrow) and zeroes the streak. `taps` counts
every tap so `accuracyPercent` (right taps over all taps) differs from `score`; `roundMillis`
is the time from the round's first tap to the tenth note found (`MonotonicClock`, so tests use
`FakeClock`), shown in the summary only as one of the metronome's animals (`NotesUiState.speed`,
the time per note: cheetah up to 2 s, rabbit 3.5 s, duck 6 s, turtle 10 s, then the snail), as the
accuracy is only one to three stars (`aimStars`: 90 % and 70 %). The chosen
notes persist through `SettingsStore.savePracticeNotes` (`practice_notes`, a string set), the last one cannot be switched off, and any change to them starts a fresh round; the sound of
a found note can be switched off (`toggleSound`, `SettingsStore.saveNoteSound`, a switch row
under the picker's rows); `resetRound()` (also behind `playAgain`) throws the round away and is
called by the tab bar when the tab is left, and `stopSound()` when the screen is disposed, so
the tuner's microphone does not hear the tone. A `Random` is passed through an internal
secondary constructor for deterministic tests. `NoteStaff` draws the staff on a Canvas
(it takes all the height left in the stage and sizes the places from it; it shows the staff
and its two neighbouring spaces, d1 to g2, plus the ledger-line places c1, a2, h2, c3 only as
far as a chosen note needs them, `StaffNotes.visiblePositions`, their ledger lines dashed; every
place a full-width band, the spaces tinted, the lines thick; a small, faint hand-built clef whose
curl sits on the G line; one note column in the middle of the width, clear of a right-handed
finger sliding on the right, after the user rejected a two-column split). The child answers by touch and slide: a finger down shows a see-through note
on the nearest place and turns its band yellow (`placing`, hoisted to `NotesContent` for the
"Slide, let go" line), it follows the finger with a haptic tick per place, lifting it answers,
sliding off the staff cancels. A miss is a cross inside its own place (`CROSS_RADIUS` 0.45 of the
pitch, so it never hides the place next to it), the latest with a ⬆️/⬇️ arrow towards the right
place; the found note is a head in its letter's colour with sparkles. Over the canvas sits a
column of fifteen full-width places that take no touches but are buttons for accessibility,
named "Staff position N" counted from the bottom, never the note, so a screen reader does not
give the answer away; their semantic click answers at once.

## The tab on screen

Moved here from the root CLAUDE.md on 2026-11-05.

The notes tab has one hero row (the streak as up to five flames, "Find" over the note's bubble, and
a "Pick" button with a dot per chosen letter) over a stage with the staff and one feedback line, and
ten round places at the bottom (a star for first time, a faint star for a retry, yellow for the
current note); "Pick" swaps the staff for the picker in place (no dialog): the fifteen notes in
octave rows, highest on top, the "Sound" switch row and a big OK, the only way back. The
end-of-round summary has no numbers on screen either: a trophy, ten stars, and tiles for the best
streak (flames), the aim (one to three stars) and the speed (an animal).
