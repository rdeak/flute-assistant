// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.tuner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.domain.tuner.FluteRange
import hr.bertlab.fluteassistant.domain.tuner.TunerMode
import hr.bertlab.fluteassistant.ui.common.AdviceLine
import hr.bertlab.fluteassistant.ui.common.FINGERING_ASPECT_RATIO
import hr.bertlab.fluteassistant.ui.common.AppIcons
import hr.bertlab.fluteassistant.ui.common.SidewaysGeometry
import hr.bertlab.fluteassistant.ui.common.SidewaysPage
import hr.bertlab.fluteassistant.ui.common.SidewaysPlainButton
import hr.bertlab.fluteassistant.ui.common.Stage
import hr.bertlab.fluteassistant.ui.common.labelRes

/*
 * The tuner on a phone on its side (spec `2026-11-05-landscape-design.md`, "Tuner"; mockups
 * `stand.html` and, for windows under 750 dp wide, `fit-v2.html`). One layout. The pitch window stays
 * upright, as up means higher, and grows to the card's height. In Tune mode it stands between its
 * two arrows, and in Which? mode the arrows' room stays empty, so the ring does not move when the
 * mode changes. The words and the fingering stand to its right; in a window under 750 dp wide
 * (at 360 tall: the 640 wide phone) the fingering would be too small there, so it goes under the
 * window across the card. The buttons are in the right column: Hear at the top in Tune mode only,
 * and Which? over Tune low.
 */

/**
 * The arrows and the gaps round the window, with the fingering beside them, are
 * `SidewaysGeometry`'s own, as that is what decides whether the fingering fits beside the window.
 */
private val ArrowSize = SidewaysGeometry.ARROW_SIZE.dp
private val ArrowGap = SidewaysGeometry.ARROW_GAP.dp
private val FingeringGap = SidewaysGeometry.FINGERING_GAP.dp

/** The arrows in a window under 750 dp wide, where they and the gap shrink as the mockup draws them. */
private val NarrowArrowGap = 12.dp
private val NarrowArrowSize = 44.dp
private val NarrowWordsGap = 6.dp

/** The room between the window and the fingering under it, in a window under 750 dp wide. */
private val FingeringUnderGap = 12.dp
private val WordsGap = 16.dp

/** The status line's size where the room is wide (the mockup's 40 over 48) and where it is not (28). */
private val StatusFont = 40.sp
private val StatusHeight = 48.dp
private val NarrowStatusFont = 28.sp
private val NarrowStatusHeight = 64.dp

/** The advice line's room: two lines of its 22 sp, so that nothing below it moves when it comes and goes. */
private val AdviceHeight = 58.dp
private val AdviceFont = 22.sp
private val NarrowAdviceFont = 19.sp

/** The floor of the microphone's error, in dp as a floor in sp doubles at the largest text size. */
private val ErrorMinFont = 12.dp

/** The longest words shrink to this before they are cut; bigger than a bubble's number, and only a large text size on a small phone gets there. */
private val StatusMinFont = 12.sp

/** Mode buttons: 92 tall with 13 between on the 915 x 411 phone, 80 with 9 between on a short one. */
private val ModeHeight = 92.dp
private val ModeHeightShort = 80.dp
private val ModeGap = 13.dp
private val ModeGapShort = 9.dp

/**
 * The whole tuner sideways, without the permission card (`TunerContent` shows that first). The pitch
 * window's size comes from the tab's height and the fingering's place from its width, both through
 * [SidewaysGeometry], which the unit tests pin.
 */
@Composable
internal fun TunerSideways(
    state: TunerUiState,
    onTargetNoteChange: (Int) -> Unit,
    onModeChange: (TunerMode) -> Unit,
    onPlayNote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val groupLabel = stringResource(R.string.tuner_mode)
    BoxWithConstraints(modifier.fillMaxSize()) {
        // The geometry is given the tab's size, not the card's, as that is what its formulas count from.
        val height = maxHeight.value
        val cardWidth = SidewaysGeometry.cardWidth(maxWidth.value, height)
        val beside = SidewaysGeometry.fingeringBeside(cardWidth, height)
        val short = SidewaysGeometry.isShort(height)
        SidewaysPage(
            column = {
                if (state.mode == TunerMode.TARGET) {
                    SidewaysPlainButton(
                        emoji = SPEAKER_EMOJI,
                        text = stringResource(R.string.hear_note),
                        onClick = onPlayNote,
                        enabled = !state.isPlayingReference,
                    )
                }
                // One child, so the pair stays low whether or not Hear is above it.
                Column(
                    verticalArrangement = Arrangement.spacedBy(if (short) ModeGapShort else ModeGap),
                    modifier = Modifier
                        .selectableGroup()
                        .semantics { contentDescription = groupLabel },
                ) {
                    TunerMode.entries.forEach { entry ->
                        SidewaysPlainButton(
                            emoji = entry.emoji,
                            text = stringResource(entry.labelRes()),
                            onClick = { onModeChange(entry) },
                            selected = entry == state.mode,
                            height = if (short) ModeHeightShort else ModeHeight,
                        )
                    }
                }
            },
        ) { card ->
            Stage(modifier = card, verticalArrangement = Arrangement.Center) {
                if (beside) {
                    TunerBeside(state, SidewaysGeometry.pitchWindowScale(height), onTargetNoteChange)
                } else {
                    TunerUnder(state, SidewaysGeometry.pitchWindowScale(height), cardWidth - SidewaysGeometry.CARD_CHROME, onTargetNoteChange)
                }
            }
        }
    }
}

/** The window between its arrows on the left, the words over the fingering on the right. */
@Composable
private fun TunerBeside(state: TunerUiState, scale: Float, onTargetNoteChange: (Int) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        WindowBetweenArrows(state, scale, ArrowSize, ArrowGap, onTargetNoteChange)
        Spacer(Modifier.width(FingeringGap))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(WordsGap, Alignment.CenterVertically),
            modifier = Modifier.weight(1f),
        ) {
            TunerWords(state, wide = true)
            TunerFingering(state, Modifier.testTag("fingering"))
        }
    }
}

/**
 * A window under 750 dp wide: the window, a little smaller, with the words beside it and the fingering
 * across the card under both. The window gives up the fingering's height and the gap between.
 */
@Composable
private fun TunerUnder(state: TunerUiState, scale: Float, innerWidth: Float, onTargetNoteChange: (Int) -> Unit) {
    val fingeringHeight = innerWidth / FINGERING_ASPECT_RATIO
    val windowScale = scale - (fingeringHeight + FingeringUnderGap.value) / PitchWindowHeight.value
    Column(
        verticalArrangement = Arrangement.spacedBy(FingeringUnderGap),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NarrowWordsGap),
            modifier = Modifier.fillMaxWidth(),
        ) {
            WindowBetweenArrows(state, windowScale, NarrowArrowSize, NarrowArrowGap, onTargetNoteChange)
            TunerWords(state, wide = false, modifier = Modifier.weight(1f))
        }
        TunerFingering(state, Modifier.testTag("fingering"))
    }
}

/**
 * The pitch window at [scale], with the ‹ and › either side in Tune mode and the same room empty
 * in Which? mode, so the block is the same width and the ring does not move when the mode does.
 */
@Composable
private fun WindowBetweenArrows(
    state: TunerUiState,
    scale: Float,
    arrow: Dp,
    gap: Dp,
    onTargetNoteChange: (Int) -> Unit,
) {
    val description = pitchWindowDescription(state)
    val windowHeight = PitchWindowHeight * scale
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        if (state.mode == TunerMode.TARGET) {
            StepButton(
                icon = AppIcons.ChevronLeft,
                description = stringResource(R.string.lower_note),
                enabled = state.targetMidi > FluteRange.LOWEST_MIDI,
                size = arrow,
                onClick = { onTargetNoteChange(state.targetMidi - 1) },
            )
        } else {
            Spacer(Modifier.size(arrow))
        }
        PitchWindow(
            homeMidi = state.homeMidi,
            heardMidi = state.heardMidi,
            cents = state.gaugeCents,
            description = description,
            maxScale = scale,
            modifier = Modifier
                .size(windowHeight * PITCH_WINDOW_ASPECT_RATIO, windowHeight)
                .testTag("pitch_window"),
        )
        if (state.mode == TunerMode.TARGET) {
            StepButton(
                icon = AppIcons.ChevronRight,
                description = stringResource(R.string.higher_note),
                enabled = state.targetMidi < FluteRange.HIGHEST_MIDI,
                size = arrow,
                onClick = { onTargetNoteChange(state.targetMidi + 1) },
            )
        } else {
            Spacer(Modifier.size(arrow))
        }
    }
}

/**
 * The status line over the advice line, and the error message when there is one. Both lines keep
 * their room whatever they say, so nothing under them moves. [wide] is the mockup's 40 sp and 22 sp
 * in one line, and not wide is its 28 sp and 19 sp, the status in up to two lines.
 */
@Composable
private fun TunerWords(state: TunerUiState, wide: Boolean, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier,
    ) {
        StatusLine(
            state,
            maxFont = if (wide) StatusFont else NarrowStatusFont,
            height = if (wide) StatusHeight else NarrowStatusHeight,
            minFont = StatusMinFont,
            maxLines = if (wide) 1 else 2,
            style = MaterialTheme.typography.headlineMedium.copy(lineHeight = 1.2.em),
        )
        Box(Modifier.fillMaxWidth().height(AdviceHeight)) {
            if (!state.micFailed) {
                AdviceLine(state.advice, wrap = true, wrapMaxFont = if (wide) AdviceFont else NarrowAdviceFont)
            } else {
                // A failed microphone means nothing is heard, so there is no advice to make room for and nothing moves.
                Text(
                    stringResource(R.string.mic_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 1.25.em),
                    textAlign = TextAlign.Center,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = with(LocalDensity.current) { ErrorMinFont.toSp() },
                        maxFontSize = if (wide) AdviceFont else NarrowAdviceFont,
                    ),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

