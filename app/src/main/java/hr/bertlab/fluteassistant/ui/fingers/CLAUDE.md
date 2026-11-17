# The Fingers tab (fingering chart)

Added on 2026-11-17 at a teacher's ask: a fingering chart for every note, in a tab of its own, with the keys of a
concert flute marked as a teacher would accept. The user picked every look from true-size mockups (spec
`docs/superpowers/specs/2026-11-17-fingers-tab-design.md`, its Approaches; the mockups and `gen.py` in
`.superpowers/brainstorm/23054-1794934732/`, local).

## What it shows

- The chosen note in every octave the flute plays (`domain/fingers/FingerChart.notesOf`: four for C, three for the
  rest, c1 to c4), low to high. Each row: the note on a small staff (`NoteOnStaff`), its name in a pill, and the
  flute (`ui/common/FingeringDiagram`, the one the tuner and Listen to me show), with "Left hand" over it and "Right
  hand" under it upright. The hand names are the row's own text in dp, never drawn into the picture, so a 360 dp
  phone or large text cannot push them into the keys. Each is centred on its hand's keys, a place along the flute in
  the picture's units (100 and 250 of 340, the mockup's), so it follows them when a tablet's row is wider; "Left hand"
  stays right of the pill (`NoteRow`'s `FluteWithHands`, which measures its lines and estimates none).
- The flute fills the row's width, but a row is never taller than a quarter of the room the keys leave: on a wide and
  short upright tab (a foldable's inner screen) the flute stops there and the staff and the flute stand in the middle
  of the card, so four rows still open on the screen. A row is never under 108 dp: shorter rooms scroll.
- Seven letter keys and ♯♭ under the rows upright, two by two in the right column on a phone on its side
  (`FingersSideways`, where the hands are not named; on a wider phone a row's name, staff and flute stand in the
  middle of the card). The keys never scroll on a phone; the rows scroll on a screen too short for them. On its side
  the keys are 58 to 72 dp tall on every phone; a window shorter than any phone (a pane of a split screen, under
  about 312 dp) shrinks them to 46 at the least, and under about 264 their column scrolls, as the flute's keys do.
  The small staff counts places as every staff in the app does (`NoteSpelling.placeOf`: c1 = 0) and draws its ledger
  lines with `StaffNotes.ledgerLinesFor`.

## Rules that must not break

- Nothing else on the tab: no sound, no legend, no explaining view, no switch, no number, nothing stored. It opens
  on C; the view model keeps the note while the app is open (Activity-scoped, as every tab).
- A tap on a row's flute opens its note in the tuner's Tune mode (since 2026-11-18, the user's ask: "when user click
  on a fingerings it opens that tone in tuner tab tune view, and when user go back to fingerings same tone should be
  selected"). The flute is the button (`NoteFlute`, `fingers_tune` for a screen reader), not the staff or the pill; it
  acts on a tap, not as the finger goes down, so a slide that scrolls the rows opens nothing. The tab only calls
  `onTune`; `MainScreen` opens the tuner (`../CLAUDE.md`), and the phone's Back there comes back here (the user, the
  same evening: "go back from tuner to fingering when user land on tuner from fingering"; `BackToTab`). Leaving drops
  only a waiting ♯♭, so the way back finds the same note, C♯ included (`FingerPickTest`).
- ♯♭ works as on the flute tab (`domain/fingers/FingerPick`): one tap waits, the next letter shows its black note in
  the app's one spelling (C♯ E♭ F♯ G♯ B♭, `NoteSpelling.blackOf`, the rule the flute's key reads too) and spends it,
  D and A are faded and off while it waits, a second tap cancels it, leaving the tab drops it (`MainScreen`'s
  `onLeaveTab` calls `leave()`). The chosen note's key is down in its ring and names that note (C♯ while C♯ is on
  the screen); a tap on it shows the white note again. Every key acts as the finger goes down, as on the flute tab.
- The keys are buttons, the chosen one heard as selected and the others with no "not selected", and no radio group:
  a selected radio button cannot be pressed again with a screen reader, and C♯ back to C needs exactly that (the
  final review, 2026-11-17).
- A row is a quarter of the card on its side whatever the note, so three rows look as four would.
- A key is `ui/common/KeyLook.kt`'s, the flute's own (`TapKey`, which a letter key is, `SharpFlatKey`, `KeyName`):
  there is one key, so change it for both tabs. Do not build a key here again: the two copies had already drifted
  (this tab's ♯♭ waited for the finger to lift; the review of 2026-11-17). Both layouts lay the same eight out
  through `FingerKey`; which key is in the ring is `FingerPick.isChosen`, and a row's size comes from
  `FingerChart.MOST_NOTES`.
- This tab imports no other tab's `ui` package.

## Log

Tag `FA.Fingers`: `fingers.note` at `d` (pitchClass 0 to 11, notes: how many rows) when the note on the screen changes.
A tap on a flute logs on the tuner's side (`tuner.mode`, `tuner.target`, and `tuner.guide` with `via` `fingers` when
it closed the guide).

## Tests

`FingerChartTest`, `FingerPickTest` and `NoteSpellingTest` (the rules, JVM); `FingersViewModelTest`;
`FingersScreenTest`, `FingersSidewaysTest` and the two Fingers cases of `TabletFitTest` (screen tests, JVM and
emulator); `FingeringDiagramTest` and `FingeringGeometryTest` for the picture.
