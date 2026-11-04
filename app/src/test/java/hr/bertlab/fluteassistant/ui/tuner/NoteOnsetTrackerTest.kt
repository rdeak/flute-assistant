package hr.bertlab.fluteassistant.ui.tuner

import hr.bertlab.fluteassistant.audio.tuner.PitchFrame
import hr.bertlab.fluteassistant.domain.tuner.NoteMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class NoteOnsetTrackerTest {

    private fun pitched(midi: Int, cents: Double = 0.0, probability: Double = 0.97) =
        PitchFrame(NoteMath.midiToFrequency(midi) * 2.0.pow(cents / 1200.0), probability, level = 0.4f)

    private val silent = PitchFrame(null, 0.0, level = 0f)

    private fun feed(tracker: NoteOnsetTracker, frames: List<PitchFrame>): List<NoteOnsetTracker.Result> = frames.map(tracker::onFrame)

    private fun List<NoteOnsetTracker.Result>.onsets() = map { it.onset }
    private fun List<NoteOnsetTracker.Result>.decisions() = map { it.decision }

    @Test
    fun `three agreeing frames make one onset and a fourth does not`() {
        val tracker = NoteOnsetTracker()
        val results = feed(tracker, List(4) { pitched(60) })
        assertEquals(
            listOf(FrameDecision.UNSTEADY, FrameDecision.UNSTEADY, FrameDecision.SHOWN, FrameDecision.SHOWN),
            results.decisions(),
        )
        assertEquals(listOf(null, null, 60, null), results.onsets())
        assertEquals(60, tracker.sounding)
    }

    @Test
    fun `a one-frame gap does not start a new note, a two-frame gap does`() {
        val one = NoteOnsetTracker()
        feed(one, List(3) { pitched(60) })
        val afterOne = feed(one, listOf(silent) + List(3) { pitched(60) })
        assertEquals(listOf(null, null, null, null), afterOne.onsets())
        assertEquals(60, one.sounding)

        val two = NoteOnsetTracker()
        feed(two, List(3) { pitched(60) })
        feed(two, List(NoteOnsetTracker.ONSET_GAP_FRAMES) { silent })
        assertNull(two.sounding)
        val afterTwo = feed(two, List(3) { pitched(60) })
        assertEquals(listOf(FrameDecision.UNSTEADY, FrameDecision.UNSTEADY, FrameDecision.SHOWN), afterTwo.decisions())
        assertEquals(listOf(null, null, 60), afterTwo.onsets())
    }

    @Test
    fun `a slur to a note two semitones away is an onset as soon as it locks`() {
        val tracker = NoteOnsetTracker()
        feed(tracker, List(3) { pitched(60) })
        val slurred = feed(tracker, List(3) { pitched(62) })
        assertEquals(listOf(null, null, 62), slurred.onsets())
        assertEquals(62, tracker.sounding)
    }

    @Test
    fun `a drift just past the halfway point switches the sounding note after SWITCH_FRAMES but is no new note`() {
        // A long note sagging flat as the breath runs out: the sounding note follows it, as the
        // tuner's ring does, but nobody played a new note, so nothing is judged.
        val tracker = NoteOnsetTracker()
        feed(tracker, List(3) { pitched(60) })
        val drifted = feed(tracker, List(2 + TunerViewModel.SWITCH_FRAMES) { pitched(60, cents = 60.0) })
        assertEquals(listOf(FrameDecision.UNSTEADY, FrameDecision.UNSTEADY), drifted.take(2).decisions())
        val shown = drifted.drop(2)
        assertTrue(shown.all { it.decision == FrameDecision.SHOWN })
        assertTrue("a drift is never an onset", shown.all { it.onset == null })
        assertEquals(61, tracker.sounding)
        // A clear step from there is a new note again.
        assertEquals(listOf(null, null, 64), feed(tracker, List(3) { pitched(64) }).onsets())
    }

    @Test
    fun `a gate flicker inside a held note is not a gap, a tongued gap is`() {
        // The probability gate blinks on a breathy tone: two frames lose their pitch but keep the
        // note's loudness. That must not end the note (it would count a written repeat as played).
        val tracker = NoteOnsetTracker()
        val loud = 0.6f
        feed(tracker, List(3) { PitchFrame(NoteMath.midiToFrequency(60), 0.97, loud) })
        val blink = feed(tracker, List(2) { PitchFrame(NoteMath.midiToFrequency(60), 0.5, loud) })
        assertEquals(listOf(FrameDecision.LOW_PROBABILITY, FrameDecision.LOW_PROBABILITY), blink.decisions())
        assertEquals(60, tracker.sounding)
        val goesOn = feed(tracker, List(3) { PitchFrame(NoteMath.midiToFrequency(60), 0.97, loud) })
        assertTrue("the same note going on after a blink is not new", goesOn.all { it.onset == null })
        // A tongued gap drops the loudness with the pitch: the next note is new.
        feed(tracker, List(2) { PitchFrame(null, 0.0, 0.1f) })
        assertNull(tracker.sounding)
        assertEquals(listOf(null, null, 60), feed(tracker, List(3) { PitchFrame(NoteMath.midiToFrequency(60), 0.97, loud) }).onsets())
    }

    @Test
    fun `low-probability and silent frames never count`() {
        val tracker = NoteOnsetTracker()
        val uncertain = feed(tracker, List(10) { pitched(60, probability = 0.5) })
        assertTrue(uncertain.all { it.decision == FrameDecision.LOW_PROBABILITY && it.onset == null })
        assertNull(tracker.sounding)
        val silence = feed(tracker, List(10) { silent })
        assertTrue(silence.all { it.decision == FrameDecision.SILENT && it.onset == null })
        assertNull(tracker.sounding)
    }

    @Test
    fun `single pitched frames between silences never make an onset`() {
        val tracker = NoteOnsetTracker()
        val clicks = feed(tracker, List(5) { listOf(pitched(60), silent, silent) }.flatten())
        assertTrue(clicks.all { it.onset == null })
        assertNull(tracker.sounding)
    }

    @Test
    fun `reset forgets the sounding note`() {
        val tracker = NoteOnsetTracker()
        feed(tracker, List(3) { pitched(60) })
        tracker.reset()
        assertNull(tracker.sounding)
        assertEquals(listOf(null, null, 60), feed(tracker, List(3) { pitched(60) }).onsets())
    }
}
