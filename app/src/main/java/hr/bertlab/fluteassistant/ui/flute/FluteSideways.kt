// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.flute

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.domain.listen.TunerAdvice
import hr.bertlab.fluteassistant.domain.metronome.TimeSignature
import hr.bertlab.fluteassistant.ui.common.AdviceLine
import hr.bertlab.fluteassistant.ui.common.AppIcons
import hr.bertlab.fluteassistant.ui.common.LocalTabZoom
import hr.bertlab.fluteassistant.ui.common.SidewaysActionButton
import hr.bertlab.fluteassistant.ui.common.SidewaysGeometry
import hr.bertlab.fluteassistant.ui.common.SidewaysPage
import hr.bertlab.fluteassistant.ui.common.Stage
import hr.bertlab.fluteassistant.ui.common.TabletGeometry
import hr.bertlab.fluteassistant.ui.common.fitOrScroll
import hr.bertlab.fluteassistant.ui.metronome.MetronomeStageSideways
import hr.bertlab.fluteassistant.ui.metronome.MetronomeUiState
import hr.bertlab.fluteassistant.ui.metronome.TempoWheelState
import hr.bertlab.fluteassistant.ui.theme.ActionGo
import hr.bertlab.fluteassistant.ui.theme.ActionStop

/*
 * The flute on a phone on its side (spec `2026-11-05-landscape-design.md`, "Flute"; mockups
 * `fit-v2.html` for writing and `frame.html`, option A, for a tune running). `FluteContent` builds
 * the pieces, the same ones upright shows, and these only say where they stand.
 */

/** Between the keys and the two big buttons, as `SidewaysGeometry.keysRoom` counts it. */
private val KeysToButtons = SidewaysGeometry.MARGIN.dp

/** The keys' column's own least height: the keys' least room and the buttons under them (299). */
private val KeysColumnMinHeight = (KeysGeometry.MIN_HEIGHT + SidewaysGeometry.UNDER_KEYS).dp

/**
 * Writing a tune: the staff's card, which takes all the width the keys leave, and the keys with Play
 * and Listen to me under them. There is no right column here: beside the keys it left the staff about
 * three beats at 800 dp wide (`fit-v2.html`), so the two buttons went under the keys, in upright's
 * order, leaving a child nothing to relearn. [keys] is handed its modifier, the largest key ([KeysGeometry.SIDEWAYS_MAX])
 * and the gap between the keys, from the window's size; the staff stays at rest scale. Where the keys' room
 * ([SidewaysGeometry.keysRoom]) is under their least ([KeysGeometry.MIN_HEIGHT]), shorter than any phone it was drawn
 * for, the column of keys and buttons scrolls instead, as upright's tab does.
 */
@Composable
internal fun FluteWritingSideways(
    tune: @Composable (Modifier) -> Unit,
    keys: @Composable (Modifier, Dp, Dp) -> Unit,
    buttons: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val margin = SidewaysGeometry.MARGIN.dp
        val contentWidth = maxWidth.value
        val keysFit = SidewaysGeometry.keysRoom(maxHeight.value) >= KeysGeometry.MIN_HEIGHT
        // A tablet gives the staff more of the room: a zoomed tab has height to spare, and a tune wants the width.
        val zoom = LocalTabZoom.current
        val keysWidth = if (zoom > 1f) TabletGeometry.sidewaysKeys(zoom) else SidewaysGeometry.keysWidth(contentWidth)
        Row(
            horizontalArrangement = Arrangement.spacedBy(margin),
            modifier = Modifier
                .fillMaxSize()
                .padding(margin),
        ) {
            Stage(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                tune(Modifier)
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(KeysToButtons),
                modifier = Modifier
                    .width(keysWidth.dp)
                    .fitOrScroll(fits = keysFit, minHeight = KeysColumnMinHeight),
            ) {
                keys(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    KeysGeometry.SIDEWAYS_MAX.dp,
                    SidewaysGeometry.keyGap(contentWidth).dp,
                )
                buttons(Modifier)
            }
        }
    }
}

/**
 * A tune running, Play or Your turn, the screen a child reads from a music stand: the keys and the
 * two buttons are gone, the card widens to the right column, and the column holds the advice at its
 * top and Stop low on the right, in Listen to me's corner, under the thumb that started the run.
 * [tune] comes drawn at `SidewaysGeometry.runningStaffScale`. [prompt], "Play B4" before the first note
 * of a "Your turn" is judged, stands where the advice does. The fingering a wrong note brings upright
 * does not show here: the column is 116 dp wide and the diagram would be about 17 dp tall; its line,
 * "Check your fingers", is one of the advices and does. [level], the pair of Practice and Exam (2026-11-16), stands
 * under the line until the run's first note is judged.
 */
@Composable
internal fun FluteRunningSideways(
    tune: @Composable (Modifier) -> Unit,
    advice: TunerAdvice,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    prompt: AnnotatedString? = null,
    level: (@Composable () -> Unit)? = null,
) {
    SidewaysPage(
        modifier = modifier,
        column = {
            // The line and, until the first note is judged, the level's pair under it (2026-11-16): one child of the
            // column, so the column keeps the line at its top and Stop at its bottom, and the pair stands under the line.
            Column(verticalArrangement = Arrangement.spacedBy(LevelGap)) {
                if (prompt != null) AdviceLine(prompt, wrap = true) else AdviceLine(advice, wrap = true)
                level?.invoke()
            }
            SidewaysActionButton(
                icon = AppIcons.Stop,
                text = stringResource(R.string.stop),
                color = ActionStop,
                onClick = onStop,
            )
        },
    ) { card ->
        Stage(verticalArrangement = Arrangement.Center, modifier = card) {
            tune(Modifier)
        }
    }
}

/**
 * The speed panel: the metronome tab's board at rest, with OK in Play's place. The caller owns the
 * [wheel] and puts `closesTempoWheel` on [modifier], as it does upright.
 */
@Composable
internal fun FluteSpeedSideways(
    metronome: MetronomeUiState,
    wheel: TempoWheelState,
    onBpmChange: (Int) -> Unit,
    onTimeSignatureChange: (TimeSignature) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SidewaysPage(
        modifier = modifier,
        column = {
            SidewaysActionButton(
                icon = AppIcons.Done,
                text = stringResource(R.string.speed_done),
                color = ActionGo,
                onClick = onDone,
            )
        },
    ) { card ->
        MetronomeStageSideways(metronome, wheel, onBpmChange, onTimeSignatureChange, card)
    }
}

/** The room between the line and the level's pair in the running column. */
private val LevelGap = 8.dp
