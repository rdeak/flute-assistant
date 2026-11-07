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

The notes tab has one hero row (the streak as up to five flames, sized in dp as pictures, since five at a larger text size would not fit their pill, "Find" over the note's bubble, and
a "Pick" button with a dot per chosen letter) over a stage with the staff and one feedback line, and
ten round places at the bottom (a star for first time, a faint star for a retry, yellow for the
current note); "Pick" swaps the staff for the picker in place (no dialog): the fifteen notes in
octave rows, highest on top, the "Sound" switch row and a big OK, the only way back. The
end-of-round summary has no numbers on screen either: a trophy, ten stars, and tiles for the best
streak (flames), the aim (one to three stars) and the speed (an animal).

At large text the upright Pick button holds together like the sideways one (2026-11-07, the user's call, on
phones and tablets alike): the backpack is in dp, the word keeps 6 dp from the outline, and it shrinks to fit
the width and `PickWordHeight` (`autoSize`, floor 8 dp). Before, "Odaberi" broke inside the word from 1.5 x text
and the dots were cut. At normal text nothing moved. A test of the word's size must go by its width:
`layoutInput.style.fontSize` keeps the size the text was given, whatever `autoSize` chose.

## Sideways

Since 2026-11-05 (spec `docs/superpowers/specs/2026-11-05-landscape-design.md`, "Notes game"; what every tab
shares sideways is in `../CLAUDE.md`). `NotesContent` keeps `choosingNotes` above the branch, so the picker
stays open when the phone turns, and then, when `LocalSideways`, hands over to `NotesSideways`
(`NotesSideways.kt`). The phone is in the hand here, not on a stand. One layout, three screens:

- **Playing.** From the left: the rail; a 124 dp panel on the page with "Find", the note's bubble (108 dp),
  the streak and the feedback line (each in a fixed slot, so nothing moves when the first flame or a line
  comes); the staff's card (padding 10, not 16: the staff needs the height); and the right column with
  the ten round places as two columns of five at the top and Pick low. The note stays in the middle of the
  staff, leaving the right side for the sliding finger, and the card stays clear of the 30 dp swipe-back zones
  at both screen edges.
- **`NoteStaff` is unchanged**, `END_MARGIN_PLACES` included, and so is the touch, slide and accessibility
  behaviour: it takes the card's full height and a place is the staff's height over the visible places plus
  two. That gives these places (the mockup in `hand.html` draws no margin places and leaves out c1, so its
  bands are taller; these are what is built, and `NotesSidewaysTest` pins them):

  | Window | Default notes | All fifteen |
  |---|---|---|
  | Sideways, 411 tall | 26 dp | 22 dp |
  | Sideways, 360 tall | 23 dp | 19 dp |
  | Upright today, 915 tall | 31 dp | 26 dp |
  | Upright at its 560 dp minimum | 21 dp | 18 dp |

  The tab never scrolls sideways at 360 dp tall or more, because scrolling would fight the slide. Whether a
  finger can slide and let go at the 19 to 22 dp places is the one thing only a real phone can say; it is the
  worst case the spec names.
- **Which notes?** The title and the same rows as upright in the card, with bigger keys (78 x 62, shrinking to
  fit: about 72 at 800 wide, 48 at 640). Sound at the top of the right column, OK low. The ten places hide here.
- **End of the round.** The trophy hopping on the left of the card beside "Great job!", one row of ten stars
  and the three tiles (92 tall); Pick at the top of the column, Again low. Still no numbers. The star and trophy
  sizes are measured from the phone's own emoji font (`emojiWidthShare`), not guessed, so the row fills the room
  the trophy leaves without leaving the card.
- Pick has its letter dots on both the question and the summary, so it is one button on both; the summary
  mockup drew it without. The dots are 11 dp; seven of them (all fifteen notes) shrink to 9 on the 100 dp
  column, so that the row keeps 9 dp from the button's outline (`SidewaysGeometry.pickDotSize`).
- For an adb or `uiautomator` driven check: the bounds of the "Staff position N" nodes are not the drawn
  bands (the lowest and the top one carry the staff's margins and are taller, upright too), so a tap at a node's centre can land in the
  next place, and at 360 dp tall the e1 place sat on the line between two nodes and was never hit. Tap the
  drawn bands instead: the nodes' union, cut into the visible places plus two (the margins), one tap per band.

## Tablets

Simply the phone layouts, zoomed (`../CLAUDE.md`, "Tablets"), with nothing of their own.
