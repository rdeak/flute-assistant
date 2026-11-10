// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.tuner

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import hr.bertlab.fluteassistant.domain.listen.Hearing
import hr.bertlab.fluteassistant.domain.tuner.FluteRange
import hr.bertlab.fluteassistant.domain.tuner.NoteMath
import hr.bertlab.fluteassistant.domain.tuner.TunerMode
import hr.bertlab.fluteassistant.ui.common.LocaleProvider
import hr.bertlab.fluteassistant.ui.common.MicrophonePermissionCard
import hr.bertlab.fluteassistant.ui.common.SidewaysGeometry
import hr.bertlab.fluteassistant.ui.common.SidewaysHost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import kotlin.math.abs

/**
 * The tuner and the microphone card on a phone on its side: the pitch window grown to the card's
 * height between its arrows, the words and the fingering beside it (under it in a window under 750 dp
 * wide, the 640 wide phone), and the buttons in the right column. Upright is `TunerScreenTest`'s. The sizes are
 * compared with the room rather than asked of `assertIsDisplayed`, as `SidewaysHost` forces its
 * size through the density and what spills past it is still on the emulator's screen.
 */
@RunWith(AndroidJUnit4::class)
class TunerSidewaysTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var state: MutableState<TunerUiState>
    private var phone by mutableStateOf(Phone(915, 411))
    private val notes = mutableListOf<Int>()
    private val modes = mutableListOf<TunerMode>()
    private var hears = 0
    private var requests = 0
    private var settings = 0
    private var dismissals = 0

    private class Phone(val width: Int, val height: Int) {
        val contentWidth: Float get() = width - SidewaysGeometry.railWidth(height.toFloat())
        val cardWidth: Float get() = SidewaysGeometry.cardWidth(contentWidth, height.toFloat())
        val column: Float get() = SidewaysGeometry.columnWidth(height.toFloat())
        val modeHeight: Float get() = if (height < 400) 80f else 92f
        override fun toString() = "$width x $height"
    }

    private fun show(
        initial: TunerUiState,
        at: Phone = Phone(915, 411),
        locale: Locale = Locale.ENGLISH,
        fontScale: Float = 1f,
    ) {
        phone = at
        state = mutableStateOf(initial)
        compose.setContent {
            LocaleProvider(locale) {
                SidewaysHost(width = phone.width, height = phone.height, fontScale = fontScale) {
                    TunerContent(
                        state = state.value,
                        onTargetNoteChange = { notes += it },
                        onModeChange = { modes += it },
                        onPlayNote = { hears++ },
                        onRequestPermission = { requests++ },
                        onOpenSettings = { settings++ },
                    )
                }
            }
        }
    }

    /** The card the flute's Your turn shares, with its way back when [dismissible], filling the tab as the flute gives it. */
    private fun showCard(
        dismissible: Boolean,
        at: Phone = Phone(915, 411),
        locale: Locale = Locale.ENGLISH,
        fontScale: Float = 1f,
    ) {
        phone = at
        compose.setContent {
            LocaleProvider(locale) {
                SidewaysHost(width = phone.width, height = phone.height, fontScale = fontScale) {
                    MicrophonePermissionCard(
                        onRequestPermission = { requests++ },
                        onOpenSettings = { settings++ },
                        modifier = Modifier.fillMaxSize(),
                        onDismiss = if (dismissible) ({ dismissals++ }) else null,
                    )
                }
            }
        }
    }

    /** [TunerScreenTest]'s `listening`: [frequency] heard (null for silence), [targetMidi] chosen. */
    private fun listening(
        frequency: Double?,
        targetMidi: Int = FluteRange.DEFAULT_TARGET,
        mode: TunerMode = TunerMode.TARGET,
    ) = TunerUiState(
        permissionGranted = true,
        isListening = true,
        reading = frequency?.let { NoteMath.frequencyToNote(it) },
        targetMidi = targetMidi,
        mode = mode,
    )

    private fun on(each: Phone) {
        compose.runOnIdle { phone = each }
        compose.waitForIdle()
    }

    private fun window() = compose.onNodeWithTag("pitch_window")
    private fun fingering() = compose.onNodeWithTag("fingering")
    private fun word(text: String) = compose.onNodeWithText(text)
    private fun lower() = compose.onNodeWithContentDescription("Lower note")
    private fun higher() = compose.onNodeWithContentDescription("Higher note")

    private fun bounds(node: SemanticsNodeInteraction) = node.getUnclippedBoundsInRoot()

    private fun width(node: SemanticsNodeInteraction) = bounds(node).let { it.right - it.left }

    private fun height(node: SemanticsNodeInteraction) = bounds(node).let { it.bottom - it.top }

    /** The forced size rounds the density down, which leaves sizes a dp or two off the ones asked for. */
    private fun assertNear(what: String, expected: Dp, actual: Dp, tolerance: Float = TOLERANCE) =
        assertTrue("$what: expected about $expected, got $actual", abs((expected - actual).value) <= tolerance)

    /** A size that follows the width grows or shrinks with the forced density's rounding, so its tolerance is a share of the size. */
    private fun assertNearBySize(what: String, expected: Dp, actual: Dp) =
        assertNear(what, expected, actual, tolerance = TOLERANCE + expected.value * RELATIVE_TOLERANCE)

    /** [node] and the [shadow] under it are within the window, which is the phone less the rail. */
    private fun assertInside(phone: Phone, what: String, node: SemanticsNodeInteraction, shadow: Dp = 0.dp) {
        val b = bounds(node)
        val right = phone.contentWidth.dp + SLACK
        val bottom = phone.height.dp + SLACK
        assertTrue("$phone: $what starts at ${b.left} x ${b.top}", b.left >= -SLACK && b.top >= -SLACK)
        assertTrue("$phone: $what ends at ${b.right} x ${b.bottom + shadow}, the room is $right x $bottom", b.right <= right && b.bottom + shadow <= bottom)
    }

    private fun layoutOf(text: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }

    /** Whole, and broken only between words: a word too wide for the line would be split inside it. */
    private fun assertWholeWords(phone: Phone, text: String) {
        val layout = layoutOf(text)
        assertFalse("$phone: \"$text\" is cut short", layout.hasVisualOverflow)
        (0 until layout.lineCount - 1).forEach { line ->
            val end = layout.getLineEnd(line)
            assertTrue("$phone: \"$text\" is split inside a word at $end", text[end - 1].isWhitespace())
        }
    }

    /** What the words may use: the card ends where the column begins, less the margin between them. */
    private fun assertBesideTheColumn(phone: Phone, what: String, node: SemanticsNodeInteraction) {
        val cardRight = (8f + phone.cardWidth).dp + SLACK
        assertTrue("$phone: $what ends at ${bounds(node).right}, the card at $cardRight", bounds(node).right <= cardRight)
    }

    @Test
    fun which_mode_has_no_arrows_and_no_hear() {
        show(listening(null, mode = TunerMode.IDENTIFY))
        window().assertIsDisplayed()
        lower().assertDoesNotExist()
        higher().assertDoesNotExist()
        word("Hear").assertDoesNotExist()
        word("Which?").assertIsDisplayed().assertIsSelected()
        word("Tune").assertIsDisplayed()
    }

    @Test
    fun tune_mode_has_arrows_and_hear() {
        show(listening(null))
        window().assertIsDisplayed()
        lower().assertIsDisplayed()
        higher().assertIsDisplayed()
        word("Hear").assertIsDisplayed()
        word("Tune").assertIsDisplayed().assertIsSelected()
        word("Which?").assertIsDisplayed()
        // The arrows are the window's neighbours, 52 dp round, one either side.
        assertNear("the minus", 52.dp, width(lower()))
        assertTrue("the arrows stand either side of the window", bounds(lower()).right <= bounds(window()).left && bounds(window()).right <= bounds(higher()).left)
    }

    @Test
    fun the_ring_stays_put_when_the_mode_changes() {
        show(listening(null, mode = TunerMode.IDENTIFY))
        PHONES.forEach { each ->
            on(each)
            compose.runOnIdle { state.value = state.value.copy(mode = TunerMode.IDENTIFY) }
            compose.waitForIdle()
            val which = bounds(window())
            compose.runOnIdle { state.value = state.value.copy(mode = TunerMode.TARGET) }
            compose.waitForIdle()
            assertEquals("$each: the window moved when Tune came", which, bounds(window()))
            compose.runOnIdle { state.value = state.value.copy(mode = TunerMode.IDENTIFY) }
            compose.waitForIdle()
            assertEquals("$each: the window moved when Which? came back", which, bounds(window()))
        }
    }

    @Test
    fun the_window_grows_with_the_height() {
        show(listening(null), Phone(915, 411))
        val tall = bounds(window())
        assertTrue("the window is ${tall.bottom - tall.top} tall, more than the 300 upright draws", tall.bottom - tall.top > 300.dp)
        assertNear("the window's height", 357.dp, tall.bottom - tall.top, tolerance = 3f)
        assertNear("the window's width", 157.dp, tall.right - tall.left, tolerance = 3f)
    }

    @Test
    fun the_fingering_sits_beside_the_window() {
        show(listening(440.0), Phone(915, 411))
        val window = bounds(window())
        val fingering = bounds(fingering())
        assertTrue("the fingering starts at ${fingering.left}, the window ends at ${window.right}", fingering.left > window.right)
        assertTrue("the fingering is ${fingering.right - fingering.left} wide, under the 205 it needs", fingering.right - fingering.left >= 205.dp)
    }

    /**
     * The decision to put the fingering beside the window counts the same arrows and gaps the layout
     * draws, so what the geometry says is left for it is what it gets: 324 dp at 915 x 411.
     */
    @Test
    fun the_fingering_is_as_wide_as_the_geometry_leaves_it() {
        show(listening(440.0))
        listOf(Phone(915, 411), Phone(800, 360), Phone(780, 360), Phone(750, 360)).forEach { each ->
            on(each)
            val room = SidewaysGeometry.fingeringRoom(each.cardWidth, each.height.toFloat())
            // The card's real inset is 6 dp under the CARD_CHROME the geometry counts, and that spare falls to the fingering.
            assertNearBySize("$each: the fingering", (room + 6f).dp, width(fingering()))
        }
        on(Phone(915, 411))
        assertNearBySize("the fingering at 915 x 411", 324.dp, width(fingering()))
    }

    /**
     * Upright's `ButtonGap` of 20 beside the window, so the sound bubble's arrow, which reaches
     * past the window, does not run into the › button just outside Perfect; the narrowest phone
     * draws the buttons and the gap smaller.
     */
    @Test
    fun the_arrows_stand_20_dp_from_the_window_and_12_on_the_narrowest_phone() {
        show(listening(null))
        listOf(Phone(915, 411), Phone(800, 360), Phone(780, 360), Phone(750, 360)).forEach { each ->
            on(each)
            assertNear("$each: the gap before the window", 20.dp, bounds(window()).left - bounds(lower()).right)
            assertNear("$each: the gap after the window", 20.dp, bounds(higher()).left - bounds(window()).right)
        }
        on(Phone(640, 360))
        assertNear("the narrow gap before the window", 12.dp, bounds(window()).left - bounds(lower()).right)
        assertNear("the narrow gap after the window", 12.dp, bounds(higher()).left - bounds(window()).right)
    }

    @Test
    fun the_fingering_stays_beside_the_window_at_800_by_360() {
        show(listening(440.0), Phone(800, 360))
        val window = bounds(window())
        val fingering = bounds(fingering())
        assertTrue("the fingering starts at ${fingering.left}, the window ends at ${window.right}", fingering.left > window.right)
    }

    /**
     * An error comes when the microphone will not open, so nothing is heard and there is no advice:
     * the line takes the advice's box. Nothing around it moves, and it stays in that box at any text
     * size, on every phone.
     */
    private fun assertAnErrorMovesNothing(scale: Float, message: String = "I can't listen right now.", locale: Locale = Locale.ENGLISH) {
        show(listening(null), fontScale = scale, locale = locale)
        PHONES.forEach { each ->
            on(each)
            compose.runOnIdle { state.value = listening(null) }
            compose.waitForIdle()
            val before = bounds(fingering())
            val ring = bounds(window())
            compose.runOnIdle { state.value = state.value.copy(isListening = false, micFailed = true) }
            compose.waitForIdle()
            assertEquals("$each at $scale x: the fingering moved", before, bounds(fingering()))
            assertEquals("$each at $scale x: the window moved", ring, bounds(window()))
            val box = bounds(word(message))
            assertInside(each, "the message", word(message))
            assertTrue("$each at $scale x: the message ends at ${box.bottom}, the fingering starts at ${before.top}", box.bottom <= before.top + SLACK)
            val layout = layoutOf(message)
            assertTrue("$each at $scale x: the message is cut short", !layout.hasVisualOverflow)
            assertEquals("$each at $scale x: the message lost its end", message.length, layout.getLineEnd(layout.lineCount - 1))
        }
    }

    @Test
    fun an_error_moves_nothing_and_stays_in_its_box() = assertAnErrorMovesNothing(1f)

    @Test
    fun an_error_moves_nothing_at_the_largest_text() = assertAnErrorMovesNothing(2f)

    @Test
    fun an_error_moves_nothing_in_croatian_at_the_largest_text() =
        assertAnErrorMovesNothing(2f, message = "Sada ne mogu slušati.", locale = Locale("hr"))

    /**
     * A camera hole takes width from the tab, so an 800 wide phone may leave 780 or 750: they keep the
     * full-size ring (the window grown to the card's height) with the fingering beside it, as 800 does.
     * Only a window under 750 puts the fingering under the window and shrinks the ring.
     */
    @Test
    fun the_ring_keeps_its_full_size_down_to_750_wide() {
        show(listening(440.0))
        listOf(Phone(800, 360), Phone(780, 360), Phone(750, 360)).forEach { each ->
            on(each)
            val full = 300f * SidewaysGeometry.pitchWindowScale(each.height.toFloat())
            assertNearBySize("$each: the window's height", full.dp, height(window()))
            assertTrue("$each: the fingering starts at ${bounds(fingering()).left}, the window ends at ${bounds(window()).right}", bounds(fingering()).left > bounds(window()).right)
            assertNear("$each: the arrows", 52.dp, width(lower()))
        }
        // The edge is 749.64, so 740 is clear of it however the forced density rounds.
        on(Phone(740, 360))
        assertTrue("740: the fingering is under the window", bounds(fingering()).top >= bounds(window()).bottom)
        assertTrue("740: the window is smaller, ${height(window())} tall", height(window()) < 260.dp)
    }

    @Test
    fun the_fingering_goes_under_the_window_on_the_narrowest_phone() {
        show(listening(440.0), Phone(640, 360))
        val window = bounds(window())
        val fingering = bounds(fingering())
        assertTrue("the fingering starts at ${fingering.top}, the window ends at ${window.bottom}", fingering.top >= window.bottom)
        // Across the card: wider than it could have been beside the window.
        assertTrue("the fingering is ${width(fingering())} wide", width(fingering()) > 320.dp)
    }

    @Test
    fun every_control_is_on_screen_on_every_phone() {
        show(listening(440.0))
        PHONES.forEach { each ->
            listOf(TunerMode.IDENTIFY, TunerMode.TARGET).forEach { mode ->
                on(each)
                compose.runOnIdle { state.value = listening(440.0, mode = mode) }
                compose.waitForIdle()
                val label = "$each in $mode"
                assertInside(each, "$label: the window", window().assertIsDisplayed())
                assertInside(each, "$label: the fingering", fingering().assertIsDisplayed())
                assertInside(each, "$label: Which?", word("Which?").assertIsDisplayed(), shadow = 5.dp)
                assertInside(each, "$label: Tune", word("Tune").assertIsDisplayed(), shadow = 5.dp)
                // Which? over Tune at the bottom of the column, as wide as the column, 92 tall or 80 when short.
                val which = bounds(word("Which?"))
                val tune = bounds(word("Tune"))
                assertNear("$label: Which?'s width", each.column.dp, which.right - which.left)
                assertNear("$label: Which?'s height", each.modeHeight.dp, which.bottom - which.top)
                assertNear("$label: Tune's height", each.modeHeight.dp, tune.bottom - tune.top)
                assertTrue("$label: Which? ends at ${which.bottom}, Tune starts at ${tune.top}", which.bottom <= tune.top)
                assertNear("$label: Tune's bottom", each.height.dp - 16.dp, tune.bottom)
                assertNear("$label: Tune's right edge", each.contentWidth.dp - 8.dp, tune.right)
                assertTrue("$label: the window ends at ${bounds(window()).right}, the column starts at ${which.left}", bounds(window()).right < which.left)
                if (mode == TunerMode.TARGET) {
                    listOf("Lower note", "Higher note").forEach { name ->
                        assertInside(each, "$label: $name", compose.onNodeWithContentDescription(name).assertIsDisplayed(), shadow = 4.dp)
                    }
                    val hear = bounds(word("Hear").assertIsDisplayed())
                    assertInside(each, "$label: Hear", word("Hear"), shadow = 5.dp)
                    assertNear("$label: Hear's width", each.column.dp, hear.right - hear.left)
                    assertTrue("$label: Hear ends at ${hear.bottom}, Which? starts at ${which.top}", hear.bottom <= which.top)
                    assertNear("$label: Hear is at the top of the column", 14.dp, hear.top)
                }
            }
        }
    }

    /** Which? and Tune share the size the phone's height gives them: 92 at 411, 80 on a short phone. */
    @Test
    fun the_arrows_shrink_on_the_narrowest_phone() {
        show(listening(null), Phone(915, 411))
        assertNear("the arrows beside the window", 52.dp, width(lower()))
        on(Phone(640, 360))
        assertNear("the arrows under it", 44.dp, width(lower()))
        assertNear("the other arrow", 44.dp, width(higher()))
    }

    @Test
    fun the_buttons_do_what_they_say() {
        show(listening(null, targetMidi = 70))
        lower().performClick()
        higher().performClick()
        assertEquals(listOf(69, 71), notes)
        word("Hear").performClick()
        assertEquals(1, hears)
        word("Which?").performClick()
        assertEquals(listOf(TunerMode.IDENTIFY), modes)
    }

    @Test
    fun hear_waits_while_the_note_plays() {
        show(listening(null).copy(isPlayingReference = true))
        word("Hear").assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(isPlayingReference = false) }
        compose.waitForIdle()
        word("Hear").assertIsEnabled()
    }

    @Test
    fun the_modes_are_one_group_for_a_screen_reader() {
        show(listening(null))
        // The group is the pair of buttons in the right column, right of the card.
        val group = bounds(compose.onNodeWithContentDescription("Tuner mode").assertIsDisplayed())
        val which = bounds(word("Which?"))
        val tune = bounds(word("Tune"))
        assertTrue("the group $group holds Which? $which and Tune $tune", group.left <= which.left && group.right >= which.right && group.top <= which.top && group.bottom >= tune.bottom)
        assertTrue("the group starts at ${group.left}, the card ends at ${(8f + Phone(915, 411).cardWidth).dp}", group.left >= (8f + Phone(915, 411).cardWidth).dp)
    }

    @Test
    fun the_arrows_fade_at_the_ends_of_the_flute() {
        show(listening(null, targetMidi = FluteRange.LOWEST_MIDI))
        lower().assertIsNotEnabled()
        higher().assertIsEnabled()
    }

    /** The mockup draws the words 40 and 22 high and the status line's first line at the top of its 48 dp. */
    @Test
    fun the_words_stand_to_the_right_of_the_window() {
        show(listening(430.0), Phone(915, 411))
        val window = bounds(window())
        val status = bounds(word("⬇️ Too low"))
        val advice = bounds(word("🔄 Roll the flute out a little"))
        assertTrue("the status starts at ${status.left}, the window ends at ${window.right}", status.left >= window.right)
        assertTrue("the advice is under the status", advice.top >= status.bottom - SLACK)
        assertTrue("the fingering is under the advice", bounds(fingering()).top >= advice.bottom - SLACK)
    }

    /** The longest words in each language: the octave and heard-note lines and the longest advice. */
    private class Case(val state: TunerUiState, val english: List<String>, val croatian: List<String>)

    private val longest = listOf(
        Case(listening(880.0), listOf("⬆️ An octave too high", "🍃 Gentler air"), listOf("⬆️ Oktavu previsoko", "🍃 Nježniji zrak")),
        Case(listening(261.63, targetMidi = 72), listOf("⬇️ An octave too low", "💨 Faster air"), listOf("⬇️ Oktavu prenisko", "💨 Brži zrak")),
        Case(listening(329.63), listOf("⬇️ That's E4", "👀 Check your fingers"), listOf("⬇️ To je e1", "👀 Provjeri prste")),
        Case(listening(440.0, targetMidi = 67), listOf("⬆️ That's A4", "👀 Check your fingers"), listOf("⬆️ To je a1", "👀 Provjeri prste")),
        Case(listening(430.0), listOf("⬇️ Too low", "🔄 Roll the flute out a little"), listOf("⬇️ Prenisko", "🔄 Zakreni flautu malo od sebe")),
        Case(listening(443.0), listOf("⬆️ Too high", "🔄 Roll the flute in a little"), listOf("⬆️ Previsoko", "🔄 Zakreni flautu malo k sebi")),
        Case(
            listening(null).copy(hearing = Hearing.LOUD_UNCLEAR),
            listOf("👂 Play A4", "🎵 Play one long, clear note"),
            listOf("👂 Odsviraj a1", "🎵 Odsviraj jedan dug, čist ton"),
        ),
    )

    /** Every line whole, only between words, and out of the right column, on each phone. */
    private fun assertTheWordsFit(locale: Locale, fontScale: Float) {
        show(longest.first().state, locale = locale, fontScale = fontScale)
        PHONES.forEach { each ->
            on(each)
            longest.forEach { case ->
                compose.runOnIdle { state.value = case.state }
                compose.waitForIdle()
                (if (locale.language == "hr") case.croatian else case.english).forEach { line ->
                    val node = word(line).assertIsDisplayed()
                    assertWholeWords(each, line)
                    assertInside(each, "\"$line\"", node)
                    assertBesideTheColumn(each, "\"$line\"", node)
                    assertTrue("$each: \"$line\" runs over the window", bounds(node).left >= bounds(window()).left)
                }
                assertInside(each, "the fingering", fingering().assertIsDisplayed())
            }
        }
    }

    @Test
    fun the_longest_english_words_fit() = assertTheWordsFit(Locale.ENGLISH, 1f)

    @Test
    fun the_longest_croatian_words_fit() = assertTheWordsFit(Locale("hr"), 1f)

    @Test
    fun the_longest_croatian_words_fit_at_large_text() = assertTheWordsFit(Locale("hr"), 1.5f)

    @Test
    fun the_longest_english_words_fit_at_large_text() = assertTheWordsFit(Locale.ENGLISH, 1.5f)

    @Test
    fun the_modes_fit_in_croatian_at_large_text() {
        show(listening(null), locale = Locale("hr"), fontScale = 1.5f)
        PHONES.forEach { each ->
            on(each)
            listOf("Koji?", "Ugodi", "Slušaj").forEach { name ->
                assertInside(each, name, word(name).assertIsDisplayed(), shadow = 5.dp)
                assertWholeWords(each, name)
            }
        }
    }

    // ---- Asking for the microphone ----

    @Test
    fun the_microphone_card_fits() {
        show(TunerUiState(permissionGranted = false))
        PHONES.forEach { each ->
            on(each)
            listOf("Can I listen?", "Yes", "Settings").forEach { text ->
                assertInside(each, text, word(text).assertIsDisplayed(), shadow = 8.dp)
            }
            word("Not now").assertDoesNotExist()
            word("Tune").assertDoesNotExist()
        }
    }

    @Test
    fun the_microphone_card_with_a_way_back_fits() {
        showCard(dismissible = true)
        PHONES.forEach { each ->
            on(each)
            listOf("Can I listen?", "Yes", "Settings", "Not now").forEach { text ->
                assertInside(each, text, word(text).assertIsDisplayed(), shadow = 8.dp)
            }
        }
    }

    /** Settings and Not now at the top of the column, Yes low on the right where Play always is. */
    @Test
    fun the_microphone_card_puts_yes_low_and_the_quiet_buttons_high() {
        showCard(dismissible = true)
        PHONES.forEach { each ->
            on(each)
            val settingsBox = bounds(word("Settings"))
            val notNow = bounds(word("Not now"))
            val yes = bounds(word("Yes"))
            assertNear("$each: Yes's width", each.column.dp, yes.right - yes.left)
            assertNear("$each: Yes's height", 104.dp, yes.bottom - yes.top)
            assertNear("$each: Yes's bottom", each.height.dp - 16.dp, yes.bottom)
            assertNear("$each: Settings is at the top of the column", 14.dp, settingsBox.top)
            assertTrue("$each: Not now (${notNow.top}) is under Settings (${settingsBox.bottom})", notNow.top >= settingsBox.bottom)
            assertTrue("$each: Not now ends at ${notNow.bottom}, Yes starts at ${yes.top}", notNow.bottom <= yes.top)
            assertNear("$each: Not now's width", each.column.dp, notNow.right - notNow.left)
            assertNear("$each: Settings starts where Yes does", yes.left, settingsBox.left)
        }
    }

    @Test
    fun the_microphone_card_without_a_way_back_has_one_quiet_button() {
        showCard(dismissible = false)
        word("Not now").assertDoesNotExist()
        val settingsBox = bounds(word("Settings").assertIsDisplayed())
        assertNear("Settings is at the top of the column", 14.dp, settingsBox.top)
        word("Settings").performClick()
        word("Yes").performClick()
        assertEquals(1, settings)
        assertEquals(1, requests)
    }

    @Test
    fun the_microphone_cards_way_back_goes_back() {
        showCard(dismissible = true)
        word("Not now").performClick()
        assertEquals(1, dismissals)
    }

    /** The disc and the words beside it, left to right, inside the card and not under the column. */
    @Test
    fun the_microphone_words_stay_in_the_card() {
        showCard(dismissible = true)
        PHONES.forEach { each ->
            on(each)
            listOf("Can I listen?", "I only listen. Nothing is saved.").forEach { line ->
                val node = word(line).assertIsDisplayed()
                assertWholeWords(each, line)
                assertInside(each, "\"$line\"", node)
                assertBesideTheColumn(each, "\"$line\"", node)
            }
            // Inside the card, which starts at the page's 8 dp margin and pads its content 16 more, and in its left half.
            val title = bounds(word("Can I listen?")).left
            assertTrue("$each: the title starts at $title, inside the card's padding", title >= 8.dp + 16.dp - SLACK)
            assertTrue("$each: the title starts at $title, in the card's left half", title < each.cardWidth.dp / 2 + 8.dp)
        }
    }

    @Test
    fun the_microphone_card_fits_in_croatian_at_large_text() {
        showCard(dismissible = true, locale = Locale("hr"), fontScale = 1.5f)
        PHONES.forEach { each ->
            on(each)
            listOf("Smijem li slušati?", "Samo slušam. Ništa se ne sprema.").forEach { line ->
                val node = word(line).assertIsDisplayed()
                assertWholeWords(each, line)
                assertInside(each, "\"$line\"", node)
                assertBesideTheColumn(each, "\"$line\"", node)
            }
            listOf("Da", "Postavke", "Ne sad").forEach { name ->
                assertInside(each, name, word(name).assertIsDisplayed(), shadow = 8.dp)
                assertWholeWords(each, name)
            }
        }
    }

    @Test
    fun the_microphone_card_fits_in_english_at_large_text() {
        showCard(dismissible = true, fontScale = 1.5f)
        PHONES.forEach { each ->
            on(each)
            listOf("Can I listen?", "I only listen. Nothing is saved.").forEach { line ->
                val node = word(line).assertIsDisplayed()
                assertWholeWords(each, line)
                assertInside(each, "\"$line\"", node)
                assertBesideTheColumn(each, "\"$line\"", node)
            }
            listOf("Yes", "Settings", "Not now").forEach { name ->
                assertInside(each, name, word(name).assertIsDisplayed(), shadow = 8.dp)
                assertWholeWords(each, name)
            }
        }
    }

    private companion object {
        const val TOLERANCE = 2f
        const val RELATIVE_TOLERANCE = 0.02f

        /**
         * The three phones the layout was drawn for, and two a camera hole makes of the 800 wide one: 780 is
         * a phone of that width, and 750 is the least width that keeps the full-size ring at 360 tall.
         */
        val PHONES = listOf(Phone(915, 411), Phone(800, 360), Phone(780, 360), Phone(750, 360), Phone(640, 360))

        /** Rounding of the forced density, in dp. */
        val SLACK = 1.dp
    }
}
