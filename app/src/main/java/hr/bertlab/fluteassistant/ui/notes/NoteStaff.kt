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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
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
import androidx.compose.ui.unit.toSize
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.domain.notes.NotesRound
import hr.bertlab.fluteassistant.domain.notes.StaffNotes
import hr.bertlab.fluteassistant.ui.common.drawTrebleClef
import hr.bertlab.fluteassistant.ui.common.noteColor
import hr.bertlab.fluteassistant.ui.theme.FluteTheme
import hr.bertlab.fluteassistant.ui.theme.Sun
import kotlin.math.min

/**
 * Where the note column sits, as a fraction of the width: the middle, clear of the clef on the
 * left and of a right-handed finger sliding on the right, so the see-through note stays in view.
 */
private const val NOTE_COLUMN = 0.5f

/** How far apart the columns of a Hear phrase sit, as a fraction of the width: a head and a bit between them. */
private const val COLUMN_STEP = 0.16f

/**
 * Hear's slots, the staff's answer to the first child test (2026-11-09: "it has confused player what needs to
 * be done, why first one is marked and second is not"). Every note still to find stands on the staff as a tall
 * dashed capsule in its column, so the child sees where a note goes and how many are left. Its half width is
 * [SLOT_HALF_WIDTH] pitches, or [SLOT_STEP_SHARE] of a column step where that is less (a narrow staff), so
 * neighbours keep a gap; its corners are round to the half width, the whole of its ends.
 */
private const val SLOT_HALF_WIDTH = 1f
private const val SLOT_STEP_SHARE = 0.42f

/**
 * A slot's look, as fractions of full alpha: the note to find is a pale yellow, the later ones have no fill; a
 * slot whose note is sounding ([SLOT_LIT_ALPHA]) is a strong yellow. The dashes are 6 dp on and 4 off, as the
 * see-through note's.
 */
private const val SLOT_FILL_ALPHA = 0.28f
private const val SLOT_LIT_ALPHA = 0.75f
private const val SLOT_DASH_ON = 6
private const val SLOT_DASH_OFF = 4

/**
 * The ❓ in a slot, in pitches, its alpha when the slot is a later one, and its size while its note sounds
 * (it grows, which with the strong yellow is the pulse: the state flips per note, nothing animates).
 */
private const val SLOT_MARK = 1.4f
private const val SLOT_MARK_LIT = 1.6f
private const val SLOT_MARK_LATER_ALPHA = 0.35f

/**
 * The halo behind a head whose note is sounding: [HALO_RADIUS] pitches round its middle (1.15 reached the next
 * capsule at true size), [HALO_ALPHA] of the sun. On a narrow staff the next capsule's edge is nearer than that
 * (a column step less the slot's half width), and the halo stops there.
 */
private const val HALO_RADIUS = 1f
private const val HALO_ALPHA = 0.7f

/**
 * The columns of a phrase of [count] notes, as fractions of the width: spread evenly around
 * the middle, so the longest phrase of four still leaves the clef its room on the left and the
 * finger its room on the right. One note, or none (Find), is [NOTE_COLUMN] alone.
 */
internal fun phraseColumns(count: Int): List<Float> =
    if (count <= 1) listOf(NOTE_COLUMN) else List(count) { NOTE_COLUMN + (it - (count - 1) / 2f) * COLUMN_STEP }

/**
 * The columns that hold a slot, the stand-in for a note still to find. In Hear, every note of the phrase from
 * [placed] on, less the one just found while [solved] (its head stands there). In Find, which has no phrase, its
 * one column until its note is found (since 2026-11-10: Find's staff showed nothing to do, as Hear's had).
 */
internal fun slotIndices(phraseSize: Int, placed: Int, solved: Boolean): List<Int> = when {
    phraseSize == 0 -> if (solved) emptyList() else listOf(0)
    else -> (placed until phraseSize).filterNot { solved && it == placed }
}

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
 * The staff of the round in [state]. A treble staff that fills whatever height it is given: the places shown are
 * [StaffNotes.visiblePositions] of [NotesRound.practiceNotes] (the staff and its two neighbouring spaces,
 * plus the ledger-line notes only as far as a chosen note needs them), and the size of a place
 * follows from the height, so the fewer places the bigger they are. Every place is a
 * full-width band, the spaces faintly tinted, the lines thick in the text colour, the clef
 * faint at the left.
 *
 * The child answers by touch and slide: a finger down puts a see-through note on the nearest
 * place ([placing], reported through [onPlacingChange]) and turns its band yellow; it follows
 * the finger with a small tick at each new place, and lifting the finger answers that place
 * through [onNoteTapped]. Sliding off the staff cancels. Each of [NotesRound.wrongTaps] gets a cross,
 * the latest ([NotesRound.lastWrongTap]) with an arrow pointing up or down towards [NotesRound.currentMidi]; once
 * [NotesRound.solved], the note sits on its place in its letter's colour with sparkles. The staff takes
 * no touch while the answer shows, nor while the phrase sounds ([NotesRound.phrasePlaying]).
 *
 * In Hear the staff holds the whole [NotesRound.phrase] across the width, one column a note
 * ([phraseColumns]): the first [NotesRound.placed] notes sit in their columns in their letters' colours
 * (never with sparkles or dashes, as they are given or done), and everything else above, the
 * far places' dashed ledger stubs, the see-through note, the crosses and the sparkles of the
 * last note, belongs to the column of the note to place, [NotesRound.placed]. In Find the [NotesRound.phrase] is
 * empty and the staff is the one column in the middle. [NotesRound.hearing] only changes the staff's
 * description for a screen reader.
 *
 * Under every note, a slot for each note still to find ([slotIndices]: Hear's phrase from the note to place on,
 * and Find's one note): a dashed capsule in its column from the
 * top place to the bottom one with a ❓ in it, the next one pale yellow and the later ones faint, so the child
 * sees what the staff is asking and how much is left (the first child test, 2026-11-09). The ❓ of the note to
 * place gives way to the see-through note while a finger is down, and every ❓ gives way to the crosses while a
 * wrong tap stands. While the phrase plays, the note at index [NotesRound.sounding] lights: a halo behind its head, or
 * its slot a strong yellow with a bigger ❓. Slots are drawn before every note, so a slot never hides one.
 *
 * For assistive technology every place is also a button named by position, never by note
 * (so a screen reader does not give the answer away), that answers at once.
 */
@Composable
fun NoteStaff(
    state: NotesRound,
    placing: Int?,
    onPlacingChange: (Int?) -> Unit,
    onNoteTapped: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentMidi = state.currentMidi
    val wrongTaps = state.wrongTaps
    val lastWrongTap = state.lastWrongTap
    val solved = state.solved
    val practiceNotes = state.practiceNotes
    val phrase = state.phrase
    val placed = state.placed
    val locked = state.phrasePlaying
    val hearing = state.hearing
    val sounding = state.sounding
    val colors = FluteTheme.colors
    val haptics = LocalHapticFeedback.current
    val staffDescription = stringResource(if (hearing) R.string.staff_description_hear else R.string.staff_description)
    val columns = phraseColumns(phrase.size)
    val currentColumn = columns[placed.coerceIn(0, columns.lastIndex)]
    val visible = StaffNotes.visiblePositions(practiceNotes)
    val currentPosition = StaffNotes.positionOf(currentMidi)
    val wrongPositions = wrongTaps.map(StaffNotes::positionOf).filter { it in visible }
    val lastWrongPosition = lastWrongTap?.let(StaffNotes::positionOf)?.takeIf { it in visible }
    val enabled = !solved && !locked
    val currentOnPlacingChange by rememberUpdatedState(onPlacingChange)
    val currentOnNoteTapped by rememberUpdatedState(onNoteTapped)
    val placedNotes = phrase.take(placed).map { it to noteColor(it) }
    val noteColor = noteColor(currentMidi)
    val sparkle = rememberTextMeasurer()
    // The layer paint of a faded emoji: one, whose alpha is set for each, not one per emoji per frame.
    val fadePaint = remember { Paint() }
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
                    // Finally: a gesture cut short (the staff locking or unlocking under the finger restarts this block)
                    // must let go of the place too, or the speaker bubble would wait for a lift that never comes.
                    try {
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                // A lift already consumed is a cancel (the system took the touch back, or the staff went
                                // away under the finger), not a child letting go: it answers nothing.
                                if (!change.isConsumed) position?.let { currentOnNoteTapped(StaffNotes.midiAt(it)) }
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
                    } finally {
                        currentOnPlacingChange(null)
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val pitch = size.height / (visible.count() + 2 * END_MARGIN_PLACES)
            val space = pitch * 2f
            val lineWidth = 3.dp.toPx()
            fun yOf(position: Int) = (visible.last - position + END_MARGIN_PLACES + 0.5f) * pitch
            val x = size.width * currentColumn

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

            fun emoji(text: String, centre: Offset, sizePx: Float, alpha: Float = 1f) {
                val layout = sparkle.measure(text, emojiStyle.copy(fontSize = (sizePx / density / fontScale).sp))
                val topLeft = centre - Offset(layout.size.width / 2f, layout.size.height / 2f)
                if (alpha >= 1f) {
                    drawText(layout, topLeft = topLeft)
                } else {
                    // A layer, not drawText's alpha: that fades the colour the text is painted in, and an emoji brings its own.
                    val bounds = Rect(topLeft, layout.size.toSize()).inflate(sizePx * 0.25f)
                    fadePaint.alpha = alpha
                    drawContext.canvas.saveLayer(bounds, fadePaint)
                    drawText(layout, topLeft = topLeft)
                    drawContext.canvas.restore()
                }
            }

            // The slot of a note still to find, under every note. The note to place has a pale yellow and an outline in
            // the text colour (the outline colour is nearly the card's own at night); the later ones only a light outline.
            // The ❓ gives way to the see-through note while a finger is down, and to the crosses and the arrow while a
            // wrong tap stands: they are drawn over the middle of the column, where the ❓ is.
            val columnStepPx = size.width * COLUMN_STEP
            val slotHalf = min(pitch * SLOT_HALF_WIDTH, columnStepPx * SLOT_STEP_SHARE)
            val slotTop = yOf(visible.last) - pitch / 2
            val slotBottom = yOf(visible.first) + pitch / 2
            fun slot(index: Int) {
                val centreX = size.width * columns[index]
                val current = index == placed
                val lit = sounding == index
                val topLeft = Offset(centreX - slotHalf, slotTop)
                val slotSize = Size(slotHalf * 2, slotBottom - slotTop)
                val fill = when {
                    lit -> Sun.copy(alpha = SLOT_LIT_ALPHA)
                    current -> Sun.copy(alpha = SLOT_FILL_ALPHA)
                    else -> null
                }
                if (fill != null) drawRoundRect(fill, topLeft, slotSize, CornerRadius(slotHalf))
                drawRoundRect(
                    if (current) colors.text else colors.border, topLeft, slotSize, CornerRadius(slotHalf),
                    style = Stroke(lineWidth, pathEffect = PathEffect.dashPathEffect(floatArrayOf(SLOT_DASH_ON.dp.toPx(), SLOT_DASH_OFF.dp.toPx()))),
                )
                if (wrongTaps.isEmpty() && (!current || placing == null)) {
                    emoji(
                        QUESTION_EMOJI, Offset(centreX, (slotTop + slotBottom) / 2), pitch * if (lit) SLOT_MARK_LIT else SLOT_MARK,
                        alpha = if (current || lit) 1f else SLOT_MARK_LATER_ALPHA,
                    )
                }
            }
            // The notes still to find (Find's one too); once solved, the found note stands where the current slot was.
            val slots = slotIndices(phrase.size, placed, solved)
            slots.forEach(::slot)

            // The ledger lines the far places need, dashed until a note sits on them. In Hear the column being answered
            // has its slot, and the stub is clipped to the capsule: cut to its half width it still poked out at the
            // rounded end, where the capsule is narrower than that.
            val ledgerHalf = pitch * LEDGER_HALF
            val slotDrawn = placed in slots
            val stubHalf = if (slotDrawn) min(ledgerHalf, slotHalf) else ledgerHalf
            val dashes = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 6.dp.toPx()))
            fun stubs() {
                for (position in visible) {
                    if (position % 2 == 0 && position !in StaffNotes.LINE_POSITIONS) {
                        drawLine(
                            colors.mutedText, Offset(x - stubHalf, yOf(position)), Offset(x + stubHalf, yOf(position)),
                            lineWidth, pathEffect = dashes,
                        )
                    }
                }
            }
            if (slotDrawn) {
                val slotShape = RoundRect(x - slotHalf, slotTop, x + slotHalf, slotBottom, CornerRadius(slotHalf))
                clipPath(Path().apply { addRoundRect(slotShape) }) { stubs() }
            } else {
                stubs()
            }

            fun head(position: Int, fill: Color, dashed: Boolean = false, centreX: Float = x) {
                val topLeft = Offset(centreX - pitch * HEAD_RX, yOf(position) - pitch * HEAD_RY)
                val headSize = Size(pitch * HEAD_RX * 2, pitch * HEAD_RY * 2)
                StaffNotes.ledgerLinesFor(position).forEach {
                    drawLine(colors.text, Offset(centreX - ledgerHalf, yOf(it)), Offset(centreX + ledgerHalf, yOf(it)), lineWidth)
                }
                drawOval(fill, topLeft, headSize)
                drawOval(
                    colors.outline, topLeft, headSize,
                    style = Stroke(lineWidth, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())) else null),
                )
            }

            // The halo of the note sounding, behind its head: a pitch round it, or less where the next capsule's edge is
            // nearer (a column step less the slot's half width), so it never reaches the next slot.
            fun halo(position: Int, centreX: Float) = drawCircle(
                Sun.copy(alpha = HALO_ALPHA), min(pitch * HALO_RADIUS, columnStepPx - slotHalf), Offset(centreX, yOf(position)),
            )

            // Hear: the notes of the phrase already on the staff, each in its own column.
            placedNotes.forEachIndexed { index, (midi, fill) ->
                val position = StaffNotes.positionOf(midi)
                if (position in visible) {
                    if (sounding == index) halo(position, size.width * columns[index])
                    head(position, fill, centreX = size.width * columns[index])
                }
            }
            // The last note's own halo goes under the crosses with the others; its head is drawn with its sparkles.
            if (solved && sounding == placed && currentPosition in visible) halo(currentPosition, x)

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
private const val QUESTION_EMOJI = "\u2753" // red question mark, the slot's and the Hear prompt's symbol
