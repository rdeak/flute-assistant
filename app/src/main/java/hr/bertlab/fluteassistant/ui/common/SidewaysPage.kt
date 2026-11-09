// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import hr.bertlab.fluteassistant.ui.theme.FluteTheme
import hr.bertlab.fluteassistant.ui.theme.Sun

/**
 * What a tab shows on a phone on its side: the thing itself in [card], which fills the room, and
 * what to do in the right [column], the same idea on every tab. The card slot is handed the modifier
 * that sizes it. `column = null` leaves no column. A column's first child sits at the top and its
 * last low on the right, where the main button goes, so a lone child goes low too. Its padding is
 * the mockup's: the 8 below is a big button's shadow, which ends level with the card.
 */
@Composable
fun SidewaysPage(
    modifier: Modifier = Modifier,
    column: (@Composable ColumnScope.() -> Unit)? = null,
    card: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val margin = SidewaysGeometry.MARGIN.dp
        val columnWidth = SidewaysGeometry.columnWidth(maxHeight.value).dp
        Row(
            horizontalArrangement = Arrangement.spacedBy(margin),
            modifier = Modifier
                .fillMaxSize()
                .padding(margin),
        ) {
            card(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            if (column != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = TopAndBottom,
                    modifier = Modifier
                        .width(columnWidth)
                        .fillMaxHeight()
                        .padding(top = 6.dp, bottom = 8.dp),
                    content = column,
                )
            }
        }
    }
}

/** [Arrangement.SpaceBetween], which alone would send a lone child to the top. */
private object TopAndBottom : Arrangement.Vertical {
    override fun Density.arrange(totalSize: Int, sizes: IntArray, outPositions: IntArray) {
        val arrangement = if (sizes.size == 1) Arrangement.Bottom else Arrangement.SpaceBetween
        with(arrangement) { arrange(totalSize, sizes, outPositions) }
    }
}

/** A column's main button: a stacked [BigActionButton], as wide as the column. */
@Composable
fun SidewaysActionButton(
    icon: ImageVector,
    text: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    BigActionButton(
        icon = icon,
        text = text,
        color = color,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        stacked = true,
        height = 104.dp,
        shape = RoundedCornerShape(34.dp),
    )
}

/**
 * A column's quieter button: an emoji over a word on the plain raised colour, as wide as the column.
 * Pass [selected] for one option of a group, yellow when chosen. The 92 tall one has the bigger
 * emoji and word the mockup gives it. The microphone card's "Not now" has no emoji, and a button
 * without one is its word alone, centred. The word shrinks to fit down to 12 dp, a floor in dp as
 * the Pick buttons' are: one in sp doubles at the largest text, and 12 sp cut "👂 Slušaj" there,
 * and a cut word is worse than a small one.
 */
@Composable
fun SidewaysPlainButton(
    emoji: String?,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean? = null,
    enabled: Boolean = true,
    height: Dp = 84.dp,
) {
    val big = height >= 92.dp
    val wordFloor = with(LocalDensity.current) { 12.dp.toSp() }
    ChunkyButton(
        onClick = onClick,
        shape = RoundedCornerShape(30.dp),
        color = if (selected == true) Sun else FluteTheme.colors.raised,
        depth = 5.dp,
        enabled = enabled,
        selected = selected,
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (emoji != null) Emoji(emoji, fontSize = if (big) 30 else 26)
            Text(
                text,
                // Relative, as before Android 14 large text would leave a short button no room for a full line.
                style = MaterialTheme.typography.titleMedium.copy(lineHeight = 1.25.em),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = wordFloor, maxFontSize = if (big) 21.sp else 19.sp),
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}
