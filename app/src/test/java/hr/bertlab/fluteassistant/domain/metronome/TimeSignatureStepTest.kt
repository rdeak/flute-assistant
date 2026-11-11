// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.metronome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeSignatureStepTest {

    @Test
    fun `beat buttons step one beat at a time from two to six`() {
        assertEquals(TimeSignature.THREE_FOUR, TimeSignature.TWO_FOUR.moreBeats())
        assertEquals(TimeSignature.SIX_EIGHT, TimeSignature.FIVE_FOUR.moreBeats())
        assertNull(TimeSignature.SIX_EIGHT.moreBeats())
        assertNull(TimeSignature.TWO_FOUR.fewerBeats())
        assertEquals(TimeSignature.FOUR_FOUR, TimeSignature.FIVE_FOUR.fewerBeats())
        // A bar the buttons do not offer steps onto the ladder.
        assertEquals(TimeSignature.FOUR_FOUR, TimeSignature.THREE_EIGHT.moreBeats())
        assertEquals(TimeSignature.SIX_EIGHT, TimeSignature.TWELVE_EIGHT.fewerBeats())
        assertNull(TimeSignature.TWO_TWO.fewerBeats())
    }
}
