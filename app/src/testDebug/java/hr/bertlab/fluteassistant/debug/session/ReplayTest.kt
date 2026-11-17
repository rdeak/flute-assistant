// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.debug.session

import hr.bertlab.fluteassistant.audio.tuner.AudioRecordTunerEngine
import hr.bertlab.fluteassistant.domain.flute.ListenFrame
import hr.bertlab.fluteassistant.domain.flute.ListenRun
import hr.bertlab.fluteassistant.domain.flute.MarkChange
import hr.bertlab.fluteassistant.domain.flute.NoteLength.HALF
import hr.bertlab.fluteassistant.domain.flute.NoteLength.QUARTER
import hr.bertlab.fluteassistant.domain.flute.Pitch
import hr.bertlab.fluteassistant.domain.flute.Pulse
import hr.bertlab.fluteassistant.domain.flute.StepMark
import hr.bertlab.fluteassistant.domain.flute.TuneItem
import hr.bertlab.fluteassistant.domain.flute.TuneNote
import hr.bertlab.fluteassistant.domain.flute.Verdict
import hr.bertlab.fluteassistant.domain.listen.PitchFrame
import hr.bertlab.fluteassistant.domain.listen.FrameDecision
import hr.bertlab.fluteassistant.domain.flute.Walk
import hr.bertlab.fluteassistant.domain.listen.Hearing
import hr.bertlab.fluteassistant.domain.listen.NoteOnsetTracker
import hr.bertlab.fluteassistant.domain.listen.TunerAdvice
import hr.bertlab.fluteassistant.fakes.FakeLogger
import hr.bertlab.fluteassistant.ui.flute.FluteSound
import hr.bertlab.fluteassistant.ui.flute.ListenPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The replay of a recorded session: [FluteSound]'s "ta ta taaa" with 50 ms tongued gaps is
 * recorded as the phone would record it (the audio hop by hop, [ListenPath]'s steps as the rows)
 * and read back, replayed, diffed and swept.
 */
class ReplayTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val rate = FluteSound.RATE
    private val hop = AudioRecordTunerEngine.HOP_SIZE
    private val tune: List<TuneItem> = listOf(TuneNote(71, QUARTER), TuneNote(71, QUARTER), TuneNote(71, HALF))
    private val samples = FluteSound.render(FluteSound.sound(gapsMs = 50, roomDb = -10.0, floorDb = -50.0))

    /**
     * Records the run as the phone would and returns its folder; [extraHops] of audio after the last row, as a Stop
     * with frames still buffered leaves. The phone heard [samples] under [rules].
     */
    private fun recorded(
        extraHops: Int = 0,
        samples: FloatArray = this.samples,
        rules: NoteOnsetTracker.Rules = NoteOnsetTracker.Rules(),
        pulse: Pulse = Pulse(90),
        run: ListenRun.Rules = ListenRun.Rules(),
        tune: List<TuneItem> = this.tune,
    ): File {
        val run = ListenPath.run(samples, rate, tune, pulse, rules, run = run)
        val r = FileSessionRecorder(folder.root, FileSessionRecorder.Facts("1.0.2", 1, "JVM", 0), FakeLogger()) { 0L }
        r.start(ListenRun.Setup(tune, pulse))
        r.opened(rate, "MIC")
        for (step in run.steps) {
            r.samples(samples.copyOfRange((step.frame - 1) * hop, step.frame * hop), hop)
            r.frame(step.row)
        }
        repeat(extraHops) { r.samples(FloatArray(hop), hop) }
        r.end(run.marks)
        return File(folder.root, "sessions").listFiles()!!.single()
    }

    @Test
    fun `a session the recorder wrote loads and replays with no diff`() {
        val session = Session.load(recorded())
        assertEquals(tune, session.tune)
        assertEquals(rate, session.audio.sampleRate)
        assertNull(session.floor)
        assertEquals("0.12", session.rules["stopDrop"])
        assertNull(session.pin)
        assertTrue(session.events.any { it.startsWith("session.audio ") })
        val report = Replay.report(session)
        assertTrue(report, report.contains("diff: none"))
        assertEquals(report, 3, Regex("verdict=right").findAll(report.substringBefore("diff:")).count())
        assertTrue(report, report.contains("marks=0:FIRST_TRY,1:FIRST_TRY,2:FIRST_TRY"))
        assertFalse("recorded under today's rules, so there is nothing more to say: $report", report.contains("today's rules"))
    }

    /**
     * A session recorded before a rule existed ran without it, so its `session.rules` line does not name it: the
     * replay that is compared with the phone runs without it too (no diff: the phone did what its rules said), and
     * the report then says what today's rules make of the same sound. Here: notes far too faint for the floor, which
     * the phone of that day counted.
     */
    @Test
    fun `a session older than a rule replays under its own rules, and the report adds today's`() {
        val faint = FluteSound.render(FluteSound.sound(gapsMs = 50, toneDb = -45.0, roomDb = -10.0, floorDb = -70.0))
        val dir = recorded(samples = faint, rules = NoteOnsetTracker.Rules(minLevel = 0f))
        val log = File(dir, "session.log")
        val written = log.readText()
        assertTrue(written, written.contains(" minLevel=0.45"))
        log.writeText(written.replace(" minLevel=0.45", "")) // as a build before the floor wrote it
        val session = Session.load(dir)
        assertNull(session.rules["minLevel"])
        assertEquals(0f, Replay.recordedRules(session).minLevel)

        val report = Replay.report(session)
        val then = report.substringBefore("today's rules")
        assertTrue(report, then.contains("diff: none"))
        assertTrue(report, then.contains("marks=0:FIRST_TRY,1:FIRST_TRY,2:FIRST_TRY"))
        assertTrue(report, report.contains("today's rules differ: minLevel=0.45 (recorded none)"))
        val now = report.substringAfter("today's rules").substringBefore("phone:")
        assertTrue("under the floor nothing of it is a note: $report", now.contains("marks=\n") && !now.contains("verdict="))
    }

    @Test
    fun `an altered row is reported with its frame`() {
        val dir = recorded()
        val csv = File(dir, "frames.csv")
        val lines = csv.readLines().toMutableList()
        val cells = lines[30].split(',').toMutableList()
        cells[6] = "IGNORED" // a decision the tracker never gives
        lines[30] = cells.joinToString(",")
        csv.writeText(lines.joinToString("\n") + "\n")
        val session = Session.load(dir)
        val diff = Replay.diff(session.rows, ListenPath.run(session.audio.samples, rate, tune, session.pulse).steps)
        assertEquals(30, diff.first)
        assertEquals(1, diff.count)
        assertEquals(0, diff.trailing)
        assertTrue(Replay.report(session).contains("diff: first=30 count=1"))
    }

    @Test
    fun `hops the phone never processed are counted, not diffed`() {
        val session = Session.load(recorded(extraHops = 3))
        val diff = Replay.diff(session.rows, ListenPath.run(session.audio.samples, rate, tune, session.pulse).steps)
        assertEquals(0, diff.count)
        assertEquals(3, diff.trailing)
        assertTrue(Replay.report(session).contains("diff: none (3 hops after the last row)"))
    }

    @Test
    fun `a sweep tries each value and tabulates what was heard`() {
        val out = Replay.sweep(Session.load(recorded()), "stopDrop:0.08,0.12,0.5")
        val lines = out.lines().filter { it.startsWith("stopDrop=") }
        assertEquals(out, 3, lines.size)
        assertTrue(lines[1], lines[1].startsWith("stopDrop=0.12 heard=71,71,71 marks=0:FIRST_TRY,1:FIRST_TRY,2:FIRST_TRY"))
        // 0.5 cannot hear a tongued stop, and the gap rule cannot see 50 ms: the three notes are one, the child's "h1 h1 long h1" miss.
        // That one note sounds 2.4 s on a quarter, held too long (since 2026-11-16).
        assertTrue(lines[2], lines[2].startsWith("stopDrop=0.5 heard=71 marks=0:LONG"))
        assertFalse(lines[2].contains("1:FIRST_TRY"))
    }

    /**
     * A session recorded before 2026-11-11 names no `lengthSlack` (nor `restartCuts`): its phone checked pitch only, so
     * its replay does too, and the report says what the length rule makes of it. Here at 40 a minute, where a quarter
     * needs 375 ms and the half note 1875, and the notes are 400, 400 and 1500.
     */
    @Test
    fun `a session without lengthSlack replays pitch only, and the report adds the length rule`() {
        val dir = recorded(pulse = Pulse(40), run = ListenRun.Rules.PITCH_ONLY)
        val log = File(dir, "session.log")
        // As a build before the length rule wrote it.
        log.writeText(log.readText().replace(Regex(" (lengthSlack|restartCuts)=[^ \n]*"), ""))
        val session = Session.load(dir)
        assertEquals(Pulse(40), session.pulse)
        val recorded = Replay.recordedRunRules(session)
        assertEquals("no length rule then", Double.POSITIVE_INFINITY, recorded.lengthSlack, 0.0)
        assertTrue("a restart cut a note then", recorded.restartCuts)
        assertEquals("a rule the line names is the phone's", 3000.0, recorded.maxNeedMs, 0.0)
        val report = Replay.report(session)
        val then = report.substringBefore("today's rules")
        assertTrue(report, then.contains("diff: none"))
        assertTrue(report, then.contains("marks=0:FIRST_TRY,1:FIRST_TRY,2:FIRST_TRY"))
        assertTrue(report, report.contains("today's rules differ: lengthSlack=1.5 (recorded none) restartCuts=0 (recorded none)"))
        val now = report.substringAfter("today's rules").substringBefore("phone:")
        assertTrue(report, now.contains("marks=0:FIRST_TRY,1:FIRST_TRY,2:SHORT\n"))
    }

    @Test
    fun `a session from before the feedback rules replays with a short note waiting`() {
        // At 40 a minute the half note's 1500 ms are too short: a build before 2026-11-14 waited on it.
        val before = ListenRun.Rules(highCents = 0.0, tuneCents = Double.POSITIVE_INFINITY, shortMovesOn = false)
        val dir = recorded(pulse = Pulse(40), run = before)
        val log = File(dir, "session.log")
        // As a build before 2026-11-14 wrote it: none of its rules named, and a table that ends at `phase`.
        log.writeText(log.readText().replace(Regex(" (highCents|tuneCents|tuneFrames|shortMovesOn|paceMinPairs|paceOff)=[^ \n]*"), ""))
        val csv = File(dir, "frames.csv")
        csv.writeText(csv.readLines().joinToString("\n") { it.substringBeforeLast(',').substringBeforeLast(',') } + "\n") // pitch and changed
        val session = Session.load(dir)
        val run = Replay.recordedRunRules(session)
        assertEquals("no band above a note", 0.0, run.highCents, 0.0)
        assertEquals("no arrow", Double.POSITIVE_INFINITY, run.tuneCents, 0.0)
        assertFalse("a short note waited", run.shortMovesOn)
        assertFalse(session.columns.pitch)
        assertTrue(session.rows.any { it.walk?.phase == Walk.Phase.SHORT })
        val report = Replay.report(session)
        val then = report.substringBefore("today's rules")
        assertTrue(report, then.contains("diff: none"))
        assertTrue(report, then.contains("marks=0:FIRST_TRY,1:FIRST_TRY\n"))
        val now = report.substringAfter("today's rules").substringBefore("phone:")
        assertTrue(report, now.contains("marks=0:FIRST_TRY,1:FIRST_TRY,2:SHORT\n"))
    }

    @Test
    fun `a session recorded before rests were walked replays them passed over`() {
        val dir = recorded()
        val log = File(dir, "session.log")
        log.writeText(log.readText().replace(" walkRests=1", ""))
        assertFalse(Replay.recordedRunRules(Session.load(dir)).walkRests)
    }

    @Test
    fun `a lengthSlack sweep gives one line per value`() {
        val out = Replay.sweep(Session.load(recorded(pulse = Pulse(60))), "lengthSlack:0,8")
        val lines = out.lines().filter { it.startsWith("lengthSlack=") }
        assertEquals(out, 2, lines.size)
        // 0: a quarter needs all of its 1000 ms. The tongued stops do not cut it ([ListenRun.Rules.restartCuts]): the
        // three notes walk as one, which the third makes long enough for the first step, and nothing is left for the others;
        // that one note, 2.4 s on a quarter, is held too long (since 2026-11-16).
        assertTrue(lines[0], lines[0].startsWith("lengthSlack=0 heard=71,71,71 ") && lines[0].endsWith("marks=0:LONG"))
        assertTrue(lines[1], lines[1].startsWith("lengthSlack=8 heard=71,71,71 marks=0:FIRST_TRY,1:FIRST_TRY,2:FIRST_TRY"))
    }

    @Test
    fun `a maxNeedMs sweep gives one line per value, and a session without it had no cap`() {
        val dir = recorded(pulse = Pulse(40))
        val log = File(dir, "session.log")
        log.writeText(log.readText().replace(" maxNeedMs=3000.0", ""))
        val session = Session.load(dir)
        assertEquals(Double.POSITIVE_INFINITY, Replay.recordedRunRules(session).maxNeedMs, 0.0)
        assertEquals(ListenRun.LENGTH_SLACK, Replay.recordedRunRules(session).lengthSlack, 0.0)
        // At 40 the half note needs 1875 ms, more than its 1500; capped at 300 it is long enough.
        val out = Replay.sweep(session, "maxNeedMs:300,3000")
        val lines = out.lines().filter { it.startsWith("maxNeedMs=") }
        assertEquals(out, 2, lines.size)
        assertTrue(lines[0], lines[0].startsWith("maxNeedMs=300 ") && lines[0].endsWith("marks=0:FIRST_TRY,1:FIRST_TRY,2:FIRST_TRY"))
        assertTrue(lines[1], lines[1].startsWith("maxNeedMs=3000 ") && lines[1].endsWith("marks=0:FIRST_TRY,1:FIRST_TRY,2:SHORT"))
    }

    // ---- The walk in a session (the review of 2026-11-11): read back, and part of the diff ----

    @Test
    fun `the rows carry where the animal was, and a walk the phone and the replay disagree on is a difference`() {
        val dir = recorded()
        val session = Session.load(dir)
        assertTrue(session.columns.walk)
        assertTrue(session.rows.any { it.walk?.phase == Walk.Phase.SOUNDING })
        assertTrue(session.rows.any { it.walk?.phase == Walk.Phase.DONE })
        assertTrue(Replay.report(session).contains("diff: none"))

        // The phone says a note was cut short on a frame where the rules say it still sounded: the frame a note
        // was earned on, or the hop the child saw, went another way than the replay's.
        val csv = File(dir, "frames.csv")
        val lines = csv.readLines().toMutableList()
        val at = lines.indexOfFirst { it.contains(",SOUNDING,") } // the phase, before the arrow's cell
        lines[at] = lines[at].replace(",SOUNDING,", ",SHORT,")
        csv.writeText(lines.joinToString("\n") + "\n")
        val altered = Session.load(dir)
        val report = Replay.report(altered)
        assertTrue(report, report.contains("diff: first=$at count=1"))
        assertTrue(report, report.contains("SHORT") && report.contains("SOUNDING"))
        assertEquals("the walk is compared only when asked", 0, Replay.diff(altered.rows, ListenPath.run(altered.frames, tune, altered.pulse).steps).count)
    }

    /**
     * Until 2026-11-13 a note heard while a note cut short waited was logged "wrong", though the screen never showed it
     * as wrong (the child went on); since then it is "ignored". Only the word changed, so it is no difference.
     */
    @Test
    fun `a wrong note logged while a note cut short waited is the ignored one of today`() {
        val short = Walk(0, 2, Walk.Phase.SHORT)
        fun step(verdict: Verdict, walk: Walk?) = ListenPath.Step(
            9,
            ListenFrame(
                PitchFrame(330.0, 0.99, 0.7f), FrameDecision.SHOWN, 64, NoteOnsetTracker.Onset(64, fresh = true), null, verdict,
                0, 60, 64, TunerAdvice.HOLD_LONGER, false, false, Hearing.CLEAR, walk,
            ),
        )
        assertEquals(0, Replay.diff(listOf(step(Verdict.WRONG, short)), listOf(step(Verdict.IGNORED, short)), Columns(walk = true)).count)
        assertEquals("with no note cut short, wrong is wrong", 1, Replay.diff(listOf(step(Verdict.WRONG, null)), listOf(step(Verdict.IGNORED, null))).count)
    }

    @Test
    fun `a table from before the walk is not compared on it`() {
        val dir = recorded()
        val csv = File(dir, "frames.csv")
        csv.writeText(csv.readLines().joinToString("\n") { it.split(',').dropLast(3).joinToString(",") } + "\n")
        val session = Session.load(dir)
        assertFalse(session.columns.walk)
        assertTrue(session.rows.all { it.walk == null })
        assertTrue(Replay.report(session).contains("diff: none"))
    }

    @Test
    fun `the rows are timed at the rate the session was recorded at`() {
        val r = FileSessionRecorder(folder.root, FileSessionRecorder.Facts("1.0.2", 1, "JVM", 0), FakeLogger()) { 0L }
        r.start(ListenRun.Setup(tune, Pulse(90)))
        r.opened(44_100, "MIC")
        r.samples(FloatArray(hop), hop)
        r.frame(ListenFrame(PitchFrame(null, 0.0, 0f), FrameDecision.SILENT, null, null, null, null, 0, 71, null, TunerAdvice.NONE, false, false, Hearing.CLEAR))
        r.end(emptyMap())
        val session = Session.load(File(folder.root, "sessions").listFiles()!!.single())
        assertEquals(PitchFrame.hopMsAt(44_100), session.rows.single().pitch.hopMs, 0.0)
        assertEquals(PitchFrame.hopMsAt(44_100), session.frames.frames.single().hopMs, 0.0)
    }

    @Test
    fun `a bad sweep spec is refused`() {
        val session = Session.load(recorded())
        assertThrows(IllegalArgumentException::class.java) { Replay.sweep(session, "nope:1") }
        assertThrows(IllegalArgumentException::class.java) { Replay.sweep(session, "stopDrop") }
    }

    @Test
    fun `a pin line is read as the marks the run should earn`() {
        val dir = recorded()
        File(dir, "session.log").appendText("pin marks=0:FIRST_TRY,1:RETRY,2:FIRST_TRY\n")
        assertEquals(mapOf(0 to StepMark.FIRST_TRY, 1 to StepMark.RETRY, 2 to StepMark.FIRST_TRY), Session.load(dir).pin)
    }

    // ---- The arrow a phone showed (2026-11-14) ----

    @Test
    fun `a session keeps the arrow it showed, and one from before has none to compare`() {
        val dir = recorded()
        assertTrue(Session.load(dir).columns.pitch)
        val csv = File(dir, "frames.csv")
        // As a build before 2026-11-14 wrote it: no pitch, and so no changed column after it either.
        csv.writeText(csv.readLines().joinToString("\n") { it.substringBeforeLast(',').substringBeforeLast(',') } + "\n")
        assertFalse(Session.load(dir).columns.pitch)
        assertTrue(Replay.report(Session.load(dir)).contains("diff: none"))
    }

    @Test
    fun `the arrow a phone showed is compared when the table has it`() {
        val session = Session.load(recorded())
        val replay = ListenPath.run(session.audio.samples, rate, tune, session.pulse).steps
        val sounding = session.rows.indexOfFirst { it.row.walk?.phase == Walk.Phase.SOUNDING }
        assertTrue(sounding >= 0)
        val phone = session.rows.mapIndexed { i, step -> if (i == sounding) step.copy(row = step.row.copy(pitch = Pitch.LOW)) else step }
        assertEquals(1, Replay.diff(phone, replay, Columns(pitch = true)).count)
        assertEquals(0, Replay.diff(phone, replay).count)
    }

    // ---- A note held too long (2026-11-16): a mark that changes, the room to run over ----

    /** Four quarters at 120: the third of the sound's notes, 1500 ms, runs two beats past its 500 ms. */
    private val quarters: List<TuneItem> = List(4) { TuneNote(71, QUARTER) }

    @Test
    fun `a session from before the levels loads and replays to its marks`() {
        val dir = recorded(pulse = Pulse(120), tune = quarters, run = ListenRun.Rules(longSlack = Double.POSITIVE_INFINITY))
        // As a build before the levels wrote it: no changed column, no longSlack nor wholeNote.
        val csv = File(dir, "frames.csv")
        csv.writeText(csv.readLines().joinToString("\n", postfix = "\n") { it.substringBeforeLast(',') })
        val log = File(dir, "session.log")
        log.writeText(log.readText().replace(Regex(" (longSlack|wholeNote)=[^ \n]*"), ""))
        val session = Session.load(dir)
        assertFalse(session.columns.changed)
        assertEquals(Double.POSITIVE_INFINITY, Replay.recordedRunRules(session).longSlack, 0.0)
        val report = Replay.report(session)
        assertTrue(report, report.lines().any { it.startsWith("diff: none") })
        assertTrue(report, report.contains("  marks=0:FIRST_TRY,1:FIRST_TRY,2:FIRST_TRY\n"))
        assertTrue("today's rules hold the third note too long: $report", report.contains("  marks=0:FIRST_TRY,1:FIRST_TRY,2:LONG\n"))
    }

    @Test
    fun `a changed mark is written down, read back and compared`() {
        val session = Session.load(recorded(pulse = Pulse(120), tune = quarters))
        assertTrue(session.columns.changed)
        val changed = session.rows.single { it.row.changed != null }
        assertEquals(MarkChange(2, StepMark.LONG), changed.row.changed)
        assertTrue(Replay.report(session).lines().any { it.startsWith("diff: none") })
        val roomier = ListenPath.run(session.frames, quarters, session.pulse, run = ListenRun.Rules(longSlack = 100.0))
        val diff = Replay.diff(session.rows, roomier.steps, session.columns)
        assertEquals(changed.frame, diff.first)
    }

    @Test
    fun `the room to run over can be swept`() {
        val session = Session.load(recorded(pulse = Pulse(120), tune = quarters))
        assertEquals(3, Replay.sweep(session, "longSlack:1,1.5,100").lines().count { it.startsWith("longSlack=") })
    }

    @Test
    fun `an old session's listenLevel and wholeNote are read and ignored`() {
        // A build of 16 November wrote the child's level on the start line and Exam's whole-note rule on the rules line;
        // since 2026-11-17 there is one set of rules, and the replay reads both and runs the one.
        val dir = recorded()
        val log = File(dir, "session.log")
        log.writeText(log.readText().replace(Regex(" listenLevel=[^ \n]*"), "").replace("session.start ", "session.start listenLevel=exam ")
            .replace(Regex(" wholeNote=[^ \n]*"), "").replace(Regex("(session\\.rules .*)"), "$1 wholeNote=1"))
        val session = Session.load(dir)
        assertEquals("exam", session.facts["listenLevel"])
        assertEquals("1", session.rules["wholeNote"])
        assertEquals(ListenRun.Rules(), Replay.recordedRunRules(session))
        val report = Replay.report(session)
        assertTrue(report, report.lines().any { it.startsWith("diff: none") })
        assertFalse(report, report.lines().contains("exam:"))
    }

    // ---- Legato (2026-11-16): a joined note is recorded, and a session from before replays without the slur check ----

    @Test
    fun `a joined note is recorded and read back joined`() {
        val joined = listOf(TuneNote(71, QUARTER), TuneNote(72, QUARTER, joined = true))
        val session = Session.load(recorded(tune = joined))
        assertEquals(joined, session.tune)
        val report = Replay.report(session)
        assertTrue(report, report.lines().any { it.startsWith("tune: ") && it.contains("1:⌒72/QUARTER") })
    }

    @Test
    fun `a session with no slurCheck in its rules replays without the slur check`() {
        val dir = recorded()
        assertTrue(Replay.recordedRunRules(Session.load(dir)).slurCheck)
        val log = File(dir, "session.log")
        log.writeText(log.readText().replace(Regex(" (slurCheck|slurStopDrop)=[^ \n]*"), ""))
        assertFalse(Replay.recordedRunRules(Session.load(dir)).slurCheck)
    }
}
