// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import hr.bertlab.fluteassistant.ui.theme.FluteTheme

/** The room either side of an upright tab's column, and the room round it: every tab's, so the tabs line up. */
val TabSide = 20.dp
val TabPadding = PaddingValues(start = TabSide, end = TabSide, top = 4.dp, bottom = 16.dp)

/** What [TabPadding] takes of a tab's height, over and under its column. */
val TabPaddingHeight = 4.dp + 16.dp

/** The room between the parts of a tab's upright column (the stage, the line, the big button). */
val TabGap = 14.dp

/** A [Stage]'s own padding inside its card, unless it asks for another. */
val StagePadding = 16.dp

/**
 * The rounded white (navy in dark mode) card each tab draws its main content on, with a
 * thin border: it is not pressable, so it has no ink outline or shadow.
 */
@Composable
fun Stage(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.SpaceBetween,
    padding: Dp = StagePadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = FluteTheme.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = verticalArrangement,
        modifier = modifier
            .clip(StageShape)
            .background(colors.card)
            .border(OutlineWidth, colors.border, StageShape)
            .padding(padding),
        content = content,
    )
}

val StageShape = RoundedCornerShape(32.dp)
