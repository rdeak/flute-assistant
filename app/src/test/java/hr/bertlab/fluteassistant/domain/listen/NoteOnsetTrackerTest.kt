// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.listen

import hr.bertlab.fluteassistant.domain.listen.TestFrames.pitched
import hr.bertlab.fluteassistant.domain.listen.TestFrames.silent
import hr.bertlab.fluteassistant.domain.tuner.NoteMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteOnsetTrackerTest {

    @Test
    fun `the pitch a note locked at comes with its onset`() {
        val tracker = NoteOnsetTracker()
        val results = List(ListenRules.LOCK_FRAMES) { tracker.onFrame(pitched(71, cents = 55.0)) }
        val locked = results.last()
        assertEquals(72, locked.onset?.midi)
        assertEquals(TestFrames.hz(71, cents = 55.0), locked.lockHz!!, 1e-6)
        assertTrue("no onset, no pitch passed on", results.dropLast(1).all { it.onset == null && it.lockHz == null })
        assertNull(tracker.onFrame(pitched(71, cents = 55.0)).lockHz)
    }

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
        // Playing over a room's noise: the pause stays over half the note's level,
        // so by level it is no gap, and the note would never end. The tuner's own hold count ends
        // it instead, so a repeat after a real pause is a new note however loud the room is.
        val tracker = NoteOnsetTracker()
        val note = PitchFrame(NoteMath.midiToFrequency(60), 0.97, level = 0.7f)
        val room = PitchFrame(null, 0.0, level = 0.5f)
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
        // falls back to it is a gap even where half the note's level (0.35) is under the floor (0.37).
        val tracker = NoteOnsetTracker()
        val room = PitchFrame(null, 0.0, level = 0.37f)
        val note = PitchFrame(NoteMath.midiToFrequency(60), 0.97, level = 0.7f)
        feed(tracker, List(10) { room })
        feed(tracker, List(3) { note })
        // A blink inside the note keeps its loudness, well over the floor: not a gap.
        feed(tracker, List(2) { PitchFrame(null, 0.0, level = 0.7f) })
        assertEquals(60, tracker.sounding)
        feed(tracker, List(NoteOnsetTracker.ONSET_GAP_FRAMES) { room })
        assertNull(tracker.sounding)
        assertEquals(listOf(null, null, 60), feed(tracker, List(3) { note }).onsets())
    }

    private fun note(midi: Int, level: Float, minLevel: Float = level) = pitched(midi, level = level, minLevel = minLevel)

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
            listOf(note(60, 0.5f), note(60, 0.6f), note(60, 0.7f, minLevel = 0.2f), note(60, 0.7f, minLevel = 0.2f), note(60, 0.7f), note(60, 0.7f)),
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
        assertEquals("0.45", fields["minLevel"])
        assertEquals("1", fields["stepFromSounding"]) // since 2026-11-16
        assertEquals(18, fields.size)
    }

    @Test
    fun `a rule is changed by name`() {
        assertEquals(0.08f, NoteOnsetTracker.Rules().with("stopDrop", 0.08).stopDrop)
        assertEquals(3, NoteOnsetTracker.Rules().with("restartFrames", 3.0).restartFrames)
        assertEquals(0.9, NoteOnsetTracker.Rules().with("gate", 0.9).gate, 0.0)
        assertThrows(IllegalArgumentException::class.java) { NoteOnsetTracker.Rules().with("nope", 1.0) }
    }

    // ---- Too faint to be the flute at the phone ----

    /**
     * A clear, steady pitch far under the level of a flute at the phone, a voice across the room or a television,
     * is no note: on the user's phone (2026-11-10) such sounds, never louder than 0.36, earned a star and five
     * wrong marks before anything was played, where every note of the real flute sessions was judged at 0.67 or over.
     */
    @Test
    fun `a clear pitch too faint to be the flute at the phone is not a note`() {
        val tracker = NoteOnsetTracker()
        val results = feed(tracker, List(10) { pitched(64).copy(level = 0.36f) })
        assertEquals(List(10) { FrameDecision.FAINT }, results.decisions())
        assertEquals(List<Int?>(10) { null }, results.onsets())
        assertNull(tracker.sounding)
    }

    @Test
    fun `the same pitch at the level of a flute at the phone is a note`() {
        val tracker = NoteOnsetTracker()
        val results = feed(tracker, List(3) { pitched(64).copy(level = 0.67f) })
        assertEquals(listOf(null, null, 64), results.onsets())
    }

    /**
     * A note does not end at the floor: its frames under it stop counting, as frames under the probability gate do,
     * and the note goes on until they fall well under its own level (the gap rule) or stay out too long.
     */
    @Test
    fun `a note goes on through its own frames under the floor and ends when they fall well under its level`() {
        val tracker = NoteOnsetTracker()
        feed(tracker, List(5) { pitched(64).copy(level = 0.75f) })
        val fading = feed(tracker, List(3) { pitched(64).copy(level = 0.42f) }) // under the floor, over half the note's level
        assertEquals(List(3) { FrameDecision.FAINT }, fading.decisions())
        assertEquals(64, tracker.sounding)
        feed(tracker, List(NoteOnsetTracker.ONSET_GAP_FRAMES) { pitched(64).copy(level = 0.3f) }) // under half of it: a gap
        assertNull(tracker.sounding)
    }

    @Test
    fun `the floor is a rule a replay can move`() {
        assertTrue("minLevel" in NoteOnsetTracker.Rules().fields().map { it.first })
        val none = NoteOnsetTracker(NoteOnsetTracker.Rules().with("minLevel", 0.0))
        assertEquals(listOf(null, null, 64), feed(none, List(3) { pitched(64).copy(level = 0.36f) }).onsets())
    }

    @Test
    fun `a seeded floor is the room's level from the start`() {
        // The view model's tracker keeps the room's floor from one run to the next; a replay of
        // the second run must start where the phone started, not learn the room again.
        assertEquals(0.23f, NoteOnsetTracker(floor = 0.23f).floor)
        assertNull(NoteOnsetTracker().floor)
    }

    // ---- Where a note began: the frames that locked it count (the length check, 2026-11-11) ----

    @Test
    fun `a note after silence starts with the frames that locked it`() {
        val tracker = NoteOnsetTracker()
        val onset = feed(tracker, List(ListenRules.LOCK_FRAMES) { pitched(60) }).last()
        assertEquals(60, onset.onset?.midi)
        assertEquals(ListenRules.LOCK_FRAMES - 1, onset.leadFrames)
    }

    @Test
    fun `frames of another note before the lock are not the note's start`() {
        val tracker = NoteOnsetTracker()
        val results = feed(tracker, List(2) { pitched(61) } + List(ListenRules.LOCK_FRAMES) { pitched(60) })
        assertEquals(60, results.last().onset?.midi)
        assertEquals(ListenRules.LOCK_FRAMES - 1, results.last().leadFrames)
    }

    @Test
    fun `a long wobble of other notes before the lock gives the note no head start`() {
        // Nine counted frames between e and f sharp that never agree (a voice, a slide, a warbling wrong note), with
        // no gap, then c: the c began with its own first frame. Before, all eleven frames were its lead, and a
        // quarter was earned on its onset frame (the review of 2026-11-11).
        val tracker = NoteOnsetTracker()
        val wobble = List(9) { pitched(if (it % 2 == 0) 64 else 66) }
        val results = feed(tracker, wobble + List(ListenRules.LOCK_FRAMES) { pitched(60) })
        assertTrue("the wobble never locks", results.dropLast(1).all { it.onset == null })
        assertEquals(NoteOnsetTracker.Onset(60, fresh = true), results.last().onset)
        assertEquals(ListenRules.LOCK_FRAMES - 1, results.last().leadFrames)
    }

    @Test
    fun `unsteady frames of the note itself before the lock are its start`() {
        // An attack that wavers inside c before it settles is still the child's c.
        val tracker = NoteOnsetTracker()
        val results = feed(tracker, listOf(pitched(60, cents = 40.0), pitched(60, cents = -40.0)) + List(ListenRules.LOCK_FRAMES) { pitched(60) })
        assertEquals(60, results.last().onset?.midi)
        assertEquals(ListenRules.LOCK_FRAMES + 1, results.last().leadFrames)
    }

    @Test
    fun `a step and a pop start with the frames of the new pitch before the lock`() {
        // A tongued change to another note while the last one is still held: the recorded sessions of 2026-11-05
        // show two frames of the new pitch before every such onset, the frames that locked it.
        val step = NoteOnsetTracker()
        feed(step, List(3) { pitched(60) })
        val slurred = feed(step, List(ListenRules.LOCK_FRAMES) { pitched(62) }).last()
        assertEquals(NoteOnsetTracker.Onset(62, fresh = false), slurred.onset)
        assertEquals(ListenRules.LOCK_FRAMES - 1, slurred.leadFrames)

        val pop = NoteOnsetTracker()
        feed(pop, List(3) { pitched(60) })
        val popped = feed(pop, List(ListenRules.LOCK_FRAMES) { pitched(72) }).last()
        assertEquals(NoteOnsetTracker.Onset(72, fresh = false), popped.onset)
        assertEquals(ListenRules.LOCK_FRAMES - 1, popped.leadFrames)
    }

    @Test
    fun `a tongued restart starts at its onset`() {
        val restart = NoteOnsetTracker()
        feed(restart, List(4) { note(60, 0.6f) })
        feed(restart, listOf(note(60, 0.55f, minLevel = 0.2f)))
        val again = feed(restart, listOf(note(60, 0.6f))).single()
        assertEquals(NoteOnsetTracker.Onset(60, fresh = true), again.onset)
        assertEquals("the stop's frame is not the new note's", 0, again.leadFrames)
    }

    @Test
    fun `a restart after a frame that did not count starts after it`() {
        // The same note again after one silent frame: the frames before it were the old note's.
        val tracker = NoteOnsetTracker()
        feed(tracker, List(4) { pitched(60) })
        val again = feed(tracker, listOf(silent, pitched(60))).last()
        assertEquals(NoteOnsetTracker.Onset(60, fresh = true), again.onset)
        assertEquals(0, again.leadFrames)
    }

    @Test
    fun `the lead starts again after a gap`() {
        val tracker = NoteOnsetTracker()
        feed(tracker, List(3) { pitched(60) })
        feed(tracker, List(NoteOnsetTracker.ONSET_GAP_FRAMES) { silent })
        val again = feed(tracker, List(ListenRules.LOCK_FRAMES) { pitched(60) }).last()
        assertEquals(NoteOnsetTracker.Onset(60, fresh = true), again.onset)
        assertEquals(ListenRules.LOCK_FRAMES - 1, again.leadFrames)
    }

    @Test
    fun `a gate blink among the locking frames is part of the note's start`() {
        // The note began on its first counted frame; a breathy frame under the gate in between is still the note.
        val tracker = NoteOnsetTracker()
        val results = feed(tracker, listOf(pitched(60), pitched(60, probability = 0.5), pitched(60), pitched(60)))
        assertEquals(60, results.last().onset?.midi)
        assertEquals(3, results.last().leadFrames)
    }

    @Test
    fun `an attack that scoops into the note from over the halfway point is the note's start`() {
        // The recorded session 2026-11-05_17-04-38, frame 199: a tongued g begins 55 cents sharp, nearer g sharp,
        // and settles within three frames. That frame is the child's g; a frame a semitone or more away is not.
        val tracker = NoteOnsetTracker()
        val results = feed(tracker, listOf(pitched(67, cents = 55.0), pitched(67, cents = 36.0), pitched(67, cents = 24.0), pitched(67, cents = 17.0)))
        assertEquals(NoteOnsetTracker.Onset(67, fresh = true), results.last().onset)
        assertEquals(3, results.last().leadFrames)
    }

    @Test
    fun `a slide down to the note counts only its last frames`() {
        // e sliding to c in steps the tracker never locks: only the frames near c are c's.
        val tracker = NoteOnsetTracker()
        val slide = listOf(400.0, 320.0, 240.0, 160.0, 80.0, 40.0).map { pitched(60, cents = it) }
        val results = feed(tracker, slide + List(ListenRules.LOCK_FRAMES) { pitched(60) })
        assertEquals(60, results.last().onset?.midi)
        assertEquals("the frame 40 cents off and the two that locked it", ListenRules.LOCK_FRAMES, results.last().leadFrames)
    }

    @Test
    fun `a held note played sharp gives the semitone slurred to after it no head start`() {
        // b held 35 cents sharp, then a slur to c 5 cents flat: the two lie 60 cents apart, inside the near band, but
        // the b sounded as b and its frames are b's. Before, all twenty were the c's lead, and a whole note was earned
        // on its onset frame.
        val tracker = NoteOnsetTracker()
        feed(tracker, List(20) { pitched(71, cents = 35.0) })
        val slurred = feed(tracker, List(ListenRules.LOCK_FRAMES) { pitched(72, cents = -5.0) }).last()
        assertEquals(NoteOnsetTracker.Onset(72, fresh = false), slurred.onset)
        assertEquals(ListenRules.LOCK_FRAMES - 1, slurred.leadFrames)
    }

    // ---- How deep the stop before a restart fell (2026-11-16): the flute's slur check tells a tongue from a breath dip ----

    @Test
    fun `a tongued change to another note says how deep its stop fell`() {
        val tracker = NoteOnsetTracker()
        feed(tracker, List(5) { pitched(60, level = 0.7f) })
        feed(tracker, listOf(pitched(60, level = 0.7f, minLevel = 0.2f)))
        val result = feed(tracker, List(ListenRules.LOCK_FRAMES) { pitched(62, level = 0.7f) }).last()
        assertEquals(NoteOnsetTracker.Onset(62, fresh = true), result.onset)
        assertEquals(0.5f, result.stopDrop!!, 0.001f)
    }

    @Test
    fun `a slur and a start after a gap have no stop depth`() {
        val slur = NoteOnsetTracker()
        feed(slur, List(5) { pitched(60) })
        val slurred = feed(slur, List(ListenRules.LOCK_FRAMES) { pitched(62) }).last()
        assertEquals(NoteOnsetTracker.Onset(62, fresh = false), slurred.onset)
        assertNull(slurred.stopDrop)
        val gap = NoteOnsetTracker()
        feed(gap, List(5) { pitched(60) })
        feed(gap, List(NoteOnsetTracker.ONSET_GAP_FRAMES) { silent })
        val again = feed(gap, List(ListenRules.LOCK_FRAMES) { pitched(62) }).last()
        assertEquals(NoteOnsetTracker.Onset(62, fresh = true), again.onset)
        assertNull(again.stopDrop)
    }

    @Test
    fun `a shallow dip at a change is a shallow stop`() {
        val tracker = NoteOnsetTracker()
        feed(tracker, List(5) { pitched(60, level = 0.7f) })
        feed(tracker, listOf(pitched(60, level = 0.7f, minLevel = 0.52f)))
        val result = feed(tracker, List(ListenRules.LOCK_FRAMES) { pitched(62, level = 0.7f) }).last()
        assertEquals(NoteOnsetTracker.Onset(62, fresh = true), result.onset)
        assertEquals(0.18f, result.stopDrop!!, 0.001f)
    }

    @Test
    fun `frames with no onset carry no stop depth`() {
        val tracker = NoteOnsetTracker()
        val results = feed(tracker, List(5) { pitched(60, level = 0.7f) } + listOf(pitched(60, level = 0.7f, minLevel = 0.2f)))
        assertTrue(results.all { it.stopDrop == null })
    }

    @Test
    fun `a softer note after a breath-sized dip measures the dip, not the change of loudness`() {
        // The final review of 2026-11-16: c at 0.80, a dip to 0.54, d at 0.70. Against c alone the stop is 0.26 deep,
        // a tongue's depth; the dip under the quieter side, d, is 0.16, a breath's.
        val tracker = NoteOnsetTracker()
        feed(tracker, List(5) { pitched(60, level = 0.8f) })
        feed(tracker, listOf(pitched(60, level = 0.8f, minLevel = 0.54f)))
        val result = feed(tracker, List(ListenRules.LOCK_FRAMES) { pitched(62, level = 0.7f) }).last()
        assertEquals(NoteOnsetTracker.Onset(62, fresh = true), result.onset)
        assertEquals(0.16f, result.stopDrop!!, 0.001f)
    }

    // ---- A slurred semitone on a flute off its pitch (2026-11-16, sharps and flats): a step measured from where the note sounded ----

    @Test
    fun `a slur to the next semitone on a flute forty cents sharp is a step`() {
        val tracker = NoteOnsetTracker()
        feed(tracker, List(5) { pitched(67, cents = 40.0) })
        val step = feed(tracker, List(ListenRules.LOCK_FRAMES) { pitched(66, cents = 40.0) }).last()
        assertEquals(NoteOnsetTracker.Onset(66, fresh = false), step.onset)
    }

    @Test
    fun `and up a semitone on a flute thirty-five cents flat`() {
        val tracker = NoteOnsetTracker()
        feed(tracker, List(5) { pitched(65, cents = -35.0) })
        val step = feed(tracker, List(ListenRules.LOCK_FRAMES) { pitched(66, cents = -35.0) }).last()
        assertEquals(NoteOnsetTracker.Onset(66, fresh = false), step.onset)
    }

    @Test
    fun `with the rule off it is a drift that begins no note`() {
        val tracker = NoteOnsetTracker(NoteOnsetTracker.Rules(stepFromSounding = false))
        feed(tracker, List(5) { pitched(67, cents = 40.0) })
        val after = feed(tracker, List(20) { pitched(66, cents = 40.0) })
        assertTrue(after.all { it.onset == null })
        assertEquals(66, tracker.sounding)
    }

    @Test
    fun `a long note sagging slowly past the halfway point is still one note`() {
        val tracker = NoteOnsetTracker()
        val frames = feed(tracker, List(25) { pitched(67, cents = -3.0 * it) }) // to 72 cents under
        assertEquals(1, frames.count { it.onset != null })
    }
}
