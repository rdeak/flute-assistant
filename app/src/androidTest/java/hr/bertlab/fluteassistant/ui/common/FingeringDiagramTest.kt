// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import hr.bertlab.fluteassistant.domain.tuner.FluteKey
import hr.bertlab.fluteassistant.ui.theme.LightFluteColors
import hr.bertlab.fluteassistant.ui.theme.NoteLetterColors
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The fingering chart (spec `2026-11-24-fingers-music-symbols-and-book-chart-design.md`, part 1). */
@RunWith(AndroidJUnit4::class)
class FingeringDiagramTest {

    @get:Rule
    val compose = createComposeRule()

    /** The picture for [midi], 340 dp wide, in the note's own colour as every caller but the tuner draws it. */
    private fun show(midi: Int?) {
        compose.setContent {
            LocaleProvider(Locale.ENGLISH) {
                FingeringDiagram(midi, Modifier.width(340.dp).testTag("diagram"))
            }
        }
    }

    /** The colour at the centre of [key], read off a capture of the 340 dp wide picture. */
    private fun colourAt(key: FluteKey): Color {
        val spot = FingeringGeometry.KEYS.getValue(key)
        val pixels = compose.onNodeWithTag("diagram").captureToImage().toPixelMap()
        val scale = pixels.width / FingeringGeometry.WIDTH
        return pixels[(spot.x * scale).toInt(), (spot.y * scale).toInt()]
    }

    private fun assertColour(expected: Color, actual: Color) = assertTrue("expected $expected, was $actual", actual.isNear(expected))

    @Test
    fun a_note_is_named_for_a_screen_reader() {
        show(69)
        compose.onNodeWithContentDescription("Fingering for A4").assertExists()
    }

    @Test
    fun with_no_note_every_key_is_open_and_nothing_is_said() {
        show(null)
        compose.onNodeWithContentDescription("Fingering", substring = true).assertDoesNotExist()
    }

    @Test
    fun the_picture_is_56_tall_at_340_wide() {
        show(69)
        val bounds = compose.onNodeWithTag("diagram").getUnclippedBoundsInRoot()
        assertEquals(340f, bounds.width.value, 0.5f)
        assertEquals(56f, bounds.height.value, 0.5f)
    }

    @Test
    fun a_pressed_key_is_in_the_notes_colour_and_an_open_one_is_not() {
        needsDevice()
        show(69)
        assertColour(NoteLetterColors[5], colourAt(FluteKey.L1))
        assertColour(LightFluteColors.raised, colourAt(FluteKey.R1))
        assertColour(LightFluteColors.raised, colourAt(FluteKey.D_TRILL))
    }

    @Test
    fun the_trill_key_is_pressed_for_b_flat_6() {
        needsDevice()
        show(94)
        assertColour(NoteLetterColors[6], colourAt(FluteKey.D_TRILL))
        assertColour(LightFluteColors.raised, colourAt(FluteKey.L1))
    }
}
