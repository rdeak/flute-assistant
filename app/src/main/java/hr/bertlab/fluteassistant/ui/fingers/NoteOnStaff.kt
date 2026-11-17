// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.fingers

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import hr.bertlab.fluteassistant.domain.notes.StaffNotes
import hr.bertlab.fluteassistant.domain.tuner.NoteSpelling
import hr.bertlab.fluteassistant.ui.common.SIGN_LINE_GAP
import hr.bertlab.fluteassistant.ui.common.drawSign
import hr.bertlab.fluteassistant.ui.common.drawStaffClef
import hr.bertlab.fluteassistant.ui.common.noteColor
import hr.bertlab.fluteassistant.ui.theme.FluteTheme

/**
 * The note as the book writes it, on a small staff: five lines, the treble clef, the note in its letter's colour with its
 * ♯ or ♭ and the ledger lines it needs. Room for c1 to c4 in the height it is given: the line gap is that height less
 * 15 dp over 10.5, 7.5 dp in a row's 94 (the mockup's `staff()` in `gen.py`). Decorative: the row's flute names the note.
 */
@Composable
internal fun NoteOnStaff(midi: Int, modifier: Modifier = Modifier) {
    val colors = FluteTheme.colors
    Canvas(modifier) {
        val gap = (size.height - 15.dp.toPx()) / 10.5f
        val bottom = 8.dp.toPx() + 9.5f * gap
        val line = 1.5.dp.toPx()
        // `StaffNotes`' places, two to a line gap, counted up from the bottom line.
        fun yOf(place: Int) = bottom - (place - StaffNotes.LINE_POSITIONS.first()) * gap / 2

        StaffNotes.LINE_POSITIONS.forEach { drawLine(colors.text, Offset(0f, yOf(it)), Offset(size.width, yOf(it)), line) }
        drawStaffClef(gap, yOf(StaffNotes.G_LINE), x = 2.dp.toPx(), color = colors.mutedText, strokeUnits = CLEF_STROKE)

        val place = NoteSpelling.placeOf(midi)
        val x = size.width - 17.dp.toPx()
        val y = yOf(place)
        val ledgerHalf = 9.dp.toPx()
        StaffNotes.ledgerLinesFor(place).forEach { drawLine(colors.text, Offset(x - ledgerHalf, yOf(it)), Offset(x + ledgerHalf, yOf(it)), line) }
        val head = Size(gap * 0.72f * 2, gap * 0.54f * 2)
        val topLeft = Offset(x - head.width / 2, y - head.height / 2)
        rotate(-18f, pivot = Offset(x, y)) {
            drawOval(noteColor(midi), topLeft, head)
            drawOval(colors.outline, topLeft, head, style = Stroke(2.dp.toPx()))
        }
        NoteSpelling.signOf(midi)?.let { sign ->
            // drawSign draws in the flute staff's units, whose line gap is 18: one unit here is this staff's gap over that.
            val unit = gap / SIGN_LINE_GAP
            scale(unit, pivot = Offset.Zero) {
                drawSign(sign, cx = (x - 15.dp.toPx()) / unit, y = y / unit, color = colors.text)
            }
        }
    }
}

/** Thicker than the clef's default 0.13 of a unit: this clef is small. */
private const val CLEF_STROKE = 0.2f
