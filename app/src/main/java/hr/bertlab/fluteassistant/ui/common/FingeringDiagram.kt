// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.domain.tuner.FluteFingerings
import hr.bertlab.fluteassistant.ui.common.FingeringGeometry.Spot
import hr.bertlab.fluteassistant.ui.theme.FluteTheme
import kotlin.math.min

/**
 * The flute seen from above by the player, the head joint on the left (since 2026-11-17, a teacher's ask: "not how
 * they mark keys"; spec `2026-11-17-fingers-tab-design.md`): the tube with its lip plate and joints, the seven cups no
 * finger rests on drawn faintly, and the fifteen keys a finger touches, each where it sits ([FingeringGeometry]).
 * The keys [midi] presses (`FluteFingerings`) are filled in [color], the note's letter colour unless the caller says
 * otherwise, with an ink outline, and the rest are open; the note is named for accessibility. With no [midi] every key
 * is open and nothing is said. No words: the Fingers tab writes the hands' names round it. It keeps its own shape
 * ([FINGERING_ASPECT_RATIO]), so a caller gives it a width or a height. The tuner, Listen to me and the Fingers tab
 * all show this one picture.
 */
@Composable
fun FingeringDiagram(
    midi: Int?,
    modifier: Modifier = Modifier,
    color: Color = if (midi != null) noteColor(midi) else Color.Unspecified,
) {
    val colors = FluteTheme.colors
    val keys = midi?.let(FluteFingerings::keysFor)
    val description = midi?.let { stringResource(R.string.fingering_for, noteLabel(it)) }

    Canvas(
        modifier = modifier
            .aspectRatio(FINGERING_ASPECT_RATIO)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
    ) {
        val unit = min(size.width / FingeringGeometry.WIDTH, size.height / FingeringGeometry.HEIGHT)
        fun u(value: Float) = value * unit

        // The tube.
        val tubeTop = u(FingeringGeometry.CENTRE_Y - FingeringGeometry.TUBE_THICKNESS / 2)
        val tubeSize = Size(u(FingeringGeometry.TUBE_RIGHT - FingeringGeometry.TUBE_LEFT), u(FingeringGeometry.TUBE_THICKNESS))
        val tubeCorner = CornerRadius(tubeSize.height / 2)
        drawRoundRect(colors.track, Offset(u(FingeringGeometry.TUBE_LEFT), tubeTop), tubeSize, tubeCorner)
        drawRoundRect(colors.border, Offset(u(FingeringGeometry.TUBE_LEFT), tubeTop), tubeSize, tubeCorner, style = Stroke(u(2f)))

        // The joints.
        FingeringGeometry.JOINTS.forEach { x ->
            drawRoundRect(
                colors.border,
                Offset(u(x), u(FingeringGeometry.CENTRE_Y - FingeringGeometry.JOINT_HEIGHT / 2)),
                Size(u(FingeringGeometry.JOINT_WIDTH), u(FingeringGeometry.JOINT_HEIGHT)),
                CornerRadius(u(2f)),
            )
        }

        // The lip plate and its hole.
        spot(FingeringGeometry.LIP_PLATE, unit, colors.raised, colors.mutedText, line = 2f)
        spot(FingeringGeometry.LIP_HOLE, unit, colors.mutedText, null, line = 0f)

        // The cups no finger rests on.
        FingeringGeometry.IDLE_CUPS.forEach { spot(it, unit, colors.raised, colors.border, line = 2f) }

        // The arms from the tube to the keys that stand off it.
        FingeringGeometry.ARMS.forEach { arm ->
            drawLine(colors.border, Offset(u(arm.x1), u(arm.y1)), Offset(u(arm.x2), u(arm.y2)), u(3f), StrokeCap.Round)
        }

        // The keys.
        FingeringGeometry.KEYS.forEach { (key, place) ->
            val down = keys?.contains(key) == true
            spot(
                place, unit,
                fill = if (down) color else colors.raised,
                outline = if (down) colors.outline else colors.mutedText,
                line = if (down) PRESSED_LINE else OPEN_LINE,
            )
        }
    }
}

/** One key, cup or plate: an oval when [Spot.round], else a rounded rectangle, turned about its centre. */
private fun DrawScope.spot(place: Spot, unit: Float, fill: Color, outline: Color?, line: Float) {
    val centre = Offset(place.x * unit, place.y * unit)
    val size = Size(place.width * unit, place.height * unit)
    val topLeft = Offset(centre.x - size.width / 2, centre.y - size.height / 2)
    rotate(place.turn, pivot = centre) {
        if (place.round) {
            drawOval(fill, topLeft, size)
            if (outline != null) drawOval(outline, topLeft, size, style = Stroke(line * unit))
        } else {
            val corner = CornerRadius(min(size.width, size.height) * CORNER_SHARE)
            drawRoundRect(fill, topLeft, size, corner)
            if (outline != null) drawRoundRect(outline, topLeft, size, corner, style = Stroke(line * unit))
        }
    }
}

/** The outline of a pressed key and of an open one, in design units (as the strip before it drew them). */
private const val PRESSED_LINE = 3f
private const val OPEN_LINE = 2.5f

/** A lever's or a thumb key's corners, as a share of its shorter side. */
private const val CORNER_SHARE = 0.42f
