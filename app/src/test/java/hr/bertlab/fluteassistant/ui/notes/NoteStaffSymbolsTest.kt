// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.notes

import hr.bertlab.fluteassistant.domain.notes.Phrases
import hr.bertlab.fluteassistant.ui.common.MusicSymbols
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The notes game's clef and heads from the music font (spec `2026-11-24-notes-staff-music-symbols-design.md`). */
class NoteStaffSymbolsTest {
    /** The canvas board's notes staff, 334 x 390 dp: a line gap of 55.7 dp (the A55's own is about 47). */
    private val gap = 55.7f
    private val boardWidth = 334f
    private val line = 3f

    @Test
    fun `the clef is as tall as the drawn one`() {
        val tall = (MusicSymbols.CLEF_RISE + MusicSymbols.CLEF_DROP) * gap * NoteStaffSymbols.CLEF_SHARE
        assertEquals(211f, tall, 2f)
        assertEquals("room to spare: the full height", 4 * gap * 0.54f, NoteStaffSymbols.clefEm(gap, room = 110f), 0.01f)
    }

    /** The drawn oval was 32.3 x 23.4 dp on the board. */
    @Test
    fun `a head is about the drawn oval's size`() {
        val width = NoteStaffSymbols.headHalfWidth(gap) * 2
        val height = NoteStaffSymbols.headHalfHeight(gap) * 2
        assertEquals(29.6f, width, 0.1f)
        assertEquals(25.1f, height, 0.1f)
        assertTrue("within 3.5 dp of the oval's width", kotlin.math.abs(width - 32.3f) <= 3.5f)
        assertTrue("within 3.5 dp of the oval's height", kotlin.math.abs(height - 23.4f) <= 3.5f)
        assertEquals(4 * gap * 0.45f, NoteStaffSymbols.headEm(gap), 0.01f)
    }

    /**
     * The font's clef is wider for its height than the drawn one, so on a staff tall for its width it would reach under
     * the nearest slot (the second column of a four-note phrase): it is drawn smaller there, as wide as the room.
     */
    @Test
    fun `the clef stops short of the nearest slot on a tall narrow staff`() {
        val clef = NoteStaffSymbols.clefEm(gap, room = 60f)
        assertEquals("as wide as the room", 60f, clef / 4 * MusicSymbols.CLEF_WIDTH, 0.01f)
        assertTrue("smaller than the full height", clef < 4 * gap * NoteStaffSymbols.CLEF_SHARE)
    }

    /** The clef's room ends a line short of the four-note phrase's second slot, wherever the staff's size puts it. */
    @Test
    fun `the room ends a line short of the nearest slot`() {
        listOf(boardWidth to gap, 290f to 84f, 600f to 40f).forEach { (width, lineGap) ->
            val slotEdge = width * phraseColumns(Phrases.MAX_LENGTH)[1] - slotHalfWidth(width, lineGap / 2)
            val right = NoteStaffSymbols.clefLeft(lineGap) + NoteStaffSymbols.clefRoom(width, lineGap, line)
            assertEquals("$width wide, lines $lineGap apart", slotEdge - line, right, 0.01f)
        }
    }

    /** On the board the full clef fits: 80.7 dp wide from 8.4, it ends at 89, short of the slot at 118. */
    @Test
    fun `on the canvas board the full clef fits`() {
        val room = NoteStaffSymbols.clefRoom(boardWidth, gap, line)
        assertEquals(4 * gap * NoteStaffSymbols.CLEF_SHARE, NoteStaffSymbols.clefEm(gap, room), 0.01f)
        assertEquals(8.4f, NoteStaffSymbols.clefLeft(gap), 0.05f)
    }

    /** The tests' tall host (a pitch of 42 dp on a 290 dp staff) is where the cap binds. */
    @Test
    fun `on a staff tall for its width the clef is capped`() {
        val lineGap = 84f
        val room = NoteStaffSymbols.clefRoom(290f, lineGap, line)
        assertTrue("capped", NoteStaffSymbols.clefEm(lineGap, room) < 4 * lineGap * NoteStaffSymbols.CLEF_SHARE)
    }
}
