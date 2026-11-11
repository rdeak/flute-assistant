// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.debug.session

import hr.bertlab.fluteassistant.audio.tuner.AudioRecordTunerEngine
import hr.bertlab.fluteassistant.domain.flute.ListenFrame
import hr.bertlab.fluteassistant.domain.flute.NoteLength.HALF
import hr.bertlab.fluteassistant.domain.flute.NoteLength.QUARTER
import hr.bertlab.fluteassistant.domain.flute.Pulse
import hr.bertlab.fluteassistant.domain.flute.StepMark
import hr.bertlab.fluteassistant.domain.flute.TuneItem
import hr.bertlab.fluteassistant.domain.flute.TuneNote
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
    ): File {
        val run = ListenPath.run(samples, rate, tune, rules)
        val r = FileSessionRecorder(folder.root, FileSessionRecorder.Facts("1.0.2", 1, "JVM", 0), FakeLogger()) { 0L }
        r.start(tune, Pulse(90), floor = null)
        r.opened(rate, "MIC")
        for (step in run.steps) {
            r.samples(samples.copyOfRange((step.frame - 1) * hop, step.frame * hop), hop)
            r.frame(ListenFrame(step.pitch, step.decision, step.sounding, step.onset, step.floor, step.verdict, step.waited, step.expected, null, TunerAdvice.NONE, false, false, Hearing.CLEAR))
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
        val diff = Replay.diff(session.rows, ListenPath.run(session.audio.samples, rate, tune).steps)
        assertEquals(30, diff.first)
        assertEquals(1, diff.count)
        assertEquals(0, diff.trailing)
        assertTrue(Replay.report(session).contains("diff: first=30 count=1"))
    }

    @Test
    fun `hops the phone never processed are counted, not diffed`() {
        val session = Session.load(recorded(extraHops = 3))
        val diff = Replay.diff(session.rows, ListenPath.run(session.audio.samples, rate, tune).steps)
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
