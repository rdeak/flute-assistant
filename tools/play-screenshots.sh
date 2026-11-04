#!/bin/bash
# Play Store phone screenshots: one per tab, English and Croatian, light mode, padded to 9:16.
#
# Needs the headless emulator (AVD Medium_Phone_API_37.0, 1080x2400, started with -no-audio, see
# CLAUDE.md) and a debug APK (./gradlew assembleDebug). Installs the APK, clears the app's data,
# drives each tab through uiautomator and writes docs/release/play-listing/screenshots/<lang>/N-tab.jpg.
# Play wants 9:16 and no alpha channel, so each 1080x2400 capture is scaled to 864x1920, padded to
# 1080x1920 with the app's sky-blue page colour and saved as JPEG.
set -u
cd "$(dirname "$0")/.." || exit 2
ADB=${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}
export ANDROID_SERIAL=${ANDROID_SERIAL:-emulator-5554}
export PYTHONUTF8=1
PKG=hr.bertlab.fluteassistant
OUT=${1:-docs/release/play-listing/screenshots}
RAW=$(mktemp -d)
BG=EAF4FF # ui/theme/Color.kt LightFluteColors.page

PY='
import sys, re, html
mode, want = sys.argv[1], sys.argv[2]
nodes = re.findall(r"<node [^>]*>", sys.stdin.read())
def val(n, attr):
    m = re.search(" " + attr + "=\"([^\"]*)\"", n)
    return html.unescape(m.group(1)) if m else ""
if mode == "exact":  # keys and buttons carry a content description; the name row and pills only text
    hit = next((n for n in nodes if val(n, "content-desc") == want), None) or next((n for n in nodes if val(n, "text") == want), None)
elif mode == "desc":
    hit = next((n for n in nodes if re.fullmatch(want, val(n, "content-desc"))), None)
else:
    hit = next((n for n in nodes if re.fullmatch(want, val(n, "text"))), None)
if hit:
    x1, y1, x2, y2 = map(int, re.search(r"bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", hit).groups())
    print((x1 + x2) // 2, (y1 + y2) // 2, val(hit, "text") or val(hit, "content-desc"))
'
find_node() { $ADB exec-out uiautomator dump /dev/tty 2>/dev/null | python3 -c "$PY" "$1" "$2"; } # prints "x y label"
tap() { # $1 exact label; or "text <regex>" / "desc <regex>" for a pattern on the text or the description
  local r x y mode=exact want=$1
  case $1 in text\ *|desc\ *) mode=${1%% *}; want=${1#* };; esac
  r=$(find_node "$mode" "$want"); [ -n "$r" ] || { echo "no node: $1" >&2; return 1; }
  read -r x y _ <<<"$r"; $ADB shell input tap "$x" "$y"; $ADB shell sleep 0.4
}
wait_node() { # $1 exact label: waits for it to appear (the first start after an install is slow)
  local i
  for i in $(seq 20); do [ -n "$(find_node exact "$1")" ] && { echo "$1 up after $i dump(s)"; return 0; }; $ADB shell sleep 0.5; done
  echo "never appeared: $1" >&2; return 1
}
hold() { # $1 exact label, $2 ms: keeps a finger on it that long without moving
  local r x y
  r=$(find_node exact "$1"); [ -n "$r" ] || { echo "no node: $1" >&2; return 1; }
  read -r x y _ <<<"$r"; $ADB shell input swipe "$x" "$y" "$x" "$y" "$2"; $ADB shell sleep 0.2
}
shot() { # $1 language, $2 file name
  local raw="$RAW/$1-$2.png" out="$OUT/$1/$2.jpg"
  mkdir -p "$OUT/$1"
  $ADB exec-out screencap -p > "$raw"
  sips -z 1920 864 "$raw" --out "$RAW/$1-$2-scaled.png" >/dev/null &&
    sips -p 1920 1080 --padColor $BG "$RAW/$1-$2-scaled.png" >/dev/null &&
    sips -s format jpeg -s formatOptions 95 "$RAW/$1-$2-scaled.png" --out "$out" >/dev/null
  echo "shot $out"
}
answer_note() { # taps the staff place of the note named on screen (C4..C6 in English, c1..c3 in Croatian)
  local r x y name pos
  r=$(find_node text "[A-Ha-h][1-6]"); [ -n "$r" ] || { echo "no note name on screen" >&2; return 1; }
  name=${r##* }
  pos=$(python3 -c "
import sys
n = sys.argv[1]; letter = n[0].upper().replace(\"H\", \"B\"); octave = int(n[1])
lowest = 1 if n[0].islower() else 4  # c1 in Croatian, C4 in English, is staff position 1
print(\"CDEFGAB\".index(letter) + 7 * (octave - lowest) + 1)" "$name")
  # The place by its own name only: "Streak of 2" carries a number too.
  tap "desc (Staff position $pos|Mjesto $pos u crtovlju)" || { echo "no staff position $pos" >&2; return 1; }
  echo "$name -> staff position $pos"
}

run_language() { # lang, flute tab, metronome tab, tuner tab, notes tab, play, stop, target-mode pill, tune notes...
  local L=$1 TAB_FLUTE=$2 TAB_METRONOME=$3 TAB_TUNER=$4 TAB_NOTES=$5 PLAY=$6 STOP=$7 TARGET=$8; shift 8
  local TUNE=("$@") last i n r x y
  last=${TUNE[$((${#TUNE[@]} - 1))]}
  $ADB shell pm clear $PKG >/dev/null
  $ADB shell pm grant $PKG android.permission.RECORD_AUDIO
  $ADB shell pm grant $PKG android.permission.POST_NOTIFICATIONS
  $ADB shell cmd locale set-app-locales $PKG --user 0 --locales "$L"
  $ADB shell am start -n $PKG/.MainActivity >/dev/null
  # The shots follow the tab bar's order, so the listing leads with the tab the app opens on. Each
  # tab is tapped, never assumed open: in English the flute's Play and the metronome's share a label.
  # Flute: the first notes of a tune played, the last one held while the shot is taken. The keys
  # are held right after the start, so wait for the screen: a key held too early is lost.
  wait_node "$TAB_FLUTE" || return 1; $ADB shell sleep 1
  tap "$TAB_FLUTE"; $ADB shell sleep 1
  # A note is as long as its key is held, in beats of the metronome's tempo (120 after pm clear,
  # so 500 ms is a beat): each a quarter note, then the last held past two beats during the shot,
  # so the held step's yellow band has grown to a half note, as on the canvas board.
  for n in "${TUNE[@]:0:$((${#TUNE[@]} - 1))}"; do hold "$n" 500; done
  r=$(find_node exact "$last"); read -r x y _ <<<"$r"
  $ADB shell input swipe "$x" "$y" "$x" "$y" 2500 & $ADB shell sleep 1.0; shot "$L" 1-flute; wait
  # Metronome, running so a beat bubble is lit with the speed's animal on it.
  tap "$TAB_METRONOME"; $ADB shell sleep 1
  tap "$PLAY"; $ADB shell sleep 1.3; shot "$L" 2-metronome; tap "$STOP"
  # Tuner in "Tune" mode: the target note in its pitch window and its fingering show without a microphone.
  tap "$TAB_TUNER"; $ADB shell sleep 1; tap "text .*$TARGET"; $ADB shell sleep 1; shot "$L" 3-tuner
  # Notes: three right answers, then the fourth caught during its feedback (the note in colour, flames).
  tap "$TAB_NOTES"; $ADB shell sleep 1
  for i in 1 2 3; do answer_note; $ADB shell sleep 1.6; done
  answer_note; $ADB shell sleep 0.3; shot "$L" 4-notes; $ADB shell sleep 1.5
}

$ADB install -r app/build/outputs/apk/debug/app-debug.apk >/dev/null || exit 1
$ADB shell cmd uimode night no >/dev/null
rm -f "$OUT"/*/[0-9]-*.jpg # a renamed tab must not leave its old shot behind
run_language en Flute Metronome Tuner Notes Play Stop Tune C4 C4 G4 G4 A4 A4 G4
run_language hr Flauta Metronom Štimer Note Sviraj Stani Ugodi c1 c1 g1 g1 a1 a1 g1
$ADB shell pm clear $PKG >/dev/null
$ADB shell cmd locale set-app-locales $PKG --user 0 --locales en
rm -rf "$RAW"
ls -la "$OUT"/*
