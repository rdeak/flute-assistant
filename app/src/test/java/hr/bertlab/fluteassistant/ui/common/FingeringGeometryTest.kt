// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import hr.bertlab.fluteassistant.domain.tuner.FluteKey
import hr.bertlab.fluteassistant.domain.tuner.FluteKey.B_FLAT_LEVER
import hr.bertlab.fluteassistant.domain.tuner.FluteKey.C_SHARP
import hr.bertlab.fluteassistant.domain.tuner.FluteKey.D_SHARP_TRILL
import hr.bertlab.fluteassistant.domain.tuner.FluteKey.D_TRILL
import hr.bertlab.fluteassistant.domain.tuner.FluteKey.E_FLAT
import hr.bertlab.fluteassistant.domain.tuner.FluteKey.G_SHARP
import hr.bertlab.fluteassistant.domain.tuner.FluteKey.L1
import hr.bertlab.fluteassistant.domain.tuner.FluteKey.L2
import hr.bertlab.fluteassistant.domain.tuner.FluteKey.L3
import hr.bertlab.fluteassistant.domain.tuner.FluteKey.LOW_C
import hr.bertlab.fluteassistant.domain.tuner.FluteKey.R1
import hr.bertlab.fluteassistant.domain.tuner.FluteKey.R2
import hr.bertlab.fluteassistant.domain.tuner.FluteKey.R3
import hr.bertlab.fluteassistant.domain.tuner.FluteKey.THUMB
import hr.bertlab.fluteassistant.domain.tuner.FluteKey.THUMB_B_FLAT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/** The chart's numbers (spec `2026-11-24-fingers-music-symbols-and-book-chart-design.md`, part 1). */
class FingeringGeometryTest {
    private val keys = FingeringGeometry.KEYS

    private fun box(s: FingeringGeometry.Spot) =
        floatArrayOf(s.x - s.width / 2, s.y - s.height / 2, s.x + s.width / 2, s.y + s.height / 2)

    @Test
    fun `every key has a spot inside the 340 by 56 picture`() {
        assertEquals(FluteKey.entries.toSet(), keys.keys)
        keys.values.forEach { s ->
            val (left, top, right, bottom) = box(s).toList()
            assertTrue("$s", left >= 0f && top >= 0f && right <= 340f && bottom <= 56f)
        }
        assertEquals(340f / 56f, FINGERING_ASPECT_RATIO, 0.001f)
    }

    @Test
    fun `the six finger keys are in a row on the tube's line`() {
        val cups = listOf(L1 to 63f, L2 to 93f, L3 to 123f, R1 to 192f, R2 to 226f, R3 to 260f)
        cups.forEach { (key, x) ->
            assertEquals(FingeringGeometry.Spot(x, 28f, 26f, 26f, round = true), keys.getValue(key))
        }
    }

    @Test
    fun `the far side's keys are above the line and the near side's below`() {
        listOf(G_SHARP, B_FLAT_LEVER, D_TRILL, D_SHARP_TRILL).forEach { assertTrue("$it", keys.getValue(it).y < 28f - 3f) }
        listOf(THUMB_B_FLAT, THUMB, E_FLAT, C_SHARP, LOW_C).forEach { assertTrue("$it", keys.getValue(it).y > 28f + 3f) }
    }

    /** The three far-side levers stand at cy − 16, one unit over the mockup, so B♭6's pressed R1 and D trill outlines do not touch. */
    @Test
    fun `the other keys are where the spec puts them`() {
        assertEquals(FingeringGeometry.Spot(17f, 41f, 12f, 22f), keys.getValue(THUMB_B_FLAT))
        assertEquals(FingeringGeometry.Spot(34f, 41f, 15f, 24f), keys.getValue(THUMB))
        assertEquals(FingeringGeometry.Spot(150f, 14f, 22f, 12f, turn = -14f), keys.getValue(G_SHARP))
        listOf(B_FLAT_LEVER to 172f, D_TRILL to 209f, D_SHARP_TRILL to 243f).forEach { (key, x) ->
            assertEquals(FingeringGeometry.Spot(x, 12f, 8f, 16f), keys.getValue(key))
        }
        assertEquals(FingeringGeometry.Spot(287f, 40f, 22f, 13f, turn = 14f), keys.getValue(E_FLAT))
        assertEquals(FingeringGeometry.Spot(308f, 43f, 13f, 15f), keys.getValue(C_SHARP))
        assertEquals(FingeringGeometry.Spot(326f, 43f, 13f, 15f), keys.getValue(LOW_C))
    }

    @Test
    fun `the hands' brackets span their keys`() {
        assertEquals(8f..160f, FingeringGeometry.LEFT_HAND_SPAN)
        assertEquals(168f..334f, FingeringGeometry.RIGHT_HAND_SPAN)
        val left = setOf(THUMB_B_FLAT, THUMB, L1, L2, L3, G_SHARP)
        keys.forEach { (key, spot) ->
            val span = if (key in left) FingeringGeometry.LEFT_HAND_SPAN else FingeringGeometry.RIGHT_HAND_SPAN
            assertTrue("$key at ${spot.x} is in $span", spot.x in span)
        }
    }

    /**
     * The real outlines, not their boxes: two pressed keys' outlines are 1.5 a side, so the shapes stand at least 3.25
     * apart. At these numbers the closest pair is E flat and C sharp at 3.43.
     */
    @Test
    fun `no two keys touch, outlines included`() {
        val outlines = keys.mapValues { (_, spot) -> outline(spot) }
        val all = outlines.entries.toList()
        for (i in all.indices) for (j in i + 1 until all.size) {
            val apart = all[i].value.minOf { p -> all[j].value.minOf { q -> hypot(p.first - q.first, p.second - q.second) } }
            assertTrue("${all[i].key} and ${all[j].key} are $apart apart", apart >= 3.25f)
        }
    }

    /** [spot] as 400 points round its edge: a circle, or a rectangle with corners of radius 0.42 of its shorter side, turned. */
    private fun outline(spot: FingeringGeometry.Spot, points: Int = 400): List<Pair<Float, Float>> {
        val local = if (spot.round) {
            List(points) { i ->
                val a = 2 * PI * i / points
                (spot.width / 2 * cos(a)).toFloat() to (spot.height / 2 * sin(a)).toFloat()
            }
        } else {
            val r = min(spot.width, spot.height) * 0.42f
            val (hw, hh) = spot.width / 2 - r to spot.height / 2 - r
            val corners = listOf(Triple(hw, hh, 0.0), Triple(-hw, hh, 90.0), Triple(-hw, -hh, 180.0), Triple(hw, -hh, 270.0))
            corners.flatMap { (cx, cy, from) ->
                List(points / 4 + 1) { i ->
                    val a = Math.toRadians(from + 90.0 * i / (points / 4))
                    (cx + r * cos(a)).toFloat() to (cy + r * sin(a)).toFloat()
                }
            }
        }
        // The straight sides between the corners too: a point every quarter unit round the whole edge.
        val edge = (local + local.first()).zipWithNext().flatMap { (a, b) ->
            val steps = maxOf(1, (hypot(b.first - a.first, b.second - a.second) / 0.25f).toInt())
            List(steps) { k -> (a.first + (b.first - a.first) * k / steps) to (a.second + (b.second - a.second) * k / steps) }
        }
        val t = Math.toRadians(spot.turn.toDouble())
        return edge.map { (px, py) ->
            (spot.x + px * cos(t) - py * sin(t)).toFloat() to (spot.y + px * sin(t) + py * cos(t)).toFloat()
        }
    }
}
