// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.notes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.domain.notes.StaffNotes
import hr.bertlab.fluteassistant.ui.common.drawTrebleClef
import hr.bertlab.fluteassistant.ui.common.noteColor
import hr.bertlab.fluteassistant.ui.theme.FluteTheme
import hr.bertlab.fluteassistant.ui.theme.Sun

/**
 * Where the note column sits, as a fraction of the width: the middle, clear of the clef on the
 * left and of a right-handed finger sliding on the right, so the see-through note stays in view.
 */
private const val NOTE_COLUMN = 0.5f

/**
 * Room kept above the highest place and below the lowest, in places, so the clef's loop and
 * tail (a space beyond the staff each way) are never cut; the end places' strips extend
 * over it.
 */
private const val END_MARGIN_PLACES = 1

/** A note head's half width and half height, in pitches (a place's height). */
private const val HEAD_RX = 0.58f
private const val HEAD_RY = 0.42f

/** A wrong place's cross circle, in pitches. */
private const val CROSS_RADIUS = 0.45f

/**
 * The clef is drawn small and faint, as on the canvas: one of its units is this share of a
 * space, and its curl ([CLEF_CURL] units below its origin) sits on the G line.
 */
private const val CLEF_SCALE = 0.62f
private const val CLEF_CURL = 3.15f
private const val G_LINE = 4

/** A ledger line's half length, in pitches: a little past the note head each side. */
private const val LEDGER_HALF = 1f

/**
 * A treble staff that fills whatever height it is given: the places shown are
 * [StaffNotes.visiblePositions] of [practiceNotes] (the staff and its two neighbouring spaces,
 * plus the ledger-line notes only as far as a chosen note needs them), and the size of a place
 * follows from the height, so the fewer places the bigger they are. Every place is a
 * full-width band, the spaces faintly tinted, the lines thick in the text colour, the clef
 * faint at the left.
 *
 * The child answers by touch and slide: a finger down puts a see-through note on the nearest
 * place ([placing], reported through [onPlacingChange]) and turns its band yellow; it follows
 * the finger with a small tick at each new place, and lifting the finger answers that place
 * through [onNoteTapped]. Sliding off the staff cancels. Each of [wrongTaps] gets a cross,
 * the latest ([lastWrongTap]) with an arrow pointing up or down towards [currentMidi]; once
 * [solved], the note sits on its place in its letter's colour with sparkles. [enabled] is
 * false while the answer shows.
 *
 * For assistive technology every place is also a button named by position, never by note
 * (so a screen reader does not give the answer away), that answers at once.
 */
@Composable
fun NoteStaff(
    currentMidi: Int,
    wrongTaps: Set<Int>,
    lastWrongTap: Int?,
    solved: Boolean,
    practiceNotes: Set<Int>,
    placing: Int?,
    onPlacingChange: (Int?) -> Unit,
    onNoteTapped: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FluteTheme.colors
    val haptics = LocalHapticFeedback.current
    val staffDescription = stringResource(R.string.staff_description)
    val visible = StaffNotes.visiblePositions(practiceNotes)
    val currentPosition = StaffNotes.positionOf(currentMidi)
    val wrongPositions = wrongTaps.map(StaffNotes::positionOf).filter { it in visible }
    val lastWrongPosition = lastWrongTap?.let(StaffNotes::positionOf)?.takeIf { it in visible }
    val enabled = !solved
    val currentOnPlacingChange by rememberUpdatedState(onPlacingChange)
    val currentOnNoteTapped by rememberUpdatedState(onNoteTapped)
    val noteColor = noteColor(currentMidi)
    val sparkle = rememberTextMeasurer()
    val emojiStyle = MaterialTheme.typography.titleLarge

    Box(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = staffDescription }
            .pointerInput(visible, enabled) {
                if (!enabled) return@pointerInput
                val places = visible.count() + 2 * END_MARGIN_PLACES
                fun positionAt(y: Float): Int {
                    val fromTop = (y / size.height * places).toInt() - END_MARGIN_PLACES
                    return (visible.last - fromTop).coerceIn(visible.first, visible.last)
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    var position: Int? = positionAt(down.position.y)
                    currentOnPlacingChange(position)
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            position?.let { currentOnNoteTapped(StaffNotes.midiAt(it)) }
                            break
                        }
                        change.consume()
                        val inside = change.position.x in 0f..size.width.toFloat() &&
                            change.position.y in 0f..size.height.toFloat()
                        // Off the staff is a change of mind: nothing is answered for this touch.
                        val next = if (inside && position != null) positionAt(change.position.y) else null
                        if (next != position) {
                            position = next
                            currentOnPlacingChange(next)
                            if (next != null) haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                        }
                    }
                    currentOnPlacingChange(null)
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val pitch = size.height / (visible.count() + 2 * END_MARGIN_PLACES)
            val space = pitch * 2f
            val lineWidth = 3.dp.toPx()
            fun yOf(position: Int) = (visible.last - position + END_MARGIN_PLACES + 0.5f) * pitch
            val x = size.width * NOTE_COLUMN

            // The bands: spaces tinted, the place under the finger yellow.
            for (position in visible) {
                val band = when {
                    position == placing -> Sun.copy(alpha = 0.55f)
                    position % 2 == 1 -> colors.staffSpace
                    else -> null
                }
                if (band != null) {
                    drawRect(band, Offset(0f, yOf(position) - pitch / 2), Size(size.width, pitch))
                }
            }
            for (position in StaffNotes.LINE_POSITIONS) {
                drawLine(colors.text, Offset(0f, yOf(position)), Offset(size.width, yOf(position)), lineWidth)
            }
            drawTrebleClef(space * CLEF_SCALE, Offset(space * 0.15f, yOf(G_LINE) - space * CLEF_SCALE * CLEF_CURL), colors.mutedText.copy(alpha = 0.4f))

            // The ledger lines the far places need, dashed until a note sits on them.
            val ledgerHalf = pitch * LEDGER_HALF
            val dashes = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 6.dp.toPx()))
            for (position in visible) {
                if (position % 2 == 0 && position !in StaffNotes.LINE_POSITIONS) {
                    drawLine(
                        colors.mutedText, Offset(x - ledgerHalf, yOf(position)), Offset(x + ledgerHalf, yOf(position)),
                        lineWidth, pathEffect = dashes,
                    )
                }
            }

            fun head(position: Int, fill: Color, dashed: Boolean = false) {
                val topLeft = Offset(x - pitch * HEAD_RX, yOf(position) - pitch * HEAD_RY)
                val headSize = Size(pitch * HEAD_RX * 2, pitch * HEAD_RY * 2)
                StaffNotes.ledgerLinesFor(position).forEach {
                    drawLine(colors.text, Offset(x - ledgerHalf, yOf(it)), Offset(x + ledgerHalf, yOf(it)), lineWidth)
                }
                drawOval(fill, topLeft, headSize)
                drawOval(
                    colors.outline, topLeft, headSize,
                    style = Stroke(lineWidth, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())) else null),
                )
            }

            fun emoji(text: String, centre: Offset, sizePx: Float) {
                val layout = sparkle.measure(text, emojiStyle.copy(fontSize = (sizePx / density / fontScale).sp))
                drawText(layout, topLeft = centre - Offset(layout.size.width / 2f, layout.size.height / 2f))
            }

            for (position in wrongPositions) {
                val centre = Offset(x, yOf(position))
                val radius = pitch * CROSS_RADIUS
                drawCircle(colors.track, radius, centre)
                drawCircle(colors.mutedText, radius, centre, style = Stroke(lineWidth))
                val arm = radius * 0.4f
                val cross = 4.dp.toPx()
                drawLine(colors.mutedText, centre + Offset(-arm, -arm), centre + Offset(arm, arm), cross, StrokeCap.Round)
                drawLine(colors.mutedText, centre + Offset(arm, -arm), centre + Offset(-arm, arm), cross, StrokeCap.Round)
                if (position == lastWrongPosition && currentPosition != position) {
                    emoji(if (currentPosition > position) UP_EMOJI else DOWN_EMOJI, centre + Offset(pitch * 1.4f, 0f), pitch * 0.8f)
                }
            }
            if (solved && currentPosition in visible) {
                head(currentPosition, noteColor)
                emoji(SPARKLES_EMOJI, Offset(x + pitch * 1.25f, yOf(currentPosition) - pitch * 0.55f), pitch * 0.8f)
                emoji(SPARKLES_EMOJI, Offset(x - pitch * 1.3f, yOf(currentPosition) + pitch * 0.35f), pitch * 0.68f)
            } else if (placing != null) {
                head(placing, colors.card.copy(alpha = 0.6f), dashed = true)
            }
        }
        // One full-width place per row, the highest at the top: what assistive technology
        // sees and activates. They take no touches of their own; the staff's gesture does.
        Column(Modifier.fillMaxSize()) {
            for (position in visible.last downTo visible.first) {
                val description = stringResource(R.string.staff_position, position + 1)
                val margin = if (position == visible.last || position == visible.first) END_MARGIN_PLACES else 0
                // Weights, not heights: rounding each strip on its own would drift them off the drawing.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight((1 + margin).toFloat())
                        .semantics {
                            contentDescription = description
                            role = Role.Button
                            if (enabled) {
                                onClick {
                                    currentOnNoteTapped(StaffNotes.midiAt(position))
                                    true
                                }
                            }
                        },
                )
            }
        }
    }
}

private const val UP_EMOJI = "\u2B06\uFE0F" // up arrow
private const val DOWN_EMOJI = "\u2B07\uFE0F" // down arrow
private const val SPARKLES_EMOJI = "\u2728" // sparkles
