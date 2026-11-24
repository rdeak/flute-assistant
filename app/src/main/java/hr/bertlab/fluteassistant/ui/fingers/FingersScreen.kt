// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.fingers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import hr.bertlab.fluteassistant.domain.fingers.FingerChart
import hr.bertlab.fluteassistant.domain.fingers.FingerPick
import hr.bertlab.fluteassistant.ui.common.KeyDepth
import hr.bertlab.fluteassistant.ui.common.KeyGap
import hr.bertlab.fluteassistant.ui.common.KeyHeight
import hr.bertlab.fluteassistant.ui.common.LocalSideways
import hr.bertlab.fluteassistant.ui.common.TabPadding

/**
 * The Fingers tab, stateful: the view model's [state] and its taps. [onTune] is the frame's: it opens a note in the
 * tuner (`MainScreen`), as this tab knows no other tab.
 */
@Composable
fun FingersScreen(state: FingerPick, viewModel: FingersViewModel, onTune: (Int) -> Unit, modifier: Modifier = Modifier) {
    FingersContent(state, onLetter = viewModel::tapLetter, onSign = viewModel::tapSign, onTune = onTune, modifier = modifier)
}

/**
 * The Fingers tab (since 2026-11-17, a teacher's ask; spec `2026-11-17-fingers-tab-design.md`): the chosen note in
 * every octave the flute plays, each on its staff in music symbols with its name and the fingering chart (since
 * 2026-11-24; spec `2026-11-24-fingers-music-symbols-and-book-chart-design.md`), and under them the seven letter keys
 * and ♯♭. Nothing else: no sound, no legend, nothing stored. A tap on a row's chart is [onTune] with its note, which the
 * frame opens in the tuner's Tune mode (since 2026-11-18, the user's ask). The rows stand in the middle of the room the
 * keys leave and scroll on a screen too short for them; the keys never scroll. A row's chart fills the row's width, but
 * only as far as four rows still fit the room: on a wide and short tab it stops there (`NoteRow`).
 */
@Composable
fun FingersContent(
    state: FingerPick,
    onLetter: (Int) -> Unit,
    onSign: () -> Unit,
    onTune: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (LocalSideways.current) {
        FingersSideways(state, onLetter, onSign, onTune, modifier)
        return
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(TabPadding),
    ) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().testTag("fingers_rows")) {
            // What the most rows a note has (C's four) have room for, whatever the note shows: three rows are as tall as four would be.
            val tallestRow = (maxHeight - RowGap * (FingerChart.MOST_NOTES - 1)) / FingerChart.MOST_NOTES
            Column(
                verticalArrangement = Arrangement.spacedBy(RowGap, Alignment.CenterVertically),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight),
            ) {
                state.notes.forEach { midi -> NoteRow(midi, tallestRow, onTune, Modifier.fillMaxWidth().testTag("fingers_row")) }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(KeyGap),
            modifier = Modifier
                .fillMaxWidth()
                // The keys' room: the ring above a key and the depth a sunk key sinks into below it.
                .testTag("fingers_keys")
                .padding(top = 6.dp, bottom = KeyDepth),
        ) {
            repeat(FINGER_KEYS) { FingerKey(state, it, onLetter, onSign, KeyHeight) }
        }
    }
}

private val RowGap = 10.dp
