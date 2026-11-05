// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * A tab's whole column: the screen's height when it [fits], so weighted parts share what is
 * left; otherwise [minHeight] tall and scrolling, so nothing is squeezed below a usable size on
 * a small phone or with large text. The weights still work, as the column keeps a fixed height.
 */
@Composable
fun Modifier.fitOrScroll(fits: Boolean, minHeight: Dp): Modifier =
    if (fits) fillMaxSize() else fillMaxWidth().verticalScroll(rememberScrollState()).height(minHeight)
