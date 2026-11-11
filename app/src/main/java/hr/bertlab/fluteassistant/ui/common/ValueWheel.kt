// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Rows a [ValueWheel] shows at once: the chosen value and one neighbour on each side. */
const val WHEEL_VISIBLE_ITEMS = 3

/**
 * How much of the muted text colour a neighbour keeps. The neighbours are 24 sp bold, so they
 * need 3:1 against the ground they sit on (`ThemeContrastTest` pins it on every ground).
 */
internal const val NEIGHBOUR_ALPHA = 0.75f

/**
 * A vertical drum of values, three visible: the one in the middle is the chosen one, the
 * neighbours above and below are small and faded. Scrolling snaps to whole rows and every
 * notch ticks; the centred index is reported as soon as it changes, so whatever the wheel
 * drives follows live. Tapping a neighbour scrolls to it; tapping the middle calls
 * [onCentreClick]. A [selectedIndex] change from elsewhere (assistive technology, another
 * control) moves the drum. [description] is the accessibility name of the whole drum.
 */
@Composable
fun ValueWheel(
    itemCount: Int,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    label: (Int) -> String,
    description: String,
    modifier: Modifier = Modifier,
    onCentreClick: () -> Unit = {},
    itemHeight: Dp = 48.dp,
    // The line must be taller than the glyphs or Text clips the bottom of the digits.
    centreStyle: TextStyle = MaterialTheme.typography.displayLarge.copy(fontSize = 64.sp, lineHeight = 76.sp),
    neighbourStyle: TextStyle = MaterialTheme.typography.titleLarge,
    centreColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val centredIndex by remember(listState) { derivedStateOf { listState.centredIndex() } }

    // Wheel -> value: as soon as another row sits in the middle, it is the value.
    LaunchedEffect(centredIndex) {
        val index = centredIndex ?: return@LaunchedEffect
        if (index != selectedIndex) {
            onSelectedIndexChange(index)
            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
        }
    }
    // Value -> wheel: a change from elsewhere moves the drum.
    LaunchedEffect(selectedIndex) {
        if (!listState.isScrollInProgress && centredIndex != selectedIndex) listState.scrollToItem(selectedIndex)
    }

    val neighbourColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = NEIGHBOUR_ALPHA)

    LazyColumn(
        state = listState,
        flingBehavior = rememberSnapFlingBehavior(listState),
        // One row's worth of padding at each end lets the first and last value reach the middle.
        contentPadding = PaddingValues(vertical = itemHeight * ((WHEEL_VISIBLE_ITEMS - 1) / 2)),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .height(itemHeight * WHEEL_VISIBLE_ITEMS)
            .semantics { contentDescription = description },
    ) {
        items(count = itemCount, key = { it }) { index ->
            val isCentre = index == centredIndex
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeight)
                    .clickable(interactionSource = null, indication = null) {
                        if (isCentre) onCentreClick() else scope.launch { listState.animateScrollToItem(index) }
                    },
            ) {
                Text(
                    text = label(index),
                    style = if (isCentre) centreStyle else neighbourStyle,
                    color = if (isCentre) centreColor else neighbourColor,
                    overflow = TextOverflow.Visible,
                    softWrap = false,
                )
            }
        }
    }
}

/** Index of the row whose middle is nearest the middle of the viewport, or null before layout. */
private fun LazyListState.centredIndex(): Int? {
    val info = layoutInfo
    val centre = (info.viewportStartOffset + info.viewportEndOffset) / 2
    return info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - centre) }?.index
}
