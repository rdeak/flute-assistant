// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import hr.bertlab.fluteassistant.domain.metronome.TempoBand
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FacingRightTest {

    @Test
    fun `google's emoji set draws every speed animal facing left, so each is mirrored`() {
        TempoBand.entries.forEach { assertTrue(it.name, mirrorsToFaceRight(it.emoji, EmojiSet.OTHER)) }
    }

    @Test
    fun `samsung's emoji set draws the snail already facing right, so it is left as drawn`() {
        assertFalse(mirrorsToFaceRight(TempoBand.SNAIL.emoji, EmojiSet.SAMSUNG))
    }

    @Test
    fun `samsung's emoji set draws the other four animals facing left, so they are mirrored`() {
        TempoBand.entries.filter { it != TempoBand.SNAIL }
            .forEach { assertTrue(it.name, mirrorsToFaceRight(it.emoji, EmojiSet.SAMSUNG)) }
    }
}
