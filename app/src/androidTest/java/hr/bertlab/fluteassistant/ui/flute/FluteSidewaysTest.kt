// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.flute

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import hr.bertlab.fluteassistant.domain.flute.Book
import hr.bertlab.fluteassistant.domain.flute.NoteLength
import hr.bertlab.fluteassistant.domain.flute.TuneNote
import hr.bertlab.fluteassistant.domain.model.MetronomeConfig
import hr.bertlab.fluteassistant.domain.model.TimeSignature
import hr.bertlab.fluteassistant.ui.common.LocaleProvider
import hr.bertlab.fluteassistant.ui.common.SidewaysGeometry
import hr.bertlab.fluteassistant.ui.common.SidewaysHost
import hr.bertlab.fluteassistant.ui.metronome.MetronomeUiState
import hr.bertlab.fluteassistant.ui.tuner.Hearing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import kotlin.math.abs

/**
 * The flute on a phone on its side: writing with the keys beside the staff, a tune running with the
 * staff at stand size and Stop low on the right, and the speed panel. Upright is `FluteScreenTest`'s.
 * The sizes are compared with the room rather than asked of `assertIsDisplayed`, as `SidewaysHost`
 * forces its size through the density and what spills past it is still on the emulator's screen.
 */
@RunWith(AndroidJUnit4::class)
class FluteSidewaysTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var state: MutableState<FluteUiState>
    private lateinit var metronome: MutableState<MetronomeUiState>
    private var phone by mutableStateOf(Phone(915, 411))
    private var sideways by mutableStateOf(true)
    private val pressed = mutableListOf<Int>()
    private val released = mutableListOf<Int>()
    private val restEvents = mutableListOf<String>()
    private var playToggles = 0
    private var yourTurns = 0
    private var stops = 0
    private var micDismissals = 0
    private var speedOpens = 0
    private var speedDones = 0
    private var chosenSignature: TimeSignature? = null
    private val chosenBooks = mutableListOf<Int>()

    private class Phone(val width: Int, val height: Int) {
        val contentWidth: Float get() = width - SidewaysGeometry.railWidth(height.toFloat())
        val keysWidth: Float get() = SidewaysGeometry.keysWidth(contentWidth)
        val keyGap: Float get() = SidewaysGeometry.keyGap(contentWidth)
        val keyHeight: Float get() = SidewaysGeometry.keyHeight(height.toFloat())
        val keyWidth: Float get() = (keysWidth - 6 * keyGap) / 7
        val column: Float get() = SidewaysGeometry.columnWidth(height.toFloat())
        val cardWidth: Float get() = SidewaysGeometry.cardWidth(contentWidth, height.toFloat())
        override fun toString() = "$width x $height"
    }

    /** [FluteScreenTest]'s `show`, inside a phone on its side. */
    private fun show(initial: FluteUiState, locale: Locale = Locale.ENGLISH, fontScale: Float = 1f) {
        state = mutableStateOf(initial)
        metronome = mutableStateOf(MetronomeUiState(config = MetronomeConfig(bpm = 120)))
        compose.setContent {
            LocaleProvider(locale) {
                SidewaysHost(width = phone.width, height = phone.height, fontScale = fontScale, sideways = sideways) {
                    FluteContent(
                        state = state.value,
                        metronome = metronome.value,
                        onKeyPressed = { pressed += it },
                        onKeyReleased = { released += it },
                        onKeyActivated = {},
                        onRestPressed = { restEvents += "press" },
                        onRestReleased = { restEvents += "release" },
                        onRestActivated = {},
                        onBreath = {},
                        onTogglePlayback = { playToggles++ },
                        onEraseLast = {},
                        onBpmChange = { bpm -> metronome.value = metronome.value.copy(config = metronome.value.config.withBpm(bpm)) },
                        onTimeSignatureChange = { chosenSignature = it },
                        onSpeedOpen = { speedOpens++ },
                        onSpeedDone = { speedDones++ },
                        onShelfOpen = {},
                        onBookChosen = { chosenBooks += it },
                        onYourTurn = { yourTurns++ },
                        onStop = { stops++ },
                        onRequestMic = {},
                        onOpenSettings = {},
                        onMicDismiss = { micDismissals++ },
                    )
                }
            }
        }
    }

    private fun on(each: Phone) {
        compose.runOnIdle { phone = each }
        compose.waitForIdle()
    }

    /** The whole turn, the shape with the flag, as `MainScreen` provides them from the window. */
    private fun turnUpright() {
        compose.runOnIdle {
            phone = UPRIGHT
            sideways = false
        }
        compose.waitForIdle()
    }

    private fun turnSideways() {
        compose.runOnIdle {
            phone = Phone(915, 411)
            sideways = true
        }
        compose.waitForIdle()
    }

    /** `LocalSideways` alone, at the same size, as a test of what leaves the screen needs. */
    private fun flip() {
        compose.runOnIdle { sideways = !sideways }
        compose.waitForIdle()
    }

    private fun key(name: String) = compose.onNodeWithContentDescription(name)
    private fun word(text: String) = compose.onNodeWithText(text)
    private fun knob() = compose.onNodeWithContentDescription("Speed: Rabbit, tap to change")
    private fun book() = compose.onNodeWithContentDescription("Tune: Lollipop, tap to change")
    private fun ring() = compose.onNodeWithContentDescription(RING)
    private fun staff() = compose.onNodeWithContentDescription("Your tune, 2 notes")

    private fun bounds(node: SemanticsNodeInteraction) = node.getUnclippedBoundsInRoot()

    private fun width(node: SemanticsNodeInteraction) = bounds(node).let { it.right - it.left }

    private fun height(node: SemanticsNodeInteraction) = bounds(node).let { it.bottom - it.top }

    /** The forced size rounds the density down, which leaves sizes a dp or two off the ones asked for. */
    private fun assertNear(what: String, expected: Dp, actual: Dp, tolerance: Float = TOLERANCE) =
        assertTrue("$what: expected about $expected, got $actual", abs((expected - actual).value) <= tolerance)

    /** A size that follows the width grows or shrinks with the same rounding, so its tolerance is a share of the size. */
    private fun assertNearBySize(what: String, expected: Dp, actual: Dp) =
        assertNear(what, expected, actual, tolerance = TOLERANCE + expected.value * RELATIVE_TOLERANCE)

    /** [node] and the [shadow] under it are within the window, which is the phone less the rail. */
    private fun assertInside(what: String, node: SemanticsNodeInteraction, shadow: Dp = 0.dp) {
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

    /** Text cut short still reads as displayed, so this asks the text's own layout. */
    private fun assertWhole(text: String) = assertFalse("$phone: \"$text\" is cut short", layoutOf(text).hasVisualOverflow)

    /** Whole, and broken only between words: a word too wide for the line would be split inside it. */
    private fun assertWholeWords(text: String) {
        assertWhole(text)
        val layout = layoutOf(text)
        (0 until layout.lineCount - 1).forEach { line ->
            val end = layout.getLineEnd(line)
            assertTrue("$phone: \"$text\" is split inside a word at $end", text[end - 1].isWhitespace())
        }
    }

    @Test
    fun writing_shows_every_key_and_both_buttons_on_every_phone() {
        show(FluteUiState(tune = twoNotes))
        PHONES.forEach { each ->
            on(each)
            listOf("C6", "B5", "C5", "B4", "C4", "Rest", "Breathe", "Erase the last note").forEach { name ->
                assertInside(name, key(name).assertIsDisplayed(), shadow = 5.dp)
                assertNearBySize("$each: $name's width", each.keyWidth.dp, width(key(name)))
                assertNear("$each: $name's height", each.keyHeight.dp, height(key(name)))
            }
            listOf("Play", "Listen to me").forEach { name ->
                assertInside(name, word(name).assertIsDisplayed(), shadow = 8.dp)
                assertNear("$each: $name's height", 84.dp, height(word(name)))
                assertWhole(name)
            }
            assertInside("the knob", knob().assertIsDisplayed())
            assertInside("the book", book().assertIsDisplayed())
        }
    }

    /** The keys stand beside the staff with the two buttons under them, the board the user picked. */
    @Test
    fun the_staff_takes_the_room_the_keys_leave() {
        show(FluteUiState(tune = twoNotes))
        PHONES.forEach { each ->
            on(each)
            val staff = bounds(staff())
            val c6 = bounds(key("C6"))
            val b5 = bounds(key("B5"))
            assertNearBySize("$each: the keys' width", each.keysWidth.dp, b5.right - c6.left)
            assertNear("$each: the keys end at the margin", each.contentWidth.dp - 8.dp, b5.right)
            // The card's inside ends the card's padding before the keys' margin.
            assertNear("$each: the staff ends at the card's padding", c6.left - 8.dp - 16.dp, staff.right)
            assertNear("$each: the staff's height", TuneStaffHeight, staff.bottom - staff.top, tolerance = 3f)
            assertNear("$each: the staff's middle", (each.height / 2).dp, (staff.top + staff.bottom) / 2)
            assertNear("$each: the knob keeps its corner", 24.dp, bounds(knob()).left)
            assertNear("$each: the book keeps its corner", 24.dp, bounds(book()).left)
        }
    }

    /** The key gap has three uses, which must agree, or the tools stop standing over the keys below them. */
    @Test
    fun the_tools_stand_over_the_last_three_keys() {
        show(FluteUiState(tune = twoNotes))
        PHONES.forEach { each ->
            on(each)
            assertNear("$each: the rest over G5", bounds(key("G5")).left, bounds(key("Rest")).left, tolerance = 1f)
            assertNear("$each: the breath over A5", bounds(key("A5")).left, bounds(key("Breathe")).left, tolerance = 1f)
            assertNear("$each: the sponge over B5", bounds(key("B5")).right, bounds(key("Erase the last note")).right, tolerance = 1f)
            assertNear("$each: C6 over C5", bounds(key("C5")).left, bounds(key("C6")).left, tolerance = 1f)
            assertNear("$each: the gap", each.keyGap.dp, bounds(key("D5")).left - bounds(key("C5")).right)
        }
    }

    @Test
    fun play_stays_left_of_listen_to_me() {
        show(FluteUiState(tune = twoNotes))
        PHONES.forEach { each ->
            on(each)
            val play = bounds(word("Play"))
            val listen = bounds(word("Listen to me"))
            val keys = bounds(key("C4"))
            assertTrue("$each: Play ends at ${play.right}, Listen to me starts at ${listen.left}", play.right < listen.left)
            // Under the keys, sharing their width, not under the staff.
            assertTrue("$each: Play starts at ${play.top}, the keys end at ${keys.bottom}", play.top >= keys.bottom)
            assertNear("$each: Play starts where the keys do", keys.left, play.left)
            assertNear("$each: Listen to me ends where the keys do", bounds(key("B4")).right, listen.right)
            assertNear("$each: they end 16 dp up, with the shadow's 8 under them", each.height.dp - 16.dp, play.bottom)
        }
        word("Play").performClick()
        word("Listen to me").performClick()
        assertEquals(1, playToggles)
        assertEquals(1, yourTurns)
    }

    @Test
    fun running_swaps_the_keys_for_stop() {
        show(FluteUiState(tune = twoNotes, isListening = true, listenIndex = 0))
        fun assertRunning() {
            key("C4").assertDoesNotExist()
            key("Erase the last note").assertDoesNotExist()
            word("Play").assertDoesNotExist()
            word("Listen to me").assertDoesNotExist()
            val stop = bounds(word("Stop").assertIsDisplayed())
            assertTrue("Stop ends at ${stop.right}", phone.contentWidth.dp - stop.right <= 16.dp)
            assertNear("Stop's width", phone.column.dp, stop.right - stop.left)
            assertNear("Stop's height", 104.dp, stop.bottom - stop.top)
            assertNear("Stop's bottom", phone.height.dp - 16.dp, stop.bottom)
            assertTrue("the card ends at ${bounds(staff()).right}, Stop starts at ${stop.left}", bounds(staff()).right < stop.left)
        }
        assertRunning()
        compose.runOnIdle { state.value = FluteUiState(tune = twoNotes, playingIndex = 0) }
        compose.waitForIdle()
        assertRunning()
        word("Stop").performClick()
        assertEquals(1, stops)
        compose.runOnIdle { state.value = FluteUiState(tune = twoNotes) }
        compose.waitForIdle()
        word("Stop").assertDoesNotExist()
        key("C4").assertIsDisplayed()
        word("Listen to me").assertIsDisplayed()
    }

    /** Stop takes Listen to me's corner, so after Your turn it is under the same thumb. */
    @Test
    fun stop_comes_up_in_listen_to_mes_corner() {
        show(FluteUiState(tune = twoNotes))
        val listen = bounds(word("Listen to me"))
        compose.runOnIdle { state.value = state.value.copy(isListening = true, listenIndex = 0) }
        compose.waitForIdle()
        val stop = bounds(word("Stop"))
        assertNear("the right edge", listen.right, stop.right)
        assertNear("the bottom", listen.bottom, stop.bottom)
    }

    @Test
    fun the_running_staff_is_as_big_as_the_height_lets_it() {
        show(FluteUiState(tune = twoNotes, isListening = true, listenIndex = 0))
        PHONES.forEach { each ->
            on(each)
            val scale = SidewaysGeometry.runningStaffScale(each.height.toFloat())
            assertNear("$each: the staff's height", tuneStaffHeight(scale), height(staff()), tolerance = 4f)
            val staff = bounds(staff())
            assertInside("the staff", staff())
            assertNear("$each: the staff's middle", (each.height / 2).dp, (staff.top + staff.bottom) / 2)
            // The card keeps the room the column leaves: the card's right edge is the column's margin away.
            assertNear("$each: the staff ends at the card's padding", (each.cardWidth + 8f - 16f).dp, staff.right)
            assertInside("the knob", knob().assertIsDisplayed())
            assertInside("the book", book().assertIsDisplayed())
        }
    }

    @Test
    fun advice_wraps_in_the_column() {
        phone = Phone(640, 360)
        show(FluteUiState(tune = twoNotes, isListening = true, listenIndex = 0, hearing = Hearing.LOUD_UNCLEAR))
        val advice = word(CLEAR_NOTE).assertIsDisplayed()
        assertWholeWords(CLEAR_NOTE)
        assertTrue("the advice is one line, it should wrap in the column", layoutOf(CLEAR_NOTE).lineCount > 1)
        val box = bounds(advice)
        val stop = bounds(word("Stop"))
        assertTrue("the advice starts at ${box.left}, the column at ${stop.left}", box.left >= stop.left - SLACK)
        assertTrue("the advice ends at ${box.right}, the column at ${stop.right}", box.right <= stop.right + SLACK)
        assertTrue("the advice ends at ${box.bottom}, Stop starts at ${stop.top}", box.bottom <= stop.top)
        assertNear("the advice sits at the top of the column", 14.dp, box.top)
    }

    /** The Croatian advice's long words in the 100 dp column at large text: smaller, never cut. */
    private fun assertCroatianAdviceFits(fontScale: Float) {
        phone = Phone(640, 360)
        show(FluteUiState(tune = twoNotes, isListening = true, listenIndex = 0), Locale("hr"), fontScale = fontScale)
        listOf(
            state.value.copy(hearing = Hearing.LOUD_UNCLEAR) to "🎵 Odsviraj jedan dug, čist ton",
            state.value.copy(hearing = Hearing.QUIET_UNCLEAR) to "📱 Dođi bliže mobitelu",
            state.value.copy(repeatHeld = true) to "🔁 Stani, pa odsviraj opet",
        ).forEach { (next, advice) ->
            compose.runOnIdle { state.value = next }
            compose.waitForIdle()
            word(advice).assertIsDisplayed()
            assertWholeWords(advice)
            assertTrue("the advice runs into Stop", bounds(word(advice)).bottom <= bounds(word("Stani")).top)
            assertInside("the advice", word(advice))
        }
    }

    @Test
    fun croatian_advice_fits_at_large_text() = assertCroatianAdviceFits(1.5f)

    /** The largest text size Android offers: the 12 sp floor is 24 dp tall there, as wide as the column's longest words allow. */
    @Test
    fun croatian_advice_fits_at_the_largest_text() = assertCroatianAdviceFits(2.0f)

    @Test
    fun stop_stays_on_screen_on_every_phone() {
        show(FluteUiState(tune = twoNotes, isListening = true, listenIndex = 0, hearing = Hearing.LOUD_UNCLEAR))
        PHONES.forEach { each ->
            on(each)
            assertInside("Stop", word("Stop").assertIsDisplayed(), shadow = 8.dp)
            assertNear("$each: Stop's width", each.column.dp, width(word("Stop")))
            assertWhole("Stop")
            assertInside("the advice", word(CLEAR_NOTE).assertIsDisplayed())
        }
    }

    @Test
    fun the_speed_panel_shows_the_ring_and_ok() {
        show(FluteUiState(tune = twoNotes))
        knob().performClick()
        assertEquals(1, speedOpens)
        ring().assertIsDisplayed()
        val ok = bounds(word("OK").assertIsDisplayed())
        // OK in Play's place: the metronome tab's board, with the keys gone.
        assertTrue("the ring ends at ${bounds(ring()).right}, OK starts at ${ok.left}", bounds(ring()).right <= ok.left)
        assertNear("OK's width", phone.column.dp, ok.right - ok.left)
        assertNear("OK's height", 104.dp, ok.bottom - ok.top)
        assertNear("OK's bottom", phone.height.dp - 16.dp, ok.bottom)
        key("C4").assertDoesNotExist()
        word("Listen to me").assertDoesNotExist()
        key("Fewer beats").performClick()
        assertEquals(TimeSignature.THREE_FOUR, chosenSignature)
        word("OK").performClick()
        assertEquals(1, speedDones)
        ring().assertDoesNotExist()
        word("OK").assertDoesNotExist()
        key("C4").assertIsDisplayed()
    }

    @Test
    fun the_speed_panel_fits_every_phone() {
        show(FluteUiState(tune = twoNotes))
        knob().performClick()
        PHONES.forEach { each ->
            on(each)
            assertInside("OK", word("OK").assertIsDisplayed(), shadow = 8.dp)
            assertInside("the ring", ring().assertIsDisplayed())
            assertNearBySize("$each: the ring", SidewaysGeometry.ringSize(each.cardWidth, each.height.toFloat()).dp, width(ring()))
            assertInside("the minus", key("Fewer beats"), shadow = 4.dp)
            assertInside("the plus", key("More beats"), shadow = 4.dp)
        }
    }

    @Test
    fun a_touch_elsewhere_closes_the_panels_wheel() {
        show(FluteUiState(tune = twoNotes))
        knob().performClick()
        word("120").performClick()
        word("119").assertIsDisplayed()
        key("More beats").performClick()
        word("119").assertDoesNotExist()
        assertEquals(TimeSignature.FIVE_FOUR, chosenSignature)
    }

    @Test
    fun a_panel_stays_open_when_the_phone_turns() {
        show(FluteUiState(tune = twoNotes))
        knob().performClick()
        ring().assertIsDisplayed()
        assertNear("OK stands in the column", 116.dp, width(word("OK")))

        turnUpright()
        ring().assertIsDisplayed()
        assertTrue("upright OK is the wide pill, it is ${width(word("OK"))}", width(word("OK")) > 200.dp)

        turnSideways()
        ring().assertIsDisplayed()
        assertNear("OK is back in the column", 116.dp, width(word("OK")))
        key("C4").assertDoesNotExist()
        assertEquals(1, speedOpens)
        assertEquals(0, speedDones)
    }

    @Test
    fun an_open_wheel_stays_open_when_the_phone_turns() {
        show(FluteUiState(tune = twoNotes))
        knob().performClick()
        word("120").performClick()
        word("119").assertIsDisplayed()
        assertNear("OK stands in the column", 116.dp, width(word("OK")))
        turnUpright()
        word("119").assertIsDisplayed()
        turnSideways()
        word("119").assertIsDisplayed()
    }

    /**
     * Review Focus 1: nothing else ends the note now that the Activity survives the turn. These four
     * say what must happen, not why: Compose sends a key leaving the screen a cancel first and then
     * cancels its gesture, and under this rule's dispatcher the cancel is handled at once, so they
     * pass with or without the `finally` in `FluteKey`. On a phone the cancel only queues and the
     * gesture dies first, which is what the two `..._gesture_is_cancelled_...` tests below stand for.
     */
    @Test
    fun a_key_held_during_a_turn_is_released() {
        show(FluteUiState(tune = twoNotes))
        key("G4").performTouchInput { down(center) }
        assertEquals(listOf(67), pressed)
        assertTrue(released.isEmpty())
        flip()
        assertEquals(listOf(67), released)
    }

    @Test
    fun a_rest_held_during_a_turn_is_released() {
        show(FluteUiState(tune = twoNotes))
        key("Rest").performTouchInput { down(center) }
        assertEquals(listOf("press"), restEvents)
        flip()
        assertEquals(listOf("press", "release"), restEvents)
    }

    @Test
    fun a_key_held_upright_during_a_turn_is_released() {
        phone = UPRIGHT
        sideways = false
        show(FluteUiState(tune = twoNotes))
        key("G4").performTouchInput { down(center) }
        assertEquals(listOf(67), pressed)
        assertTrue(released.isEmpty())
        flip()
        assertEquals(listOf(67), released)
    }

    @Test
    fun a_rest_held_upright_during_a_turn_is_released() {
        phone = UPRIGHT
        sideways = false
        show(FluteUiState(tune = twoNotes))
        key("Rest").performTouchInput { down(center) }
        assertEquals(listOf("press"), restEvents)
        flip()
        assertEquals(listOf("press", "release"), restEvents)
    }

    /**
     * What a turn does to a held key on a phone: the gesture is cancelled before any up or cancel
     * reaches it. A change of density does just that here, without the key leaving the screen.
     */
    private fun cancelTheGestures() = on(Phone(phone.width - 1, phone.height))

    @Test
    fun a_key_whose_gesture_is_cancelled_is_released() {
        show(FluteUiState(tune = twoNotes))
        key("G4").performTouchInput { down(center) }
        assertEquals(listOf(67), pressed)
        assertTrue(released.isEmpty())
        cancelTheGestures()
        assertEquals(listOf(67), released)
    }

    @Test
    fun a_rest_whose_gesture_is_cancelled_is_released() {
        show(FluteUiState(tune = twoNotes))
        key("Rest").performTouchInput { down(center) }
        assertEquals(listOf("press"), restEvents)
        cancelTheGestures()
        assertEquals(listOf("press", "release"), restEvents)
    }

    /** Review Focus 4. */
    @Test
    fun an_empty_tune_keeps_its_hint_on_the_narrowest_phone() {
        phone = Phone(640, 360)
        show(FluteUiState())
        val hint = bounds(word(HINT).assertIsDisplayed())
        assertWholeWords(HINT)
        // Inside the card, whose edge is the keys' margin away from C6.
        assertTrue("the hint ends at ${hint.right}, the keys start at ${bounds(key("C6")).left}", hint.right <= bounds(key("C6")).left - 8.dp)
        assertNear("the card", 228.dp, bounds(key("C6")).left - 16.dp)
        word("Play").assertIsDisplayed().assertIsNotEnabled()
        word("Listen to me").assertIsDisplayed().assertIsNotEnabled()
        key("Erase the last note").assertIsNotEnabled()
        word("Play").performClick()
        word("Listen to me").performClick()
        assertEquals(0, playToggles)
        assertEquals(0, yourTurns)
    }

    @Test
    fun the_croatian_hint_stays_whole_at_large_text() {
        phone = Phone(640, 360)
        show(FluteUiState(), Locale("hr"), fontScale = 1.5f)
        word(HINT_HR).assertIsDisplayed()
        assertWholeWords(HINT_HR)
        assertTrue("the hint runs into the keys", bounds(word(HINT_HR)).right <= bounds(key("c3")).left - 8.dp)
    }

    @Test
    fun the_hint_stays_whole_at_large_text() {
        phone = Phone(640, 360)
        show(FluteUiState(), fontScale = 1.5f)
        word(HINT).assertIsDisplayed()
        assertWholeWords(HINT)
        assertTrue("the hint runs into the keys", bounds(word(HINT)).right <= bounds(key("C6")).left - 8.dp)
    }

    /** Review Focus 3: the words shrink in the narrow buttons rather than being cut. */
    @Test
    fun croatian_buttons_fit_at_large_text() {
        phone = Phone(640, 360)
        show(FluteUiState(tune = twoNotes), Locale("hr"), fontScale = 1.5f)
        listOf("Odsviraj", "Poslušaj me").forEach { name ->
            assertInside(name, word(name).assertIsDisplayed(), shadow = 8.dp)
            assertWhole(name)
            assertTrue("$name is under the keys", bounds(word(name)).top >= bounds(key("c1")).bottom)
        }
    }

    @Test
    fun croatian_stop_and_ok_fit_at_large_text() {
        phone = Phone(640, 360)
        show(FluteUiState(tune = twoNotes), Locale("hr"), fontScale = 1.5f)
        compose.onNodeWithContentDescription("Brzina: Zec, dodirni za promjenu").performClick()
        assertInside("OK", word("U redu").assertIsDisplayed(), shadow = 8.dp)
        assertWhole("U redu")
        word("U redu").performClick()
        compose.runOnIdle { state.value = state.value.copy(isListening = true, listenIndex = 0) }
        compose.waitForIdle()
        assertInside("Stop", word("Stani").assertIsDisplayed(), shadow = 8.dp)
        assertWhole("Stani")
    }

    // ---- Writing below the least height ----

    /**
     * Under 329 dp (the margins, the keys at their least, a gap and Play) the keys' column scrolls
     * rather than let the key rows run past it onto Play: the top row would stand above the page's
     * margin and the bottom one on Play. Both phones here are shorter than any the layout was drawn for.
     */
    @Test
    fun below_329_dp_tall_the_keys_scroll_and_stay_clear_of_play() {
        phone = Phone(640, 320)
        show(FluteUiState(tune = twoNotes))
        listOf(Phone(640, 320), Phone(640, 300)).forEach { each ->
            on(each)
            val top = bounds(key("C6"))
            val bottom = bounds(key("C4"))
            val play = bounds(word("Play"))
            assertTrue("$each: the top key starts at ${top.top}, inside the page's 8 dp margin", top.top >= 8.dp - SLACK)
            assertTrue("$each: the lowest keys and their shadow end at ${bottom.bottom + 5.dp}, Play starts at ${play.top}", bottom.bottom + 5.dp <= play.top + SLACK)
            // Play and its shadow are past the window's end, so a child scrolls to them.
            word("Play").performScrollTo().assertIsDisplayed()
            word("Listen to me").assertIsDisplayed()
        }
    }

    /** A little over the least height the keys and Play need, the column fits and does not scroll. */
    @Test
    fun just_over_329_dp_tall_nothing_needs_to_scroll() {
        phone = Phone(640, 340)
        show(FluteUiState(tune = twoNotes))
        assertInside("Play", word("Play").assertIsDisplayed(), shadow = 8.dp)
        assertInside("the top key", key("C6"), shadow = 5.dp)
    }

    // ---- The staff keeps its place ----

    /** A tune of 40 notes, far longer than the staff shows; the first is the only D4, so its step can be asked for. */
    private val longTune = listOf(TuneNote(62, NoteLength.QUARTER)) + List(40) { TuneNote(60, NoteLength.QUARTER) }

    private fun firstStep() = compose.onAllNodesWithContentDescription("D4, 1 beat")

    /**
     * The staff scrolls to the step it follows when it enters the screen, so a staff that started
     * again would show its first step before it got there. Frames are advanced by hand to see it.
     */
    private fun assertTheStaffNeverGoesBackToItsStart(frames: Int = 90, change: () -> Unit) {
        compose.mainClock.autoAdvance = false
        firstStep().assertCountEquals(0)
        compose.runOnIdle { change() }
        repeat(frames) { frame ->
            compose.mainClock.advanceTimeByFrame()
            assertEquals("frame $frame: the staff shows its first step again", 0, firstStep().fetchSemanticsNodes().size)
        }
    }

    @Test
    fun the_staff_keeps_its_place_when_the_phone_turns() {
        show(FluteUiState(tune = longTune))
        compose.waitForIdle()
        assertTheStaffNeverGoesBackToItsStart { sideways = false }
        assertTheStaffNeverGoesBackToItsStart { sideways = true }
    }

    @Test
    fun the_staff_keeps_its_place_when_a_run_ends() {
        show(FluteUiState(tune = longTune, isListening = true, listenIndex = 38))
        compose.waitForIdle()
        assertTheStaffNeverGoesBackToItsStart { state.value = FluteUiState(tune = longTune) }
    }

    @Test
    fun the_staff_keeps_its_place_when_a_run_starts() {
        show(FluteUiState(tune = longTune))
        compose.waitForIdle()
        assertTheStaffNeverGoesBackToItsStart { state.value = FluteUiState(tune = longTune, playingIndex = 38) }
    }

    // ---- The microphone card ----

    /**
     * Your turn when the phone has said no: the tuner's card with its quiet Not now, handed the whole tab,
     * so it has the margins of a sideways page and not upright's padding around it.
     */
    @Test
    fun the_microphone_card_shows_not_now_on_every_phone() {
        show(FluteUiState(tune = twoNotes, micDenied = true))
        PHONES.forEach { each ->
            on(each)
            listOf("Can I listen?", "Settings", "Not now", "Yes").forEach { name ->
                assertInside(name, word(name).assertIsDisplayed(), shadow = 8.dp)
            }
            val settings = bounds(word("Settings"))
            val notNow = bounds(word("Not now"))
            val yes = bounds(word("Yes"))
            assertNear("$each: Settings is at the top of the column", 14.dp, settings.top)
            assertTrue("$each: Not now is under Settings", notNow.top >= settings.bottom)
            assertTrue("$each: Not now is over Yes", notNow.bottom <= yes.top)
            assertNear("$each: Yes's bottom", each.height.dp - 16.dp, yes.bottom)
            assertNear("$each: Yes's right edge", each.contentWidth.dp - 8.dp, yes.right)
            assertNear("$each: the column's width", each.column.dp, yes.right - yes.left)
            // The card has the page's 8 dp margin and its content 16 dp of padding more, not upright's 20 dp around the card.
            val title = bounds(word("Can I listen?")).left
            assertTrue("$each: the title starts at $title, inside the card's padding", title >= 8.dp + 16.dp - SLACK)
            assertTrue("$each: the title starts at $title, not past the card's middle", title < 8.dp + each.cardWidth.dp / 2)
            key("C4").assertDoesNotExist()
            word("Listen to me").assertDoesNotExist()
        }
    }

    @Test
    fun the_microphone_cards_way_back_is_whole_in_croatian_at_large_text() {
        phone = Phone(640, 360)
        show(FluteUiState(tune = twoNotes, micDenied = true), Locale("hr"), fontScale = 1.5f)
        listOf("Postavke", "Ne sad", "Da").forEach { name ->
            assertInside(name, word(name).assertIsDisplayed(), shadow = 8.dp)
            assertWhole(name)
        }
        word("Ne sad").performClick()
        assertEquals(1, micDismissals)
    }

    // ---- The shelf ----

    /** The cookie holds five notes (C4 to G4), of which the label shows as many as fit; the others are empty. */
    private val fiveNoteCookie = List(6) { Book() }.toMutableList().also {
        it[1] = Book(listOf(60, 62, 64, 65, 67).map { midi -> TuneNote(midi, NoteLength.QUARTER) })
    }

    private val shelfNames = listOf("Lollipop, open now", "Cookie, 5 notes", "Cupcake, empty", "Donut, empty", "Ice cream, empty", "Candy, empty")

    /** The open lollipop's tune is the one A4, so no letter of the cookie's is its. */
    private fun showShelf() {
        show(FluteUiState(tune = listOf(TuneNote(69, NoteLength.HALF)), books = fiveNoteCookie))
        book().performClick()
    }

    private fun cover(name: String) = compose.onNodeWithContentDescription(name)

    /** How many bubbles of [name] the label of its cover holds; they are silent, so the unmerged tree is asked. */
    private fun bubbles(name: String) = compose.onAllNodesWithText(name, useUnmergedTree = true)

    @Test
    fun the_shelf_is_one_row_of_six() {
        showShelf()
        listOf(Phone(915, 411), Phone(640, 360)).forEach { each ->
            on(each)
            val covers = shelfNames.map { bounds(cover(it).assertIsDisplayed()) }
            // One row: one top and one height, left to right in the sweets' order.
            covers.forEach {
                assertNear("$each: a book's top", covers[0].top, it.top, tolerance = 0.5f)
                assertNear("$each: a book's height", covers[0].bottom - covers[0].top, it.bottom - it.top, tolerance = 0.5f)
            }
            val room = each.contentWidth.dp - 16.dp
            val book = (room - 8.dp * 5) / 6
            covers.forEachIndexed { index, b ->
                // The cover is its book less the 8 dp of page edges beside it, and the books stand 8 dp apart.
                assertNear("$each: book $index's left", 8.dp + (book + 8.dp) * index, b.left)
                assertNear("$each: book $index's width", book - 8.dp, b.right - b.left)
            }
            assertInside("the last book", cover(shelfNames.last()), shadow = 4.dp)
            // 300 dp tall where the room (395 and 344 dp here) allows more, centred in the window, not stretched over it.
            val height = covers[0].bottom - covers[0].top
            assertNear("$each: the books' height", 300.dp, height)
            assertNear("$each: the books' top", (each.height.dp - height) / 2, covers[0].top, tolerance = 2f)
        }
        cover("Cookie, 5 notes").performClick()
        assertEquals(listOf(1), chosenBooks)
    }

    @Test
    fun the_shelf_has_no_scrolling_and_no_column() {
        showShelf()
        PHONES.forEach { each ->
            on(each)
            word("Play").assertDoesNotExist()
            word("OK").assertDoesNotExist()
            key("C4").assertDoesNotExist()
            // The first and the last book are in the room without a scroll, and the shelf is as tall as the books.
            shelfNames.forEach { assertInside(it, cover(it).assertIsDisplayed(), shadow = 4.dp) }
        }
    }

    @Test
    fun a_label_shows_the_notes_that_fit() {
        showShelf()
        // 915 x 411: the label has about 92 dp, room for three bubbles, as the mockup draws it.
        listOf("C4", "D4", "E4").forEach { bubbles(it).assertCountEquals(1) }
        listOf("F4", "G4").forEach { bubbles(it).assertCountEquals(0) }
        // 800 x 360: the books are 110 dp wide and the label holds two.
        on(Phone(800, 360))
        listOf("C4", "D4").forEach { bubbles(it).assertCountEquals(1) }
        listOf("E4", "F4", "G4").forEach { bubbles(it).assertCountEquals(0) }
        // 640 x 360: the books are 83 dp wide and the label holds one.
        on(Phone(640, 360))
        bubbles("C4").assertCountEquals(1)
        listOf("D4", "E4", "F4", "G4").forEach { bubbles(it).assertCountEquals(0) }
    }

    @Test
    fun the_shelf_stays_open_and_changes_shape_when_the_phone_turns() {
        showShelf()
        turnUpright()
        // Upright is two columns: the cookie stands beside the lollipop and the cupcake under it.
        val lollipop = bounds(cover("Lollipop, open now").assertIsDisplayed())
        val cookie = bounds(cover("Cookie, 5 notes").assertIsDisplayed())
        val cupcake = bounds(cover("Cupcake, empty").assertIsDisplayed())
        assertNear("the cookie's top", lollipop.top, cookie.top, tolerance = 0.5f)
        assertTrue("the cupcake stands under the lollipop", cupcake.top > lollipop.bottom && abs((cupcake.left - lollipop.left).value) <= 0.5f)
        // Upright keeps five bubbles, shrunk to fit.
        listOf("C4", "D4", "E4", "F4", "G4").forEach { bubbles(it).assertCountEquals(1) }
        key("C4").assertDoesNotExist()

        turnSideways()
        val row = shelfNames.map { bounds(cover(it).assertIsDisplayed()).top }
        row.forEach { assertNear("the books' top", row[0], it, tolerance = 0.5f) }
        assertEquals(emptyList<Int>(), chosenBooks)
    }

    private companion object {
        const val RING = "Tempo ring, drag around it to change the speed"
        const val HINT = "👆 Tap or hold a note"
        const val HINT_HR = "👆 Dodirni ili drži notu"
        const val CLEAR_NOTE = "🎵 Play one long, clear note"
        const val TOLERANCE = 2f
        const val RELATIVE_TOLERANCE = 0.02f

        val PHONES = listOf(Phone(915, 411), Phone(800, 360), Phone(640, 360))

        /** A 412 x 915 phone upright: `SidewaysHost` takes the rail off the width, so it is added here. */
        val UPRIGHT = Phone(504, 915)

        /** Rounding of the forced density, in dp. */
        val SLACK = 1.dp

        val twoNotes = listOf(TuneNote(60, NoteLength.QUARTER), TuneNote(67, NoteLength.QUARTER))
    }
}
