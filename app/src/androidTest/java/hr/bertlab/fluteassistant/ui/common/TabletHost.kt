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

/** The tab areas of Google's tablet test sizes, in real dp after the bars (spec `2026-11-06-tablet-design.md`). */
enum class TabletSize(val width: Float, val height: Float, val sideways: Boolean) {
    TEN_UPRIGHT(800f, 1065f, false),
    TEN_SIDEWAYS(1188f, 752f, true),
    EIGHT_UPRIGHT(640f, 809f, false),
    EIGHT_SIDEWAYS(932f, 592f, true),
    ;

    val zoom: Float get() = TabletGeometry.tabZoom(width, height, sideways, tablet = true)

    /** The area the tab is laid out in, which comes out [zoom] times bigger. */
    val logicalWidth: Float get() = width / zoom
    val logicalHeight: Float get() = height / zoom
}

/**
 * Test helper: renders [content] as the tab is on a tablet of this [size]. Only the logical size
 * matters to the layout, so this forces that as `SidewaysHost` forces a phone's, with the same
 * limits: it reaches neither a `Dialog` nor a popup's own window, and what spills past the size is
 * still on the emulator's screen, so `assertIsDisplayed` alone does not show that something fits.
 */
@Composable
fun TabletHost(size: TabletSize, fontScale: Float = 1f, content: @Composable () -> Unit) {
    DeviceConfigurationOverride(
        DeviceConfigurationOverride.FontScale(fontScale) then
            DeviceConfigurationOverride.ForcedSize(DpSize(size.logicalWidth.dp, size.logicalHeight.dp)),
    ) {
        CompositionLocalProvider(
            LocalSideways provides size.sideways,
            LocalTabZoom provides size.zoom,
            content = content,
        )
    }
}
