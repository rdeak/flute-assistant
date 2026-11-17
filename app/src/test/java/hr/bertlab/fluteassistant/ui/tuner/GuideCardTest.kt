// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.tuner

import hr.bertlab.fluteassistant.domain.listen.TunerAdvice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuideCardTest {

    /** The tuner's line opens the guide when it names a fix on the flute, on the card about it (2026-11-18). */
    @Test
    fun `a fix on the flute opens its card and nothing else opens one`() {
        assertEquals(GuideCard.HEAD_JOINT, TunerAdvice.HEAD_JOINT_IN.guideCard)
        assertEquals(GuideCard.HEAD_JOINT, TunerAdvice.HEAD_JOINT_OUT.guideCard)
        assertEquals(GuideCard.ONE_NOTE_OFF, TunerAdvice.ROLL_IN.guideCard)
        assertEquals(GuideCard.ONE_NOTE_OFF, TunerAdvice.ROLL_OUT.guideCard)
        val fixes = setOf(TunerAdvice.HEAD_JOINT_IN, TunerAdvice.HEAD_JOINT_OUT, TunerAdvice.ROLL_IN, TunerAdvice.ROLL_OUT)
        (TunerAdvice.entries - fixes).forEach { assertNull(it.name, it.guideCard) }
    }

    /** The line keeps a fix on the flute a while (`FixHold`) so that its card can still be opened: the two go together. */
    @Test
    fun `what the line holds as a fix on the flute is what opens a card`() {
        TunerAdvice.entries.forEach { assertEquals(it.name, it.isFluteFix, it.guideCard != null) }
    }
}
