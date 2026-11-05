# UI conventions

Loaded when working under `ui/`; moved here from the root CLAUDE.md on 2026-11-05 so it is read only
when needed. Each tab's own screen is in its folder's CLAUDE.md; the rules every screen keeps are in
the root CLAUDE.md, "Kid-friendly UI constraint". User-visible strings: the root CLAUDE.md too.

The top bar shows the app icon and names the open tab; the tab bar is four emoji-and-word buttons.

- One Activity, which sets `volumeControlStream` to the media stream so the volume keys adjust
  the app's sound even while nothing is playing. `MainScreen` hosts a top bar (the app icon, `AppLogo`, and the open tab's name plus the `LanguageSwitch`: two flag
  buttons over `ui/common/AppLanguage`; selecting one calls
  `AppCompatDelegate.setApplicationLocales`, which re-creates the Activity) and a bottom bar
  switching between the four tabs (`AppTab`: flute, metronome, tuner, notes; since 2026-11-04 the
  flute is first and the app opens on it, the first entry always being the start tab, and the drum
  sits beside it as the tab the speed knob opens; before that the order was just the build order,
  never a decision; Play screenshots follow the same order). All ViewModels are
  Activity-scoped so state and playback survive tab switches, rotation and that re-creation;
  the one exception is the game's round, which the tab bar resets (`NotesViewModel.resetRound`)
  when the notes tab is left, so the child always comes back to a zero score. `MainScreen` also
  keeps the screen on (`ui/common/KeepScreenOn`) while the metronome plays, the tuner listens or
  the flute's tune runs (Play, or "Your turn" listening). While the metronome plays, the bottom bar's 🥁 hops on every beat
  on every tab (since 2026-11-03: `TabBar` with `rememberBeatFlash` / `hopOnBeat` from
  `ui/common/BeatFlash.kt`, the same hop as the ring's animal), so a running drum is visible
  everywhere and one tap on it reaches Stop; a screen reader hears that tab as "Playing"
  (`tab_metronome_playing`).
- Each screen is split into a stateful `MetronomeScreen` / `TunerScreen` / `NotesScreen` /
  `FluteScreen` (ViewModel, permissions, lifecycle) and a stateless `MetronomeContent` /
  `TunerContent` / `NotesContent` / `FluteContent` taking state plus callbacks (`FluteScreen` is also handed the metronome's state and
  ViewModel, for the flute's speed knob, its panel and the book switch). The `*Content` composables are what the Compose
  UI tests render, wrapped in the test-only `LocaleProvider` to pin the language.
- The look comes from the Claude Design canvas (see `.superpowers/design/flute-assistant-screens/`):
  white cards (`Stage`) on sky blue, or navy in dark mode, with dynamic colour off; the tokens that
  change with the theme are `FluteColors` (read through `FluteTheme.colors`), the bright fills that
  never change are `Sun` (yellow = chosen), `Go` (the Sound switch), `Stop` and `NoteLetterColors`, one per letter,
  which `noteColor(midi)` applies wherever a note appears (a sharp takes the letter below). Text on a
  bright fill is always `InkOnFill`. Everything pressable is a `ChunkyButton` / `Modifier.chunky`
  (thick ink outline, solid shadow, sinks 3 dp when pressed; a faded one goes through `fadedUnless`, as `Modifier.alpha` cuts the shadow off flat); big actions are `BigActionButton`,
  the one place with white on colour: a drawn icon and the word in `OnAction` on `ActionGo` (deep
  green: Play, OK, Again, Yes), `ActionStop` (deep red: Stop) or `ActionListen` (deep blue: the
  flute's Your turn), for contrast (canvas, 2026-10-24); the flute's Play and Your turn stack the
  icon over the word (`stacked`), since two pills with the word beside the icon do not fit a phone.
  The font is Nunito (three static weights in `res/font`, licence in `docs/licenses/`); the canvas
  used Fredoka, which has no č, ć or đ. Icons are emoji (`Emoji`, hidden from accessibility), plus
  the chevrons, arrows and tick of the tuner and the big buttons' play, stop, done, again and
  microphone icons in `AppIcons`. The app icon's picture is one vector, `@drawable/ic_app_art` (a robot
  over a flute, from the canvas's "App icon" board): the launcher insets it into the adaptive icon's
  safe zone, `AppLogo` puts it on the golden rounded square in the top bar, and `tools/IconGen.java`
  draws the same shapes for the PNGs and the Play listing. Note names are shown with the octave raised as every canvas board draws it, a `sup` at 0.48 of the
letter, its top level with the letter's top, 1 sp after it (`noteText`, matched on 2026-11-02, built from
  `noteLabelParts`) in a round `NoteBubble`; English keeps scientific octaves (C⁴), Croatian the
  school ones (c¹), and accessibility and tests still read the plain `noteLabel`.
- On a short screen (360 x 640 dp, large text) the ring and the tuner's pitch window shrink to
  fit, so Play and the status line stay in view; only below a tab's minimum height
  does `Modifier.fitOrScroll` make it scroll (a fixed-height column, so weights still work). The
  notes tab scrolls a little on such phones rather than shrink the staff's places, and the flute
  scrolls below its own `FitsHeight` as before (its speed panel, like the metronome tab, only
  below `MetronomeMinHeight`).
- The credits (`ui/common/Credits.kt`, the user's call on 2026-11-05): a long press on the top bar's
  icon (`CreditsLogo`) opens a `Dialog` with the copyright, the licence and the source address. Nothing
  on screen shows it can, and a tap does nothing, so the main screens stay as sparse as ever; a screen
  reader is offered the long press as an action. Keep it: section 7(b) of `NOTICE` has forks keep this
  credit, and it is the only place the app shows it. The address stays plain text, never a link, so a
  child cannot leave the app. It is not a main screen, so the year in the copyright line is fine.
  Its strings are read before the `Dialog`, whose own window `LocaleProvider` does not reach.
- `ui/common/ValueWheel` is the snapping three-row drum behind the metronome's `TempoWheel`. Its
  content description stays on the `LazyColumn` node: the UI tests scroll it with
  `performScrollToIndex`.
