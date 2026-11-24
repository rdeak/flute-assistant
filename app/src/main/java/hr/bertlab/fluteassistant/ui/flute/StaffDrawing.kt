// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.flute

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import hr.bertlab.fluteassistant.domain.flute.FluteNotes
import hr.bertlab.fluteassistant.domain.flute.LineMark
import hr.bertlab.fluteassistant.domain.flute.NoteLength
import hr.bertlab.fluteassistant.domain.flute.StepPart
import hr.bertlab.fluteassistant.domain.flute.TuneBreath
import hr.bertlab.fluteassistant.domain.flute.TuneItem
import hr.bertlab.fluteassistant.domain.flute.TuneNote
import hr.bertlab.fluteassistant.domain.flute.TuneRest
import hr.bertlab.fluteassistant.domain.notes.StaffNotes
import hr.bertlab.fluteassistant.ui.common.drawSign
import hr.bertlab.fluteassistant.ui.common.noteColor
import hr.bertlab.fluteassistant.ui.flute.StaffGeometry.BAND_ALPHA
import hr.bertlab.fluteassistant.ui.flute.StaffGeometry.BAND_BOTTOM
import hr.bertlab.fluteassistant.ui.flute.StaffGeometry.BAND_RADIUS
import hr.bertlab.fluteassistant.ui.flute.StaffGeometry.BAND_TOP
import hr.bertlab.fluteassistant.ui.flute.StaffGeometry.BAR_LINE_WIDTH
import hr.bertlab.fluteassistant.ui.flute.StaffGeometry.BOTTOM_LINE_Y
import hr.bertlab.fluteassistant.ui.flute.StaffGeometry.HEAD_RX
import hr.bertlab.fluteassistant.ui.flute.StaffGeometry.HEAD_RY
import hr.bertlab.fluteassistant.ui.flute.StaffGeometry.MIDDLE_LINE_Y
import hr.bertlab.fluteassistant.ui.flute.StaffGeometry.TOP_LINE_Y
import hr.bertlab.fluteassistant.ui.flute.StaffGeometry.yOf
import hr.bertlab.fluteassistant.ui.theme.Sun

// What one step draws on the staff: its notes with their stems, flags, signs and ties, or its rest.

/** Draws one step where [layout] puts its pieces, in dp: the band and the rings first, so no piece's ring covers another piece's head. */
internal fun DrawScope.drawStep(item: TuneItem, step: LineMark.Step, layout: StaffGeometry.StepLayout, lit: Boolean, growing: Boolean, ink: StaffInk) {
    if (growing) {
        drawRoundRect(
            Sun.copy(alpha = BAND_ALPHA),
            topLeft = Offset(1f, BAND_TOP),
            size = Size(layout.width - 2f, BAND_BOTTOM - BAND_TOP),
            cornerRadius = CornerRadius(BAND_RADIUS),
        )
    }
    for (x in layout.bars) drawLine(ink.line, Offset(x, TOP_LINE_Y), Offset(x, BOTTOM_LINE_Y), BAR_LINE_WIDTH)
    // Each piece's head centre and length.
    val pieces = layout.heads.zip(step.parts.filterIsInstance<StepPart.Length>().map { it.length })
    val sign = step.sign
    when (item) {
        is TuneNote -> {
            val position = FluteNotes.positionOf(item.midi) // a black note on its letter's place
            if (lit) pieces.forEach { (cx, _) -> drawCircle(Sun, StaffGeometry.LIT_RADIUS, Offset(cx, yOf(position))) }
            pieces.forEachIndexed { at, (cx, length) -> drawNote(cx, item.midi, position, length, ink, signed = at == 0 && sign != null) }
            drawTies(layout.heads, yOf(position), noteColor(item.midi))
            if (sign != null) drawSign(sign, layout.heads.first() - StaffGeometry.SIGN_X, yOf(position), ink.line)
        }
        is TuneRest -> {
            if (lit) pieces.forEach { (cx, _) -> drawCircle(Sun, StaffGeometry.REST_LIT_RADIUS, Offset(cx, MIDDLE_LINE_Y)) }
            pieces.forEach { (cx, length) -> drawRest(cx, length, ink.rest) }
        }
        TuneBreath -> Unit
    }
}

/**
 * One note at [position] as printed music draws it, all of it in its letter's colour: its ledger
 * lines (in the staff's ink), a tilted oval head (filled for an eighth and a quarter, a ring for a
 * half and a dotted half, a wider ring for a whole note) with a thin edge, a stem up on the right
 * (none for a whole note), a flag on an eighth and a dot on a dotted half.
 */
private fun DrawScope.drawNote(cx: Float, midi: Int, position: Int, length: NoteLength, ink: StaffInk, signed: Boolean) {
    val y = yOf(position)
    for (ledger in StaffNotes.ledgerLinesFor(position)) {
        val ly = yOf(ledger)
        drawLine(ink.line, Offset(cx - StaffGeometry.ledgerLeft(signed), ly), Offset(cx + StaffGeometry.LEDGER_HALF, ly), StaffGeometry.LINE_WIDTH)
    }
    val color = noteColor(midi)
    if (length != NoteLength.WHOLE) {
        val stemX = cx + StaffGeometry.STEM_X
        val top = y - StaffGeometry.STEM_REACH
        drawLine(color, Offset(stemX, y - StaffGeometry.STEM_START), Offset(stemX, top), StaffGeometry.STEM_WIDTH, cap = StrokeCap.Round)
        if (length == NoteLength.EIGHTH) {
            // The flag, to the right of the stem, curving down from its top.
            drawPath(
                Path().apply {
                    moveTo(stemX, top)
                    cubicTo(stemX + 1f, top + 8f, stemX + 8f, top + 10f, stemX + 6f, top + 20f)
                },
                color,
                style = Stroke(2.75f, cap = StrokeCap.Round),
            )
        }
    }
    val (rx, ry) = if (length == NoteLength.WHOLE) StaffGeometry.WHOLE_RX to StaffGeometry.WHOLE_RY else HEAD_RX to HEAD_RY
    rotate(StaffGeometry.HEAD_TILT, pivot = Offset(cx, y)) {
        val edge = StaffGeometry.HEAD_EDGE
        if (length.halfBeats >= NoteLength.HALF.halfBeats) {
            // A ring: the staff shows through the hole, as on paper; the edge drawn on both sides of it.
            val ring = StaffGeometry.OPEN_RING
            drawOval(color, Offset(cx - rx + ring / 2, y - ry + ring / 2), Size((rx - ring / 2) * 2, (ry - ring / 2) * 2), style = Stroke(ring))
            drawOval(ink.outline, Offset(cx - rx, y - ry), Size(rx * 2, ry * 2), style = Stroke(edge))
            drawOval(ink.outline, Offset(cx - rx + ring, y - ry + ring), Size((rx - ring) * 2, (ry - ring) * 2), style = Stroke(edge))
        } else {
            drawOval(color, Offset(cx - rx, y - ry), Size(rx * 2, ry * 2))
            drawOval(ink.outline, Offset(cx - rx, y - ry), Size(rx * 2, ry * 2), style = Stroke(edge))
        }
    }
    if (length == NoteLength.DOTTED_HALF) {
        val dotY = if (StaffGeometry.onLine(position)) y - StaffGeometry.STEP else y
        drawCircle(color, StaffGeometry.DOT_RADIUS, Offset(cx + StaffGeometry.DOT_X, dotY))
    }
}

/** The ties between the heads of one note, centred at [heads] at height [y], in its [color]: always under them, away from the stems. */
private fun DrawScope.drawTies(heads: List<Float>, y: Float, color: Color) {
    for ((from, to) in heads.zipWithNext()) drawPath(tiePath(from, to, y), color, style = Stroke(StaffGeometry.TIE_WIDTH, cap = StrokeCap.Round))
}

/**
 * One piece of a rest centred on [cx], as written in the treble staff, with 💤 under the staff:
 * the eighth's dot and slanting stem, the quarter's zigzag (drawn on the canvas), a block on the
 * middle line (half, with a dot for the dotted half) or hanging from the line above it (whole).
 */
private fun DrawScope.drawRest(cx: Float, length: NoteLength, color: Color) {
    val m = MIDDLE_LINE_Y
    val block = Size(15f, StaffGeometry.STEP)
    when (length) {
        NoteLength.EIGHTH -> {
            drawCircle(color, 3.4f, Offset(cx - 3f, m - 8.25f))
            drawPath(
                Path().apply {
                    moveTo(cx - 3f, m - 6f)
                    quadraticTo(cx + 0.75f, m - 3f, cx + 5.25f, m - 10.5f)
                    lineTo(cx, m + 13.5f)
                },
                color,
                style = Stroke(2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        NoteLength.QUARTER -> drawPath(
            Path().apply {
                moveTo(cx - 3f, m - 18f)
                lineTo(cx + 3.75f, m - 9f)
                lineTo(cx - 2.25f, m - 1.5f)
                lineTo(cx + 4.5f, m + 7.5f)
                cubicTo(cx - 3f, m + 3.75f, cx - 4.5f, m + 9f, cx + 0.75f, m + 15f)
            },
            color,
            style = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        // A half rest sits on the middle line; a whole rest hangs from the line above it.
        NoteLength.HALF -> drawRect(color, Offset(cx - block.width / 2, m - block.height), block)
        NoteLength.DOTTED_HALF -> {
            drawRect(color, Offset(cx - block.width / 2, m - block.height), block)
            drawCircle(color, StaffGeometry.DOT_RADIUS, Offset(cx + 13.5f, m - StaffGeometry.STEP))
        }
        NoteLength.WHOLE -> drawRect(color, Offset(cx - block.width / 2, m - StaffGeometry.LINE_GAP), block)
    }
}
