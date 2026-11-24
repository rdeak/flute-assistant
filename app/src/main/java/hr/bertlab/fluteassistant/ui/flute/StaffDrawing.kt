// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.flute

import android.graphics.Paint
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
import hr.bertlab.fluteassistant.domain.tuner.Accidental
import hr.bertlab.fluteassistant.ui.common.MusicSymbols
import hr.bertlab.fluteassistant.ui.common.drawMusicSymbol
import hr.bertlab.fluteassistant.ui.common.noteColor
import hr.bertlab.fluteassistant.ui.flute.StaffGeometry.BAND_ALPHA
import hr.bertlab.fluteassistant.ui.flute.StaffGeometry.BAND_BOTTOM
import hr.bertlab.fluteassistant.ui.flute.StaffGeometry.BAND_RADIUS
import hr.bertlab.fluteassistant.ui.flute.StaffGeometry.BAND_TOP
import hr.bertlab.fluteassistant.ui.flute.StaffGeometry.BAR_LINE_WIDTH
import hr.bertlab.fluteassistant.ui.flute.StaffGeometry.BOTTOM_LINE_Y
import hr.bertlab.fluteassistant.ui.flute.StaffGeometry.MIDDLE_LINE_Y
import hr.bertlab.fluteassistant.ui.flute.StaffGeometry.TOP_LINE_Y
import hr.bertlab.fluteassistant.ui.flute.StaffGeometry.yOf
import hr.bertlab.fluteassistant.ui.theme.InkOnFill
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
            pieces.forEachIndexed { at, (cx, length) -> drawNote(cx, item.midi, position, length, ink, signed = at == 0 && sign != null, lit = lit) }
            drawTies(layout.heads, yOf(position), ink.line)
            if (sign != null) drawStaffSymbol(ink, symbolOf(sign), layout.heads.first() - StaffGeometry.SIGN_X, yOf(position), StaffGeometry.SIGN_SHARE)
        }
        is TuneRest -> {
            if (lit) pieces.forEach { (cx, _) -> drawCircle(Sun, StaffGeometry.REST_LIT_RADIUS, Offset(cx, MIDDLE_LINE_Y)) }
            pieces.forEach { (cx, length) -> drawRest(cx, length, ink, lit) }
        }
        TuneBreath -> Unit
    }
}

/**
 * One note at [position] in the music font's symbols (since 2026-11-24, the user's pick A2: ink as a book prints it, the
 * colour in the heads; spec `2026-11-24-flute-staff-music-symbols-design.md`): its ledger lines, a stem up on the right
 * (none for a whole note) and an eighth's flag in the staff's ink; a filled head (eighth, quarter) in the letter's colour
 * with an ink edge, which keeps a yellow E in sight on the lit disc; an open head (half, dotted half, whole) in ink with
 * its hollow in the letter's colour; a dotted half's dot in ink. On a [lit] step (the yellow disc behind it) an open head's
 * ring takes the dark ink a bright fill takes ([InkOnFill]) in both themes: the night's light ink would vanish on the
 * yellow (the final review, 2026-11-24).
 */
private fun DrawScope.drawNote(cx: Float, midi: Int, position: Int, length: NoteLength, ink: StaffInk, signed: Boolean, lit: Boolean) {
    val y = yOf(position)
    for (ledger in StaffNotes.ledgerLinesFor(position)) {
        val ly = yOf(ledger)
        drawLine(ink.line, Offset(cx - StaffGeometry.ledgerLeft(signed), ly), Offset(cx + StaffGeometry.LEDGER_HALF, ly), StaffGeometry.LINE_WIDTH)
    }
    val color = noteColor(midi)
    if (length != NoteLength.WHOLE) {
        val stemX = cx + StaffGeometry.STEM_X
        val top = y - StaffGeometry.STEM_REACH
        drawLine(ink.line, Offset(stemX, y - StaffGeometry.STEM_START), Offset(stemX, top), StaffGeometry.STEM_WIDTH, cap = StrokeCap.Round)
        if (length == NoteLength.EIGHTH) {
            drawStaffSymbol(ink, MusicSymbols.FLAG_EIGHTH, stemX - StaffGeometry.STEM_WIDTH / 2, top, StaffGeometry.FLAG_SHARE, Paint.Align.LEFT)
        }
    }
    when (length) {
        NoteLength.EIGHTH, NoteLength.QUARTER -> drawMusicSymbol(
            ink.symbols, MusicSymbols.NOTE_BLACK, cx, y, StaffGeometry.EM * StaffGeometry.HEAD_SHARE, color, Paint.Align.CENTER,
            edge = ink.outline, edgeWidth = StaffGeometry.HEAD_EDGE,
        )
        NoteLength.HALF, NoteLength.DOTTED_HALF -> {
            val gap = StaffGeometry.LINE_GAP * StaffGeometry.HEAD_SHARE
            rotate(MusicSymbols.HALF_HOLLOW_TURN, pivot = Offset(cx, y)) {
                hollow(cx, y, MusicSymbols.HALF_HOLLOW_RX * gap, MusicSymbols.HALF_HOLLOW_RY * gap, color)
            }
            drawStaffSymbol(ink, MusicSymbols.NOTE_HALF, cx, y, StaffGeometry.HEAD_SHARE, color = if (lit) InkOnFill else ink.line)
        }
        NoteLength.WHOLE -> {
            val gap = StaffGeometry.LINE_GAP * StaffGeometry.WHOLE_SHARE
            hollow(cx, y, MusicSymbols.WHOLE_HOLLOW_RX * gap, MusicSymbols.WHOLE_HOLLOW_RY * gap, color)
            drawStaffSymbol(ink, MusicSymbols.NOTE_WHOLE, cx, y, StaffGeometry.WHOLE_SHARE, color = if (lit) InkOnFill else ink.line)
        }
    }
    if (length == NoteLength.DOTTED_HALF) {
        val dotY = if (StaffGeometry.onLine(position)) y - StaffGeometry.STEP else y
        drawStaffSymbol(ink, MusicSymbols.DOT, cx + StaffGeometry.DOT_X, dotY, StaffGeometry.DOT_SHARE)
    }
}

/** An open head's hollow, filled with the letter's colour under the head's symbol: an oval of radii [rx] by [ry]. */
private fun DrawScope.hollow(cx: Float, y: Float, rx: Float, ry: Float, color: Color) =
    drawOval(color, Offset(cx - rx, y - ry), Size(rx * 2, ry * 2))

/** One of the staff's symbols in [color] (its ink unless the step is lit), at [share] of the staff's em, its baseline on [y]. */
private fun DrawScope.drawStaffSymbol(
    ink: StaffInk,
    symbol: String,
    x: Float,
    y: Float,
    share: Float,
    align: Paint.Align = Paint.Align.CENTER,
    color: Color = ink.line,
) = drawMusicSymbol(ink.symbols, symbol, x, y, StaffGeometry.EM * share, color, align)

/** The font's symbol for [sign]. */
private fun symbolOf(sign: Accidental): String = when (sign) {
    Accidental.SHARP -> MusicSymbols.SHARP
    Accidental.FLAT -> MusicSymbols.FLAT
    Accidental.NATURAL -> MusicSymbols.NATURAL
}

/** The ties between the heads of one note, centred at [heads] at height [y], in [color] (the staff's ink since 2026-11-24): always under them, away from the stems. */
private fun DrawScope.drawTies(heads: List<Float>, y: Float, color: Color) {
    for ((from, to) in heads.zipWithNext()) drawPath(tiePath(from, to, y), color, style = Stroke(StaffGeometry.TIE_WIDTH, cap = StrokeCap.Round))
}

/**
 * One piece of a rest centred on [cx] in the music font's symbols, in the staff's ink (since 2026-11-24; `mutedText`
 * before), or on a [lit] step in the dark ink a bright fill takes ([InkOnFill]): the eighth and the quarter rest on the
 * middle line at [StaffGeometry.REST_SHARE], so the quarter stays inside its lit ring; the half rest sitting on the middle
 * line and the whole rest hanging from the line above it at [StaffGeometry.REST_BLOCK_SHARE], the width drawn before; a
 * dotted half rest's dot where it stood.
 */
private fun DrawScope.drawRest(cx: Float, length: NoteLength, ink: StaffInk, lit: Boolean) {
    val m = MIDDLE_LINE_Y
    val color = if (lit) InkOnFill else ink.line
    fun rest(symbol: String, y: Float, share: Float) = drawStaffSymbol(ink, symbol, cx, y, share, color = color)
    when (length) {
        NoteLength.EIGHTH -> rest(MusicSymbols.REST_EIGHTH, m, StaffGeometry.REST_SHARE)
        NoteLength.QUARTER -> rest(MusicSymbols.REST_QUARTER, m, StaffGeometry.REST_SHARE)
        NoteLength.HALF -> rest(MusicSymbols.REST_HALF, m, StaffGeometry.REST_BLOCK_SHARE)
        NoteLength.DOTTED_HALF -> {
            rest(MusicSymbols.REST_HALF, m, StaffGeometry.REST_BLOCK_SHARE)
            drawStaffSymbol(ink, MusicSymbols.DOT, cx + StaffGeometry.REST_DOT_X, m - StaffGeometry.STEP, StaffGeometry.DOT_SHARE, color = color)
        }
        NoteLength.WHOLE -> rest(MusicSymbols.REST_WHOLE, m - StaffGeometry.LINE_GAP, StaffGeometry.REST_BLOCK_SHARE)
    }
}
