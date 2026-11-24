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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import hr.bertlab.fluteassistant.ui.common.CreditsLogo
import hr.bertlab.fluteassistant.ui.common.OutlineWidth
import hr.bertlab.fluteassistant.ui.common.SidewaysGeometry
import hr.bertlab.fluteassistant.ui.common.rememberBeatFlash
import hr.bertlab.fluteassistant.ui.theme.FluteTheme

/**
 * Where the top bar and the tab bar go on a phone on its side: the logo, the five tabs and the
 * flag, always on screen and never rearranged. Its size follows the height it is given, as the
 * frame's numbers do. The yellow tab says which is open, so there is no title; a screen reader
 * hears the open tab's word as a selected tab. The background runs under a camera cutout on the
 * rail's side, and the width below is what is left clear of it. The credits and the language list
 * are open or shut as [panels] says: the caller that also shows the title bar hands both the same
 * one, so a turn from the title bar leaves them as they were.
 */
@Composable
internal fun SideRail(
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    drumBeating: Boolean,
    beatPulse: Long,
    modifier: Modifier = Modifier,
    panels: BarPanels = rememberBarPanels(),
) {
    val colors = FluteTheme.colors
    val drumFlash = rememberBeatFlash(beatPulse, drumBeating)
    BoxWithConstraints(modifier) {
        val height = maxHeight.value
        val short = SidewaysGeometry.isShort(height)
        val iconSize = if (short) 38.dp else 44.dp
        val railWidth = SidewaysGeometry.railWidth(height).dp
        val flagStart = RailPadding + (railWidth - RailPadding * 2 - OutlineWidth - iconSize) / 2
        // Five tabs (since 2026-11-17) leave a cell about 48 dp tall on the short rail and 55 on the taller one. The
        // cells share what the logo, the flag and the gaps leave, and each fits its emoji to its own height
        // (`TabItem`): on a window shorter than the short rail was drawn for (about 320 dp tall: a phone on its side
        // with the display size set larger) the emoji is drawn smaller, so the word keeps clear of the open tab's outline.
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
            CreditsLogo(isOpen = panels.creditsOpen, onOpenChange = { panels.creditsOpen = it }, modifier = Modifier.size(iconSize))
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
                        look = if (short) RailLookShort else RailLook,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                }
            }
            // Beside the rail, as above the flag the list would cover the tabs.
            AppLanguageSwitch(
                isOpen = panels.languageOpen,
                onOpenChange = { panels.languageOpen = it },
                modifier = Modifier.size(iconSize),
                listOffset = railWidth - flagStart + SidewaysGeometry.MARGIN.dp,
            )
        }
    }
}

/** The room either side of the rail's contents. */
private val RailPadding = 6.dp

private val RailTabShape = RoundedCornerShape(20.dp)

/**
 * The rail's tabs, on the taller rail and on the short one (`TabLook`, every size in dp).
 * - The emoji at the most: 24 (26 with four tabs) and 20 (22 with four). A cell too short for it draws it smaller, by
 *   its own measure (`TabItem`): about 19 on the 360 dp rail and 23.5 on the 411 dp one.
 * - The word's floor is 7, under the bottom bar's 9, as the rail is narrower: "Metronome" must fit the rail at every
 *   text size, and a floor in sp is twice as big at twice the text, which no longer fits the 84 dp rail (it was cut
 *   above about 1.7 times). At normal text it is the 7 it was.
 * - The word grows with the text size by 1.1 at the most on the taller rail and not at all on the short one, the most
 *   with which five tabs' words stay inside their cells at the largest text (searched on 2026-11-17 with
 *   `SideRailTest`; with four tabs it was 1.3 on both).
 */
private val RailLook = TabLook(emoji = 24.dp, word = 12.dp, wordFloor = 7.dp, wordGrowth = 1.1f, wordPadding = 3.dp, shape = RailTabShape)
private val RailLookShort = TabLook(emoji = 20.dp, word = 11.dp, wordFloor = 7.dp, wordGrowth = 1.0f, wordPadding = 3.dp, shape = RailTabShape)
