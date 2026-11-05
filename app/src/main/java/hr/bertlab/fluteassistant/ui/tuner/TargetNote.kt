// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.tuner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.domain.tuner.FluteRange
import hr.bertlab.fluteassistant.ui.common.AppIcons
import hr.bertlab.fluteassistant.ui.common.ChunkyButton
import hr.bertlab.fluteassistant.ui.common.Emoji

/**
 * Tune mode's pitch window ([window], drawn around the chosen note [targetMidi]) between a ‹ and
 * a › button that move the note a half step down or up the flute's range ([FluteRange]); the
 * buttons fade at the ends. Under it a "Hear" pill calls [onPlay] so the child can hear the
 * note first; it is disabled while [isPlaying].
 */
@Composable
fun TargetNote(
    targetMidi: Int,
    onTargetChange: (Int) -> Unit,
    onPlay: () -> Unit,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    window: @Composable (Modifier) -> Unit,
) {
    // The canvas drew a 132 x 300 dp window between 52 dp buttons; a short or narrow space
    // shrinks the window (and on a narrow one the buttons) rather than squeeze the buttons out.
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val button = if (maxWidth >= 260.dp) 52.dp else 44.dp
        val gap = if (maxHeight >= PitchWindowHeight + HearHeight) 14.dp else 8.dp
        val windowHeight = minOf(PitchWindowHeight, maxHeight - HearHeight - gap).coerceAtLeast(MinWindowHeight)
        val windowWidth = minOf(windowHeight * PITCH_WINDOW_ASPECT_RATIO, maxWidth - (button + ButtonGap) * 2)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ButtonGap)) {
                StepButton(
                    icon = AppIcons.ChevronLeft,
                    description = stringResource(R.string.lower_note),
                    enabled = targetMidi > FluteRange.LOWEST_MIDI,
                    size = button,
                    onClick = { onTargetChange(targetMidi - 1) },
                )
                window(Modifier.size(windowWidth, windowHeight))
                StepButton(
                    icon = AppIcons.ChevronRight,
                    description = stringResource(R.string.higher_note),
                    enabled = targetMidi < FluteRange.HIGHEST_MIDI,
                    size = button,
                    onClick = { onTargetChange(targetMidi + 1) },
                )
            }
            ChunkyButton(
                onClick = onPlay,
                enabled = !isPlaying,
                modifier = Modifier.height(HearHeight),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 22.dp),
                ) {
                    Emoji(SPEAKER_EMOJI, fontSize = 24)
                    Text(stringResource(R.string.hear_note), style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp))
                }
            }
        }
    }
}

private val HearHeight = 56.dp

/** Wider than the canvas's 12 dp, so the arrow beside the sound bubble clears the › button. */
private val ButtonGap = 20.dp

/** The smallest the window gets before the tab scrolls instead. */
private val MinWindowHeight = 120.dp

@Composable
private fun StepButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    size: Dp,
    onClick: () -> Unit,
) {
    ChunkyButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(size)) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(30.dp))
    }
}

private const val SPEAKER_EMOJI = "🔊" // speaker, loud
