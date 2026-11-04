---
name: emulator
description: Use when running Flute Assistant on the Android emulator, for instrumented (connected) tests, adb-driven checks, screenshots, Croatian or dark mode, or whenever a phone is attached over USB.
---

# Emulator and instrumented tests

Instrumented-test notes: pass one class at a time (a comma-separated list only ran the first
class here); the task uninstalls the app when it finishes, so `adb install -r` again before any
adb-driven checks; uninstall a release-signed build first or the debug install fails.

A physical phone is often attached over USB, so pin adb to the emulator with
`ANDROID_SERIAL=emulator-5554`. The only AVD is `Medium_Phone_API_37.0`. `-no-audio` is
required: with host audio on, the emulator crashed about 30 s after the metronome started. That
also means the audio output itself can only be judged on a real device.

```sh
$ANDROID_HOME/emulator/emulator -avd Medium_Phone_API_37.0 -no-window -no-audio -gpu swiftshader_indirect -no-boot-anim &
adb shell pm grant hr.bertlab.fluteassistant android.permission.RECORD_AUDIO
adb shell cmd locale set-app-locales hr.bertlab.fluteassistant --user 0 --locales hr   # switch the app to Croatian (the in-app flags do the same)
adb shell cmd uimode night yes                                                         # dark mode
adb exec-out screencap -p > shot.png
```

The notification-permission dialog appears on the first metronome start (API 33+);
`adb shell input keyevent 4` dismisses it.

Reading a `uiautomator dump`: when a merged Compose button has children (an emoji, a label), Compose
adds a child node holding only the button's `content-desc` (and one for its role), and that child
always reads `enabled="true"` and `clickable="false"`. The button's real state (enabled, clickable,
focusable) is on its parent node, the one without the description. Matching a button by its
`content-desc` therefore finds the wrong node. A button without children keeps the description on
itself, so the same button can switch between the two shapes when its content goes.
