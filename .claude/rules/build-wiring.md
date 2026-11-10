---
paths:
  - "**/*.gradle.kts"
  - "gradle.properties"
  - "gradle/**"
  - "app/src/debug/**"
  - "app/src/release/**"
  - "app/src/testDebug/**"
  - "app/src/main/AndroidManifest.xml"
  - "app/src/main/res/values/themes.xml"
  - "app/src/main/java/hr/bertlab/fluteassistant/MainActivity.kt"
  - "app/src/main/java/hr/bertlab/fluteassistant/ui/common/AppLanguage.kt"
  - "app/src/main/java/hr/bertlab/fluteassistant/ui/common/LanguageSwitch.kt"
---

# Build wiring (AGP 9)

AGP 9.4.1 uses built-in Kotlin: there is no `kotlin-android` plugin and no kapt. The root
`build.gradle.kts` puts the Kotlin Gradle plugin on the buildscript classpath to raise AGP's
bundled Kotlin to the catalog's `kotlin` version, and the Compose compiler plugin shares that
same version ref; keep the two equal. KSP is the standalone 2.3.x line and runs for Hilt alone
(Room went with the presets on 2026-11-09); Hilt must stay >= 2.59. `localeFilters` strips every
library locale except en and hr.

The build types differ in source: `app/src/debug` holds the "Your turn" session recorder
(`debug/session`, the `RecordingLogger` and the `di/SessionModule` that binds them, plus backup
rules that keep the recordings out of Auto Backup) and `app/src/release` the `di/SessionModule`
that binds the plain logger and no-ops, so the Play build compiles none of the recorder. Unit
tests that touch the recorder live in `app/src/testDebug` (shared `app/src/test` is also compiled
for the release variant's unit tests). The recorder logs through the plain `AndroidLogger`, never
the recording one. Details: the `sessions` skill.

The screen tests in `app/src/androidTest` are also a source folder of the `test` source set
(`sourceSets` in `app/build.gradle.kts`, since 2026-11-10), so `testDebugUnitTest` runs them on the
JVM under Robolectric (4.17, with `robolectric.properties` in `app/src/test/resources`: native
graphics and the emulator's 411 x 914 dp window). That needs `unitTests.isIncludeAndroidResources`,
the `--add-exports=java.base/jdk.internal.access=ALL-UNNAMED` JVM argument (Robolectric on Java
17+) and Espresso 3.7 on the test classpath (the one the Compose rule brings calls
`InputManager.getInstance()`, gone since API 34). A test class there needs
`@RunWith(AndroidJUnit4::class)`, which picks Robolectric on the JVM and the device runner on the
emulator. Android Studio lists a folder under one source set only, so it shows these files as
instrumented tests; Gradle runs them both ways.

`androidx.appcompat` is in the build only for the in-app language switch:
`AppCompatDelegate.setApplicationLocales` is the per-app-language backport below Android 13, so
`MainActivity` is an `AppCompatActivity`, the window theme must stay an AppCompat theme
(`Theme.AppCompat.DayNight.NoActionBar`) and the manifest declares
`AppLocalesMetadataHolderService` with `autoStoreLocales`. The choice is stored by the platform
(system on 13+, AppCompat below), not by `SettingsStore`.

`MainActivity` declares `android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout"` so
a turn of the phone does not re-create it and a running tune, Your turn, the tuner or the metronome go on
(`TurningThePhoneTest` reads the flags). The locale must stay out of the list: the language switch relies
on the re-creation.
