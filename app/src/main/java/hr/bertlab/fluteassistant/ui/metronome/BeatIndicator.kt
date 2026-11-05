// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.metronome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.domain.model.BeatStrength
import hr.bertlab.fluteassistant.domain.model.TimeSignature
import hr.bertlab.fluteassistant.ui.common.ChunkyButton
import hr.bertlab.fluteassistant.ui.common.Emoji
import hr.bertlab.fluteassistant.ui.common.OutlineWidth
import hr.bertlab.fluteassistant.ui.common.facingRight
import hr.bertlab.fluteassistant.ui.theme.FluteTheme
import hr.bertlab.fluteassistant.ui.theme.Sun

/** Height of one bubble's column: room for the hopping animal above the largest bubble. */
private val ColumnHeight = 112.dp
private val AnimalHeight = 40.dp

/** How much a beat bubble pops (scale) and the animal above it hops (dp) at the flash's peak. */
private const val POP = 0.18f
private const val HOP_DP = 10f

/**
 * The beats of the bar between a − and a + button. One bubble per beat, the accented ones
 * bigger; the one sounding now turns yellow and pops with [flash] (1 = just hit, 0 = faded),
 * and the speed's animal hops on top of it. [flash] is a lambda read only in the layer
 * phase, so the beat animation never recomposes the row. The buttons step the bar through
 * [fewerBeats] / [moreBeats] and fade out at either end.
 */
@Composable
fun BeatIndicator(
    timeSignature: TimeSignature,
    currentBeat: Int,
    animal: String,
    flash: () -> Float,
    onTimeSignatureChange: (TimeSignature) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fewer = timeSignature.fewerBeats()
    val more = timeSignature.moreBeats()
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BeatStepButton(
            text = "−",
            description = stringResource(R.string.fewer_beats),
            enabled = fewer != null,
            onClick = { fewer?.let(onTimeSignatureChange) },
        )
        BeatBubbles(timeSignature, currentBeat, animal, flash, Modifier.weight(1f))
        BeatStepButton(
            text = "+",
            description = stringResource(R.string.more_beats),
            enabled = more != null,
            onClick = { more?.let(onTimeSignatureChange) },
        )
    }
}

@Composable
private fun BeatStepButton(text: String, description: String, enabled: Boolean, onClick: () -> Unit) {
    ChunkyButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .padding(bottom = 4.dp)
            .size(52.dp)
            .semantics { contentDescription = description },
    ) {
        Text(text, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp, lineHeight = 30.sp))
    }
}

@Composable
private fun BeatBubbles(
    timeSignature: TimeSignature,
    currentBeat: Int,
    animal: String,
    flash: () -> Float,
    modifier: Modifier = Modifier,
) {
    val colors = FluteTheme.colors
    val beats = timeSignature.accents
    val description = stringResource(R.string.beats_in_bar, beats.size)
    BoxWithConstraints(modifier.clearAndSetSemantics { contentDescription = description }) {
        // Fit every bubble: up to 52 dp for a plain beat, the first a little bigger.
        val gap = if (beats.size <= 4) 12.dp else 6.dp
        val unit = ((maxWidth - gap * (beats.size - 1)) / (beats.size + 0.16f)).coerceAtMost(52.dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.Bottom,
        ) {
            beats.forEachIndexed { index, strength ->
                val active = index == currentBeat
                val diameter = when (strength) {
                    BeatStrength.STRONG -> unit * 1.16f
                    BeatStrength.MEDIUM -> unit * 1.08f
                    else -> unit
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom),
                    modifier = Modifier.height(ColumnHeight),
                ) {
                    Box(Modifier.height(AnimalHeight), contentAlignment = Alignment.BottomCenter) {
                        if (active) {
                            Emoji(
                                emoji = animal,
                                fontSize = 32,
                                modifier = Modifier
                                    .facingRight(animal)
                                    .graphicsLayer { translationY = -HOP_DP * density * flash() },
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(diameter)
                            .graphicsLayer {
                                val pop = if (active) 1f + POP * flash() else 1f
                                scaleX = pop
                                scaleY = pop
                            }
                            .clip(CircleShape)
                            .background(if (active) Sun else colors.track)
                            .border(OutlineWidth, if (active) colors.outline else colors.border, CircleShape),
                    )
                }
            }
        }
    }
}
