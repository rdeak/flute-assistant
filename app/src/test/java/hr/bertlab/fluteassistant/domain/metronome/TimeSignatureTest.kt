// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.metronome

import hr.bertlab.fluteassistant.domain.metronome.BeatStrength.MEDIUM
import hr.bertlab.fluteassistant.domain.metronome.BeatStrength.STRONG
import hr.bertlab.fluteassistant.domain.metronome.BeatStrength.WEAK
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeSignatureTest {

    @Test
    fun `every signature has one accent per beat and starts on a strong beat`() {
        TimeSignature.entries.forEach { signature ->
            assertEquals(signature.label, signature.beatsPerBar, signature.accents.size)
            assertEquals(signature.label, STRONG, signature.accents.first())
        }
    }

    @Test
    fun `the beginner signatures come first in display order`() {
        assertEquals(
            listOf("2/4", "3/4", "4/4"),
            TimeSignature.entries.take(3).map { it.label },
        )
    }

    @Test
    fun `compound meters accent each group of three`() {
        assertEquals(listOf(STRONG, WEAK, WEAK, MEDIUM, WEAK, WEAK), TimeSignature.SIX_EIGHT.accents)
        assertEquals(listOf(STRONG, WEAK, WEAK, MEDIUM, WEAK, WEAK, MEDIUM, WEAK, WEAK), TimeSignature.NINE_EIGHT.accents)
        assertEquals(
            listOf(STRONG, WEAK, WEAK, MEDIUM, WEAK, WEAK, MEDIUM, WEAK, WEAK, MEDIUM, WEAK, WEAK),
            TimeSignature.TWELVE_EIGHT.accents,
        )
    }

    @Test
    fun `seven eight is grouped two plus two plus three`() {
        assertEquals(listOf(STRONG, WEAK, MEDIUM, WEAK, MEDIUM, WEAK, WEAK), TimeSignature.SEVEN_EIGHT.accents)
    }

    @Test
    fun `simple meters only accent the first beat`() {
        assertEquals(listOf(STRONG, WEAK), TimeSignature.TWO_TWO.accents)
        assertEquals(listOf(STRONG, WEAK, WEAK), TimeSignature.THREE_EIGHT.accents)
        assertEquals("2/2", TimeSignature.TWO_TWO.label)
        assertEquals("3/8", TimeSignature.THREE_EIGHT.label)
    }
}
