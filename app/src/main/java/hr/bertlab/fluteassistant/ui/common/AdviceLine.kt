// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.bertlab.fluteassistant.ui.theme.FluteTheme
import hr.bertlab.fluteassistant.ui.tuner.TunerAdvice

/**
 * The quieter line saying what to change: roll the flute, the air, the fingers, come closer, or
 * play a clear note. Under the tuner's status line, and under the flute's staff while it listens.
 * Empty text for [TunerAdvice.NONE] keeps the line's height, so nothing around it moves when
 * advice comes and goes.
 */
@Composable
fun AdviceLine(advice: TunerAdvice, modifier: Modifier = Modifier) {
    Text(
        text = advice.labelRes()?.let { stringResource(it) } ?: "",
        style = MaterialTheme.typography.bodyLarge,
        color = FluteTheme.colors.mutedText,
        textAlign = TextAlign.Center,
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = 22.sp),
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp),
    )
}
