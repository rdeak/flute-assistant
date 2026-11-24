# The Fingers tab (fingering chart)

Added on 2026-11-17 at a teacher's ask: a fingering chart for every note, in a tab of its own, with the keys of a
concert flute marked as a teacher would accept. The user picked every look from true-size mockups (spec
`docs/superpowers/specs/2026-11-17-fingers-tab-design.md`, its Approaches; the mockups and `gen.py` in
`.superpowers/brainstorm/23054-1794934732/`, local). On 2026-11-24 the user asked that it "actually show glyphs and
fingering diagram": the note in Bravura's music symbols and a chart as a flute book prints it in place of the whole
flute, the user's pick A (spec `docs/superpowers/specs/2026-11-24-fingers-music-symbols-and-book-chart-design.md`;
mockups `.superpowers/brainstorm/93282-1795524238/`, local).

## What it shows

- The chosen note in every octave the flute plays (`domain/fingers/FingerChart.notesOf`: four for C, three for the
  rest, c1 to c4), low to high. Each row upright: the note on a small staff in Bravura's symbols (`NoteOnStaff`, its
  sizes in `SymbolStaff`), and beside it the note's name in a pill over the chart (`ui/common/FingeringDiagram`, the
  one the tuner and Listen to me show), with two brackets under the chart and "Left hand" and "Right hand" under them
  (`NoteRow`'s `ChartWithHands`, which measures its lines and estimates none). The hand names are the row's own text
  in dp, never drawn into the picture, so a 360 dp phone or large text cannot push them into the keys. Each is centred
  on its bracket and kept inside it, a span along the chart in the picture's units (`FingeringGeometry.LEFT_HAND_SPAN`,
  8 to 160 of 340, and `RIGHT_HAND_SPAN`, 168 to 334), so it follows its keys when a tablet's row is wider.
- The staff is drawn from a cut of Bravura renamed "Flute Assistant Music" (`res/font/flute_music.otf`, made by
  `tools/cut-music-font.py`; OFL, `NOTICE`; the symbols and their measured sizes in `ui/common/MusicSymbols`, which the
  flute tab's staff reads too since the same day): the clef, the whole note (its hollow in the letter's colour), the sharp
  and the flat, all in the theme's ink, sized in dp; a sign stands left of its note's ledger lines. It asks for an 8.2 dp
  line gap and room for C♯4's sharp and B♭6's flat (108 dp tall, `SymbolStaff.TALL`) and gives way on a short tab, where
  a sign may reach 1 dp into the row's 5 dp padding.
- The chart fills the row's width, but a row is never taller than a quarter of the room the keys leave: on a wide and
  short upright tab (a foldable's inner screen) the chart stops there and the staff and the chart stand in the middle
  of the card, so four rows still open on the screen. A row is never under 108 dp: shorter rooms scroll.
- Seven letter keys and ♯♭ under the rows upright, two by two in the right column on a phone on its side
  (`FingersSideways`, where the hands are not named; on a wider phone a row's name, staff and flute stand in the
  middle of the card). The keys never scroll on a phone; the rows scroll on a screen too short for them. On its side
  the keys are 58 to 72 dp tall on every phone; a window shorter than any phone (a pane of a split screen, under
  about 312 dp) shrinks them to 46 at the least, and under about 264 their column scrolls, as the flute's keys do.
  The small staff counts places as every staff in the app does (`NoteSpelling.placeOf`: c1 = 0) and draws its ledger
  lines with `StaffNotes.ledgerLinesFor`. The flute tab's staff draws from the same font (since the same day), and so does
  the notes game's (its faint clef and filled heads, `ui/notes/NoteStaffSymbols`, the same evening).

## Rules that must not break

- Nothing else on the tab: no sound, no legend, no explaining view, no switch, no number, nothing stored. It opens
  on C; the view model keeps the note while the app is open (Activity-scoped, as every tab).
- A tap on a row opens its note in the tuner's Tune mode (since 2026-11-18, the user's ask: "when user click on a
  fingerings it opens that tone in tuner tab tune view, and when user go back to fingerings same tone should be
  selected"). The whole row is the button since 2026-11-24 (the user: "make whole white rectangle as link for opening
  tuner tune view"; until then only the flute was): upright the white card, on its side the row's strip of the card
  (`NoteRow`'s `tuneOnTap`, `fingers_tune` for a screen reader, which hears the row's pill, names and the chart's
  description as one). It acts on a tap, not as the finger goes down, so a slide that scrolls the rows opens nothing.
  A test that measures the chart, the pill or a name inside a row reads the unmerged tree (`useUnmergedTree = true`). The tab only calls
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
emulator); `FingeringDiagramTest` and `FingeringGeometryTest` for the chart; `SymbolStaffTest` (JVM) and
`NoteOnStaffTest` (on a device the night ink, the text size, the sharp, the flat and the whole note's hollow) for the
staff; `ui/common/MusicSymbolsTest` for the font's fourteen symbols and their measured sizes.
