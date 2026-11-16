// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.flute

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import hr.bertlab.fluteassistant.domain.flute.Listening
import hr.bertlab.fluteassistant.domain.flute.NoteLength
import hr.bertlab.fluteassistant.domain.flute.TuneNote
import hr.bertlab.fluteassistant.domain.metronome.MetronomeConfig
import hr.bertlab.fluteassistant.ui.common.LocaleProvider
import hr.bertlab.fluteassistant.ui.common.TabletGeometry
import hr.bertlab.fluteassistant.ui.common.TabletHost
import hr.bertlab.fluteassistant.ui.common.TabletSize
import hr.bertlab.fluteassistant.ui.common.assertWhole
import hr.bertlab.fluteassistant.ui.metronome.MetronomeUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import kotlin.math.abs

/**
 * The two things the flute adjusts on top of a tablet's zoom (spec `2026-11-06-tablet-design.md`):
 * taller keys upright, and sideways a narrower column of keys beside a smaller resting staff. Sizes
 * are logical, as the layout has them; `TabletHost` forces the area.
 */
@RunWith(AndroidJUnit4::class)
class FluteTabletTest {

    @get:Rule
    val compose = createComposeRule()

    private var size by mutableStateOf(TabletSize.TEN_UPRIGHT)
    private var state by mutableStateOf(FluteUiState())

    private val twoNotes = listOf(TuneNote(60, NoteLength.QUARTER), TuneNote(67, NoteLength.QUARTER))

    private fun show(first: TabletSize, initial: FluteUiState = FluteUiState(tune = twoNotes)) {
        size = first
        state = initial
        val metronome = MetronomeUiState(config = MetronomeConfig(bpm = 120))
        compose.setContent {
            LocaleProvider(Locale.ENGLISH) {
                TabletHost(size) {
                    FluteContent(
                        state = state,
                        metronome = metronome,
                        actions = FluteActions(),
                    )
                }
            }
        }
    }

    private fun on(each: TabletSize) {
        compose.runOnIdle { size = each }
        compose.waitForIdle()
    }

    private fun key(name: String) = compose.onNodeWithContentDescription(name)
    private fun book() = compose.onNodeWithContentDescription("Tune: Lollipop, tap to change")
    private fun staff() = compose.onNodeWithContentDescription("Your tune, 2 notes")
    private fun bounds(node: SemanticsNodeInteraction) = node.getUnclippedBoundsInRoot()

    /** `TabletHost`'s forced size rounds the density down, which leaves sizes a dp or two off the ones asked for. */
    private fun assertNear(what: String, expected: Dp, actual: Dp) =
        assertTrue("$size: $what: expected about $expected, got $actual", abs((expected - actual).value) <= TOLERANCE)

    /** Since 2026-11-16 (sharps and flats): spare height shows more rows before it grows keys, and upright the five do not fit. */
    @Test
    fun upright_keys_are_58_tall_on_both_tablets() {
        show(TabletSize.TEN_UPRIGHT)
        for (each in listOf(TabletSize.TEN_UPRIGHT, TabletSize.EIGHT_UPRIGHT)) {
            on(each)
            val c4 = bounds(key("C4"))
            val cs4 = bounds(key("C♯4"))
            assertNear1("C4's height", 58.dp, c4.bottom - c4.top)
            assertNear1("C♯4's height", 48.dp, cs4.bottom - cs4.top)
        }
    }

    /** On its side a tablet fits all five rows, and the keys grow together to fill the height, up to 88 dp. */
    @Test
    fun sideways_all_five_rows_fit_and_the_keys_grow() {
        show(TabletSize.TEN_SIDEWAYS)
        for (each in listOf(TabletSize.TEN_SIDEWAYS, TabletSize.EIGHT_SIDEWAYS)) {
            on(each)
            val keys = bounds(key("Flute keys"))
            val rows = bounds(compose.onNodeWithTag(KEY_ROWS_TAG))
            for (name in listOf("C♯5", "B♭5", "C5", "B5", "C♯4", "C4")) {
                val b = bounds(key(name))
                assertTrue("$each: $name $b should be whole inside the rows $rows", b.top >= rows.top - 1.dp && b.bottom + 5.dp <= rows.bottom + 1.dp)
            }
            val white = KeysGeometry.whiteHeight((keys.bottom - keys.top).value, KeysGeometry.SIDEWAYS_MAX)
            assertTrue("$each: the keys should grow past 58, are $white", white > 70f)
            val c4 = bounds(key("C4"))
            assertNear1("C4's height", white.dp, c4.bottom - c4.top)
        }
    }

    private fun assertNear1(what: String, expected: Dp, actual: Dp) =
        assertTrue("$size: $what: expected about $expected, got $actual", abs((expected - actual).value) <= 1f)

    @Test
    fun upright_nothing_scrolls_and_play_ends_inside_the_room() {
        show(TabletSize.TEN_UPRIGHT)
        for (each in listOf(TabletSize.TEN_UPRIGHT, TabletSize.EIGHT_UPRIGHT)) {
            on(each)
            val play = bounds(compose.onNodeWithText("Play"))
            assertTrue("$each: Play ends at ${play.bottom}", play.bottom <= each.logicalHeight.dp)
        }
    }

    @Test
    fun sideways_the_keys_are_336_wide() {
        show(TabletSize.TEN_SIDEWAYS)
        for (each in listOf(TabletSize.TEN_SIDEWAYS, TabletSize.EIGHT_SIDEWAYS)) {
            on(each)
            assertNear("the keys' width", TabletGeometry.sidewaysKeys(each.zoom).dp, bounds(key("B4")).right - bounds(key("C4")).left)
        }
    }

    @Test
    fun sideways_the_staff_rests_at_0_91() {
        show(TabletSize.TEN_SIDEWAYS)
        val below = bounds(book()).top - bounds(staff()).top
        assertNear("the book under the staff's top", StaffGeometry.bookButtonTop(TabletGeometry.sidewaysStaffScale(size.zoom)).dp, below)
    }

    @Test
    fun a_running_staff_keeps_its_scale() {
        show(TabletSize.TEN_SIDEWAYS, FluteUiState(tune = twoNotes, playing = Playing(0)))
        val below = bounds(book()).top - bounds(staff()).top
        assertNear("the book under the staff's top", StaffGeometry.bookButtonTop(4f / 3f).dp, below)
    }

    private companion object {
        const val TOLERANCE = 3f
    }

    @Test
    fun the_level_pair_shows_on_a_tablet() {
        show(TabletSize.TEN_UPRIGHT, FluteUiState(tune = twoNotes, listening = Listening(index = 0)))
        for (each in TabletSize.entries) {
            on(each)
            val group = bounds(compose.onNodeWithContentDescription("Level").assertIsDisplayed())
            val stop = bounds(compose.onNodeWithText("Stop"))
            assertTrue("$each: the pair ends at ${group.bottom}, Stop starts at ${stop.top}", group.bottom <= stop.top)
            for (word in listOf("Practice", "Exam")) assertWhole("$each", compose.onNodeWithText(word, useUnmergedTree = true))
        }
    }
}
