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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.domain.metronome.BeatStrength
import hr.bertlab.fluteassistant.domain.metronome.TimeSignature
import hr.bertlab.fluteassistant.domain.metronome.fewerBeats
import hr.bertlab.fluteassistant.domain.metronome.moreBeats
import hr.bertlab.fluteassistant.ui.common.ChunkyButton
import hr.bertlab.fluteassistant.ui.common.Emoji
import hr.bertlab.fluteassistant.ui.common.OutlineWidth
import hr.bertlab.fluteassistant.ui.common.facingRight
import hr.bertlab.fluteassistant.ui.theme.FluteTheme
import hr.bertlab.fluteassistant.ui.theme.Sun

/** The room above a bubble for the hopping animal, and the biggest plain bubble upright draws. */
private val AnimalHeight = 40.dp
private val UprightUnit = 52.dp

/** The first bubble is a little bigger than the rest, and so is a compound bar's secondary accent. */
private const val FIRST_BEAT = 1.16f
private const val MEDIUM_BEAT = 1.08f

/** How many plain bubbles' width this bubble takes. */
private fun BeatStrength.sizeFactor(): Float = when (this) {
    BeatStrength.STRONG -> FIRST_BEAT
    BeatStrength.MEDIUM -> MEDIUM_BEAT
    else -> 1f
}

/** The gap between a bubble and the animal above it. */
private val AnimalGap = 6.dp

/**
 * What a bubble's column has to spare under the animal, the gap and the biggest bubble upright
 * draws: upright's column is 112 dp tall ([AnimalHeight] + [AnimalGap] + 52 * 1.16 + this).
 */
private val ColumnSpare = 5.68.dp

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

/** A round − or + that steps the bar by a beat; [size] is 52 dp upright, 56 dp under the bubbles sideways. */
@Composable
internal fun BeatStepButton(
    text: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    size: Dp = 52.dp,
) {
    ChunkyButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .padding(bottom = 4.dp)
            .size(size)
            .semantics { contentDescription = description },
    ) {
        Text(text, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp, lineHeight = 30.sp))
    }
}

/**
 * The bubbles alone, one per beat, as wide as [modifier] leaves them: a plain beat up to [maxUnit]
 * (52 dp upright; sideways 68 dp at rest and 150 dp while it plays), the first a little bigger.
 * [gapFew] is the room between them up to four beats and [gapMany] with more. The animal above the
 * sounding one is [animalFont] sp in a box [animalHeight] tall. The column is as tall as its first
 * bubble needs, but never less than upright's 112 dp, which narrow phones keep.
 *
 * The bubbles are counted by their real sizes: counting only the first as bigger leaves 6/8's fourth
 * (a medium accent, 1.08) too wide for the row, which squeezes the last one into an oval.
 *
 * With [stableRow] the animal's box is no wider than its bubble, so an animal wider than a small
 * bubble overflows into the gap instead of widening the sounding column and reflowing the row on
 * every beat. Only the sideways layouts ask for it, as upright's animal fits its bubble.
 */
@Composable
internal fun BeatBubbles(
    timeSignature: TimeSignature,
    currentBeat: Int,
    animal: String,
    flash: () -> Float,
    modifier: Modifier = Modifier,
    maxUnit: Dp = UprightUnit,
    gapFew: Dp = 12.dp,
    gapMany: Dp = 6.dp,
    animalFont: Int = 32,
    animalHeight: Dp = AnimalHeight,
    stableRow: Boolean = false,
) {
    val colors = FluteTheme.colors
    val beats = timeSignature.accents
    val description = stringResource(R.string.beats_in_bar, beats.size)
    BoxWithConstraints(modifier.clearAndSetSemantics { contentDescription = description }) {
        // Fit every bubble: up to maxUnit for a plain beat, the first a little bigger.
        val gap = if (beats.size <= 4) gapFew else gapMany
        val widths = beats.sumOf { it.sizeFactor().toDouble() }.toFloat()
        val unit = ((maxWidth - gap * (beats.size - 1)) / widths).coerceAtMost(maxUnit)
        val columnHeight = animalHeight + AnimalGap + maxOf(unit, UprightUnit) * FIRST_BEAT + ColumnSpare
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.Bottom,
        ) {
            beats.forEachIndexed { index, strength ->
                val active = index == currentBeat
                val diameter = unit * strength.sizeFactor()
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AnimalGap, Alignment.Bottom),
                    modifier = Modifier.height(columnHeight),
                ) {
                    Box(
                        modifier = Modifier
                            .height(animalHeight)
                            .then(if (stableRow) Modifier.width(diameter) else Modifier),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        if (active) {
                            Emoji(
                                emoji = animal,
                                fontSize = animalFont,
                                modifier = Modifier
                                    .then(if (stableRow) Modifier.wrapContentWidth(unbounded = true) else Modifier)
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
