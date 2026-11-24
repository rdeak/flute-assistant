// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.fingers

import hr.bertlab.fluteassistant.ui.common.MusicSymbols
import hr.bertlab.fluteassistant.ui.common.MusicSymbols.WHOLE_WIDTH
import kotlin.math.min

/**
 * The Fingers tab's staff in numbers (spec `2026-11-24-fingers-music-symbols-and-book-chart-design.md`, part 2): sizes
 * in dp as `Float`, the symbols' own in line gaps. The box holds c1 to c4, so a letter's notes stand at their true
 * heights row to row: from c4's head to the clef's foot is [SPAN_GAPS] line gaps, with [MARGIN] over and under. At
 * full size ([TALL]) the box also holds the signs that reach past those, C♯4's sharp and B♭6's flat ([SIGN_REACH]).
 * The gap is the mockup's [MAX_GAP] where the box has room and gives way on a shorter row. Plain numbers, so the JVM
 * tests pin them; `NoteOnStaff` draws from them.
 */
internal object SymbolStaff {
    /** The largest line gap, the mockup's, in dp. */
    const val MAX_GAP = 8.2f

    /** Over c4's head and under the clef's foot, in dp. */
    const val MARGIN = 4f

    /** From c4's head to the clef's foot, in line gaps. */
    const val SPAN_GAPS = 11.85f

    /** The clef's left, in dp from the staff's start. */
    const val CLEF_LEFT = 1f

    /**
     * The note's centre from the staff's end, and the room between a sign and the note, in line gaps: 0.55, so a sign
     * stands left of the note's ledger lines ([LEDGER_HALF]) with air, as an engraver puts it (0.3 until the final
     * review of 2026-11-24, when the ledger line ran into the sign on seven black notes).
     */
    const val NOTE_FROM_END = 1.35f
    const val SIGN_CLEAR = 0.55f

    /** Half a ledger line's length, in line gaps: the note's half width and Bravura's 0.4 extension either side. */
    const val LEDGER_HALF = 1.25f

    /**
     * How far, in line gaps, a sign reaches past the span: B♭6's flat 0.656 over c4's head, C♯4's sharp 0.642 under the
     * clef's foot (from [MusicSymbols.FLAT_TOP] and [MusicSymbols.SHARP_DROP]).
     */
    const val SIGN_REACH = 0.66f

    /** How tall the box is at the largest gap, the signs held: 107.99 dp (105.17 until the final review of 2026-11-24). */
    const val TALL: Float = SPAN_GAPS * MAX_GAP + 2 * SIGN_REACH * MAX_GAP

    /** The line gap a box [height] dp tall gives. */
    fun gapFor(height: Float): Float = min(MAX_GAP, (height - 2 * MARGIN) / SPAN_GAPS)

    /** Where the bottom line (e1) stands in a box [height] tall at [gap]: the content in the middle, the line 10.1 gaps under its top. */
    fun bottomLine(height: Float, gap: Float): Float = (height - SPAN_GAPS * gap) / 2 + BOTTOM_LINE_GAPS * gap

    /** Where [place] stands (`NoteSpelling.placeOf`: c1 = 0, e1 = 2, f2 = 10, c4 = 21): two places to a line gap. */
    fun yOf(place: Int, bottomLine: Float, gap: Float): Float = bottomLine - (place - 2) * gap / 2

    /** The note's centre across a staff [width] wide. */
    fun noteX(width: Float, gap: Float): Float = width - NOTE_FROM_END * gap

    /** Where a sign's right side stands: [SIGN_CLEAR] clear of the note's left. */
    fun signRight(width: Float, gap: Float): Float = noteX(width, gap) - (WHOLE_WIDTH / 2 + SIGN_CLEAR) * gap

    /** From the top of the content (c4's head with a little air) to the bottom line: c4 is 9.5 gaps over it. */
    private const val BOTTOM_LINE_GAPS = 10.1f
}
