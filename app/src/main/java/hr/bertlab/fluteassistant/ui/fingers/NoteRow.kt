// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.fingers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.ui.common.FINGERING_ASPECT_RATIO
import hr.bertlab.fluteassistant.ui.common.FingeringDiagram
import hr.bertlab.fluteassistant.ui.common.FingeringGeometry
import hr.bertlab.fluteassistant.ui.common.OutlineWidth
import hr.bertlab.fluteassistant.ui.common.asTextSize
import hr.bertlab.fluteassistant.ui.common.noteColor
import hr.bertlab.fluteassistant.ui.common.noteText
import hr.bertlab.fluteassistant.ui.theme.FluteTheme
import hr.bertlab.fluteassistant.ui.theme.InkOnFill
import kotlin.math.roundToInt

/**
 * One note upright: a white card with the note on its staff in music symbols, and beside it the note's name over the
 * chart and the hands named under it ([ChartWithHands]; spec `2026-11-24-fingers-music-symbols-and-book-chart-design.md`,
 * part 3). On its side the tab has a row of its own with no hand names (`FingersSideways`).
 *
 * The whole card is the button (the user, 2026-11-24: "make whole white rectangle as link for opening tuner tune
 * view"): a tap anywhere on it is [onTune] with [midi] ([tuneOnTap]); until then only the chart was.
 *
 * The staff asks for its full size ([SymbolStaff.TALL], an 8.2 dp line gap, its signs held) and takes less when the row is shorter;
 * the row is never under [RowHeight] nor taller than [tallest], what four rows have room for. The chart fills the
 * row's width as far as that height allows: on a wide and short tab it stops short of the card's end, and the staff
 * and the chart stand in the middle of the card.
 */
@Composable
internal fun NoteRow(midi: Int, tallest: Dp, onTune: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = FluteTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        modifier = modifier
            .heightIn(min = RowHeight, max = maxOf(tallest, RowHeight))
            .clip(RowShape)
            .background(colors.card, RowShape)
            .border(OutlineWidth, colors.border, RowShape)
            .tuneOnTap(stringResource(R.string.fingers_tune), midi, onTune)
            .padding(horizontal = 12.dp, vertical = RowPadding),
    ) {
        NoteOnStaff(midi, Modifier.width(StaffWidth).height(SymbolStaff.TALL.dp).testTag("fingers_staff"))
        ChartWithHands(midi, Modifier.weight(1f, fill = false))
    }
}

/**
 * The chart for [midi] with the words round it: the note's pill on a line of its own over it, and under it two
 * brackets with "Left hand" and "Right hand". The chart is as wide as it is given, or as wide as the height it is given
 * leaves it once the pill's line and the names' line are measured, whichever is less; nothing here is an estimate of a
 * line's height.
 *
 * The words are the row's own text, in dp, so a narrow phone or large text cannot push them into the picture. Each name
 * is centred on its bracket and kept inside it, a span along the chart in the picture's own units
 * ([FingeringGeometry.LEFT_HAND_SPAN], [FingeringGeometry.RIGHT_HAND_SPAN] of [FingeringGeometry.WIDTH]), so it follows
 * its hand's keys as the chart grows or shrinks with the row. In a pane too narrow for a name's bracket it still stays
 * inside the chart's width.
 */
@Composable
private fun ChartWithHands(midi: Int, modifier: Modifier = Modifier) {
    Layout(
        content = {
            NotePill(midi)
            FingeringDiagram(midi)
            HandBrackets()
            HandName(stringResource(R.string.hand_left))
            HandName(stringResource(R.string.hand_right))
        },
        modifier = modifier,
    ) { (pillBox, chartBox, bracketsBox, leftName, rightName), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val pill = pillBox.measure(loose)
        val left = leftName.measure(loose)
        val right = rightName.measure(loose)
        val bracketHeight = BracketHeight.roundToPx()
        val over = pill.height + PillGap.roundToPx()
        val under = bracketHeight + maxOf(left.height, right.height)
        val room = if (constraints.hasBoundedHeight) ((constraints.maxHeight - over - under) * FINGERING_ASPECT_RATIO).toInt() else Int.MAX_VALUE
        val width = minOf(constraints.maxWidth, room).coerceAtLeast(0)
        val chartHeight = (width / FINGERING_ASPECT_RATIO).roundToInt()
        val chart = chartBox.measure(Constraints.fixed(width, chartHeight))
        val brackets = bracketsBox.measure(Constraints.fixed(width, bracketHeight))

        // Centred on its bracket and kept inside it; in a pane too narrow for that, still inside the chart.
        fun nameX(span: ClosedFloatingPointRange<Float>, name: Placeable): Int {
            val from = width * span.start / FingeringGeometry.WIDTH
            val to = width * span.endInclusive / FingeringGeometry.WIDTH
            val inBracket = ((from + to) / 2 - name.width / 2f).coerceIn(from, maxOf(from, to - name.width))
            return inBracket.coerceIn(0f, maxOf(0f, (width - name.width).toFloat())).roundToInt()
        }
        layout(width, over + chartHeight + under) {
            pill.place(0, 0)
            chart.place(0, over)
            brackets.place(0, over + chartHeight)
            left.place(nameX(FingeringGeometry.LEFT_HAND_SPAN, left), over + chartHeight + bracketHeight)
            right.place(nameX(FingeringGeometry.RIGHT_HAND_SPAN, right), over + chartHeight + bracketHeight)
        }
    }
}

/**
 * The two hands' brackets, as wide as the chart over them: each from its span's start down, across and up to its end,
 * the ends turned up towards the keys.
 */
@Composable
private fun HandBrackets() {
    val color = FluteTheme.colors.border
    Canvas(Modifier) {
        val line = BracketLine.toPx()
        val ends = line / 2
        val across = BracketDrop.toPx()
        listOf(FingeringGeometry.LEFT_HAND_SPAN, FingeringGeometry.RIGHT_HAND_SPAN).forEach { span ->
            val from = size.width * span.start / FingeringGeometry.WIDTH
            val to = size.width * span.endInclusive / FingeringGeometry.WIDTH
            val path = Path().apply {
                moveTo(from, ends)
                lineTo(from, across)
                lineTo(to, across)
                lineTo(to, ends)
            }
            drawPath(path, color, style = Stroke(line, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** The note's name in a small pill of its letter's colour, the octave raised as everywhere in the app. */
@Composable
internal fun NotePill(midi: Int, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .testTag("fingers_pill")
            .height(PillHeight)
            .background(noteColor(midi), CircleShape)
            .border(2.dp, FluteTheme.colors.outline, CircleShape)
            .padding(horizontal = 8.dp),
    ) {
        Text(
            noteText(midi),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 15.dp.asTextSize(), lineHeight = 18.dp.asTextSize()),
            color = InkOnFill,
            maxLines = 1,
        )
    }
}

/**
 * A row of [midi] as the button that opens it: a tap anywhere on the row is [onTune] with [midi], and the frame opens the
 * note in the tuner's Tune mode (since 2026-11-18, the user's ask; the whole row since 2026-11-24, before only the
 * chart). [label] is what a screen reader says the tap does (`fingers_tune`). It acts on a tap, not as the finger goes
 * down, so a slide that scrolls the rows opens nothing. The row's words and the chart's description are what a screen
 * reader hears of it.
 */
internal fun Modifier.tuneOnTap(label: String, midi: Int, onTune: (Int) -> Unit): Modifier =
    clickable(onClickLabel = label, role = Role.Button) { onTune(midi) }

@Composable
private fun HandName(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.dp.asTextSize(), lineHeight = 14.dp.asTextSize()),
        color = FluteTheme.colors.mutedText,
        maxLines = 1,
    )
}

/** A row's least height, and its padding over and under (5 dp since 2026-11-24, so a row at its least gives the staff 98 dp). */
private val RowHeight = 108.dp
private val RowPadding = 5.dp
private val PillHeight = 24.dp

/** Between the pill's line and the chart. */
private val PillGap = 4.dp

/** The brackets' room under the chart, how far down their line runs, and its width. */
private val BracketHeight = 6.dp
private val BracketDrop = 4.dp
private val BracketLine = 2.dp

/** The staff's width (74 dp, for an 8.2 dp line gap; 60 before 2026-11-24). */
private val StaffWidth = 74.dp
private val RowShape = RoundedCornerShape(24.dp)
