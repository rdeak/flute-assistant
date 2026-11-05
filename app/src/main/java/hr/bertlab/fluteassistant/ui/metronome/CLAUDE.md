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
the ring's state; tap the figure for the in-place `TempoWheel`; there are no tempo −/+ buttons: the
user turned them down again on 2026-10-24); then one Play / Stop pill (no volume control: the
phone's buttons set loudness).
