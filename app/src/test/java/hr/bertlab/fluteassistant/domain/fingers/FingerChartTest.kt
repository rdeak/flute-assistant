// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.fingers

import org.junit.Assert.assertEquals
import org.junit.Test

class FingerChartTest {
    @Test
    fun `c has four notes and every other note three`() {
        assertEquals(listOf(60, 72, 84, 96), FingerChart.notesOf(0))
        for (pc in 1..11) assertEquals("pitch class $pc", listOf(60 + pc, 72 + pc, 84 + pc), FingerChart.notesOf(pc))
    }

    @Test
    fun `no note has more octaves than c`() {
        assertEquals(4, FingerChart.MOST_NOTES)
    }
}
