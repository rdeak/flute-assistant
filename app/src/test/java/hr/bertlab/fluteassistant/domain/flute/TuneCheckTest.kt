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
        assertEquals(Verdict.RIGHT, check.judge(72, fresh = true))
        assertEquals(mapOf(0 to StepMark.FIRST_TRY), check.marks)
        assertEquals(1, check.index)
    }

    @Test
    fun `a wrong note keeps the place, and the right one after it earns a faint star`() {
        val check = TuneCheck(listOf(q(60), q(62)))
        assertEquals(Verdict.WRONG, check.judge(64, fresh = true))
        assertEquals(0, check.index)
        assertTrue(check.marks.isEmpty())
        assertEquals(Verdict.RETRY, check.judge(60, fresh = true))
        assertEquals(mapOf(0 to StepMark.RETRY), check.marks)
        assertEquals(1, check.index)
    }

    @Test
    fun `the previous note heard again is ignored, in its own or another octave, started again or by a step`() {
        val check = TuneCheck(listOf(q(60), q(62)))
        assertEquals(Verdict.RIGHT, check.judge(60, fresh = true))
        assertEquals(Verdict.IGNORED, check.judge(60, fresh = true))
        assertEquals(Verdict.IGNORED, check.judge(72, fresh = false))
        assertEquals(1, check.index)
    }

    @Test
    fun `a written repeat counts on a fresh start and never by a step without a gap`() {
        // The octave pop of the note just counted is a step inside one note: not the repeat.
        val check = TuneCheck(listOf(q(60), q(60)))
        assertEquals(Verdict.RIGHT, check.judge(60, fresh = true))
        assertEquals(Verdict.IGNORED, check.judge(72, fresh = false))
        assertEquals(1, check.index)
        // Let go of and started again, in whichever octave: the repeat, played.
        assertEquals(Verdict.RIGHT, check.judge(72, fresh = true))
        assertTrue(check.isDone)
    }

    @Test
    fun `a repeat the phone could not hear is forgiven when the note after it comes`() {
        // Written c c d, heard c then d: the second c was slurred on to or skipped, which the
        // phone cannot tell apart; it gets a faint star rather than stopping everything.
        val check = TuneCheck(listOf(q(60), q(60), q(62)))
        assertEquals(Verdict.RIGHT, check.judge(60, fresh = true))
        assertTrue(check.forgiven.isEmpty())
        assertEquals(Verdict.RIGHT, check.judge(62, fresh = false))
        assertEquals(listOf(1), check.forgiven)
        assertEquals(mapOf(0 to StepMark.FIRST_TRY, 1 to StepMark.RETRY, 2 to StepMark.FIRST_TRY), check.marks)
        assertTrue(check.isDone)
    }

    @Test
    fun `several repeats in a row are forgiven together`() {
        val check = TuneCheck(listOf(q(60), q(60), q(60), q(62), q(64)))
        check.judge(60, fresh = true)
        assertEquals(Verdict.RIGHT, check.judge(62, fresh = true))
        assertEquals(listOf(1, 2), check.forgiven)
        assertEquals(4, check.index)
        assertEquals(Verdict.RIGHT, check.judge(64, fresh = true))
        assertTrue("the last judgement forgave nothing", check.forgiven.isEmpty())
    }

    @Test
    fun `a skipped note that repeats nothing is not forgiven`() {
        val check = TuneCheck(listOf(q(60), q(64), q(62)))
        check.judge(60, fresh = true)
        assertEquals(Verdict.WRONG, check.judge(62, fresh = true))
        assertTrue(check.forgiven.isEmpty())
        assertEquals(1, check.index)
    }

    @Test
    fun `the previous letter is known`() {
        val check = TuneCheck(listOf(q(60), q(62)))
        assertNull(check.previous)
        check.judge(72, fresh = true)
        assertEquals(60, check.previous)
    }

    @Test
    fun `the last note judged right finishes the check and skips trailing rests`() {
        val check = TuneCheck(listOf(q(60), TuneRest(NoteLength.QUARTER), q(62), TuneBreath))
        check.judge(60, fresh = true)
        assertEquals(2, check.index)
        assertEquals(Verdict.RIGHT, check.judge(62, fresh = true))
        assertNull(check.index)
        assertEquals(mapOf(0 to StepMark.FIRST_TRY, 2 to StepMark.FIRST_TRY), check.marks)
    }

    @Test
    fun `judging after the end is ignored`() {
        val check = TuneCheck(listOf(q(60)))
        check.judge(60, fresh = true)
        assertEquals(Verdict.IGNORED, check.judge(60, fresh = true))
    }
}
