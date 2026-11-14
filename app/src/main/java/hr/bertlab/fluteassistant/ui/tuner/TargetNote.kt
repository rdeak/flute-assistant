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
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
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
 * note first; it is disabled while [isPlaying]. Beside it, in the same row and at the same height so
 * nothing moves, "🔧 How?" calls [onHow], which opens the guide to tuning the flute (since 2026-11-14).
 */
@Composable
fun TargetNote(
    targetMidi: Int,
    onTargetChange: (Int) -> Unit,
    onPlay: () -> Unit,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    onHow: () -> Unit = {},
    window: @Composable (Modifier) -> Unit,
) {
    // The canvas drew a 132 x 300 dp window between 52 dp buttons; a short or narrow space
    // shrinks the window (and on a narrow one the buttons) rather than squeeze the buttons out.
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val button = if (maxWidth >= 260.dp) 52.dp else 44.dp
        val pillPadding = if (maxWidth >= 300.dp) 22.dp else 14.dp
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
            // The two pills share the row, so on a narrow phone at a large text size neither crowds the
            // other out: each word shrinks within its half instead.
            Row(horizontalArrangement = Arrangement.spacedBy(PillGap, Alignment.CenterHorizontally)) {
                Pill(SPEAKER_EMOJI, stringResource(R.string.hear_note), onPlay, enabled = !isPlaying, pillPadding, Modifier.weight(1f, fill = false))
                Pill(WRENCH_EMOJI, stringResource(R.string.tune_how), onHow, enabled = true, pillPadding, Modifier.weight(1f, fill = false))
            }
        }
    }
}

/** One of the pills under the window: an emoji and a word, the word shrinking to 12 dp before it is cut. */
@Composable
private fun Pill(emoji: String, word: String, onClick: () -> Unit, enabled: Boolean, padding: Dp, modifier: Modifier) {
    val floor = with(LocalDensity.current) { PillMinFont.toSp() }
    ChunkyButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(HearHeight),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = padding),
        ) {
            Emoji(emoji, fontSize = 24)
            Text(
                word,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = floor, maxFontSize = 22.sp),
            )
        }
    }
}

private val PillGap = 14.dp

/** A floor in dp, as one in sp doubles at the largest text size (`SidewaysPlainButton`'s rule). */
private val PillMinFont = 12.dp

private val HearHeight = 56.dp

/** Wider than the canvas's 12 dp, so the arrow beside the sound bubble clears the › button. */
private val ButtonGap = 20.dp

/** The smallest the window gets before the tab scrolls instead. */
private val MinWindowHeight = 120.dp

/** The ‹ and › buttons; the tuner on its side stands them beside the window, with no Hear pill under it. */
@Composable
internal fun StepButton(
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

internal const val SPEAKER_EMOJI = "🔊" // speaker, loud

internal const val WRENCH_EMOJI = "🔧" // wrench: how to tune the flute
