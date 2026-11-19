// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.flute

import hr.bertlab.fluteassistant.domain.flute.FluteNotes
import hr.bertlab.fluteassistant.domain.flute.LineMark
import hr.bertlab.fluteassistant.domain.flute.NoteLength
import hr.bertlab.fluteassistant.domain.flute.StepPart
import hr.bertlab.fluteassistant.domain.flute.TuneItem
import hr.bertlab.fluteassistant.domain.flute.TuneNote
import hr.bertlab.fluteassistant.domain.notes.StaffNotes
import hr.bertlab.fluteassistant.ui.common.TREBLE_CLEF_TIP_X
import hr.bertlab.fluteassistant.ui.common.TREBLE_CLEF_TIP_Y
import kotlin.math.hypot

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
 *
 * Since 2026-11-18 (spec `2026-11-18-flute-every-octave-design.md`, Revision 3) the flute writes up to c⁴, five ledger
 * lines over the staff. Every number here stands: a tune that goes above c³ needs room over the staff ([roomOver], up
 * to [HIGH_ROOM]), and the staff is drawn that much lower in a box that much taller. Upright, while the child writes,
 * the box is this height and what the tab has to spare ([uprightBox]) and never changes; the staff stands at its
 * bottom, at full size while the box has the room the tune needs ([restingScale]). While the tune runs it has only the
 * room it needs and is no taller than before ([runningScale]): a tune with no note above c³ runs exactly as before.
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
    /** The arrow beside the live bubble of a note sounding off its pitch (2026-11-14), smaller than a mark. */
    const val PITCH_ARROW_SIZE = 12f
    /** The arrow's middle, right of the bubble's middle: just past its edge, overlapping it by a dp as in the mockup. */
    const val PITCH_ARROW_X = (MARKS_HEIGHT + PITCH_ARROW_SIZE) / 2 - 1
    const val TOTAL_HEIGHT = MARKS_TOP + MARKS_HEIGHT

    /** The room c⁴ needs over the box, which holds notes up to c³ (since 2026-11-18): seven steps, from c³'s place (14) to c⁴'s (21). */
    const val HIGH_ROOM = 63f

    /** c³'s place (c¹ = 0): the highest note the box holds with no room over it. */
    private const val C3_PLACE = 14

    /**
     * The room [tune] needs over the box, in dp at rest: a step ([STEP]) for each place its highest note stands over
     * c³ (a black note counts by its letter's place), [HIGH_ROOM] at c⁴; none with no note above c³. With its room the
     * highest note stands where c³ stands with none.
     */
    fun roomOver(tune: List<TuneItem>): Float {
        val top = tune.filterIsInstance<TuneNote>().maxOfOrNull { FluteNotes.positionOf(it.midi) } ?: return 0f
        return ((top - C3_PLACE) * STEP).coerceIn(0f, HIGH_ROOM)
    }

    /**
     * The staff's box upright while the child writes (since 2026-11-18): [TOTAL_HEIGHT] and what the tab's height has to
     * [spare] over its least, up to [HIGH_ROOM]: 300 on a Galaxy A55, whose tab has 43 dp to spare, 320 from 63 dp.
     */
    fun uprightBox(spare: Float): Float = TOTAL_HEIGHT + spare.coerceIn(0f, HIGH_ROOM)

    /**
     * The scale of the staff while the child writes, upright, when the tune needs [room] over it and its box is [box]
     * tall (the released [TOTAL_HEIGHT] and what the tab's height has to spare, up to [HIGH_ROOM]): 1 while the box has
     * the room, else what fits the tune in it, so the box and the keys under it never move.
     */
    fun restingScale(room: Float, box: Float = TOTAL_HEIGHT): Float = minOf(1f, box / (TOTAL_HEIGHT + room))

    /** The scale of the running staff upright with [room] over it: [RunningStaffScale], or less, so it is never taller than a tune up to c³ runs. */
    fun runningScale(room: Float): Float = minOf(RunningStaffScale, TOTAL_HEIGHT * RunningStaffScale / (TOTAL_HEIGHT + room))

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

    /** The clef's tip, its highest point and the nearest it comes to the speed knob: a space over the top line. */
    const val CLEF_TIP_X = CLEF_X + TREBLE_CLEF_TIP_X * CLEF_UNIT
    const val CLEF_TIP_Y = TOP_LINE_Y + TREBLE_CLEF_TIP_Y * CLEF_UNIT

    /**
     * The buttons in the staff's left corner (since 2026-11-04): the round speed knob at the top
     * and, under the clef, beside the names row, the open book as a little book with its sweet,
     * [BOOK_BUTTON_HEIGHT] tall. Both are [CORNER_BUTTON] across, the book's page edges included
     * ([NOTES_CLIP] is no less, so no note runs under either), with a [CORNER_SHADOW] shadow.
     */
    const val CORNER_BUTTON = 54f
    const val CORNER_SHADOW = 5f

    /**
     * What stands on the speed knob (since the evening of 2026-11-04, the user's pick "E" from
     * true-size mockups): the tab bar's drum, [KNOB_DRUM] tall, [KNOB_DRUM_SHIFT] up and left of the
     * disc's middle to leave the badge its corner, and the speed's animal, [KNOB_BADGE_ANIMAL] tall,
     * in a round badge [KNOB_BADGE] across with a [KNOB_BADGE_EDGE] edge at the disc's lower right. The
     * badge reaches [KNOB_BADGE_OVERHANG] past the knob's box to the right and below: to
     * [NOTES_CLIP], so no note runs under it, and no further. The knob, its shadow and the badge keep
     * clear of the clef ([KNOB_LIFT], [knobClefAir]).
     */
    const val KNOB_DRUM = 26f
    const val KNOB_DRUM_SHIFT = 4f
    const val KNOB_BADGE = 29f
    const val KNOB_BADGE_EDGE = 2.25f
    const val KNOB_BADGE_ANIMAL = 19f
    const val KNOB_BADGE_OVERHANG = 3f

    /**
     * How far the knob stands above the staff's box, in the stage's padding (since 2026-11-10, the
     * user on their phone: "G clef overlaps with metronome button"). Until then its top was the
     * box's, the rule let the badge touch the clef's tip and nobody counted the knob's shadow, which
     * lay 2.8 dp over the tip. Lifted, the shadow ends 5 dp short of the clef's line and 5 dp of
     * white are left between the knob and the stage's border; nothing on the staff moved.
     */
    const val KNOB_LIFT = 8f

    /**
     * Where the knob's top is, from the top of the staff's box, with the staff drawn at [scale]. A
     * staff drawn smaller than at rest (a tablet on its side) brings the clef's tip up, and the knob,
     * which keeps its size, goes up as far: the staff is centred in a taller card there, so there is
     * room. A staff drawn bigger (a tune running) takes the clef away from it, and the knob stays.
     */
    fun knobTop(scale: Float, room: Float = 0f): Float = -KNOB_LIFT - maxOf(0f, CLEF_TIP_Y - (CLEF_TIP_Y + room) * minOf(scale, 1f))

    /**
     * The air, with the staff drawn at [scale], between the clef's line at its tip and the nearest of
     * the knob's shadow and its badge (the knob's own disc is above its shadow); under zero they overlap.
     */
    fun knobClefAir(scale: Float, room: Float = 0f): Float {
        val top = knobTop(scale, room)
        val tipX = CLEF_TIP_X * scale
        val tipY = (CLEF_TIP_Y + room) * scale
        val knob = CORNER_BUTTON / 2
        val badge = KNOB_BADGE / 2
        val badgeMiddle = CORNER_BUTTON + KNOB_BADGE_OVERHANG - badge
        val toShadow = hypot(tipX - knob, tipY - (top + CORNER_SHADOW + knob)) - knob
        val toBadge = hypot(tipX - badgeMiddle, tipY - (top + badgeMiddle)) - badge
        return minOf(toShadow, toBadge) - CLEF_STROKE * CLEF_UNIT * scale / 2
    }

    /**
     * The knob's animal while the tune runs (since the evening of 2026-11-04, the user's pick "A"
     * from a moving true-size mockup; it walked the marks row before): it leaves the knob and walks
     * the staff near the knob's height (the knob's middle until the knob went up by [KNOB_LIFT]; the
     * animal's line stayed, as its hop must keep inside the box), its middle [ANIMAL_Y] from the top
     * of the staff's box, as the knob does not grow with the running staff, or higher where the staff is drawn smaller
     * ([animalY], since 2026-11-18); [ANIMAL_SIZE] tall at rest
     * scale (the running staff draws it a third bigger). Its hop (8 dp) keeps it inside the box,
     * and it stands over the stems of the notes from f² up, never over a head but c³'s top edge.
     */
    const val ANIMAL_Y = CORNER_BUTTON / 2
    const val ANIMAL_SIZE = 24f

    /** How much of the top note's head the animal may stand over: c³'s 3.1 dp at [RunningStaffScale], and a little more. */
    private const val ANIMAL_COVER = 4f

    /** The animal's hop, `HopDistance` (8 dp): at its top the animal is still inside the box. */
    private const val HOP = 8f

    /**
     * Where the animal's middle walks, from the box's top, with the staff drawn at [scale] (since 2026-11-18, the user's
     * pick "b, higher"): [ANIMAL_Y], unless there it would stand over more than [ANIMAL_COVER] of the top note's head,
     * which happens on a staff drawn smaller (a tune above c³, a phone on its side): then as high as its hop keeps it
     * inside the box. The highest note a box holds, with its room, stands where c³ does.
     */
    fun animalY(scale: Float): Float {
        val half = ANIMAL_SIZE * scale / 2
        val headTop = (yOf(C3_PLACE) - HEAD_RY - 0.25f - HEAD_EDGE) * scale // the head's tilt adds a quarter dp
        return if (ANIMAL_Y + half - headTop <= ANIMAL_COVER) ANIMAL_Y else half + HOP
    }

    /** The book button's height: a book stands taller than it is wide, and the cell under the clef has 90 dp of room. */
    const val BOOK_BUTTON_HEIGHT = 64f

    /** Where the book button's top is at [scale] (1 at rest, [RunningStaffScale] while the tune runs): centred on the names' pills. */
    fun bookButtonTop(scale: Float, room: Float = 0f): Float = (NAMES_TOP + room + PILL_HEIGHT / 2) * scale - BOOK_BUTTON_HEIGHT / 2

    /** The time signature's figures: centred at this x, each across two spaces of the staff. */
    const val SIGNATURE_X = 48f
    const val SIGNATURE_TOP_Y = TOP_LINE_Y + LINE_GAP
    const val SIGNATURE_BOTTOM_Y = TOP_LINE_Y + LINE_GAP * 3
    const val SIGNATURE_SIZE = 24f

    /** Where the first step starts; the notes scroll in from [NOTES_CLIP] so a ring is never cut. */
    const val NOTES_START = 62f
    const val NOTES_CLIP = 57f
    const val NOTES_END_PADDING = 4f

    /**
     * The room after the last step: [NOTES_END_PADDING], and while the tune runs also what the arrow beside a live bubble
     * ([PITCH_ARROW_X]) reaches past a one-beat step, so on the last note of a long tune it is not cut at the row's end.
     */
    fun endPadding(running: Boolean): Float =
        NOTES_END_PADDING + if (running) maxOf(0f, BEAT_WIDTH / 2 + PITCH_ARROW_X + PITCH_ARROW_SIZE / 2 - BEAT_WIDTH) else 0f

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

    /**
     * The room a sign takes before a note's head (since 2026-11-16, sharps and flats): a step with a sign
     * ([LineMark.Step.sign], ♯ ♭ or ♮) is this much wider, everything in it after the sign moved right by it, so the sign
     * never touches the note before. The sign's centre stands [SIGN_X] left of its head's centre, [SIGN_HALF_WIDTH] either side.
     */
    const val ACCIDENTAL_ROOM = 10f
    const val SIGN_X = 17f
    const val SIGN_HALF_WIDTH = 5.5f

    /** How far under its head's centre a sign's ink reaches: the sharp's and the natural's right stem, 11, and its cap. */
    const val SIGN_BELOW = 12f

    /** The air a slur keeps under a sign's foot (a head's is [SLUR_CLEAR]): less, so a slur onto a sign does not sag. */
    const val SIGN_SLUR_AIR = 1.5f

    /** The room before [mark]'s first head: [ACCIDENTAL_ROOM] when it carries a sign, else none. */
    fun signRoom(mark: LineMark.Step): Float = if (mark.sign != null) ACCIDENTAL_ROOM else 0f

    /** The room one mark of the line takes: a step its pieces' and its sign's, a bar line [BAR_SLOT], a breath [BREATH_SLOT]. */
    fun markWidth(mark: LineMark): Float = when (mark) {
        is LineMark.Step -> signRoom(mark) + mark.parts.fold(0f) { sum, part -> sum + partWidth(part) }
        is LineMark.Breath -> BREATH_SLOT
        LineMark.BarLine -> BAR_SLOT
    }

    /**
     * Where the pieces of one step stand, in dp from the step's left: the room its sign takes before the first head
     * ([room], none without a sign), the step's whole [width], each piece's head centre ([heads]), the middle of each
     * piece's name pill ([pills]) and the middle of each bar line inside the step ([bars]).
     */
    class StepLayout(val room: Float, val width: Float, val heads: List<Float>, val pills: List<Float>, val bars: List<Float>)

    /**
     * The layout of [mark]: the one place that knows a sign's room stands before the first head, makes that piece's
     * pill as much wider and moves everything after it right. The staff's drawing, the slurs over it and the ties
     * under its pills all read it, so they cannot part.
     */
    fun layout(mark: LineMark.Step): StepLayout {
        val heads = mutableListOf<Float>()
        val pills = mutableListOf<Float>()
        val bars = mutableListOf<Float>()
        var x = 0f
        var extra = signRoom(mark) // waits for the first piece: its head stands after it, its pill is as much wider
        for (part in mark.parts) {
            when (part) {
                is StepPart.Length -> {
                    heads += x + extra + HEAD_X
                    pills += x + (slotWidth(part.length) + extra) / 2
                    x += extra
                    extra = 0f
                }
                LineMark.BarLine -> bars += x + extra + BAR_SLOT / 2
            }
            x += partWidth(part)
        }
        return StepLayout(signRoom(mark), markWidth(mark), heads, pills, bars)
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

    /**
     * How far past the mark the staff keeps at its left ([anchorStart], dp at rest from the row's first mark) the
     * row must scroll, in dp on screen, for the walking animal over [animalX] (the same measure) to stand inside a
     * row [rowWidth] dp wide drawn at [scale], its whole box ([CORNER_BUTTON]) in view; 0 when it is in view already.
     * A whole note's last beat after a whole note lies past a phone's edge: the staff follows by just that much, and
     * the note before stays as far in view as it can.
     */
    fun followScroll(anchorStart: Float, animalX: Float, rowWidth: Float, scale: Float): Float {
        val middle = (NOTES_START - NOTES_CLIP) * scale + (animalX - anchorStart) * scale
        return maxOf(0f, middle + CORNER_BUTTON / 2 - rowWidth)
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

    /**
     * How far left of its head's centre a ledger line reaches: [LEDGER_HALF], or beside a sign a dp short of the sign's
     * right edge, just past the head (the final review of 2026-11-16: it ran 3 dp into C♯4's sharp).
     */
    fun ledgerLeft(signed: Boolean): Float = if (signed) SIGN_X - SIGN_HALF_WIDTH - LEDGER_SIGN_AIR else LEDGER_HALF
    private const val LEDGER_SIGN_AIR = 1f

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

    /** How far under its heads' centre a tie's ink reaches: a quadratic dips halfway between its ends and its control point. */
    const val TIE_LOW = (TIE_START + TIE_CONTROL) / 2 + TIE_WIDTH / 2

    /**
     * A slur (since 2026-11-16), always under the heads as the ties are, since every stem points up: its ends
     * [SLUR_END_INSET] inside the end heads' centres and [SLUR_END_DROP] under them, its belly [SLUR_BELLY] and a dp
     * more per [SLUR_BELLY_SPAN] of its length under the line from end to end, deeper where a head or a tie between
     * must be cleared by [SLUR_CLEAR], but never so deep that its middle comes within [SLUR_PILL_AIR] of the name
     * pills (under the lowest notes the pills win over the clearance); drawn as a crescent whose inner curve's control
     * points lie [SLUR_THICKNESS] higher.
     */
    const val SLUR_END_INSET = 1f
    const val SLUR_END_DROP = 9f
    const val SLUR_BELLY = 7f
    const val SLUR_BELLY_SPAN = 40f
    const val SLUR_CLEAR = 4f
    const val SLUR_PILL_AIR = 2f
    const val SLUR_THICKNESS = 3.2f

    /**
     * How far the dashed arc of a join waiting for its next note runs on to the right of the note's head: within the rest
     * of its beat and the row's end padding, since the staff scrolls its newest note to the end (26 dp was cut there).
     */
    const val WAITING_ARC_LENGTH = 16f

    /** A slur's outer curve in dp: a cubic from ([x0], [y0]) through ([c1x], [c1y]) and ([c2x], [c2y]) to ([x1], [y1]). */
    data class SlurCurve(val x0: Float, val y0: Float, val c1x: Float, val c1y: Float, val c2x: Float, val c2y: Float, val x1: Float, val y1: Float)

    /**
     * The slur from the head at [x0] (its centre at [headY0]) to the head at [x1] ([headY1]), passing under every
     * (x, lowest y) of [clear]: the heads between its ends and the lowest points of the ties under them. Its control
     * points lie a quarter of the way in from each end, on the line from end to end moved down by the belly, so the
     * curve tilts with notes at different heights.
     */
    fun slurCurve(x0: Float, headY0: Float, x1: Float, headY1: Float, clear: List<Pair<Float, Float>>): SlurCurve {
        val sx = x0 + SLUR_END_INSET
        val sy = headY0 + SLUR_END_DROP
        val ex = x1 - SLUR_END_INSET
        val ey = headY1 + SLUR_END_DROP
        val span = ex - sx
        var belly = SLUR_BELLY + span / SLUR_BELLY_SPAN
        for ((x, lowest) in clear) {
            val along = ((x - sx) / span).coerceIn(0f, 1f)
            val t = tAlong(along)
            val reach = 3 * t * (1 - t) // how much of the belly the curve has at that point
            if (reach <= 0f) continue
            belly = maxOf(belly, (lowest + SLUR_CLEAR - (sy + (ey - sy) * along)) / reach)
        }
        // Never within the air of the pills anywhere along the curve, a steep one's low end included.
        for (step in 1 until 50) {
            val t = step / 50f
            val chord = sy + (ey - sy) * xShare(t)
            belly = minOf(belly, (NAMES_TOP - SLUR_PILL_AIR - chord) / (3 * t * (1 - t)))
        }
        val rise = (ey - sy) / 4
        return SlurCurve(sx, sy, sx + span / 4, sy + rise + belly, ex - span / 4, ey - rise + belly, ex, ey)
    }

    /** The slur's parameter where its x is [along] of the way from end to end (its points' x run 0, 1/4, 3/4, 1): by halving. */
    private fun tAlong(along: Float): Float {
        var lo = 0f
        var hi = 1f
        repeat(30) {
            val t = (lo + hi) / 2
            if (xShare(t) < along) lo = t else hi = t
        }
        return (lo + hi) / 2
    }

    /** How far along from end to end the slur's x is at its parameter [t]: its points' x run 0, 1/4, 3/4, 1. */
    private fun xShare(t: Float): Float {
        val u = 1 - t
        return 3 * u * u * t * 0.25f + 3 * u * t * t * 0.75f + t * t * t
    }

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
        val ledgers = StaffNotes.ledgerLinesFor(position).map(::yOf)
        return minOf(y - maxOf(ring, stem), ledgers.minOrNull() ?: y)..maxOf(y + maxOf(ring, TIE_LOW), ledgers.maxOrNull() ?: y)
    }
}
