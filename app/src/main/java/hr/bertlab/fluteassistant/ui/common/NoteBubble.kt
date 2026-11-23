// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.bertlab.fluteassistant.ui.theme.FluteTheme
import hr.bertlab.fluteassistant.ui.theme.InkOnFill

/**
 * A note in its letter's colour ([noteColor]): a round [chunky] bubble with the name and its
 * raised octave figure ([noteText]).
 */
@Composable
fun NoteBubble(
    midi: Int,
    size: Dp,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    depth: Dp = 6.dp,
) {
    val style = MaterialTheme.typography.displayLarge.copy(fontSize = fontSize, lineHeight = fontSize, letterSpacing = 0.sp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .chunky(CircleShape, noteColor(midi), FluteTheme.colors.outline, depth),
    ) {
        Text(noteText(midi), style = style, color = InkOnFill, textAlign = TextAlign.Center, maxLines = 1)
    }
}

/** A dashed outline in [shape], for places that are empty or not chosen. */
fun Modifier.dashedBorder(width: Dp, color: Color, shape: Shape): Modifier = drawBehind {
    val stroke = width.toPx()
    val outline = shape.createOutline(size, layoutDirection, this)
    drawOutline(
        outline = outline,
        color = color,
        style = Stroke(width = stroke * 2, pathEffect = PathEffect.dashPathEffect(floatArrayOf(stroke * 2.4f, stroke * 1.8f))),
    )
}
