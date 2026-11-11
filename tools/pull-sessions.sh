#!/bin/sh
# SPDX-License-Identifier: GPL-3.0-or-later
# Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.
# Pulls the "Your turn" sessions a debug build recorded on the attached phone (or the device
# ANDROID_SERIAL names) into a folder here, ./sessions by default, which git ignores: the audio
# is a child's playing. What a session holds and how to replay, sweep, pin and picture it:
# app/src/main/java/hr/bertlab/fluteassistant/ui/flute/CLAUDE.md, "Sessions".
#
#   tools/pull-sessions.sh [dest]
set -eu
dest=${1:-sessions}
remote=/sdcard/Android/data/hr.bertlab.fluteassistant/files/sessions
if command -v adb >/dev/null 2>&1; then
  adb=adb
elif [ -x "${ANDROID_HOME:-}/platform-tools/adb" ]; then
  adb="$ANDROID_HOME/platform-tools/adb"
elif [ -x "$HOME/Library/Android/sdk/platform-tools/adb" ]; then
  adb="$HOME/Library/Android/sdk/platform-tools/adb"
else
  echo "adb not found: put the SDK's platform-tools on PATH or set ANDROID_HOME" >&2
  exit 2
fi
if ! state=$("$adb" get-state 2>&1); then
  echo "no device: $state" >&2
  echo "plug the phone in or start the emulator, check 'adb devices', and set ANDROID_SERIAL when more than one is attached" >&2
  exit 1
fi
if ! "$adb" shell "test -d $remote" 2>/dev/null; then
  echo "no sessions on the phone yet ($remote): install the debug build and run Your turn once" >&2
  exit 1
fi
mkdir -p "$dest"
"$adb" pull "$remote/." "$dest"
echo "sessions in $dest:"
ls -1 "$dest"
