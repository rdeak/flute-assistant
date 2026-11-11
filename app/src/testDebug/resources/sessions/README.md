# Pinned sessions

A "Your turn" session recorded by a debug build (`tools/pull-sessions.sh` brings them from the
phone; the design is `docs/superpowers/specs/2026-11-05-flute-listen-session-recorder-design.md`)
can be pinned here as a regression test: copy its folder in, keeping `audio.wav` and
`session.log` (`frames.csv` may stay), and add one line to `session.log` saying what the run
should earn, for example

    pin marks=0:FIRST_TRY,1:FIRST_TRY,2:RETRY

`RecordedSessionsTest` (`./gradlew :app:testDebugUnitTest --tests '*RecordedSessionsTest*'`)
replays every folder here through the real detector, tracker and check and fails when the marks
differ from the pin; a folder without a pin line, or one that cannot be replayed (no
`session.log`, no `audio.wav`, a file that does not read), fails by its name too. Open `replay.txt` after
`-Psession=app/src/testDebug/resources/sessions/<folder>` to see why.

Privacy: the audio is a child's playing and this repository is public, so the folder's
`.gitignore` keeps every session out of git. A pinned session lives on the laptop that pulled
it. Only a synthetic session (one the tests themselves generate) may be force-added.
