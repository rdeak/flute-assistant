// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.flute

import androidx.compose.ui.graphics.Path

/**
 * A slur as drawn (since 2026-11-16), in dp: the outer curve of [curve], then back along the inner one, whose control
 * points lie [StaffGeometry.SLUR_THICKNESS] higher; filled, it is a crescent thin at the ends, as engraved music has
 * it. The staff draws it under joined notes and the ⌒ key draws it as its picture.
 */
internal fun slurPath(curve: StaffGeometry.SlurCurve): Path = Path().apply {
    val t = StaffGeometry.SLUR_THICKNESS
    moveTo(curve.x0, curve.y0)
    cubicTo(curve.c1x, curve.c1y, curve.c2x, curve.c2y, curve.x1, curve.y1)
    cubicTo(curve.c2x, curve.c2y - t, curve.c1x, curve.c1y - t, curve.x0, curve.y0)
    close()
}
