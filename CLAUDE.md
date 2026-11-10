# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Flute Assistant: a single-module Android app (metronome, chromatic tuner, a note reading
game and a digital flute) for primary-school children learning the flute. Package and applicationId are `hr.bertlab.fluteassistant`; the
project folder keeps the old name FluteTempo. Shipped languages: English (default resources) and
Croatian (`values-hr`).

## Git rules

Do not commit, push, or create git worktrees in this repository. Leave changes in the working
tree for the user to review and commit. A recorded session is a child's playing: never commit one
(the repository is public).

## Licence

GPL-3.0-or-later with the section 7 terms in `NOTICE`; the copyright holder is Bertlab d.o.o. (the
name on the Play listing). `LICENSE` stays the exact GPL text, so GitHub detects it. Every new
Kotlin, Java, Python or shell source file starts with the two-line header (after any shebang):

```kotlin
// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.
```

Section 7 is why the header is there: the terms, or a pointer to them, must be in the source files.
The source address is written in `NOTICE` only: the app does not show it (the user dropped it on
2026-11-05, users do not need it). The privacy policy's address is `credits_privacy_url` and the
Play Console field, which only changes after a review.

## Commands

Everything goes through the wrapper (Gradle 9.8.2, sha256-pinned in
`gradle-wrapper.properties`; the daemon runs on JDK 25 via the foojay toolchain). The
configuration cache is on.

```sh
./gradlew testDebugUnitTest                               # every JVM test: the rules, the view models and the screens (about 25 s)
./gradlew :app:testDebugUnitTest --tests '*NoteMathTest*' # one test class
./gradlew :app:testDebugUnitTest --tests '*MetronomeViewModelTest.tempo feel follows the bpm'  # one test
./gradlew :app:testDebugUnitTest --tests '*FluteScreenTest*'  # one screen's tests, on the JVM
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=hr.bertlab.fluteassistant.ui.tuner.TunerScreenTest  # the same class on the emulator
./gradlew testDebugUnitTest lintRelease bundleRelease     # pre-upload check (docs/release/README.md)
ANDROID_SERIAL=emulator-5554 ./gradlew :app:installDebug -PleakCanary   # debug build with LeakCanary, emulator only
```

The screen tests (`app/src/androidTest`) run two ways since 2026-11-10. `testDebugUnitTest` compiles
them a second time and runs them on the JVM under Robolectric, all of them in seconds: that is the
check after a change. The emulator runs the same classes as instrumented tests, many minutes for all
of them: that is the check before a release, and after a change to drawing, sizes or a gesture, since
a test whose first line is `needsDevice()` (it reads the screen's pixels or needs a real gesture's
cancel) is skipped on the JVM, and fonts and emoji there are not a phone's.

Recorded "Your turn" sessions (pull, replay, sweep, report): the `sessions` skill
(`.claude/skills/sessions/SKILL.md`).

### Emulator

Running on the emulator and instrumented tests: the `emulator` skill (`.claude/skills/emulator/SKILL.md`).
In short: a phone is often attached, so pin `ANDROID_SERIAL=emulator-5554`; start the AVD with
`-no-audio`; pass one test class at a time; the test run uninstalls the app when it finishes.

### Release

The release process (version bump in `gradle.properties`, the release workflow,
signing, Play upload, store graphics and screenshots) is in the `release` skill
(`.claude/skills/release/SKILL.md`) and `docs/release/`. Never read or print the keystore password.

## Build wiring

AGP 9 with built-in Kotlin, the version constraints, the debug and release source sets and the
AppCompat language switch: `.claude/rules/build-wiring.md`, which loads with the gradle, manifest,
theme and debug/release files.

## Architecture

Three layers, each a top-level package under `hr.bertlab.fluteassistant` (since 2026-11-10, when the
rules moved out of the ViewModels):

- `domain/` holds the rules as plain Kotlin with no Android import, a package per feature: `flute`
  (the tune, `TuneWriter`, `ListenRun`, bars, the songbook), `listen` (what the microphone's frames
  become: `NoteOnsetTracker`, `HearingTracker`, the thresholds in `ListenRules`), `metronome`
  (`MetronomeConfig`, `TempoBand`), `notes` (`NotesRound`, phrases, the sticker book), `tuner`
  (`NoteMath`, fingerings) and `settings` (`SettingsStore`, the one storage interface, and
  `MetronomeSettings`, the one owner of the metronome's tempo and beat, which the metronome tab and
  the flute both read and change there and nowhere else). A rule goes here and is tested here, on
  the JVM, with no dispatcher.
- `audio/`, `data/` and `playback/` implement the interfaces the rules and the screens use: the
  engines and players, the SharedPreferences store, the foreground service. `di/AppModule` binds
  them; the logger and the session recorder differ by build type (`SessionModule`).
- `ui/` shows it: one ViewModel per tab, which gives the rules the clock, the sound, the storage
  and the log and holds no rule of its own, and stateless `*Content` composables that take the
  state and the callbacks.

A tab does not import another tab's `ui` package and is never handed another tab's ViewModel; what
two tabs share has one owner below them. The one import that stands is the flute showing the
metronome's own stage (a composable and its state) as its speed panel.

Each folder's CLAUDE.md loads when you work there and holds what must not be broken: `ui/` (the
frame, the look and the structure every tab shares), `audio/metronome/` (metronome audio path,
player contract), `ui/metronome/` (that tab), `playback/` (background playback), `ui/tuner/` (tuner
path), `ui/notes/` (note reading game: Find and Hear, and the sticker book) and `ui/flute/` (digital
flute). The matching `audio/` and `domain/` folders point there. For `ui/`, `ui/flute/` and
`ui/notes/` the long text (how it works, the reasons, who decided what) is `DESIGN.md` beside the
CLAUDE.md, and the songbook's is `ui/flute/SONGBOOK.md`: these are not loaded by themselves, so
read the part the CLAUDE.md names before you change that part.

### Strings

Every user-visible string is a resource in both `values/strings.xml` and
`values-hr/strings.xml`. Domain enums stay language-free; `ui/common/Labels.kt` maps them to
string resources and builds note names from the `note_names` array (Croatian uses the German
H / B convention). Croatian copy addresses the child in the informal singular.

### Kid-friendly UI constraint

The UI was deliberately reduced (spec: `docs/superpowers/specs/2026-10-22-kid-friendly-ui-design.md`,
follow-up: `docs/superpowers/specs/2026-10-22-metronome-controls-and-language-design.md`) and on
2026-10-24 redrawn from the user's Claude Design canvas. What each tab shows, and why, is in that
tab's CLAUDE.md; these rules hold on all of them:

- Do not reintroduce numbers or jargon (BPM, Hz, cents, Italian markings) or extra controls on the
  main screens; an "advanced mode" is the intended home for those features.
- Two numbers are meant to be there, keep them: the metronome's tempo figure (also in the flute's
  speed panel) and the time signature's figures on the flute's staff, the one number the user asked
  for on a main screen. The notes game's summary and Your turn show no number, and Your turn has no
  summary.
- The user rejected a wider layout (−/+ beside the ring, a ten-button signature scroller, flags in
  the content area) as too packed: keep the screens this sparse. The one agreed exception is the
  sideways layout (a phone on its side, 2026-11-05; spec
  `docs/superpowers/specs/2026-11-05-landscape-design.md`, `ui/DESIGN.md`): its width goes to size and
  air, not to more things, and the turned-down list below holds there too. A tablet's room goes the
  same way (2026-11-06, `ui/DESIGN.md`, "Tablets"): the tab grows, and nothing is added.
- Added at the user's request (2026-11-07): a day/night switch in the top bar before the flag, a moon or a
  sun with no words (`ui/DESIGN.md`). It is part of the frame like the flag, not a control on a tab.
- Turned down, do not add: tempo −/+ buttons (again on 2026-10-24), a volume control (the phone's
  buttons set loudness), a visible About or credits entry (2026-11-05: the credits stay behind a long
  press on the logo), words in the language list and, on the flute, a record button, a clear-all, a tempo control other than
  the speed knob and the metronome stage it opens in place, and note lengths as a control (lengths
  come only from how long a key is held; the user drew this on 2026-10-24).
- Tap tempo, subdivisions, the beep and bell click sounds, the volume setting, presets (with their
  Room database) and the A4 reference are gone from the code since 2026-11-09, at the user's
  decision: no screen could reach them. The metronome clicks one woodblock on every beat at a fixed
  gain (`MetronomeRenderer.OUTPUT_GAIN`), `NoteMath` measures against 440 Hz and the reference tone
  plays at a fixed `TONE_VOLUME` (`audio/ToneVolume.kt`, every tone the app plays). `MetronomeSettings` still brings a stored
  tempo onto the ring's 40 to 200. Git keeps the old code (the preset sheet is in commit `6a20374`).

## Logging and observability

`core/log/Logger` (Hilt-provided, `AndroidLogger` in production, `FakeLogger` in tests) is the
one way to log. Every line is a dotted event name plus `key=value` fields built by `LogLine`
(two decimals for floating point, `none` for null, quoted text), under one tag per subsystem
(`LogTags`: `FA.Tuner`, `FA.Metronome`, `FA.Playback`, `FA.Flute`, `FA.Notes`), so lines are greppable and a script
can split them on spaces. Levels: `v` per-frame detail, off unless switched on per tag at
runtime; `d` periodic summaries and state changes, on in debug builds; `i` lifecycle; `w`/`e`
failures with the exception. Guard costly `v` calls with `logger.isVerbose(tag)`. Never log
through `android.util.Log` directly, never log user-identifying data, and keep event names
stable: they are the interface a logcat session is read with.

Each subsystem's events are listed beside its code: `ui/tuner/CLAUDE.md` (`FA.Tuner`),
`audio/metronome/CLAUDE.md` (`FA.Metronome`, `FA.Playback`), `ui/flute/DESIGN.md` (`FA.Flute`) and
`ui/notes/DESIGN.md` (`FA.Notes`). A debug build also
keeps every "Your turn" run on the phone (the audio, a per-frame table and the run's log lines
aligned to it) for replay on the laptop: the `sessions` skill.

```sh
adb logcat -s FA.Tuner FA.Metronome FA.Playback FA.Flute FA.Notes   # everything structured, debug build
adb shell setprop log.tag.FA.Tuner VERBOSE               # per-frame tuner lines (any build)
adb logcat -v time -s FA.Tuner | grep tuner.summary     # one line a second: did frames pass the gate?
```

## Testing conventions

A rule is tested where it lives, in `domain`, as a plain JVM test with no dispatcher and no fake
(`TuneWriterTest`, `ListenRunTest`, `NotesRoundTest`). Other unit tests use hand-written fakes from
`app/src/test/.../fakes/Fakes.kt`; there is no mocking library. ViewModel tests cover the wiring (the
sound, the waits, the saving, the log lines): they run under `StandardTestDispatcher` with
`Dispatchers.setMain` and drive time with `advanceTimeBy` / `advanceUntilIdle`. The screen tests (`app/src/androidTest`,
run on the JVM and on the emulator, "Commands") cover the `*Content` composables, the
settings store (over a throwaway preferences file so the app's own settings are never touched), the
microphone engine's tap and locale-dependent resources; one that only a device can judge starts
with `needsDevice()` (`ui/common/NeedsDevice.kt`). Unit tests that touch the debug-only session
recorder live in `app/src/testDebug` (shared `app/src/test` is also compiled for the release
variant's unit tests). The `config_summary` string is on no screen: the playback notification shows
it and `LabelsTest` checks it.
