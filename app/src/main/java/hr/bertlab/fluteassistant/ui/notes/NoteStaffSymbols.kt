// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.notes

import hr.bertlab.fluteassistant.domain.notes.Phrases
import hr.bertlab.fluteassistant.ui.common.MusicSymbols
import kotlin.math.min

/**
 * The notes game's clef and heads from the music font (since 2026-11-24, the user's pick B; spec
 * `2026-11-24-notes-staff-music-symbols-design.md`). The game's staff is drawn with tall places, so its symbols are
 * sized to what it drew before, not to its line gap: each one's staff space is a share of the line gap. Sizes are in the
 * caller's units (`NoteStaff` draws in px, its line gap two pitches). Plain numbers, so the JVM tests pin them.
 */
internal object NoteStaffSymbols {
    /**
     * The clef's staff space over the line gap: as tall as the hand-built clef it replaced (211 dp at the canvas board's
     * 55.7 dp line gap; about 178 dp on the A55, whose gap is about 47).
     */
    const val CLEF_SHARE = 0.54f

    /** A head's staff space over the line gap: 29.6 x 25.1 dp at the board's 55.7, where the drawn oval was 32.3 x 23.4. */
    const val HEAD_SHARE = 0.45f

    /** The clef's opacity: faint, as Hear's notes pass over it; the font's clef is solid, so a little under the outline's 0.4. */
    const val CLEF_ALPHA = 0.3f

    /** A head's ink edge, in dp: keeps a yellow E in sight on the yellow bands and halo. */
    const val HEAD_EDGE = 2.5f

    /** The clef's left, as a share of the line gap: where the drawn clef stood. */
    const val CLEF_LEFT = 0.15f

    fun clefLeft(lineGap: Float): Float = lineGap * CLEF_LEFT

    /**
     * The room for the clef from its left to a line short of the nearest slot there can be, on a staff [width] wide with
     * lines [lineGap] apart and [lineWidth] thick: the second column of the longest phrase (Hear's first note is given)
     * less a slot's half width. [clefEm] keeps the clef inside it.
     */
    fun clefRoom(width: Float, lineGap: Float, lineWidth: Float): Float =
        width * phraseColumns(Phrases.MAX_LENGTH)[1] - slotHalfWidth(width, lineGap / 2) - lineWidth - clefLeft(lineGap)

    /**
     * The clef's em: as tall as the drawn clef was, or, on a staff so tall for its width that it would reach the nearest
     * slot, as wide as the [room] from its left to there (the font's clef is wider for its height than the drawn one,
     * and the clef must not crowd Hear's notes).
     */
    fun clefEm(lineGap: Float, room: Float): Float =
        min(MusicSymbols.em(lineGap * CLEF_SHARE), MusicSymbols.em(room.coerceAtLeast(0f) / MusicSymbols.CLEF_WIDTH))

    fun headEm(lineGap: Float): Float = MusicSymbols.em(lineGap * HEAD_SHARE)

    fun headHalfWidth(lineGap: Float): Float = MusicSymbols.NOTE_WIDTH * lineGap * HEAD_SHARE / 2

    fun headHalfHeight(lineGap: Float): Float = MusicSymbols.NOTE_HALF_HEIGHT * lineGap * HEAD_SHARE
}
