// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.flute

import hr.bertlab.fluteassistant.domain.flute.NoteLength.EIGHTH
import hr.bertlab.fluteassistant.domain.flute.NoteLength.HALF
import hr.bertlab.fluteassistant.domain.flute.NoteLength.QUARTER
import hr.bertlab.fluteassistant.domain.flute.NoteLength.WHOLE
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
import kotlin.math.ceil
import kotlin.math.pow

/** A "Your turn" fed hand-made frames, no microphone and no coroutines; real sound is `ListenPathTest`'s. */
class ListenRunTest {

    private fun q(midi: Int) = TuneNote(midi, QUARTER)
    private fun h(midi: Int) = TuneNote(midi, HALF)
    /** A frame of a note at a real flute's level at the phone (the recorded sessions: 0.67 and over), clear of the tracker's floor. */
    private fun pitched(midi: Int, cents: Double = 0.0, probability: Double = 0.97, hopMs: Double = PitchFrame.DEFAULT_HOP_MS) =
        PitchFrame(NoteMath.midiToFrequency(midi) * 2.0.pow(cents / 1200.0), probability, level = 0.7f, hopMs = hopMs)

    /** The rule of before 2026-11-11, pitch only: every note is long enough at its onset. */
    private fun pitchOnly(tune: List<TuneItem>) = ListenRun(tune, Pulse(60), lengthSlack = ListenRun.LENGTH_OFF)

    /** The length rule at 60 a minute in 4/4: a quarter note lasts 1000 ms and is long enough at 500. */
    private fun timed(vararg tune: TuneItem, pulse: Pulse = Pulse(60)) = ListenRun(tune.toList(), pulse)

    /**
     * Frames after the onset frame by which a note begun with [begin] has sounded [ms]: by its onset
     * [begin]'s note has sounded [ListenRules.LOCK_FRAMES] whole frames (the onset's and the ones that
     * locked it).
     */
    private fun after(ms: Double, hopMs: Double = PitchFrame.DEFAULT_HOP_MS) =
        ceil(ms / hopMs).toInt() - ListenRules.LOCK_FRAMES
    private val silent = PitchFrame(null, 0.0, level = 0f)

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
        assertEquals(Listening(index = 1), run.listening)
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

        val before = ListenRun(listOf(TuneNote(60, NoteLength.QUARTER), TuneNote(62, NoteLength.QUARTER)), Pulse(60), NoteOnsetTracker.Rules(minLevel = 0f), lengthSlack = ListenRun.LENGTH_OFF)
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
    fun `a quarter is earned after half a beat, not before`() {
        val run = timed(q(60), q(62))
        val onset = run.begin(60)
        assertTrue("the animal hops as the note begins", onset.hop)
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
    fun `its own note started again cuts it short and walks it again from the head`() {
        val run = timed(h(60), q(62))
        run.begin(60)
        run.hold(60, after(1200.0))
        run.onFrame(PitchFrame(NoteMath.midiToFrequency(60), 0.97, level = 0.65f, minLevel = 0.2f)) // the tongue stops the air
        val again = run.onFrame(pitched(60))
        assertEquals(0, again.short?.index)
        assertTrue(again.hop)
        assertEquals(Walk(0, 0, Walk.Phase.SOUNDING), run.listening.walk)
        // Started again from a tongued stop, the note's start is the onset itself (no frames locked it).
        run.hold(60, after(1500.0) + ListenRules.LOCK_FRAMES - 1)
        assertEquals(mapOf(0 to StepMark.RETRY), run.marks)
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
        run.begin(64)
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
        val hop = 2048 * 1000.0 / 44_100
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
    fun `a rest is passed over`() {
        val run = timed(q(60), TuneRest(QUARTER), q(62))
        run.begin(60)
        run.hold(60, after(500.0))
        assertEquals(2, run.listening.index)
        assertEquals(0, run.listening.walk?.index)
        run.hold(60, after(1000.0) - after(500.0))
        assertNull(run.listening.walk)
        assertEquals("the animal waits on the note after the rest", 2, run.listening.index)
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
        val run = ListenRun(listOf(TuneNote(60, HALF), q(62)), Pulse(60), maxNeedMs = 1000.0)
        run.begin(60)
        val steps = List(after(1000.0)) { run.onFrame(pitched(60)) }
        assertEquals(1000L, steps.last().held?.needMs)
    }
}
