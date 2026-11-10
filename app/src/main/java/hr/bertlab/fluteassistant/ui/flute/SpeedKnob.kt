// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.flute

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.domain.metronome.tempoBand
import hr.bertlab.fluteassistant.ui.AppTab
import hr.bertlab.fluteassistant.ui.common.ChunkyButton
import hr.bertlab.fluteassistant.ui.common.Emoji
import hr.bertlab.fluteassistant.ui.common.PressDepth
import hr.bertlab.fluteassistant.ui.common.facingRight
import hr.bertlab.fluteassistant.ui.common.labelRes
import hr.bertlab.fluteassistant.ui.theme.FluteTheme
import hr.bertlab.fluteassistant.ui.theme.Sun

/** Width and height of [SpeedKnob]; the staff's tune starts further in, so it never covers a note. */
private val SpeedKnobSize: Dp = StaffGeometry.CORNER_BUTTON.dp

/**
 * The metronome's yellow knob at the flute staff's top left, where printed music writes the tempo:
 * the tab bar's drum, which says what the knob opens, and in a badge at its lower right the animal
 * of the speed at [bpm], facing right, which says how fast. A tap is [onClick]. While the tune is
 * [running] (Play or Your turn) the animal is away on the staff, walking the tune, and its badge
 * with it: the drum stays, faded and not pressable, and the animal comes home when the run ends. A
 * screen reader hears the animal's name ("Speed: Rabbit, tap to change"), never a number.
 */
@Composable
fun SpeedKnob(
    bpm: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    running: Boolean = false,
) {
    val band = tempoBand(bpm)
    val description = stringResource(R.string.flute_speed_knob, stringResource(band.labelRes()))
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(modifier) {
        ChunkyButton(
            onClick = onClick,
            shape = CircleShape,
            color = Sun,
            depth = StaffGeometry.CORNER_SHADOW.dp,
            enabled = !running,
            interactionSource = source,
            modifier = Modifier
                .size(SpeedKnobSize)
                .semantics { contentDescription = description },
        ) {
            Emoji(
                AppTab.METRONOME.emoji,
                fontSize = emojiSizeFor(StaffGeometry.KNOB_DRUM),
                modifier = Modifier.offset(x = -StaffGeometry.KNOB_DRUM_SHIFT.dp, y = -StaffGeometry.KNOB_DRUM_SHIFT.dp),
            )
        }
        // Beside the button, not in it: the button clips to its disc and the badge reaches past it.
        if (!running) {
            AnimalBadge(
                emoji = band.emoji,
                pressed = pressed,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = StaffGeometry.KNOB_BADGE_OVERHANG.dp, y = StaffGeometry.KNOB_BADGE_OVERHANG.dp),
            )
        }
    }
}

/**
 * The speed's animal on yellow, as it stood on the knob before the drum. It takes no touches, so a
 * tap goes through to the knob, and sinks with the knob while [pressed].
 */
@Composable
private fun AnimalBadge(emoji: String, pressed: Boolean, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(StaffGeometry.KNOB_BADGE.dp)
            .graphicsLayer { translationY = if (pressed) PressDepth.toPx() else 0f }
            .clip(CircleShape)
            .background(Sun)
            .border(StaffGeometry.KNOB_BADGE_EDGE.dp, FluteTheme.colors.outline, CircleShape),
    ) {
        Emoji(emoji, fontSize = emojiSizeFor(StaffGeometry.KNOB_BADGE_ANIMAL), modifier = Modifier.facingRight(emoji))
    }
}
