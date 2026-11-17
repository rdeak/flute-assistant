# UI conventions

This file holds only what must not be broken. The frame, the look, the sideways layout and tablets,
with the reasons and who decided what, are in `DESIGN.md` beside it (not loaded by itself: read the
part named under "Read first" before you change that part). The rules every screen keeps are in the
root CLAUDE.md, "Kid-friendly UI constraint".

## How a screen is built

- One Activity. `MainScreen` hosts the frame (upright: `TitleBar` and `TabBar`; a phone on its
  side: `SideRail`) and the open tab. All ViewModels are Activity-scoped, so state and playback
  survive tab switches; the two exceptions are the notes round, which the tab bar resets when that
  tab is left, and a ♯♭ waiting on the Fingers tab, which leaving drops (`FingersViewModel.leave`).
  What a tab drops when it is left is one branch of `MainScreen`'s `onLeaveTab`, a `when` over every
  tab, so a new tab has to say.
- Each tab is a stateful `XScreen` (ViewModel, permissions, lifecycle) and a stateless `XContent`
  that takes the state and the callbacks; the flute, the notes tab and the tuner take theirs as one object
  (`FluteActions`, `NotesActions`, `TunerActions`). The `*Content` composables are what the UI tests render.
- A ViewModel holds no rules of its own: they are plain classes in `domain/` (root CLAUDE.md,
  "Architecture"), and the ViewModel gives them the clock, the sound, the storage and the log.
- Shared pieces are in `ui/common` and never import a tab's package; a tab hands them what they
  need (the flute gives `SidewaysGeometry.runningStaffScale` its staff's height).

## Rules

- **One call site for the tab.** `MainFrame` calls its `tab` slot in one place. Composing the tab
  in an `if (sideways) ... else ...` makes it leave the composition on a turn, and the flute then
  stops its tune (`MainFrameTest` guards it). For the same reason the credits' and the language
  list's open state lives in `MainScreen`.
- **A turn re-creates nothing.** `MainActivity`'s `configChanges` hold the orientation, size and
  keyboard flags and never the locale: the language switch relies on re-creation.
- Screens read `LocalSideways` and `LocalTabZoom`, never `LocalConfiguration` (the tests' hosts do
  not move it). State that must survive a turn sits above the sideways branch.
- **Tablets:** the tab zooms (`MainFrame` raises `LocalDensity` by `TabletGeometry.tabZoom`), the
  frame does not. Only the flute adjusts anything on top; keep the rest "the phone layout, zoomed".
- The day/night switch never goes through AppCompat's night mode, which would re-create the
  Activity and stop a playing tune: `MainScreen` wraps the frame in `FluteAssistantTheme` itself.
- Text on a bright fill is `InkOnFill`; a note's colour is `noteColor(midi)`. Everything pressable
  is a `ChunkyButton` / `Modifier.chunky`; a faded one goes through `fadedUnless` (`Modifier.alpha`
  cuts the shadow off flat). Emoji that are pictures are sized in dp, so large text does not grow
  them out of their room. The smallest a shrinking word gets is a size in dp too (`Dp.asTextSize()` and `WordFloor`,
  `ui/common/TextFloor.kt`): a floor in sp doubles at the largest text and pushes the word out.
- The credits stay behind a long press on the logo, with the website's address as plain text (never
  a link, so a child cannot leave the app), and since 2026-11-11 no privacy or licence lines (the
  user's call; Play asks for the policy in the app, so a review that flags it starts here, see
  `DESIGN.md`). Keep them: `NOTICE` section 7(b) needs the author credit and CC BY the robot's line.
  The one button that does leave the app, the microphone card's Settings, shows only once the phone
  will not ask again (`tuner/CLAUDE.md`, "Asking for the microphone").
- **The phone's Back** is the way back the screen itself shows, wherever one is open: OK on the
  notes picker and the flute's speed panel, a tap on the open book on the shelf, Not now on the
  flute's microphone card, Stop while a tune runs, ✕ or OK on the tuner's guide, and it closes an open
  tempo wheel first. On the tuner the Fingers tab's flute opened, it goes back to the Fingers tab (since 2026-11-18,
  the user's ask; `MainScreen`'s `BackToTab`, composed before the tab so the guide takes Back first) until a tab is
  chosen on the bar or the rail. With
  nothing open it is the phone's and leaves the app. A new panel gets a `BackHandler` and a test
  with `BackKey` (androidTest): `DESIGN.md`, "The phone's Back".
- `ValueWheel`'s content description stays on the `LazyColumn` node: the tests scroll it by it.
- `ModeChoice` is shared by the tuner's modes and the notes tab's games: change it for both (the flute's level used
  it from 2026-11-16 until the levels went, 2026-11-17).
  `FingeringDiagram` (the flute as the player sees it, its numbers in `FingeringGeometry`, since 2026-11-17) is
  shared by the tuner, the flute's listening room and the Fingers tab, and `AdviceLine` is the flute's (it also takes
  its "Play B4"; the tuner's one line took its advice on 2026-11-18): the same. A key (`KeyLook.kt`: its look, `KeyShape`, `KeyDepth`,
  `drawKeyRing`, `SharpFlatPicture`, and the key itself, `ChunkyKey`, with `TapKey`, `SharpFlatKey` and
  `KeyName`) and the staff's signs (`StaffSigns.kt`) are in `ui/common` since 2026-11-17, as the flute's keys and the
  Fingers tab's are the same keys. `ChunkyKey` draws and speaks; each key brings its own gesture in its modifier.
- **Five tabs.** The bar and the rail take their words' floor, their emoji and the word's growth with the text size
  in dp (one `TabLook` each: `TabBar`'s `BarLook`, `SideRail`'s `RailLook` and `RailLookShort`; `TabItem` turns it
  into text sizes): with five, a floor in sp cut "Metronome". `TabBarTest` and
  `SideRailTest` hold every word whole at 360 and 412 dp wide, on the 360 and 411 dp rails, at twice the text.
  A tab measures its own cell (`TabItem`): the word first, at its size, and the emoji in what is left, drawn smaller
  where that is too little, so the rail's emoji sizes are the most and not what a 360 dp rail shows (about 19 of the
  20). Do not work a cell's height out from the bar's paddings again, or guess a line's height: a line is as tall as
  its font makes it, not its `lineHeight` (an emoji's is about 1.37 times its size, where the rail counted 1.2).
- **One order** (the user, 2026-11-18: "idea is to make all tabs and views with same structure or order of
  elements"). Upright, a tab reads from the top: its choice pills, the line of words saying what to do, the buttons,
  then the card with the content. The tuner: Which?/Tune, its one line, then its card with the window and the flute
  (since the calmer Tune view of 2026-11-18 it has no buttons of its own: the window is Hear and the line opens the
  guide); the notes tab: Find/Hear, the feedback line, the hero row (flames, the note, Pick), the card with the staff. The one
  exception is the user's own: the flute's listening room keeps its line under the fingering (`flute/CLAUDE.md`). A
  new tab or view follows the order; sideways layouts keep their own.
- **A tab opens another only through `MainScreen`.** The Fingers tab's flute opens its note in the tuner
  (`onTuneNote`, since 2026-11-18): the Fingers tab gets a callback and knows no tuner, and `MainScreen` sets the
  tuner first and then shows it, and keeps the way back for the phone's Back (`TabRoute`, the open tab and its way
  back as one saved value, pinned by `TabRouteTest`; cleared by any tab
  chosen on the bar or the rail).
- **A test of a fit compares bounds.** `assertIsDisplayed` does not prove one: what spills past a
  window is still displayed. Compare `getUnclippedBoundsInRoot()` with the room, and ask
  `GetTextLayoutResult` about words (`assertWhole`, `ui/common/WholeText.kt` in androidTest). What a layer
  scales (a tab's emoji) keeps its full size in `getUnclippedBoundsInRoot()`: read `boundsInRoot` there.

## Sideways

A phone on its side (the window wider than tall and at least 600 dp wide,
`SidewaysGeometry.isSideways`). Each tab keeps the pieces it shows upright and arranges them in a
`*Sideways.kt` file; `SidewaysPage` is the shape they share (the rail, the card, a column on the
right). Its width goes to size and air, never to more things.

## Tablets

The shortest side at least 600 dp (`TabletGeometry.isTablet`). Judged as a Play Store screenshot:
the user owns no tablet, and no real one has seen the app.

## Read first (`DESIGN.md`)

| Before changing | Read |
|---|---|
| The top bar, the tab bar, the theme switch, the look, the credits | the list under the first heading |
| Anything on a phone on its side: the rail, `SidewaysPage`, system bars, a turn, its tests | "Sideways (a phone on its side)" |
| The zoom, the density, tablet tests | "Tablets" |
