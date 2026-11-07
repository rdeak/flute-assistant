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
adb shell settings put system accelerometer_rotation 0                                # then turn it by hand:
adb shell settings put system user_rotation 1                                         # sideways (3 the other way, 0 upright)
adb shell wm size 945x2100                                                            # the 800 x 360 dp phone; 945x1680 is 640 x 360; `wm size reset` after
```

There is no tablet AVD and `avdmanager` is not installed, so the phone AVD fakes one by its size and
density. `wm density` is what makes it a tablet: `wm size` alone only stretches the phone's dp.

```sh
adb shell pm grant hr.bertlab.fluteassistant android.permission.POST_NOTIFICATIONS   # else the first Play's dialog blocks a scripted run
adb shell wm size 1600x2560; adb shell wm density 320                                # 800 x 1280 dp, 10 inches; 1280x2048 is 640 x 1024, 8 inches
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1                                        # sideways: 1280 x 800 dp
adb shell wm size reset; adb shell wm density reset; adb shell settings put system user_rotation 0   # after
```

The notification-permission dialog appears on the first metronome start (API 33+);
`adb shell input keyevent 4` dismisses it.

Reading a `uiautomator dump`: when a merged Compose button has children (an emoji, a label), Compose
adds a child node holding only the button's `content-desc` (and one for its role), and that child
always reads `enabled="true"` and `clickable="false"`. The button's real state (enabled, clickable,
focusable) is on its parent node, the one without the description. Matching a button by its
`content-desc` therefore finds the wrong node. A button without children keeps the description on
itself, so the same button can switch between the two shapes when its content goes.
