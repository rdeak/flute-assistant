// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.fingers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import hr.bertlab.fluteassistant.ui.common.LocaleProvider
import hr.bertlab.fluteassistant.ui.common.MusicSymbols
import hr.bertlab.fluteassistant.ui.common.isNear
import hr.bertlab.fluteassistant.ui.common.needsDevice
import hr.bertlab.fluteassistant.ui.theme.DarkFluteColors
import hr.bertlab.fluteassistant.ui.theme.FluteAssistantTheme
import hr.bertlab.fluteassistant.ui.theme.FluteTheme
import hr.bertlab.fluteassistant.ui.theme.LightFluteColors
import hr.bertlab.fluteassistant.ui.theme.NoteLetterColors
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Fingers tab's staff in Bravura's music symbols (spec `2026-11-24-fingers-music-symbols-and-book-chart-design.md`, part 2). */
@RunWith(AndroidJUnit4::class)
class NoteOnStaffTest {

    @get:Rule
    val compose = createComposeRule()

    private var fontScale by mutableStateOf(1f)

    /** Each of [midis] on a staff at its full size, side by side on the card's colour, tagged "staff<midi>". */
    private fun show(vararg midis: Int, dark: Boolean = false) {
        compose.setContent {
            LocaleProvider(Locale.ENGLISH) {
                if (dark) FluteAssistantTheme(darkTheme = true) { Staves(midis) } else Staves(midis)
            }
        }
    }

    @Composable
    private fun Staves(midis: IntArray) {
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
            Row(Modifier.background(FluteTheme.colors.card)) {
                midis.forEach { NoteOnStaff(it, Modifier.size(StaffWidth.dp, StaffHeight.dp).testTag("staff$it")) }
            }
        }
    }

    private fun pixels(midi: Int): PixelMap = compose.onNodeWithTag("staff$midi").captureToImage().toPixelMap()

    /** How many pixels near [ink] in the columns [fromDp] to [toDp] of [midi]'s staff. */
    private fun inkIn(midi: Int, fromDp: Float, toDp: Float, ink: Color): Int {
        val map = pixels(midi)
        val perDp = map.width / StaffWidth
        var count = 0
        for (x in (fromDp * perDp).toInt() until (toDp * perDp).toInt()) for (y in 0 until map.height) {
            if (map[x, y].isNear(ink, 0.15f)) count++
        }
        return count
    }

    @Test
    fun on_the_night_theme_the_symbols_are_in_the_night_ink() {
        needsDevice()
        show(61, dark = true)
        val map = pixels(61)
        val all = (0 until map.width).flatMap { x -> (0 until map.height).map { y -> map[x, y] } }
        assertTrue("some pixel is the night ink", all.any { it.isNear(DarkFluteColors.text) })
        assertTrue("no pixel is the day ink", all.none { it.isNear(LightFluteColors.text) })
    }

    @Test
    fun the_symbols_do_not_grow_with_the_text_size() {
        needsDevice()
        show(61)
        val before = pixels(61)
        compose.runOnIdle { fontScale = 2f }
        compose.waitForIdle()
        val after = pixels(61)
        assertEquals(before.width, after.width)
        assertEquals(before.height, after.height)
        for (x in 0 until before.width) for (y in 0 until before.height) {
            assertEquals("pixel $x, $y", before[x, y], after[x, y])
        }
    }

    /** Where the sharp stands, left of the note and its ledger line, C♯4 has ink that C4 has not. */
    @Test
    fun a_sharp_note_draws_its_sign_before_the_note() = assertSignDrawn(plain = 60, signed = 61, width = MusicSymbols.SHARP_WIDTH)

    /** The same for a flat: B♭4 against B4. */
    @Test
    fun a_flat_note_draws_its_sign_before_the_note() = assertSignDrawn(plain = 71, signed = 70, width = MusicSymbols.FLAT_WIDTH)

    private fun assertSignDrawn(plain: Int, signed: Int, width: Float) {
        needsDevice()
        show(plain, signed)
        val g = SymbolStaff.gapFor(StaffHeight)
        val right = SymbolStaff.signRight(StaffWidth, g)
        val ink = LightFluteColors.text
        val without = inkIn(plain, right - width * g, right, ink)
        val with = inkIn(signed, right - width * g, right, ink)
        assertTrue("$signed has $with ink pixels where its sign stands, $plain $without", with > without + 20)
    }

    /**
     * The whole note's hollow is all the letter's colour: no hairline of the card between the colour and the ink (the
     * final review, 2026-11-24: an oval of 0.62 x 0.4 gaps left one at the hollow's tilted ends). The hairline is thinner
     * than a pixel, so it shows as the note's yellow paled towards the card: a pixel with the yellow's red and more blue
     * than the yellow has. The ink, and its blend with the yellow, keep the yellow's low blue.
     */
    @Test
    fun the_whole_notes_hollow_is_all_colour() {
        needsDevice()
        show(76) // E5, yellow, in a space: no line through it
        val map = pixels(76)
        val perDp = map.width / StaffWidth
        val g = SymbolStaff.gapFor(StaffHeight)
        val bottom = SymbolStaff.bottomLine(StaffHeight, g)
        val cx = SymbolStaff.noteX(StaffWidth, g) * perDp
        val cy = SymbolStaff.yOf(9, bottom, g) * perDp
        val (rx, ry) = 0.78f * g * perDp to 0.44f * g * perDp // inside the head's outer shape (0.844 x 0.5)
        val yellow = NoteLetterColors[2]
        var pale = 0
        for (x in (cx - rx).toInt()..(cx + rx).toInt()) for (y in (cy - ry).toInt()..(cy + ry).toInt()) {
            val (dx, dy) = (x - cx) / rx to (y - cy) / ry
            val pixel = map[x, y]
            if (dx * dx + dy * dy <= 1f && pixel.red > 0.6f && pixel.blue > yellow.blue + 0.08f) pale++
        }
        assertEquals("pixels of the yellow paled by the card inside the note", 0, pale)
    }

    private companion object {
        const val StaffWidth = 74f
        const val StaffHeight = SymbolStaff.TALL

    }
}
