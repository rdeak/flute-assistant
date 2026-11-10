---
name: sessions
description: Use when a child's "Your turn" run on a debug phone did not do what was expected, when pulling, replaying, sweeping or pinning a recorded session (tools/pull-sessions.sh, SessionReplayTest, RecordedSessionsTest, session-report.py), or when changing the debug session recorder.
---

# "Your turn" sessions (debug builds only)

Since 2026-11-05 (spec `docs/superpowers/specs/2026-11-05-flute-listen-session-recorder-design.md`,
asked for after the first child test: when a child plays and the phone does not do what was
expected, the session must be inspectable afterwards with everything in it), the build installed
from Android Studio records every Your turn run, automatically and with nothing on the screen,
into `Android/data/hr.bertlab.fluteassistant/files/sessions/<yyyy-MM-dd_HH-mm-ss>/` on the phone,
the newest `FileSessionRecorder.KEEP` (20) kept: `audio.wav` (the samples the engine analysed,
mono 32-bit float at the microphone's rate, from the first hop after it opened, so hop `k` of the
file is the newest hop of frame `k`), `frames.csv` (one row per frame the view model processed:
frame, ms, f, p, level, minLevel, decision, sounding, onset `fresh`/`step`, verdict, the step
waited on before the judgement and its note, heardNow, advice, repeatHeld, finishing, hearing,
and the tracker's floor; what the phone actually did) and `session.log` (the app's log format:
`session.start` with the app, device and the floor the tracker still held from the run before,
`session.rules` with every threshold in force read from the constants, `session.pulse`, one
`session.tune` per item, `session.audio` when the microphone opened, then every log line the app
wrote while the run was open stamped `frame=<rows written before it> tag=<its tag> level=<d|i|w|e>`,
so a line logged while frame 25 is processed says `frame=24`, and `session.end` with the marks; `flute.listen_stop` is
logged before the run ends so it is in, `mic.close` comes after and is not). The audio may run a
few hops past the last row (frames the phone never processed: a Stop with frames still buffered,
or the run ending by `FINISH_FRAMES`); the replay says how many. Privacy: the audio is a child's
playing; it is written only by the debug build, only into that app-private folder (gone with the
app, excluded from Auto Backup and device transfer by `src/debug/res/xml`), leaves the phone only
over the cable, and never goes into git (the repository is public): `/sessions/` and the pinned
folder are ignored.

How it is wired: `AudioRecordTunerEngine` has an `AudioTap` (`opened`, `samples` per hop on its IO
thread, never for a hop read after the collector cancelled, since a run restarted within one hop
would otherwise begin with the last hop of the run before, `closed`; `AudioRecordTunerEngineTest`
checks that on the emulator's microphone) and `FluteViewModel` a `ListenRecorder` (`start` before `flute.listen` and the
microphone, a `ListenFrame` per frame after the judgement and before the run may end, `end` with
the marks); `src/debug` implements both, plus the `RecordingLogger`'s `LogSink`, in one
`FileSessionRecorder` bound by `di/SessionModule` (`src/release` binds the plain logger and the
no-ops, and compiles none of the recorder); the recorder logs through the plain `AndroidLogger`,
never the recording one. `NoteOnsetTracker` takes its thresholds as `Rules` (defaults are the
constants) and a seeded floor, for the replay. Unit tests that touch the recorder live in
`src/testDebug`.

On the laptop (every command from the repository root; the replay is a test only because that
is what runs the app's classes on the JVM):

```sh
tools/pull-sessions.sh                                   # the phone's sessions into ./sessions (gitignored)
./gradlew :app:testDebugUnitTest --tests '*SessionReplayTest*' -Psession=sessions/<folder>
                                                         # writes <folder>/replay.txt: the real detector, tracker and check over audio.wav,
                                                         # the onsets and marks, and the diff against frames.csv (none: the rules did it,
                                                         # work on them here; some: timing on the phone, the first frame says where)
./gradlew :app:testDebugUnitTest --tests '*SessionReplayTest*' -Psession=sessions/<folder> -Psweep=stopDrop:0.08,0.12,0.2
                                                         # also <folder>/sweep.txt: what each value would have heard
python3 tools/session-report.py sessions/<folder>        # <folder>/report.html: the run on a timeline, hover for a frame's row
```

`ListenPath` (test source set) frames the audio as the engine does and feeds it to `ListenRun`
(`domain/flute`), the same class the app's view model feeds the microphone to, so the synthetic
`ListenPathTest`, the replay and the phone judge a run by one set of rules. A session can be pinned as a regression test: copy it into
`app/src/testDebug/resources/sessions/<name>/` with a `pin marks=0:FIRST_TRY,...` line added to
its log; `RecordedSessionsTest` replays every folder there and fails by the folder's name, for
other marks, a missing pin line, or a folder that cannot be replayed (`frames.csv` may be left
out of a pinned folder, the audio and the log may not; its README has the rules; the folder is
gitignored but for the README).
