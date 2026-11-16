// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.flute

/**
 * The flute keys' sizes in plain dp (since 2026-11-16, sharps and flats; spec
 * `2026-11-16-flute-more-notes-design.md`, "The keys' sizes"). Five rows: the top row (C6 and the tools), which never
 * scrolls, then, [TOP_GAP] lower, four that scroll as one, [ROW_GAP] apart: the C5 octave's black keys, its white keys,
 * the C4 octave's black keys, its white keys. Every row has its key's [DEPTH] of shadow under it.
 *
 * No white key is under [WHITE] and no black key under [BLACK], and spare height shows more rows before it grows keys:
 * while the five rows do not fit at that size the keys keep it and the rows scroll; once they fit, the keys grow together
 * to fill the height, up to the layout's largest ([WHITE] on a phone upright, [TABLET_UPRIGHT_MAX] on a zoomed tab
 * upright, [SIDEWAYS_MAX] on its side), and nothing scrolls.
 */
internal object KeysGeometry {
    /** A white key's least height, the canvas's 58 (the user, 2026-11-02). */
    const val WHITE = 58f

    /** A black key's least height: the smallest a finger should be asked to hit. */
    const val BLACK = 48f

    /** The solid shadow under every key, drawn in its row's room. */
    const val DEPTH = 5f

    /** Between the top row and the scrolling rows. */
    const val TOP_GAP = 12f

    /** Between two scrolling rows. */
    const val ROW_GAP = 8f

    /** The scrolling rows' top edge fades into the page over this much while there is more above. */
    const val FADE = 22f

    /** The thumb beside the scrolling rows that shows which part of them is in view. */
    const val BAR_WIDTH = 4f

    /** The largest white key on a zoomed tab upright, and on any screen on its side. */
    const val TABLET_UPRIGHT_MAX = 72f
    const val SIDEWAYS_MAX = 88f

    /** The keys' least room: the top row and the C4 octave's two rows, whole (199). */
    const val MIN_HEIGHT = WHITE + DEPTH + TOP_GAP + BLACK + DEPTH + ROW_GAP + WHITE + DEPTH

    /** A black key's height beside a white key of [white]: 48 to 58. */
    fun black(white: Float): Float = white * BLACK / WHITE

    /** The four scrolling rows at a white key of [white]: two white, two black, their shadows and the three gaps. */
    fun scrollingRows(white: Float): Float = 2 * (white + DEPTH) + 2 * (black(white) + DEPTH) + 3 * ROW_GAP

    /** All five rows at a white key of [white]: the top row, its gap and the scrolling rows (331 at 58). */
    fun allRows(white: Float): Float = white + DEPTH + TOP_GAP + scrollingRows(white)

    /** A white key's height in keys [height] tall whose largest is [max]: see the class. */
    fun whiteHeight(height: Float, max: Float): Float {
        if (scrolls(height)) return WHITE
        val fixed = allRows(0f) // allRows is fixed room plus so much per dp of white key
        val perWhite = (allRows(WHITE) - fixed) / WHITE
        return minOf(max, maxOf(WHITE, (height - fixed) / perWhite))
    }

    /** Whether keys [height] tall scroll: the five rows do not fit at the least size. */
    fun scrolls(height: Float): Boolean = height < allRows(WHITE)

    /**
     * The left of a black key in its row, whose white key below it is the [after]th (0 for C♯), with white keys
     * [keyWidth] wide and [gap] apart: as wide as a white key and centred on the gap between its two.
     */
    fun blackLeft(after: Int, keyWidth: Float, gap: Float): Float = (after + 0.5f) * (keyWidth + gap)
}
