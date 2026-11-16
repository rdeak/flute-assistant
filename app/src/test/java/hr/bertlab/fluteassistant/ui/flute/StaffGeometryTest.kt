// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.flute

import hr.bertlab.fluteassistant.domain.flute.Bars
import hr.bertlab.fluteassistant.domain.flute.LineMark
import hr.bertlab.fluteassistant.domain.flute.NoteLength
import hr.bertlab.fluteassistant.domain.flute.StepPart
import hr.bertlab.fluteassistant.domain.flute.TuneNote
import hr.bertlab.fluteassistant.domain.metronome.TimeSignature
import hr.bertlab.fluteassistant.domain.notes.StaffNotes
import hr.bertlab.fluteassistant.ui.common.TabletGeometry
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
    fun `the running animal walks near the knob's height, above the staff, inside its box, over no head but c3's edge`() {
        val s = RunningStaffScale
        val half = StaffGeometry.ANIMAL_SIZE * s / 2
        // Half a knob under the box's top, where the knob's middle was before the knob was lifted: the
        // knob does not grow with the running staff, so neither does this height.
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

    /**
     * The child's report of 2026-11-12: on a whole note after a whole note the animal walked off the staff's right
     * edge. The staff keeps the note before the lit one at its left ([Bars.anchor]); that leaves the lit whole note's
     * last beat past the row on a phone, so the staff scrolls on by just as much as the animal needs.
     */
    @Test
    fun `the staff scrolls on just enough to keep the walking animal in its row`() {
        val s = RunningStaffScale
        val whole = listOf(StepPart.Length(NoteLength.WHOLE))
        val start = StaffGeometry.slotWidth(NoteLength.WHOLE) + StaffGeometry.BAR_SLOT // the second whole note
        val lastBeat = start + StaffGeometry.beatX(whole, 6)
        val row = 300f
        val scroll = StaffGeometry.followScroll(anchorStart = 0f, animalX = lastBeat, rowWidth = row, scale = s)
        assertTrue("the last beat is past the row: $scroll", scroll > 0f)
        val middle = (StaffGeometry.NOTES_START - StaffGeometry.NOTES_CLIP) * s + lastBeat * s - scroll
        assertEquals("the animal's box ends at the row's end", row, middle + StaffGeometry.CORNER_BUTTON / 2, 0.01f)
        assertEquals("its head needs no scroll", 0f, StaffGeometry.followScroll(0f, start + StaffGeometry.beatX(whole, 0), row, s))
        assertEquals("at rest there is room", 0f, StaffGeometry.followScroll(0f, lastBeat, row, 1f))
    }

    @Test
    fun `a mark's room is what the staff draws for it`() {
        val tied = LineMark.Step(0, listOf(StepPart.Length(NoteLength.QUARTER), LineMark.BarLine, StepPart.Length(NoteLength.HALF)))
        assertEquals(29f + 10f + 58f, StaffGeometry.markWidth(tied))
        assertEquals(StaffGeometry.BAR_SLOT, StaffGeometry.markWidth(LineMark.BarLine))
        assertEquals(StaffGeometry.BREATH_SLOT, StaffGeometry.markWidth(LineMark.Breath(1)))
    }

    @Test
    fun `the knob's badge stops where the notes start and holds its animal`() {
        val badgeEnd = StaffGeometry.CORNER_BUTTON + StaffGeometry.KNOB_BADGE_OVERHANG
        assertTrue("no note may run under it", badgeEnd <= StaffGeometry.NOTES_CLIP)
        assertTrue(StaffGeometry.KNOB_BADGE_ANIMAL <= StaffGeometry.KNOB_BADGE - 2 * StaffGeometry.KNOB_BADGE_EDGE)
    }

    @Test
    fun `the knob, its shadow and its badge keep clear of the clef's tip at every size the staff is drawn`() {
        // At rest on a phone, on a tablet on its side (the smallest the staff is drawn) and while the tune runs.
        val tabletOnItsSide = TabletGeometry.sidewaysStaffScale(zoom = 10f)
        assertTrue(tabletOnItsSide < 1f)
        for (s in listOf(1f, tabletOnItsSide, 0.95f, RunningStaffScale)) {
            assertTrue("scale $s: ${StaffGeometry.knobClefAir(s)} dp between them", StaffGeometry.knobClefAir(s) >= 4.5f)
        }
        // Upright the knob stands in the stage's padding (16 dp, 3 of them its border): white is left over it.
        assertTrue(-StaffGeometry.knobTop(1f) <= 16f - 3f - 4f)
        // It does not grow with the running staff, and stays where it is at rest.
        assertEquals(StaffGeometry.knobTop(1f), StaffGeometry.knobTop(RunningStaffScale))
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

    @Test
    fun `the arrow beside the last note's bubble stays inside a running row's end`() {
        // The arrow stands right of the bubble, centred in the step's first beat; a one-beat step at the row's end leaves
        // only the end padding past it (the emulator showed it cut to a sliver with the 4 dp of before, 2026-11-14).
        val reach = StaffGeometry.BEAT_WIDTH / 2 + StaffGeometry.PITCH_ARROW_X + StaffGeometry.PITCH_ARROW_SIZE / 2
        assertTrue(reach <= StaffGeometry.BEAT_WIDTH + StaffGeometry.endPadding(running = true))
        assertEquals("writing keeps its end as it was", StaffGeometry.NOTES_END_PADDING, StaffGeometry.endPadding(running = false), 0f)
    }

    // ---- The walking animal on a tie (2026-11-16): Listen to me walks a tie as one note ----

    @Test
    fun `the animal walks onto the second note of a tie`() {
        val tie = listOf(TuneNote(60, NoteLength.QUARTER), TuneNote(60, NoteLength.QUARTER, joined = true))
        val apart = listOf(TuneNote(60, NoteLength.QUARTER), TuneNote(60, NoteLength.QUARTER))
        val animal = StaffAnimal(index = 0, emoji = "🐇", flash = { 0f }, waiting = false, halfBeat = 2)
        for ((tune, expectedStep) in listOf(tie to 1, apart to 0)) {
            val lineMarks = Bars.write(tune, TimeSignature.FOUR_FOUR)
            val starts = lineMarks.runningFold(0f) { x, mark -> x + StaffGeometry.markWidth(mark) }
            assertEquals(starts[expectedStep] + StaffGeometry.HEAD_X, animalX(animal, tune, lineMarks, starts)!!, 0.001f)
        }
    }

    // ---- The slur (2026-11-16): under the heads, tilting with them, clear of the heads and ties between, above the pills ----

    private val c1 = StaffGeometry.yOf(StaffNotes.positionOf(60))
    private val d1 = StaffGeometry.yOf(StaffNotes.positionOf(62))
    private val e1 = StaffGeometry.yOf(StaffNotes.positionOf(64))

    /** The outer curve's y at [t]. */
    private fun StaffGeometry.SlurCurve.y(t: Float): Float {
        val u = 1 - t
        return u * u * u * y0 + 3 * u * u * t * c1y + 3 * u * t * t * c2y + t * t * t * y1
    }

    private fun StaffGeometry.SlurCurve.x(t: Float): Float {
        val u = 1 - t
        return u * u * u * x0 + 3 * u * u * t * c1x + 3 * u * t * t * c2x + t * t * t * x1
    }

    /** The outer curve's y where it passes [x], sampled. */
    private fun lowestAt(c: StaffGeometry.SlurCurve, x: Float): Float =
        (0..1000).map { it / 1000f }.minBy { Math.abs(c.x(it) - x) }.let { c.y(it) }

    private fun lowestOf(c: StaffGeometry.SlurCurve): Float = (0..1000).maxOf { c.y(it / 1000f) }

    @Test
    fun `a slur between notes at different heights tilts with them`() {
        val c = StaffGeometry.slurCurve(14.5f, c1, 43.5f, d1, emptyList())
        assertEquals(c1 + StaffGeometry.SLUR_END_DROP, c.y0, 0.001f)
        assertEquals(d1 + StaffGeometry.SLUR_END_DROP, c.y1, 0.001f)
        assertEquals(14.5f + StaffGeometry.SLUR_END_INSET, c.x0, 0.001f)
        assertTrue("${c.c1y} > ${c.c2y}", c.c1y > c.c2y)
        assertTrue("the belly lies under the line from end to end", lowestAt(c, 29f) > (c.y0 + c.y1) / 2 + StaffGeometry.SLUR_BELLY / 2)
    }

    @Test
    fun `a slur clears the heads and ties between its ends`() {
        val middle = 29f to c1 + StaffGeometry.HEAD_RY
        val c = StaffGeometry.slurCurve(0f, e1, 58f, e1, listOf(middle))
        assertTrue("${lowestAt(c, 29f)}", lowestAt(c, 29f) >= middle.second + StaffGeometry.SLUR_CLEAR - 0.5f)
        val open = StaffGeometry.slurCurve(0f, e1, 58f, e1, emptyList())
        assertTrue("nothing to clear: the plain belly", lowestAt(open, 29f) < middle.second)
    }

    @Test
    fun `a slur under the lowest notes stays above the pills`() {
        val c = StaffGeometry.slurCurve(14.5f, c1, 43.5f, d1, emptyList())
        assertTrue("${lowestOf(c)}", lowestOf(c) + StaffGeometry.SLUR_THICKNESS < StaffGeometry.NAMES_TOP)
        val tie = 29f to c1 + (StaffGeometry.TIE_START + StaffGeometry.TIE_CONTROL) / 2 + StaffGeometry.TIE_WIDTH / 2
        val over = StaffGeometry.slurCurve(14.5f, c1, 72.5f, c1, listOf(tie))
        assertTrue("${lowestOf(over)}", lowestOf(over) < StaffGeometry.NAMES_TOP)
    }

    @Test
    fun `the waiting arc fits the room after the newest note`() {
        // The staff scrolls its newest note to the end: the arc after it has the rest of its beat and the end padding.
        val room = StaffGeometry.BEAT_WIDTH - StaffGeometry.HEAD_X + StaffGeometry.NOTES_END_PADDING
        assertTrue("${StaffGeometry.WAITING_ARC_LENGTH} in $room", StaffGeometry.SLUR_END_INSET + StaffGeometry.WAITING_ARC_LENGTH <= room)
    }

    @Test
    fun `a steep slur clearing a tie under c1 never reaches the pills`() {
        // The final review of 2026-11-16: c¹ tied to c¹ slurred to c³ reached 194.7 dp, the cap checking only its middle.
        val c3 = StaffGeometry.yOf(StaffNotes.positionOf(84))
        val tie = 29f to c1 + (StaffGeometry.TIE_START + StaffGeometry.TIE_CONTROL) / 2 + StaffGeometry.TIE_WIDTH / 2
        val steep = StaffGeometry.slurCurve(14.5f, c1, 72.5f, c3, listOf(tie))
        // The cap samples the curve: a tenth of a dp past the air between samples is nothing against 2 dp of it.
        assertTrue("${lowestOf(steep)}", lowestOf(steep) <= StaffGeometry.NAMES_TOP - StaffGeometry.SLUR_PILL_AIR + 0.1f)
    }
}
