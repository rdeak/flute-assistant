// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.notes

import org.junit.Assert.assertEquals
import org.junit.Test

/** Where the notes staff's places are: the drawing, the finger and the screen reader's rows all count with [StaffFrame]. */
class StaffFrameTest {

    // Five places and a margin of one at either end: seven pitches of 100.
    private val frame = StaffFrame(height = 700f, visible = 2..6)

    @Test
    fun `a place is one pitch tall, the highest at the top under the margin`() {
        assertEquals(100f, frame.pitch, 0.001f)
        assertEquals(150f, frame.yOf(6), 0.001f)
        assertEquals(550f, frame.yOf(2), 0.001f)
    }

    @Test
    fun `the place at a place's middle is that place`() {
        for (position in 2..6) assertEquals(position, frame.positionAt(frame.yOf(position)))
    }

    @Test
    fun `the margins belong to the places next to them`() {
        assertEquals(6, frame.positionAt(0f))
        assertEquals(6, frame.positionAt(99f))
        assertEquals(2, frame.positionAt(650f))
        assertEquals(2, frame.positionAt(700f))
    }

    @Test
    fun `a place ends where the next one begins`() {
        assertEquals(6, frame.positionAt(199f))
        assertEquals(5, frame.positionAt(200f))
    }
}
