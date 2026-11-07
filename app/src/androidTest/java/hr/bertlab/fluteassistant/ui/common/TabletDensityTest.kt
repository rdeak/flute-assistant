// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import android.content.res.Configuration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android scales large text along a curve, not a straight line: at twice the text 24 sp is 36 dp, not
 * 48. A zoomed tab must keep that curve, or its words would grow out of step with the frame's. This
 * reads Android's own density, as the tests' `FontScale` override does not go through it.
 */
@RunWith(AndroidJUnit4::class)
class TabletDensityTest {

    private fun androidDensity(fontScale: Float): Density {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(context.resources.configuration).apply { this.fontScale = fontScale }
        return Density(context.createConfigurationContext(configuration))
    }

    @Test
    fun a_zoomed_density_keeps_androids_curve_for_large_text() {
        for (fontScale in listOf(1f, 1.15f, 1.3f, 1.5f, 1.8f, 2f)) {
            val real = androidDensity(fontScale)
            val zoomed = real.zoomedBy(1.43f)
            for (size in listOf(12, 16, 22, 24, 30, 36, 56)) {
                val asked = with(real) { size.sp.toDp().value }
                assertEquals("$size sp at font scale $fontScale", asked, with(zoomed) { size.sp.toDp().value }, 0.01f)
            }
        }
    }

    @Test
    fun a_zoomed_density_draws_that_many_times_bigger() {
        val real = androidDensity(1.5f)
        val zoomed = real.zoomedBy(1.43f)
        assertEquals(real.density * 1.43f, zoomed.density, 0.001f)
        assertEquals(with(real) { 24.sp.toPx() } * 1.43f, with(zoomed) { 24.sp.toPx() }, 0.5f)
    }

    @Test
    fun no_zoom_is_the_same_density() {
        val real = androidDensity(1.5f)
        assertSame(real, real.zoomedBy(1f))
    }
}
