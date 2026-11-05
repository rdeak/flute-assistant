// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.tuner

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.domain.tuner.FluteKey
import hr.bertlab.fluteassistant.ui.theme.FluteTheme

/**
 * A flute seen from above, head joint on the left, on a rounded track: the thumb key, the
 * three left-hand keys, the G sharp lever, the three right-hand keys, the E flat key and the
 * two foot-joint rollers. Keys in [keys] are filled in [color] (the note's letter colour)
 * with an ink outline, the rest are open; with no [keys] every key is drawn open.
 * [noteLabel] names the note for accessibility. Draw it [FINGERING_ASPECT_RATIO] wide to tall.
 */
@Composable
fun FingeringDiagram(
    keys: Set<FluteKey>?,
    noteLabel: String?,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val colors = FluteTheme.colors
    val description = if (keys != null && noteLabel != null) stringResource(R.string.fingering_for, noteLabel) else null

    Canvas(
        modifier = modifier.then(
            if (description != null) Modifier.semantics { contentDescription = description } else Modifier,
        ),
    ) {
        val unit = size.width / SPAN
        val radius = unit * 0.37f
        val small = radius * 0.62f
        val pressedStroke = Stroke(width = size.height * 3f / DESIGN_HEIGHT)
        val openStroke = Stroke(width = size.height * 2.5f / DESIGN_HEIGHT)
        val centreY = size.height / 2f
        val lowY = centreY + radius * 0.55f // the little-finger keys sit a bit lower, as on the flute

        // The track: a pill nearly as tall as the canvas.
        val trackHeight = size.height * 44f / DESIGN_HEIGHT
        drawRoundRect(
            color = colors.track,
            topLeft = Offset(0f, centreY - trackHeight / 2f),
            size = Size(size.width, trackHeight),
            cornerRadius = CornerRadius(trackHeight / 2f),
        )
        drawRoundRect(
            color = colors.border,
            topLeft = Offset(0f, centreY - trackHeight / 2f),
            size = Size(size.width, trackHeight),
            cornerRadius = CornerRadius(trackHeight / 2f),
            style = Stroke(width = size.height * 2f / DESIGN_HEIGHT),
        )

        fun x(slot: Float) = (MARGIN + slot) * unit

        fun circle(slot: Float, r: Float, y: Float, down: Boolean) {
            val centre = Offset(x(slot), y)
            drawCircle(if (down) color else colors.raised, r, centre)
            drawCircle(if (down) colors.outline else colors.mutedText, r, centre, style = if (down) pressedStroke else openStroke)
        }

        fun paddle(slot: Float, width: Float, height: Float, y: Float, down: Boolean) {
            val topLeft = Offset(x(slot) - width / 2f, y - height / 2f)
            val corner = CornerRadius(width * 0.4f)
            drawRoundRect(if (down) color else colors.raised, topLeft, Size(width, height), corner)
            drawRoundRect(
                if (down) colors.outline else colors.mutedText, topLeft, Size(width, height), corner,
                style = if (down) pressedStroke else openStroke,
            )
        }

        fun down(key: FluteKey) = keys?.contains(key) == true

        paddle(THUMB_SLOT, radius * 1.15f, radius * 2.45f, centreY, down(FluteKey.THUMB))
        circle(L1_SLOT, radius, centreY, down(FluteKey.L1))
        circle(L1_SLOT + 1f, radius, centreY, down(FluteKey.L2))
        circle(L1_SLOT + 2f, radius, centreY, down(FluteKey.L3))
        circle(G_SHARP_SLOT, small, lowY, down(FluteKey.G_SHARP))
        circle(R1_SLOT, radius, centreY, down(FluteKey.R1))
        circle(R1_SLOT + 1f, radius, centreY, down(FluteKey.R2))
        circle(R1_SLOT + 2f, radius, centreY, down(FluteKey.R3))
        circle(E_FLAT_SLOT, small, lowY, down(FluteKey.E_FLAT))
        paddle(C_SHARP_SLOT, small * 1.3f, small * 2.2f, lowY, down(FluteKey.C_SHARP))
        paddle(LOW_C_SLOT, small * 1.3f, small * 2.2f, lowY, down(FluteKey.LOW_C))
    }
}

/** Width over height of the diagram as the canvas drew it. */
const val FINGERING_ASPECT_RATIO = 388f / 56f
private const val DESIGN_HEIGHT = 56f

// Horizontal slots, in key widths; the right hand starts after a wider gap.
private const val MARGIN = 0.5f
private const val THUMB_SLOT = 0f
private const val L1_SLOT = 1f
private const val G_SHARP_SLOT = 4f
private const val R1_SLOT = 5.6f
private const val E_FLAT_SLOT = 8.6f
private const val C_SHARP_SLOT = 9.4f
private const val LOW_C_SLOT = 10.1f
private const val SPAN = LOW_C_SLOT + 2 * MARGIN
