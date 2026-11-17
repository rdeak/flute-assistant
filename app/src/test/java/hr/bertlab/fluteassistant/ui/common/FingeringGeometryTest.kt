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

/** The picture's numbers, as the spec's table (2026-11-17-fingers-tab-design.md, part 2) gives them. */
class FingeringGeometryTest {
    private val keys = FingeringGeometry.KEYS

    private fun box(s: FingeringGeometry.Spot) =
        floatArrayOf(s.x - s.width / 2, s.y - s.height / 2, s.x + s.width / 2, s.y + s.height / 2)

    @Test
    fun `every key has a spot inside the 340 by 66 picture`() {
        assertEquals(FluteKey.entries.toSet(), keys.keys)
        (keys.values + FingeringGeometry.IDLE_CUPS).forEach { s ->
            val (left, top, right, bottom) = box(s).toList()
            assertTrue("$s", left >= 0f && top >= 0f && right <= 340f && bottom <= 66f)
        }
        assertEquals(340f / 66f, FINGERING_ASPECT_RATIO, 0.001f)
    }

    @Test
    fun `the six finger cups are in a row on the tube`() {
        val cups = listOf(L1 to 66f, L2 to 112f, L3 to 136f, R1 to 205f, R2 to 233f, R3 to 261f)
        cups.forEach { (key, x) ->
            assertEquals(FingeringGeometry.Spot(x, 31f, 21f, 21f, round = true), keys.getValue(key))
        }
    }

    @Test
    fun `the far side's keys are above the tube and the near side's below`() {
        listOf(G_SHARP, B_FLAT_LEVER, D_TRILL, D_SHARP_TRILL).forEach { assertTrue("$it", keys.getValue(it).y < 31f - 7f) }
        listOf(THUMB_B_FLAT, THUMB, E_FLAT, C_SHARP, LOW_C).forEach { assertTrue("$it", keys.getValue(it).y > 31f + 7f) }
    }

    @Test
    fun `the levers and the foot keys are where the spec puts them`() {
        assertEquals(FingeringGeometry.Spot(158f, 9f, 21f, 12f, turn = -10f), keys.getValue(G_SHARP))
        assertEquals(FingeringGeometry.Spot(72f, 54f, 12f, 19f), keys.getValue(THUMB_B_FLAT))
        assertEquals(FingeringGeometry.Spot(89f, 54f, 16f, 17f), keys.getValue(THUMB))
        listOf(B_FLAT_LEVER to 190f, D_TRILL to 219f, D_SHARP_TRILL to 247f).forEach { (key, x) ->
            assertEquals(FingeringGeometry.Spot(x, 11f, 8f, 14f), keys.getValue(key))
        }
        assertEquals(FingeringGeometry.Spot(288f, 52f, 19f, 12f, turn = 12f), keys.getValue(E_FLAT))
        assertEquals(FingeringGeometry.Spot(307f, 54f, 13f, 13f), keys.getValue(C_SHARP))
        assertEquals(FingeringGeometry.Spot(324f, 54f, 13f, 13f, round = true), keys.getValue(LOW_C))
    }

    /** G sharp against L3 is exactly 1 apart (147.5 and 146.5): loosen this margin before moving a key. */
    @Test
    fun `no two keys touch`() {
        val all = keys.entries.toList()
        for (i in all.indices) for (j in i + 1 until all.size) {
            val (a, b) = box(all[i].value) to box(all[j].value)
            val apart = a[2] + 1f <= b[0] || b[2] + 1f <= a[0] || a[3] + 1f <= b[1] || b[3] + 1f <= a[1]
            assertTrue("${all[i].key} and ${all[j].key}", apart)
        }
    }

    @Test
    fun `seven cups no finger rests on`() {
        assertEquals(listOf(45f, 89f, 160f, 181f, 290f, 308f, 326f), FingeringGeometry.IDLE_CUPS.map { it.x })
        assertEquals(12f, FingeringGeometry.IDLE_CUPS.first().width) // radius 6
        FingeringGeometry.IDLE_CUPS.drop(1).forEach { assertEquals(15f, it.width) } // radius 7.5
    }
}
