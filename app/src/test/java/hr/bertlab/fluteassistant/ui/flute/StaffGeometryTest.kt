package hr.bertlab.fluteassistant.ui.flute

import hr.bertlab.fluteassistant.domain.flute.LineMark
import hr.bertlab.fluteassistant.domain.flute.NoteLength
import hr.bertlab.fluteassistant.domain.flute.StepPart
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
    }

    @Test
    fun `the running animal walks at the knob's height, above the staff, inside its box, over no head but c3's edge`() {
        val s = RunningStaffScale
        val half = StaffGeometry.ANIMAL_SIZE * s / 2
        // Level with the knob's middle: the knob does not grow with the running staff, so neither does this height.
        assertEquals(StaffGeometry.CORNER_BUTTON / 2, StaffGeometry.ANIMAL_Y)
        // At the top of its hop (8 dp) it is still inside the staff's box, and it stands clear of the top line.
        assertTrue(StaffGeometry.ANIMAL_Y - half - 8f >= 0f)
        assertTrue(StaffGeometry.ANIMAL_Y + half <= StaffGeometry.TOP_LINE_Y * s)
        // Every head is under it, but the top edge of the highest note's, c³'s, by a few dp (the head's tilt adds a quarter dp to its height).
        val headTop = { position: Int -> (StaffGeometry.yOf(position) - StaffGeometry.HEAD_RY - 0.25f - StaffGeometry.HEAD_EDGE) * s }
        for (position in 0 until StaffNotes.POSITIONS - 1) {
            assertTrue("position $position", headTop(position) >= StaffGeometry.ANIMAL_Y + half)
        }
        assertTrue(headTop(StaffNotes.POSITIONS - 1) >= StaffGeometry.ANIMAL_Y + half - 4f)
    }

    @Test
    fun `on each click the animal stands a new place on, a beat's room in four four and half of one in six eight`() {
        val half = listOf(StepPart.Length(NoteLength.HALF))
        assertEquals(StaffGeometry.HEAD_X, StaffGeometry.beatX(half, 0))
        assertEquals(StaffGeometry.HEAD_X + StaffGeometry.BEAT_WIDTH, StaffGeometry.beatX(half, 2)) // 4/4: the second click
        val whole = listOf(StepPart.Length(NoteLength.WHOLE))
        assertEquals(listOf(14.5f, 43.5f, 72.5f, 101.5f), listOf(0, 2, 4, 6).map { StaffGeometry.beatX(whole, it) })
        val quarter = listOf(StepPart.Length(NoteLength.QUARTER))
        assertEquals(StaffGeometry.BEAT_WIDTH, StaffGeometry.beatX(quarter, 1)) // 6/8: the second click, at the room's edge
        // The next note's head is the same distance on again: one place a click, all along the line.
        assertEquals(StaffGeometry.slotWidth(NoteLength.QUARTER) + StaffGeometry.HEAD_X - StaffGeometry.beatX(quarter, 1), StaffGeometry.BEAT_WIDTH / 2)
        // A half note tied over a bar line: its second beat is past the bar line, over the second head.
        val tied = listOf(StepPart.Length(NoteLength.QUARTER), LineMark.BarLine, StepPart.Length(NoteLength.QUARTER))
        assertEquals(StaffGeometry.BEAT_WIDTH + StaffGeometry.BAR_SLOT + StaffGeometry.HEAD_X, StaffGeometry.beatX(tied, 2))
        // An eighth only ever has its head; past a step's end the animal stays on its last head.
        assertEquals(StaffGeometry.HEAD_X, StaffGeometry.beatX(listOf(StepPart.Length(NoteLength.EIGHTH)), 0))
        assertEquals(StaffGeometry.BEAT_WIDTH + StaffGeometry.BAR_SLOT + StaffGeometry.HEAD_X, StaffGeometry.beatX(tied, 9))
    }

    @Test
    fun `a mark's room is what the staff draws for it`() {
        val tied = LineMark.Step(0, listOf(StepPart.Length(NoteLength.QUARTER), LineMark.BarLine, StepPart.Length(NoteLength.HALF)))
        assertEquals(29f + 10f + 58f, StaffGeometry.markWidth(tied))
        assertEquals(StaffGeometry.BAR_SLOT, StaffGeometry.markWidth(LineMark.BarLine))
        assertEquals(StaffGeometry.BREATH_SLOT, StaffGeometry.markWidth(LineMark.Breath(1)))
    }

    @Test
    fun `the book button sits under the clef, left of the notes, inside the staff, at rest and while running`() {
        for (s in listOf(1f, RunningStaffScale)) {
            val top = StaffGeometry.bookButtonTop(s)
            assertTrue("scale $s: top $top", top >= StaffGeometry.CLEF_BOTTOM * s + 4f)
            assertTrue("scale $s", top + StaffGeometry.BOOK_BUTTON_HEIGHT + StaffGeometry.CORNER_SHADOW <= StaffGeometry.TOTAL_HEIGHT * s)
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
