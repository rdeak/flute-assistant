// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.fingers

import hr.bertlab.fluteassistant.domain.tuner.NoteSpelling
import hr.bertlab.fluteassistant.ui.common.MusicSymbols.CLEF_DROP
import hr.bertlab.fluteassistant.ui.common.MusicSymbols.CLEF_WIDTH
import hr.bertlab.fluteassistant.ui.common.MusicSymbols.FLAT_TOP
import hr.bertlab.fluteassistant.ui.common.MusicSymbols.NOTE_HALF_HEIGHT
import hr.bertlab.fluteassistant.ui.common.MusicSymbols.SHARP_DROP
import hr.bertlab.fluteassistant.ui.common.MusicSymbols.SHARP_WIDTH
import hr.bertlab.fluteassistant.ui.fingers.SymbolStaff.CLEF_LEFT
import hr.bertlab.fluteassistant.ui.fingers.SymbolStaff.LEDGER_HALF
import hr.bertlab.fluteassistant.ui.fingers.SymbolStaff.bottomLine
import hr.bertlab.fluteassistant.ui.fingers.SymbolStaff.gapFor
import hr.bertlab.fluteassistant.ui.fingers.SymbolStaff.noteX
import hr.bertlab.fluteassistant.ui.fingers.SymbolStaff.signRight
import hr.bertlab.fluteassistant.ui.fingers.SymbolStaff.yOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Fingers staff's sizes (spec `2026-11-24-fingers-music-symbols-and-book-chart-design.md`, part 2). */
class SymbolStaffTest {

    @Test
    fun `the full staff is 108 dp tall at an 8_2 gap`() {
        assertEquals(107.99f, SymbolStaff.TALL, 0.01f)
        assertEquals(8.2f, gapFor(SymbolStaff.TALL), 0.001f)
    }

    @Test
    fun `a row at its least gives a 7_6 gap`() {
        assertEquals(7.595f, gapFor(98f), 0.01f)
        assertTrue("at least today's 7.5", gapFor(98f) >= 7.5f)
    }

    @Test
    fun `a taller box keeps the 8_2 gap`() {
        assertEquals(8.2f, gapFor(140f), 0.001f)
    }

    @Test
    fun `places count from c1 as every staff in the app`() {
        val b = bottomLine(SymbolStaff.TALL, 8.2f)
        assertEquals(b, yOf(2, b, 8.2f), 0.001f)
        assertEquals(b - 4 * 8.2f, yOf(10, b, 8.2f), 0.001f)
        assertEquals(b + 8.2f, yOf(0, b, 8.2f), 0.001f)
    }

    /** From a split-screen pane's row on its side (56) to a row taller than the staff needs (140). */
    @Test
    fun `c4 c1 and the clef's foot stay inside the box`() {
        listOf(56f, 70f, 98f, SymbolStaff.TALL, 140f).forEach { h ->
            val g = gapFor(h)
            val b = bottomLine(h, g)
            assertTrue("$h: c4's head at ${yOf(21, b, g) - NOTE_HALF_HEIGHT * g}", yOf(21, b, g) - NOTE_HALF_HEIGHT * g >= 0f)
            assertTrue("$h: c1's head at ${yOf(0, b, g) + NOTE_HALF_HEIGHT * g}", yOf(0, b, g) + NOTE_HALF_HEIGHT * g <= h)
            assertTrue("$h: the clef's foot at ${b - g + CLEF_DROP * g}", b - g + CLEF_DROP * g <= h)
        }
    }

    /** The sharp is the wider sign; on its side the staff is 56 dp wide, upright 74, and a tablet's row on its side gives 56 x 140. */
    @Test
    fun `a sign stands clear of the clef on every staff`() {
        listOf(56f to 56f, 56f to 70f, 56f to 140f, 74f to 98f, 74f to SymbolStaff.TALL).forEach { (w, h) ->
            val g = gapFor(h)
            val signLeft = signRight(w, g) - SHARP_WIDTH * g
            val clefRight = CLEF_LEFT + CLEF_WIDTH * g
            assertTrue("$w x $h: the sign starts at $signLeft, the clef ends at $clefRight", signLeft >= clefRight + 1f)
        }
    }

    /** A sign stands left of the ledger lines with air, as engravers put it, not on them (the final review, 2026-11-24). */
    @Test
    fun `a sign stands clear of its note's ledger lines`() {
        listOf(56f to 70f, 74f to 98f, 74f to SymbolStaff.TALL).forEach { (w, h) ->
            val g = gapFor(h)
            val ledgerStart = noteX(w, g) - LEDGER_HALF * g
            assertTrue("$w x $h: the sign ends at ${signRight(w, g)}, the ledger line starts at $ledgerStart", signRight(w, g) <= ledgerStart - 0.1f * g)
        }
    }

    /** C♯4's sharp hangs lowest and B♭6's flat stands highest: at its full size the box holds both (the final review, 2026-11-24). */
    @Test
    fun `the signs stay inside the box where it has room`() {
        val sharpPlace = NoteSpelling.placeOf(61)
        val flatPlace = NoteSpelling.placeOf(94)
        listOf(56f, 70f, SymbolStaff.TALL, 140f).forEach { h ->
            val g = gapFor(h)
            val b = bottomLine(h, g)
            assertTrue("$h: B♭6's flat at ${yOf(flatPlace, b, g) - FLAT_TOP * g}", yOf(flatPlace, b, g) - FLAT_TOP * g >= 0f)
            assertTrue("$h: C♯4's sharp at ${yOf(sharpPlace, b, g) + SHARP_DROP * g}", yOf(sharpPlace, b, g) + SHARP_DROP * g <= h)
        }
    }

    /** At a row's least the box is 98 dp: the signs may reach into the row's 5 dp padding, never to its 3 dp outline. */
    @Test
    fun `at a row's least the signs reach at most 1 dp past the box`() {
        val h = 98f
        val g = gapFor(h)
        val b = bottomLine(h, g)
        assertTrue("the flat", -(yOf(NoteSpelling.placeOf(94), b, g) - FLAT_TOP * g) <= 1f)
        assertTrue("the sharp", yOf(NoteSpelling.placeOf(61), b, g) + SHARP_DROP * g - h <= 1f)
    }
}
