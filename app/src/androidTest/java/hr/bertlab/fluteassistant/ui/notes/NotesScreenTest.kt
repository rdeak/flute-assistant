// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.notes

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import hr.bertlab.fluteassistant.domain.notes.StaffNotes
import hr.bertlab.fluteassistant.ui.common.LocaleProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class NotesScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var state: MutableState<NotesUiState>
    private val tapped = mutableListOf<Int>()
    private val toggled = mutableListOf<Int>()
    private var playAgainRequests = 0
    private var soundToggles = 0

    private fun show(initial: NotesUiState, locale: Locale = Locale.ENGLISH) {
        state = mutableStateOf(initial)
        compose.setContent {
            LocaleProvider(locale) {
                NotesContent(
                    state = state.value,
                    onNoteTapped = { tapped += it },
                    onPlayAgain = { playAgainRequests++ },
                    onTogglePracticeNote = { toggled += it },
                    onToggleSound = { soundToggles++ },
                )
            }
        }
    }

    /** A round in progress asking [midi], with the default notes chosen. */
    private fun asking(midi: Int = 60) = NotesUiState(practiceNotes = StaffNotes.DEFAULT_PRACTICE, currentMidi = midi)

    private val staffDescription = "Staff, press where the note goes and let go"

    /**
     * Flames are pictures and not words: a larger text size would run five of them out of their pill,
     * which is only 92 dp wide inside, so they keep their size.
     */
    @Test
    fun the_flames_do_not_grow_with_the_text_size() {
        var fontScale by mutableFloatStateOf(1f)
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(fontScale)) {
                LocaleProvider(Locale.ENGLISH) { StreakFlames(3) }
            }
        }
        val pill = compose.onNodeWithContentDescription("Streak of 3")
        val normal = pill.getBoundsInRoot().width
        listOf(1.5f, 2f).forEach { scale ->
            compose.runOnIdle { fontScale = scale }
            compose.waitForIdle()
            val grown = pill.getBoundsInRoot().width
            assertTrue("at $scale x the pill is $grown wide, at normal text $normal", (grown - normal).value <= 1f)
        }
    }

    @Test
    fun shows_the_note_name_as_the_whole_question() {
        show(asking(60))
        compose.onNodeWithText("🔎 Find").assertIsDisplayed()
        compose.onNodeWithText("C4").assertIsDisplayed()
        compose.onNodeWithText("🎉 That's it!").assertDoesNotExist()
        compose.onNodeWithContentDescription(staffDescription).assertExists()
        compose.onNodeWithContentDescription("Round progress, 0 of 10 notes").assertExists()
    }

    @Test
    fun the_pick_button_sits_beside_the_note_name() {
        show(asking(60))
        val name = compose.onNodeWithText("C4").getBoundsInRoot()
        val button = compose.onNodeWithContentDescription("Choose notes").getBoundsInRoot()
        assertTrue("button $button should be right of the name $name", button.left >= name.right)
        val nameMiddle = (name.top + name.bottom) / 2
        assertTrue("button $button should share the name's row $name", button.top <= nameMiddle && button.bottom >= nameMiddle)
        compose.onNodeWithText("Pick").assertIsDisplayed()
    }

    @Test
    fun the_round_stars_sit_below_the_staff_and_its_feedback() {
        show(asking(60).copy(wrongTaps = setOf(62), lastWrongTap = 62, taps = 1))
        val staff = compose.onNodeWithContentDescription(staffDescription).getBoundsInRoot()
        val feedback = compose.onNodeWithText("🙈 Try again").getBoundsInRoot()
        val stars = compose.onNodeWithContentDescription("Round progress, 0 of 10 notes").getBoundsInRoot()
        assertTrue("feedback $feedback should be under the staff $staff", feedback.top >= staff.bottom)
        assertTrue("stars $stars should be under the feedback $feedback", stars.top >= feedback.bottom)
    }

    @Test
    fun tapping_a_staff_position_reports_its_note() {
        show(asking(60).copy(practiceNotes = StaffNotes.DEFAULT_PRACTICE + 84))
        compose.onNodeWithContentDescription("Staff position 1").performClick()
        compose.onNodeWithContentDescription("Staff position 15").performClick()
        assertEquals(listOf(60, 84), tapped)
    }

    @Test
    fun a_finger_on_the_staff_places_the_note_and_letting_go_answers() {
        show(asking(60))
        val c4 = compose.onNodeWithContentDescription("Staff position 1").getBoundsInRoot()
        val d4 = compose.onNodeWithContentDescription("Staff position 2").getBoundsInRoot()
        val staff = compose.onNodeWithContentDescription(staffDescription)
        val top = staff.getBoundsInRoot().top
        val x = with(compose.density) { 100.dp.toPx() }
        fun yOf(place: DpRect) = with(compose.density) { ((place.top + place.bottom) / 2 - top).toPx() }

        staff.performTouchInput { down(Offset(x, yOf(d4))) }
        compose.onNodeWithText("👆 Slide, let go").assertIsDisplayed()
        assertTrue("nothing is answered while the finger is down", tapped.isEmpty())
        staff.performTouchInput { moveTo(Offset(x, yOf(c4))) }
        staff.performTouchInput { up() }
        assertEquals(listOf(60), tapped)
        compose.onNodeWithText("👆 Slide, let go").assertDoesNotExist()
    }

    @Test
    fun sliding_off_the_staff_answers_nothing() {
        show(asking(60))
        val staff = compose.onNodeWithContentDescription(staffDescription)
        staff.performTouchInput { down(center) }
        staff.performTouchInput { moveTo(Offset(centerX, -200f)) }
        staff.performTouchInput { up() }
        assertTrue("a slide off the staff is a change of mind", tapped.isEmpty())
    }

    @Test
    fun ledger_places_outside_the_staff_appear_only_for_chosen_notes() {
        show(asking(62).copy(practiceNotes = setOf(62, 64, 65))) // nothing below or above the staff
        compose.onNodeWithContentDescription("Staff position 1").assertDoesNotExist() // c1
        compose.onNodeWithContentDescription("Staff position 2").assertExists() // d1, the space under the staff
        compose.onNodeWithContentDescription("Staff position 12").assertExists() // g2, the space above it
        compose.onNodeWithContentDescription("Staff position 13").assertDoesNotExist() // a2

        compose.runOnIdle { state.value = state.value.copy(practiceNotes = setOf(62, 84)) } // c3 chosen
        compose.onNodeWithContentDescription("Staff position 13").assertExists()
        compose.onNodeWithContentDescription("Staff position 15").assertExists()
        compose.onNodeWithContentDescription("Staff position 1").assertDoesNotExist()
    }

    @Test
    fun the_staff_takes_the_height_left_on_the_screen() {
        show(asking(60))
        val staff = compose.onNodeWithContentDescription(staffDescription).getBoundsInRoot()
        val stars = compose.onNodeWithContentDescription("Round progress, 0 of 10 notes").getBoundsInRoot()
        assertTrue("staff $staff should be far taller than a fixed 390 dp", staff.height >= 450.dp)
        assertTrue("stars $stars should sit at the bottom, under the staff $staff", stars.top >= staff.bottom)
    }

    @Test
    fun places_stack_in_one_column_with_room_between_them() {
        show(asking(60))
        val c4 = compose.onNodeWithContentDescription("Staff position 1").getBoundsInRoot()
        val d4 = compose.onNodeWithContentDescription("Staff position 2").getBoundsInRoot()
        val g5 = compose.onNodeWithContentDescription("Staff position 12").getBoundsInRoot()
        // One column: every place spans the same width, the lower note below the higher one.
        assertEquals(c4.left, d4.left)
        assertEquals(c4.right, d4.right)
        assertTrue("d4 $d4 should sit above c4 $c4", d4.bottom <= c4.top)
        assertTrue("g5 $g5 should sit above d4 $d4", g5.bottom <= d4.top)
        // Each place is at least 30 dp tall and the full width, so a finger lands on one place.
        listOf(c4, d4, g5).forEach { assertTrue("target $it too small", it.height >= 30.dp && it.width >= 300.dp) }
    }

    @Test
    fun a_wrong_place_is_crossed_inside_its_own_place() {
        // D4 answered by mistake right under the asked E4: the cross may not reach into E4's
        // place, or the child has nothing clear to aim at next. Whatever the miss changed in
        // the note column is the cross (the arrow beside it is drawn further right).
        show(asking(64))
        val staff = compose.onNodeWithContentDescription(staffDescription)
        val staffBounds = staff.getBoundsInRoot()
        val before = staff.captureToImage().toPixelMap()
        compose.runOnIdle { state.value = state.value.copy(wrongTaps = setOf(62), lastWrongTap = 62, taps = 1) }
        val after = staff.captureToImage().toPixelMap()
        val columns = (after.width * 0.42f).toInt()..(after.width * 0.58f).toInt()
        val changed = (0 until after.height).filter { y -> columns.any { x -> before[x, y] != after[x, y] } }
        assertTrue("no cross painted", changed.isNotEmpty())
        val d4 = compose.onNodeWithContentDescription("Staff position 2").getBoundsInRoot()
        val d4Rows = with(compose.density) { (d4.top - staffBounds.top).roundToPx()..(d4.bottom - staffBounds.top).roundToPx() }
        assertTrue(
            "cross rows ${changed.first()}..${changed.last()} should stay inside D4's place $d4Rows",
            changed.first() >= d4Rows.first && changed.last() <= d4Rows.last,
        )
    }

    @Test
    fun a_right_answer_says_so() {
        show(asking(60).copy(solved = true, results = listOf(true), taps = 1, streak = 1))
        compose.onNodeWithText("🎉 That's it!").assertIsDisplayed()
    }

    @Test
    fun a_wrong_answer_says_try_again() {
        show(asking(60).copy(wrongTaps = setOf(62), lastWrongTap = 62, taps = 1))
        compose.onNodeWithText("🙈 Try again").assertIsDisplayed()
        compose.onNodeWithText("C4").assertIsDisplayed() // the question stays
    }

    @Test
    fun the_summary_shows_stars_and_animals_but_no_numbers() {
        show(
            asking(60).copy(
                roundOver = true,
                results = List(8) { true } + List(2) { false },
                taps = 13,
                bestStreak = 5,
                roundMillis = 83_000L,
            ),
        )
        compose.onNodeWithText("🌟 Great job!").assertIsDisplayed()
        compose.onNodeWithContentDescription("Right first time: 8 of 10").assertIsDisplayed()
        compose.onNodeWithContentDescription("Streak of 5").assertIsDisplayed()
        compose.onNodeWithContentDescription("Aim: 2 of 3 stars").assertIsDisplayed() // 77 %
        compose.onNodeWithContentDescription("Speed: Turtle").assertIsDisplayed() // 8.3 s a note
        compose.onNodeWithText("8 / 10").assertDoesNotExist()
        compose.onNodeWithContentDescription(staffDescription).assertDoesNotExist()
        compose.onNodeWithText("Again").performClick()
        assertEquals(1, playAgainRequests)
    }

    @Test
    fun choosing_notes_toggles_them() {
        show(asking(60))
        compose.onNodeWithContentDescription("Choose notes").performClick()
        compose.onNodeWithText("Which notes?").assertIsDisplayed()
        compose.onNodeWithText("C4").assertIsOn()
        compose.onNodeWithText("C6").assertIsOff()
        compose.onNodeWithText("C6").performClick()
        assertEquals(listOf(84), toggled)
        compose.onNodeWithContentDescription("Choose notes").assertDoesNotExist() // one way out: the OK button
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithContentDescription(staffDescription).assertExists()
        compose.onNodeWithContentDescription("Choose notes").assertExists()
    }

    @Test
    fun the_picker_rows_run_from_c6_on_top_to_c4_at_the_bottom() {
        show(asking(60))
        compose.onNodeWithContentDescription("Choose notes").performClick()
        val c6 = compose.onNodeWithText("C6").getBoundsInRoot()
        val c5 = compose.onNodeWithText("C5").getBoundsInRoot()
        val c4 = compose.onNodeWithText("C4").getBoundsInRoot()
        assertTrue("C6 $c6 should be above C5 $c5", c6.bottom <= c5.top)
        assertTrue("C5 $c5 should be above C4 $c4", c5.bottom <= c4.top)
        assertEquals(c6.left, c4.left)
    }

    @Test
    fun the_ok_button_is_big_and_sits_under_the_sound_switch() {
        show(asking(60))
        compose.onNodeWithContentDescription("Choose notes").performClick()
        val lastPill = compose.onNodeWithText("B4").getBoundsInRoot()
        val sound = compose.onNodeWithText("Sound").getBoundsInRoot()
        val ok = compose.onNodeWithText("OK").getBoundsInRoot()
        assertTrue("sound row $sound should sit under the pills $lastPill", sound.top >= lastPill.bottom)
        assertTrue("OK $ok should sit under the sound row $sound", ok.top >= sound.bottom)
        assertTrue("OK $ok should be a big full-width button", ok.width >= 300.dp && ok.height >= 56.dp)
    }

    @Test
    fun the_sound_toggle_lives_with_the_note_picker() {
        show(asking(60))
        compose.onNodeWithText("Sound").assertDoesNotExist() // not on the game screen itself
        compose.onNodeWithContentDescription("Choose notes").performClick()
        compose.onNodeWithText("Sound").assertIsOn()
        compose.onNodeWithText("Sound").performClick()
        assertEquals(1, soundToggles)

        compose.runOnIdle { state.value = state.value.copy(soundOn = false) }
        compose.onNodeWithText("Sound").assertIsOff()
    }

    @Test
    fun the_streak_shows_as_flames_from_one() {
        show(asking(60).copy(streak = 3, results = listOf(true, true, true), taps = 3))
        compose.onNodeWithContentDescription("Streak of 3").assertIsDisplayed()
        compose.onNodeWithText("🔥 3").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(streak = 0) }
        compose.onNodeWithContentDescription("Streak of 0").assertDoesNotExist()
    }

    @Test
    fun croatian_names_and_copy() {
        show(asking(60), Locale("hr"))
        compose.onNodeWithText("c1").assertIsDisplayed()
        compose.onNodeWithText("🔎 Nađi").assertIsDisplayed()
        compose.onNodeWithContentDescription("Odaberi note").assertExists()
        compose.onNodeWithContentDescription("Mjesto 1 u crtovlju").assertExists()
        compose.onNodeWithContentDescription("Odaberi note").performClick()
        compose.onNodeWithText("Koje note?").assertIsDisplayed()
        compose.onNodeWithText("Zvuk").assertIsOn()
        compose.onNodeWithText("U redu").assertIsDisplayed()
    }
}
