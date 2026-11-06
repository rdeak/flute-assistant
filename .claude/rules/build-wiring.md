---
paths:
  - "**/*.gradle.kts"
  - "gradle.properties"
  - "gradle/**"
  - "app/schemas/**"
  - "app/src/debug/**"
  - "app/src/release/**"
  - "app/src/testDebug/**"
  - "app/src/main/AndroidManifest.xml"
  - "app/src/main/res/values/themes.xml"
  - "app/src/main/java/hr/bertlab/fluteassistant/MainActivity.kt"
  - "app/src/main/java/hr/bertlab/fluteassistant/data/local/**"
  - "app/src/main/java/hr/bertlab/fluteassistant/ui/common/AppLanguage.kt"
  - "app/src/main/java/hr/bertlab/fluteassistant/ui/common/LanguageSwitch.kt"
---

# Build wiring (AGP 9)

AGP 9.4.1 uses built-in Kotlin: there is no `kotlin-android` plugin and no kapt. The root
`build.gradle.kts` puts the Kotlin Gradle plugin on the buildscript classpath to raise AGP's
bundled Kotlin to the catalog's `kotlin` version, and the Compose compiler plugin shares that
same version ref; keep the two equal. KSP is the standalone 2.3.x line; Hilt must stay >= 2.59.
Room exports schemas to `app/schemas` (database version 1, destructive fallback migration for
now). `localeFilters` strips every library locale except en and hr.

The build types differ in source: `app/src/debug` holds the "Your turn" session recorder
(`debug/session`, the `RecordingLogger` and the `di/SessionModule` that binds them, plus backup
rules that keep the recordings out of Auto Backup) and `app/src/release` the `di/SessionModule`
that binds the plain logger and no-ops, so the Play build compiles none of the recorder. Unit
tests that touch the recorder live in `app/src/testDebug` (shared `app/src/test` is also compiled
for the release variant's unit tests). The recorder logs through the plain `AndroidLogger`, never
the recording one. Details: the `sessions` skill.

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
