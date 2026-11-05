// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * A stroked treble clef, drawn in [unit]s from [origin]: the hook and dot at the bottom, the
 * stem up to the loop at the top, the sweep back down and the curl around the G line. The notes
 * game and the flute's staff both draw it, each as faint as its canvas board, the line
 * [strokeUnits] of a unit thick.
 */
fun DrawScope.drawTrebleClef(unit: Float, origin: Offset, color: Color, strokeUnits: Float = 0.13f) {
    fun x(u: Float) = origin.x + u * unit
    fun y(u: Float) = origin.y + u * unit
    val path = Path().apply {
        moveTo(x(0.6f), y(4.7f))
        cubicTo(x(0.65f), y(5.1f), x(1.1f), y(5.12f), x(1.05f), y(4.65f))
        cubicTo(x(1.0f), y(3.8f), x(1.1f), y(2.0f), x(1.3f), y(0.5f))
        cubicTo(x(1.35f), y(-0.2f), x(1.5f), y(-0.8f), x(1.55f), y(-1.0f))
        cubicTo(x(1.7f), y(-0.9f), x(1.85f), y(-0.3f), x(1.75f), y(0.3f))
        cubicTo(x(1.65f), y(1.2f), x(0.9f), y(1.9f), x(0.5f), y(2.4f))
        cubicTo(x(0.1f), y(2.9f), x(0.1f), y(3.5f), x(0.5f), y(3.9f))
        cubicTo(x(0.9f), y(4.25f), x(1.55f), y(4.0f), x(1.6f), y(3.3f))
        cubicTo(x(1.65f), y(2.7f), x(1.1f), y(2.4f), x(0.8f), y(2.75f))
        cubicTo(x(0.55f), y(3.0f), x(0.7f), y(3.4f), x(1.0f), y(3.3f))
    }
    drawPath(path, color, style = Stroke(width = unit * strokeUnits, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawCircle(color, radius = unit * 0.15f, center = Offset(x(0.6f), y(4.7f)))
}
