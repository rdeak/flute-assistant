// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.flute

import hr.bertlab.fluteassistant.domain.flute.NoteLength.EIGHTH
import hr.bertlab.fluteassistant.domain.flute.NoteLength.HALF
import hr.bertlab.fluteassistant.domain.flute.NoteLength.QUARTER
import hr.bertlab.fluteassistant.domain.flute.NoteLength.WHOLE
import hr.bertlab.fluteassistant.domain.listen.TestFrames.framesFor
import hr.bertlab.fluteassistant.domain.listen.TestFrames.pitched
import hr.bertlab.fluteassistant.domain.listen.TestFrames.silent
import hr.bertlab.fluteassistant.domain.listen.Hearing
import hr.bertlab.fluteassistant.domain.listen.HearingTracker
import hr.bertlab.fluteassistant.domain.listen.ListenRules
import hr.bertlab.fluteassistant.domain.listen.NoteOnsetTracker
import hr.bertlab.fluteassistant.domain.listen.PitchFrame
import hr.bertlab.fluteassistant.domain.listen.TunerAdvice
import hr.bertlab.fluteassistant.domain.metronome.TimeSignature
import hr.bertlab.fluteassistant.domain.tuner.NoteMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A "Your turn" fed hand-made frames, no microphone and no coroutines; real sound is `ListenPathTest`'s. */
class ListenRunTest {

    private fun q(midi: Int) = TuneNote(midi, QUARTER)
    private fun h(midi: Int) = TuneNote(midi, HALF)
    /** The rule of before 2026-11-11, pitch only: every note is long enough at its onset. */
    private fun pitchOnly(tune: List<TuneItem>) = ListenRun(tune, Pulse(60), run = ListenRun.Rules.PITCH_ONLY)

    /**
     * The walk's workings at half a beat's slack, whose numbers are round: at 60 a minute in 4/4 a quarter note lasts
     * 1000 ms and is long enough at 500, a half note at 1500. The slack the app runs with is checked on its own
     * ("the slack is three quarters of a beat").
     */
    private val halfBeatSlack = ListenRun.Rules(lengthSlack = 1.0)
    private fun timed(vararg tune: TuneItem, pulse: Pulse = Pulse(60), run: ListenRun.Rules = halfBeatSlack) =
        ListenRun(tune.toList(), pulse, run = run)

    /**
     * Frames after the onset frame by which a note begun with [begin] has sounded [ms]: by its onset
     * [begin]'s note has sounded [ListenRules.LOCK_FRAMES] whole frames (the onset's and the ones that
     * locked it).
     */
    private fun after(ms: Double, hopMs: Double = PitchFrame.DEFAULT_HOP_MS) = framesFor(ms, hopMs) - ListenRules.LOCK_FRAMES

    /** Enough agreeing frames for [midi] to lock: the note begins on the last. */
    private fun ListenRun.begin(midi: Int, hopMs: Double = PitchFrame.DEFAULT_HOP_MS): ListenRun.Step =
        List(ListenRules.LOCK_FRAMES) { onFrame(pitched(midi, hopMs = hopMs)) }.last()

    /** [n] more frames of [midi]; the last one's step. */
    private fun ListenRun.hold(midi: Int, n: Int): ListenRun.Step = List(n) { onFrame(pitched(midi)) }.last()

    /** The gap that ends the sounding note. */
    private fun ListenRun.stop() = repeat(NoteOnsetTracker.ONSET_GAP_FRAMES) { onFrame(silent) }

    @Test
    fun `it waits on the first note, and a right note in any octave earns a star and moves on`() {
        val run = pitchOnly(listOf(TuneRest(QUARTER), q(60), q(62)))
        assertEquals("the opening rest is walked, as in Play", Listening(index = 1, walk = Walk(0, 0, Walk.Phase.REST)), run.listening)
        val step = run.begin(72)
        assertEquals(ListenRun.Heard(index = 1, expected = 60, NoteOnsetTracker.Onset(72, fresh = true), Verdict.RIGHT), step.heard)
        assertTrue(step.advanced)
        assertFalse(step.done)
        assertEquals(mapOf(1 to StepMark.FIRST_TRY), run.marks)
        assertEquals(2, run.listening.index)
        assertEquals("the frame is kept as it was judged", 1, step.row.waited)
        assertEquals(60, step.row.expected)
        assertEquals(Verdict.RIGHT, step.row.verdict)
    }

    @Test
    fun `a wrong note stays shown until the right one, which earns a faint star`() {
        val run = pitchOnly(listOf(q(60), q(62)))
        val wrong = run.begin(64)
        assertFalse(wrong.advanced)
        assertEquals(WrongNote(index = 0, midi = 64), run.listening.wrong)
        run.stop()
        assertEquals("no timer takes it away", WrongNote(0, 64), run.listening.wrong)
        run.begin(60)
        assertNull(run.listening.wrong)
        assertEquals(mapOf(0 to StepMark.RETRY), run.marks)
    }

    @Test
    fun `the note sounding shows under the step waited on, but not the tail of the note just counted`() {
        val run = pitchOnly(listOf(q(60), q(62)))
        run.begin(64)
        assertEquals(64, run.listening.heardNow)
        run.stop()
        assertNull(run.listening.heardNow)
        run.begin(60) // counted; it goes on while d1 is waited on
        run.onFrame(pitched(60))
        assertNull(run.listening.heardNow)
    }

    @Test
    fun `a written repeat held on instead of started again asks to stop and play it again`() {
        // Since 2026-11-11 counted from the end of the note's written length: until then holding it is right.
        val run = timed(q(60), q(60))
        run.begin(60)
        run.hold(60, after(1000.0) - 1)
        assertEquals("the first is earned and the animal finishes its beat", Walk.Phase.DONE, run.listening.walk?.phase)
        assertEquals("the repeat waited on is just what the child must see", 60, run.listening.heardNow)
        run.hold(60, 1)
        assertNull("the beat is over: the animal is at the repeat", run.listening.walk)
        repeat(ListenRun.REPEAT_ADVICE_FRAMES - 1) { run.onFrame(pitched(60)) }
        assertEquals(TunerAdvice.NONE, run.listening.advice)
        run.onFrame(pitched(60))
        assertTrue(run.listening.repeatHeld)
        assertEquals(TunerAdvice.PLAY_AGAIN, run.listening.advice)
        run.stop()
        assertEquals(TunerAdvice.NONE, run.listening.advice)
    }

    @Test
    fun `the last note counted, the run goes on until its sound ends`() {
        val run = pitchOnly(listOf(q(60)))
        val step = run.begin(60)
        assertTrue(step.done)
        assertTrue(run.listening.finishing)
        assertEquals("the animal stays over the last note", 0, run.listening.index)
        assertFalse(run.isOver)
        run.stop()
        assertTrue(run.isOver)
    }

    @Test
    fun `a last note held on ends the run after FINISH_FRAMES`() {
        val run = pitchOnly(listOf(q(60)))
        run.begin(60)
        repeat(ListenRun.FINISH_FRAMES - 1) { run.onFrame(pitched(60)) }
        assertFalse(run.isOver)
        run.onFrame(pitched(60))
        assertTrue(run.isOver)
    }

    @Test
    fun `sound that never becomes a note changes the hearing, reported once with its mean level`() {
        val run = pitchOnly(listOf(q(60)))
        val noise = PitchFrame(null, 0.0, level = 0.3f)
        val steps = List(HearingTracker.UNCLEAR_FRAMES) { run.onFrame(noise) }
        assertTrue(steps.dropLast(1).all { it.hearingMean == null })
        assertEquals(0.3, steps.last().hearingMean!!, 1e-6)
        assertEquals(Hearing.QUIET_UNCLEAR, run.listening.hearing)
        assertEquals(TunerAdvice.COME_CLOSER, run.listening.advice)
        assertNull("unchanged: not reported again", run.onFrame(noise).hearingMean)
    }

    @Test
    fun `the room's floor carries from one run to the next`() {
        val first = pitchOnly(listOf(q(60)))
        first.onFrame(PitchFrame(null, 0.0, level = 0.2f))
        assertEquals(0.2f, first.floor)
        assertEquals(0.2f, ListenRun(listOf(q(60)), Pulse(60), floor = first.floor).floor)
    }

    /**
     * A clear note far too faint to be the flute at the phone (the child across the room, or the room itself) is
     * not judged, so it can earn no wrong mark, and the screen does not stay silent about it: no heard bubble, the
     * listening dots, and after about a second of it "Come closer to the phone". The phone of before 2026-11-10,
     * with no floor, judged it: a wrong mark for a sound nobody played at the phone.
     */
    @Test
    fun `a clear note too faint for the phone is not judged and asks to come closer`() {
        val faint = PitchFrame(NoteMath.midiToFrequency(64), 0.97, level = 0.36f)
        val run = pitchOnly(listOf(TuneNote(60, NoteLength.QUARTER), TuneNote(62, NoteLength.QUARTER)))
        repeat(HearingTracker.UNCLEAR_FRAMES) { run.onFrame(faint) }
        assertEquals(emptyMap<Int, StepMark>(), run.marks)
        assertNull(run.listening.wrong)
        assertNull(run.listening.heardNow)
        assertEquals(0, run.listening.index)
        assertEquals(TunerAdvice.COME_CLOSER, run.listening.advice)

        val before = ListenRun(listOf(TuneNote(60, NoteLength.QUARTER), TuneNote(62, NoteLength.QUARTER)), Pulse(60), NoteOnsetTracker.Rules(minLevel = 0f), run = ListenRun.Rules.PITCH_ONLY)
        repeat(HearingTracker.UNCLEAR_FRAMES) { before.onFrame(faint) }
        assertEquals(WrongNote(0, 64), before.listening.wrong)
    }

    /**
     * After a wrong note the advice is to check the fingers, and it stays, as the heard note stays under its step,
     * until the right note is played (since 2026-11-10: the heard note said what came out, nothing said what to do
     * about it). What the microphone itself has to say, and a repeat held on, come first.
     */
    @Test
    fun `a wrong note asks to check the fingers until the right note`() {
        val run = pitchOnly(listOf(q(60), q(62)))
        assertEquals(TunerAdvice.NONE, run.listening.advice)
        run.begin(64)
        assertEquals(TunerAdvice.CHECK_FINGERS, run.listening.advice)
        run.stop()
        assertEquals("it stays through the silence, as the heard note does", TunerAdvice.CHECK_FINGERS, run.listening.advice)
        run.begin(60)
        assertEquals(TunerAdvice.NONE, run.listening.advice)

        val wrong = WrongNote(0, 64)
        assertEquals(TunerAdvice.COME_CLOSER, Listening(index = 0, wrong = wrong, hearing = Hearing.QUIET_UNCLEAR).advice)
        assertEquals(TunerAdvice.CLEAR_NOTE, Listening(index = 0, wrong = wrong, hearing = Hearing.LOUD_UNCLEAR).advice)
        assertEquals(TunerAdvice.PLAY_AGAIN, Listening(index = 0, wrong = wrong, repeatHeld = true).advice)
    }

    // ---- Each note held for its length (2026-11-11, spec 2026-11-11-flute-your-turn-note-length-design) ----

    @Test
    fun `the slack is three quarters of a beat, so a half note at 60 a minute is long enough at 1250 ms`() {
        // The user's pick of 2026-11-13 from the sessions of 2026-11-12: the child stopped half notes at 1.2 to 1.7 s.
        val run = ListenRun(listOf(h(60), q(62), q(64)), Pulse(60))
        run.begin(60)
        repeat(after(1250.0) - 1) { assertFalse(run.onFrame(pitched(60)).advanced) }
        val half = run.onFrame(pitched(60))
        assertTrue(half.advanced)
        assertEquals(1250L, half.held?.needMs)
        run.stop()
        run.begin(62)
        repeat(after(250.0) - 1) { assertFalse(run.onFrame(pitched(62)).advanced) }
        val quarter = run.onFrame(pitched(62))
        assertTrue("a quarter needs a quarter of a beat", quarter.advanced)
        assertEquals(250L, quarter.held?.needMs)
    }

    @Test
    fun `a whole note at 60 a minute still needs the longest hold, not more`() {
        val run = ListenRun(listOf(TuneNote(60, WHOLE), q(62)), Pulse(60))
        run.begin(60)
        repeat(after(3000.0) - 1) { assertFalse(run.onFrame(pitched(60)).advanced) }
        assertEquals(3000L, run.onFrame(pitched(60)).held?.needMs)
    }

    @Test
    fun `a quarter is earned after half a beat, not before`() {
        val run = timed(q(60), q(62))
        val onset = run.begin(60)
        assertFalse("the animal stands on this note's head already: no hop on the spot", onset.hop)
        assertFalse(onset.advanced)
        assertEquals(Walk(0, 0, Walk.Phase.SOUNDING), run.listening.walk)
        repeat(after(500.0) - 1) { assertFalse(run.onFrame(pitched(60)).advanced) }
        assertTrue(run.marks.isEmpty())
        assertEquals(0, run.listening.index)
        val earned = run.onFrame(pitched(60))
        assertTrue(earned.advanced)
        assertEquals(0, earned.held?.index)
        assertEquals(500L, earned.held?.needMs)
        assertTrue("${earned.held}", earned.held!!.heldMs >= 500)
        assertEquals(mapOf(0 to StepMark.FIRST_TRY), run.marks)
        assertEquals("the run waits on the next note", 1, run.listening.index)
        assertEquals("the animal finishes the beat on the note it earned", Walk(0, 0, Walk.Phase.DONE), run.listening.walk)
    }

    @Test
    fun `a half note walks its second beat on the click and the animal moves on at its end, sound or not`() {
        val run = timed(h(60), q(62))
        run.begin(60)
        run.hold(60, after(1000.0) - 1)
        assertEquals(0, run.listening.walk?.halfBeat)
        val click = run.hold(60, 1)
        assertTrue(click.hop)
        assertEquals(Walk(0, 2, Walk.Phase.SOUNDING), run.listening.walk)
        val earned = run.hold(60, after(1500.0) - after(1000.0))
        assertTrue(earned.advanced)
        assertFalse("no click at the moment it is earned", earned.hop)
        // Let go in its last half beat: it was long enough, so the animal still finishes the note's time.
        val waits = List(after(2000.0) - after(1500.0) - 1) { run.onFrame(silent) }
        assertTrue(waits.none { it.hop })
        assertEquals(Walk(0, 2, Walk.Phase.DONE), run.listening.walk)
        val moved = run.onFrame(silent)
        assertTrue(moved.hop)
        assertNull(run.listening.walk)
        assertEquals(1, run.listening.index)
    }

    @Test
    fun `an eighth is earned at its onset and the animal moves on after its half beat`() {
        val run = timed(TuneNote(60, EIGHTH), q(62))
        val onset = run.begin(60)
        assertTrue(onset.advanced)
        assertEquals(mapOf(0 to StepMark.FIRST_TRY), run.marks)
        assertEquals(Walk.Phase.DONE, run.listening.walk?.phase)
        run.hold(60, after(500.0) - 1)
        assertEquals(0, run.listening.walk?.index)
        assertTrue(run.hold(60, 1).hop)
        assertNull(run.listening.walk)
    }

    @Test
    fun `a note cut short by silence stops the animal where it was and asks to hold it longer`() {
        val run = timed(h(60), q(62))
        run.begin(60)
        run.hold(60, after(1200.0))
        assertEquals(2, run.listening.walk?.halfBeat)
        val steps = List(NoteOnsetTracker.ONSET_GAP_FRAMES) { run.onFrame(silent) }
        val cut = steps.last()
        assertTrue(steps.dropLast(1).all { it.short == null })
        assertEquals(0, cut.short?.index)
        assertEquals(1500L, cut.short?.needMs)
        assertTrue("${cut.short}", cut.short!!.heldMs in 1200 until 1500)
        assertFalse(cut.hop)
        assertEquals(Walk(0, 2, Walk.Phase.SHORT), run.listening.walk)
        assertEquals(TunerAdvice.HOLD_LONGER, run.listening.advice)
        assertEquals(0, run.listening.index)
        assertTrue(run.marks.isEmpty())
    }

    @Test
    fun `another note cuts the note short`() {
        val run = timed(h(60), q(62))
        run.begin(60)
        run.hold(60, 5)
        val slur = run.begin(62)
        assertEquals(0, slur.short?.index)
        assertEquals(Walk.Phase.SHORT, run.listening.walk?.phase)
        assertEquals(TunerAdvice.HOLD_LONGER, run.listening.advice)
    }

    @Test
    fun `its own note started again with no gap goes on, so a breath dipping in a long note does not cut it`() {
        // The sessions of 2026-11-12: a child's breath dips 10 dB inside a whole note, which the tracker hears as a
        // tongued stop. The note never stopped; nor would two tongued halves of it have, for a child learning.
        val run = timed(h(60), q(62))
        run.begin(60)
        run.hold(60, after(1200.0))
        run.onFrame(pitched(60, level = 0.65f, minLevel = 0.2f)) // the breath dips
        val again = run.onFrame(pitched(60))
        assertEquals("the tracker heard a restart", NoteOnsetTracker.Onset(60, fresh = true), again.row.onset)
        assertNull(again.short)
        assertNull("nothing new is judged", again.heard)
        assertFalse(again.hop)
        assertEquals(Walk(0, 2, Walk.Phase.SOUNDING), run.listening.walk)
        val earned = run.hold(60, after(1500.0) - after(1200.0) - 2)
        assertTrue("its time counts from its first onset: ${earned.held}", earned.advanced)
        assertEquals(mapOf(0 to StepMark.FIRST_TRY), run.marks)
    }

    @Test
    fun `under the rule recorded until 2026-11-13 its own note started again cuts it short and walks it again`() {
        val run = timed(h(60), q(62), run = halfBeatSlack.copy(restartCuts = true))
        run.begin(60)
        run.hold(60, after(1200.0))
        run.onFrame(pitched(60, level = 0.65f, minLevel = 0.2f)) // the tongue stops the air
        val again = run.onFrame(pitched(60))
        assertEquals(0, again.short?.index)
        assertTrue(again.hop)
        assertEquals(Walk(0, 0, Walk.Phase.SOUNDING), run.listening.walk)
        // Started again from a tongued stop, the note's start is the onset itself (no frames locked it).
        run.hold(60, after(1500.0) + ListenRules.LOCK_FRAMES - 1)
        assertEquals(mapOf(0 to StepMark.RETRY), run.marks)
    }

    @Test
    fun `its own note started again after a gap still cuts it short`() {
        val run = timed(h(60), q(62))
        run.begin(60)
        run.hold(60, after(1200.0))
        run.stop()
        assertEquals(Walk.Phase.SHORT, run.listening.walk?.phase)
        assertEquals(TunerAdvice.HOLD_LONGER, run.listening.advice)
    }

    @Test
    fun `a note played again after a cut walks from the head and earns a faint star`() {
        val run = timed(h(60), q(62))
        run.begin(60)
        run.hold(60, after(1200.0))
        run.stop()
        val again = run.begin(60)
        assertTrue(again.hop)
        assertEquals(Walk(0, 0, Walk.Phase.SOUNDING), run.listening.walk)
        assertEquals(TunerAdvice.NONE, run.listening.advice)
        val earned = run.hold(60, after(1500.0))
        assertTrue(earned.advanced)
        assertEquals(mapOf(0 to StepMark.RETRY), run.marks)
    }

    @Test
    fun `another note while cut short is not a wrong note`() {
        val run = timed(q(60), q(62))
        run.begin(60)
        run.stop()
        val other = run.begin(64)
        assertEquals("logged as the screen shows it", Verdict.IGNORED, other.heard?.verdict)
        assertEquals(Verdict.IGNORED, other.row.verdict)
        assertNull(run.listening.wrong)
        assertEquals(TunerAdvice.HOLD_LONGER, run.listening.advice)
        assertEquals(64, run.listening.heardNow)
        assertEquals(Walk.Phase.SHORT, run.listening.walk?.phase)
        assertEquals(0, run.listening.index)
    }

    @Test
    fun `an octave pop while walking goes on`() {
        val run = timed(h(60), q(62))
        run.begin(60)
        run.hold(60, 10)
        val pop = run.begin(72)
        assertEquals(NoteOnsetTracker.Onset(72, fresh = false), pop.row.onset)
        assertNull(pop.short)
        assertEquals(Walk.Phase.SOUNDING, run.listening.walk?.phase)
        run.hold(72, after(1500.0) - 10 - ListenRules.LOCK_FRAMES)
        assertEquals(mapOf(0 to StepMark.FIRST_TRY), run.marks)
    }

    @Test
    fun `a long note sagging into the next letter is still the same note`() {
        // The tracker's drift: past halfway to c#1 for SWITCH_FRAMES moves the sounding note with no onset.
        val run = timed(TuneNote(60, WHOLE), q(62))
        run.begin(60)
        val sag = List(after(3500.0)) { run.onFrame(pitched(60, cents = 60.0)) }
        assertTrue("the drift happened", sag.any { it.row.sounding == 61 })
        assertTrue(sag.none { it.short != null })
        assertEquals(mapOf(0 to StepMark.FIRST_TRY), run.marks)
    }

    @Test
    fun `the gate blinking inside a held note does not cut it`() {
        val run = timed(h(60), q(62))
        run.begin(60)
        run.hold(60, 10)
        val blink = List(2) { run.onFrame(pitched(60, probability = 0.5)) }
        assertTrue(blink.none { it.short != null })
        run.hold(60, after(1500.0) - 12)
        assertEquals(mapOf(0 to StepMark.FIRST_TRY), run.marks)
    }

    @Test
    fun `a note begun in the last half beat is judged for the next step at once`() {
        val run = timed(q(60), q(62))
        run.begin(60)
        run.hold(60, after(500.0))
        run.stop()
        assertEquals(Walk.Phase.DONE, run.listening.walk?.phase)
        val next = run.begin(62)
        assertTrue(next.hop)
        assertEquals(Walk(1, 0, Walk.Phase.SOUNDING), run.listening.walk)
        assertEquals(1, run.listening.index)
    }

    @Test
    fun `at 44_1 kHz the same note is earned after fewer frames`() {
        val hop = PitchFrame.hopMsAt(44_100)
        val run = timed(q(60), q(62))
        run.begin(60, hopMs = hop)
        repeat(after(500.0, hop) - 1) { assertFalse(run.onFrame(pitched(60, hopMs = hop)).advanced) }
        assertTrue(run.onFrame(pitched(60, hopMs = hop)).advanced)
        assertTrue("fewer frames than at 48 kHz", after(500.0, hop) < after(500.0))
    }

    @Test
    fun `cut short, a wrong note, then the right one long enough never asks to check the fingers`() {
        val run = timed(q(60), q(62))
        val advice = mutableListOf<TunerAdvice>()
        fun note(midi: Int, frames: Int) = repeat(frames) { run.onFrame(pitched(midi)); advice += run.listening.advice }
        fun gap() = repeat(NoteOnsetTracker.ONSET_GAP_FRAMES) { run.onFrame(silent); advice += run.listening.advice }
        note(60, ListenRules.LOCK_FRAMES + 2)
        gap()
        note(64, ListenRules.LOCK_FRAMES + 2)
        gap()
        note(60, ListenRules.LOCK_FRAMES + after(500.0))
        assertTrue(advice.contains(TunerAdvice.HOLD_LONGER))
        assertFalse(advice.contains(TunerAdvice.CHECK_FINGERS))
        assertNull(run.listening.wrong)
        assertEquals(mapOf(0 to StepMark.RETRY), run.marks)
    }

    @Test
    fun `clicks follow the time signature`() {
        // 6/8: a click is an eighth, so a quarter has a click on its second half beat, at 1000 ms.
        val sixEight = timed(q(60), q(62), pulse = Pulse(60, TimeSignature.SIX_EIGHT))
        sixEight.begin(60)
        sixEight.hold(60, after(1000.0) - 1)
        assertEquals(0, sixEight.listening.walk?.halfBeat)
        assertTrue(sixEight.hold(60, 1).hop)
        assertEquals(1, sixEight.listening.walk?.halfBeat)
        // 2/2: a click is a half note, so a whole note hops once, to its fifth half beat, at 1000 ms.
        val twoTwo = timed(TuneNote(60, WHOLE), q(62), pulse = Pulse(60, TimeSignature.TWO_TWO))
        twoTwo.begin(60)
        val beats = List(after(1750.0)) { twoTwo.onFrame(pitched(60)); twoTwo.listening.walk?.halfBeat }
        assertEquals(listOf(0, 4), beats.filterNotNull().distinct())
        assertEquals(mapOf(0 to StepMark.FIRST_TRY), twoTwo.marks)
    }

    @Test
    fun `the last note earned finishes as before`() {
        val run = timed(q(60))
        run.begin(60)
        val earned = run.hold(60, after(500.0))
        assertTrue(earned.done)
        assertTrue(run.listening.finishing)
        assertEquals(0, run.listening.index)
        assertFalse(run.isOver)
        run.stop()
        assertTrue(run.isOver)
    }

    @Test
    fun `a written repeat slurred on is forgiven and the next note walks`() {
        val run = timed(q(60), q(60), q(62))
        run.begin(60)
        run.hold(60, after(500.0))
        val slur = run.begin(62)
        assertEquals(listOf(1), slur.forgiven)
        assertEquals(mapOf(0 to StepMark.FIRST_TRY, 1 to StepMark.RETRY), run.marks)
        assertEquals(Walk(2, 0, Walk.Phase.SOUNDING), run.listening.walk)
    }

    @Test
    fun `the advice order puts hold it longer under the microphone's and the held repeat's`() {
        val short = Walk(0, 2, Walk.Phase.SHORT)
        assertEquals(TunerAdvice.HOLD_LONGER, Listening(index = 0, walk = short).advice)
        assertEquals(TunerAdvice.COME_CLOSER, Listening(index = 0, walk = short, hearing = Hearing.QUIET_UNCLEAR).advice)
        assertEquals(TunerAdvice.CLEAR_NOTE, Listening(index = 0, walk = short, hearing = Hearing.LOUD_UNCLEAR).advice)
        assertEquals(TunerAdvice.PLAY_AGAIN, Listening(index = 0, walk = short, repeatHeld = true).advice)
        assertEquals(TunerAdvice.HOLD_LONGER, Listening(index = 0, walk = short, wrong = WrongNote(0, 64)).advice)
        assertEquals(TunerAdvice.COME_CLOSER, Listening(index = 0, wrong = WrongNote(0, 72, tooHigh = true), hearing = Hearing.QUIET_UNCLEAR).advice)
        assertEquals(TunerAdvice.OUT_OF_AIR, Listening(index = 0, walk = short, faded = true, wrong = WrongNote(0, 64)).advice)
        assertEquals(TunerAdvice.PLAY_AGAIN, Listening(index = 0, repeatHeld = true, walk = short, faded = true).advice)
    }

    // ---- Rests walked as in Play (2026-11-13, spec 2026-11-13-flute-your-turn-rests-design) ----

    private val rest = TuneRest(QUARTER)

    /** Feeds [frames], keeping each new walk the run shows with whether that frame hopped, and how many frames a rest was walked. */
    private class Watch(val run: ListenRun) {
        val walks = mutableListOf<Pair<Walk?, Boolean>>()
        var restFrames = 0
        private var last = run.listening.walk
        fun feed(frame: PitchFrame): ListenRun.Step {
            val step = run.onFrame(frame)
            val walk = run.listening.walk
            if (walk != last) walks += walk to step.hop
            last = walk
            if (walk?.phase == Walk.Phase.REST) restFrames++
            return step
        }
        fun feed(frame: PitchFrame, n: Int) = List(n) { feed(frame) }.lastOrNull()
        val sawRest get() = walks.any { it.first?.phase == Walk.Phase.REST }
    }

    /** A watched run over [tune] whose first note (60) has been earned: begun, then held half a beat. */
    private fun earned(vararg tune: TuneItem, run: ListenRun.Rules = halfBeatSlack): Watch {
        val watch = Watch(timed(*tune, run = run))
        watch.feed(pitched(60), ListenRules.LOCK_FRAMES + after(500.0))
        assertTrue(watch.run.marks.isNotEmpty())
        return watch
    }

    /** Silence until the walk stands on a rest. */
    private fun Watch.untilRest() {
        repeat(framesFor(2000.0)) { if (run.listening.walk?.phase != Walk.Phase.REST) feed(silent) }
        assertEquals(Walk.Phase.REST, run.listening.walk?.phase)
    }

    @Test
    fun `a rest after a note is walked on its clicks and then the animal waits at the next note`() {
        val watch = earned(q(60), TuneRest(HALF), q(62))
        watch.feed(silent, framesFor(3500.0))
        assertEquals(
            listOf(Walk(0, 0, Walk.Phase.SOUNDING), Walk(0, 0, Walk.Phase.DONE), Walk(1, 0, Walk.Phase.REST), Walk(1, 2, Walk.Phase.REST), null),
            watch.walks.map { it.first },
        )
        assertTrue("every move after the note was earned is a hop: ${watch.walks}", watch.walks.drop(2).all { it.second })
        assertEquals(2, watch.run.listening.index)
        assertEquals(mapOf(0 to StepMark.FIRST_TRY), watch.run.marks)
        assertTrue("the half rest lasted ${watch.restFrames} frames", watch.restFrames in framesFor(2000.0) - 1..framesFor(2000.0) + 1)
    }

    @Test
    fun `a note begun before the rest goes on to the note after it`() {
        val watch = earned(q(60), rest, q(62))
        watch.feed(silent, NoteOnsetTracker.ONSET_GAP_FRAMES)
        watch.feed(pitched(62), ListenRules.LOCK_FRAMES)
        assertEquals(Walk(2, 0, Walk.Phase.SOUNDING), watch.run.listening.walk)
        assertFalse(watch.sawRest)
    }

    @Test
    fun `a right note during a rest counts at once`() {
        val watch = earned(q(60), TuneRest(HALF), q(62))
        watch.untilRest()
        val step = watch.feed(pitched(62), ListenRules.LOCK_FRAMES)!!
        assertEquals(2, step.heard?.index)
        assertEquals(Verdict.RIGHT, step.heard?.verdict)
        assertEquals(Walk(2, 0, Walk.Phase.SOUNDING), watch.run.listening.walk)
        assertTrue(step.hop)
    }

    @Test
    fun `a wrong note during a rest shows at the next note`() {
        val watch = earned(q(60), TuneRest(HALF), q(62))
        watch.untilRest()
        val step = watch.feed(pitched(64), ListenRules.LOCK_FRAMES)!!
        assertEquals(WrongNote(2, 64), watch.run.listening.wrong)
        assertNull(watch.run.listening.walk)
        assertEquals(2, watch.run.listening.animalIndex)
        assertTrue(step.hop)
    }

    @Test
    fun `rests in a row are walked one after the other`() {
        val watch = earned(q(60), rest, rest, q(62))
        watch.feed(silent, framesFor(3500.0))
        assertEquals(
            listOf(Walk(1, 0, Walk.Phase.REST), Walk(2, 0, Walk.Phase.REST), null),
            watch.walks.map { it.first }.dropWhile { it?.phase != Walk.Phase.REST },
        )
        assertEquals(3, watch.run.listening.index)
    }

    @Test
    fun `a breath still takes no time`() {
        val watch = earned(q(60), TuneBreath, q(62))
        watch.feed(silent, framesFor(1500.0))
        assertFalse(watch.sawRest)
        assertNull(watch.run.listening.walk)
        assertEquals(2, watch.run.listening.index)
    }

    @Test
    fun `a rest that opens the tune is walked from the start`() {
        val run = timed(rest, q(60))
        assertEquals(Walk(0, 0, Walk.Phase.REST), run.listening.walk)
        assertEquals(1, run.listening.index)
        repeat(framesFor(1000.0)) { run.onFrame(silent) }
        assertNull(run.listening.walk)
        val early = timed(rest, q(60))
        early.begin(60)
        assertEquals("the first note may begin during it", Walk(1, 0, Walk.Phase.SOUNDING), early.listening.walk)
    }

    @Test
    fun `a rest that ends the tune is walked and then the run is over`() {
        val watch = earned(q(60), rest)
        assertTrue(watch.run.marks.isNotEmpty())
        watch.feed(pitched(60), framesFor(1100.0)) // the last note held on past its written time
        assertEquals(Walk(1, 0, Walk.Phase.REST), watch.run.listening.walk)
        assertFalse(watch.run.listening.finishing)
        assertFalse(watch.run.isOver)
        val late = watch.feed(pitched(62), ListenRules.LOCK_FRAMES)!!
        assertNull("nothing is waited on: not judged", late.heard)
        repeat(framesFor(1000.0)) { if (!watch.run.isOver) watch.feed(pitched(62)) }
        assertTrue(watch.run.isOver)
    }

    @Test
    fun `a rest waits behind a note cut short`() {
        val watch = Watch(timed(h(60), rest, q(62)))
        watch.feed(pitched(60), ListenRules.LOCK_FRAMES + 15)
        watch.feed(silent, framesFor(3000.0))
        assertEquals(Walk.Phase.SHORT, watch.run.listening.walk?.phase)
        assertEquals(0, watch.run.listening.index)
        assertFalse(watch.sawRest)
    }

    @Test
    fun `a rest after a note played again is timed from that note's start`() {
        val watch = Watch(timed(h(60), rest, q(62)))
        watch.feed(pitched(60), ListenRules.LOCK_FRAMES + 15)
        watch.feed(silent, NoteOnsetTracker.ONSET_GAP_FRAMES) // cut short
        watch.feed(pitched(60), ListenRules.LOCK_FRAMES) // played again: its start is three frames back
        var frames = ListenRules.LOCK_FRAMES
        while (watch.run.listening.walk?.phase != Walk.Phase.REST && frames < framesFor(3000.0)) {
            watch.feed(if (frames < framesFor(1600.0)) pitched(60) else silent)
            frames++
        }
        assertEquals(mapOf(0 to StepMark.RETRY), watch.run.marks)
        assertTrue("the rest began $frames frames after the note did", frames in framesFor(2000.0) - 1..framesFor(2000.0) + 1)
    }

    @Test
    fun `a written repeat held on through a rest asks to stop once the rest is over`() {
        // g, rest, g: the first g held on through the rest. Nothing is asked while the animal walks the rest; the
        // advice comes REPEAT_ADVICE_FRAMES after it, when the repeat is waited on.
        val watch = Watch(timed(q(67), rest, q(67)))
        watch.feed(pitched(67), ListenRules.LOCK_FRAMES + after(1000.0))
        while (watch.run.listening.walk?.phase != Walk.Phase.REST) watch.feed(pitched(67))
        while (watch.run.listening.walk?.phase == Walk.Phase.REST) {
            watch.feed(pitched(67))
            assertFalse(watch.run.listening.repeatHeld)
        }
        watch.feed(pitched(67), ListenRun.REPEAT_ADVICE_FRAMES - 1)
        assertFalse(watch.run.listening.repeatHeld)
        watch.feed(pitched(67))
        assertTrue(watch.run.listening.repeatHeld)
        assertEquals(TunerAdvice.PLAY_AGAIN, watch.run.listening.advice)
    }

    @Test
    fun `a wrong note during an opening rest shows at the first note`() {
        val run = timed(rest, q(60))
        run.begin(64)
        assertEquals(WrongNote(1, 64), run.listening.wrong)
        assertNull(run.listening.walk)
        assertEquals(1, run.listening.animalIndex)
    }

    @Test
    fun `with walkRests off rests are passed over as before`() {
        val off = halfBeatSlack.copy(walkRests = false)
        val watch = earned(q(60), rest, q(62), run = off)
        watch.feed(silent, framesFor(1100.0))
        assertFalse(watch.sawRest)
        assertNull(watch.run.listening.walk)
        assertEquals(2, watch.run.listening.index)
        assertNull(timed(rest, q(60), run = off).listening.walk)
    }

    // ---- Why, in a few words (2026-11-13, spec 2026-11-13-flute-your-turn-why-lines-design) ----

    @Test
    fun `a note stopped while still strong asks to hold it until the star`() {
        val run = timed(h(60), q(62))
        run.begin(60)
        run.hold(60, 15)
        val cut = List(NoteOnsetTracker.ONSET_GAP_FRAMES) { run.onFrame(silent) }.last()
        assertEquals(false, cut.short?.faded)
        assertFalse(run.listening.faded)
        assertEquals(TunerAdvice.HOLD_LONGER, run.listening.advice)
    }

    @Test
    fun `a note that fades away before it is long enough asks if the air ran out`() {
        val run = timed(h(60), q(62))
        run.begin(60)
        run.hold(60, 15)
        for (level in listOf(0.6f, 0.5f, 0.48f, 0.46f)) run.onFrame(pitched(60, level = level))
        val cut = List(NoteOnsetTracker.ONSET_GAP_FRAMES) { run.onFrame(silent) }.last()
        assertEquals(true, cut.short?.faded)
        assertTrue(run.listening.faded)
        assertEquals(TunerAdvice.OUT_OF_AIR, run.listening.advice)
        run.begin(60)
        assertFalse("played again, the guess goes", run.listening.faded)
        assertEquals(TunerAdvice.NONE, run.listening.advice)
    }

    @Test
    fun `a note cut short by another note is held too briefly, not out of air`() {
        val run = timed(h(60), q(62))
        run.begin(60)
        run.hold(60, 15)
        run.begin(62)
        assertEquals(TunerAdvice.HOLD_LONGER, run.listening.advice)
    }

    @Test
    fun `a note that sags and then slurs on to another is not out of air`() {
        // The child is still blowing: a guess about the air would be wrong. Only a note that stopped can have run out.
        val run = timed(h(60), q(62))
        run.begin(60)
        run.hold(60, 15)
        for (level in listOf(0.6f, 0.5f, 0.48f)) run.onFrame(pitched(60, level = level))
        run.begin(62)
        assertFalse(run.listening.faded)
        assertEquals(TunerAdvice.HOLD_LONGER, run.listening.advice)
    }

    @Test
    fun `with a longer gap rule a note stopped while strong is still no fade`() {
        // The tracker's gap frames are walked before the cut; they are silence, not the note's end.
        val run = ListenRun(listOf(h(60), q(62)), Pulse(60), NoteOnsetTracker.Rules(onsetGapFrames = 4), run = halfBeatSlack)
        run.begin(60)
        run.hold(60, 15)
        repeat(4) { run.onFrame(silent) }
        assertEquals(Walk.Phase.SHORT, run.listening.walk?.phase)
        assertFalse(run.listening.faded)
    }

    @Test
    fun `eight frames walked are enough to tell a fade, seven are not`() {
        fun cutAfter(holds: Int): ListenRun {
            val run = timed(h(60), q(62))
            run.begin(60)
            run.hold(60, holds)
            for (level in listOf(0.5f, 0.48f, 0.46f)) run.onFrame(pitched(60, level = level))
            run.stop()
            return run
        }
        assertTrue("onset, three held, three fading, one gap frame: eight", cutAfter(3).listening.faded)
        assertFalse("seven: no guess", cutAfter(2).listening.faded)
    }

    @Test
    fun `a note cut short at once is no guess about the air`() {
        val run = timed(h(60), q(62))
        run.begin(60)
        run.onFrame(pitched(60, level = 0.5f))
        run.stop()
        assertFalse(run.listening.faded)
        assertEquals(TunerAdvice.HOLD_LONGER, run.listening.advice)
    }

    @Test
    fun `a note just above the written one, near its pitch, asks if it was too high`() {
        // The sharp b of 2026-11-13: fingered b, blown 55 cents high, heard as c.
        val run = timed(q(71), q(67))
        val heard = List(ListenRules.LOCK_FRAMES) { run.onFrame(pitched(71, cents = 55.0)) }.last()
        assertEquals(WrongNote(0, 72, tooHigh = true), run.listening.wrong)
        assertEquals(TunerAdvice.TOO_HIGH, run.listening.advice)
        assertEquals(55.0, heard.cents!!, 0.01)
        run.stop()
        run.begin(71)
        assertNull(run.listening.wrong)
        assertEquals(TunerAdvice.NONE, run.listening.advice)
    }

    @Test
    fun `a note a semitone above but far from the written pitch is a wrong note`() {
        val run = timed(q(71), q(67))
        val heard = run.begin(72)
        assertEquals(WrongNote(0, 72), run.listening.wrong)
        assertEquals(TunerAdvice.CHECK_FINGERS, run.listening.advice)
        assertEquals(100.0, heard.cents!!, 0.01)
    }

    @Test
    fun `a note just under the written one is a wrong note, never too low`() {
        val run = timed(q(71), q(67))
        List(ListenRules.LOCK_FRAMES) { run.onFrame(pitched(71, cents = -55.0)) }
        assertEquals(WrongNote(0, 70), run.listening.wrong)
        assertEquals(TunerAdvice.CHECK_FINGERS, run.listening.advice)
    }

    @Test
    fun `a note above the written one in another octave is a wrong note`() {
        val run = timed(q(71), q(67))
        List(ListenRules.LOCK_FRAMES) { run.onFrame(pitched(84, cents = -45.0)) }
        assertEquals(WrongNote(0, 84), run.listening.wrong)
        assertEquals(TunerAdvice.CHECK_FINGERS, run.listening.advice)
    }

    @Test
    fun `nothing judged, no cents`() {
        assertNull(timed(q(71)).onFrame(silent).cents)
    }

    @Test
    fun `a written repeat heard and cut short waits, and the note after it does not forgive it`() {
        // Mary had a little lamb's half notes repeat the note before them: c, then c held too short, then d.
        val run = timed(q(60), h(60), q(62))
        run.begin(60)
        run.hold(60, after(1000.0)) // c earned, its time over
        run.stop()
        run.begin(60)
        run.hold(60, 10) // the half note's c, heard and walking
        run.stop() // cut short
        assertEquals(Walk.Phase.SHORT, run.listening.walk?.phase)
        val d = run.begin(62)
        assertTrue(d.forgiven.isEmpty())
        assertEquals(mapOf(0 to StepMark.FIRST_TRY), run.marks)
        assertEquals(Walk(1, run.listening.walk!!.halfBeat, Walk.Phase.SHORT), run.listening.walk)
        assertEquals(TunerAdvice.HOLD_LONGER, run.listening.advice)
    }

    @Test
    fun `a slur back to the note just earned waits a full second before asking to play it again`() {
        // c earned; in its last half beat a wrong d, then a slur back to c: the repeat's advice counts from there.
        val run = timed(q(60), q(60), q(64))
        run.begin(60)
        run.hold(60, after(500.0))
        run.begin(62)
        assertEquals(WrongNote(1, 62), run.listening.wrong)
        val back = run.begin(60)
        assertEquals(Verdict.IGNORED, back.heard?.verdict)
        val advice = List(ListenRun.REPEAT_ADVICE_FRAMES - 1) { run.onFrame(pitched(60)); run.listening.advice }
        assertFalse(advice.toString(), advice.contains(TunerAdvice.PLAY_AGAIN))
        run.onFrame(pitched(60))
        assertEquals(TunerAdvice.PLAY_AGAIN, run.listening.advice)
    }

    @Test
    fun `a note's time counts its first frame in full`() {
        // The sound began inside the first frame that heard it: a note heard in 12 frames has sounded 12 hops, 512 ms.
        val run = timed(q(60), q(62))
        run.begin(60) // three frames: the onset's and the two that locked it
        repeat(8) { assertFalse(run.onFrame(pitched(60)).advanced) } // 11 frames, 469 ms
        val earned = run.onFrame(pitched(60)) // 12 frames, 512 ms
        assertTrue(earned.advanced)
        assertEquals(512L, earned.held?.heldMs)
    }

    @Test
    fun `a long note at a slow tempo is earned after the longest hold a child can give`() {
        // A whole note at 40 a minute would need 5250 ms: more breath than a child has. Since 2026-11-11 (the user's
        // "ok") no note needs more than MAX_NEED_MS; the animal still walks its written time until the next note.
        val run = timed(TuneNote(60, WHOLE), q(62), pulse = Pulse(40))
        run.begin(60)
        repeat(after(ListenRun.MAX_NEED_MS) - 1) { assertFalse(run.onFrame(pitched(60)).advanced) }
        val earned = run.onFrame(pitched(60))
        assertTrue(earned.advanced)
        assertEquals(ListenRun.MAX_NEED_MS.toLong(), earned.held?.needMs)
        assertEquals(Walk.Phase.DONE, run.listening.walk?.phase)
        val next = run.begin(62)
        assertTrue("the next note takes the animal there at once", next.hop)
        assertEquals(Walk(1, 0, Walk.Phase.SOUNDING), run.listening.walk)
    }

    @Test
    fun `the longest hold is a rule a replay can move`() {
        val run = timed(TuneNote(60, HALF), q(62), run = halfBeatSlack.copy(maxNeedMs = 1000.0))
        run.begin(60)
        val steps = List(after(1000.0)) { run.onFrame(pitched(60)) }
        assertEquals(1000L, steps.last().held?.needMs)
    }

    @Test
    fun `a repeat cut short by a slip is played again by going back to it`() {
        // c, then c again as a half note: tongued and walking, a finger slips to g and comes back. The c that
        // comes back by a step walks again; before, it was ignored as the first c's tail and the run sat on
        // "Hold it longer" while the child held the right note (the review of 2026-11-11).
        val run = timed(q(60), h(60), q(62))
        run.begin(60)
        run.hold(60, after(1000.0)) // the first c earned, its time over
        run.stop()
        run.begin(60) // the repeat, tongued
        run.hold(60, 5)
        run.begin(67) // the slip
        assertEquals(Walk.Phase.SHORT, run.listening.walk?.phase)
        val back = run.begin(60)
        assertEquals(Verdict.RETRY, back.heard?.verdict)
        assertEquals(Walk(1, 0, Walk.Phase.SOUNDING), run.listening.walk)
        run.hold(60, after(1500.0))
        assertEquals(mapOf(0 to StepMark.FIRST_TRY, 1 to StepMark.RETRY), run.marks)
    }

    @Test
    fun `the animal never hops twice in one place`() {
        // The user's rule of 2026-11-04 for Play, kept here: the animal hops when its place changes. It already
        // stands on the head of the note waited on, so that note beginning is no hop; arriving there was.
        val run = timed(q(60), q(62), q(64))
        val places = mutableListOf<Pair<Int?, Int>>()
        fun feed(frame: PitchFrame) {
            val step = run.onFrame(frame)
            if (step.hop) places += run.listening.animalIndex to run.listening.animalHalfBeat
        }
        repeat(ListenRules.LOCK_FRAMES + after(1000.0)) { feed(pitched(60)) } // c, for all of its beat
        repeat(NoteOnsetTracker.ONSET_GAP_FRAMES) { feed(silent) }
        repeat(ListenRules.LOCK_FRAMES + after(1000.0)) { feed(pitched(62)) } // d, the same
        assertEquals("one hop to d's head and one to e's, none on the spot", listOf<Pair<Int?, Int>>(1 to 0, 2 to 0), places)
    }

    @Test
    fun `a wobble of other notes before the note is not the note's time`() {
        val run = timed(q(60), q(62))
        repeat(9) { run.onFrame(pitched(if (it % 2 == 0) 64 else 66)) }
        val onset = run.begin(60)
        assertEquals(Verdict.RIGHT, onset.heard?.verdict)
        assertFalse("128 ms of c is no quarter", onset.advanced)
        assertTrue(run.marks.isEmpty())
    }

    // ---- What counts as the note sounding (the review of 2026-11-11) ----

    @Test
    fun `loud sound with no pitch after a note began is not the note's time`() {
        // Three frames of c, then air noise as loud as a note: the tracker holds the note through it for most of a
        // second (a pause is not a blink), but only the two frames a gap takes to be seen are the note's.
        val run = timed(q(60), q(62))
        run.begin(60)
        val noise = PitchFrame(null, 0.0, level = 0.6f)
        val steps = List(ListenRules.HOLD_FRAMES) { run.onFrame(noise) }
        assertTrue("noise earned the note", steps.none { it.advanced })
        val short = steps.mapNotNull { it.short }.single()
        assertEquals(0, short.index)
        // The three that locked it and the two of grace.
        assertTrue("$short", short.heldMs <= (ListenRules.LOCK_FRAMES + ListenRun.GRACE_FRAMES) * PitchFrame.DEFAULT_HOP_MS)
        assertTrue(run.marks.isEmpty())
    }

    @Test
    fun `a note that fades under the phone's floor is not held on`() {
        val run = timed(q(60), q(62))
        run.begin(60)
        val faint = PitchFrame(NoteMath.midiToFrequency(60), 0.97, level = 0.4f)
        val steps = List(ListenRules.HOLD_FRAMES) { run.onFrame(faint) }
        assertTrue(steps.none { it.advanced })
        assertTrue(run.marks.isEmpty())
    }

    @Test
    fun `a note's last two frames still count when they lost their pitch`() {
        // The recorded quarters end like this (2026-11-05_17-01-39, step 4): the tone thins before it stops.
        val run = timed(q(60), q(62))
        run.begin(60)
        run.hold(60, 8) // 11 frames, 469 ms
        assertTrue(run.marks.isEmpty())
        val last = run.onFrame(pitched(60, probability = 0.5))
        assertTrue(last.advanced)
        assertEquals(512L, last.held?.heldMs)
    }

    @Test
    fun `how many frames a note's time runs on past its last shown frame is the run's own rule`() {
        val run = timed(q(60), q(62), run = halfBeatSlack.copy(graceFrames = 0))
        run.begin(60)
        run.hold(60, 8) // 11 frames, 469 ms
        assertFalse("no grace: the unshown frame adds nothing", run.onFrame(pitched(60, probability = 0.5)).advanced)
        assertEquals("the tracker's own gap is not it", NoteOnsetTracker.ONSET_GAP_FRAMES, ListenRun.GRACE_FRAMES)
    }

    @Test
    fun `a breathy stretch inside a note counts in full once the tone is back`() {
        val run = timed(q(60), q(62))
        run.begin(60)
        run.hold(60, 2)
        repeat(4) { assertFalse(run.onFrame(pitched(60, probability = 0.5)).advanced) } // 9 frames
        assertFalse(run.onFrame(pitched(60)).advanced)
        assertFalse(run.onFrame(pitched(60)).advanced) // 11 frames, 469 ms
        assertTrue(run.onFrame(pitched(60)).advanced)
    }

    // ---- The run's own rules, one value a session records and a replay moves ----

    @Test
    fun `the run's rules are named with their values, in the order a session writes them`() {
        assertEquals(
            listOf(
                "repeatAdviceFrames" to "25", "finishFrames" to "35", "lengthSlack" to "1.5", "maxNeedMs" to "3000.0",
                "restartCuts" to "0", "graceFrames" to "2", "walkRests" to "1",
            ),
            ListenRun.Rules().fields(),
        )
        assertEquals(
            setOf("repeatAdviceFrames", "finishFrames", "lengthSlack", "maxNeedMs", "restartCuts", "graceFrames", "walkRests"),
            ListenRun.Rules.NAMES,
        )
    }

    @Test
    fun `a rule of the run is changed by name`() {
        assertEquals(ListenRun.Rules(lengthSlack = 2.0), ListenRun.Rules().with("lengthSlack", 2.0))
        assertEquals(ListenRun.Rules(maxNeedMs = 2000.0), ListenRun.Rules().with("maxNeedMs", 2000.0))
        assertEquals(ListenRun.Rules(finishFrames = 10), ListenRun.Rules().with("finishFrames", 10.4))
        assertEquals(ListenRun.Rules(repeatAdviceFrames = 12), ListenRun.Rules().with("repeatAdviceFrames", 12.0))
        assertEquals(ListenRun.Rules(restartCuts = true), ListenRun.Rules().with("restartCuts", 1.0))
        assertEquals(ListenRun.Rules(restartCuts = false), ListenRun.Rules(restartCuts = true).with("restartCuts", 0.0))
        assertEquals("1", ListenRun.Rules(restartCuts = true).fields().toMap()["restartCuts"])
        assertEquals(ListenRun.Rules(graceFrames = 3), ListenRun.Rules().with("graceFrames", 3.0))
        assertEquals(ListenRun.Rules(walkRests = false), ListenRun.Rules().with("walkRests", 0.0))
        assertTrue(runCatching { ListenRun.Rules().with("stopDrop", 0.1) }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `pitch only, any note is long enough the moment it is heard`() {
        // No slack to run out of however long a note is written: the rule of before 2026-11-11.
        val run = ListenRun(listOf(TuneNote(60, WHOLE), q(62)), Pulse(40), run = ListenRun.Rules.PITCH_ONLY)
        assertTrue(run.begin(60).advanced)
        assertEquals(mapOf(0 to StepMark.FIRST_TRY), run.marks)
    }

    @Test
    fun `the frame counts of the run are rules too`() {
        val run = timed(q(60), run = halfBeatSlack.copy(finishFrames = 3))
        run.begin(60)
        run.hold(60, after(500.0)) // earned: the last note, finishing
        assertTrue(run.listening.finishing)
        run.hold(60, 2)
        assertFalse(run.isOver)
        run.hold(60, 1)
        assertTrue(run.isOver)
    }
}
