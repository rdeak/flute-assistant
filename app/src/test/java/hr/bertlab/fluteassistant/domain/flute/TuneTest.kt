// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.flute

import hr.bertlab.fluteassistant.domain.flute.NoteLength.DOTTED_HALF
import hr.bertlab.fluteassistant.domain.flute.NoteLength.EIGHTH
import hr.bertlab.fluteassistant.domain.flute.NoteLength.HALF
import hr.bertlab.fluteassistant.domain.flute.NoteLength.QUARTER
import hr.bertlab.fluteassistant.domain.flute.NoteLength.WHOLE
import hr.bertlab.fluteassistant.domain.model.TimeSignature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TuneTest {

    @Test
    fun `a hold makes the nearest length, split halfway between two`() {
        assertEquals(EIGHTH, NoteLength.ofBeats(0.0))
        assertEquals(EIGHTH, NoteLength.ofBeats(0.74))
        assertEquals(QUARTER, NoteLength.ofBeats(0.75))
        assertEquals(QUARTER, NoteLength.ofBeats(1.49))
        assertEquals(HALF, NoteLength.ofBeats(1.5))
        assertEquals(DOTTED_HALF, NoteLength.ofBeats(2.5))
        assertEquals(WHOLE, NoteLength.ofBeats(3.5))
        assertEquals(WHOLE, NoteLength.ofBeats(12.0))
    }

    @Test
    fun `lengths follow the tempo`() {
        val pulse = Pulse(120)
        assertEquals(500.0, pulse.quarterMs, 0.001)
        assertEquals(EIGHTH, Tune.lengthOf(374, pulse))
        assertEquals(QUARTER, Tune.lengthOf(375, pulse))
        assertEquals(QUARTER, Tune.lengthOf(749, pulse))
        assertEquals(HALF, Tune.lengthOf(750, pulse))
        assertEquals(EIGHTH, Tune.lengthOf(749, Pulse(60))) // the same hold is shorter at a slower tempo
        assertEquals(250L, Tune.durationMs(EIGHTH, pulse))
        assertEquals(2000L, Tune.durationMs(WHOLE, pulse))
        assertEquals(1500L, Tune.durationMs(QUARTER, Pulse(40)))
    }

    @Test
    fun `in eighths time a click is an eighth note, as the metronome counts`() {
        val sixEight = Pulse(120, TimeSignature.SIX_EIGHT)
        assertEquals(500.0, sixEight.clickMs, 0.001)
        assertEquals(1000.0, sixEight.quarterMs, 0.001)
        assertEquals(500L, Tune.durationMs(EIGHTH, sixEight)) // one click
        assertEquals(3000L, Tune.durationMs(DOTTED_HALF, sixEight)) // a full bar: six clicks
        assertEquals(EIGHTH, Tune.lengthOf(500, sixEight)) // held for one click
        assertEquals(QUARTER, Tune.lengthOf(1000, sixEight)) // two clicks
        assertEquals(listOf(750L, 1500L, 2500L, 3500L), Tune.growAtMs(sixEight))
        assertEquals(1000L, Tune.durationMs(HALF, Pulse(60, TimeSignature.TWO_TWO))) // 2/2 clicks half notes
    }

    @Test
    fun `a bar lasts as long as the metronome's bar in every time signature`() {
        for (signature in TimeSignature.entries) {
            val pulse = Pulse(100, signature)
            val metronomeBarMs = signature.beatsPerBar * pulse.clickMs
            assertEquals(signature.name, metronomeBarMs, Bars.halfBeatsPerBar(signature) * pulse.quarterMs / 2, 0.001)
        }
    }

    @Test
    fun `a held note grows exactly when its release would make the longer length`() {
        for (bpm in listOf(40, 70, 97, 120, 133, 200)) {
            for (signature in listOf(TimeSignature.FOUR_FOUR, TimeSignature.SIX_EIGHT, TimeSignature.TWO_TWO)) {
                val pulse = Pulse(bpm, signature)
                for ((step, at) in Tune.growAtMs(pulse).withIndex()) {
                    assertEquals("$pulse, step $step", NoteLength.entries[step + 1], Tune.lengthOf(at, pulse))
                    assertEquals("$pulse, step $step", NoteLength.entries[step], Tune.lengthOf(at - 1, pulse))
                }
            }
        }
    }

    @Test
    fun `the gap is short and never more than a quarter of the note`() {
        assertEquals(80L, Tune.gapMs(500))
        assertEquals(37L, Tune.gapMs(150))
    }

    @Test
    fun `encode and decode round-trip in half beats`() {
        val notes = listOf(TuneNote(60, EIGHTH), TuneNote(67, QUARTER), TuneNote(69, DOTTED_HALF), TuneNote(84, WHOLE))
        assertEquals("60:1,67:2,69:6,84:8", Tune.encode(notes))
        assertEquals(notes, Tune.decode("60:1,67:2,69:6,84:8", legacyBpm = 120))
        assertEquals("", Tune.encode(emptyList()))
        assertTrue(Tune.decode("", legacyBpm = 120).isEmpty())
    }

    @Test
    fun `a tune saved as held times becomes lengths at the tempo given`() {
        assertEquals(
            listOf(TuneNote(60, EIGHTH), TuneNote(62, QUARTER), TuneNote(64, HALF), TuneNote(65, WHOLE)),
            Tune.decode("60:300,62:450,64:1000,65:2000", legacyBpm = 120),
        )
        assertEquals(listOf(TuneNote(60, QUARTER)), Tune.decode("60:450", legacyBpm = 100)) // 0.75 of a beat
    }

    @Test
    fun `decode drops what it cannot read, rounds odd lengths and ignores a pause field`() {
        assertEquals(
            listOf(TuneNote(60, QUARTER), TuneNote(72, HALF), TuneNote(74, HALF)),
            Tune.decode("60:2,x:1,61:2,67,72:4:150, ,74:3,76:0,77:-2", legacyBpm = 120),
        )
    }

    @Test
    fun `rests and breaths round-trip next to notes`() {
        val items = listOf(TuneNote(60, QUARTER), TuneRest(EIGHTH), TuneNote(67, HALF), TuneBreath, TuneRest(DOTTED_HALF), TuneRest(WHOLE))
        assertEquals("60:2,r:1,67:4,b,r:6,r:8", Tune.encode(items))
        assertEquals(items, Tune.decode("60:2,r:1,67:4,b,r:6,r:8", legacyBpm = 120))
    }

    @Test
    fun `decode reads rests and breaths leniently`() {
        assertEquals(
            listOf(TuneRest(QUARTER), TuneRest(WHOLE), TuneBreath, TuneRest(HALF)),
            Tune.decode("r:2,r:20,r,r:0,r:x, b ,r:3:150", legacyBpm = 120),
        )
    }

    @Test
    fun `sanitize keeps rests and breaths but never two breaths in a row`() {
        assertEquals(
            listOf(TuneBreath, TuneNote(60, QUARTER), TuneBreath, TuneRest(HALF), TuneBreath),
            Tune.sanitize(
                listOf(TuneBreath, TuneNote(60, QUARTER), TuneBreath, TuneNote(96, QUARTER), TuneBreath, TuneRest(HALF), TuneBreath, TuneBreath),
            ),
        )
        assertEquals(200, Tune.sanitize(List(250) { if (it % 2 == 0) TuneRest(QUARTER) else TuneBreath }).size)
    }

    @Test
    fun `sanitize keeps offered notes and caps`() {
        assertEquals(200, Tune.sanitize(List(250) { TuneNote(60, QUARTER) }).size)
        assertEquals(listOf(TuneNote(62, HALF)), Tune.sanitize(listOf(TuneNote(96, QUARTER), TuneNote(62, HALF))))
    }
}
