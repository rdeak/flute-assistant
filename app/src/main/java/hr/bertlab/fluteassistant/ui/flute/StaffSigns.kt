// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.flute

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import hr.bertlab.fluteassistant.domain.tuner.Accidental

/**
 * A sharp, a flat or a natural before a note (since 2026-11-16), centred at [cx] on the head's height [y], in dp inside the
 * staff's scaled drawing, in [color] (the ink): as engraved, thin stems and thick bars rising to the right. The sharp: two
 * stems 4 apart, 21 tall, two bars 7 apart. The flat: a stem from 16 above the head's centre to 6 under it and a bowl
 * on the head's place. The natural: two stems, the left from 11 above to 5 under, the right from 5 above to 11 under,
 * and two bars between them. All of it within [StaffGeometry.SIGN_HALF_WIDTH] of [cx].
 */
internal fun DrawScope.drawSign(sign: Accidental, cx: Float, y: Float, color: Color) {
    val thin = 1.4f
    val bar = 3f
    when (sign) {
        Accidental.SHARP -> {
            drawLine(color, Offset(cx - 2f, y - 11f), Offset(cx - 2f, y + 10f), thin, cap = StrokeCap.Round)
            drawLine(color, Offset(cx + 2f, y - 10f), Offset(cx + 2f, y + 11f), thin, cap = StrokeCap.Round)
            drawLine(color, Offset(cx - 5f, y - 2f), Offset(cx + 5f, y - 5f), bar, cap = StrokeCap.Round)
            drawLine(color, Offset(cx - 5f, y + 5f), Offset(cx + 5f, y + 2f), bar, cap = StrokeCap.Round)
        }
        Accidental.FLAT -> {
            drawLine(color, Offset(cx - 3f, y - 16f), Offset(cx - 3f, y + 6f), 1.6f, cap = StrokeCap.Round)
            drawPath(
                Path().apply {
                    moveTo(cx - 3f, y + 6f)
                    cubicTo(cx + 3f, y + 2f, cx + 6f, y - 3f, cx + 2f, y - 4f)
                    cubicTo(cx, y - 4.5f, cx - 2f, y - 3f, cx - 3f, y - 1f)
                },
                color,
                style = Stroke(2.2f, cap = StrokeCap.Round),
            )
        }
        Accidental.NATURAL -> {
            drawLine(color, Offset(cx - 3f, y - 11f), Offset(cx - 3f, y + 5f), thin, cap = StrokeCap.Round)
            drawLine(color, Offset(cx + 3f, y - 5f), Offset(cx + 3f, y + 11f), thin, cap = StrokeCap.Round)
            drawLine(color, Offset(cx - 3f, y - 2f), Offset(cx + 3f, y - 4f), bar, cap = StrokeCap.Butt)
            drawLine(color, Offset(cx - 3f, y + 4f), Offset(cx + 3f, y + 2f), bar, cap = StrokeCap.Butt)
        }
    }
}
