# UI conventions: design and history

The long text: the frame, the look, the sideways layout and tablets, why they are as they are, and
who decided what. It was `ui/CLAUDE.md` until 2026-11-10 (and part of the root one until
2026-11-05); `CLAUDE.md` beside it now holds only the rules that must not be broken and says which
part of this file to read before changing what. It is not loaded by itself: open it when you change
the frame, a shared piece or a sideways or tablet layout. Each tab's own screen is in its folder
(`CLAUDE.md` for the rules, `DESIGN.md` for the flute and the notes tab's long text); the rules every
screen keeps are in the root CLAUDE.md, "Kid-friendly UI constraint". User-visible strings: the root
CLAUDE.md too.

The top bar shows the app icon and names the open tab; the tab bar is four emoji-and-word buttons.
That is the upright frame. On a phone on its side a rail stands in for both ("Sideways", at the end).
On a tablet the same frames stay phone-sized and the tab between them grows ("Tablets", after it).

- One Activity, which sets `volumeControlStream` to the media stream so the volume keys adjust
  the app's sound even while nothing is playing. `MainScreen` hosts a top bar (the app icon, `AppLogo`, and the open tab's name plus, at the end, the `ThemeSwitch` and the `LanguageSwitch`: since 2026-11-05 one
  flag button that opens a list of the flags, with no words and sized in dp so they keep to their circles at any text size, over `ui/common/AppLanguage`; selecting one calls
  `AppCompatDelegate.setApplicationLocales`, which re-creates the Activity) and a bottom bar
  switching between the four tabs (`AppTab`: flute, metronome, tuner, notes; since 2026-11-04 the
  flute is first and the app opens on it, the first entry always being the start tab, and the drum
  sits beside it as the tab the speed knob opens; before that the order was just the build order,
  never a decision; Play screenshots follow the same order). All ViewModels are
  Activity-scoped so state and playback survive tab switches and that re-creation; a turn of the
  phone re-creates nothing (`configChanges` in the manifest, "Sideways" below);
  the one exception is the game's round, which the tab bar resets (`NotesViewModel.resetRound`)
  when the notes tab is left, so the child always comes back to a zero score. `MainScreen` also
  keeps the screen on (`ui/common/KeepScreenOn`) while the metronome plays, the tuner listens or
  the flute's tune runs (Play, or "Your turn" listening). While the metronome plays, the bottom bar's 🥁 hops on every beat
  on every tab (since 2026-11-03: `TabBar` with `rememberBeatFlash` / `hopOnBeat` from
  `ui/common/BeatFlash.kt`, the same hop as the ring's animal), so a running drum is visible
  everywhere and one tap on it reaches Stop; a screen reader hears that tab as "Playing"
  (`tab_metronome_playing`).
- The day/night switch (`ui/common/ThemeSwitch`, the user's call on 2026-11-07, "add theme change button
  near flags"): a round button just before the flag, a moon by day (tap it for night) and a sun by night, the
  picture saying what the tap does as Play shows while stopped, no words, and sized in dp like the flag. A
  screen reader hears a "Dark theme" switch (`theme_dark`). The choice is `ThemeChoice` in `SettingsStore`:
  `SYSTEM` (the phone's night setting) until the first tap, then `LIGHT` or `DARK` for good, a two-way switch
  with no way back to the phone's setting, as a child needs none. `ThemeViewModel` holds it and `MainScreen`
  applies it: it computes `dark` and wraps the frame in `FluteAssistantTheme(darkTheme = dark)` itself
  (`MainActivity` only calls `MainScreen()`), so a tap changes the colours in place; it never goes through
  AppCompat's night mode, which would re-create the Activity and stop a playing tune. `SystemBarsFollowTheme`
  (`ui/common/SystemBars.kt`) calls `enableEdgeToEdge` again with the app's answer, so the phone's bar icons
  match the app and not the phone. The rail has no theme switch: the short rail (360 dp) has no spare height
  (another 38 dp button would take the tabs from about 60 to 49 dp, and at large text the words would stand on
  the open tab's outline), and sideways is for playing, not settings; a child turns the phone upright to
  change it. `ThemeSwitchTest` covers the button and its place before the flag.
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
  bright fill is always `InkOnFill`, with one exception (2026-11-07): the notes tab's keys (picker and summary) sit on `noteTint`
  (`ui/common/Labels.kt`, the letter colour at 0.3 over the card colour), which in dark mode is a dark surface and
  not a bright fill, so a key's label takes `colors.text` there (`NoteKey`, `ui/notes/DESIGN.md`). Everything pressable is a `ChunkyButton` / `Modifier.chunky`
  (thick ink outline, solid shadow, sinks 3 dp when pressed; a faded one goes through `fadedUnless`, as `Modifier.alpha` cuts the shadow off flat); big actions are `BigActionButton`,
  the one place with white on colour: a drawn icon and the word in `OnAction` on `ActionGo` (deep
  green: Play, OK, Again, Yes), `ActionStop` (deep red: Stop) or `ActionListen` (deep blue: the
  flute's Your turn), for contrast (canvas, 2026-10-24); the flute's Play and Your turn stack the
  icon over the word (`stacked`), since two pills with the word beside the icon do not fit a phone.
  Contrast audit of the light theme (2026-11-11, WCAG 2.x on the tokens): the palette passes except two things,
  fixed and pinned by `ThemeContrastTest`. Material's `error` (the tuner's "microphone failed" text) is the coral
  `Stop` in dark but `ActionStop` with `OnAction` on it in light, as the coral is 2:1 on white; the tempo wheel's
  neighbours keep `NEIGHBOUR_ALPHA` (0.75) of the muted text, as 0.45 was 2:1. Left as the canvas drew them, on
  purpose: the pale `border` (1.3 to 1.5:1, only on things that cannot be pressed, bar the microphone card's quiet
  pill), the `track` and `staffSpace` fills, the tempo bands, the ghosted clef and the note-coloured ring (the
  note's name is inside it). Open: a chosen `ModeChoice` / sideways pill / open book is `Sun` against white
  (1.38:1) with the same outline, so the state is carried by the fill alone.
  The font is Nunito (three static weights in `res/font`, licence in `docs/licenses/`); the canvas
  used Fredoka, which has no č, ć or đ. Icons are emoji (`Emoji`, hidden from accessibility), plus
  the chevrons, arrows and tick of the tuner and the big buttons' play, stop, done, again and
  microphone icons in `AppIcons`. The app icon's picture is one vector, `@drawable/ic_app_art` (the Android
  robot playing a flute, since 2026-11-07; Google's robot, modified under
  CC BY 3.0, so the credits show the attribution line `credits_android_robot`): the launcher insets it
  into the adaptive icon's safe zone, `AppLogo` puts it on the golden rounded square in the top bar,
  and `tools/IconGen.java` draws the same shapes for the PNGs and the Play listing. Note names are shown with the octave raised as every canvas board draws it, a `sup` at 0.48 of the
letter, its top level with the letter's top, 1 sp after it (`noteText`, matched on 2026-11-02, built from
  `noteLabelParts`) in a round `NoteBubble`; English keeps scientific octaves (C⁴), Croatian the
  school ones (c¹), and accessibility and tests still read the plain `noteLabel`.
- On a short screen (360 x 640 dp, large text) the ring and the tuner's pitch window shrink to
  fit, so Play and the status line stay in view; only below a tab's minimum height
  does `Modifier.fitOrScroll` make it scroll (a fixed-height column, so weights still work). The
  notes tab scrolls a little on such phones rather than shrink the staff's places, and the flute
  scrolls below its own `FitsHeight` as before (its speed panel, like the metronome tab, only
  below `MetronomeMinHeight`).
- The credits (`ui/common/Credits.kt`, the user's call on 2026-11-05): a long press on the app icon
  (`CreditsLogo`, in the top bar or at the top of the rail) opens a `Dialog` with the version, the
  website's address (`credits_website_url`, flute-assistant.bertlab.hr), the copyright and the
  Android robot's line, and no repository address (the user's call: users do not need it). On
  2026-11-11 the user dropped the privacy policy's address and the whole licence part (the GPL line
  and its address): the website's footer leads to the policy. Play asks to find the policy inside
  the app; the user chose the website's address over it, so if a Play review flags the policy,
  look here first. A visible entry was drawn and turned
  down the same day as more load than it is worth. Nothing on screen shows it can, and a tap does
  nothing, so the main screens stay as sparse as ever; a screen reader is offered the long press as
  an action. Keep it: section 7(b) of `NOTICE` has forks keep this credit, the robot's CC BY line
  must be shown, and it is the only place the app names the website. The address stays plain text,
  never a link, so a child cannot leave the app. It is not a main screen, so its numbers are fine.
  Its strings are read before the `Dialog`, whose own window `LocaleProvider` does not reach. It stays
  open across a turn (the open state lives in `MainScreen`, see below), and it scrolls on a short window.
- `ui/common/ValueWheel` is the snapping three-row drum behind the metronome's `TempoWheel`. Its
  content description stays on the `LazyColumn` node: the UI tests scroll it with
  `performScrollToIndex`.
- `ui/common/ModeChoice` is the pair of chunky pills on top of a tab (64 dp, 12 apart, the chosen one yellow, a
  selectable group for a screen reader), lifted from the tuner's `TunerModeChoice` on 2026-11-07 and shared by the
  tuner's two modes and the notes tab's two games. Change it for both.

## The phone's Back

Since 2026-11-10 (the review of that day: on the emulator, Back on the notes picker and on the shelf dropped to the
launcher, and no `BackHandler` stood anywhere; the user's phone has three navigation buttons, so Back is one tap
under the tab bar). Each panel that takes a tab's place takes Back as its own way back, and nothing new is drawn:

- the notes picker: as its OK (`NotesContent`);
- the flute's speed panel: as its OK, after an open tempo wheel, which closes first; the shelf: as a tap on the open
  book (`flute.shelf_close`); the microphone card: as Not now; a tune that runs, Play or Your turn: as Stop
  (`FluteContent`, a handler in each branch, and Compose asks the one composed last first);
- the metronome tab: an open tempo wheel closes (`MetronomeContent`);
- the language list and the credits are a popup and a dialog and always closed on Back.

With nothing open Back is the phone's: it leaves the app, as before, and a running metronome plays on. The tuner
takes none (its microphone card has no way back: the tab is the microphone). The handlers stand above each
`LocalSideways` branch, so a phone on its side has them too. Tests press it with `BackKey` (androidTest, `ui/common`):
`Watch()` in the content, `press`, and `taken` for "Back is the phone's here"; a Back nothing takes would close the
test's own Activity, so `press` refuses one.

## Sideways (a phone on its side)

Since 2026-11-05; the design, the mockups and who decided what are in
`docs/superpowers/specs/2026-11-05-landscape-design.md`. Each tab's sideways screen is described in
its own folder's CLAUDE.md; this is what they share. Before it the app rotated but had no sideways
layout: the bars left about 209 of 411 dp, so every tab was cut off. The moment it is for is a
child who props the phone on a music stand and plays with both hands on the flute, so the metronome,
the tuner and the flute's Your turn must read from arm's length and need no tap until Stop.

- **When.** `SidewaysGeometry.isSideways(width, height)`: the window is wider than tall and at least
  600 dp wide, in both sideways directions. It follows the window, not the device, so a narrow pane of
  a split screen keeps the upright layouts (which already scroll there). `MainScreen` reads the window in a
  `BoxWithConstraints` and provides `LocalSideways` (static: it changes only with the window, and then the
  whole screen is laid out again anyway). Screens read `LocalSideways` and branch inside their `*Content`: the
  metronome, tuner and notes with an early `return` into a `*Sideways` composable (the upright body is untouched),
  the flute inside `FluteContent` over the same lambdas for the tune, the keys and the Play row, and the
  microphone card by itself, which gives the tuner and the flute theirs; never
  `LocalConfiguration`, which the tests' `SidewaysHost` does not move. The one thing that reads a
  configuration is the flag (`AppLanguageSwitch`): it reads `LocalResources.current.configuration`, the
  resources the words come from, so the flag cannot disagree with them after a turn. The orientation is
  never locked.
- **`SidewaysGeometry`** (`ui/common/Sideways.kt`): the sideways sizes as plain dp floats, like
  `StaffGeometry`, so `SidewaysGeometryTest` pins what the three phones it was drawn for get (915 x 411,
  800 x 360, 640 x 360). The screens read these: the rail (92 wide, 84 under 400 dp tall,
  `isShort`), the right column (116, 100), the card, the keys, the running staff's scale, the ring, the
  big beats' gaps and cap, the tuner's window and where its fingering stands. Some sizes stay local
  to the screen that draws them (the metronome's panel and well, the picker's keys), and `bigBeatUnit`
  is what the tests hold `BeatBubbles` to: no screen calls it. `CARD_CHROME` is 38 (the card's padding
  and border) although the border takes no layout room, so the real inset is 32: upright's `FitsHeight`
  counts the same 38, and the spare 6 dp gives c3's stem its room.
- **The frame.** `SideRail` replaces `TitleBar` and `TabBar`: from the top the logo (`CreditsLogo`, its
  long press opens the credits), the four tabs as emoji over word in `AppTab` order with the open one a
  yellow pill (`TabItem`, shared with the tab bar), and the flag (`AppLanguageSwitch`). It never
  rearranges, not even when a tune starts. The drum hops on every beat as in the tab bar, and a screen
  reader still hears "Playing". There is no title: the yellow tab says which is open, and a screen reader
  hears it as the selected tab. The rail's words may shrink to 7 dp (the bar's floor is 10 sp) because
  "Metronome" was cut short at large text and a cut word is worse than a small one. The rail's sizes are
  in dp where the text size would break the 60 dp cell of a short rail: the floor and the emoji over the word
  do not grow with the text (a floor of 7 sp is 14 dp at twice the text, wider than "Metronome" has room for), and
  the word grows to 1.3 times at most (`RailWordGrowth`; at twice the emoji and the word took the whole cell and
  the open tab's outline cut the word). The language list
  opens beside the rail (`LanguageSwitch.listOffset`): above the flag it would cover the tabs. A camera
  cutout: the rail's background runs under the one on its side and its contents stand clear, and the tab's
  box clears the one on the other (`EndCutout`); sideways `Scaffold` gets `WindowInsets.systemBars` rather
  than its default, which also takes the cutout and would double it. The emulator has no cutout of its
  own: `adb shell cmd overlay enable --user 0 com.android.internal.display.cutout.emulation.hole` (also
  `.tall`, `.corner`, `.double`; `disable` afterwards) shows the rail and the right column clearing it in both
  sideways directions, but only a real phone's camera settles it.
- **`SidewaysPage`** (`ui/common/SidewaysPage.kt`): the shape every tab has, where am I on the left (the
  rail), the thing itself in the middle (the white card, `Stage`, 8 dp margins), what to do on the right
  (a column, `column = null` for none). Its first child sits at the top, its last low, the same spot on every
  tab (near the stand's ledge, under the right hand), and a lone child goes low too
  (`Arrangement.SpaceBetween` alone would send it to the top). `SidewaysActionButton` is the stacked
  `BigActionButton` (116 x 104), `SidewaysPlainButton` the quieter emoji-over-word one (84 tall, or 92 and
  80 for the tuner's modes; no emoji means the word alone, for "Not now"). The flute's writing screen has no
  column, so it does not use the page. Words shrink with `autoSize` and use relative line heights: before
  Android 14 font scaling is linear, and a fixed line would not fit.
- **`HideSystemBars`** (`ui/common/SystemBars.kt`): while a phone is sideways the status and navigation bars
  hide, which gives the running staff its full size (with them 48 dp of the height goes); a tablet keeps them
  ("Tablets"). A swipe from the edge brings them
  back for a moment (`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`). They return upright and stay in split screen, where
  they are not the app's to take. The activity handles the change into and out of split screen itself, so
  the effect also listens to it: `enabled` does not change when a wide pane is dragged to full screen.
  **Android shows its own one-time "Viewing full screen / To exit, swipe down from the top" prompt with a
  "Got it" button the first time the bars hide.** It covers the middle of the screen until it is tapped, shows
  once, and an app cannot turn it off; a child on the stand meets it once. Kept on purpose (the staff needs the
  height); the user decides whether it is worth it. Another system overlay: when the microphone opens (the
  tuner, Your turn) Android draws its green microphone chip, with the clock, over the top of the rail and of
  the right column for a few seconds, bars hidden or not; there is nothing to do about it.
- **A turn re-creates nothing.** `MainActivity` declares `orientation|screenSize|smallestScreenSize|screenLayout`
  and, since 2026-11-06, `keyboard|keyboardHidden|navigation` in `configChanges`, and not the locale: the language
  switch relies on re-creation. The keyboard flags are for a tablet's keyboard case attached during a tune; Android
  17 does that by itself for an app targeting 37 (Google's Android 17 beta post, and seen on the emulator, where
  a manifest without them still reported them), older Android does not. So a tune, Your turn, the
  tuner and the metronome run on through a turn, and an open panel (speed, shelf, picker) stays open. Going
  to the background (Home) still stops the flute (`flute.stop reason=leave`). `TurningThePhoneTest` reads the
  flags the activity reports and `ManifestFlagsTest` the manifest's own, as on API 37 the two differ.
- **One call site for the tab.** `MainScreen` writes the `when (selectedTab)` once and hands it to the stateless
  `MainFrame` as its `tab` slot; `MainFrame` calls it in one place, a `BoxWithConstraints` in a `Row` that holds the rail only
  when sideways, so a turn only recomposes it. Were the tab composed in an `if (sideways) ... else ...`, it would
  leave the composition on a turn and `FluteScreen`'s lifecycle effect would call `leaveTab()` and stop the tune:
  the same loss the manifest flag prevents, by another road. `MainFrameTest` guards it: a tab with a
  `DisposableEffect` and a `remember`ed object is flipped upright to sideways and back, and must be entered once,
  never disposed and made once (checked by putting `tab()` in two branches, which fails it). The real run is still
  worth a look after a change to the frame: a turn during Play with no `flute.stop reason=leave` in
  `adb logcat -s FA.Flute`. For the same reason the credits' and the language list's open state is held in
  `MainScreen` (`creditsOpen`, `languageOpen`) and passed to whichever bar shows: the logo and the flag move
  between the top bar and the rail, and state kept inside them would be lost.
- **Tests.** `SidewaysHost(width, height, fontScale, sideways)` (androidTest) forces a window size and
  `LocalSideways`; it takes the rail off the width, so a test with `SideRail` beside a tab forces its own full
  size. Its density is floored, so it is about 0.5 % larger than asked. **`assertIsDisplayed` does not prove a
  fit**: what spills past a window is still displayed, and a word cut short still has its node. Compare
  `getUnclippedBoundsInRoot()` with the room, and ask `GetTextLayoutResult` about words (`hasVisualOverflow`, line
  breaks only at whitespace; for a plain `Text` this action hands back a paragraph as wide as the room and a box as
  wide as the word, so `hasVisualOverflow` is always true there: see `TabletFitTest.assertWhole`). Text also measures a little differently at the phone's real density, so a tight fit
  is worth one run with the emulator itself sideways (the rail's "Metronome" failed only there). A flip of
  `LocalSideways` alone cannot show that a key held through a turn is released: the Compose test dispatcher
  handles the gesture's cancel at once, where a phone throws it; `FluteSidewaysTest` cancels the gesture by a
  density change instead, and the real rotation is checked by hand (`ui/flute/DESIGN.md`).

## Tablets

Since 2026-11-06; the design, the numbers and who decided what are in
`docs/superpowers/specs/2026-11-06-tablet-design.md`. The reason is the Play Store, not a child: the user
has no tablet and wants the app to look right to someone choosing it on one, so a tablet screen is judged
as a screenshot. Before it nothing was broken, but every control stayed phone-sized in a big empty card.
The user picked this over one uniform scale of the whole app.

- **When.** `TabletGeometry.isTablet(width, height)` (`ui/common/Tablet.kt`): the window's shortest side is at
  least 600 dp. It follows the window, not the device, and `MainScreen` reads it where it reads `isSideways`.
  Upright or sideways is decided exactly as on a phone.
- **The tab zooms, the frame does not.** `MainFrame` draws the tab at a higher `LocalDensity`, by
  `TabletGeometry.tabZoom` of the room the bars leave, so the phone layouts are laid out in a smaller logical
  area and come out bigger: about 560 x 745 upright and 830 x 526 sideways on a 10-inch tablet, a zoom of 1.43.
  One provider covers every tab, panel, picker and summary, words and touch included, and `SidewaysGeometry`
  needs no change, as it is handed logical sizes like any other layout. `TitleBar`, `TabBar`, `SideRail`, the
  language list, the credits and the snackbar are outside it and keep their phone sizes. The provider stands on
  a phone too, at 1, for the one call site ("One call site for the tab"). A window too short to grow, a foldable
  or a split pane, stays at 1 with its phone sizes; the zoom stops at 2.
- **What a tab adjusts on top of it** reads `LocalTabZoom`, which is 1 on a phone: the flute's keys and staff
  (`ui/flute/DESIGN.md`). The rest is simply the phone layout, zoomed; keep it that way unless a screen shows
  why.
- **The density.** `Density.zoomedBy`: at 1 it is the frame's own density object, so a phone is untouched by
  construction. When zoomed it is a plain `Density`, which keeps Android's curve for large text (at twice the
  text 24 sp is 36 dp, not 48): measured equal to Android's own at every font scale on 2026-11-06, and
  `TabletDensityTest` holds it there, as a Compose update could change that.
- **Bars.** `TabletGeometry.hidesSystemBars`: a tablet keeps them sideways, as hiding them only ever bought
  height on 360 to 411 dp phones, and so it never meets Android's "Viewing full screen" prompt.
- **Tests.** `TabletHost(size, fontScale)` (androidTest) forces a `TabletSize`'s logical area, with the limits of
  `SidewaysHost`. The sizes are the tab areas of Google's test sizes, after the bars. A real tablet has not seen
  this app: the emulator fakes one (the `emulator` skill), so the look and the audio on real hardware are unchecked.
