package hr.bertlab.fluteassistant.domain.flute

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TuneCheckTest {

    private fun q(midi: Int) = TuneNote(midi, NoteLength.QUARTER)

    @Test
    fun `waits on the first note, skipping rests and breaths, and on nothing in a tune without notes`() {
        assertEquals(2, TuneCheck(listOf(TuneRest(NoteLength.QUARTER), TuneBreath, q(60), q(62))).index)
        val nothing = TuneCheck(listOf(TuneRest(NoteLength.QUARTER), TuneBreath))
        assertNull(nothing.index)
        assertTrue(nothing.isDone)
    }

    @Test
    fun `the right note, in any octave, earns a star and moves on`() {
        val check = TuneCheck(listOf(q(60), q(62)))
        assertEquals(Verdict.RIGHT, check.judge(72, nowMs = 0))
        assertEquals(mapOf(0 to StepMark.FIRST_TRY), check.marks)
        assertEquals(1, check.index)
    }

    @Test
    fun `a wrong note keeps the place, and the right one after it earns a faint star`() {
        val check = TuneCheck(listOf(q(60), q(62)))
        assertEquals(Verdict.WRONG, check.judge(64, nowMs = 0))
        assertEquals(0, check.index)
        assertTrue(check.marks.isEmpty())
        assertEquals(Verdict.RETRY, check.judge(60, nowMs = 100))
        assertEquals(mapOf(0 to StepMark.RETRY), check.marks)
        assertEquals(1, check.index)
    }

    @Test
    fun `the previous note heard again is ignored, in its own or another octave`() {
        val check = TuneCheck(listOf(q(60), q(62)))
        assertEquals(Verdict.RIGHT, check.judge(60, nowMs = 0))
        assertEquals(Verdict.IGNORED, check.judge(60, nowMs = 1000))
        assertEquals(Verdict.IGNORED, check.judge(72, nowMs = 1000))
        assertEquals(1, check.index)
    }

    @Test
    fun `a written repeat is right after the guard and ignored before it`() {
        val check = TuneCheck(listOf(q(60), q(60)))
        assertEquals(Verdict.RIGHT, check.judge(60, nowMs = 0))
        assertEquals(Verdict.IGNORED, check.judge(72, nowMs = 100))
        assertEquals(1, check.index)
        assertEquals(Verdict.RIGHT, check.judge(60, nowMs = 400))
        assertTrue(check.isDone)
    }

    @Test
    fun `the last note judged right finishes the check and skips trailing rests`() {
        val check = TuneCheck(listOf(q(60), TuneRest(NoteLength.QUARTER), q(62), TuneBreath))
        check.judge(60, nowMs = 0)
        assertEquals(2, check.index)
        assertEquals(Verdict.RIGHT, check.judge(62, nowMs = 500))
        assertNull(check.index)
        assertEquals(mapOf(0 to StepMark.FIRST_TRY, 2 to StepMark.FIRST_TRY), check.marks)
    }

    @Test
    fun `judging after the end is ignored`() {
        val check = TuneCheck(listOf(q(60)))
        check.judge(60, nowMs = 0)
        assertEquals(Verdict.IGNORED, check.judge(60, nowMs = 5000))
    }
}
