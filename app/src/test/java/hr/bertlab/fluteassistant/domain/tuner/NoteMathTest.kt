// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.tuner

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteMathTest {

    @Test
    fun `440 Hz is A4 with zero cents`() {
        val reading = NoteMath.frequencyToNote(440.0)
        assertEquals(69, reading.midi)
        assertEquals(0.0, reading.cents, 0.01)
        assertEquals(440.0, reading.frequency, 0.0)
    }

    @Test
    fun `middle C and the octaves around it`() {
        val c4 = NoteMath.frequencyToNote(261.6256)
        assertEquals(60, c4.midi)
        assertEquals(0.0, c4.cents, 0.05)

        assertEquals(84, NoteMath.frequencyToNote(1046.502).midi) // C6
        assertEquals(59, NoteMath.frequencyToNote(246.94).midi) // B3
    }

    @Test
    fun `sharps are notes of their own`() {
        assertEquals(70, NoteMath.frequencyToNote(466.16).midi) // A sharp 4
        assertEquals(78, NoteMath.frequencyToNote(739.99).midi) // F sharp 5
    }

    @Test
    fun `sharp and flat deviations in cents`() {
        val sharp = NoteMath.frequencyToNote(445.0)
        assertEquals(69, sharp.midi)
        assertEquals(19.56, sharp.cents, 0.05)

        val flat = NoteMath.frequencyToNote(432.0)
        assertEquals(69, flat.midi)
        assertEquals(-31.77, flat.cents, 0.05)
    }

    @Test
    fun `deviation is never more than half a semitone`() {
        var f = 55.0
        while (f < 4000.0) {
            val cents = NoteMath.frequencyToNote(f).cents
            assert(cents > -50.0001 && cents <= 50.0001) { "cents=$cents at $f Hz" }
            f *= 1.01
        }
    }

    @Test
    fun `midi to frequency`() {
        assertEquals(440.0, NoteMath.midiToFrequency(69), 1e-9)
        assertEquals(261.6256, NoteMath.midiToFrequency(60), 1e-3)
    }

    @Test
    fun `cents between a frequency and a target note`() {
        assertEquals(0.0, NoteMath.centsBetween(440.0, 69), 0.01)
        assertEquals(100.0, NoteMath.centsBetween(466.16, 69), 0.05)
        assertEquals(-1200.0, NoteMath.centsBetween(220.0, 69), 0.01)
        assertEquals(900.0, NoteMath.centsBetween(440.0, 60), 0.01)
    }
}
