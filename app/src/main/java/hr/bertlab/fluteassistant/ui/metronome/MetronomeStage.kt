// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.metronome

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import hr.bertlab.fluteassistant.domain.metronome.TimeSignature
import hr.bertlab.fluteassistant.domain.metronome.tempoBand
import hr.bertlab.fluteassistant.ui.common.Stage
import hr.bertlab.fluteassistant.ui.common.TabGap
import hr.bertlab.fluteassistant.ui.common.TabPadding
import hr.bertlab.fluteassistant.ui.common.fitOrScroll
import hr.bertlab.fluteassistant.ui.common.rememberBeatFlash

/**
 * The ring shrinks to fit a small phone; only below this height (split screen, a tiny phone)
 * does the tab scroll instead.
 */
private val MetronomeMinHeight: Dp = 420.dp

/**
 * Whether the tempo wheel is open, and where the wheel and the column around it sit, so a
 * touch anywhere outside the wheel can close it ([closesTempoWheel]). Owned by the tab so both
 * the stage and the column's gesture see the same state.
 */
class TempoWheelState {
    /** The tempo wheel is open while this is true; it is state, so callers recompose on a change. */
    var editing: Boolean by mutableStateOf(false)

    internal var wheelCoordinates: LayoutCoordinates? by mutableStateOf(null)
    internal var rootCoordinates: LayoutCoordinates? by mutableStateOf(null)
}

/** A closed [TempoWheelState] that lives as long as the tab's composition. */
@Composable
fun rememberTempoWheelState(): TempoWheelState = remember { TempoWheelState() }

/**
 * Closes the open tempo wheel on a touch that starts anywhere outside it. Goes on the column
 * that holds the [MetronomeStage], so a tap on any other control closes the wheel too; the
 * touch is observed on the initial pass, so the touched control still works.
 */
fun Modifier.closesTempoWheel(wheel: TempoWheelState): Modifier = this
    .onGloballyPositioned { wheel.rootCoordinates = it }
    .pointerInput(wheel.editing) {
        if (!wheel.editing) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val wheelBox = wheel.wheelCoordinates?.takeIf { it.isAttached }
            val root = wheel.rootCoordinates?.takeIf { it.isAttached }
            val insideWheel = wheelBox != null && root != null &&
                root.localBoundingBoxOf(wheelBox).contains(down.position)
            if (!insideWheel) wheel.editing = false
        }
    }

/**
 * The metronome's card: the beat bubbles (with − / + for beats in a bar) over the tempo ring
 * (drag the animal, tap a band, or tap the number for the in-place wheel). Both the drum tab
 * and the flute tab show it, so it takes the [wheel] state from its caller, which also puts
 * [closesTempoWheel] on its column.
 */
@Composable
fun MetronomeStage(
    state: MetronomeUiState,
    wheel: TempoWheelState,
    onBpmChange: (Int) -> Unit,
    onTimeSignatureChange: (TimeSignature) -> Unit,
    modifier: Modifier = Modifier,
) {
    // One flash shared by the bubbles and the knob, so the animal hops with the beat.
    val flash = rememberBeatFlash(state.beatPulse, state.isPlaying)

    Stage(
        verticalArrangement = Arrangement.SpaceEvenly,
        modifier = modifier,
    ) {
        BeatIndicator(
            timeSignature = state.config.timeSignature,
            currentBeat = state.currentBeat,
            animal = tempoBand(state.config.bpm).emoji,
            flash = flash,
            onTimeSignatureChange = onTimeSignatureChange,
        )
        TempoDial(
            bpm = state.config.bpm,
            onBpmChange = onBpmChange,
            wheel = wheel,
            flash = flash,
            modifier = Modifier
                .weight(1f, fill = false)
                .widthIn(max = 304.dp)
                .aspectRatio(1f),
        )
    }
}

/**
 * The stage over one big [button], upright: the drum tab (Play) and the flute's speed panel (OK). It scrolls when the
 * tab ([tabHeight]) is lower than [MetronomeMinHeight], and a touch anywhere in it closes an open [wheel]. The
 * caller owns the [wheel] and its Back handler.
 */
@Composable
fun MetronomeStageColumn(
    state: MetronomeUiState,
    wheel: TempoWheelState,
    tabHeight: Dp,
    onBpmChange: (Int) -> Unit,
    onTimeSignatureChange: (TimeSignature) -> Unit,
    modifier: Modifier = Modifier,
    button: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fitOrScroll(fits = tabHeight >= MetronomeMinHeight, minHeight = MetronomeMinHeight)
            .closesTempoWheel(wheel)
            .padding(TabPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TabGap),
    ) {
        MetronomeStage(
            state = state,
            wheel = wheel,
            onBpmChange = onBpmChange,
            onTimeSignatureChange = onTimeSignatureChange,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
        button()
    }
}
