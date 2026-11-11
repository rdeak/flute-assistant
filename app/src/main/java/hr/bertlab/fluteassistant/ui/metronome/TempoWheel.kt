// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.metronome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.domain.metronome.TempoBand
import hr.bertlab.fluteassistant.ui.common.OutlineWidth
import hr.bertlab.fluteassistant.ui.common.ValueWheel
import hr.bertlab.fluteassistant.ui.theme.FluteTheme
import hr.bertlab.fluteassistant.ui.theme.InkOnFill
import hr.bertlab.fluteassistant.ui.theme.Sun

private val RowHeight = 56.dp

/**
 * A [ValueWheel] of the ring's tempos, one beat per notch: the chosen tempo big on a yellow
 * pill in the middle, the neighbours small and faded, reported as it changes so the ring and
 * a running metronome follow live. Tapping the middle calls [onClose], the caller's cue to
 * show the plain number again.
 */
@Composable
fun TempoWheel(
    bpm: Int,
    onBpmChange: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val minBpm = TempoBand.MIN_BPM
    Box(modifier = modifier.width(150.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .width(132.dp)
                .height(RowHeight + 4.dp)
                .clip(CircleShape)
                .background(Sun)
                .border(OutlineWidth, FluteTheme.colors.outline, CircleShape),
        )
        ValueWheel(
            itemCount = TempoBand.MAX_BPM - minBpm + 1,
            selectedIndex = bpm.coerceIn(minBpm, TempoBand.MAX_BPM) - minBpm,
            onSelectedIndexChange = { onBpmChange(minBpm + it) },
            label = { (minBpm + it).toString() },
            description = stringResource(R.string.tempo_wheel),
            onCentreClick = onClose,
            itemHeight = RowHeight,
            centreStyle = MaterialTheme.typography.displayLarge.copy(fontSize = 48.sp, lineHeight = 56.sp),
            centreColor = InkOnFill,
        )
    }
}
