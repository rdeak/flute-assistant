// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.tuner

import hr.bertlab.fluteassistant.domain.tuner.FluteKey.C_SHARP
import hr.bertlab.fluteassistant.domain.tuner.FluteKey.E_FLAT
import hr.bertlab.fluteassistant.domain.tuner.FluteKey.G_SHARP
import hr.bertlab.fluteassistant.domain.tuner.FluteKey.L1
import hr.bertlab.fluteassistant.domain.tuner.FluteKey.L2
import hr.bertlab.fluteassistant.domain.tuner.FluteKey.L3
import hr.bertlab.fluteassistant.domain.tuner.FluteKey.LOW_C
import hr.bertlab.fluteassistant.domain.tuner.FluteKey.R1
import hr.bertlab.fluteassistant.domain.tuner.FluteKey.R2
import hr.bertlab.fluteassistant.domain.tuner.FluteKey.R3
import hr.bertlab.fluteassistant.domain.tuner.FluteKey.THUMB
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Spot checks against the Woodwind Fingering Guide's basic chart (C-foot Boehm flute). */
class FluteFingeringsTest {

    @Test
    fun `first octave`() {
        assertEquals(setOf(THUMB, L1, L2, L3, R1, R2, R3, LOW_C), FluteFingerings.keysFor(60)) // c1
        assertEquals(setOf(THUMB, L1, L2, L3, R1, R2, R3, C_SHARP), FluteFingerings.keysFor(61)) // cis1
        assertEquals(setOf(THUMB, L1, L2, L3, R1, R2, R3), FluteFingerings.keysFor(62)) // d1, no E flat key
        assertEquals(setOf(THUMB, L1, L2, L3, R3, E_FLAT), FluteFingerings.keysFor(66)) // fis1
        assertEquals(setOf(THUMB, L1, L2, L3, G_SHARP, E_FLAT), FluteFingerings.keysFor(68)) // gis1
        assertEquals(setOf(THUMB, L1, L2, E_FLAT), FluteFingerings.keysFor(69)) // a1
        assertEquals(setOf(THUMB, L1, R1, E_FLAT), FluteFingerings.keysFor(70)) // b1, "one and one"
        assertEquals(setOf(THUMB, L1, E_FLAT), FluteFingerings.keysFor(71)) // h1
    }

    @Test
    fun `second octave lifts the first finger for d and dis and otherwise repeats the first`() {
        assertEquals(setOf(L1, E_FLAT), FluteFingerings.keysFor(72)) // c2
        assertEquals(setOf(E_FLAT), FluteFingerings.keysFor(73)) // cis2
        assertEquals(setOf(THUMB, L2, L3, R1, R2, R3), FluteFingerings.keysFor(74)) // d2
        assertEquals(setOf(THUMB, L2, L3, R1, R2, R3, E_FLAT), FluteFingerings.keysFor(75)) // dis2
        for (midi in 76..83) assertEquals("midi $midi", FluteFingerings.keysFor(midi - 12), FluteFingerings.keysFor(midi))
    }

    @Test
    fun `third octave`() {
        assertEquals(setOf(THUMB, L2, L3, E_FLAT), FluteFingerings.keysFor(86)) // d3
        assertEquals(setOf(THUMB, L1, L2, L3, G_SHARP, R1, R2, R3, E_FLAT), FluteFingerings.keysFor(87)) // dis3
        assertEquals(setOf(THUMB, L1, L2, R1, R2, E_FLAT), FluteFingerings.keysFor(88)) // e3
        assertEquals(setOf(L1, L2, L3, E_FLAT), FluteFingerings.keysFor(91)) // g3
        assertEquals(setOf(THUMB, L2, R1, E_FLAT), FluteFingerings.keysFor(93)) // a3
        assertEquals(setOf(L1, L2, L3, G_SHARP, R1), FluteFingerings.keysFor(96)) // c4 on a C foot
    }

    @Test
    fun `every flute note has a fingering except the two that need trill keys`() {
        for (midi in FluteRange.midiRange) {
            if (midi == 94 || midi == 95) assertNull("midi $midi", FluteFingerings.keysFor(midi))
            else assertNotNull("midi $midi", FluteFingerings.keysFor(midi))
        }
        assertNull(FluteFingerings.keysFor(59))
        assertNull(FluteFingerings.keysFor(97))
    }
}
