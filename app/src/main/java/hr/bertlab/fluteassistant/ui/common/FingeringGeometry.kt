// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import hr.bertlab.fluteassistant.domain.tuner.FluteKey

/**
 * Where everything in [FingeringDiagram] stands, in design units of a [WIDTH] x [HEIGHT] picture: the flute seen
 * from above by the player, the head joint on the left (spec `2026-11-17-fingers-tab-design.md`, part 2, the user's
 * pick "B, the flute itself"). Keys on the far side of the flute (the G sharp lever, the side B flat lever, the two
 * trill keys) stand above the tube; the thumb's keys and the foot's below it. Plain numbers, so the JVM tests pin them.
 */
internal object FingeringGeometry {
    const val WIDTH = 340f
    const val HEIGHT = 66f

    /** The tube's centre line. */
    const val CENTRE_Y = 31f

    /** The tube: from [TUBE_LEFT] to [TUBE_RIGHT], [TUBE_THICKNESS] thick, with round ends. */
    const val TUBE_LEFT = 2f
    const val TUBE_RIGHT = 338f
    const val TUBE_THICKNESS = 14f

    /** The two joints, where the head joint meets the body and the body the foot: 5 wide and 18 tall. */
    val JOINTS = listOf(29f, 274f)
    const val JOINT_WIDTH = 5f
    const val JOINT_HEIGHT = 18f

    /** The lip plate, an ellipse with its hole. */
    val LIP_PLATE = Spot(15f, CENTRE_Y, 20f, 19f, round = true)
    val LIP_HOLE = Spot(15f, CENTRE_Y, 9f, 6f, round = true)

    /** A key or a cup: its centre, its size, how far it is turned (degrees, clockwise) and whether it is round. */
    data class Spot(
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val turn: Float = 0f,
        val round: Boolean = false,
    )

    private fun cup(x: Float, radius: Float) = Spot(x, CENTRE_Y, radius * 2, radius * 2, round = true)

    /** The fifteen keys a finger touches. */
    val KEYS: Map<FluteKey, Spot> = mapOf(
        FluteKey.THUMB_B_FLAT to Spot(72f, CENTRE_Y + 23f, 12f, 19f),
        FluteKey.THUMB to Spot(89f, CENTRE_Y + 23f, 16f, 17f),
        FluteKey.L1 to cup(66f, 10.5f),
        FluteKey.L2 to cup(112f, 10.5f),
        FluteKey.L3 to cup(136f, 10.5f),
        FluteKey.G_SHARP to Spot(158f, CENTRE_Y - 22f, 21f, 12f, turn = -10f),
        FluteKey.B_FLAT_LEVER to Spot(190f, CENTRE_Y - 20f, 8f, 14f),
        FluteKey.R1 to cup(205f, 10.5f),
        FluteKey.D_TRILL to Spot(219f, CENTRE_Y - 20f, 8f, 14f),
        FluteKey.R2 to cup(233f, 10.5f),
        FluteKey.D_SHARP_TRILL to Spot(247f, CENTRE_Y - 20f, 8f, 14f),
        FluteKey.R3 to cup(261f, 10.5f),
        FluteKey.E_FLAT to Spot(288f, CENTRE_Y + 21f, 19f, 12f, turn = 12f),
        FluteKey.C_SHARP to Spot(307f, CENTRE_Y + 23f, 13f, 13f),
        FluteKey.LOW_C to Spot(324f, CENTRE_Y + 23f, 13f, 13f, round = true),
    )

    /** The seven cups no finger rests on, drawn faintly so a child can count along the real flute. */
    val IDLE_CUPS: List<Spot> = listOf(cup(45f, 6f)) + listOf(89f, 160f, 181f, 290f, 308f, 326f).map { cup(it, 7.5f) }

    /** A short arm from the tube ([x1], [y1]) out to a key that stands off it ([x2], [y2]). */
    data class Arm(val x1: Float, val y1: Float, val x2: Float, val y2: Float)

    /** The arms, drawn under the keys. */
    val ARMS: List<Arm> = listOf(
        Arm(160f, CENTRE_Y - 8f, 158f, CENTRE_Y - 18f), // G sharp
        Arm(76f, CENTRE_Y + 8f, 74f, CENTRE_Y + 16f), // the thumb's B flat lever
        Arm(87f, CENTRE_Y + 8f, 89f, CENTRE_Y + 16f), // the thumb's B key
    ) + listOf(190f, 219f, 247f).map { Arm(it, CENTRE_Y - 8f, it, CENTRE_Y - 14f) } // the side lever and the trill keys
}

/** Width over height of [FingeringDiagram]. */
const val FINGERING_ASPECT_RATIO = FingeringGeometry.WIDTH / FingeringGeometry.HEIGHT
