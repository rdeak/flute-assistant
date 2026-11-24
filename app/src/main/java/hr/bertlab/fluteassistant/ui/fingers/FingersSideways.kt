// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.fingers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.domain.fingers.FingerChart
import hr.bertlab.fluteassistant.domain.fingers.FingerPick
import hr.bertlab.fluteassistant.ui.common.FingeringDiagram
import hr.bertlab.fluteassistant.ui.common.KeyDepth
import hr.bertlab.fluteassistant.ui.common.KeyRingReach
import hr.bertlab.fluteassistant.ui.common.SidewaysPage
import hr.bertlab.fluteassistant.ui.common.Stage
import hr.bertlab.fluteassistant.ui.common.fitOrScroll
import hr.bertlab.fluteassistant.ui.theme.FluteTheme

/**
 * The Fingers tab on a phone on its side (spec `2026-11-17-fingers-tab-design.md`, part 3; the mockup's `side_board`):
 * the notes in the card, a row each with the name, the staff and the chart, parted by thin lines; the eight keys two by
 * two in the right column, where the other tabs have their buttons. No hand names here. A row is a quarter of the
 * card whatever the note, so three rows look as four would.
 */
@Composable
internal fun FingersSideways(
    state: FingerPick,
    onLetter: (Int) -> Unit,
    onSign: () -> Unit,
    onTune: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    SidewaysPage(
        modifier = modifier,
        column = { KeyColumn(state, onLetter, onSign, Modifier.fillMaxHeight()) },
    ) { card ->
        Stage(modifier = card.testTag("fingers_card"), verticalArrangement = Arrangement.Center) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val rowHeight = (maxHeight - DividerRoom * (FingerChart.MOST_NOTES - 1)) / FingerChart.MOST_NOTES
                Column(verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
                    state.notes.forEachIndexed { index, midi ->
                        if (index > 0) Divider()
                        SideRow(midi, onTune, Modifier.fillMaxWidth().height(rowHeight).testTag("fingers_row"))
                    }
                }
            }
        }
    }
}

/**
 * One note on its side: the name, the staff and the chart, the chart as wide as the row's height allows; the whole row
 * opens the note in the tuner (`tuneOnTap`). On a wider
 * phone that stops short of the card's end, and the three stand in the middle of the card (the final review).
 */
@Composable
private fun SideRow(midi: Int, onTune: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        modifier = modifier.tuneOnTap(stringResource(R.string.fingers_tune), midi, onTune),
    ) {
        Box(Modifier.widthIn(min = PillSlot)) { NotePill(midi) }
        NoteOnStaff(midi, Modifier.width(SideStaffWidth).fillMaxHeight())
        Box(Modifier.weight(1f, fill = false).fillMaxHeight(), contentAlignment = Alignment.Center) { FingeringDiagram(midi) }
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .padding(vertical = 2.dp)
            .fillMaxWidth()
            .height(2.dp)
            .background(FluteTheme.colors.track),
    )
}

/**
 * The eight keys two by two (C D, E F, G A, B ♯♭), sharing the column's height: 58 to 72 dp tall on every phone on
 * its side. A window shorter than any phone (under about 312 dp, a pane of a split screen) shrinks them, down to
 * [MinKey]; under that (about 264 dp) they keep that size and the column scrolls, as the flute's keys do in a window
 * too short for them, with room for the chosen key's ring above and the last pair's shadow below.
 */
@Composable
private fun KeyColumn(state: FingerPick, onLetter: (Int) -> Unit, onSign: () -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val keyHeight = ((maxHeight - PairRowGap * (PAIRS - 1) - KeyDepth) / PAIRS).coerceIn(MinKey, MaxKey)
        val least = MinKey * PAIRS + PairRowGap * (PAIRS - 1) + KeyDepth
        Column(
            verticalArrangement = Arrangement.spacedBy(PairRowGap, Alignment.CenterVertically),
            // When it scrolls it keeps room for the chosen key's ring above and below.
            modifier = Modifier.fitOrScroll(fits = maxHeight >= least, minHeight = least + KeyRingReach * 2),
        ) {
            for (pair in 0 until PAIRS) {
                Row(horizontalArrangement = Arrangement.spacedBy(PairGap), modifier = Modifier.fillMaxWidth()) {
                    FingerKey(state, pair * 2, onLetter, onSign, keyHeight)
                    FingerKey(state, pair * 2 + 1, onLetter, onSign, keyHeight)
                }
            }
        }
    }
}

private val DividerRoom = 6.dp
private val PillSlot = 44.dp
private val SideStaffWidth = 56.dp
/** Between the two keys of a pair, and between one pair and the next. */
private val PairGap = 8.dp
private val PairRowGap = 15.dp
private const val PAIRS = FINGER_KEYS / 2

/**
 * A key's height at the most, and at the least: the width a key has in the short window's column (100 dp, two keys 8
 * apart), so it is never flatter than square. No phone on its side reaches the least: at 320 dp tall a key is 60.
 */
private val MaxKey: Dp = 72.dp
private val MinKey: Dp = 46.dp
