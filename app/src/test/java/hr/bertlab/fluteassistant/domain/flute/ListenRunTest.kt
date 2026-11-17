// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.flute

import hr.bertlab.fluteassistant.domain.flute.NoteLength.EIGHTH
import hr.bertlab.fluteassistant.domain.flute.NoteLength.HALF
import hr.bertlab.fluteassistant.domain.flute.NoteLength.QUARTER
import hr.bertlab.fluteassistant.domain.flute.NoteLength.WHOLE
import hr.bertlab.fluteassistant.domain.flute.TestTunes.h
import hr.bertlab.fluteassistant.domain.flute.TestTunes.j
import hr.bertlab.fluteassistant.domain.flute.TestTunes.q
import hr.bertlab.fluteassistant.domain.listen.TestFrames.framesFor
import hr.bertlab.fluteassistant.domain.listen.TestFrames.pitched
import hr.bertlab.fluteassistant.domain.listen.TestFrames.silent
import hr.bertlab.fluteassistant.domain.listen.Hearing
import hr.bertlab.fluteassistant.domain.listen.HearingTracker
import hr.bertlab.fluteassistant.domain.listen.ListenRules
import hr.bertlab.fluteassistant.domain.listen.NoteOnsetTracker
import hr.bertlab.fluteassistant.domain.listen.PitchFrame
import hr.bertlab.fluteassistant.domain.listen.TunerAdvice
import hr.bertlab.fluteassistant.domain.metronome.TempoBand
import hr.bertlab.fluteassistant.domain.metronome.TimeSignature
import hr.bertlab.fluteassistant.domain.tuner.NoteMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A "Your turn" fed hand-made frames, no microphone and no coroutines; real sound is `ListenPathTest`'s. */
class ListenRunTest {

    /** The rule of before 2026-11-11, pitch only: every note is long enough at its onset. */
    private fun pitchOnly(tune: List<TuneItem>) = ListenRun(tune, Pulse(60), run = ListenRun.Rules.PITCH_ONLY)

    /**
     * The walk's workings at half a beat's slack, whose numbers are round: at 60 a minute in 4/4 a quarter note lasts
     * 1000 ms and is long enough at 500, a half note at 1500. The slack the app runs with is checked on its own
     * ("the slack is three quarters of a beat"). No note is too long here (`longSlack` infinite): these tests are about
     * the rules of before the levels of 2026-11-16, and many hold a note on; the room to run over is checked on its own
     * ("A note held too long").
     */
    private val halfBeatSlack = ListenRun.Rules(lengthSlack = 1.0, longSlack = Double.POSITIVE_INFINITY)

    /** The rule until 2026-11-14: a note let go too early is waited on until it is played long enough. */
    private val waits = halfBeatSlack.copy(shortMovesOn = false)
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
        val run = timed(h(60), q(62), run = waits)
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
        val run = timed(h(60), q(62), run = waits)
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
        val run = timed(h(60), q(62), run = waits.copy(restartCuts = true))
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
        val run = timed(h(60), q(62), run = waits)
        run.begin(60)
        run.hold(60, after(1200.0))
        run.stop()
        assertEquals(Walk.Phase.SHORT, run.listening.walk?.phase)
        assertEquals(TunerAdvice.HOLD_LONGER, run.listening.advice)
    }

    @Test
    fun `a note played again after a cut walks from the head and earns a faint star`() {
        val run = timed(h(60), q(62), run = waits)
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
        val run = timed(q(60), q(62), run = waits)
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
        assertEquals("one note, and since 2026-11-14 one that went 60 cents high", mapOf(0 to StepMark.HIGH), run.marks)
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
        val run = timed(q(60), q(62), run = waits)
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
        assertEquals(TunerAdvice.COME_CLOSER, Listening(index = 0, pitch = Pitch.HIGH, hearing = Hearing.QUIET_UNCLEAR).advice)
        assertEquals(TunerAdvice.TOO_LOW, Listening(index = 0, pitch = Pitch.LOW, wrong = WrongNote(0, 64)).advice)
        assertEquals(TunerAdvice.TOO_HIGH, Listening(index = 1, remark = StepMark.HIGH).advice)
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
        val watch = Watch(timed(h(60), rest, q(62), run = waits))
        watch.feed(pitched(60), ListenRules.LOCK_FRAMES + 15)
        watch.feed(silent, framesFor(3000.0))
        assertEquals(Walk.Phase.SHORT, watch.run.listening.walk?.phase)
        assertEquals(0, watch.run.listening.index)
        assertFalse(watch.sawRest)
    }

    @Test
    fun `a rest after a note played again is timed from that note's start`() {
        val watch = Watch(timed(h(60), rest, q(62), run = waits))
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
        val run = timed(h(60), q(62), run = waits)
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
        val run = timed(h(60), q(62), run = waits)
        run.begin(60)
        run.hold(60, 15)
        run.begin(62)
        assertEquals(TunerAdvice.HOLD_LONGER, run.listening.advice)
    }

    @Test
    fun `a note that sags and then slurs on to another is not out of air`() {
        // The child is still blowing: a guess about the air would be wrong. Only a note that stopped can have run out.
        val run = timed(h(60), q(62), run = waits)
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
        val run = ListenRun(listOf(h(60), q(62)), Pulse(60), NoteOnsetTracker.Rules(onsetGapFrames = 4), run = waits)
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
    fun `a note blown high in another octave is the written note too`() {
        // c3 45 cents flat is b2 55 cents high: the letter counts in any octave, and so does the band above it.
        val run = timed(q(71), q(67))
        val heard = List(ListenRules.LOCK_FRAMES) { run.onFrame(pitched(84, cents = -45.0)) }.last()
        assertEquals(Verdict.RIGHT, heard.heard?.verdict)
        assertNull(run.listening.wrong)
        assertEquals(Pitch.HIGH, run.listening.pitch)
    }

    @Test
    fun `nothing judged, no cents`() {
        assertNull(timed(q(71)).onFrame(silent).cents)
    }

    @Test
    fun `a written repeat heard and cut short waits, and the note after it does not forgive it`() {
        // Mary had a little lamb's half notes repeat the note before them: c, then c held too short, then d.
        val run = timed(q(60), h(60), q(62), run = waits)
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
        val run = timed(q(60), h(60), q(62), run = waits)
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
    fun `a note played again that already reaches the click the animal stopped on is no hop`() {
        // Cut short on its second beat, then played again with an attack that wavers for over a beat before it
        // locks: those frames are the note's (the tracker's lead), so by its onset it has reached the same click.
        // Before, the way back to the head counted as a hop, and the animal hopped on the spot.
        val run = timed(h(60), q(62), run = waits)
        run.begin(60)
        run.hold(60, after(1200.0))
        run.stop()
        assertEquals(Walk(0, 2, Walk.Phase.SHORT), run.listening.walk)
        repeat(framesFor(1000.0)) { run.onFrame(pitched(60, cents = if (it % 2 == 0) 20.0 else -20.0)) }
        assertEquals("the wavering attack is no note yet", Walk(0, 2, Walk.Phase.SHORT), run.listening.walk)
        val again = generateSequence { run.onFrame(pitched(60)) }.first { it.heard != null }
        assertEquals(Verdict.RETRY, again.heard?.verdict)
        assertEquals(Walk(0, 2, Walk.Phase.SOUNDING), run.listening.walk)
        assertFalse("it stands where it stood", again.hop)
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
        val run = timed(q(60), q(62), run = waits)
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
        val run = timed(q(60), q(62), run = waits)
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
                "highCents" to "75.0", "tuneCents" to "25.0", "tuneFrames" to "3", "shortMovesOn" to "1",
                "paceMinPairs" to "6", "paceOff" to "0.15", "longSlack" to "1.5",
                "slurCheck" to "1", "slurStopDrop" to "0.25",
            ),
            ListenRun.Rules().fields(),
        )
        assertEquals(
            setOf(
                "repeatAdviceFrames", "finishFrames", "lengthSlack", "maxNeedMs", "restartCuts", "graceFrames", "walkRests",
                "highCents", "tuneCents", "tuneFrames", "shortMovesOn", "paceMinPairs", "paceOff", "longSlack",
                "slurCheck", "slurStopDrop",
            ),
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

    // ---- Feedback, not stars (2026-11-14, spec 2026-11-14-flute-your-turn-feedback-design) ----

    @Test
    fun `a note blown high walks with the arrow and earns the down arrow`() {
        val run = timed(q(71), q(69))
        val begun = List(ListenRules.LOCK_FRAMES) { run.onFrame(pitched(71, cents = 60.0)) }.last()
        assertEquals(Verdict.RIGHT, begun.heard?.verdict)
        assertEquals("the tracker's nearest semitone is c2", 72, begun.heard?.onset?.midi)
        assertEquals(Pitch.HIGH, run.listening.pitch)
        assertEquals("the bubble is the written note's", 71, run.listening.heardNow)
        assertEquals(TunerAdvice.TOO_HIGH, run.listening.advice)
        assertEquals("the frame keeps the arrow", Pitch.HIGH, begun.row.pitch)
        val held = List(after(500.0)) { run.onFrame(pitched(71, cents = 60.0)) }.last()
        assertEquals(StepMark.HIGH, held.held?.mark)
        assertEquals(60.0, held.held?.cents!!, 1.0)
        assertEquals(mapOf(0 to StepMark.HIGH), run.marks)
        assertNull(run.listening.pitch)
        assertEquals("the line stays until the next note is judged", TunerAdvice.TOO_HIGH, run.listening.advice)
        run.stop()
        run.begin(69)
        assertEquals(TunerAdvice.NONE, run.listening.advice)
    }

    @Test
    fun `a long note that comes down in time earns its star`() {
        val run = timed(h(71))
        List(ListenRules.LOCK_FRAMES) { run.onFrame(pitched(71, cents = 38.0)) }
        repeat(5) { run.onFrame(pitched(71, cents = 38.0)) }
        assertEquals(Pitch.HIGH, run.listening.pitch)
        repeat(4) { run.onFrame(pitched(71, cents = 12.0)) } // the median of five turns on the third, two more make three in a row
        assertEquals(Pitch.HIGH, run.listening.pitch)
        run.onFrame(pitched(71, cents = 12.0))
        assertNull(run.listening.pitch)
        assertEquals(TunerAdvice.NONE, run.listening.advice)
        val held = List(after(1500.0) - 10) { run.onFrame(pitched(71, cents = 12.0)) }.last()
        assertEquals(StepMark.FIRST_TRY, held.held?.mark)
    }

    @Test
    fun `a note a little low gets the up arrow`() {
        val run = timed(q(71))
        List(ListenRules.LOCK_FRAMES) { run.onFrame(pitched(71, cents = -40.0)) }
        assertEquals(Pitch.LOW, run.listening.pitch)
        assertEquals(TunerAdvice.TOO_LOW, run.listening.advice)
        val held = List(after(500.0)) { run.onFrame(pitched(71, cents = -40.0)) }.last()
        assertEquals(StepMark.LOW, held.held?.mark)
    }

    @Test
    fun `frames the tracker only holds do not move the arrow`() {
        val run = timed(h(71))
        List(ListenRules.LOCK_FRAMES) { run.onFrame(pitched(71, cents = 40.0)) }
        repeat(6) { run.onFrame(pitched(71, probability = 0.5)) } // under the gate: held, not shown
        assertEquals(Pitch.HIGH, run.listening.pitch)
    }

    @Test
    fun `an octave pop of a note blown high is the note going on`() {
        val run = timed(h(71))
        List(ListenRules.LOCK_FRAMES) { run.onFrame(pitched(71, cents = 60.0)) }
        val popped = List(6) { run.onFrame(pitched(83, cents = 60.0)) }
        assertTrue(popped.none { it.short != null })
        assertEquals(Walk.Phase.SOUNDING, run.listening.walk?.phase)
    }

    @Test
    fun `a note blown high before a written note a semitone up does not swallow that note`() {
        // The band's one exception: c2 27 cents flat is b1 73 cents high, and the c written next is what was played. The
        // tracker measures a step from the b's own pitch, so only past 70 cents is it a new note and not a sag.
        val run = timed(h(71), q(72))
        List(ListenRules.LOCK_FRAMES + 2) { run.onFrame(pitched(71)) }
        val step = generateSequence { run.onFrame(pitched(72, cents = -27.0)) }.take(8).first { it.heard != null || it.short != null }
        assertTrue("the b is cut short", step.short != null)
        assertEquals("and the c is judged for its own step", ListenRun.Heard(1, 72, NoteOnsetTracker.Onset(72, fresh = false), Verdict.RIGHT), step.heard)
    }

    @Test
    fun `the tail of a note blown high is not the next note's bubble, and a held repeat asks to play it again`() {
        val run = timed(q(71), q(69))
        List(ListenRules.LOCK_FRAMES + after(500.0)) { run.onFrame(pitched(71, cents = 60.0)) }
        assertEquals(mapOf(0 to StepMark.HIGH), run.marks)
        repeat(framesFor(1000.0)) { run.onFrame(pitched(71, cents = 60.0)) } // held on past its written length: still c2 to the tracker
        assertNull("no c under the a waited on", run.listening.heardNow)

        val repeat = timed(q(71), q(71))
        List(ListenRules.LOCK_FRAMES + after(500.0)) { repeat.onFrame(pitched(71, cents = 60.0)) }
        repeat(framesFor(1000.0) + ListenRun.REPEAT_ADVICE_FRAMES) { repeat.onFrame(pitched(71, cents = 60.0)) }
        assertEquals("the written b under its repeat", 71, repeat.listening.heardNow)
        assertTrue(repeat.listening.repeatHeld)
        assertEquals(TunerAdvice.PLAY_AGAIN, repeat.listening.advice)
    }

    @Test
    fun `without the feedback rules a note above is a wrong note and no arrow shows`() {
        val before = ListenRun.Rules(lengthSlack = 1.0, highCents = 0.0, tuneCents = Double.POSITIVE_INFINITY)
        val high = timed(q(71), run = before)
        assertEquals(Verdict.WRONG, List(ListenRules.LOCK_FRAMES) { high.onFrame(pitched(71, cents = 60.0)) }.last().heard?.verdict)
        val sharp = timed(q(71), run = before)
        List(ListenRules.LOCK_FRAMES) { sharp.onFrame(pitched(71, cents = 40.0)) }
        assertNull(sharp.listening.pitch)
    }

    // ---- A note let go too early is marked and the run moves on (2026-11-14) ----

    private fun ListenRun.gap(): ListenRun.Step = List(NoteOnsetTracker.ONSET_GAP_FRAMES) { onFrame(silent) }.last()

    @Test
    fun `a note let go too early gets the hourglass and the run is on the next note`() {
        val run = timed(h(60), q(62))
        run.begin(60)
        run.hold(60, after(500.0)) // 500 of the 1500 ms it needs
        val cut = run.gap()
        assertEquals(StepMark.SHORT, cut.short?.mark)
        assertTrue(cut.advanced)
        assertEquals(mapOf(0 to StepMark.SHORT), run.marks)
        assertEquals(1, run.listening.index)
        assertEquals(Walk(0, 0, Walk.Phase.CUT), run.listening.walk)
        assertEquals(TunerAdvice.HOLD_LONGER, run.listening.advice)
        val next = run.begin(62)
        assertEquals(ListenRun.Heard(1, 62, NoteOnsetTracker.Onset(62, fresh = true), Verdict.RIGHT), next.heard)
        assertEquals(TunerAdvice.NONE, run.listening.advice)
    }

    @Test
    fun `the note that cuts a note short is judged for the next step`() {
        val run = timed(h(60), q(62))
        run.begin(60)
        run.hold(60, after(500.0))
        val step = generateSequence { run.onFrame(pitched(62)) }.take(8).first { it.heard != null }
        assertEquals(StepMark.SHORT, step.short?.mark)
        assertEquals(Verdict.RIGHT, step.heard?.verdict)
        assertEquals(1, step.heard?.index)
    }

    @Test
    fun `after a cut the animal stands for the rest of the written time, then goes on`() {
        val run = timed(h(60), q(62))
        run.begin(60)
        run.hold(60, after(500.0))
        run.gap()
        val rest = List(framesFor(2000.0) - framesFor(500.0) - NoteOnsetTracker.ONSET_GAP_FRAMES) { run.onFrame(silent) }
        assertTrue("no hop while it stands", rest.dropLast(1).none { it.hop })
        assertTrue("one hop to the next note", rest.last().hop)
        assertNull(run.listening.walk)
    }

    @Test
    fun `the hourglass says more than an arrow`() {
        val run = timed(h(71))
        List(ListenRules.LOCK_FRAMES) { run.onFrame(pitched(71, cents = 60.0)) }
        assertEquals(StepMark.SHORT, run.gap().short?.mark)
    }

    @Test
    fun `the last note let go too early ends the run`() {
        val run = timed(q(60), h(62))
        run.begin(60)
        run.hold(60, after(500.0))
        run.stop()
        run.begin(62)
        val cut = run.gap()
        assertTrue(cut.done)
        run.onFrame(silent)
        assertTrue(run.isOver)
    }

    @Test
    fun `a note that faded before it was long enough asks about the air and moves on`() {
        val run = timed(h(60), q(62))
        run.begin(60)
        run.hold(60, 12)
        repeat(4) { run.onFrame(pitched(60, level = 0.45f)) }
        val cut = run.gap()
        assertEquals(StepMark.SHORT, cut.short?.mark)
        assertTrue(cut.short!!.faded)
        assertEquals(TunerAdvice.OUT_OF_AIR, run.listening.advice)
    }

    // ---- The speed of a finished run (2026-11-14) ----

    /** One note every [every] frames: three frames lock it, [sound] more sound, silence for the rest; the step that marked the last note if one did, else the last. */
    private fun ListenRun.note(midi: Int, every: Int, sound: Int = 9): ListenRun.Step {
        val steps = List(ListenRules.LOCK_FRAMES + sound) { onFrame(pitched(midi)) } +
            List(every - ListenRules.LOCK_FRAMES - sound) { onFrame(silent) }
        return steps.lastOrNull { it.done } ?: steps.last()
    }

    private fun eight() = Array<TuneItem>(8) { q(if (it % 2 == 0) 60 else 62) }

    @Test
    fun `a run at the turtle's pace with the knob on the rabbit says slower`() {
        val run = timed(*eight(), pulse = Pulse(100))
        val steps = (0 until 8).map { run.note(if (it % 2 == 0) 60 else 62, every = 22) } // 939 ms a note: 64 a minute
        val pace = Pace(bpm = 64, pairs = 7, knobBpm = 100, speaks = true)
        assertEquals(pace, steps.last { it.done }.pace)
        assertEquals(TempoBand.TURTLE, pace.child)
        assertEquals(TempoBand.RABBIT, pace.knob)
        assertTrue(pace.slower)
        repeat(40) { run.onFrame(silent) }
        assertTrue(run.isOver)
        assertEquals(pace, run.listening.pace)
    }

    @Test
    fun `at the knob's own animal nothing is said`() {
        val run = timed(*eight(), pulse = Pulse(60))
        val steps = (0 until 8).map { run.note(if (it % 2 == 0) 60 else 62, every = 22) }
        assertEquals(Pace(64, 7, 60, speaks = false), steps.last { it.done }.pace)
        repeat(40) { run.onFrame(silent) }
        assertNull(run.listening.pace)
    }

    @Test
    fun `another animal within 15 percent says nothing`() {
        val run = timed(*eight(), pulse = Pulse(80)) // the duck from 80; 18 frames a note is 78 a minute, the turtle
        val steps = (0 until 8).map { run.note(if (it % 2 == 0) 60 else 62, every = 18, sound = 6) } // nine frames, 384 of the 375 ms a quarter needs at 80
        assertEquals(Pace(78, 7, 80, speaks = false), steps.last { it.done }.pace)
    }

    @Test
    fun `fewer than six pairs say nothing`() {
        val six = Array<TuneItem>(6) { q(if (it % 2 == 0) 60 else 62) }
        val run = timed(*six, pulse = Pulse(100))
        val steps = (0 until 6).map { run.note(if (it % 2 == 0) 60 else 62, every = 22) }
        assertEquals(Pace(64, 5, 100, speaks = false), steps.last { it.done }.pace)
    }

    @Test
    fun `a pair with a wrong note before its second note is left out`() {
        val run = timed(*eight(), pulse = Pulse(100))
        repeat(3) { run.note(if (it % 2 == 0) 60 else 62, every = 22) }
        run.begin(65)
        run.stop() // a wrong note for the fourth
        val steps = (3 until 8).map { run.note(if (it % 2 == 0) 60 else 62, every = 22) }
        assertEquals(6, steps.last { it.done }.pace?.pairs)
    }

    @Test
    fun `a rest between two notes counts as written`() {
        val run = timed(q(60), TuneRest(QUARTER), q(62), pulse = Pulse(100))
        run.note(60, every = 44) // two written beats to the next head
        val last = run.note(62, every = 22)
        assertEquals(64, last.pace?.bpm)
        assertEquals(1, last.pace?.pairs)
    }

    @Test
    fun `a run stopped before its end has no pace`() {
        val run = timed(*eight(), pulse = Pulse(100))
        val steps = (0 until 7).map { run.note(if (it % 2 == 0) 60 else 62, every = 22) }
        assertTrue(steps.none { it.pace != null })
        assertNull(run.listening.pace)
    }

    // ---- The final review's findings (2026-11-14) ----

    @Test
    fun `in 6 8 a run played in time says nothing, its speed counted in the knob's clicks`() {
        val run = timed(*eight(), pulse = Pulse(60, TimeSignature.SIX_EIGHT))
        // A quarter is two clicks of 1000 ms: one every 47 frames (2005 ms) is in time.
        val steps = (0 until 8).map { run.note(if (it % 2 == 0) 60 else 62, every = 47, sound = 21) }
        assertEquals(Pace(60, 7, 60, speaks = false), steps.last { it.done }.pace)
    }

    @Test
    fun `in 6 8 a run much faster than the knob says faster`() {
        val run = timed(*eight(), pulse = Pulse(120, TimeSignature.SIX_EIGHT))
        // A quarter every 14 frames (597 ms) is a click every 299 ms: 201 a minute, the leopard against the rabbit.
        val steps = (0 until 8).map { run.note(if (it % 2 == 0) 60 else 62, every = 14, sound = 9) }
        val pace = steps.last { it.done }.pace!!
        assertEquals(Pace(201, 7, 120, speaks = true), pace)
        assertFalse(pace.slower)
    }

    @Test
    fun `in 2 2 a run played in time says nothing`() {
        val run = timed(*eight(), pulse = Pulse(60, TimeSignature.TWO_TWO))
        // A click is a half note: a quarter every 12 frames (512 ms) is a click every 1024 ms, 59 a minute.
        val steps = (0 until 8).map { run.note(if (it % 2 == 0) 60 else 62, every = 12, sound = 7) }
        assertEquals(Pace(59, 7, 60, speaks = false), steps.last { it.done }.pace)
    }

    @Test
    fun `an octave pop of a note blown high goes on before a written note a semitone up too`() {
        val run = timed(h(71), q(72))
        List(ListenRules.LOCK_FRAMES) { run.onFrame(pitched(71, cents = 60.0)) }
        val popped = List(6) { run.onFrame(pitched(83, cents = 60.0)) }
        assertTrue(popped.none { it.short != null || it.heard?.verdict == Verdict.RIGHT })
        assertEquals(Walk(0, 0, Walk.Phase.SOUNDING), run.listening.walk)
    }

    /** A breath dip: one frame whose quietest stretch falls far under the note, the tone back after it. */
    private fun dip(midi: Int, cents: Double) = pitched(midi, cents = cents, level = 0.65f, minLevel = 0.2f)

    @Test
    fun `a breath dip in a note blown high is the note going on, before a written note a semitone up too`() {
        val run = timed(h(71), q(72))
        List(ListenRules.LOCK_FRAMES + 5) { run.onFrame(pitched(71, cents = 60.0)) }
        val steps = listOf(run.onFrame(dip(71, 60.0))) + List(8) { run.onFrame(pitched(71, cents = 60.0)) }
        assertTrue("the dip started the note again", steps.any { it.row.onset?.fresh == true })
        assertTrue(steps.none { it.short != null || it.heard?.verdict == Verdict.RIGHT })
        assertEquals(0, run.listening.walk?.index)
    }

    @Test
    fun `the tail of an earned note blown high, dipping, is not the note written a semitone up`() {
        val run = timed(q(71), q(72), q(69))
        List(ListenRules.LOCK_FRAMES + after(500.0) + 4) { run.onFrame(pitched(71, cents = 60.0)) }
        assertEquals(mapOf(0 to StepMark.HIGH), run.marks)
        val steps = listOf(run.onFrame(dip(71, 60.0))) + List(8) { run.onFrame(pitched(71, cents = 60.0)) }
        assertTrue("the dip started the note again", steps.any { it.row.onset?.fresh == true })
        assertTrue(steps.none { it.heard?.verdict == Verdict.RIGHT })
        assertEquals(mapOf(0 to StepMark.HIGH), run.marks)
        run.stop()
        assertEquals("a real c after a gap is the c", Verdict.RIGHT, run.begin(72).heard?.verdict)
    }

    @Test
    fun `the tail of an earned note a little flat, dipping, is not the note written a semitone under`() {
        val run = timed(q(72), q(71), q(69))
        List(ListenRules.LOCK_FRAMES + after(500.0) + 4) { run.onFrame(pitched(72, cents = -35.0)) }
        assertEquals(mapOf(0 to StepMark.LOW), run.marks)
        val steps = listOf(run.onFrame(dip(72, -35.0))) + List(8) { run.onFrame(pitched(72, cents = -35.0)) }
        assertTrue("the dip started the note again", steps.any { it.row.onset?.fresh == true })
        assertTrue(steps.none { it.heard?.verdict == Verdict.RIGHT })
        assertEquals(mapOf(0 to StepMark.LOW), run.marks)
    }

    @Test
    fun `a note blown high after a note of the letter it sounds as still shows its own bubble`() {
        val run = timed(q(72), h(71))
        run.begin(72)
        run.hold(72, after(500.0))
        run.stop()
        List(ListenRules.LOCK_FRAMES) { run.onFrame(pitched(71, cents = 60.0)) }
        assertEquals(Walk.Phase.SOUNDING, run.listening.walk?.phase)
        assertEquals("the written b, not the dots", 71, run.listening.heardNow)
    }

    @Test
    fun `a rest after a note let go too early is walked after its written time`() {
        val run = timed(h(60), TuneRest(QUARTER), q(62))
        run.begin(60)
        run.hold(60, after(500.0))
        run.gap()
        repeat(framesFor(2000.0) - framesFor(500.0) - NoteOnsetTracker.ONSET_GAP_FRAMES) { run.onFrame(silent) }
        assertEquals("the half note's written time is over: the rest is walked", Walk(1, 0, Walk.Phase.REST), run.listening.walk)
        repeat(framesFor(1000.0)) { run.onFrame(silent) }
        assertNull("then the animal waits at the next note", run.listening.walk)
        assertEquals(2, run.listening.index)
    }

    // The room to run over (2026-11-16), one set of rules since the levels went (2026-11-17).

    @Test
    fun `the default rules are the numbers of before plus the room to run over`() {
        val rules = ListenRun.Rules()
        assertEquals(25.0, rules.tuneCents, 0.0)
        assertEquals(1.5, rules.lengthSlack, 0.0)
        assertEquals(1.5, rules.longSlack, 0.0)
    }

    @Test
    fun `the room to run over is written down before the slur rules and can be moved`() {
        assertEquals("longSlack" to "1.5", ListenRun.Rules().fields().dropLast(2).last())
        assertEquals(0.5, ListenRun.Rules().with("longSlack", 0.5).longSlack, 0.0)
        assertEquals(Double.POSITIVE_INFINITY, ListenRun.Rules.PITCH_ONLY.longSlack, 0.0)
    }

    // A note held too long (2026-11-16): three quarters of a beat over.

    /** [ms] of [midi] sounding, [cents] off its pitch; every frame's step. */
    private fun ListenRun.sound(midi: Int, ms: Double, cents: Double = 0.0): List<ListenRun.Step> =
        List(framesFor(ms)) { onFrame(pitched(midi, cents)) }

    private fun ListenRun.quiet(ms: Double): List<ListenRun.Step> = List(framesFor(ms)) { onFrame(silent) }

    /** A run under the app's rules, [ListenRun.Rules] at their defaults (Practice's until the levels went, 2026-11-17). */
    private fun practice(vararg tune: TuneItem, pulse: Pulse = Pulse(60)) = timed(*tune, pulse = pulse, run = ListenRun.Rules())

    @Test
    fun `a quarter note held a beat over is too long, once, and says so`() {
        val run = practice(q(67), q(69))
        val steps = run.sound(67, 2000.0)
        val changed = steps.mapNotNull { it.changed }
        assertEquals(1, changed.size)
        assertEquals(0, changed[0].index)
        assertEquals(StepMark.LONG, changed[0].mark)
        assertEquals(1000L, changed[0].writtenMs)
        assertTrue("${changed[0].soundedMs}", changed[0].soundedMs in 1750L..1850L) // a quarter at 60 is 1000 ms, and three quarters of a beat more
        assertEquals(MarkChange(0, StepMark.LONG), steps.single { it.changed != null }.row.changed)
        assertEquals(StepMark.LONG, run.marks[0])
        assertEquals(StepMark.LONG, run.listening.remark)
        assertEquals(TunerAdvice.TOO_LONG, run.listening.advice)
    }

    @Test
    fun `six tenths of a beat over is not too long`() {
        val practice = practice(q(67), q(69))
        practice.sound(67, 1600.0)
        practice.quiet(300.0)
        assertEquals(StepMark.FIRST_TRY, practice.marks[0])
    }

    @Test
    fun `the too long line stands until the next note is judged`() {
        val run = practice(q(67), q(69))
        run.sound(67, 2000.0)
        run.quiet(200.0)
        assertEquals(TunerAdvice.TOO_LONG, run.listening.advice)
        run.sound(69, 300.0)
        assertNull(run.listening.remark)
        assertEquals(TunerAdvice.NONE, run.listening.advice)
    }

    @Test
    fun `the last note is never too long`() {
        val run = practice(q(67))
        run.sound(67, 3000.0)
        assertEquals(StepMark.FIRST_TRY, run.marks[0])
    }

    @Test
    fun `too long wins over the arrow`() {
        val run = practice(q(67), q(69))
        run.sound(67, 2000.0, cents = 40.0)
        assertEquals(StepMark.LONG, run.marks[0])
    }

    @Test
    fun `a note that runs straight into the next one is not too long`() {
        val run = practice(q(67), q(69), q(71))
        run.sound(67, 1000.0)
        run.sound(69, 1000.0)
        assertEquals(StepMark.FIRST_TRY, run.marks[0])
        assertEquals(StepMark.FIRST_TRY, run.marks[1])
    }

    @Test
    fun `sound the tracker only holds does not make a note too long`() {
        val run = practice(q(67), q(69))
        run.sound(67, 1000.0)
        val held = List(20) { run.onFrame(pitched(67, probability = 0.5)) } // loud, no clear pitch: held, not shown
        assertTrue(held.none { it.changed != null })
        assertEquals(StepMark.FIRST_TRY, run.marks[0])
    }

    @Test
    fun `a written repeat held on keeps its line and the held note is too long`() {
        val run = practice(q(67), q(67), q(69))
        run.sound(67, 2600.0)
        assertEquals(StepMark.LONG, run.marks[0])
        assertEquals(TunerAdvice.PLAY_AGAIN, run.listening.advice)
    }

    @Test
    fun `a note that needs only three seconds is too long only past its written length`() {
        val run = practice(TuneNote(67, WHOLE), q(69), pulse = Pulse(40)) // 6000 ms written, earned at 3000
        run.sound(67, 6500.0)
        assertEquals(StepMark.FIRST_TRY, run.marks[0])
    }

    @Test
    fun `a breath dip inside a long note does not start its time again`() {
        val run = practice(q(67), q(69))
        run.sound(67, 900.0)
        val dipped = run.onFrame(dip(67, 0.0))
        val after = run.sound(67, 1100.0)
        assertTrue("the dip started the note again", (listOf(dipped) + after).any { it.row.onset?.fresh == true })
        assertEquals(StepMark.LONG, run.marks[0])
    }

    // A mark is final once the note is long enough (Exam's whole-note mark went on 2026-11-17).

    @Test
    fun `the mark does not follow the note once it is long enough`() {
        val run = practice(q(67), q(69))
        run.sound(67, 600.0)
        run.sound(67, 800.0, cents = 30.0)
        run.quiet(200.0)
        assertEquals(StepMark.FIRST_TRY, run.marks[0])
    }

    @Test
    fun `twenty cents off is still in tune`() {
        val practice = practice(q(67), q(69))
        practice.sound(67, 1000.0, cents = 20.0)
        practice.quiet(200.0)
        assertEquals(StepMark.FIRST_TRY, practice.marks[0])
    }

    @Test
    fun `a quarter let go after three tenths of a beat is long enough`() {
        val practice = practice(q(67), q(69)) // needs a quarter of a beat, 250 ms
        practice.sound(67, 300.0)
        practice.quiet(300.0)
        assertEquals(StepMark.FIRST_TRY, practice.marks[0])
    }

    @Test
    fun `an octave pop inside a long note does not start its time again`() {
        val run = practice(q(67), q(69))
        run.sound(67, 900.0)
        val popped = run.sound(79, 200.0) + run.sound(67, 1100.0) // up an octave and back: the same note going on
        assertTrue("the pop was heard as a note", popped.any { it.row.onset?.midi == 79 })
        assertTrue(popped.none { it.heard?.verdict == Verdict.RIGHT || it.heard?.verdict == Verdict.WRONG })
        assertEquals(StepMark.LONG, run.marks[0])
    }

    // ---- A tie, or a slur to the same letter in another octave, is one note to Listen to me (2026-11-16) ----

    @Test
    fun `a tie is walked as one note across both`() {
        val run = timed(q(60), j(60), q(62))
        run.begin(60)
        run.hold(60, after(1100.0))
        assertTrue("the tie needs both its beats less the slack", run.marks.isEmpty())
        assertEquals(2, run.listening.walk?.halfBeat) // the click at 1000 ms, on the tie's second note
        run.hold(60, after(1600.0) - after(1100.0))
        assertEquals(mapOf(0 to StepMark.FIRST_TRY, 1 to StepMark.FIRST_TRY), run.marks)
        assertEquals(2, run.listening.index)
    }

    @Test
    fun `an octave slur played joined is one note and the first is never too long`() {
        val run = practice(q(60), j(72), q(62))
        run.begin(60)
        run.hold(60, after(900.0))
        run.begin(72)
        run.hold(72, after(900.0))
        run.stop()
        run.begin(62)
        assertEquals(StepMark.FIRST_TRY, run.marks[0])
        assertEquals(StepMark.FIRST_TRY, run.marks[1])
    }

    @Test
    fun `a tie that ends the tune is never too long and the run finishes`() {
        val run = practice(q(60), j(60))
        run.begin(60)
        run.hold(60, after(4000.0))
        run.stop()
        assertEquals(mapOf(0 to StepMark.FIRST_TRY, 1 to StepMark.FIRST_TRY), run.marks)
        assertTrue(run.isOver)
    }

    // ---- ✂️, a joined note started with the tongue (2026-11-16): a gap, or a stop as deep as a tongue ----

    /** A stop at the end of [from] (one frame, its quietest stretch at [minLevel]), then [to] locks: a tongue at 0.2. */
    private fun ListenRun.tongueTo(from: Int, to: Int, minLevel: Float = 0.2f) {
        onFrame(pitched(from, minLevel = minLevel))
        begin(to)
    }

    @Test
    fun `a joined note started with the tongue earns the scissors and the line`() {
        val run = timed(q(60), j(62), q(64))
        run.begin(60)
        run.hold(60, after(800.0))
        run.tongueTo(60, 62)
        assertTrue(run.listening.tongued)
        assertEquals(TunerAdvice.JOIN, run.listening.advice)
        run.hold(62, after(800.0))
        assertEquals(StepMark.TONGUED, run.marks[1])
        assertEquals(TunerAdvice.JOIN, run.listening.advice)
    }

    @Test
    fun `a joined note played joined, or after a breath-sized dip, earns its star`() {
        val slur = timed(q(60), j(62))
        slur.begin(60)
        slur.hold(60, after(800.0))
        slur.begin(62)
        slur.hold(62, after(800.0))
        assertEquals(StepMark.FIRST_TRY, slur.marks[1])
        val dip = timed(q(60), j(62))
        dip.begin(60)
        dip.hold(60, after(800.0))
        dip.tongueTo(60, 62, minLevel = 0.52f)
        assertFalse(dip.listening.tongued)
        dip.hold(62, after(800.0))
        assertEquals(StepMark.FIRST_TRY, dip.marks[1])
    }

    @Test
    fun `a joined note after a gap earns the scissors, a note not joined never does`() {
        val joined = timed(q(60), j(62))
        joined.begin(60)
        joined.hold(60, after(800.0))
        joined.stop()
        joined.begin(62)
        joined.hold(62, after(800.0))
        assertEquals(StepMark.TONGUED, joined.marks[1])
        val apart = timed(q(60), q(62))
        apart.begin(60)
        apart.hold(60, after(800.0))
        apart.tongueTo(60, 62)
        apart.hold(62, after(800.0))
        assertEquals(StepMark.FIRST_TRY, apart.marks[1])
    }

    @Test
    fun `no scissors after a wrong note, nor with the rule off`() {
        val wrong = timed(q(60), j(62))
        wrong.begin(60)
        wrong.hold(60, after(800.0))
        wrong.stop()
        wrong.begin(65)
        wrong.stop()
        wrong.begin(62)
        wrong.hold(62, after(800.0))
        assertEquals(StepMark.RETRY, wrong.marks[1])
        val off = ListenRun(listOf(q(60), j(62)), Pulse(60), run = halfBeatSlack.copy(slurCheck = false))
        off.begin(60)
        off.hold(60, after(800.0))
        off.tongueTo(60, 62)
        off.hold(62, after(800.0))
        assertEquals(StepMark.FIRST_TRY, off.marks[1])
    }

    @Test
    fun `cut short, the hourglass wins and the line still says join`() {
        val run = timed(q(60), j(62), q(64))
        run.begin(60)
        run.hold(60, after(800.0))
        run.tongueTo(60, 62)
        run.hold(62, 2)
        run.stop()
        assertEquals(StepMark.SHORT, run.marks[1])
        assertEquals(TunerAdvice.JOIN, run.listening.advice)
    }

    @Test
    fun `held too long, the scissors become the hand`() {
        val run = practice(q(60), j(62), q(64))
        run.begin(60)
        run.hold(60, after(800.0))
        run.tongueTo(60, 62)
        run.hold(62, after(2600.0))
        assertEquals(StepMark.LONG, run.marks[1])
        assertEquals(TunerAdvice.TOO_LONG, run.listening.advice)
    }

    @Test
    fun `while the tongued note sounds off its pitch the tuning line comes first`() {
        val run = timed(q(60), j(62))
        run.begin(60)
        run.hold(60, after(800.0))
        run.tongueTo(60, 62)
        run.sound(62, 300.0, cents = 40.0)
        assertEquals(TunerAdvice.TOO_HIGH, run.listening.advice)
    }

    @Test
    fun `the next note judged ends the line`() {
        val run = timed(q(60), j(62), q(64))
        run.begin(60)
        run.hold(60, after(800.0))
        run.tongueTo(60, 62)
        run.hold(62, after(800.0))
        run.stop()
        run.begin(64)
        assertFalse(run.listening.tongued)
        assertEquals(TunerAdvice.NONE, run.listening.advice)
    }

    @Test
    fun `the slur rules are named rules`() {
        val rules = ListenRun.Rules()
        assertEquals(listOf("slurCheck" to "1", "slurStopDrop" to "0.25"), rules.fields().takeLast(2))
        assertEquals(false, rules.with("slurCheck", 0.0).slurCheck)
        assertEquals(0.4, rules.with("slurStopDrop", 0.4).slurStopDrop, 0.0)
    }

    @Test
    fun `a joined note softer than the one before earns its star after a breath-sized dip, or with no dip at all`() {
        // The final review of 2026-11-16: the stop was measured against the louder note before, so a softer joined note
        // turned a breath's dip, or no dip, into a tongue's depth.
        val dip = timed(q(60), j(62))
        List(ListenRules.LOCK_FRAMES + after(800.0)) { dip.onFrame(pitched(60, level = 0.8f)) }
        dip.onFrame(pitched(60, level = 0.8f, minLevel = 0.54f))
        List(ListenRules.LOCK_FRAMES + after(800.0)) { dip.onFrame(pitched(62, level = 0.7f)) }
        assertEquals(StepMark.FIRST_TRY, dip.marks[1])
        val swell = timed(q(60), j(62))
        List(ListenRules.LOCK_FRAMES + after(800.0)) { swell.onFrame(pitched(60, level = 0.85f)) }
        listOf(0.58f, 0.66f, 0.74f).forEach { swell.onFrame(pitched(62, level = it)) }
        List(after(800.0)) { swell.onFrame(pitched(62, level = 0.74f)) }
        assertEquals(StepMark.FIRST_TRY, swell.marks[1])
        val tongue = timed(q(60), j(62))
        List(ListenRules.LOCK_FRAMES + after(800.0)) { tongue.onFrame(pitched(60, level = 0.8f)) }
        tongue.onFrame(pitched(60, level = 0.8f, minLevel = 0.2f))
        List(ListenRules.LOCK_FRAMES + after(800.0)) { tongue.onFrame(pitched(62, level = 0.6f)) }
        assertEquals("a real tongue into a softer note is still one", StepMark.TONGUED, tongue.marks[1])
    }

    // An octave slur broken by a real gap (the final review of 2026-11-16): not "too short" on two notes held their length.

    @Test
    fun `an octave slur broken by a gap after its first note's beat earns its star and the scissors`() {
        val run = practice(q(60), j(72), q(62))
        run.begin(60)
        run.hold(60, after(950.0))
        run.stop()
        assertEquals(StepMark.FIRST_TRY, run.marks[0])
        assertEquals(StepMark.TONGUED, run.marks[1])
        assertEquals(TunerAdvice.JOIN, run.listening.advice)
        run.begin(72)
        run.hold(72, after(950.0))
        assertNull("the c² after the gap is the slur's own, not a wrong note", run.listening.wrong)
        run.stop()
        run.begin(62)
        run.hold(62, after(800.0))
        assertEquals(StepMark.FIRST_TRY, run.marks[2])
    }

    @Test
    fun `an octave slur let go before its first note's beat, and a tie broken by a gap, are still too short`() {
        val early = practice(q(60), j(72), q(62))
        early.begin(60)
        early.hold(60, 1)
        early.stop()
        assertEquals(mapOf(0 to StepMark.SHORT, 1 to StepMark.SHORT), early.marks)
        val tie = practice(q(60), j(60), q(62))
        tie.begin(60)
        tie.hold(60, after(950.0))
        tie.stop()
        assertEquals("a tie is one written sound", mapOf(0 to StepMark.SHORT, 1 to StepMark.SHORT), tie.marks)
    }

    @Test
    fun `the heard note carries how deep the stop before it fell`() {
        val run = timed(q(60), j(62))
        run.begin(60)
        run.hold(60, after(800.0))
        run.onFrame(pitched(60, minLevel = 0.2f))
        val onset = List(ListenRules.LOCK_FRAMES) { run.onFrame(pitched(62)) }.last()
        assertEquals(0.5f, onset.heard?.stopDrop!!, 0.001f)
    }

    // ---- Black notes (2026-11-16): the rules judge by semitone already; these pin it ----

    @Test
    fun `a written f sharp is right when f sharp is heard, and f played for it is wrong`() {
        val right = practice(q(66))
        repeat(30) { right.onFrame(pitched(66)) }
        assertEquals(mapOf(0 to StepMark.FIRST_TRY), right.marks)
        for (cents in listOf(0.0, 40.0)) {
            val wrong = practice(q(66))
            repeat(30) { wrong.onFrame(pitched(65, cents = cents)) }
            assertTrue("F at $cents cents", wrong.marks.isEmpty())
            assertEquals("F at $cents cents", TunerAdvice.CHECK_FINGERS, wrong.listening.advice)
        }
    }

    @Test
    fun `a known limit, a forgotten sharp on a flute sixty cents sharp reads as f sharp played low`() {
        // Spec section 6: pitch alone cannot tell an F blown 60 cents sharp from an F♯ blown 40 flat. No rule is added.
        val run = practice(q(66))
        repeat(30) { run.onFrame(pitched(65, cents = 60.0)) }
        assertEquals(mapOf(0 to StepMark.LOW), run.marks)
        assertEquals(TunerAdvice.TOO_LOW, run.listening.advice)
    }

    @Test
    fun `an f a little sharp is an f with the down arrow, and a full f sharp for it is wrong`() {
        val sharp = practice(q(65))
        repeat(30) { sharp.onFrame(pitched(65, cents = 40.0)) }
        assertEquals(mapOf(0 to StepMark.HIGH), sharp.marks)
        val wrong = practice(q(65))
        repeat(30) { wrong.onFrame(pitched(66)) }
        assertTrue(wrong.marks.isEmpty())
        assertEquals(TunerAdvice.CHECK_FINGERS, wrong.listening.advice)
    }

    // ---- A slurred semitone on a flute off its pitch (2026-11-16): heard, and each note marked for how it sounded ----

    private fun ListenRun.play(midi: Int, cents: Double) = repeat(ListenRules.LOCK_FRAMES + after(800.0)) { onFrame(pitched(midi, cents = cents)) }

    @Test
    fun `g slurred to f sharp forty cents sharp earns both marks`() {
        val run = timed(q(67), j(66))
        run.play(67, 40.0)
        run.play(66, 40.0)
        assertEquals(mapOf(0 to StepMark.HIGH, 1 to StepMark.HIGH), run.marks)
    }

    @Test
    fun `f slurred to f sharp thirty-five cents flat earns both marks`() {
        val run = timed(q(65), j(66))
        run.play(65, -35.0)
        run.play(66, -35.0)
        assertEquals(mapOf(0 to StepMark.LOW, 1 to StepMark.LOW), run.marks)
    }

    @Test
    fun `c slurred to b forty cents sharp earns both marks`() {
        val run = timed(q(72), j(71))
        run.play(72, 40.0)
        run.play(71, 40.0)
        assertEquals(mapOf(0 to StepMark.HIGH, 1 to StepMark.HIGH), run.marks)
    }

    @Test
    fun `with the rule off the second note is never heard`() {
        val run = ListenRun(listOf(q(67), j(66)), Pulse(60), rules = NoteOnsetTracker.Rules(stepFromSounding = false), run = halfBeatSlack)
        run.play(67, 40.0)
        run.play(66, 40.0)
        assertNull(run.marks[1])
    }
}
