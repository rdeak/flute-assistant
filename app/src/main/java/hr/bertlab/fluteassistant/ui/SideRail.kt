// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import hr.bertlab.fluteassistant.ui.common.CreditsLogo
import hr.bertlab.fluteassistant.ui.common.OutlineWidth
import hr.bertlab.fluteassistant.ui.common.SidewaysGeometry
import hr.bertlab.fluteassistant.ui.common.rememberBeatFlash
import hr.bertlab.fluteassistant.ui.theme.FluteTheme

/**
 * Where the top bar and the tab bar go on a phone on its side: the logo, the four tabs and the
 * flag, always on screen and never rearranged. Its size follows the height it is given, as the
 * frame's numbers do. The yellow tab says which is open, so there is no title; a screen reader
 * hears the open tab's word as a selected tab. The background runs under a camera cutout on the
 * rail's side, and the width below is what is left clear of it. The credits and the language list
 * are open or shut as [creditsOpen] and [languageOpen] say when the caller passes them with their
 * change callbacks, so a turn from the title bar leaves them as they were.
 */
@Composable
internal fun SideRail(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    drumBeating: Boolean,
    beatPulse: Long,
    modifier: Modifier = Modifier,
    creditsOpen: Boolean? = null,
    onCreditsOpenChange: ((Boolean) -> Unit)? = null,
    languageOpen: Boolean? = null,
    onLanguageOpenChange: ((Boolean) -> Unit)? = null,
) {
    val colors = FluteTheme.colors
    val drumFlash = rememberBeatFlash(beatPulse, drumBeating)
    BoxWithConstraints(modifier) {
        val height = maxHeight.value
        val short = SidewaysGeometry.isShort(height)
        val iconSize = if (short) 38.dp else 44.dp
        val railWidth = SidewaysGeometry.railWidth(height).dp
        val flagStart = RailPadding + (railWidth - RailPadding * 2 - OutlineWidth - iconSize) / 2
        // In dp, so that the floor and the emoji over the word do not double with the text size, and the word
        // grows with it only up to [RailWordGrowth]: a cell is 60 dp tall on the short rail, and at 1.5 times
        // the text the emoji and the word took all of it and the word stood on the open tab's outline, at
        // twice the word was cut by it.
        val density = LocalDensity.current
        val minWordSize = with(density) { RailMinWordSize.toSp() }
        val emojiSize = with(density) { (if (short) 22.dp else 26.dp).toSp().value.toInt() }
        val maxWordSize = with(density) { ((if (short) 11.dp else 12.dp) * minOf(fontScale, RailWordGrowth)).toSp() }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxHeight()
                .background(colors.card)
                .drawBehind {
                    val line = OutlineWidth.toPx()
                    val x = if (layoutDirection == LayoutDirection.Ltr) size.width - line else 0f
                    drawRect(colors.border, topLeft = Offset(x, 0f), size = Size(line, size.height))
                }
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Start))
                .width(railWidth)
                .padding(start = RailPadding, end = RailPadding + OutlineWidth, top = 10.dp, bottom = 12.dp),
        ) {
            CreditsLogo(modifier = Modifier.size(iconSize), isOpen = creditsOpen, onOpenChange = onCreditsOpenChange)
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .selectableGroup(),
            ) {
                AppTab.entries.forEach { tab ->
                    TabItem(
                        tab = tab,
                        selected = tab == selected,
                        onSelect = onSelect,
                        drumBeating = drumBeating,
                        drumFlash = drumFlash,
                        emojiSize = emojiSize,
                        wordStyle = MaterialTheme.typography.labelMedium.copy(lineHeight = RailWordLineHeight),
                        minWordSize = minWordSize,
                        maxWordSize = maxWordSize,
                        wordPadding = 3.dp,
                        shape = RailTabShape,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                }
            }
            // Beside the rail, as above the flag the list would cover the tabs.
            AppLanguageSwitch(
                modifier = Modifier.size(iconSize),
                listOffset = railWidth - flagStart + SidewaysGeometry.MARGIN.dp,
                isOpen = languageOpen,
                onOpenChange = onLanguageOpenChange,
                // In dp, as a flag of 24 sp is bigger than the 38 dp button at one and a half times the text size.
                flagSize = FlagSize,
            )
        }
    }
}

/** The flag in the rail's round button, 24 dp whatever the text size. */
private val FlagSize = 24.dp

/** The room either side of the rail's contents. */
private val RailPadding = 6.dp

/** Relative, as before Android 14 large text would otherwise leave the short rail's word no room for its line. */
private val RailWordLineHeight = 1.25.em

/**
 * Under the bottom bar's 10 sp, as the rail is narrower. A size in dp and not sp: "Metronome" must fit
 * the rail at every text size, and a floor in sp is twice as big at twice the text, which no longer fits
 * the 84 dp rail (it was cut above about 1.7 times). At normal text it is the 7 it was.
 */
private val RailMinWordSize = 7.dp

/** The most the rail's words grow with the text size, as a share of their size at normal text. */
private const val RailWordGrowth = 1.3f

private val RailTabShape = RoundedCornerShape(20.dp)
