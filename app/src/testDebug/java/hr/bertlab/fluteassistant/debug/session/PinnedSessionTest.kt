package hr.bertlab.fluteassistant.debug.session

import hr.bertlab.fluteassistant.audio.tuner.AudioRecordTunerEngine
import hr.bertlab.fluteassistant.domain.flute.NoteLength.HALF
import hr.bertlab.fluteassistant.domain.flute.NoteLength.QUARTER
import hr.bertlab.fluteassistant.domain.flute.Pulse
import hr.bertlab.fluteassistant.domain.flute.TuneItem
import hr.bertlab.fluteassistant.domain.flute.TuneNote
import hr.bertlab.fluteassistant.fakes.FakeLogger
import hr.bertlab.fluteassistant.ui.flute.FluteSound
import hr.bertlab.fluteassistant.ui.flute.ListenFrame
import hr.bertlab.fluteassistant.ui.flute.ListenPath
import hr.bertlab.fluteassistant.ui.tuner.Hearing
import hr.bertlab.fluteassistant.ui.tuner.TunerAdvice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** [RecordedSessions] over synthetic sessions in a temporary folder: the check that pins a real child's session as a test. */
class PinnedSessionTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val tune: List<TuneItem> = listOf(TuneNote(71, QUARTER), TuneNote(71, QUARTER), TuneNote(71, HALF))

    /** A synthetic session under [folder]/sessions, named [name], with [pin] as its pin line when given. */
    private fun session(name: String, pin: String?): File {
        val samples = FluteSound.render(FluteSound.sound(gapsMs = 50))
        val hop = AudioRecordTunerEngine.HOP_SIZE
        val run = ListenPath.run(samples, FluteSound.RATE, tune)
        val root = folder.newFolder(name)
        val r = FileSessionRecorder(root, FileSessionRecorder.Facts("1.0.2", 1, "JVM", 0), FakeLogger()) { 0L }
        r.start(tune, Pulse(90), floor = null)
        r.opened(FluteSound.RATE, "MIC")
        for (step in run.steps) {
            r.samples(samples.copyOfRange((step.frame - 1) * hop, step.frame * hop), hop)
            r.frame(ListenFrame(step.pitch, step.decision, step.sounding, step.onset, step.floor, step.verdict, step.waited, step.expected, null, TunerAdvice.NONE, false, false, Hearing.CLEAR))
        }
        r.end(run.marks)
        val dir = File(root, "sessions").listFiles()!!.single()
        val pinned = File(folder.root, "pinned").apply { mkdirs() }.resolve(name)
        dir.renameTo(pinned)
        if (pin != null) File(pinned, "session.log").appendText("pin marks=$pin\n")
        return pinned
    }

    @Test
    fun `a pinned session with the right marks passes`() {
        session("good", pin = "0:FIRST_TRY,1:FIRST_TRY,2:FIRST_TRY")
        assertEquals(emptyList<String>(), RecordedSessions.check(File(folder.root, "pinned")))
    }

    @Test
    fun `a wrong pin and a missing pin are reported with the folder's name`() {
        session("wrong", pin = "0:FIRST_TRY,1:RETRY,2:FIRST_TRY")
        session("unpinned", pin = null)
        val failures = RecordedSessions.check(File(folder.root, "pinned"))
        assertEquals(failures.toString(), 2, failures.size)
        assertTrue(failures[0], failures[0].startsWith("unpinned: ") && failures[0].contains("no pin line"))
        assertTrue(failures[1], failures[1].startsWith("wrong: ") && failures[1].contains("pin 0:FIRST_TRY,1:RETRY,2:FIRST_TRY") && failures[1].contains("replay 0:FIRST_TRY,1:FIRST_TRY,2:FIRST_TRY"))
    }

    @Test
    fun `a pinned session without its frames table still replays`() {
        // The README allows dropping frames.csv from a pinned folder: the replay needs the audio, the tune and the pin.
        val dir = session("lean", pin = "0:FIRST_TRY,1:FIRST_TRY,2:FIRST_TRY")
        assertTrue(File(dir, "frames.csv").delete())
        assertEquals(emptyList<String>(), RecordedSessions.check(File(folder.root, "pinned")))
    }

    @Test
    fun `a folder that cannot be replayed fails by its name instead of throwing`() {
        val broken = session("broken", pin = "0:FIRST_TRY,1:FIRST_TRY,2:FIRST_TRY")
        assertTrue(File(broken, "audio.wav").delete())
        folder.newFolder("pinned", "bare") // no session.log at all: a half-copied folder
        val failures = RecordedSessions.check(File(folder.root, "pinned"))
        assertEquals(failures.toString(), 2, failures.size)
        assertTrue(failures[0], failures[0].startsWith("bare: ") && failures[0].contains("no session.log"))
        assertTrue(failures[1], failures[1].startsWith("broken: ") && failures[1].contains("audio.wav"))
    }

    @Test
    fun `no folder or an empty one is nothing to check`() {
        assertEquals(emptyList<String>(), RecordedSessions.check(File(folder.root, "nowhere")))
        assertEquals(emptyList<String>(), RecordedSessions.check(folder.newFolder("empty")))
    }
}
