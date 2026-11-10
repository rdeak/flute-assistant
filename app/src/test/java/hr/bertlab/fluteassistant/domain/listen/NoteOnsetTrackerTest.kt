// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.listen

import hr.bertlab.fluteassistant.domain.tuner.NoteMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class NoteOnsetTrackerTest {

    private fun pitched(midi: Int, cents: Double = 0.0, probability: Double = 0.97) =
        PitchFrame(NoteMath.midiToFrequency(midi) * 2.0.pow(cents / 1200.0), probability, level = 0.4f)

    private val silent = PitchFrame(null, 0.0, level = 0f)

    private fun feed(tracker: NoteOnsetTracker, frames: List<PitchFrame>): List<NoteOnsetTracker.Result> = frames.map(tracker::onFrame)

    private fun List<NoteOnsetTracker.Result>.onsets() = map { it.onset?.midi }
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
    fun `an onset after a gap is a fresh start, one by a step without a gap is not`() {
        // The flute's check needs the difference: a written repeat is played only by stopping and
        // starting again, while an octave pop inside one note is a step and must not count as it.
        val tracker = NoteOnsetTracker()
        assertEquals(NoteOnsetTracker.Onset(60, fresh = true), feed(tracker, List(3) { pitched(60) }).last().onset)
        assertEquals(NoteOnsetTracker.Onset(72, fresh = false), feed(tracker, List(3) { pitched(72) }).last().onset)
        feed(tracker, List(NoteOnsetTracker.ONSET_GAP_FRAMES) { silent })
        assertEquals(NoteOnsetTracker.Onset(72, fresh = true), feed(tracker, List(3) { pitched(72) }).last().onset)
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
        val drifted = feed(tracker, List(2 + ListenRules.SWITCH_FRAMES) { pitched(60, cents = 60.0) })
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
    fun `a pause the level never falls under still ends the note after HOLD_FRAMES, so its repeat is new`() {
        // Soft or distant playing over a room's noise: the pause stays over half the note's level,
        // so by level it is no gap, and the note would never end. The tuner's own hold count ends
        // it instead, so a repeat after a real pause is a new note however quiet the child plays.
        val tracker = NoteOnsetTracker()
        val note = PitchFrame(NoteMath.midiToFrequency(60), 0.97, level = 0.4f)
        val room = PitchFrame(null, 0.0, level = 0.3f)
        feed(tracker, List(3) { note })
        feed(tracker, List(ListenRules.HOLD_FRAMES - 1) { room })
        assertEquals("not yet: a blink of this length is still inside the note", 60, tracker.sounding)
        feed(tracker, listOf(room))
        assertNull(tracker.sounding)
        assertEquals(listOf(null, null, 60), feed(tracker, List(3) { note }).onsets())
    }

    @Test
    fun `a pause at the room's floor ends a note that stays over half its level`() {
        // The room is heard before the child plays, so the tracker knows its floor; a pause that
        // falls back to it is a gap even for a soft note, where half the level is under the floor.
        val tracker = NoteOnsetTracker()
        val room = PitchFrame(null, 0.0, level = 0.23f)
        val note = PitchFrame(NoteMath.midiToFrequency(60), 0.97, level = 0.4f)
        feed(tracker, List(10) { room })
        feed(tracker, List(3) { note })
        // A blink inside the note keeps its loudness, well over the floor: not a gap.
        feed(tracker, List(2) { PitchFrame(null, 0.0, level = 0.4f) })
        assertEquals(60, tracker.sounding)
        feed(tracker, List(NoteOnsetTracker.ONSET_GAP_FRAMES) { room })
        assertNull(tracker.sounding)
        assertEquals(listOf(null, null, 60), feed(tracker, List(3) { note }).onsets())
    }

    private fun note(midi: Int, level: Float, minLevel: Float = level) =
        PitchFrame(NoteMath.midiToFrequency(midi), 0.97, level, minLevel)

    @Test
    fun `a tongued stop inside the newest hop starts the same note again`() {
        // "ta ta": the tongue stops the air for 50 ms, too short for the window's level or the
        // pitch gate to notice, but the quietest stretch of that hop falls to the room's floor;
        // once the tone is back at its loudness, the note has started again.
        val tracker = NoteOnsetTracker()
        feed(tracker, List(4) { note(60, 0.6f) })
        val stop = feed(tracker, listOf(note(60, 0.55f, minLevel = 0.2f)))
        assertEquals(listOf(null), stop.onsets())
        val again = feed(tracker, listOf(note(60, 0.6f)))
        assertEquals(listOf(NoteOnsetTracker.Onset(60, fresh = true)), again.map { it.onset })
        assertEquals(60, tracker.sounding)
        assertTrue("the note going on is not new again", feed(tracker, List(3) { note(60, 0.6f) }).all { it.onset == null })
    }

    @Test
    fun `a wobble that never falls far is no new note`() {
        val tracker = NoteOnsetTracker()
        feed(tracker, List(4) { note(60, 0.6f) })
        val wobble = feed(tracker, listOf(note(60, 0.58f, minLevel = 0.5f), note(60, 0.6f), note(60, 0.6f)))
        assertTrue(wobble.all { it.onset == null })
    }

    @Test
    fun `a stop in the first frames of a note is its attack, not a note started again`() {
        // The hop of the onset frame and of the one after it still hold the swell from nothing.
        val tracker = NoteOnsetTracker()
        val results = feed(
            tracker,
            listOf(note(60, 0.4f), note(60, 0.5f), note(60, 0.6f, minLevel = 0.2f), note(60, 0.6f, minLevel = 0.2f), note(60, 0.6f), note(60, 0.6f)),
        )
        assertEquals(listOf(null, null, 60, null, null, null), results.onsets())
    }

    @Test
    fun `a stop followed by silence is the end of the note, not a note started again`() {
        val tracker = NoteOnsetTracker()
        feed(tracker, List(4) { note(60, 0.6f) })
        feed(tracker, listOf(note(60, 0.55f, minLevel = 0.2f)))
        val gone = feed(tracker, List(NoteOnsetTracker.ONSET_GAP_FRAMES) { PitchFrame(null, 0.0, 0.1f) })
        assertTrue(gone.all { it.onset == null })
        assertNull(tracker.sounding)
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

    // --- rules: the thresholds as a value, so a replay can try another ---

    @Test
    fun `a smaller stop drop hears a shallower tongued stop`() {
        // A dip of 0.09 under the note's level: too shallow for STOP_DROP (0.12), a stop for 0.08.
        fun run(tracker: NoteOnsetTracker): List<Int?> {
            feed(tracker, List(4) { note(60, 0.6f) })
            return feed(tracker, listOf(note(60, 0.58f, minLevel = 0.51f), note(60, 0.65f))).onsets()
        }
        assertEquals(listOf(null, null), run(NoteOnsetTracker()))
        assertEquals(listOf(null, 60), run(NoteOnsetTracker(rules = NoteOnsetTracker.Rules(stopDrop = 0.08f))))
    }

    @Test
    fun `rules name every threshold with its value`() {
        val fields = NoteOnsetTracker.Rules().fields().toMap()
        assertEquals("0.85", fields["gate"])
        assertEquals("0.002", fields["floorRise"])
        assertEquals("0.12", fields["stopDrop"])
        assertEquals("70.0", fields["nearCents"])
        assertEquals(16, fields.size)
    }

    @Test
    fun `a rule is changed by name`() {
        assertEquals(0.08f, NoteOnsetTracker.Rules().with("stopDrop", 0.08).stopDrop)
        assertEquals(3, NoteOnsetTracker.Rules().with("restartFrames", 3.0).restartFrames)
        assertEquals(0.9, NoteOnsetTracker.Rules().with("gate", 0.9).gate, 0.0)
        assertThrows(IllegalArgumentException::class.java) { NoteOnsetTracker.Rules().with("nope", 1.0) }
    }

    @Test
    fun `a seeded floor is the room's level from the start`() {
        // The view model's tracker keeps the room's floor from one run to the next; a replay of
        // the second run must start where the phone started, not learn the room again.
        assertEquals(0.23f, NoteOnsetTracker(floor = 0.23f).floor)
        assertNull(NoteOnsetTracker().floor)
    }
}
