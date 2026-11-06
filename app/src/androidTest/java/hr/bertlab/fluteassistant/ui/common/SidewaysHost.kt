// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * Test helper: renders [content] as it sits beside the rail on a phone [width] x [height] dp on its
 * side, so the content gets that size less the rail, as the real tab area does. [sideways] is what
 * `LocalSideways` says; a test flips it through a `mutableStateOf` to turn the phone.
 *
 * The size is forced by scaling the density, so read sizes from layout constraints, as `LocalConfiguration`
 * and `LocalWindowInfo` still describe the emulator. It reaches neither a `Dialog` nor a popup's own
 * window, and on an upright emulator what spills past [height] is still on the screen, so
 * `assertIsDisplayed` alone does not show that something fits.
 */
@Composable
fun SidewaysHost(
    width: Int = 915,
    height: Int = 411,
    fontScale: Float = 1f,
    sideways: Boolean = true,
    content: @Composable () -> Unit,
) {
    val rail = SidewaysGeometry.railWidth(height.toFloat())
    DeviceConfigurationOverride(
        DeviceConfigurationOverride.FontScale(fontScale) then
            DeviceConfigurationOverride.ForcedSize(DpSize((width - rail).dp, height.dp)),
    ) {
        CompositionLocalProvider(LocalSideways provides sideways, content = content)
    }
}
