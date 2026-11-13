// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.debug.session

import hr.bertlab.fluteassistant.audio.tuner.AudioRecordTunerEngine
import hr.bertlab.fluteassistant.domain.flute.ListenFrame
import hr.bertlab.fluteassistant.domain.flute.ListenRun
import hr.bertlab.fluteassistant.domain.flute.NoteLength.HALF
import hr.bertlab.fluteassistant.domain.flute.NoteLength.QUARTER
import hr.bertlab.fluteassistant.domain.flute.Pulse
import hr.bertlab.fluteassistant.domain.flute.StepMark
import hr.bertlab.fluteassistant.domain.flute.TuneItem
import hr.bertlab.fluteassistant.domain.flute.TuneNote
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
    ): File {
        val run = ListenPath.run(samples, rate, tune, pulse, rules, run = run)
        val r = FileSessionRecorder(folder.root, FileSessionRecorder.Facts("1.0.2", 1, "JVM", 0), FakeLogger()) { 0L }
        r.start(tune, pulse, floor = null)
        r.opened(rate, "MIC")
        for (step in run.steps) {
            r.samples(samples.copyOfRange((step.frame - 1) * hop, step.frame * hop), hop)
            r.frame(ListenFrame(step.pitch, step.decision, step.sounding, step.onset, step.floor, step.verdict, step.waited, step.expected, null, TunerAdvice.NONE, false, false, Hearing.CLEAR, step.walk))
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
        assertTrue(report, report.contains("today's rules differ: minLevel=0.45 (recorded 0.0)"))
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
        assertTrue(lines[2], lines[2].startsWith("stopDrop=0.5 heard=71 marks=0:FIRST_TRY"))
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
        assertEquals(ListenRun.Rules.PITCH_ONLY, Replay.recordedRunRules(session).copy(maxNeedMs = Double.POSITIVE_INFINITY))
        assertEquals("a rule the line names is the phone's", 3000.0, Replay.recordedRunRules(session).maxNeedMs, 0.0)
        val report = Replay.report(session)
        val then = report.substringBefore("today's rules")
        assertTrue(report, then.contains("diff: none"))
        assertTrue(report, then.contains("marks=0:FIRST_TRY,1:FIRST_TRY,2:FIRST_TRY"))
        assertTrue(report, report.contains("today's rules differ: lengthSlack=1.5 (recorded none) restartCuts=0 (recorded none)"))
        val now = report.substringAfter("today's rules").substringBefore("phone:")
        assertTrue(report, now.contains("marks=0:FIRST_TRY,1:FIRST_TRY\n"))
    }

    @Test
    fun `a lengthSlack sweep gives one line per value`() {
        val out = Replay.sweep(Session.load(recorded(pulse = Pulse(60))), "lengthSlack:0,8")
        val lines = out.lines().filter { it.startsWith("lengthSlack=") }
        assertEquals(out, 2, lines.size)
        // 0: a quarter needs all of its 1000 ms. The tongued stops do not cut it ([ListenRun.Rules.restartCuts]): the
        // three notes walk as one, which the third makes long enough for the first step, and nothing is left for the others.
        assertTrue(lines[0], lines[0].startsWith("lengthSlack=0 heard=71,71,71 ") && lines[0].endsWith("marks=0:FIRST_TRY"))
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
        assertTrue(lines[1], lines[1].startsWith("maxNeedMs=3000 ") && lines[1].endsWith("marks=0:FIRST_TRY,1:FIRST_TRY"))
    }

    // ---- The walk in a session (the review of 2026-11-11): read back, and part of the diff ----

    @Test
    fun `the rows carry where the animal was, and a walk the phone and the replay disagree on is a difference`() {
        val dir = recorded()
        val session = Session.load(dir)
        assertTrue(session.recordsWalk)
        assertTrue(session.rows.any { it.walk?.phase == Walk.Phase.SOUNDING })
        assertTrue(session.rows.any { it.walk?.phase == Walk.Phase.DONE })
        assertTrue(Replay.report(session).contains("diff: none"))

        // The phone says a note was cut short on a frame where the rules say it still sounded: the frame a note
        // was earned on, or the hop the child saw, went another way than the replay's.
        val csv = File(dir, "frames.csv")
        val lines = csv.readLines().toMutableList()
        val at = lines.indexOfFirst { it.endsWith(",SOUNDING") }
        lines[at] = lines[at].removeSuffix("SOUNDING") + "SHORT"
        csv.writeText(lines.joinToString("\n") + "\n")
        val altered = Session.load(dir)
        val report = Replay.report(altered)
        assertTrue(report, report.contains("diff: first=$at count=1"))
        assertTrue(report, report.contains("SHORT") && report.contains("SOUNDING"))
        assertEquals("the walk is compared only when asked", 0, Replay.diff(altered.rows, ListenPath.run(altered.frames, tune, altered.pulse).steps).count)
    }

    @Test
    fun `a table from before the walk is not compared on it`() {
        val dir = recorded()
        val csv = File(dir, "frames.csv")
        csv.writeText(csv.readLines().joinToString("\n") { it.split(',').dropLast(3).joinToString(",") } + "\n")
        val session = Session.load(dir)
        assertFalse(session.recordsWalk)
        assertTrue(session.rows.all { it.walk == null })
        assertTrue(Replay.report(session).contains("diff: none"))
    }

    @Test
    fun `the rows are timed at the rate the session was recorded at`() {
        val r = FileSessionRecorder(folder.root, FileSessionRecorder.Facts("1.0.2", 1, "JVM", 0), FakeLogger()) { 0L }
        r.start(tune, Pulse(90), floor = null)
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
}
