// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.debug.session

import hr.bertlab.fluteassistant.audio.tuner.PitchFrame
import hr.bertlab.fluteassistant.core.log.LogTags
import hr.bertlab.fluteassistant.domain.flute.NoteLength.HALF
import hr.bertlab.fluteassistant.domain.flute.NoteLength.QUARTER
import hr.bertlab.fluteassistant.domain.flute.Pulse
import hr.bertlab.fluteassistant.domain.flute.StepMark
import hr.bertlab.fluteassistant.domain.flute.TuneBreath
import hr.bertlab.fluteassistant.domain.flute.TuneNote
import hr.bertlab.fluteassistant.domain.flute.TuneRest
import hr.bertlab.fluteassistant.domain.flute.Verdict
import hr.bertlab.fluteassistant.fakes.FakeLogger
import hr.bertlab.fluteassistant.ui.flute.ListenFrame
import hr.bertlab.fluteassistant.ui.tuner.FrameDecision
import hr.bertlab.fluteassistant.ui.tuner.Hearing
import hr.bertlab.fluteassistant.ui.tuner.NoteOnsetTracker
import hr.bertlab.fluteassistant.ui.tuner.TunerAdvice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileSessionRecorderTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val logger = FakeLogger()
    private val facts = FileSessionRecorder.Facts(app = "1.0.2", code = 1, device = "Google Pixel 7", sdk = 34)
    private var now = 1_762_366_330_000L // 2025-11-05 18:32:10 UTC, give or take the zone
    private val tune = listOf(TuneNote(71, QUARTER), TuneRest(HALF), TuneBreath, TuneNote(72, QUARTER))
    private val pulse = Pulse(90)

    private fun recorder(root: File = folder.root) = FileSessionRecorder(root, facts, logger) { now }

    private fun frame(
        f: Double? = 493.883, p: Double = 0.9123, level: Float = 0.41234f, minLevel: Float = 0.3f,
        decision: FrameDecision = FrameDecision.SHOWN, sounding: Int? = 71, onset: NoteOnsetTracker.Onset? = NoteOnsetTracker.Onset(71, fresh = true),
        floor: Float? = null, verdict: Verdict? = Verdict.RIGHT, waited: Int? = 0, expected: Int? = 71, heardNow: Int? = null,
        advice: TunerAdvice = TunerAdvice.NONE, repeatHeld: Boolean = false, finishing: Boolean = false, hearing: Hearing = Hearing.CLEAR,
    ) = ListenFrame(PitchFrame(f, p, level, minLevel), decision, sounding, onset, floor, verdict, waited, expected, heardNow, advice, repeatHeld, finishing, hearing)

    private fun sessions(root: File = folder.root) = File(root, "sessions").listFiles().orEmpty().sortedBy { it.name }

    private fun lines(dir: File) = File(dir, "session.log").readLines()

    @Test
    fun `a run makes a folder with the three files and says where`() {
        val r = recorder()
        r.start(tune, pulse, floor = null)
        r.opened(48_000, "MIC")
        r.frame(frame())
        r.end(mapOf(0 to StepMark.FIRST_TRY))
        val dir = sessions().single()
        assertTrue(dir.name, Regex("""\d{4}-\d{2}-\d{2}_\d{2}-\d{2}-\d{2}""").matches(dir.name))
        assertEquals(setOf("audio.wav", "frames.csv", "session.log"), dir.list()!!.toSet())
        val open = logger.events("session.open").single()
        assertEquals(LogTags.FLUTE, open.tag)
        assertEquals(dir.path, open["path"])
    }

    @Test
    fun `the header names the facts, the rules, the pulse and every tune item`() {
        val r = recorder()
        r.start(tune, pulse, floor = 0.2345f)
        r.opened(48_000, "UNPROCESSED")
        r.end(emptyMap())
        val lines = lines(sessions().single())
        val start = lines[0]
        assertTrue(start, start.startsWith("session.start at=") && start.endsWith(" app=1.0.2 code=1 device=\"Google Pixel 7\" sdk=34 floor=0.2345"))
        assertTrue(start, Regex("""session\.start at=\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}[+-]\d{2}:\d{2} .*""").matches(start))
        val rules = lines[1].removePrefix("session.rules ").split(' ').associate { it.substringBefore('=') to it.substringAfter('=') }
        assertEquals(18, rules.size)
        assertEquals("0.85", rules["gate"])
        assertEquals("0.002", rules["floorRise"])
        assertEquals("0.12", rules["stopDrop"])
        assertEquals("25", rules["repeatAdviceFrames"])
        assertEquals("35", rules["finishFrames"])
        assertEquals("session.pulse bpm=90 time=4/4", lines[2])
        assertEquals("session.tune index=0 note=71 length=QUARTER", lines[3])
        assertEquals("session.tune index=1 rest=HALF", lines[4])
        assertEquals("session.tune index=2 breath=true", lines[5])
        assertEquals("session.tune index=3 note=72 length=QUARTER", lines[6])
        assertEquals("session.audio rate=48000 source=UNPROCESSED window=4096 hop=2048", lines[7])
    }

    @Test
    fun `a frame is one csv row at the stated precision`() {
        val r = recorder()
        r.start(tune, pulse, floor = null)
        r.opened(48_000, "MIC")
        r.frame(frame())
        r.frame(frame(f = null, p = 0.0, level = 0.05f, minLevel = 0.04f, decision = FrameDecision.SILENT, sounding = null, onset = null, floor = 0.05f, verdict = null, waited = 3, expected = 72, heardNow = 60, advice = TunerAdvice.COME_CLOSER, repeatHeld = true, finishing = true, hearing = Hearing.QUIET_UNCLEAR))
        r.end(emptyMap())
        val csv = File(sessions().single(), "frames.csv").readLines()
        assertEquals("frame,ms,f,p,level,minLevel,decision,sounding,onset,verdict,waited,expected,heardNow,advice,repeatHeld,finishing,hearing,floor", csv[0])
        assertEquals("1,42.67,493.88,0.912,0.4123,0.3000,SHOWN,71,fresh,right,0,71,,,false,false,CLEAR,", csv[1])
        assertEquals("2,85.33,,0.000,0.0500,0.0400,SILENT,,,,3,72,60,COME_CLOSER,true,true,QUIET_UNCLEAR,0.0500", csv[2])
        assertEquals(3, csv.size)
    }

    @Test
    fun `a step onset and the other verdicts are spelled out`() {
        val r = recorder()
        r.start(tune, pulse, floor = null)
        r.opened(48_000, "MIC")
        r.frame(frame(onset = NoteOnsetTracker.Onset(72, fresh = false), verdict = Verdict.WRONG))
        r.frame(frame(onset = NoteOnsetTracker.Onset(72, fresh = true), verdict = Verdict.RETRY))
        r.frame(frame(verdict = Verdict.IGNORED))
        r.end(emptyMap())
        val csv = File(sessions().single(), "frames.csv").readLines().drop(1)
        assertEquals(listOf("step,wrong", "fresh,retry", "fresh,ignored"), csv.map { it.split(',').let { c -> "${c[8]},${c[9]}" } })
    }

    @Test
    fun `ms follows the opened rate`() {
        val r = recorder()
        r.start(tune, pulse, floor = null)
        r.opened(44_100, "MIC")
        r.frame(frame())
        r.end(emptyMap())
        assertEquals("46.44", File(sessions().single(), "frames.csv").readLines()[1].split(',')[1])
        assertEquals(44_100, WavFile.read(File(sessions().single(), "audio.wav")).sampleRate)
    }

    @Test
    fun `audio is written only between start and end`() {
        val r = recorder()
        r.samples(floatArrayOf(9f, 9f), 2) // nothing open: ignored
        r.start(tune, pulse, floor = null)
        r.opened(48_000, "MIC")
        r.samples(floatArrayOf(0.1f, 0.2f, 0.3f), 3)
        r.end(emptyMap())
        r.samples(floatArrayOf(9f, 9f), 2)
        r.closed()
        val audio = WavFile.read(File(sessions().single(), "audio.wav"))
        assertEquals(listOf(0.1f, 0.2f, 0.3f), audio.samples.toList())
        assertEquals(48_000, audio.sampleRate)
    }

    @Test
    fun `a teed line carries the frame count, its tag and level, and the audio line the microphone`() {
        val r = recorder()
        r.line(LogTags.FLUTE, 'd', "flute.idle") // nothing open: dropped
        r.start(tune, pulse, floor = null)
        r.line(LogTags.FLUTE, 'd', "flute.listen notes=2")
        r.opened(48_000, "MIC")
        r.frame(frame())
        r.frame(frame())
        r.line(LogTags.FLUTE, 'd', "flute.heard index=0 expected=71 heard=71 fresh=true verdict=right")
        r.line(LogTags.TUNER, 'i', "mic.close")
        r.end(emptyMap())
        val lines = lines(sessions().single())
        assertEquals("flute.listen frame=0 tag=FA.Flute level=d notes=2", lines[7])
        assertEquals("session.audio rate=48000 source=MIC window=4096 hop=2048", lines[8])
        assertEquals("flute.heard frame=2 tag=FA.Flute level=d index=0 expected=71 heard=71 fresh=true verdict=right", lines[9])
        assertEquals("mic.close frame=2 tag=FA.Tuner level=i", lines[10])
        assertFalse(lines.any { it.startsWith("flute.idle") })
    }

    @Test
    fun `a microphone opened again inside one run keeps the first one's audio and says so`() {
        val r = recorder()
        r.start(tune, pulse, floor = null)
        r.opened(48_000, "MIC")
        r.samples(floatArrayOf(0.1f), 1)
        r.frame(frame())
        r.opened(44_100, "UNPROCESSED")
        r.samples(floatArrayOf(0.2f, 0.3f), 2)
        r.end(emptyMap())
        val dir = sessions().single()
        val audio = WavFile.read(File(dir, "audio.wav"))
        assertEquals(listOf(0.1f, 0.2f, 0.3f), audio.samples.toList())
        assertEquals(48_000, audio.sampleRate)
        val lines = lines(dir)
        assertEquals(1, lines.count { it.startsWith("session.audio ") })
        assertEquals("session.reopened frame=1 rate=44100 source=UNPROCESSED", lines[lines.size - 2])
    }

    @Test
    fun `the files are closed even when the end line cannot be written`() {
        // Stands in for a disk that fills at the very end: the marks cannot be spelled, so the end
        // line fails, and the audio's header must still be patched (a leaked writer never patches it).
        val poison = object : Map<Int, StepMark> by emptyMap() {
            override val entries: Set<Map.Entry<Int, StepMark>> get() = throw IllegalStateException("no marks")
        }
        val r = recorder()
        r.start(tune, pulse, floor = null)
        r.opened(48_000, "MIC")
        r.samples(FloatArray(2048), 2048)
        r.frame(frame())
        r.end(poison)
        val dir = sessions().single()
        assertEquals("no marks", logger.events("session.error").single()["message"])
        val header = File(dir, "audio.wav").readBytes()
        val dataBytes = (header[40].toInt() and 0xff) or ((header[41].toInt() and 0xff) shl 8) or ((header[42].toInt() and 0xff) shl 16) or ((header[43].toInt() and 0xff) shl 24)
        assertEquals("the data size the closed writer patched in", 2048 * 4, dataBytes)
        now += 1_000
        r.start(tune, pulse, floor = null) // the failed run is over: a new one gets its own folder
        r.end(emptyMap())
        assertEquals(2, sessions().size)
    }

    @Test
    fun `the end line has the marks and the wav its length`() {
        val r = recorder()
        r.start(tune, pulse, floor = null)
        r.opened(48_000, "MIC")
        r.samples(FloatArray(2048), 2048)
        r.frame(frame())
        r.samples(FloatArray(2048), 2048)
        r.frame(frame())
        r.end(mapOf(0 to StepMark.FIRST_TRY, 3 to StepMark.RETRY))
        val dir = sessions().single()
        assertEquals("session.end frames=2 marks=0:FIRST_TRY,3:RETRY", lines(dir).last())
        assertEquals(4096, WavFile.read(File(dir, "audio.wav")).samples.size)
    }

    @Test
    fun `a run with no microphone still closes cleanly`() {
        val r = recorder()
        r.start(tune, pulse, floor = null)
        r.end(emptyMap())
        val dir = sessions().single()
        val audio = WavFile.read(File(dir, "audio.wav"))
        assertEquals(0, audio.samples.size)
        assertEquals(48_000, audio.sampleRate)
        assertEquals("session.end frames=0 marks=", lines(dir).last())
        assertEquals(1, File(dir, "frames.csv").readLines().size)
    }

    @Test
    fun `the newest twenty sessions are kept`() {
        val r = recorder()
        val made = mutableListOf<String>()
        repeat(FileSessionRecorder.KEEP + 1) {
            r.start(tune, pulse, floor = null)
            r.end(emptyMap())
            made += sessions().last().name
            now += 1_000
        }
        assertEquals("the oldest run's folder is gone, the newest twenty stand", made.drop(1), sessions().map { it.name })
        now -= 1_000L * (FileSessionRecorder.KEEP + 1)
        r.start(tune, pulse, floor = null) // a start at the oldest time again: its folder was deleted, so this makes it anew, not a `_2`
        r.end(emptyMap())
        assertEquals(listOf(made[0]) + made.drop(2), sessions().map { it.name })
    }

    @Test
    fun `two runs in one second get their own folders`() {
        val r = recorder()
        repeat(2) {
            r.start(tune, pulse, floor = null)
            r.end(emptyMap())
        }
        val names = sessions().map { it.name }
        assertEquals(2, names.size)
        assertEquals(names[0] + "_2", names[1])
    }

    @Test
    fun `a root that cannot be written drops the session without throwing`() {
        val file = folder.newFile("not-a-folder")
        val r = recorder(root = file)
        r.start(tune, pulse, floor = null)
        r.opened(48_000, "MIC")
        r.samples(FloatArray(10), 10)
        r.frame(frame())
        r.line(LogTags.FLUTE, 'd', "flute.heard")
        r.end(emptyMap())
        assertEquals(1, logger.events("session.error").size)
        assertTrue(logger.events("session.open").isEmpty())
        assertTrue(file.isFile)
    }

    @Test
    fun `a failure of any kind inside the recorder is logged once and never thrown`() {
        // The recorder must never disturb the run on a child's phone: a bug in it is a log line, not a crash.
        val r = FileSessionRecorder(folder.root, facts, logger) { throw IllegalStateException("no clock") }
        r.start(tune, pulse, floor = null)
        r.opened(48_000, "MIC")
        r.frame(frame())
        r.end(emptyMap())
        assertEquals(1, logger.events("session.error").size)
        assertEquals("no clock", logger.events("session.error").single()["message"])
        assertTrue(sessions().isEmpty())
    }

    @Test
    fun `a start while a run is open closes the one before`() {
        val r = recorder()
        r.start(tune, pulse, floor = null)
        r.frame(frame())
        now += 1_000
        r.start(tune, pulse, floor = null)
        r.end(emptyMap())
        val dirs = sessions()
        assertEquals(2, dirs.size)
        assertEquals("session.end frames=1 marks=", lines(dirs[0]).last())
        assertEquals("session.end frames=0 marks=", lines(dirs[1]).last())
    }
}
