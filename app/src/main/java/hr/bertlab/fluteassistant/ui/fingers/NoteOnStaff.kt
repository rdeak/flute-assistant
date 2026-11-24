// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.fingers

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import hr.bertlab.fluteassistant.domain.notes.StaffNotes
import hr.bertlab.fluteassistant.domain.tuner.Accidental
import hr.bertlab.fluteassistant.domain.tuner.NoteSpelling
import hr.bertlab.fluteassistant.ui.common.MusicSymbols
import hr.bertlab.fluteassistant.ui.common.drawMusicSymbol
import hr.bertlab.fluteassistant.ui.common.rememberMusicPaint
import hr.bertlab.fluteassistant.ui.common.noteColor
import hr.bertlab.fluteassistant.ui.theme.FluteTheme

/**
 * The note as a book prints it, in Bravura's music symbols (since 2026-11-24; spec
 * `2026-11-24-fingers-music-symbols-and-book-chart-design.md`, part 2): five lines, the treble clef, the whole note with
 * its hollow in the letter's colour, its ♯ or ♭ and the ledger lines it needs, all in the theme's ink. The sizes are
 * [SymbolStaff]'s: room for c1 to c4 and their signs in the height it is given, the line gap 8.2 dp where that allows
 * and less on a short row. The symbols are sized in dp, so large text does not grow them. Decorative: the row's chart names the note.
 */
@Composable
internal fun NoteOnStaff(midi: Int, modifier: Modifier = Modifier) {
    val colors = FluteTheme.colors
    val paint = rememberMusicPaint()
    Canvas(modifier) {
        val ink = colors.text
        val gapDp = SymbolStaff.gapFor(size.height.toDp().value)
        val g = gapDp.dp.toPx()
        val bottom = SymbolStaff.bottomLine(size.height.toDp().value, gapDp).dp.toPx()
        fun yOf(place: Int) = SymbolStaff.yOf(place, bottom, g)

        val line = maxOf(1.2.dp.toPx(), 0.13f * g)
        StaffNotes.LINE_POSITIONS.forEach { drawLine(ink, Offset(0f, yOf(it)), Offset(size.width, yOf(it)), line) }

        val place = NoteSpelling.placeOf(midi)
        val x = SymbolStaff.noteX(size.width, g)
        val y = yOf(place)
        val ledger = 0.16f * g + 0.2.dp.toPx()
        StaffNotes.ledgerLinesFor(place).forEach {
            drawLine(ink, Offset(x - SymbolStaff.LEDGER_HALF * g, yOf(it)), Offset(x + SymbolStaff.LEDGER_HALF * g, yOf(it)), ledger)
        }
        val hollow = Size(MusicSymbols.WHOLE_HOLLOW_RX * 2 * g, MusicSymbols.WHOLE_HOLLOW_RY * 2 * g)
        drawOval(noteColor(midi), Offset(x - hollow.width / 2, y - hollow.height / 2), hollow)

        val em = MusicSymbols.em(g)
        drawMusicSymbol(paint, MusicSymbols.G_CLEF, SymbolStaff.CLEF_LEFT.dp.toPx(), yOf(StaffNotes.G_LINE), em, ink)
        drawMusicSymbol(paint, MusicSymbols.NOTE_WHOLE, x, y, em, ink, Paint.Align.CENTER)
        val sign = when (NoteSpelling.signOf(midi)) {
            Accidental.SHARP -> MusicSymbols.SHARP
            Accidental.FLAT -> MusicSymbols.FLAT
            Accidental.NATURAL, null -> null
        }
        if (sign != null) drawMusicSymbol(paint, sign, SymbolStaff.signRight(size.width, g), y, em, ink, Paint.Align.RIGHT)
    }
}
