// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.fingers

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
 * One note upright: a white card with the note on its staff, and beside it the flute with the note's name and "Left
 * hand" over it and "Right hand" under it ([FluteWithHands]). On its side the tab has a row of its own with no hand
 * names (`FingersSideways`).
 *
 * The flute fills the row's width, but the row is never taller than [tallest], what four rows have room for: on a wide
 * and short tab the flute stops short of the card's end, and the staff and the flute stand in the middle of the card.
 */
@Composable
internal fun NoteRow(midi: Int, tallest: Dp, onTune: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = FluteTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        modifier = modifier
            .heightIn(min = RowHeight, max = maxOf(tallest, RowHeight))
            .background(colors.card, RowShape)
            .border(OutlineWidth, colors.border, RowShape)
            .padding(horizontal = 12.dp, vertical = RowPadding),
    ) {
        NoteOnStaff(midi, Modifier.width(StaffWidth).height(RowHeight - RowPadding * 2))
        FluteWithHands(midi, onTune, Modifier.weight(1f, fill = false))
    }
}

/**
 * The flute for [midi] with the words round it: a line over it with the note's pill at its start and "Left hand", and
 * a line under it with "Right hand". The flute is as wide as it is given, or as wide as the height it is given leaves
 * it once the two lines are measured, whichever is less; nothing here is an estimate of a line's height.
 *
 * The words are the row's own text, in dp, so a narrow phone or large text cannot push them into the picture (spec,
 * part 3). Each is centred on its hand's keys, a place along the flute in the picture's own units ([LEFT_HAND_X],
 * [RIGHT_HAND_X] of [FingeringGeometry.WIDTH]), so it follows them as the flute grows or shrinks with the row; it
 * stays inside the flute's width, and "Left hand" right of the pill with [LeadGap] between, so the two cannot meet.
 */
@Composable
private fun FluteWithHands(midi: Int, onTune: (Int) -> Unit, modifier: Modifier = Modifier) {
    Layout(
        content = {
            NotePill(midi)
            HandName(stringResource(R.string.hand_left))
            NoteFlute(midi, onTune)
            HandName(stringResource(R.string.hand_right))
        },
        modifier = modifier,
    ) { (pillBox, leftName, fluteBox, rightName), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val pill = pillBox.measure(loose)
        val left = leftName.measure(loose)
        val right = rightName.measure(loose)
        val over = maxOf(pill.height, left.height)
        val under = right.height
        val room = if (constraints.hasBoundedHeight) ((constraints.maxHeight - over - under) * FINGERING_ASPECT_RATIO).toInt() else Int.MAX_VALUE
        val width = minOf(constraints.maxWidth, room).coerceAtLeast(0)
        val fluteHeight = (width / FINGERING_ASPECT_RATIO).roundToInt()
        val flute = fluteBox.measure(Constraints.fixed(width, fluteHeight))

        fun across(at: Float, name: Placeable, least: Int): Int {
            val most = maxOf(least, width - name.width)
            return (width * at / FingeringGeometry.WIDTH - name.width / 2f).roundToInt().coerceIn(least, most)
        }
        layout(width, over + fluteHeight + under) {
            pill.place(0, (over - pill.height) / 2)
            left.place(across(LEFT_HAND_X, left, least = pill.width + LeadGap.roundToPx()), (over - left.height) / 2)
            flute.place(0, over)
            right.place(across(RIGHT_HAND_X, right, least = 0), over + fluteHeight)
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
 * The flute for [midi], as wide as it is given. A tap on it is [onTune] with [midi]: the frame opens the note in the
 * tuner's Tune mode (since 2026-11-18, the user's ask), and the tab keeps its note for the way back.
 */
@Composable
internal fun NoteFlute(midi: Int, onTune: (Int) -> Unit, modifier: Modifier = Modifier) {
    FingeringDiagram(
        midi,
        modifier.clickable(onClickLabel = stringResource(R.string.fingers_tune), role = Role.Button) { onTune(midi) },
    )
}

@Composable
private fun HandName(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.dp.asTextSize(), lineHeight = 14.dp.asTextSize()),
        color = FluteTheme.colors.mutedText,
        maxLines = 1,
    )
}

/** A row's least height, and what the phones it was drawn for give it (a flute about 280 dp wide at 412). */
private val RowHeight = 108.dp
private val RowPadding = 7.dp
private val PillHeight = 24.dp

/** Between the pill and "Left hand" at the least. */
private val LeadGap = 6.dp

/**
 * Where the hands' names stand along the flute, in the picture's units: the mockup's (`gen.py`, `real()`), over the
 * left hand's keys (L1 at 66 to the G sharp lever at 158) and under the right hand's (R1 at 205 to E flat at 288).
 */
private const val LEFT_HAND_X = 100f
private const val RIGHT_HAND_X = 250f

private val StaffWidth = 60.dp
private val RowShape = RoundedCornerShape(24.dp)
