// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.tuner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.domain.tuner.TunerMode
import hr.bertlab.fluteassistant.ui.common.FINGERING_ASPECT_RATIO
import hr.bertlab.fluteassistant.ui.common.SidewaysGeometry
import hr.bertlab.fluteassistant.ui.common.SidewaysModeChoice
import hr.bertlab.fluteassistant.ui.common.SidewaysPage
import hr.bertlab.fluteassistant.ui.common.Stage
import hr.bertlab.fluteassistant.ui.common.labelRes

/*
 * The tuner on a phone on its side (spec `2026-11-05-landscape-design.md`, "Tuner"; mockups
 * `stand.html` and, for windows under 750 dp wide, `fit-v2.html`). One layout. The pitch window stays
 * upright, as up means higher, and grows to the card's height. In Tune mode it stands between its
 * two arrows, and in Which? mode the arrows' room stays empty, so the ring does not move when the
 * mode changes. The words and the fingering stand to its right; in a window under 750 dp wide
 * (at 360 tall: the 640 wide phone) the fingering would be too small there, so it goes under the
 * window across the card. The right column holds only Which? over Tune, low. In Tune mode a tap on the window plays
 * the chosen note, and a line that names a fix on the flute opens the guide on its card: the Hear and How? buttons
 * that stood at the top of the column went on 2026-11-18 (the calmer Tune view), as upright.
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

/**
 * The most the fingering is wide under the window (since 2026-11-17): the flute picture is taller for its width than
 * the strip was, and across the whole card it would take 20 dp more from the window than the strip did.
 */
private val UnderFingeringWidth = 300.dp

/**
 * The line of words: 40 sp in up to two lines where the room is wide (the mockup's), 28 sp in up to three where it is
 * not, in a box 108 or 124 dp tall. That is the room the status line and the advice line under it had together until
 * 2026-11-18, so the fingering beside or under the words stands where it stood, whatever the line says.
 */
private val WordsFont = 40.sp
private val WordsHeight = 108.dp
private val NarrowWordsFont = 28.sp
private val NarrowWordsHeight = 124.dp

/** The words over the fingering beside the window: one value, as an arrangement built while the tab draws is never equal to the last. */
private val WordsArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)

/** Mode buttons: 92 tall (the big look) with 13 between on the 915 x 411 phone, 80 with 9 between on a short one. */
internal val ModeHeight = 92.dp
internal val ModeHeightShort = 80.dp
private val ModeGap = 13.dp
private val ModeGapShort = 9.dp

/**
 * The whole tuner sideways, without the permission card (`TunerContent` shows that first). The pitch
 * window's size comes from the tab's height and the fingering's place from its width, both through
 * [SidewaysGeometry], which the unit tests pin.
 */
@Composable
internal fun TunerSideways(state: TunerUiState, actions: TunerActions, modifier: Modifier = Modifier) {
    val groupLabel = stringResource(R.string.tuner_mode)
    BoxWithConstraints(modifier.fillMaxSize()) {
        // The geometry is given the tab's size, not the card's, as that is what its formulas count from.
        val height = maxHeight.value
        val cardWidth = SidewaysGeometry.cardWidth(maxWidth.value, height)
        val beside = SidewaysGeometry.fingeringBeside(cardWidth, height)
        val short = SidewaysGeometry.isShort(height)
        SidewaysPage(
            column = {
                // The column's one child, so the pair stays low.
                SidewaysModeChoice(
                    entries = TunerMode.entries,
                    selected = state.mode,
                    onSelect = actions.onModeChange,
                    groupLabel = groupLabel,
                    emojiOf = { it.emoji },
                    labelResOf = { it.labelRes() },
                    height = if (short) ModeHeightShort else ModeHeight,
                    gap = if (short) ModeGapShort else ModeGap,
                    big = !short,
                )
            },
        ) { card ->
            Stage(modifier = card, verticalArrangement = Arrangement.Center) {
                val scale = SidewaysGeometry.pitchWindowScale(height)
                if (beside) {
                    TunerBeside(state, scale, actions)
                } else {
                    TunerUnder(state, scale, cardWidth - SidewaysGeometry.CARD_CHROME, actions)
                }
            }
        }
    }
}

/** The window between its arrows on the left, the words over the fingering on the right. */
@Composable
private fun TunerBeside(state: TunerUiState, scale: Float, actions: TunerActions) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        WindowBetweenArrows(state, scale, ArrowSize, ArrowGap, actions)
        Spacer(Modifier.width(FingeringGap))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = WordsArrangement,
            modifier = Modifier.weight(1f),
        ) {
            TunerWords(state.line, wide = true, onOpenGuide = actions.onOpenGuide)
            TunerFingering(state.homeMidi, state.fingeringLit, Modifier.testTag(FINGERING_TAG))
        }
    }
}

/**
 * A window under 750 dp wide: the window, a little smaller, with the words beside it and the fingering
 * across the card under both. The window gives up the fingering's height and the gap between.
 */
@Composable
private fun TunerUnder(state: TunerUiState, scale: Float, innerWidth: Float, actions: TunerActions) {
    val fingeringHeight = minOf(innerWidth, UnderFingeringWidth.value) / FINGERING_ASPECT_RATIO
    val windowScale = scale - (fingeringHeight + FingeringUnderGap.value) / PitchWindowHeight.value
    Column(
        verticalArrangement = Arrangement.spacedBy(FingeringUnderGap),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NarrowWordsGap),
            modifier = Modifier.fillMaxWidth(),
        ) {
            WindowBetweenArrows(state, windowScale, NarrowArrowSize, NarrowArrowGap, actions)
            TunerWords(state.line, wide = false, onOpenGuide = actions.onOpenGuide, modifier = Modifier.weight(1f))
        }
        TunerFingering(state.homeMidi, state.fingeringLit, Modifier.testTag(FINGERING_TAG).widthIn(max = UnderFingeringWidth))
    }
}

/**
 * The pitch window at [scale] between its ‹ and › ([StepArrows]: their room stays empty in Which? mode, so the ring
 * does not move when the mode does).
 */
@Composable
private fun WindowBetweenArrows(state: TunerUiState, scale: Float, arrow: Dp, gap: Dp, actions: TunerActions) {
    val windowHeight = PitchWindowHeight * scale
    StepArrows(state.targetMidi, actions.onTargetNoteChange, arrows = state.mode == TunerMode.TARGET, button = arrow, gap = gap) {
        TunerWindow(
            state,
            actions.onPlayNote,
            Modifier
                .size(windowHeight * PITCH_WINDOW_ASPECT_RATIO, windowHeight)
                .testTag("pitch_window"),
            maxScale = scale,
        )
    }
}

/** The one line of words ([StatusLine]) at this layout's sizes: [wide] beside the fingering, not wide beside a smaller window. */
@Composable
private fun TunerWords(line: TunerLine, wide: Boolean, onOpenGuide: (GuideCard) -> Unit, modifier: Modifier = Modifier) {
    StatusLine(
        line,
        onOpenGuide,
        modifier = modifier,
        maxFont = if (wide) WordsFont else NarrowWordsFont,
        height = if (wide) WordsHeight else NarrowWordsHeight,
        maxLines = if (wide) 2 else 3,
        style = MaterialTheme.typography.headlineMedium.copy(lineHeight = 1.2.em),
    )
}
