// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.flute

import hr.bertlab.fluteassistant.domain.listen.TestFrames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TuneCheckTest {

    private fun q(midi: Int) = TuneNote(midi, NoteLength.QUARTER)
    private fun j(midi: Int) = TuneNote(midi, NoteLength.QUARTER, joined = true)

    /** The frequency of [midi] [cents] off it: where a note locked. */
    private fun hz(midi: Int, cents: Double) = TestFrames.hz(midi, cents)

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
        assertEquals(StepMark.FIRST_TRY, check.earn())
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
        assertEquals(StepMark.RETRY, check.earn())
        assertEquals(mapOf(0 to StepMark.RETRY), check.marks)
        assertEquals(1, check.index)
    }

    @Test
    fun `the previous note heard again is ignored, in its own or another octave, started again or by a step`() {
        val check = TuneCheck(listOf(q(60), q(62)))
        assertEquals(Verdict.RIGHT, check.judge(60, fresh = true))
        check.earn()
        assertEquals(Verdict.IGNORED, check.judge(60, fresh = true))
        assertEquals(Verdict.IGNORED, check.judge(72, fresh = false))
        assertEquals(1, check.index)
    }

    @Test
    fun `a written repeat counts on a fresh start and never by a step without a gap`() {
        // The octave pop of the note just counted is a step inside one note: not the repeat.
        val check = TuneCheck(listOf(q(60), q(60)))
        assertEquals(Verdict.RIGHT, check.judge(60, fresh = true))
        check.earn()
        assertEquals(Verdict.IGNORED, check.judge(72, fresh = false))
        assertEquals(1, check.index)
        // Let go of and started again, in whichever octave: the repeat, played.
        assertEquals(Verdict.RIGHT, check.judge(72, fresh = true))
        check.earn()
        assertTrue(check.isDone)
    }

    @Test
    fun `a repeat the phone could not hear is forgiven when the note after it comes`() {
        // Written c c d, heard c then d: the second c was slurred on to or skipped, which the
        // phone cannot tell apart; it gets a faint star rather than stopping everything.
        val check = TuneCheck(listOf(q(60), q(60), q(62)))
        assertEquals(Verdict.RIGHT, check.judge(60, fresh = true))
        check.earn()
        assertTrue(check.forgiven.isEmpty())
        assertEquals(Verdict.RIGHT, check.judge(62, fresh = false))
        assertEquals(listOf(1), check.forgiven)
        assertEquals("the forgiven repeat is earned at once", mapOf(0 to StepMark.FIRST_TRY, 1 to StepMark.RETRY), check.marks)
        assertEquals(2, check.index)
        check.earn()
        assertEquals(mapOf(0 to StepMark.FIRST_TRY, 1 to StepMark.RETRY, 2 to StepMark.FIRST_TRY), check.marks)
        assertTrue(check.isDone)
    }

    @Test
    fun `several repeats in a row are forgiven together`() {
        val check = TuneCheck(listOf(q(60), q(60), q(60), q(62), q(64)))
        check.judge(60, fresh = true)
        check.earn()
        assertEquals(Verdict.RIGHT, check.judge(62, fresh = true))
        assertEquals(listOf(1, 2), check.forgiven)
        assertEquals(3, check.index)
        check.earn()
        assertEquals(4, check.index)
        assertEquals(Verdict.RIGHT, check.judge(64, fresh = true))
        assertTrue("the last judgement forgave nothing", check.forgiven.isEmpty())
    }

    @Test
    fun `a skipped note that repeats nothing is not forgiven`() {
        val check = TuneCheck(listOf(q(60), q(64), q(62)))
        check.judge(60, fresh = true)
        check.earn()
        assertEquals(Verdict.WRONG, check.judge(62, fresh = true))
        assertTrue(check.forgiven.isEmpty())
        assertEquals(1, check.index)
    }

    @Test
    fun `the previous letter is known`() {
        val check = TuneCheck(listOf(q(60), q(62)))
        assertNull(check.previous)
        check.judge(72, fresh = true)
        assertNull("a right pitch is not yet the note just counted", check.previous)
        check.earn()
        assertEquals(60, check.previous)
    }

    @Test
    fun `the last note judged right finishes the check and skips trailing rests`() {
        val check = TuneCheck(listOf(q(60), TuneRest(NoteLength.QUARTER), q(62), TuneBreath))
        check.judge(60, fresh = true)
        check.earn()
        assertEquals(2, check.index)
        assertEquals(Verdict.RIGHT, check.judge(62, fresh = true))
        check.earn()
        assertNull(check.index)
        assertEquals(mapOf(0 to StepMark.FIRST_TRY, 2 to StepMark.FIRST_TRY), check.marks)
    }

    @Test
    fun `judging after the end is ignored`() {
        val check = TuneCheck(listOf(q(60)))
        check.judge(60, fresh = true)
        check.earn()
        assertEquals(Verdict.IGNORED, check.judge(60, fresh = true))
    }

    // ---- A right pitch is heard at its onset and earned once it is long enough (2026-11-11) ----

    @Test
    fun `a right pitch does not move on until earned`() {
        val check = TuneCheck(listOf(q(60), q(62)))
        assertEquals(Verdict.RIGHT, check.judge(60, fresh = true))
        assertEquals(0, check.index)
        assertTrue(check.marks.isEmpty())
        assertEquals(StepMark.FIRST_TRY, check.earn())
        assertEquals(1, check.index)
    }

    @Test
    fun `a stumble makes the step a retry`() {
        val check = TuneCheck(listOf(q(60), q(62)))
        check.stumble()
        assertEquals(Verdict.RETRY, check.judge(60, fresh = true))
        assertEquals(StepMark.RETRY, check.earn())
        assertEquals(mapOf(0 to StepMark.RETRY), check.marks)
    }

    @Test
    fun `the same note played again before it is earned is judged again`() {
        val check = TuneCheck(listOf(q(62), q(60), q(64)))
        check.judge(62, fresh = true)
        check.earn()
        assertEquals(Verdict.RIGHT, check.judge(60, fresh = true))
        assertEquals("cut short, and started again", Verdict.RIGHT, check.judge(60, fresh = true))
        assertEquals(1, check.index)
    }

    @Test(expected = IllegalStateException::class)
    fun `nothing to earn after the end`() {
        val check = TuneCheck(listOf(q(60)))
        check.judge(60, fresh = true)
        check.earn()
        check.earn()
    }

    @Test
    fun `a written repeat heard and not yet earned is not forgiven by the note after it`() {
        // Written c c d: the second c was heard (its pitch judged right) and then cut short. The note after it is
        // no sign of a repeat the phone could not hear: the run waits for the c (the user's "wait", 2026-11-11).
        val check = TuneCheck(listOf(q(60), q(60), q(62)))
        check.judge(60, fresh = true)
        check.earn()
        assertEquals(Verdict.RIGHT, check.judge(60, fresh = true))
        check.stumble()
        assertEquals("the child went on: no wrong note", Verdict.IGNORED, check.judge(62, fresh = false, begun = true))
        assertTrue(check.forgiven.isEmpty())
        assertEquals(1, check.index)
        assertEquals(mapOf(0 to StepMark.FIRST_TRY), check.marks)
    }

    @Test
    fun `a repeat begun and cut short is played again by a step back to it`() {
        // Written c c d: the second c was tongued (begun), a finger slipped to g, and the c came back by a step.
        // That c is the child returning to the note waited on, not the tail of the first c.
        val check = TuneCheck(listOf(q(60), q(60), q(62)))
        check.judge(60, fresh = true)
        check.earn()
        assertEquals(Verdict.RIGHT, check.judge(60, fresh = true))
        check.stumble()
        assertEquals(Verdict.IGNORED, check.judge(67, fresh = false, begun = true))
        assertEquals(Verdict.RETRY, check.judge(60, fresh = false, begun = true))
        assertEquals("not begun, the same step back is the first c's tail", Verdict.IGNORED, check.judge(60, fresh = false))
    }

    // Since 2026-11-14 ("feedback, not stars"): the right fingering blown high is the written note, and a step's mark
    // says how it was played.

    @Test
    fun `a note up to 75 cents above the written one is the written note blown high`() {
        val check = TuneCheck(listOf(q(71), q(69)))
        assertEquals(Verdict.RIGHT, check.judge(72, fresh = true, lockHz = hz(71, 71.0))) // the highest of 2026-11-14 was 71.04
        assertEquals(StepMark.HIGH, check.earn(StepMark.HIGH))
        assertEquals(mapOf(0 to StepMark.HIGH), check.marks)
    }

    @Test
    fun `a note 76 cents above, or with no pitch known, is another note`() {
        val check = TuneCheck(listOf(q(71)))
        assertEquals(Verdict.WRONG, check.judge(72, fresh = true, lockHz = hz(71, 76.0)))
        assertEquals(Verdict.WRONG, check.judge(72, fresh = true))
    }

    @Test
    fun `a note under the written one is another note however near`() {
        // 2026-11-13: an f sharp fingered for g came out 52 to 72 cents under the g.
        assertEquals(Verdict.WRONG, TuneCheck(listOf(q(67))).judge(66, fresh = true, lockHz = hz(67, -60.0)))
    }

    @Test
    fun `with the band at nothing a note above is another note, as before 2026-11-14`() {
        assertEquals(Verdict.WRONG, TuneCheck(listOf(q(71)), highCents = 0.0).judge(72, fresh = true, lockHz = hz(71, 60.0)))
    }

    @Test
    fun `the note after unheard repeats, blown high, forgives them`() {
        val check = TuneCheck(listOf(q(67), q(67), q(71)))
        check.judge(67, fresh = true)
        check.earn()
        assertEquals(Verdict.RIGHT, check.judge(72, fresh = true, lockHz = hz(71, 57.0)))
        assertEquals(listOf(1), check.forgiven)
        assertEquals(2, check.index)
    }

    @Test
    fun `the tail of a note blown high is ignored, in any octave`() {
        val check = TuneCheck(listOf(q(71), q(69)))
        check.judge(72, fresh = true, lockHz = hz(71, 60.0))
        check.earn(StepMark.HIGH)
        assertEquals(Verdict.IGNORED, check.judge(84, fresh = false, lockHz = hz(83, 60.0)))
    }

    @Test
    fun `a slur up a semitone onto a note a little flat is that note`() {
        val check = TuneCheck(listOf(q(71), q(72)))
        check.judge(71, fresh = true)
        check.earn()
        assertEquals(Verdict.RIGHT, check.judge(72, fresh = false, lockHz = hz(72, -30.0)))
    }

    @Test
    fun `a sign says more than the faint star`() {
        val check = TuneCheck(listOf(q(60)))
        check.judge(64, fresh = true) // a wrong note first
        check.judge(60, fresh = true)
        assertEquals(StepMark.SHORT, check.earn(StepMark.SHORT))
    }

    @Test
    fun `cents from a written note are read in its nearest octave`() {
        assertEquals(60.0, TuneCheck.centsFrom(hz(83, 60.0), 71), 1e-6)
        assertEquals(-40.0, TuneCheck.centsFrom(hz(59, -40.0), 71), 1e-6)
        assertEquals(-60.0, TuneCheck.centsFrom(hz(66, 40.0), 67), 1e-6)
    }

    @Test
    fun `a mark already given can be changed, and only one that was given`() {
        val check = TuneCheck(listOf(q(67), q(69)))
        check.judge(67, fresh = true)
        check.earn()
        check.remark(0, StepMark.LONG)
        assertEquals(mapOf(0 to StepMark.LONG), check.marks)
        assertThrows(IllegalArgumentException::class.java) { check.remark(1, StepMark.LONG) }
    }

    @Test
    fun `the star a step would earn is faint after a wrong note`() {
        val check = TuneCheck(listOf(q(67)))
        assertEquals(StepMark.FIRST_TRY, check.star)
        check.judge(60, fresh = true)
        assertEquals(StepMark.RETRY, check.star)
    }

    // ---- A tie, or a slur to the same letter in another octave, is one step (2026-11-16) ----

    @Test
    fun `a tie is one step, earning it marks both notes`() {
        val check = TuneCheck(listOf(q(60), j(60), q(62)))
        assertEquals(Verdict.RIGHT, check.judge(60, fresh = true))
        check.earn()
        assertEquals(mapOf(0 to StepMark.FIRST_TRY, 1 to StepMark.FIRST_TRY), check.marks)
        assertEquals(2, check.index)
    }

    @Test
    fun `an octave slur is one step, and a changed mark changes both`() {
        val check = TuneCheck(listOf(q(60), j(72), q(62)))
        check.judge(60, fresh = true)
        check.earn()
        assertEquals(72, check.previous)
        assertEquals(2, check.index)
        check.remark(0, StepMark.LONG)
        assertEquals(mapOf(0 to StepMark.LONG, 1 to StepMark.LONG), check.marks)
    }

    @Test
    fun `a written repeat after a tie still needs a fresh start`() {
        val check = TuneCheck(listOf(q(60), j(60), q(60)))
        check.judge(60, fresh = true)
        check.earn()
        assertEquals("waits on the written repeat after the tie", 2, check.index)
        assertEquals(Verdict.IGNORED, check.judge(60, fresh = false))
        assertEquals(Verdict.RIGHT, check.judge(60, fresh = true))
    }

    @Test
    fun `an octave slur broken by a gap gives its first note its mark and the later one the scissors`() {
        val check = TuneCheck(listOf(q(60), j(72), q(62)))
        check.judge(60, fresh = true)
        assertEquals(StepMark.FIRST_TRY, check.earnBroken())
        assertEquals(mapOf(0 to StepMark.FIRST_TRY, 1 to StepMark.TONGUED), check.marks)
        assertEquals(72, check.previous)
        assertEquals(2, check.index)
    }
}
