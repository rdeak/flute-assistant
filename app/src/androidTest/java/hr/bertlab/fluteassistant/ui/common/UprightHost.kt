// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * The tab of the user's phone upright, a Galaxy A55: its three navigation buttons leave the tab 653 dp of height,
 * where the emulator leaves 709, so a layout that fits the emulator may not fit there.
 */
val A55Tab = DpSize(411.dp, 653.dp)

/**
 * Test helper: renders [content] in an upright tab of [size] dp at [fontScale] times the text, the user's phone by
 * default. The size is forced by scaling the density, as [SidewaysHost] forces its own: read sizes from layout
 * constraints, and compare bounds to show that something fits.
 */
@Composable
fun UprightHost(size: DpSize = A55Tab, fontScale: Float = 1f, content: @Composable () -> Unit) {
    DeviceConfigurationOverride(
        DeviceConfigurationOverride.FontScale(fontScale) then DeviceConfigurationOverride.ForcedSize(size),
        content = content,
    )
}
