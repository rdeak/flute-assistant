// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.metronome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TempoBandTest {

    @Test
    fun `bands climb from the snail to the cheetah inside the engine's range`() {
        val starts = TempoBand.entries.map { it.fromBpm }
        assertEquals(starts.sorted(), starts)
        assertEquals(TempoBand.MIN_BPM, starts.first())
        assertTrue(TempoBand.MIN_BPM >= MetronomeConfig.MIN_BPM && TempoBand.MAX_BPM <= MetronomeConfig.MAX_BPM)
    }

    @Test
    fun `a tempo belongs to the band it has reached`() {
        assertEquals(TempoBand.SNAIL, tempoBand(40))
        assertEquals(TempoBand.SNAIL, tempoBand(59))
        assertEquals(TempoBand.TURTLE, tempoBand(60))
        assertEquals(TempoBand.RABBIT, tempoBand(120))
        assertEquals(TempoBand.CHEETAH, tempoBand(200))
        assertEquals(TempoBand.SNAIL, tempoBand(10))
    }

    @Test
    fun `every band takes a fifth of the ring`() {
        assertEquals(0f, dialFraction(40), 0.0001f)
        assertEquals(0.2f, dialFraction(60), 0.0001f)
        assertEquals(0.6f, dialFraction(100), 0.0001f)
        assertEquals(0.8f, dialFraction(130), 0.0001f)
        assertEquals(1f, dialFraction(200), 0.0001f)
        assertEquals(0.6f + 0.2f * 20 / 30, dialFraction(120), 0.0001f)
        // Outside the ring's range the knob stays at its end.
        assertEquals(1f, dialFraction(250), 0.0001f)
    }

    @Test
    fun `the ring position maps back to the same tempo`() {
        for (bpm in TempoBand.MIN_BPM..TempoBand.MAX_BPM) {
            assertEquals(bpm, bpmAtDialFraction(dialFraction(bpm)))
        }
        assertEquals(TempoBand.MIN_BPM, bpmAtDialFraction(-1f))
        assertEquals(TempoBand.MAX_BPM, bpmAtDialFraction(2f))
    }

    @Test
    fun `tapping a band jumps to its start, and the rabbit again to 120`() {
        assertEquals(40, TempoBand.SNAIL.jumpFrom(97))
        assertEquals(100, TempoBand.RABBIT.jumpFrom(80))
        assertEquals(120, TempoBand.RABBIT.jumpFrom(100))
        assertEquals(100, TempoBand.RABBIT.jumpFrom(120))
        assertEquals(130, TempoBand.CHEETAH.jumpFrom(130))
    }
}
