// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.tuner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.domain.tuner.FluteRange
import hr.bertlab.fluteassistant.ui.common.AppIcons
import hr.bertlab.fluteassistant.ui.common.ChunkyButton
import hr.bertlab.fluteassistant.ui.common.SidewaysGeometry

/**
 * The pitch window ([window], drawn around the chosen note [targetMidi]) between a ‹ and a › button that move the note
 * a half step down or up the flute's range ([FluteRange]); the buttons fade at the ends. With no [arrows] (Which? mode)
 * their room stays empty, so the window has the same size and place in both modes.
 *
 * The window takes the room it is given, up to [WINDOW_MAX_SCALE] times the canvas's 132 x 300 dp (since 2026-11-18,
 * when the Hear and How? buttons and the second line left the tuner and the window grew into their room), never under
 * [MinWindowHeight], and never wider than the arrows leave it: on a narrow phone the width decides the height.
 */
@Composable
fun TargetNote(
    targetMidi: Int,
    onTargetChange: (Int) -> Unit,
    arrows: Boolean,
    modifier: Modifier = Modifier,
    window: @Composable (Modifier) -> Unit,
) {
    // The canvas drew a 132 x 300 dp window between 52 dp buttons; a short or narrow space
    // shrinks the window (and on a narrow one the buttons) rather than squeeze the buttons out.
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val button = if (maxWidth >= NarrowBelow) StepSize else NarrowStepSize
        val widest = maxWidth - (button + ButtonGap) * 2
        var windowHeight = minOf(PitchWindowHeight * WINDOW_MAX_SCALE, maxHeight).coerceAtLeast(MinWindowHeight)
        var windowWidth = windowHeight * PITCH_WINDOW_ASPECT_RATIO
        if (windowWidth > widest) {
            windowWidth = widest
            windowHeight = windowWidth / PITCH_WINDOW_ASPECT_RATIO
        }
        StepArrows(targetMidi, onTargetChange, arrows, button, ButtonGap) {
            window(Modifier.size(windowWidth, windowHeight))
        }
    }
}

/**
 * [window] between the ‹ and › that move the chosen note a half step, within the flute's range: written once for both
 * layouts, which give the buttons' size and the [gap]. Without [arrows] (Which? mode) their room stays empty, so the
 * window keeps its size and place when the mode changes.
 */
@Composable
internal fun StepArrows(
    targetMidi: Int,
    onTargetChange: (Int) -> Unit,
    arrows: Boolean,
    button: Dp,
    gap: Dp,
    window: @Composable () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(gap)) {
        if (arrows) {
            StepButton(
                icon = AppIcons.ChevronLeft,
                description = stringResource(R.string.lower_note),
                enabled = targetMidi > FluteRange.LOWEST_MIDI,
                onClick = { onTargetChange(targetMidi - 1) },
                modifier = Modifier.size(button),
            )
        } else {
            Spacer(Modifier.size(button))
        }
        window()
        if (arrows) {
            StepButton(
                icon = AppIcons.ChevronRight,
                description = stringResource(R.string.higher_note),
                enabled = targetMidi < FluteRange.HIGHEST_MIDI,
                onClick = { onTargetChange(targetMidi + 1) },
                modifier = Modifier.size(button),
            )
        } else {
            Spacer(Modifier.size(button))
        }
    }
}

/** The most the upright window grows, as a share of the canvas's size: 450 dp tall, on a tall phone. */
internal const val WINDOW_MAX_SCALE = 1.5f

/** The ‹ and ›: 52 dp, and 44 where the room for the three is under 260 dp wide. */
private val StepSize = SidewaysGeometry.ARROW_SIZE.dp
private val NarrowStepSize = 44.dp
private val NarrowBelow = 260.dp

/** Wider than the canvas's 12 dp, so the arrow beside the sound bubble clears the › button; sideways counts the same. */
private val ButtonGap = SidewaysGeometry.ARROW_GAP.dp

/** The smallest the window gets before the tab scrolls instead. */
private val MinWindowHeight = 120.dp

/**
 * A ‹ or › button: a chunky button with the drawn chevron, named [description] for a screen reader and faded while
 * not [enabled]. The tuner steps its chosen note with a pair beside the window, upright and on its side, and the guide
 * to tuning turns its cards with another.
 */
@Composable
internal fun StepButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    iconSize: Dp = 30.dp,
) {
    ChunkyButton(onClick = onClick, enabled = enabled, shape = shape, modifier = modifier) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(iconSize))
    }
}
