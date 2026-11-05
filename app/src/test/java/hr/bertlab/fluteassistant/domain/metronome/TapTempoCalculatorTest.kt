// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.metronome

import hr.bertlab.fluteassistant.domain.model.MetronomeConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TapTempoCalculatorTest {

    @Test
    fun `first tap yields no tempo`() {
        val calc = TapTempoCalculator()
        assertNull(calc.tap(1_000))
        assertEquals(1, calc.tapCount)
    }

    @Test
    fun `two taps half a second apart give 120 bpm`() {
        val calc = TapTempoCalculator()
        calc.tap(0)
        assertEquals(120, calc.tap(500))
    }

    @Test
    fun `tempo is averaged over all taps in the window`() {
        val calc = TapTempoCalculator()
        calc.tap(0)
        calc.tap(480)
        assertEquals(125, calc.tap(960))
    }

    @Test
    fun `a long pause starts a fresh sequence`() {
        val calc = TapTempoCalculator(resetAfterMillis = 2_000)
        calc.tap(0)
        calc.tap(500)
        assertNull(calc.tap(5_000))
        assertEquals(1, calc.tapCount)
        assertEquals(120, calc.tap(5_500))
    }

    @Test
    fun `only the most recent taps are used`() {
        val calc = TapTempoCalculator(maxTaps = 3)
        calc.tap(0)
        calc.tap(1_000)
        calc.tap(2_000) // 60 bpm so far
        calc.tap(2_500)
        assertEquals(120, calc.tap(3_000)) // window = 2000, 2500, 3000
    }

    @Test
    fun `result is clamped to the supported bpm range`() {
        val fast = TapTempoCalculator()
        fast.tap(0)
        assertEquals(MetronomeConfig.MAX_BPM, fast.tap(50))

        val slow = TapTempoCalculator(resetAfterMillis = 10_000)
        slow.tap(0)
        assertEquals(MetronomeConfig.MIN_BPM, slow.tap(4_000))
    }

    @Test
    fun `reset clears taps`() {
        val calc = TapTempoCalculator()
        calc.tap(0)
        calc.tap(500)
        calc.reset()
        assertEquals(0, calc.tapCount)
        assertNull(calc.tap(1_000))
    }
}
