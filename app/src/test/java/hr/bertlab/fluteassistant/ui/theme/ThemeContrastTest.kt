// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import hr.bertlab.fluteassistant.ui.common.NEIGHBOUR_ALPHA
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

/**
 * The palette's text pairings that are not "ink on a bright fill", WCAG 2.x: 4.5:1 for small text,
 * 3:1 for large (24 sp bold and up). Found by the light theme's audit of 2026-11-11.
 */
class ThemeContrastTest {

    private val themes = listOf(
        Theme("light", LightFluteColors, dark = false),
        Theme("dark", DarkFluteColors, dark = true),
    )

    private class Theme(val name: String, val colors: FluteColors, val dark: Boolean)

    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
    }

    private fun assertContrast(what: String, ratio: Float, least: Float) {
        assertTrue("$what is $ratio:1, needs $least:1", ratio >= least)
    }

    @Test
    fun `the error line can be read on the card and on the page in both themes`() {
        for (theme in themes) {
            val error = theme.colors.toColorScheme(theme.dark).error
            assertContrast("${theme.name} error on the card", contrast(error, theme.colors.card), 4.5f)
            assertContrast("${theme.name} error on the page", contrast(error, theme.colors.page), 4.5f)
        }
    }

    @Test
    fun `text on the error colour can be read in both themes`() {
        for (theme in themes) {
            val scheme = theme.colors.toColorScheme(theme.dark)
            assertContrast("${theme.name} onError on error", contrast(scheme.onError, scheme.error), 4.5f)
        }
    }

    @Test
    fun `the tempo wheel's neighbours reach large text contrast on every ground`() {
        for (theme in themes) {
            val faded = theme.colors.mutedText.copy(alpha = NEIGHBOUR_ALPHA)
            for ((ground, colour) in listOf(
                "the dial's centre" to theme.colors.track,
                "the card" to theme.colors.card,
                "the page" to theme.colors.page,
            )) {
                assertContrast("${theme.name} neighbour on $ground", contrast(faded.compositeOver(colour), colour), 3f)
            }
        }
    }
}
