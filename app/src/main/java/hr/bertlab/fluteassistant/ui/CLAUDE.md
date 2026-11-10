# UI conventions

This file holds only what must not be broken. The frame, the look, the sideways layout and tablets,
with the reasons and who decided what, are in `DESIGN.md` beside it (not loaded by itself: read the
part named under "Read first" before you change that part). The rules every screen keeps are in the
root CLAUDE.md, "Kid-friendly UI constraint".

## How a screen is built

- One Activity. `MainScreen` hosts the frame (upright: `TitleBar` and `TabBar`; a phone on its
  side: `SideRail`) and the open tab. All ViewModels are Activity-scoped, so state and playback
  survive tab switches; the one exception is the notes round, which the tab bar resets when that
  tab is left.
- Each tab is a stateful `XScreen` (ViewModel, permissions, lifecycle) and a stateless `XContent`
  that takes the state and the callbacks; the flute and the notes tab take theirs as one object
  (`FluteActions`, `NotesActions`). The `*Content` composables are what the UI tests render.
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
  them out of their room.
- The credits stay behind a long press on the logo, with plain-text addresses (never links, so a
  child cannot leave the app). Keep them: `NOTICE` section 7(b) and Play both need them in the app.
- `ValueWheel`'s content description stays on the `LazyColumn` node: the tests scroll it by it.
- `ModeChoice` is shared by the tuner's modes and the notes tab's games: change it for both.
- **A test of a fit compares bounds.** `assertIsDisplayed` does not prove one: what spills past a
  window is still displayed. Compare `getUnclippedBoundsInRoot()` with the room, and ask
  `GetTextLayoutResult` about words (`TabletFitTest.assertWhole`).

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
