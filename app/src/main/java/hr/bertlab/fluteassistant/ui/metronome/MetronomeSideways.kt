// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.metronome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.domain.metronome.TimeSignature
import hr.bertlab.fluteassistant.domain.metronome.fewerBeats
import hr.bertlab.fluteassistant.domain.metronome.moreBeats
import hr.bertlab.fluteassistant.domain.metronome.tempoBand
import hr.bertlab.fluteassistant.ui.common.AppIcons
import hr.bertlab.fluteassistant.ui.common.SidewaysActionButton
import hr.bertlab.fluteassistant.ui.common.SidewaysGeometry
import hr.bertlab.fluteassistant.ui.common.SidewaysPage
import hr.bertlab.fluteassistant.ui.common.Stage
import hr.bertlab.fluteassistant.ui.common.rememberBeatFlash
import hr.bertlab.fluteassistant.ui.theme.ActionGo
import hr.bertlab.fluteassistant.ui.theme.ActionStop
import hr.bertlab.fluteassistant.ui.theme.FluteTheme

/*
 * The metronome on a phone on its side (spec `2026-11-05-landscape-design.md`, "Metronome"; mockup
 * `stand.html`, option B). At rest the beats stand on the left over their − and + and the ring on
 * the right, the order upright reads top down, and Play is low in the right column. While it plays
 * the ring and the − and + go and the beats take over the card, big enough to see from the corner
 * of the eye from a music stand, with the speed kept as a figure above Stop that does nothing when
 * pressed: to change the speed the child presses Stop first. The two states swap without a fade:
 * each is a whole card, and two cards fading over each other would let the page show through.
 */

/** The room between the beats' column and the ring (the mockup's 645 dp inside leaves 317 for the beats). */
private val GroupGap = 24.dp

/** The bubbles at rest are about 68 dp on the 915 dp phone (spec), against upright's 52. */
private val RestMaxUnit = 68.dp
private const val REST_ANIMAL_FONT = 36

/** The − and + under the bubbles: bigger than upright's 52 and 28 apart, as the mockup draws them. */
private val StepSize = 56.dp
private val StepGap = 28.dp
private val StepsGap = 16.dp

/** While it plays: bubbles up to 150 dp (the geometry's cap, which its tests hold the bubbles to), the animal 58 sp in a 72 dp room, and more air between the bubbles. */
private val PlayingMaxUnit = SidewaysGeometry.BIG_BEAT_MAX.dp
private val PlayingGapFew = SidewaysGeometry.BIG_BEAT_GAP_FEW.dp
private val PlayingGapMany = SidewaysGeometry.BIG_BEAT_GAP_MANY.dp
private const val PLAYING_ANIMAL_FONT = 58
private val PlayingAnimalHeight = 72.dp

/** The round well the speed stays in while it plays. */
private val FigureWell = 104.dp

/** The well's test tag: it has no words or action of its own to find it by. */
internal const val FIGURE_WELL_TAG = "tempo_well"

/**
 * The whole metronome tab sideways: the card, and in the right column Play, or the speed figure
 * above Stop. The caller owns the [wheel] and puts `closesTempoWheel` on [modifier], as upright does.
 */
@Composable
internal fun MetronomeSideways(
    state: MetronomeUiState,
    wheel: TempoWheelState,
    onTogglePlayback: () -> Unit,
    onBpmChange: (Int) -> Unit,
    onTimeSignatureChange: (TimeSignature) -> Unit,
    modifier: Modifier = Modifier,
) {
    // A touch on Play closes the wheel by itself; a start from elsewhere is not a touch, and Stop
    // would bring the ring back with the wheel still open.
    LaunchedEffect(state.isPlaying) {
        if (state.isPlaying) wheel.editing = false
    }
    SidewaysPage(
        modifier = modifier,
        column = {
            if (state.isPlaying) TempoFigure(state.config.bpm)
            SidewaysActionButton(
                icon = if (state.isPlaying) AppIcons.Stop else AppIcons.Play,
                text = stringResource(if (state.isPlaying) R.string.stop else R.string.play),
                color = if (state.isPlaying) ActionStop else ActionGo,
                onClick = onTogglePlayback,
            )
        },
    ) { card ->
        if (state.isPlaying) {
            MetronomeTakeover(state, card)
        } else {
            MetronomeStageSideways(state, wheel, onBpmChange, onTimeSignatureChange, card)
        }
    }
}

/**
 * The card at rest: the beats over their − and + on the left, the tempo ring on the right. The
 * ring is [SidewaysGeometry.ringSize] of this card, so a small phone shrinks it and leaves the
 * bubbles their room, which may fall below upright's 52 dp. Both the drum tab and the flute's
 * speed panel show it, so it takes the [wheel] from its caller.
 */
@Composable
fun MetronomeStageSideways(
    state: MetronomeUiState,
    wheel: TempoWheelState,
    onBpmChange: (Int) -> Unit,
    onTimeSignatureChange: (TimeSignature) -> Unit,
    modifier: Modifier = Modifier,
) {
    // One flash shared by the bubbles and the knob, so the animal hops with the beat.
    val flash = rememberBeatFlash(state.beatPulse, state.isPlaying)
    val timeSignature = state.config.timeSignature
    val fewer = timeSignature.fewerBeats()
    val more = timeSignature.moreBeats()

    Stage(verticalArrangement = Arrangement.Center, modifier = modifier) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // The geometry wants the card and the window around it; this card's inside is what it was given.
            val ring = SidewaysGeometry.ringSize(
                cardWidth = maxWidth.value + SidewaysGeometry.CARD_CHROME,
                height = maxHeight.value + 2 * SidewaysGeometry.MARGIN + SidewaysGeometry.CARD_CHROME,
            ).dp
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GroupGap),
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(StepsGap),
                    modifier = Modifier.weight(1f),
                ) {
                    BeatBubbles(
                        timeSignature = timeSignature,
                        currentBeat = state.currentBeat,
                        animal = tempoBand(state.config.bpm).emoji,
                        flash = flash,
                        modifier = Modifier.fillMaxWidth(),
                        maxUnit = RestMaxUnit,
                        animalFont = REST_ANIMAL_FONT,
                        stableRow = true,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(StepGap)) {
                        BeatStepButton(
                            text = "−",
                            description = stringResource(R.string.fewer_beats),
                            enabled = fewer != null,
                            onClick = { fewer?.let(onTimeSignatureChange) },
                            size = StepSize,
                        )
                        BeatStepButton(
                            text = "+",
                            description = stringResource(R.string.more_beats),
                            enabled = more != null,
                            onClick = { more?.let(onTimeSignatureChange) },
                            size = StepSize,
                        )
                    }
                }
                TempoDial(
                    bpm = state.config.bpm,
                    onBpmChange = onBpmChange,
                    editing = wheel.editing,
                    onEditingChange = { wheel.editing = it },
                    onWheelPositioned = { wheel.wheelCoordinates = it },
                    flash = flash,
                    modifier = Modifier.size(ring),
                )
            }
        }
    }
}

/**
 * The card while it plays: the beats alone, as big as the card allows (144 dp with four beats and
 * 97 with six on the 915 dp phone), the first a little bigger, the speed's animal hopping on the
 * sounding one. Nothing in it can be pressed.
 */
@Composable
internal fun MetronomeTakeover(state: MetronomeUiState, modifier: Modifier = Modifier) {
    val flash = rememberBeatFlash(state.beatPulse, state.isPlaying)
    Stage(verticalArrangement = Arrangement.Center, modifier = modifier) {
        BeatBubbles(
            timeSignature = state.config.timeSignature,
            currentBeat = state.currentBeat,
            animal = tempoBand(state.config.bpm).emoji,
            flash = flash,
            modifier = Modifier.fillMaxWidth(),
            maxUnit = PlayingMaxUnit,
            gapFew = PlayingGapFew,
            gapMany = PlayingGapMany,
            animalFont = PLAYING_ANIMAL_FONT,
            animalHeight = PlayingAnimalHeight,
            stableRow = true,
        )
    }
}

/**
 * The speed while it plays: the figure alone in a round well, with no click action. It is no
 * wider than the column, which is 100 dp on a short window.
 */
@Composable
private fun TempoFigure(bpm: Int) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .widthIn(max = FigureWell)
            .fillMaxWidth()
            .aspectRatio(1f)
            .testTag(FIGURE_WELL_TAG)
            .background(FluteTheme.colors.track, CircleShape),
    ) {
        Text(
            text = bpm.toString(),
            style = MaterialTheme.typography.displayLarge.copy(
                lineHeight = 1.1.em,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.sp,
            ),
            color = FluteTheme.colors.text,
            maxLines = 1,
            softWrap = false,
            // 40 sp, but a bigger text size must not push "200" out of the well.
            autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = 40.sp),
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}
