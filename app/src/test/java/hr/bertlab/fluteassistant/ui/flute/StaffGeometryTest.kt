// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.flute

import hr.bertlab.fluteassistant.domain.flute.Bars
import hr.bertlab.fluteassistant.domain.flute.FluteNotes
import hr.bertlab.fluteassistant.domain.flute.Joins
import hr.bertlab.fluteassistant.domain.flute.LineMark
import hr.bertlab.fluteassistant.domain.flute.NoteLength
import hr.bertlab.fluteassistant.domain.flute.StepPart
import hr.bertlab.fluteassistant.domain.flute.TuneNote
import hr.bertlab.fluteassistant.domain.metronome.TimeSignature
import hr.bertlab.fluteassistant.domain.notes.StaffNotes
import hr.bertlab.fluteassistant.domain.tuner.Accidental
import hr.bertlab.fluteassistant.ui.common.MusicSymbols
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
        for (position in 0 until StaffNotes.ALL.size) {
            val extent = StaffGeometry.noteExtent(position)
            assertTrue("position $position reaches ${extent.start}", extent.start >= -StaffGeometry.OVERFLOW_TOP)
            assertTrue("position $position reaches ${extent.endInclusive}", extent.endInclusive <= StaffGeometry.HEIGHT + StaffGeometry.OVERFLOW_BOTTOM)
        }
        // c³'s stem uses the room above all the way: the box is no taller than it needs to be.
        assertEquals(-StaffGeometry.OVERFLOW_TOP, StaffGeometry.noteExtent(StaffNotes.ALL.size - 1).start)
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
        for (position in 0 until StaffNotes.ALL.size - 1) {
            assertTrue("position $position", headTop(position) >= StaffGeometry.ANIMAL_Y + half)
        }
        assertTrue(headTop(StaffNotes.ALL.size - 1) >= StaffGeometry.ANIMAL_Y + half - 4f)
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

    // ---- Signs before the notes (2026-11-16, sharps and flats): ten dp more room, the sign inside it ----

    @Test
    fun `a step with a sign takes ten dp more room`() {
        val plain = LineMark.Step(0, listOf(StepPart.Length(NoteLength.QUARTER)))
        assertEquals(StaffGeometry.markWidth(plain) + 10f, StaffGeometry.markWidth(plain.copy(sign = Accidental.SHARP)), 0.001f)
        assertEquals(StaffGeometry.markWidth(plain) + 10f, StaffGeometry.markWidth(plain.copy(sign = Accidental.NATURAL)), 0.001f)
        assertEquals(0f, StaffGeometry.signRoom(plain), 0.001f)
    }

    @Test
    fun `the sign lies inside its own step's room`() =
        assertTrue(StaffGeometry.ACCIDENTAL_ROOM + StaffGeometry.HEAD_X - StaffGeometry.SIGN_X - StaffGeometry.SIGN_HALF_WIDTH >= 0f)

    @Test
    fun `the animal stands over a signed note's head, after its room`() {
        val tune = listOf(TuneNote(65, NoteLength.QUARTER), TuneNote(66, NoteLength.QUARTER))
        val lineMarks = Bars.write(tune, TimeSignature.FOUR_FOUR)
        val starts = lineMarks.runningFold(0f) { x, mark -> x + StaffGeometry.markWidth(mark) }
        val animal = StaffAnimal(index = 1, emoji = "🐇", flash = { 0f }, waiting = false, halfBeat = 0)
        assertEquals(starts[1] + 10f + StaffGeometry.HEAD_X, animalX(animal, tune, lineMarks, starts)!!, 0.001f)
    }

    /**
     * The slur layer's places (the final review, 2026-11-16): a signed note split at a bar line has its sign's room once,
     * before its first head, as `drawStep` draws it; its later heads stand where an unsigned split note's do, moved by the
     * room alone. Counted twice, a slur or the waiting arc ending on its second head hung 10 dp past it.
     */
    @Test
    fun `a split signed note's heads in the slur layer stand where they are drawn`() {
        fun headsOf(last: Int): List<Float> {
            val tune = listOf(TuneNote(60, NoteLength.QUARTER), TuneNote(62, NoteLength.QUARTER), TuneNote(64, NoteLength.QUARTER), TuneNote(last, NoteLength.HALF))
            val lineMarks = Bars.write(tune, TimeSignature.FOUR_FOUR)
            val starts = lineMarks.runningFold(0f) { x, mark -> x + StaffGeometry.markWidth(mark) }
            return stepPlaces(lineMarks, starts).getValue(3).heads
        }
        val plain = headsOf(65)
        val signed = headsOf(66)
        assertEquals(2, signed.size)
        assertEquals(plain[0] + StaffGeometry.ACCIDENTAL_ROOM, signed[0], 0.001f)
        assertEquals(plain[1] + StaffGeometry.ACCIDENTAL_ROOM, signed[1], 0.001f)
    }

    /** A ledger line beside a sign starts clear of it (the final review): it ran 3 dp into C♯4's sharp. */
    @Test
    fun `a ledger line beside a sign starts clear of it, and still shows left of the head`() {
        assertEquals(StaffGeometry.LEDGER_HALF, StaffGeometry.ledgerLeft(signed = false), 0.001f)
        assertTrue(StaffGeometry.ledgerLeft(signed = true) <= StaffGeometry.SIGN_X - StaffGeometry.SIGN_HALF_WIDTH - 1f)
        assertTrue(StaffGeometry.ledgerLeft(signed = true) > StaffGeometry.HEAD_RX)
    }

    /**
     * A slur passes under the signs of the notes it reaches, as under their heads (the final review's look, 2026-11-16: it
     * ran through the foot of C♯4's sharp): the sign's ink reaches [StaffGeometry.SIGN_BELOW] under its head's centre.
     */
    private fun assertTheSlurClearsTheSign(vararg midis: Int, signed: Int) {
        val tune = midis.mapIndexed { k, midi -> TuneNote(midi, NoteLength.EIGHTH, joined = k > 0) }
        val lineMarks = Bars.write(tune, TimeSignature.FOUR_FOUR)
        val starts = lineMarks.runningFold(0f) { x, mark -> x + StaffGeometry.markWidth(mark) }
        val places = stepPlaces(lineMarks, starts)
        val curve = slurFor(Joins.arcs(tune).single(), tune, places)
        val sign = places.getValue(signed).heads.first() - StaffGeometry.SIGN_X
        val foot = StaffGeometry.yOf(FluteNotes.positionOf(midis[signed])) + StaffGeometry.SIGN_BELOW
        for (x in listOf(sign - 3f, sign, sign + 3f)) {
            assertTrue("under the sign at $x: ${lowestAt(curve, x)} against its foot $foot", lowestAt(curve, x) >= foot + 1f)
        }
    }

    @Test
    fun `a slur passes under the sign of a note inside it`() = assertTheSlurClearsTheSign(64, 66, 67, signed = 1)

    @Test
    fun `a slur passes under the sign of the note it ends on`() = assertTheSlurClearsTheSign(71, 73, signed = 1)

    @Test
    fun `a slur clears the sign of every black note, inside it, at its end and coming down onto it`() {
        val misses = mutableListOf<String>()
        for (black in listOf(61, 63, 66, 68, 70, 73, 75, 78, 80, 82)) {
            val runs = listOf("inside" to intArrayOf(black - 1, black, black + 1), "at its end" to intArrayOf(black - 1, black), "coming down" to intArrayOf(black + 3, black))
            for ((how, midis) in runs) {
                try {
                    assertTheSlurClearsTheSign(*midis, signed = 1)
                } catch (e: AssertionError) {
                    misses += "$how $black: ${e.message}"
                }
            }
        }
        assertTrue(misses.joinToString("\n"), misses.isEmpty())
    }


    // ---- The room over the staff for a tune above c³ (2026-11-18): every released number stands, the box gets taller ----

    @Test
    fun `the room over the staff fits the tune`() {
        fun room(vararg midi: Int) = StaffGeometry.roomOver(midi.map { TuneNote(it, NoteLength.QUARTER) })
        assertEquals(0f, StaffGeometry.roomOver(emptyList()))
        assertEquals(0f, room(60, 84))
        assertEquals(9f, room(86))
        assertEquals(18f, room(88))
        assertEquals("b3 (B♭6) on h3's place", 54f, room(94))
        assertEquals(54f, room(95))
        assertEquals(63f, room(72, 96))
        assertEquals(63f, StaffGeometry.HIGH_ROOM)
    }

    @Test
    fun `every note up to c4 stays inside the box with its own room`() {
        for (position in 0..21) {
            val room = maxOf(0, position - 14) * StaffGeometry.STEP
            val extent = StaffGeometry.noteExtent(position)
            assertTrue("position $position reaches ${extent.start + room}", extent.start + room >= -StaffGeometry.OVERFLOW_TOP)
        }
        // c⁴'s stem, with all the room, reaches where c³'s does with none.
        assertEquals(StaffGeometry.noteExtent(14).start, StaffGeometry.noteExtent(21).start + StaffGeometry.HIGH_ROOM)
    }

    @Test
    fun `the resting staff keeps the released height for every highest note`() {
        for (steps in 0..7) {
            val room = steps * StaffGeometry.STEP
            assertEquals("room $room", StaffGeometry.TOTAL_HEIGHT, (StaffGeometry.TOTAL_HEIGHT + room) * StaffGeometry.restingScale(room), 0.01f)
        }
        assertEquals(1f, StaffGeometry.restingScale(0f))
        assertEquals("up to d3, 3% smaller", 0.966f, StaffGeometry.restingScale(9f), 0.001f)
        assertEquals("up to g3, 12%", 0.877f, StaffGeometry.restingScale(36f), 0.001f)
        assertEquals("up to c4, 20%", 0.803f, StaffGeometry.restingScale(63f), 0.001f)
    }

    @Test
    fun `the staff's box takes the tab's spare height, up to the room c4 needs`() {
        assertEquals(257f, StaffGeometry.uprightBox(-20f))
        assertEquals(257f, StaffGeometry.uprightBox(0f))
        assertEquals("a Galaxy A55: 653 of tab, 618 needed", 292f, StaffGeometry.uprightBox(35f))
        assertEquals(320f, StaffGeometry.uprightBox(63f))
        assertEquals(320f, StaffGeometry.uprightBox(150f))
    }

    @Test
    fun `a taller box draws a high tune at full size while it has the room`() {
        // The A55's box, 292 dp (the released 257 and 35 to spare): up to f3 at full size, c4 9% smaller.
        assertEquals(1f, StaffGeometry.restingScale(0f, box = 292f))
        assertEquals(1f, StaffGeometry.restingScale(27f, box = 292f))
        assertEquals("up to g3 needs 293: almost", 0.997f, StaffGeometry.restingScale(36f, box = 292f), 0.001f)
        assertEquals(0.9125f, StaffGeometry.restingScale(63f, box = 292f), 0.0001f)
        // A box with all the room, on a taller phone: every tune at full size.
        assertEquals(1f, StaffGeometry.restingScale(63f, box = StaffGeometry.TOTAL_HEIGHT + StaffGeometry.HIGH_ROOM))
    }

    @Test
    fun `a tune up to c3 runs as before, and one above it no taller`() {
        assertEquals(RunningStaffScale, StaffGeometry.runningScale(0f))
        for (steps in 0..7) {
            val room = steps * StaffGeometry.STEP
            assertTrue("room $room", (StaffGeometry.TOTAL_HEIGHT + room) * StaffGeometry.runningScale(room) <= StaffGeometry.TOTAL_HEIGHT * RunningStaffScale + 0.01f)
        }
        assertEquals(1.07f, StaffGeometry.runningScale(63f), 0.005f)
    }

    @Test
    fun `the running animal covers at most four dp of the top note's head at every scale, and its hop stays in the box`() {
        assertEquals("as before over a tune up to c3", StaffGeometry.ANIMAL_Y, StaffGeometry.animalY(RunningStaffScale))
        // Every scale a running staff takes, upright or on its side. The highest note of a box with its room stands where c³ does.
        val topNote = StaffGeometry.yOf(14) - StaffGeometry.HEAD_RY - 0.25f - StaffGeometry.HEAD_EDGE
        var step = 0
        while (0.9f + step * 0.01f <= RunningStaffScale + 0.001f) {
            val s = 0.9f + step * 0.01f
            val half = StaffGeometry.ANIMAL_SIZE * s / 2
            val y = StaffGeometry.animalY(s)
            assertTrue("scale $s: its hop leaves the box at $y", y - half - 8f >= -0.01f)
            assertTrue("scale $s: it covers ${y + half - topNote * s} dp of the top head", y + half - topNote * s <= 4f + 0.01f)
            step++
        }
        assertEquals("over a tune up to c4, as high as its hop allows", 20.86f, StaffGeometry.animalY(StaffGeometry.runningScale(63f)), 0.05f)
    }

    @Test
    fun `the knob and the book keep their corner with room over the staff`() {
        // Writing a tune up to c⁴ upright and on its side, running it, and writing one up to d³.
        val drawn = listOf(
            StaffGeometry.restingScale(63f) to 63f, 0.956f to 63f, StaffGeometry.runningScale(63f) to 63f, StaffGeometry.restingScale(9f) to 9f,
        )
        for ((s, room) in drawn) {
            assertTrue("scale $s room $room: ${StaffGeometry.knobClefAir(s, room)} dp to the clef", StaffGeometry.knobClefAir(s, room) >= 4.5f)
            assertEquals("the knob stays where it is", StaffGeometry.knobTop(1f), StaffGeometry.knobTop(s, room))
            val top = StaffGeometry.bookButtonTop(s, room)
            // Under the clef's lowest bound (its ink ends above it), inside the box.
            assertTrue("scale $s room $room: book at $top", top >= (StaffGeometry.CLEF_BOTTOM + room) * s - 0.5f)
            assertTrue("scale $s room $room", top + StaffGeometry.BOOK_BUTTON_HEIGHT + StaffGeometry.CORNER_SHADOW <= (StaffGeometry.TOTAL_HEIGHT + room) * s)
        }
    }

    // ---- The staff's symbols from the music font (spec 2026-11-24-flute-staff-music-symbols-design.md, part 3) ----

    @Test
    fun `the heads, stems and dots are the font's at their shares`() {
        assertEquals(9.558f, StaffGeometry.HEAD_RX, 0.01f)
        assertEquals(8.1f, StaffGeometry.HEAD_RY, 0.01f)
        assertEquals(10.634f, StaffGeometry.WHOLE_RX, 0.01f)
        assertEquals(6.3f, StaffGeometry.WHOLE_RY, 0.01f)
        assertEquals(8.558f, StaffGeometry.STEM_X, 0.01f)
        assertEquals(3.6f, StaffGeometry.DOT_RADIUS, 0.01f)
    }

    @Test
    fun `the clef is the font's at its share, its tip, foot and right edge`() {
        assertEquals(26.21f, StaffGeometry.CLEF_TIP_X, 0.01f)
        assertEquals(65.76f, StaffGeometry.CLEF_TIP_Y, 0.01f)
        assertEquals(166.9f, StaffGeometry.CLEF_BOTTOM, 0.01f)
        assertEquals(38.65f, StaffGeometry.CLEF_RIGHT, 0.01f)
    }

    /**
     * The figures' ink must not reach under the notes' row, which starts at NOTES_CLIP, nor meet the clef: they stand in
     * the middle between, with air each side (a note scrolled to the row's left edge sat 0.2 dp from the 4 at x 50).
     */
    @Test
    fun `the time signature's figures stand clear of the clef and of the notes' row`() {
        val notesAir = StaffGeometry.NOTES_CLIP - (StaffGeometry.SIGNATURE_X + StaffGeometry.SIGNATURE_FIGURE_HALF)
        val clefAir = StaffGeometry.SIGNATURE_X - StaffGeometry.SIGNATURE_FIGURE_HALF - StaffGeometry.CLEF_RIGHT
        assertTrue("air before the notes' row: $notesAir", notesAir >= 2f)
        assertTrue("air after the clef: $clefAir", clefAir >= 2f)
        assertEquals("in the middle", clefAir, notesAir, 0.01f)
    }

    private fun signHalfWidths() = listOf(MusicSymbols.SHARP_WIDTH, MusicSymbols.FLAT_WIDTH, MusicSymbols.NATURAL_WIDTH)
        .map { it * StaffGeometry.LINE_GAP * StaffGeometry.SIGN_SHARE / 2 }

    /**
     * The flag hangs right of its stem no further than [StaffGeometry.FLAG_RIGHT] from its head, and never above the stem's
     * top, so checking across is enough at every interval: the next head stands a beat on, a signed one's sign before it.
     */
    @Test
    fun `an eighth's flag keeps clear of the next step`() {
        val nextHead = StaffGeometry.BEAT_WIDTH - StaffGeometry.HEAD_RX - StaffGeometry.HEAD_EDGE / 2
        assertTrue("to the next head: ${nextHead - StaffGeometry.FLAG_RIGHT}", StaffGeometry.FLAG_RIGHT + 2.5f <= nextHead)
        signHalfWidths().forEach { half ->
            val nextSign = StaffGeometry.BEAT_WIDTH + StaffGeometry.ACCIDENTAL_ROOM - StaffGeometry.SIGN_X - half
            assertTrue("to a sign $half wide each side: ${nextSign - StaffGeometry.FLAG_RIGHT}", StaffGeometry.FLAG_RIGHT + 0.5f <= nextSign)
        }
    }

    /** Boxes: the shapes keep 1.1 dp. */
    @Test
    fun `a whole head keeps clear of its own sign`() {
        signHalfWidths().forEach { half ->
            assertTrue("a sign $half wide each side", StaffGeometry.SIGN_X - half - StaffGeometry.WHOLE_RX >= 0.9f)
        }
    }

    @Test
    fun `a sign fits today's half width and its foot is counted`() {
        signHalfWidths().forEach { assertTrue("$it", it <= StaffGeometry.SIGN_HALF_WIDTH) }
        val share = StaffGeometry.LINE_GAP * StaffGeometry.SIGN_SHARE
        assertTrue(StaffGeometry.SIGN_BELOW >= MusicSymbols.SHARP_DROP * share)
        assertTrue(StaffGeometry.SIGN_BELOW >= MusicSymbols.NATURAL_DROP * share)
        assertTrue(StaffGeometry.SIGN_BELOW >= MusicSymbols.FLAT_DROP * share)
    }

    /** The half and the whole rest's block about the width drawn before the font (15 dp), and a dotted half rest's dot clear of it. */
    @Test
    fun `a half rest is today's width and its dot keeps clear of it`() {
        val block = MusicSymbols.REST_BLOCK_WIDTH * StaffGeometry.LINE_GAP * StaffGeometry.REST_BLOCK_SHARE
        assertEquals("the block's width", 15f, block, 1f)
        val gap = StaffGeometry.REST_DOT_X - StaffGeometry.DOT_RADIUS - block / 2
        assertTrue("the dot is $gap from the block", gap >= 1.5f)
    }
}
