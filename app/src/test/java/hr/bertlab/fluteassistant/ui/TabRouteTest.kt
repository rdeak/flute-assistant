// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TabRouteTest {

    @Test
    fun `the app opens on the first tab, and back is the phone's`() {
        assertEquals(TabRoute(AppTab.entries.first(), back = null), TabRoute())
    }

    @Test
    fun `a tab chosen on the bar is open, and back is the phone's`() {
        assertEquals(TabRoute(AppTab.NOTES), TabRoute().select(AppTab.NOTES))
    }

    @Test
    fun `a tab opened by another goes back to it`() {
        val onFingers = TabRoute(AppTab.FINGERS)
        assertEquals(TabRoute(AppTab.TUNER, back = AppTab.FINGERS), onFingers.openFromHere(AppTab.TUNER))
    }

    @Test
    fun `going back opens the tab it came from, and back is the phone's again`() {
        val onTuner = TabRoute(AppTab.FINGERS).openFromHere(AppTab.TUNER)
        assertEquals(TabRoute(AppTab.FINGERS), onTuner.select(onTuner.back!!))
    }

    @Test
    fun `another tab chosen on the bar forgets the way back`() {
        val onTuner = TabRoute(AppTab.FINGERS).openFromHere(AppTab.TUNER)
        assertEquals(TabRoute(AppTab.FLUTE), onTuner.select(AppTab.FLUTE))
    }

    @Test
    fun `the open tab chosen again changes nothing, the way back included`() {
        val onTuner = TabRoute(AppTab.FINGERS).openFromHere(AppTab.TUNER)
        assertEquals(onTuner, onTuner.select(AppTab.TUNER))
        assertEquals(onTuner, onTuner.openFromHere(AppTab.TUNER))
    }
}
