// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import hr.bertlab.fluteassistant.domain.tuner.FluteKey

/**
 * Where everything in [FingeringDiagram] stands, in design units of a [WIDTH] x [HEIGHT] picture: a fingering chart
 * as a flute book prints it (spec `2026-11-24-fingers-music-symbols-and-book-chart-design.md`, part 1, the user's
 * pick "A, lying down"). The flute lies as the player holds it, the head end on the left, and only the fifteen keys a
 * finger touches are drawn, each on its side of a thin line for the tube: keys on the far side of the flute (the G
 * sharp lever, the side B flat lever, the two trill keys) above the line, the thumb's keys and the foot's below it.
 * Plain numbers, so the JVM tests pin them. From 2026-11-17 to 2026-11-24 the picture was the whole flute.
 */
internal object FingeringGeometry {
    const val WIDTH = 340f
    const val HEIGHT = 56f

    /** The tube's line. */
    const val CENTRE_Y = 28f

    /** The tube: a thin line from [TUBE_LEFT] to [TUBE_RIGHT], [TUBE_THICKNESS] thick, with round ends. */
    const val TUBE_LEFT = 40f
    const val TUBE_RIGHT = 276f
    const val TUBE_THICKNESS = 6f

    /** A key: its centre, its size, how far it is turned (degrees, clockwise) and whether it is round. */
    data class Spot(
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val turn: Float = 0f,
        val round: Boolean = false,
    )

    private fun fingerKey(x: Float) = Spot(x, CENTRE_Y, 26f, 26f, round = true)

    /**
     * The fifteen keys a finger touches (the mockup's `strip()`). The three far-side levers stand at [CENTRE_Y] − 16,
     * one unit over the mockup: at − 15 a lever and its finger key are exactly 3 apart, and B♭6 presses both R1 and the
     * D trill key, whose 1.5-unit outlines would touch.
     */
    val KEYS: Map<FluteKey, Spot> = mapOf(
        FluteKey.THUMB_B_FLAT to Spot(17f, CENTRE_Y + 13f, 12f, 22f),
        FluteKey.THUMB to Spot(34f, CENTRE_Y + 13f, 15f, 24f),
        FluteKey.L1 to fingerKey(63f),
        FluteKey.L2 to fingerKey(93f),
        FluteKey.L3 to fingerKey(123f),
        FluteKey.G_SHARP to Spot(150f, CENTRE_Y - 14f, 22f, 12f, turn = -14f),
        FluteKey.B_FLAT_LEVER to Spot(172f, CENTRE_Y - 16f, 8f, 16f),
        FluteKey.R1 to fingerKey(192f),
        FluteKey.D_TRILL to Spot(209f, CENTRE_Y - 16f, 8f, 16f),
        FluteKey.R2 to fingerKey(226f),
        FluteKey.D_SHARP_TRILL to Spot(243f, CENTRE_Y - 16f, 8f, 16f),
        FluteKey.R3 to fingerKey(260f),
        FluteKey.E_FLAT to Spot(287f, CENTRE_Y + 12f, 22f, 13f, turn = 14f),
        FluteKey.C_SHARP to Spot(308f, CENTRE_Y + 15f, 13f, 15f),
        FluteKey.LOW_C to Spot(326f, CENTRE_Y + 15f, 13f, 15f),
    )

    /**
     * Where the Fingers tab brackets each hand under the chart: the left hand's keys (both thumb keys to the G sharp
     * lever) and the right hand's (the side B flat lever to the C key). The names stand inside these.
     */
    val LEFT_HAND_SPAN: ClosedFloatingPointRange<Float> = 8f..160f
    val RIGHT_HAND_SPAN: ClosedFloatingPointRange<Float> = 168f..334f
}

/** Width over height of [FingeringDiagram]. */
const val FINGERING_ASPECT_RATIO = FingeringGeometry.WIDTH / FingeringGeometry.HEIGHT
