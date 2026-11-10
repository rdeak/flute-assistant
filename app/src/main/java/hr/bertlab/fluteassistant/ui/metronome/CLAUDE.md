How the metronome's audio path and player contract works, with the reasons behind it, is in `../../audio/metronome/CLAUDE.md`; read it before changing anything here.

The tab's stage (the beat bubbles over the tempo ring) is `MetronomeStage`, shared with the flute tab's speed panel (2026-11-03): its caller owns the `TempoWheelState` and puts `closesTempoWheel` on its column, and `MetronomeMinHeight` is the height below which the metronome tab and the flute's speed panel scroll; `ui/common/BeatFlash.kt` (`rememberBeatFlash`, `hopOnBeat`) is the one hop shared by the ring's knob, the flute's walking animal and the bottom bar's 🥁.

## The tab on screen

Moved here from the root CLAUDE.md on 2026-11-05.

The metronome is one stage with the beat bubbles (the first bigger; the sounding one yellow with the
speed's animal hopping on it) between a − and a + that step the bar from two to six beats
(`fewerBeats` / `moreBeats` over 2/4, 3/4, 4/4, 5/4, 6/8; the other signatures are not offered), and
the tempo ring from 40 to 200: five animal bands (`TempoBand`: snail 40, turtle 60, duck 80, rabbit
100, cheetah 130), each a fifth of the ring and every animal turned to face right
(`facingRight(emoji)` in `ui/common`, also used for the summary's speed animal: Google's and Apple's
emoji sets draw all five facing left, so each is mirrored; Samsung's draws the snail already facing
right, so on a Samsung phone the snail is not), a yellow knob with the current animal that is
dragged round, a tap on an animal jumps to its band (the rabbit again to 120), and the tempo figure
alone in the middle (the band's name above it was dropped on 2026-10-24; a screen reader hears it as
the ring's state; tap the figure for the in-place `TempoWheel`, which a touch outside it or the phone's Back closes; there are no tempo −/+ buttons: the
user turned them down again on 2026-10-24); then one Play / Stop pill (no volume control: the
phone's buttons set loudness).

## Sideways

Since 2026-11-05 (spec `docs/superpowers/specs/2026-11-05-landscape-design.md`, "Metronome"; what every
tab shares sideways is in `../DESIGN.md`). `MetronomeContent` keeps `rememberTempoWheelState()` first and
then, when `LocalSideways`, hands over to `MetronomeSideways` (`MetronomeSideways.kt`) with the same
`closesTempoWheel` modifier; the upright body below it is untouched. The user's pick of two (B): the beat
takes over while it plays.

- **At rest** (`MetronomeStageSideways`): the beats on the left over their − and +, the ring on the right, the
  order they read upright; Play low in the right column. The ring is `SidewaysGeometry.ringSize`, at most 304
  dp, and also at most the card's inside height and half its inside width, so a small phone shrinks it and
  leaves the bubbles their room (they may fall below upright's 52 dp at the 640 floor: six beats are about 23
  dp there). The bubbles are up to 68 dp (animal 36 sp). The − and + are 56 dp, 28 apart, centred under the
  bubbles, not beside them, because beside them the bubbles would shrink below their upright 52. The
  flute's speed panel shows this same composable with OK in Play's place, so it takes the caller's
  `TempoWheelState`.
- **While it plays** (`MetronomeTakeover`): the ring and the − and + go, and the bubbles grow to fill the card:
  144 dp with four beats and 97 with six at 915 wide, the first a little bigger as upright (`bigBeatUnit`, up
  to 150, gaps 16 and 10); the animal hops on the sounding bubble at 58 sp in a 72 dp room. The speed stays
  as the figure alone in a 104 dp round well (`FIGURE_WELL_TAG`; 100 on the short phones' narrower column) above Stop, and pressing it does nothing:
  to change the speed the child presses Stop first (the user accepted that). Stop puts the ring back. The
  two states are two whole cards swapped at once, with no fade: two translucent white cards fading over
  each other let the page show through at the midpoint. A `LaunchedEffect` closes an open wheel when
  play starts, since a start from elsewhere (the drum in the rail) is not a touch on Play and Stop would
  bring the ring back with the wheel still open.
- **Why `BeatBubbles` has `stableRow`.** It is `internal` now, with `maxUnit`, `gapFew`, `gapMany`,
  `animalFont`, `animalHeight` and `stableRow` (defaults are upright's), and `BeatStepButton` takes a
  `size`. Both sideways layouts pass `stableRow = true`, because the sounding column is as wide as the animal
  when the animal is wider than its bubble: at 640 x 360 with six beats playing (56 dp bubbles, a 58 sp animal
  of about 68 dp) the whole row reflowed on every beat. With the flag the animal's box is the bubble's width and
  the emoji overflows into the gap; upright's animal fits its bubble, so it does not ask for it. The row divides
  by the sum of the bubbles' real sizes in every layout: counting only the first as bigger left 6/8's fourth
  beat, a medium accent (1.08), 0.08 of a bubble too wide, and the last bubble was drawn as an oval, upright too
  until the user asked for it to go on 2026-11-06. The column's height follows the drawn bubble
  (`animalHeight + 6 + max(unit, 52) * 1.16 + 5.68`, which is exactly upright's 112 at the defaults), so a big bubble
  is not left 40 dp below the card's middle on a small phone.
- `SidewaysGeometry.bigBeatUnit` counts only the first bubble as bigger, so it is about 1.3 percent over what
  6/8 draws; the tests allow for it.

