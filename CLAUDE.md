# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Flute Assistant: a single-module Android app (metronome, chromatic tuner, a note reading
game and a digital flute) for primary-school children learning the flute. Package and applicationId are `hr.bertlab.fluteassistant`; the
project folder keeps the old name FluteTempo. Kotlin, Jetpack Compose + Material 3, Hilt, Room,
coroutines/Flow. Shipped languages: English (default resources) and Croatian (`values-hr`).

## Git rules

Do not commit, push, or create git worktrees in this repository. Leave changes in the working
tree for the user to review and commit.

## Commands

Everything goes through the wrapper (Gradle 9.8.2, sha256-pinned in
`gradle-wrapper.properties`; the daemon runs on JDK 25 via the foojay toolchain). The
configuration cache is on.

```sh
./gradlew assembleDebug                                   # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest                               # JVM unit tests (app/src/test)
./gradlew :app:testDebugUnitTest --tests '*NoteMathTest*' # one test class
./gradlew :app:testDebugUnitTest --tests '*MetronomeViewModelTest.tempo feel follows the bpm'  # one test
./gradlew lintRelease
./gradlew :app:connectedDebugAndroidTest                  # Compose UI + resource tests (app/src/androidTest)
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=hr.bertlab.fluteassistant.ui.tuner.TunerScreenTest
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=hr.bertlab.fluteassistant.ui.notes.NotesScreenTest
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=hr.bertlab.fluteassistant.ui.flute.FluteScreenTest
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=hr.bertlab.fluteassistant.data.settings.SharedPreferencesSettingsStoreTest
./gradlew testDebugUnitTest lintRelease bundleRelease     # pre-upload check (docs/release/README.md)
```

### Emulator

Running on the emulator and instrumented tests: the `emulator` skill (`.claude/skills/emulator/SKILL.md`).
In short: a phone is often attached, so pin `ANDROID_SERIAL=emulator-5554`; start the AVD with
`-no-audio`; pass one test class at a time; the test run uninstalls the app when it finishes.

### Release

The release process (version bump in `gradle.properties`, the release and listing workflows,
signing, Play upload, store graphics and screenshots) is in the `release` skill
(`.claude/skills/release/SKILL.md`) and `docs/release/`. Never read or print the keystore password.

## Build wiring (AGP 9)

AGP 9.4.1 uses built-in Kotlin: there is no `kotlin-android` plugin and no kapt. The root
`build.gradle.kts` puts the Kotlin Gradle plugin on the buildscript classpath to raise AGP's
bundled Kotlin to the catalog's `kotlin` version, and the Compose compiler plugin shares that
same version ref; keep the two equal. KSP is the standalone 2.3.x line; Hilt must stay >= 2.59.
Room exports schemas to `app/schemas` (database version 1, destructive fallback migration for
now). `localeFilters` strips every library locale except en and hr.

`androidx.appcompat` is in the build only for the in-app language switch:
`AppCompatDelegate.setApplicationLocales` is the per-app-language backport below Android 13, so
`MainActivity` is an `AppCompatActivity`, the window theme must stay an AppCompat theme
(`Theme.AppCompat.DayNight.NoActionBar`) and the manifest declares
`AppLocalesMetadataHolderService` with `autoStoreLocales`. The choice is stored by the platform
(system on 13+, AppCompat below), not by `SettingsStore`.

## Architecture

Layered by package under `app/src/main/java/hr/bertlab/fluteassistant/` (`domain/` pure Kotlin,
`data/`, `audio/`, `playback/`, `ui/`), with the Hilt modules in `di/` binding each interface to
its Android implementation. Room enum `Converters` fall back to defaults on unknown names.

Each feature's design, thresholds and the reasons behind them live in a CLAUDE.md next to its
code, which loads when you work in that folder; read the one for the feature you are changing:
`audio/metronome/` (metronome audio path, player contract), `playback/` (background playback),
`ui/tuner/` (tuner path), `ui/notes/` (note reading game), `ui/flute/` (digital flute: tune,
rests and breaths, bars and ties, the songbook of six tunes). The matching `audio/`, `domain/` and `ui/metronome/` folders
point there.

### UI conventions

- One Activity, which sets `volumeControlStream` to the media stream so the volume keys adjust
  the app's sound even while nothing is playing. `MainScreen` hosts a top bar (the app icon, `AppLogo`, and the open tab's name plus the `LanguageSwitch`: two flag
  buttons over `ui/common/AppLanguage`; selecting one calls
  `AppCompatDelegate.setApplicationLocales`, which re-creates the Activity) and a bottom bar
  switching between the four tabs (`AppTab`: metronome, tuner, notes, flute). All ViewModels are
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
- Every user-visible string is a resource in both `values/strings.xml` and
  `values-hr/strings.xml`. Domain enums stay language-free; `ui/common/Labels.kt` maps them to
  string resources and builds note names from the `note_names` array (Croatian uses the German
  H / B convention). Croatian copy addresses the child in the informal singular.
- The look comes from the Claude Design canvas (see `.superpowers/design/flute-assistant-screens/`):
  white cards (`Stage`) on sky blue, or navy in dark mode, with dynamic colour off; the tokens that
  change with the theme are `FluteColors` (read through `FluteTheme.colors`), the bright fills that
  never change are `Sun` (yellow = chosen), `Go` (the Sound switch), `Stop` and `NoteLetterColors`, one per letter,
  which `noteColor(midi)` applies wherever a note appears (a sharp takes the letter below). Text on a
  bright fill is always `InkOnFill`. Everything pressable is a `ChunkyButton` / `Modifier.chunky`
  (thick ink outline, solid shadow, sinks 3 dp when pressed); big actions are `BigActionButton`,
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
- `ui/common/ValueWheel` is the snapping three-row drum behind the metronome's `TempoWheel`. Its
  content description stays on the `LazyColumn` node: the UI tests scroll it with
  `performScrollToIndex`.

### Kid-friendly UI constraint

The UI was deliberately reduced (spec: `docs/superpowers/specs/2026-10-22-kid-friendly-ui-design.md`,
follow-up: `docs/superpowers/specs/2026-10-22-metronome-controls-and-language-design.md`) and on
2026-10-24 redrawn from the user's Claude Design canvas. The top bar shows the app icon and names
the open tab; the tab bar is four emoji-and-word buttons. The metronome is one stage with the beat bubbles
(the first bigger; the sounding one yellow with the speed's animal hopping on it) between a − and
a + that step the bar from two to six beats (`fewerBeats` / `moreBeats` over 2/4, 3/4, 4/4, 5/4,
6/8; the other signatures are not offered), and the tempo ring from 40 to 200: five animal bands
(`TempoBand`: snail 40, turtle 60, duck 80, rabbit 100, cheetah 130), each a fifth of the ring and
every animal turned to face right (`facingRight(emoji)` in `ui/common`, also used for the summary's
speed animal: Google's and Apple's emoji sets draw all five facing left, so each is mirrored;
Samsung's draws the snail already facing right, so on a Samsung phone the snail is not), a
yellow knob with the current animal that is dragged round, a tap on an animal jumps to its band
(the rabbit again to 120), and the tempo figure alone in the middle (the band's name above it was
dropped on 2026-10-24; a screen reader hears it as the ring's state; tap the figure for the
in-place `TempoWheel`; there are no tempo −/+ buttons: the user turned them down again on
2026-10-24); then one Play / Stop pill (no volume control: the phone's buttons set loudness). The
tuner has the two mode pills on top ("Which?" / "Tune"), then a stage with the pitch window (since
2026-10-24, replacing a face meter): a dashed home ring holding the note to play on a dotted
up-down guide, and the child's sound as a bubble floating above it when too high and below when
too low, with an arrow pointing home; another note shows as that note filled in its colour, in
tune the bubble fills, sits in the ring and gets a tick and sparkles, and in "Which?" silence the
ring is grey with "…". In "Tune" the window sits between ‹ › with a "Hear" pill under it (spec:
`docs/superpowers/specs/2026-10-22-tuner-target-note-design.md`); then the fingering for the
ring's note, one status line and, under it, one quieter advice line that says what to change
(roll the flute, air, fingers, come closer, a clear note), never a number. The notes tab has one
hero row (the streak as up to five flames, "Find" over the note's bubble, and a "Pick" button with
a dot per chosen letter) over a stage with the staff and one feedback line, and ten round places
at the bottom (a star for first time, a faint star for a retry, yellow for the current note);
"Pick" swaps the staff for the picker in place (no dialog): the fifteen notes in octave rows,
highest on top, the "Sound" switch row and a big OK, the only way back. The end-of-round summary
has no numbers on screen either: a trophy, ten stars, and tiles for the best streak (flames), the
aim (one to three stars) and the speed (an animal). The flute tab has a stage (the user's canvas of
2026-11-02, replacing the hold dots and the line of pills, then changed by the user the same
evening) with two things, the staff and the names, and since 2026-11-03 a third, the speed knob over the staff, and since 2026-11-04 a fourth, the book button under its clef: the tune on a real five-line staff (`TuneStaff`, geometry in `StaffGeometry`, lines
18 dp apart: the faint treble clef and the metronome tab's time signature fixed at the left, each
note at its true height drawn as printed music draws it, the whole note (head, stem, flag, dot,
tie) in its letter's colour, the tilted oval head with a thin ink edge and no letter in it, every
stem up, as the user asked; the shape says the length, filled with a flag for an eighth, filled
for a quarter, a ring for a half and a dotted half, a wider ring and no stem for a whole note, and the
room after it grows 29 dp a beat; bar lines after each full bar, a note held across one as two
heads tied under them; the held step gets a yellow ring and a soft yellow band that grows with it,
the playing step the ring; the signature's figures are the one number the user asked for on a
main screen, keep them) and under each note, in the fingering's place, its name in a pill in its
letter's colour, in the same room as the note so names and notes line up and scroll as one (the
user's "align note names with the notes"), the pill as wide as the note's room, bar lines through
the names too, a rest a grey 💤 pill, a breath no pill; the held step's yellow band covers its notes
and its names together; the names went from a single-name bubble to a separate pill line (with
length symbols, then without) to this, all on 2026-11-02; the flute tab shows no
fingering any more, the tuner still does; at the staff's top left, since 2026-11-03 (chosen by
the user from three mockups), the metronome's yellow knob with the speed's animal on it (`SpeedKnob`,
54 dp, touching only the faint clef's tip, never a note or the signature; it hops on each click while
the tune plays back), whose tap swaps the stage, the keys and the Play row, in place, for the
metronome tab's own stage (`MetronomeStage`: the beat bubbles with − / + and the ring exactly as
drawn there, its tempo figure the one number the panel shows) over one big green OK, the only way
back; at the staff's bottom left, under the clef and beside the names, since 2026-11-04 (chosen by
the user from true-size mockups, the top right covered the newest notes' stems), the open book
itself as a little yellow book like the shelf's, spine, page edges and its sweet on the cover, the
yellow knob's twin (`BookButton`: 🍭 🍪 🧁 🍩 🍦 🍬, one for each of six books; a round white button
until the user asked for "same book layout" the same day), whose
tap swaps the stage, the keys and the Play row, in place, for a shelf of six little books
(`TuneShelf`, always in the same order, the open one yellow, each a sweet over the tune's first five
notes, no word and no number on it) where one tap opens a book and closes the shelf: the flute
keeps six tunes at once, each with its own speed and beat (opening one with notes in it sets the speed knob and the
metronome tab to them), with no save step (every change to the tune is kept in the open book), no
name, no delete (a book is emptied only by wiping its tune with the sponge) and no choice of
picture, and the old single tune moved into the first book on update (spec
`docs/superpowers/specs/2026-11-04-flute-songbook-design.md`, details in `ui/flute/CLAUDE.md`);
the fifteen keys in three octave rows highest on top (each in its letter's colour; a key sounds
for as long as it is held) with, filling the top row after c³, a 😮‍💨 breath key, a
💤 rest key and a 🧽 erase key (two keys wide each, icon only; the rest is held like a note and grows the same way,
on the staff its rest symbol in grey with 💤 under the staff, in the line a grey 💤 pill; a breath has no length, on the staff the breath mark with 😮‍💨 over it, and playback
goes past it; the breath and rest keys were asked for on 2026-11-02 in a brainstorm, not on the canvas; the erase key sinks while held and repeats like a
keyboard's backspace, moved there from the Play row at the user's request on 2026-11-04) and, at the bottom, two big buttons that
share the row's width and stack their icon over their word, "Play" (the tune at the metronome's tempo) and, since 2026-11-04, "Your turn"
(labelled "Listen to me" / "Poslušaj me", the child speaking to the phone, as is Croatian
"Odsviraj" for Play; blue, a microphone: the phone listens to the child's real flute and waits for each written note,
the same letter in any octave; under the note a star for a first try, a faint star after a stumble,
and under a wrong one the note that was heard as a bubble in its own letter colour until the next
note (since 2026-11-04 evening, in place of a ❌ for a moment: it tells the child what came out; the
user's first idea, the note name in green under every note, was turned down with them because the
name is already in the pill above and green is F's colour), never a number and no summary; the first right-or-wrong outside
the notes game, kept the child's by being opt-in and child-paced; spec
`docs/superpowers/specs/2026-11-04-flute-your-turn-design.md`, details in `ui/flute/CLAUDE.md`);
while the tune runs, by Play or by Your turn, the keys and the sponge hide, the staff
grows by a third for a phone on a music stand, the knob's animal walks a marks row under the names
(bouncing with the beats in Play, waiting under the next note in Your turn, the knob empty and the
book button faded meanwhile) and one wide red Stop takes the row: no record button, no clear-all, no tempo control but that knob and the metronome's
stage it opens in place, and no note lengths as a
control (lengths come only from how long a key is held; the user drew this on 2026-10-24). The user rejected a wider
layout (−/+ beside the ring, a ten-button signature scroller, flags in the content area) as too
packed: keep the screens this sparse. Tap tempo, subdivisions, click sounds, volume, presets and the A4 reference still
exist in the engine, data layer and ViewModels but are not exposed: `MetronomeViewModel.initialState()`
resets subdivision, sound and volume to their defaults (and brings a stored tempo onto the
ring's 40 to 200) and `TunerViewModel` starts from 440 Hz
and plays the reference tone at a fixed `REFERENCE_TONE_VOLUME`, so a stale stored value cannot
silently affect playback. Do not reintroduce numbers or jargon
(BPM, Hz, cents, Italian markings) or extra controls on the main screens; an "advanced mode"
is the intended home for those features.

## Logging and observability

`core/log/Logger` (Hilt-provided, `AndroidLogger` in production, `FakeLogger` in tests) is the
one way to log. Every line is a dotted event name plus `key=value` fields built by `LogLine`
(two decimals for floating point, `none` for null, quoted text), under one tag per subsystem
(`LogTags`: `FA.Tuner`, `FA.Metronome`, `FA.Playback`, `FA.Flute`), so lines are greppable and a script
can split them on spaces. Levels: `v` per-frame detail, off unless switched on per tag at
runtime; `d` periodic summaries and state changes, on in debug builds; `i` lifecycle; `w`/`e`
failures with the exception. Guard costly `v` calls with `logger.isVerbose(tag)`. Never log
through `android.util.Log` directly, never log user-identifying data, and keep event names
stable: they are the interface a logcat session is read with.

Each subsystem's events are listed in its folder's CLAUDE.md: `ui/tuner/` (`FA.Tuner`),
`audio/metronome/` (`FA.Metronome`, `FA.Playback`), `ui/flute/` (`FA.Flute`).

```sh
adb logcat -s FA.Tuner FA.Metronome FA.Playback FA.Flute   # everything structured, debug build
adb shell setprop log.tag.FA.Tuner VERBOSE               # per-frame tuner lines (any build)
adb logcat -v time -s FA.Tuner | grep tuner.summary     # one line a second: did frames pass the gate?
```

## Testing conventions

Unit tests use hand-written fakes from `app/src/test/.../fakes/Fakes.kt` (`FakeMetronomePlayer`,
`FakeTunerEngine`, `FakeSettingsStore`, `FakePresetRepository`, `FakeClock`,
`FakeBackgroundPlayback`, `FakeReferenceTonePlayer`, `FakeFluteTonePlayer`, `FakeLogger`); there is no mocking library. ViewModel tests run under
`StandardTestDispatcher` with `Dispatchers.setMain` and drive time with `advanceTimeBy` /
`advanceUntilIdle`. Instrumented tests cover the `*Content` composables (`MetronomeScreenTest`, `TunerScreenTest`,
`NotesScreenTest`, `FluteScreenTest`), the flag buttons (`LanguageSwitchTest`), the pieces the
tuner and the flute share (`SharedActionsTest`: the microphone card's "Not now", the stacked big
button), the settings store with the songbook's one-time migration of the old single tune
(`SharedPreferencesSettingsStoreTest`, over a throwaway preferences file so the app's own settings
are never touched) and locale-dependent resources (`LabelsTest`). The `config_summary`, `subdivision_*` and `sound_*` strings must stay
even though the screens no longer show them: the playback notification and `LabelsTest` use them.
