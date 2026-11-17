// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Density

/**
 * How much the open tab is zoomed (spec `2026-11-06-tablet-design.md`): 1 on a phone, more on a
 * tablet, where `MainFrame` draws the tab at a higher density. Static, as it changes only when the
 * window does and then the whole tab is laid out again anyway. The flute reads it for its keys and
 * staff, the things adjusted on top of the zoom; nothing else needs to.
 */
val LocalTabZoom = staticCompositionLocalOf { 1f }

/**
 * This density [zoom] times higher, for the tab. At 1 it is this very density, so a phone's tab is
 * handed exactly what its frame has. A plain `Density` keeps Android's curve for large text, which
 * `TabletDensityTest` holds it to against Android's own.
 */
internal fun Density.zoomedBy(zoom: Float): Density = if (zoom == 1f) this else Density(density * zoom, fontScale)

/**
 * The tablet's numbers, in dp as plain floats like [SidewaysGeometry], so a unit test can check what
 * Google's test sizes get. The zoom lays the phone layouts out in a smaller logical area, so they
 * come out bigger and the frame round them stays phone-sized.
 */
internal object TabletGeometry {
    /** The window's shortest side must be this long to be a tablet. It follows the window, not the device. */
    private const val MIN_SIDE = 600f

    /** The logical width an upright tab keeps: about a big phone's, with room to spare for its keys. */
    private const val UPRIGHT_WIDTH = 560f

    /** The logical height an upright tab keeps: the flute's `FitsHeight` (664) and a little more, so nothing scrolls. */
    private const val UPRIGHT_HEIGHT = 690f

    /** The logical width a sideways tab keeps: the 915 dp phone's content (823), which the layouts were drawn for. */
    private const val SIDEWAYS_WIDTH = 830f

    /** The logical height a sideways tab keeps: over the 411 dp phone's, as the width is what limits a 16:10 tablet. */
    private const val SIDEWAYS_HEIGHT = 500f

    /** The most the tab grows: past it a desktop window would get toy-sized controls. */
    private const val MAX_ZOOM = 2f

    /**
     * Upright, a phone's key and the most a zoomed one is: about 104 dp on a 10-inch tablet. The 58 is
     * also the flute's `KeyHeight` and `SidewaysGeometry.KEY_HEIGHT_MIN`; change the three together.
     */
    private const val KEY_HEIGHT = 58f
    private const val KEY_HEIGHT_MAX = 72f

    /** The flute's three key rows, which share what the height spares. */
    private const val KEY_ROWS = 3

    /**
     * The keys' column on a zoomed tab, sideways, against a phone's [PHONE_KEYS]: with
     * [SIDEWAYS_STAFF_SCALE] it leaves the staff room for about 13 quarter notes and their bar lines,
     * against about 10.
     */
    private const val SIDEWAYS_KEYS = 336f
    private const val PHONE_KEYS = 372f

    /** The resting staff on a zoomed tab, sideways: 1.3 times a phone's at 10 inches, 1.02 at 8. */
    private const val SIDEWAYS_STAFF_SCALE = 0.91f

    fun isTablet(windowWidth: Float, windowHeight: Float): Boolean = minOf(windowWidth, windowHeight) >= MIN_SIDE

    /**
     * How much a tab area of [width] x [height] grows, in real dp after the bars. A window too short
     * to grow, as a foldable or a pane of a split screen can be, stays at 1 and keeps its phone sizes.
     */
    fun tabZoom(width: Float, height: Float, sideways: Boolean, tablet: Boolean): Float {
        if (!tablet) return 1f
        val zoom = if (sideways) {
            minOf(width / SIDEWAYS_WIDTH, height / SIDEWAYS_HEIGHT)
        } else {
            minOf(width / UPRIGHT_WIDTH, height / UPRIGHT_HEIGHT)
        }
        return zoom.coerceIn(1f, MAX_ZOOM)
    }

    /**
     * Only a phone on its side hides the bars: the height they give back is what its running staff
     * needs, and a tablet has height to spare and need not meet Android's "Viewing full screen" prompt.
     */
    fun hidesSystemBars(sideways: Boolean, tablet: Boolean): Boolean = sideways && !tablet

    /** The keys' column at this [zoom]: [SIDEWAYS_KEYS], but a barely zoomed window keeps a phone's real width. */
    fun sidewaysKeys(zoom: Float): Float = maxOf(SIDEWAYS_KEYS, PHONE_KEYS / zoom)

    /** The resting staff at this [zoom]: [SIDEWAYS_STAFF_SCALE], but a barely zoomed window keeps a phone's real size. */
    fun sidewaysStaffScale(zoom: Float): Float = maxOf(SIDEWAYS_STAFF_SCALE, 1f / zoom)

    /** An upright key on a zoomed tab: a third of what the height [spare]s, as there are three rows. */
    fun uprightKeyHeight(spare: Float): Float = (KEY_HEIGHT + spare / KEY_ROWS).coerceIn(KEY_HEIGHT, KEY_HEIGHT_MAX)
}
