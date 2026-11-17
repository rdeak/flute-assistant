// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Test helper: whether these bounds lie within [outer], give or take [slack]. A fit is shown by bounds, not by `assertIsDisplayed`. */
fun DpRect.isInside(outer: DpRect, slack: Dp = 1.dp): Boolean =
    left >= outer.left - slack && top >= outer.top - slack && right <= outer.right + slack && bottom <= outer.bottom + slack

/** Test helper: whether a pixel read off the screen is this colour, give or take [tolerance] a channel (a capture is not exact). */
fun Color.isNear(other: Color, tolerance: Float = 0.03f): Boolean =
    abs(red - other.red) < tolerance && abs(green - other.green) < tolerance && abs(blue - other.blue) < tolerance
