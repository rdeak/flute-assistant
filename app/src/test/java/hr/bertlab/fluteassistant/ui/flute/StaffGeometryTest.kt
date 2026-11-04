package hr.bertlab.fluteassistant.ui.flute

import hr.bertlab.fluteassistant.domain.flute.NoteLength
import hr.bertlab.fluteassistant.domain.notes.StaffNotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StaffGeometryTest {

    @Test
    fun `notes sit a step apart, c1 on its ledger line under the staff`() {
        val c4 = StaffNotes.positionOf(60)
        val g4 = StaffNotes.positionOf(67)
        assertEquals(StaffGeometry.BOTTOM_LINE_Y + StaffGeometry.LINE_GAP, StaffGeometry.yOf(c4))
        assertEquals(StaffGeometry.BOTTOM_LINE_Y - StaffGeometry.LINE_GAP, StaffGeometry.yOf(g4))
        // The five lines are the line positions, e¹ at the bottom and f² on top, b¹ in the middle.
        assertEquals(StaffGeometry.BOTTOM_LINE_Y, StaffGeometry.yOf(StaffNotes.LINE_POSITIONS.first()))
        assertEquals(StaffGeometry.MIDDLE_LINE_Y, StaffGeometry.yOf(StaffNotes.LINE_POSITIONS[2]))
        assertEquals(StaffGeometry.TOP_LINE_Y, StaffGeometry.yOf(StaffNotes.LINE_POSITIONS.last()))
    }

    @Test
    fun `every note, lit and tied, with its stem up, stays inside the staff's box and the room allowed around it`() {
        for (position in 0 until StaffNotes.POSITIONS) {
            val extent = StaffGeometry.noteExtent(position)
            assertTrue("position $position reaches ${extent.start}", extent.start >= -StaffGeometry.OVERFLOW_TOP)
            assertTrue("position $position reaches ${extent.endInclusive}", extent.endInclusive <= StaffGeometry.HEIGHT + StaffGeometry.OVERFLOW_BOTTOM)
        }
        // c³'s stem uses the room above all the way: the box is no taller than it needs to be.
        assertEquals(-StaffGeometry.OVERFLOW_TOP, StaffGeometry.noteExtent(StaffNotes.POSITIONS - 1).start)
        // The stage's padding (16 dp, 3 of them its border) and the 14 dp gap above the line of pills hold what reaches out.
        assertTrue(StaffGeometry.OVERFLOW_TOP <= 16f - 3f && StaffGeometry.OVERFLOW_BOTTOM < 14f)
        // A rest's ring stays on the staff; the band covers the staff and the names under it, and stays in the whole.
        assertTrue(StaffGeometry.MIDDLE_LINE_Y + StaffGeometry.REST_LIT_RADIUS <= StaffGeometry.HEIGHT)
        assertTrue(StaffGeometry.BAND_TOP >= 0f && StaffGeometry.BAND_BOTTOM <= StaffGeometry.TOTAL_HEIGHT)
        assertTrue(StaffGeometry.BAND_BOTTOM >= StaffGeometry.NAMES_TOP + StaffGeometry.PILL_HEIGHT)
        // What reaches below the staff (c¹'s ring and tie) stops short of the names, and a tie under the pills starts above the marks row.
        assertTrue(StaffGeometry.HEIGHT + StaffGeometry.OVERFLOW_BOTTOM <= StaffGeometry.NAMES_TOP)
        assertTrue(StaffGeometry.NAMES_TOP + StaffGeometry.PILL_HEIGHT + StaffGeometry.PILL_TIE_GAP <= StaffGeometry.MARKS_TOP)
        assertEquals(StaffGeometry.TOTAL_HEIGHT, StaffGeometry.MARKS_TOP + StaffGeometry.MARKS_HEIGHT)
    }

    @Test
    fun `the marks row sits under the names and the whole staff grew by sixteen`() {
        assertEquals(StaffGeometry.NAMES_TOP + StaffGeometry.PILL_HEIGHT + StaffGeometry.MARKS_GAP, StaffGeometry.MARKS_TOP)
        assertEquals(257f, StaffGeometry.TOTAL_HEIGHT)
        assertTrue(StaffGeometry.MARK_SIZE <= StaffGeometry.MARKS_HEIGHT)
        assertTrue(StaffGeometry.ANIMAL_SIZE <= StaffGeometry.MARKS_HEIGHT)
        // The animal at the top of its hop (8 dp) still stands under the pills, not over them.
        val animalTop = StaffGeometry.MARKS_TOP + (StaffGeometry.MARKS_HEIGHT - StaffGeometry.ANIMAL_SIZE) / 2 - 8f
        assertTrue(animalTop >= StaffGeometry.NAMES_TOP + StaffGeometry.PILL_HEIGHT - 2f)
    }

    @Test
    fun `the book button sits under the clef, left of the notes, inside the staff, at rest and while running`() {
        for (s in listOf(1f, RunningStaffScale)) {
            val top = StaffGeometry.bookButtonTop(s)
            assertTrue("scale $s: top $top", top >= StaffGeometry.CLEF_BOTTOM * s + 4f)
            assertTrue("scale $s", top + StaffGeometry.CORNER_BUTTON + StaffGeometry.CORNER_SHADOW <= StaffGeometry.TOTAL_HEIGHT * s)
            assertTrue("scale $s", StaffGeometry.CORNER_BUTTON <= StaffGeometry.NOTES_CLIP * s)
        }
    }

    @Test
    fun `space grows with the length, 29 per beat, and heads never touch`() {
        assertEquals(listOf(29f, 29f, 58f, 87f, 116f), NoteLength.entries.map(StaffGeometry::slotWidth))
        val head = StaffGeometry.WHOLE_RX * 2 + StaffGeometry.HEAD_EDGE * 2 // the widest head, a whole note
        assertTrue(NoteLength.entries.all { StaffGeometry.slotWidth(it) > head })
        // Two 4/4 bars of quarters fit the canvas's 334 dp staff.
        val twoBars = 2 * (4 * StaffGeometry.slotWidth(NoteLength.QUARTER) + StaffGeometry.BAR_SLOT)
        assertTrue(StaffGeometry.NOTES_START + twoBars + StaffGeometry.NOTES_END_PADDING <= 334f)
        // Each pill fits its note's room with a gap, wide enough for a name.
        assertTrue(NoteLength.entries.all { StaffGeometry.slotWidth(it) - StaffGeometry.PILL_GAP >= 26f })
        // The first note's ring is not cut where the notes start to scroll.
        assertTrue(StaffGeometry.NOTES_START + StaffGeometry.HEAD_X - StaffGeometry.LIT_RADIUS >= StaffGeometry.NOTES_CLIP)
    }
}
