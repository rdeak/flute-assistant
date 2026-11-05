// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The few icons that are not emoji, hand-built so the app does not depend on the (large)
 * extended Material icon set: the tuner's ‹ › buttons (the standard 24dp Material paths), the
 * pitch window's round-ended strokes and the big action buttons' icons (the canvas's own, in
 * the same 24 box). Tinting is applied by [androidx.compose.material3.Icon].
 */
object AppIcons {
    val ChevronLeft: ImageVector by lazy { filled("chevron_left", "M15.41,7.41L14,6l-6,6 6,6 1.41,-1.41L10.83,12z") }
    val ChevronRight: ImageVector by lazy { filled("chevron_right", "M10,6L8.59,7.41 13.17,12l-4.58,4.59L10,18l6,-6z") }
    val ChevronUp: ImageVector by lazy { stroked("chevron_up", "M6,15l6,-6 6,6", 3.5f) }
    val ChevronDown: ImageVector by lazy { stroked("chevron_down", "M6,9l6,6 6,-6", 3.5f) }
    val ArrowUp: ImageVector by lazy { stroked("arrow_up", "M12,19V6M6,11l6,-6 6,6", 3.5f) }
    val ArrowDown: ImageVector by lazy { stroked("arrow_down", "M12,5V18M6,13l6,6 6,-6", 3.5f) }
    val Check: ImageVector by lazy { stroked("check", "M5,12.5l4.5,4.5L19,7.5", 4f) }

    // The big action buttons: Play, Stop, OK / Yes and Again.
    val Play: ImageVector by lazy {
        filled("play", "M8,5.5v13a1,1 0 0 0 1.5,0.86l10.5,-6.5a1,1 0 0 0 0,-1.72L9.5,4.64A1,1 0 0 0 8,5.5z")
    }
    val Stop: ImageVector by lazy {
        filled("stop", "M8.5,6h7a2.5,2.5 0 0 1 2.5,2.5v7a2.5,2.5 0 0 1 -2.5,2.5h-7a2.5,2.5 0 0 1 -2.5,-2.5v-7a2.5,2.5 0 0 1 2.5,-2.5z")
    }
    val Done: ImageVector by lazy { stroked("done", "M5,12.5l4.5,4.5L19,7.5", 3f) }
    val Again: ImageVector by lazy {
        stroked("again", "M17,2l3,3 -3,3M4,11V9a4,4 0 0 1 4,-4h12M7,22l-3,-3 3,-3M20,13v2a4,4 0 0 1 -4,4H4", 3f)
    }
    /** The flute's "Your turn": a microphone, its capsule in a U-shaped stand on a base. */
    val Microphone: ImageVector by lazy {
        filled(
            "microphone",
            "M12,2.5a3.25,3.25 0 0 1 3.25,3.25v4.75a3.25,3.25 0 0 1 -6.5,0V5.75A3.25,3.25 0 0 1 12,2.5z" +
                "M5.5,10.5a1,1 0 0 1 2,0a4.5,4.5 0 0 0 9,0a1,1 0 0 1 2,0a6.5,6.5 0 0 1 -5.5,6.42V19.5h2.5a1,1 0 0 1 0,2h-7a1,1 0 0 1 0,-2H11v-2.58A6.5,6.5 0 0 1 5.5,10.5z",
        )
    }

    private fun nodes(pathData: String) = PathParser().parsePathString(pathData).toNodes()

    private fun filled(name: String, pathData: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(pathData = nodes(pathData), fill = SolidColor(Color.Black))
            .build()

    private fun stroked(name: String, pathData: String, width: Float): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            .addPath(
                pathData = nodes(pathData),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            .build()
}
