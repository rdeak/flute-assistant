package hr.bertlab.fluteassistant.ui.flute

import hr.bertlab.fluteassistant.domain.flute.LineMark
import hr.bertlab.fluteassistant.domain.flute.NoteLength
import hr.bertlab.fluteassistant.domain.flute.StepPart
import hr.bertlab.fluteassistant.domain.notes.StaffNotes

/**
 * Where [TuneStaff] draws everything, in dp. The canvas of 2026-11-02 drew the staff with lines
 * 24 apart and the note's letter inside each head; at the user's request (the same evening) the
 * heads lost their letters and every stem points up, the names and lengths going to the line of
 * pills under each note ([NAMES_TOP]), so the staff is drawn at three quarters
 * of the canvas's size ([LINE_GAP] 18, a step 9; chosen while a name bubble briefly sat there) to
 * leave room for that line and for c³'s stem, 87 dp above the top line. Spacing along the
 * staff stays the canvas's 29 dp a beat. c³'s stem may reach [OVERFLOW_TOP] above the box, into
 * the stage's padding, and c¹'s ring and the tie under it [OVERFLOW_BOTTOM] below it, into the gap
 * above the line of pills, rather than make the staff that much taller; both stay inside what the
 * scrolling row lets a child draw beyond it (30 dp). Kept apart from the drawing so a unit test can
 * check that no note, at any height and length, lit or tied, goes further.
 */
internal object StaffGeometry {
    /** One note step: a line to the next space. */
    const val STEP = 9f
    const val LINE_GAP = STEP * 2

    /** The top line (f²), with room above it for the high notes' stems (c³'s reaches 87 dp up). */
    const val TOP_LINE_Y = 75f

    /** The bottom line (e¹); the others lie [LINE_GAP] apart between them. */
    const val BOTTOM_LINE_Y = TOP_LINE_Y + LINE_GAP * 4
    const val MIDDLE_LINE_Y = TOP_LINE_Y + LINE_GAP * 2
    const val LINE_WIDTH = 2f

    /** The staff's height: down to c¹, two steps under the bottom line, and its ring. */
    const val HEIGHT = 175f

    /** How far c³'s stem (and b²'s, a little) may reach above the box: within the stage's 16 dp padding, clear of its 3 dp border. */
    const val OVERFLOW_TOP = 12f

    /** How far c¹'s ring and the tie under it may reach below the box: within the gap above the names. */
    const val OVERFLOW_BOTTOM = 8f

    /**
     * The names under the staff (the user, 2026-11-02: "align note names with the notes, left to
     * right"): a pill per piece in the same room as its note on the staff, [NAMES_GAP] under it, so
     * notes and names scroll as one; [TOTAL_HEIGHT] is the staff and its names together.
     */
    const val NAMES_GAP = 14f
    const val NAMES_TOP = HEIGHT + NAMES_GAP
    const val PILL_HEIGHT = 36f
    const val PILL_GAP = 3f
    const val PILL_RADIUS = 13f

    /**
     * The marks row under the names (since 2026-11-04, "Your turn"): under each name, in the
     * first beat's room so it stands under the head, the star a note earned, or the note heard
     * for a wrong one as a bubble until the next note is judged. [TOTAL_HEIGHT] is the staff, its
     * names and its marks: 16 more than before the row, the air under the pills used.
     */
    const val MARKS_GAP = 6f
    const val MARKS_TOP = NAMES_TOP + PILL_HEIGHT + MARKS_GAP
    const val MARKS_HEIGHT = 26f
    const val MARK_SIZE = 20f
    const val TOTAL_HEIGHT = MARKS_TOP + MARKS_HEIGHT

    /** A tie between two pills of one note: [PILL_TIE_GAP] under them, dipping [PILL_TIE_DIP]. */
    const val PILL_TIE_GAP = 4f
    const val PILL_TIE_DIP = 5f

    /** Where the staff's [position] (0 = c¹, [StaffNotes.positionOf]) sits. */
    fun yOf(position: Int): Float = BOTTOM_LINE_Y + (StaffNotes.LINE_POSITIONS.first() - position) * STEP

    /** Whether [position] is on a line (a ledger line too) rather than in a space: a dot moves up off it. */
    fun onLine(position: Int): Boolean = position % 2 == 0

    /** The treble clef's unit (one space), drawn from the top line as the canvas placed it. */
    const val CLEF_X = 4f
    const val CLEF_UNIT = LINE_GAP
    const val CLEF_STROKE = 0.14f
    const val CLEF_ALPHA = 0.45f

    /**
     * The lowest control point of `drawTrebleClef`'s hook (5.12 units under the top line): a bound
     * the clef's stroke never passes, so the book button may stand under it.
     */
    const val CLEF_BOTTOM = TOP_LINE_Y + 5.12f * CLEF_UNIT

    /**
     * The buttons in the staff's left corner (since 2026-11-04): the round speed knob at the top
     * and, under the clef, beside the names row, the open book as a little book with its sweet,
     * [BOOK_BUTTON_HEIGHT] tall. Both are [CORNER_BUTTON] across, the book's page edges included
     * ([NOTES_CLIP] is no less, so no note runs under either), with a [CORNER_SHADOW] shadow.
     */
    const val CORNER_BUTTON = 54f
    const val CORNER_SHADOW = 5f

    /**
     * The knob's animal while the tune runs (since the evening of 2026-11-04, the user's pick "A"
     * from a moving true-size mockup; it walked the marks row before): it leaves the knob and walks
     * the staff at the knob's height, its middle [ANIMAL_Y] from the top of the staff's box at
     * every scale, as the knob does not grow with the running staff; [ANIMAL_SIZE] tall at rest
     * scale (the running staff draws it a third bigger). Its hop (8 dp) keeps it inside the box,
     * and it stands over the stems of the notes from f² up, never over a head but c³'s top edge.
     */
    const val ANIMAL_Y = CORNER_BUTTON / 2
    const val ANIMAL_SIZE = 24f

    /** The book button's height: a book stands taller than it is wide, and the cell under the clef has 90 dp of room. */
    const val BOOK_BUTTON_HEIGHT = 64f

    /** Where the book button's top is at [scale] (1 at rest, [RunningStaffScale] while the tune runs): centred on the names' pills. */
    fun bookButtonTop(scale: Float): Float = (NAMES_TOP + PILL_HEIGHT / 2) * scale - BOOK_BUTTON_HEIGHT / 2

    /** The time signature's figures: centred at this x, each across two spaces of the staff. */
    const val SIGNATURE_X = 48f
    const val SIGNATURE_TOP_Y = TOP_LINE_Y + LINE_GAP
    const val SIGNATURE_BOTTOM_Y = TOP_LINE_Y + LINE_GAP * 3
    const val SIGNATURE_SIZE = 24f

    /** Where the first step starts; the notes scroll in from [NOTES_CLIP] so a ring is never cut. */
    const val NOTES_START = 62f
    const val NOTES_CLIP = 57f
    const val NOTES_END_PADDING = 4f

    /** Space per beat, as the canvas drew it; an eighth gets a quarter's, as half of it would crowd the heads. */
    const val BEAT_WIDTH = 29f

    fun slotWidth(length: NoteLength): Float = maxOf(BEAT_WIDTH * length.halfBeats / 2f, BEAT_WIDTH)

    /** A bar line's room, the line in its middle. */
    const val BAR_SLOT = 10f
    const val BAR_LINE_WIDTH = 2f

    /** A breath's room: no time, but enough that its face clears the stems and heads either side. */
    const val BREATH_SLOT = 18f

    /** The room one piece of a step takes: its length's, or a bar line's inside a tied note. */
    fun partWidth(part: StepPart): Float = when (part) {
        is StepPart.Length -> slotWidth(part.length)
        LineMark.BarLine -> BAR_SLOT
    }

    /** The room one mark of the line takes: a step its pieces', a bar line [BAR_SLOT], a breath [BREATH_SLOT]. */
    fun markWidth(mark: LineMark): Float = when (mark) {
        is LineMark.Step -> mark.parts.fold(0f) { sum, part -> sum + partWidth(part) }
        is LineMark.Breath -> BREATH_SLOT
        LineMark.BarLine -> BAR_SLOT
    }

    /**
     * Where the running animal stands, from the left of a step written as [parts], [halfBeat] half
     * beats into it: over the head at 0, then half a beat's room ([BEAT_WIDTH] / 2) further for each
     * half beat, as each beat of a piece has its own room, so on every click it lands on a new place
     * (a half note's second beat 29 dp on, a quarter's second click in 6/8 at the edge of its room),
     * passing over a bar line inside a tied note. An eighth's room is a quarter's, but it lasts half a
     * beat, so it only ever has its head. Past the step's end it stays on its last piece's head.
     */
    fun beatX(parts: List<StepPart>, halfBeat: Int): Float {
        var x = 0f
        var left = halfBeat
        var lastHead = HEAD_X
        for (part in parts) {
            when (part) {
                LineMark.BarLine -> x += BAR_SLOT
                is StepPart.Length -> {
                    if (left < part.length.halfBeats) return x + HEAD_X + left * BEAT_WIDTH / 2
                    lastHead = x + HEAD_X
                    left -= part.length.halfBeats
                    x += slotWidth(part.length)
                }
            }
        }
        return lastHead
    }

    /** A note head's centre from the start of its slot. */
    const val HEAD_X = 14.5f

    /**
     * A head as printed music draws it: an oval about a space tall, tilted [HEAD_TILT] degrees, in the
     * note's letter colour (the user, 2026-11-02: each note in its name's colour, its shape by the rules
     * of notation), filled for an eighth and a quarter, a ring [OPEN_RING] thick for a half and a
     * dotted half, a wider ring for a whole note; a thin [HEAD_EDGE] in the outline colour keeps a
     * yellow e on white, or on the yellow ring of the lit note, in sight.
     */
    const val HEAD_RX = 9.6f
    const val HEAD_RY = 7.6f
    const val HEAD_TILT = -20f
    const val HEAD_EDGE = 1.25f
    const val OPEN_RING = 3f
    const val WHOLE_RX = 11f
    const val WHOLE_RY = 8f

    /** The stem, always up on the right of the head, in the note's colour: from inside the head to [STEM_REACH] above its centre. */
    const val STEM_X = 8.5f
    const val STEM_START = 1f
    const val STEM_REACH = 50f
    const val STEM_WIDTH = 2f

    /** Half the length of a ledger line. */
    const val LEDGER_HALF = 14.5f

    /** A dotted half's dot, right of the head. */
    const val DOT_X = 16.5f
    const val DOT_RADIUS = 2.75f

    /** The yellow behind the lit note's head, and behind a lit rest. */
    const val LIT_RADIUS = 15f
    const val REST_LIT_RADIUS = 16.5f

    /** The soft band behind the step still growing under a finger: its notes on the staff and its names under it. */
    const val BAND_TOP = TOP_LINE_Y - 6f
    const val BAND_BOTTOM = NAMES_TOP + PILL_HEIGHT + 6f
    const val BAND_RADIUS = 9f
    const val BAND_ALPHA = 0.35f

    /** A tie, always under the heads: from [TIE_INSET] inside each head, [TIE_START] below its centre, curving to [TIE_CONTROL]. */
    const val TIE_INSET = 4.5f
    const val TIE_START = 11f
    const val TIE_CONTROL = 22f
    const val TIE_WIDTH = 2f

    /** The 😮‍💨 over a breath: where its centre sits, and how big. */
    const val BREATH_FACE_Y = TOP_LINE_Y - 14f
    const val BREATH_FACE_SIZE = 14f

    /** The dashed hint on the empty staff, across its middle line. */
    const val HINT_START = NOTES_START + 8f
    const val HINT_END_PADDING = 10f
    const val HINT_HEIGHT = 44f
    const val HINT_TOP = MIDDLE_LINE_Y - HINT_HEIGHT / 2
    const val HINT_RADIUS = 22f

    /**
     * The highest and the lowest y anything drawn for a note at [position] reaches, lit and tied:
     * its stem, yellow ring, head, ledger lines and the tie under it.
     */
    fun noteExtent(position: Int): ClosedFloatingPointRange<Float> {
        val y = yOf(position)
        val ring = maxOf(LIT_RADIUS, WHOLE_RY + HEAD_EDGE)
        val stem = STEM_REACH + STEM_WIDTH / 2
        // A quadratic tie dips halfway between its ends and its control point.
        val tie = (TIE_START + TIE_CONTROL) / 2 + TIE_WIDTH / 2
        val ledgers = StaffNotes.ledgerLinesFor(position).map(::yOf)
        return minOf(y - maxOf(ring, stem), ledgers.minOrNull() ?: y)..maxOf(y + maxOf(ring, tie), ledgers.maxOrNull() ?: y)
    }
}
