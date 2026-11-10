// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.notes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.then
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import hr.bertlab.fluteassistant.domain.notes.NoteProgress
import hr.bertlab.fluteassistant.domain.notes.NotesGame
import hr.bertlab.fluteassistant.domain.notes.NotesRound
import hr.bertlab.fluteassistant.domain.notes.StaffNotes
import hr.bertlab.fluteassistant.ui.common.BigActionHeight
import hr.bertlab.fluteassistant.ui.common.BackKey
import hr.bertlab.fluteassistant.ui.common.LocaleProvider
import hr.bertlab.fluteassistant.ui.common.OutlineWidth
import hr.bertlab.fluteassistant.ui.common.needsDevice
import hr.bertlab.fluteassistant.ui.theme.FluteAssistantTheme
import hr.bertlab.fluteassistant.ui.theme.NoteLetterColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

@RunWith(AndroidJUnit4::class)
class NotesScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var state: MutableState<NotesRound>
    private lateinit var window: MutableState<DpSize>
    private val tapped = mutableListOf<Int>()
    private val toggled = mutableListOf<Int>()
    private val games = mutableListOf<NotesGame>()
    private var playAgainRequests = 0
    private var hearAgainRequests = 0
    private var phraseShownRequests = 0
    private var soundToggles = 0
    private val back = BackKey()

    private fun show(initial: NotesRound, locale: Locale = Locale.ENGLISH, dark: Boolean = false) {
        state = mutableStateOf(initial)
        compose.setContent {
            LocaleProvider(locale) {
                back.Watch()
                val content = @Composable {
                    NotesContent(
                        state = state.value,
                        actions = NotesActions(
                            onNoteTapped = { tapped += it },
                            onPlayAgain = { playAgainRequests++ },
                            onTogglePracticeNote = { toggled += it },
                            onToggleSound = { soundToggles++ },
                            onSetGame = { games += it },
                            onHearAgain = { hearAgainRequests++ },
                            onPhraseShown = { phraseShownRequests++ },
                        ),
                    )
                }
                // The theme `LocaleProvider` gives follows the phone; a test of night pins it.
                if (dark) FluteAssistantTheme(darkTheme = true, content = content) else content()
            }
        }
    }

    /** A round in progress asking [midi], with the default notes chosen. */
    private fun asking(midi: Int = 60) = NotesRound(practiceNotes = StaffNotes.DEFAULT_PRACTICE, currentMidi = midi)

    /** A Hear round whose phrase is C4 then D4, with the first on the staff and the second asked. */
    private fun hearing() = asking(62).copy(game = NotesGame.HEAR, phrase = listOf(60, 62), placed = 1)

    /** A finished round with [score] notes found first time. */
    private fun over(score: Int) = asking(60).copy(
        roundOver = true,
        results = List(score) { true } + List(NotesRound.ROUND_LENGTH - score) { false },
        taps = 13,
        bestStreak = 5,
        roundMillis = 83_000L,
    )

    private val staffDescription = "Staff, press where the note goes and let go"
    private val hearStaffDescription = "Staff, press where the next note goes and let go"

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

    /** The screen at this [fontScale], as `show` has it but for the text size; the question unless [initial] says otherwise. */
    private fun showAtTextSize(fontScale: Float, locale: Locale, initial: NotesRound = asking()) {
        state = mutableStateOf(initial)
        compose.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(fontScale)) {
                LocaleProvider(locale) {
                    NotesContent(state = state.value, actions = NotesActions())
                }
            }
        }
    }

    /**
     * The screen in a window of exactly [size] (changed later through [window]), as `show` has it but for the
     * room: the test's own window is the whole emulator, with none of the bars a phone's tab has to share its
     * height with. At [fontScale] and in [locale] when a test asks.
     */
    private fun showInWindow(size: DpSize, initial: NotesRound, fontScale: Float = 1f, locale: Locale = Locale.ENGLISH) {
        state = mutableStateOf(initial)
        window = mutableStateOf(size)
        compose.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(window.value) then DeviceConfigurationOverride.FontScale(fontScale),
            ) {
                LocaleProvider(locale) {
                    NotesContent(state = state.value, actions = NotesActions())
                }
            }
        }
    }

    private fun layoutOf(word: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(word, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }

    private fun tagged(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot()

    /**
     * Everything stays on the button at this [fontScale], as on the sideways one: the word one whole line
     * 6 dp from the outline, less a pixel for rounding, and the backpack and dots inside it. "Odaberi" used
     * to break inside the word, then, only shrunk, lost its last letter under the outline, and the backpack
     * grew over the edge and pushed the dots out.
     */
    private fun assertThePickButtonHolds(fontScale: Float, locale: Locale, word: String, name: String) {
        showAtTextSize(fontScale, locale)
        val layout = layoutOf(word)
        val says = "at $fontScale x: \"$word\" is ${layout.lineCount} lines"
        assertEquals("$says, it broke", 1, layout.lineCount)
        assertTrue("$says, wider than its ${layout.size.width} px", layout.multiParagraph.maxIntrinsicWidth <= layout.size.width + 1)
        val pick = compose.onNodeWithContentDescription(name).getBoundsInRoot()
        val box = compose.onNodeWithText(word, useUnmergedTree = true).getBoundsInRoot()
        val backpack = tagged("pick_backpack")
        val dots = tagged("pick_dots")
        val least = 6f - 1f / compose.density.density
        assertTrue("$says, it ends ${pick.right - box.right} inside Pick", (pick.right - box.right).value >= least)
        assertTrue("$says, the backpack starts ${backpack.left - pick.left} inside Pick", (backpack.left - pick.left).value >= least)
        assertTrue("$says, the dots end ${pick.bottom - dots.bottom} inside Pick", pick.bottom - dots.bottom >= OutlineWidth)
    }

    @Test
    fun the_pick_button_holds_at_normal_text() = assertThePickButtonHolds(1f, Locale("hr"), "Odaberi", "Odaberi note")

    @Test
    fun the_pick_button_holds_at_a_larger_text() = assertThePickButtonHolds(1.5f, Locale("hr"), "Odaberi", "Odaberi note")

    @Test
    fun the_pick_button_holds_at_the_largest_text() = assertThePickButtonHolds(2f, Locale("hr"), "Odaberi", "Odaberi note")

    @Test
    fun the_pick_button_holds_at_the_largest_text_in_english() = assertThePickButtonHolds(2f, Locale.ENGLISH, "Pick", "Choose notes")

    /**
     * The fit is for large text only: at the phone's normal text the word is as wide as the plain word in
     * the same style. By width, as `layoutInput.style` keeps the size it was given whatever `autoSize` chose.
     */
    @Test
    fun the_pick_word_keeps_its_size_at_normal_text() {
        compose.setContent {
            LocaleProvider(Locale("hr")) {
                Column {
                    Text("Odaberi", style = MaterialTheme.typography.titleMedium)
                    NotesContent(state = asking(), actions = NotesActions())
                }
            }
        }
        val (plain, onPick) = compose.onAllNodesWithText("Odaberi", useUnmergedTree = true).fetchSemanticsNodes().map { it.size.width }
        assertEquals(plain, onPick)
    }

    @Test
    fun the_two_games_are_pills_on_top_and_the_chosen_one_is_selected() {
        show(asking(60))
        val find = compose.onNodeWithText("🔎 Find")
        val hear = compose.onNodeWithText("👂 Hear")
        find.assertIsSelected()
        hear.assertIsNotSelected()
        val pills = hear.getBoundsInRoot()
        val note = compose.onNodeWithText("C4").getBoundsInRoot()
        assertTrue("the pills $pills should sit above the note $note", pills.bottom <= note.top)
        compose.onNodeWithContentDescription("Hear it again").assertDoesNotExist() // Find asks for the note itself
        hear.performClick()
        assertEquals(listOf(NotesGame.HEAR), games)

        compose.runOnIdle { state.value = hearing() }
        hear.assertIsSelected()
        find.assertIsNotSelected()
    }

    @Test
    fun hear_shows_the_speaker_bubble_in_place_of_the_note() {
        show(hearing())
        val bubble = compose.onNodeWithContentDescription("Hear it again")
        bubble.assertIsDisplayed()
        compose.onNodeWithText("D4").assertDoesNotExist() // the answer is not on screen
        bubble.performClick()
        assertEquals(1, hearAgainRequests)
    }

    @Test
    fun a_finger_on_the_staff_is_answering_and_not_asking_to_hear() {
        show(hearing())
        val staff = compose.onNodeWithContentDescription(hearStaffDescription)
        compose.onNodeWithContentDescription("Hear it again").assertIsEnabled()
        staff.performTouchInput { down(center) }
        compose.onNodeWithContentDescription("Hear it again").assertIsNotEnabled()
        staff.performTouchInput { up() }
        compose.onNodeWithContentDescription("Hear it again").assertIsEnabled()
    }

    /**
     * The place under a finger belongs to the question: when the question goes with the finger still down (the
     * round over here; a second finger on Pick would do the same), it goes too, so the next question does not
     * come back with the speaker bubble waiting and "Slide, let go" on it. Nor is the note under the finger
     * answered: the staff going away cancels the touch, and a cancelled touch answers nothing.
     */
    @Test
    fun a_finger_left_down_is_forgotten_when_the_question_goes() {
        show(hearing())
        compose.onNodeWithContentDescription(hearStaffDescription).performTouchInput { down(center) }
        compose.onNodeWithText("👆 Slide, let go").assertIsDisplayed()
        compose.runOnIdle { state.value = over(8) }
        compose.waitForIdle()
        compose.runOnIdle { state.value = hearing() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Hear it again").assertIsEnabled()
        compose.onNodeWithText("👆 Slide, let go").assertDoesNotExist()
        assertTrue("the question went with the finger down, so nothing is answered: $tapped", tapped.isEmpty())
    }

    /**
     * A touch the system takes back (a cancel: the shade pulled down, the back gesture, the staff going away) is
     * not a lift: nothing is answered, and the place goes, so the speaker bubble is ready again.
     */
    @Test
    fun a_cancelled_touch_answers_nothing() {
        show(hearing())
        val staff = compose.onNodeWithContentDescription(hearStaffDescription)
        staff.performTouchInput { down(center) }
        compose.onNodeWithContentDescription("Hear it again").assertIsNotEnabled()
        staff.performTouchInput { cancel() }
        compose.waitForIdle()
        assertTrue("a cancelled touch answered $tapped", tapped.isEmpty())
        compose.onNodeWithContentDescription("Hear it again").assertIsEnabled()
        compose.onNodeWithText("👆 Slide, let go").assertDoesNotExist()
    }

    /** A plain touch, down and up on one place with no slide, answers that place. */
    @Test
    fun a_plain_down_and_up_answers_the_place() {
        show(asking(62))
        val staff = compose.onNodeWithContentDescription(staffDescription)
        val d4 = compose.onNodeWithContentDescription("Staff position 2").getBoundsInRoot()
        val top = staff.getBoundsInRoot().top
        val y = with(compose.density) { ((d4.top + d4.bottom) / 2 - top).toPx() }
        val x = with(compose.density) { 100.dp.toPx() }
        staff.performTouchInput {
            down(Offset(x, y))
            up()
        }
        assertEquals(listOf(62), tapped)
    }

    /**
     * The staff locks under a finger that is already down (the phrase starts playing mid-slide): the touch ends
     * there, and the place under it with it, so the speaker bubble does not wait for a lift that answers nothing
     * and "Slide, let go" does not come back when the phrase has played.
     */
    @Test
    fun a_lock_in_the_middle_of_a_slide_lets_go_of_the_place() {
        show(hearing())
        compose.onNodeWithContentDescription(hearStaffDescription).performTouchInput { down(center) }
        compose.onNodeWithContentDescription("Hear it again").assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(phrasePlaying = true) }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Hear it again").assertIsEnabled()
        compose.runOnIdle { state.value = state.value.copy(phrasePlaying = false) }
        compose.waitForIdle()
        compose.onNodeWithText("👆 Slide, let go").assertDoesNotExist()
        compose.onNodeWithContentDescription("Hear it again").assertIsEnabled()
    }

    /**
     * A phrase is asked for as it comes on screen and only then: the staff shown asks once; a new phrase drawn
     * while the picker covers the staff (a note toggled there, or the other game's pill) asks nothing, and the
     * picker closing on it asks, so it plays as the child sees it. The ViewModel plays each phrase once, so an
     * ask for one already heard (the picker closed on an unchanged phrase) plays nothing.
     */
    @Test
    fun a_phrase_is_asked_for_only_while_the_staff_is_on_screen() {
        show(hearing())
        compose.waitForIdle()
        assertEquals("the question shown", 1, phraseShownRequests)
        compose.onNodeWithContentDescription("Choose notes").performClick()
        compose.waitForIdle()
        assertEquals("the picker opened", 1, phraseShownRequests)
        compose.runOnIdle { state.value = state.value.copy(phraseId = state.value.phraseId + 1) }
        compose.waitForIdle()
        assertEquals("a new phrase under the picker", 1, phraseShownRequests)
        compose.onNodeWithText("OK").performClick()
        compose.waitForIdle()
        assertEquals("the picker closed on the new phrase", 2, phraseShownRequests)
    }

    /** The last note of a phrase is placed: "That's it!" while the phrase plays back as its feedback, not "Listen". */
    @Test
    fun the_last_note_of_a_phrase_says_thats_it_while_the_phrase_plays_back() {
        show(hearing().copy(solved = true, phrasePlaying = true, results = listOf(true), taps = 1, streak = 1))
        compose.onNodeWithText("🎉 That's it!").assertIsDisplayed()
        compose.onNodeWithText("👂 Listen").assertDoesNotExist()
    }

    /**
     * The given note and the ones placed so far sit on the staff in their letters' colours, side by side
     * and not on top of each other: E and F of E F G A with two placed, G (the note to place) not yet.
     * The centre of a head is its fill, whatever line or ledger line runs through it.
     */
    @Test
    fun hear_shows_the_given_and_placed_notes_on_the_staff() {
        needsDevice()
        show(asking(67).copy(game = NotesGame.HEAR, phrase = listOf(64, 65, 67, 69), placed = 2))
        val image = compose.onNodeWithContentDescription(hearStaffDescription).captureToImage().toPixelMap()
        val visible = StaffNotes.visiblePositions(StaffNotes.DEFAULT_PRACTICE)
        val pitch = image.height / (visible.count() + 2f)
        fun colourAt(column: Float, midi: Int): Int {
            val y = (visible.last - StaffNotes.positionOf(midi) + 1.5f) * pitch
            return image[(image.width * column).toInt(), y.toInt()].toArgb()
        }
        assertEquals("E, the given note, at the first column", NoteLetterColors[2].toArgb(), colourAt(0.26f, 64))
        assertEquals("F, placed, at the second column", NoteLetterColors[3].toArgb(), colourAt(0.42f, 65))
        assertTrue("G is still to place, so its column is bare", colourAt(0.58f, 67) != NoteLetterColors[4].toArgb())
        assertTrue("A is not asked yet, so its column is bare", colourAt(0.74f, 69) != NoteLetterColors[5].toArgb())
    }

    @Test
    fun the_staff_takes_no_answer_while_the_phrase_plays() {
        show(hearing().copy(phrasePlaying = true))
        val staff = compose.onNodeWithContentDescription(hearStaffDescription)
        val c4 = compose.onNodeWithContentDescription("Staff position 1").getBoundsInRoot()
        val d4 = compose.onNodeWithContentDescription("Staff position 2").getBoundsInRoot()
        val top = staff.getBoundsInRoot().top
        val x = with(compose.density) { 100.dp.toPx() }
        fun yOf(place: DpRect) = with(compose.density) { ((place.top + place.bottom) / 2 - top).toPx() }
        fun slide(whileDown: () -> Unit = {}) {
            staff.performTouchInput { down(Offset(x, yOf(d4))) }
            whileDown()
            staff.performTouchInput { moveTo(Offset(x, yOf(c4))) }
            staff.performTouchInput { up() }
        }

        slide {
            // With the finger down: a locked staff places nothing under it, so the bubble does not wait for it.
            compose.onNodeWithText("👆 Slide, let go").assertDoesNotExist()
            compose.onNodeWithContentDescription("Hear it again").assertIsEnabled()
        }
        assertTrue("the phrase is sounding, so nothing is answered: $tapped", tapped.isEmpty())

        compose.runOnIdle { state.value = state.value.copy(phrasePlaying = false) }
        slide()
        assertEquals(listOf(60), tapped)
    }

    @Test
    fun the_hear_staff_has_its_own_description() {
        show(hearing())
        compose.onNodeWithContentDescription(hearStaffDescription).assertExists()
        compose.onNodeWithContentDescription(staffDescription).assertDoesNotExist()

        compose.runOnIdle { state.value = asking(60) }
        compose.onNodeWithContentDescription(staffDescription).assertExists()
        compose.onNodeWithContentDescription(hearStaffDescription).assertDoesNotExist()
    }

    @Test
    fun the_pills_stay_while_choosing_notes_and_on_the_summary() {
        show(asking(60))
        compose.onNodeWithContentDescription("Choose notes").performClick()
        compose.onNodeWithText("Which notes?").assertIsDisplayed()
        compose.onNodeWithText("🔎 Find").assertIsDisplayed()
        compose.onNodeWithText("👂 Hear").assertIsDisplayed()

        compose.onNodeWithText("OK").performClick()
        compose.runOnIdle { state.value = over(8) }
        compose.onNodeWithText("🌟 Great job!").assertIsDisplayed()
        compose.onNodeWithText("🔎 Find").assertIsDisplayed()
        compose.onNodeWithText("👂 Hear").assertIsDisplayed()
    }

    /**
     * [word] stands whole on one line in the hero row at twice the text: not wider than its box, not taller
     * than the paragraph's own box, and the box no taller than the row, less a pixel for rounding. The
     * row's fixed height and `maxLines` would pass the last two by themselves.
     */
    private fun assertTheHeroWordFits(word: String) {
        val layout = layoutOf(word)
        val says = "at twice the text \"$word\" is ${layout.lineCount} lines"
        assertEquals("$says, it broke", 1, layout.lineCount)
        assertTrue(
            "$says, ${layout.multiParagraph.maxIntrinsicWidth} px wide, wider than its ${layout.size.width} px",
            layout.multiParagraph.maxIntrinsicWidth <= layout.size.width + 1,
        )
        assertTrue("$says, taller than its ${layout.size.height} px", layout.multiParagraph.height <= layout.size.height + 1)
        val box = compose.onNodeWithText(word, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val row = 76.dp + (1f / compose.density.density).dp
        assertTrue("$says, its box $box is taller than the row", box.height <= row)
    }

    /**
     * Every headline the summary can have ([headlines], best first: scores 10, 8, 5 and 2, one per tier of
     * `headlineFor`) and then the picker's [title], opened with [pick], each whole in the hero row at twice the text:
     * on the emulator's 411 dp, or in a window [width] wide (360 for the narrowest phone).
     */
    private fun assertTheHeroWordsFit(locale: Locale, headlines: List<String>, title: String, pick: String, width: Dp? = null) {
        if (width == null) showAtTextSize(2f, locale, over(10)) else showInWindow(DpSize(width, 800.dp), over(10), fontScale = 2f, locale = locale)
        listOf(10, 8, 5, 2).zip(headlines).forEach { (score, headline) ->
            compose.runOnIdle { state.value = over(score) }
            compose.waitForIdle()
            assertTheHeroWordFits(headline)
        }
        compose.onNodeWithContentDescription(pick).performClick()
        assertTheHeroWordFits(title)
    }

    @Test
    fun the_picker_title_and_the_headline_fit_the_hero_row_at_twice_the_text() = assertTheHeroWordsFit(
        Locale.ENGLISH,
        listOf("🏆 Amazing!", "🌟 Great job!", "👍 Good work!", "💪 Keep practising!"),
        title = "Which notes?",
        pick = "Choose notes",
    )

    @Test
    fun the_picker_title_and_the_headline_fit_the_hero_row_at_twice_the_text_in_croatian() = assertTheHeroWordsFit(
        Locale("hr"),
        listOf("🏆 Nevjerojatno!", "🌟 Odlično!", "👍 Dobro!", "💪 Vježbaj dalje!"),
        title = "Koje note?",
        pick = "Odaberi note",
    )

    @Test
    fun the_picker_title_and_the_headline_fit_a_360_dp_phone_at_twice_the_text() = assertTheHeroWordsFit(
        Locale.ENGLISH,
        listOf("🏆 Amazing!", "🌟 Great job!", "👍 Good work!", "💪 Keep practising!"),
        title = "Which notes?",
        pick = "Choose notes",
        width = 360.dp,
    )

    @Test
    fun the_picker_title_and_the_headline_fit_a_360_dp_phone_at_twice_the_text_in_croatian() = assertTheHeroWordsFit(
        Locale("hr"),
        listOf("🏆 Nevjerojatno!", "🌟 Odlično!", "👍 Dobro!", "💪 Vježbaj dalje!"),
        title = "Koje note?",
        pick = "Odaberi note",
        width = 360.dp,
    )

    /**
     * The two game pills keep their words whole at twice the text, on a 360 dp phone and on a 411 dp one: one
     * line, not cut short with an ellipsis, not wider than the room they are given.
     */
    private fun assertThePillWordsHold(locale: Locale, words: List<String>) {
        showInWindow(DpSize(360.dp, 800.dp), asking(), fontScale = 2f, locale = locale)
        listOf(360, 411).forEach { width ->
            compose.runOnIdle { window.value = DpSize(width.dp, 800.dp) }
            compose.waitForIdle()
            words.forEach { word ->
                val layout = layoutOf(word)
                val says = "$width dp wide at twice the text, \"$word\""
                assertEquals("$says is ${layout.lineCount} lines", 1, layout.lineCount)
                assertFalse("$says is cut short", layout.isLineEllipsized(0))
                assertTrue("$says is wider than its ${layout.size.width} px", layout.multiParagraph.maxIntrinsicWidth <= layout.size.width + 1)
            }
        }
    }

    @Test
    fun the_game_pills_keep_their_words_whole_at_twice_the_text() = assertThePillWordsHold(Locale.ENGLISH, listOf("🔎 Find", "👂 Hear"))

    @Test
    fun the_game_pills_keep_their_words_whole_at_twice_the_text_in_croatian() =
        assertThePillWordsHold(Locale("hr"), listOf("🔎 Nađi", "👂 Slušaj"))

    @Test
    fun the_feedback_line_says_listen_while_the_phrase_plays() {
        show(hearing().copy(phrasePlaying = true))
        compose.onNodeWithText("👂 Listen").assertIsDisplayed()

        // The staff is locked then, so nothing else is said over it.
        compose.runOnIdle { state.value = state.value.copy(wrongTaps = setOf(60), lastWrongTap = 60, taps = 1) }
        compose.onNodeWithText("👂 Listen").assertIsDisplayed()
        compose.onNodeWithText("🙈 Try again").assertDoesNotExist()

        compose.runOnIdle { state.value = state.value.copy(phrasePlaying = false) }
        compose.onNodeWithText("🙈 Try again").assertIsDisplayed()
        compose.onNodeWithText("👂 Listen").assertDoesNotExist()
    }

    /**
     * Hear's line under the staff asks while nothing else is said: "Find the next note" at rest, "Listen" while
     * the phrase plays (the staff takes no answer then), nothing in Find. In Croatian "Slušaj" is also the Hear
     * pill's word, so the line is counted by what the pill alone already gives, not by there being one node.
     */
    private fun assertTheHearPromptAsksForTheNextNote(locale: Locale, prompt: String, listen: String) {
        show(hearing(), locale)
        compose.onNodeWithText(prompt).assertIsDisplayed()
        val pill = compose.onAllNodesWithText(listen).fetchSemanticsNodes().size

        compose.runOnIdle { state.value = state.value.copy(phrasePlaying = true) }
        compose.onAllNodesWithText(listen).assertCountEquals(pill + 1)
        compose.onNodeWithText(prompt).assertDoesNotExist()

        compose.runOnIdle { state.value = asking(60) }
        compose.onNodeWithText(prompt).assertDoesNotExist()
        compose.onAllNodesWithText(listen).assertCountEquals(pill)
    }

    @Test
    fun hear_asks_for_the_next_note_while_waiting() =
        assertTheHearPromptAsksForTheNextNote(Locale.ENGLISH, "❓ Find the next note", "👂 Listen")

    @Test
    fun hear_asks_for_the_next_note_while_waiting_in_croatian() =
        assertTheHearPromptAsksForTheNextNote(Locale("hr"), "❓ Nađi sljedeću notu", "👂 Slušaj")

    /**
     * The staff's pixels read by the staff's own formula (the same as `hear_shows_the_given_and_placed_notes_on_the_staff`):
     * a place is the height over the visible places plus two, and the middle of a place is
     * `(visible.last - position + 1.5)` pitches from the top. The default notes are chosen, so the places are 0 to 11,
     * the odd ones tinted and the even ones (the lines among them) bare. The light theme is assumed, as the rest of the file does.
     */
    private class StaffPixels(private val image: PixelMap, val pxPerDp: Float) {
        private val visible = StaffNotes.visiblePositions(StaffNotes.DEFAULT_PRACTICE)
        val pitch = image.height / (visible.count() + 2f)

        /** The staff's height in pitches: the visible places and the margin over and under them. */
        val totalPitches = visible.count() + 2f

        /** The pixel at ([x], [y]). */
        operator fun get(x: Int, y: Int): Color = image[x, y]

        /** The pixel [dy] pitches under the middle of [position]'s place, at [x] pixels from the left. */
        fun atX(x: Float, position: Int, dy: Float = 0f): Color = image[x.toInt(), middleOf(position, dy).toInt()]

        /** The row, in pixels, [dy] pitches under the middle of [position]'s place. */
        fun middleOf(position: Int, dy: Float = 0f): Float = (visible.last - position + 1.5f + dy) * pitch

        /** The bottom edge, in pixels, of a slot: the bottom of the lowest place. */
        val slotBottom = (visible.last - visible.first + 2f) * pitch

        /** The pixel [dx] pitches right of [column] of the width, [dy] pitches under the middle of [position]'s place. */
        fun at(column: Float, position: Int, dy: Float = 0f, dx: Float = 0f): Color =
            image[(image.width * column + dx * pitch).toInt(), ((visible.last - position + 1.5f + dy) * pitch).toInt()]

        val width = image.width

        /** Whether any pixel with x in [xs], from [top] to [bottom] pitches from the staff's top, is [match]. */
        fun anyIn(xs: IntRange, top: Float, bottom: Float, match: (Color) -> Boolean): Boolean =
            (((top * pitch).toInt())..((bottom * pitch).toInt())).any { y -> xs.any { x -> match(image[x, y]) } }

        /** Whether any pixel within half a pitch either side of [column], from [top] to [bottom] pitches from the staff's top, is [match]. */
        fun any(column: Float, top: Float, bottom: Float, match: (Color) -> Boolean): Boolean =
            anyIn((width * column - pitch / 2).toInt()..(width * column + pitch / 2).toInt(), top, bottom, match)

        /** A slot's half width in pixels, as `NoteStaff` has it: a pitch, or 0.42 of a column step where that is less. */
        val slotHalf = minOf(pitch, width * 0.16f * 0.42f)
    }

    private fun staffPixels(description: String = hearStaffDescription): StaffPixels {
        val staff = compose.onNodeWithContentDescription(description)
        val image = staff.captureToImage().toPixelMap()
        // The pixels per dp of the capture: a forced size may scale the density, so it is not the device's.
        return StaffPixels(image, image.width / staff.getBoundsInRoot().width.value)
    }

    /** Yellow over white or over the tint: the blue falls well under the red and the green. */
    private fun isYellowish(c: Color) = c.blue < c.red - 0.1f && c.blue < c.green - 0.1f

    /** The ❓'s own red, strong. */
    private fun isRed(c: Color) = c.red > 0.6f && c.green < 0.4f && c.blue < 0.4f

    /** The ❓ faded over white: pink, the red clear of the green and the blue. */
    private fun isReddish(c: Color) = c.red > c.green + 0.15f && c.red > c.blue + 0.15f

    /**
     * What a child sees of the note still to find (the first child test, 2026-11-09: "why first one is marked and
     * second is not"): E F G A with the E given is a pale yellow capsule in the second column, from the staff's top
     * place to its bottom one, with a ❓ in it, and fainter ones, no fill and a pale ❓, for the notes after it. The spot
     * sampled is the white half of the B line's place, clear of the line, the ❓ and every head.
     */
    @Test
    fun the_note_to_find_has_a_slot_and_later_notes_fainter_ones() {
        needsDevice()
        show(asking(65).copy(game = NotesGame.HEAR, phrase = listOf(64, 65, 67, 69), placed = 1))
        val staff = staffPixels()
        val bare = staff.at(0.9f, 8, 0.3f)
        assertFalse("the staff there is plain: $bare", isYellowish(bare))

        assertTrue("inside the slot of the note to find: ${staff.at(0.42f, 8, 0.3f)}", isYellowish(staff.at(0.42f, 8, 0.3f)))
        assertEquals("the given note's column, away from its head, is the plain staff", bare, staff.at(0.26f, 8, 0.3f))
        assertEquals("inside a later slot there is no fill", bare, staff.at(0.74f, 8, 0.3f))

        // From the staff's top place to its bottom one, and no further: the margins above and below stay plain.
        assertTrue("the top place is inside the slot", isYellowish(staff.at(0.42f, 11)))
        assertTrue("the bottom place is inside the slot", isYellowish(staff.at(0.42f, 1)))
        assertFalse("the margin above the staff is plain", isYellowish(staff.at(0.42f, 11, -0.9f)))
        assertFalse("the margin below the staff is plain", isYellowish(staff.at(0.42f, 0, 0.9f)))

        // The ❓ in the middle: strong red in the slot of the note to find, faded in a later one.
        assertTrue("the slot of the note to find has a ❓", staff.any(0.42f, 6f, 8f, ::isRed))
        assertTrue("a later slot has a ❓", staff.any(0.74f, 6f, 8f, ::isReddish))
        assertFalse("a later slot's ❓ is faded, not red", staff.any(0.74f, 6f, 8f, ::isRed))
    }

    /**
     * A miss puts its cross, and the arrow, into the slot's place, so the ❓ steps aside: the current slot's ❓ and the later
     * ones' are not drawn while a wrong tap stands, and the capsules stay. The wrong place is a low one (D4) so the
     * cross and the arrow are nowhere near the ❓'s rows.
     */
    @Test
    fun a_miss_takes_the_question_marks_off_the_slots_and_keeps_the_capsules() {
        needsDevice()
        show(asking(65).copy(game = NotesGame.HEAR, phrase = listOf(64, 65, 67, 69), placed = 1))
        val rest = staffPixels()
        assertTrue("the slot of the note to find has its ❓ at rest", rest.any(0.42f, 6f, 8f, ::isRed))
        assertTrue("a later slot has its ❓ at rest", rest.any(0.58f, 6f, 8f, ::isReddish))
        assertTrue("the last slot has its ❓ at rest", rest.any(0.74f, 6f, 8f, ::isReddish))

        compose.runOnIdle { state.value = state.value.copy(wrongTaps = setOf(62), lastWrongTap = 62, taps = 1) }
        compose.waitForIdle()
        val missed = staffPixels()
        assertFalse("no ❓ in the slot of the note to find after a miss", missed.any(0.42f, 6f, 8f, ::isRed))
        assertFalse("no ❓ in a later slot after a miss", missed.any(0.58f, 6f, 8f, ::isReddish))
        assertFalse("no ❓ in the last slot after a miss", missed.any(0.74f, 6f, 8f, ::isReddish))
        assertTrue("the capsule of the note to find stays: ${missed.at(0.42f, 8, 0.3f)}", isYellowish(missed.at(0.42f, 8, 0.3f)))
    }

    /**
     * At night the slot of the note to find is outlined in the text colour (white), as the staff's lines are, and not in
     * the outline colour, which is almost the card's own navy there: its left edge has bright pixels in a stretch of rows
     * clear of the lines.
     */
    @Test
    fun the_slot_of_the_note_to_find_has_a_bright_outline_at_night() {
        needsDevice()
        show(hearing(), dark = true)
        val staff = staffPixels()
        val edge = staff.width * 0.58f - staff.slotHalf
        assertTrue(
            "the left edge of the slot stands out on the dark card",
            staff.anyIn((edge - 6).toInt()..(edge + 6).toInt(), 3.0f, 4.2f) { it.luminance() > 0.6f },
        )
    }

    /**
     * The dashed ledger stub of the far place (C4's, here) that stands in the column of the note to find stays inside the
     * slot: it used to poke out at the capsule's rounded end, where the capsule is narrower than its half width. The
     * window is wide and short enough for a slot as wide as a place (a pitch to each side), where the end is most rounded.
     * The stub's start is the sample: a dashed line always begins with a dash, so the left end is there whatever the
     * window, where the right end's dash phase depends on the length (it can end in a gap and show nothing). From the
     * stub's start to just clear of the capsule's outline, row by row over the stub's thickness, nothing dark may stand
     * (the outline's reach is worked out for each row: it leans out where the end curves). The given note is E4, whose
     * head has no ledger line, so nothing else is dark there. The stub itself must still be drawn, inside the slot.
     */
    @Test
    fun the_far_places_stub_stays_inside_the_slot() {
        needsDevice()
        showInWindow(DpSize(640.dp, 760.dp), asking(62).copy(game = NotesGame.HEAR, phrase = listOf(64, 62), placed = 1))
        compose.waitForIdle()
        val staff = staffPixels()
        val centre = staff.width * 0.58f
        val r = staff.slotHalf
        assertTrue("this window is too small for a slot as wide as a place: $r px against a pitch of ${staff.pitch}", r >= staff.pitch - 1f)
        val stroke = 1.5f * staff.pxPerDp
        val rows = (staff.middleOf(0) - stroke + 1f).toInt()..(staff.middleOf(0) + stroke - 1f).toInt()

        var examined = 0
        for (y in rows) {
            val d = staff.slotBottom - y
            // Where the outline's outer edge is on this row: the circle of the rounded end, a stroke wider.
            val outer = if (d < r) sqrt((r + stroke) * (r + stroke) - (r - d) * (r - d)) else r + stroke
            val poke = (centre - staff.pitch).toInt()..(centre - outer - 2f).toInt()
            examined += poke.count()
            assertFalse(
                "the stub pokes out of the slot at row $y, $d px over the capsule's bottom",
                poke.any { x -> staff[x, y].luminance() < 0.7f },
            )
        }
        assertTrue("this window is too small to show a stub poking out: $examined px looked at", examined >= 10)

        val middle = (centre - r / 2).toInt()..(centre + r / 2).toInt()
        assertTrue("the stub is still drawn inside the slot", rows.any { y -> middle.any { x -> staff[x, y].luminance() < 0.7f } })
    }

    /**
     * Hear's prompt in the one line of the upright screen at twice the text, on the narrowest phone and the one the
     * emulator is: whole, not cut at the end (the line is as wide as its words, not wider than the room it has).
     */
    private fun assertTheHearPromptHoldsAtTwiceTheText(locale: Locale, prompt: String) {
        showInWindow(DpSize(360.dp, 800.dp), hearing(), fontScale = 2f, locale = locale)
        listOf(360, 411).forEach { width ->
            compose.runOnIdle { window.value = DpSize(width.dp, 800.dp) }
            compose.waitForIdle()
            val layout = layoutOf(prompt)
            val says = "$width dp wide at twice the text, \"$prompt\""
            assertEquals("$says is ${layout.lineCount} lines", 1, layout.lineCount)
            assertFalse("$says is cut short", layout.isLineEllipsized(0))
            assertTrue(
                "$says is ${layout.multiParagraph.maxIntrinsicWidth} px wide, wider than its ${layout.size.width} px",
                layout.multiParagraph.maxIntrinsicWidth <= layout.size.width + 1,
            )
        }
    }

    @Test
    fun the_hear_prompt_holds_at_twice_the_text() = assertTheHearPromptHoldsAtTwiceTheText(Locale.ENGLISH, "❓ Find the next note")

    @Test
    fun the_hear_prompt_holds_at_twice_the_text_in_croatian() = assertTheHearPromptHoldsAtTwiceTheText(Locale("hr"), "❓ Nađi sljedeću notu")

    /**
     * The sound lights its note: the given head gets a halo while it sounds (0.6 of a pitch above its middle, outside
     * the head and inside the halo, is yellow, and only then), the slot of the note after it turns a stronger yellow
     * while that one sounds, and the halo moves on with the sound. This window is the whole emulator, taller than a
     * phone with its bars: a pitch is 42 dp and the next capsule's edge 31 dp from the head, so the halo is capped
     * there (`the_halo_stops_at_the_next_capsule`) and the spot is within the cap, not at 0.8 of a pitch as it was
     * while the halo overran the capsule.
     */
    @Test
    fun the_note_sounding_lights_up() {
        needsDevice()
        show(asking(64).copy(game = NotesGame.HEAR, phrase = listOf(62, 64), placed = 1))
        val rest = staffPixels()
        assertFalse("no halo at rest: ${rest.at(0.42f, 1, dy = -0.6f)}", isYellowish(rest.at(0.42f, 1, dy = -0.6f)))
        val restBlue = rest.at(0.58f, 8, 0.3f).blue
        assertTrue("the slot has its pale yellow at rest", isYellowish(rest.at(0.58f, 8, 0.3f)))

        compose.runOnIdle { state.value = state.value.copy(phrasePlaying = true, sounding = 0) }
        compose.waitForIdle()
        val first = staffPixels()
        assertTrue("the given note sounds, so its halo is yellow: ${first.at(0.42f, 1, dy = -0.6f)}", isYellowish(first.at(0.42f, 1, dy = -0.6f)))
        assertEquals("the slot is at rest while the first note sounds", restBlue, first.at(0.58f, 8, 0.3f).blue, 0.02f)

        compose.runOnIdle { state.value = state.value.copy(sounding = 1) }
        compose.waitForIdle()
        val second = staffPixels()
        assertFalse("the halo went on with the sound", isYellowish(second.at(0.42f, 1, dy = -0.6f)))
        val lit = second.at(0.58f, 8, 0.3f)
        assertTrue("the slot of the second note is a stronger yellow: blue $restBlue at rest, ${lit.blue} lit", lit.blue < restBlue - 0.1f)
    }

    /**
     * The halo is a pitch round its head, but never reaches the next capsule: on a narrow staff the next column is
     * nearer than a pitch plus the slot's half width, and it met the capsule's edge. F G A B with the F given and
     * sounding, the G's slot next to it: a pixel just inside that slot's near edge, in the F's row, is the slot's own
     * pale yellow, as its mirror just inside the far edge is (the row is clear of the ❓). The halo still shows round
     * the F, above its head.
     */
    @Test
    fun the_halo_stops_at_the_next_capsule() {
        needsDevice()
        showInWindow(
            DpSize(320.dp, 900.dp),
            asking(67).copy(game = NotesGame.HEAR, phrase = listOf(65, 67, 69, 71), placed = 1, sounding = 0, phrasePlaying = true),
        )
        compose.waitForIdle()
        val staff = staffPixels()
        val room = staff.width * 0.16f - staff.slotHalf // from the F's middle to the capsule's near edge
        assertTrue(
            "this window is too wide for a halo to reach the capsule: room $room px, pitch ${staff.pitch}",
            staff.pitch - room >= 0.25f * staff.pitch,
        )
        val capsule = staff.width * 0.42f
        val depth = 0.12f * staff.pitch
        val near = staff.atX(capsule - staff.slotHalf + depth, 3)
        val far = staff.atX(capsule + staff.slotHalf - depth, 3)
        assertEquals("red inside the near edge: $near, the far edge $far", far.red, near.red, 0.03f)
        assertEquals("green inside the near edge: $near, the far edge $far", far.green, near.green, 0.03f)
        assertEquals("blue inside the near edge: $near, the far edge $far", far.blue, near.blue, 0.03f)
        assertTrue("the slot has its pale yellow there: $far", isYellowish(far))

        assertTrue("the halo still shows round the F: ${staff.at(0.26f, 3, dy = -0.5f)}", isYellowish(staff.at(0.26f, 3, dy = -0.5f)))
    }

    /**
     * Where there is room the halo is still a whole pitch: on a phone with its bars (a shorter staff, so a smaller
     * pitch) the next capsule is further than a pitch from the head, and 0.9 of a pitch to the side of the D, outside
     * the head and just inside a pitch, is yellow while it sounds.
     */
    @Test
    fun the_halo_is_a_whole_pitch_where_there_is_room() {
        needsDevice()
        showInWindow(
            DpSize(411.dp, 640.dp),
            asking(64).copy(game = NotesGame.HEAR, phrase = listOf(62, 64), placed = 1, sounding = 0, phrasePlaying = true),
        )
        compose.waitForIdle()
        val staff = staffPixels()
        val room = staff.width * 0.16f - staff.slotHalf
        assertTrue("this window is too tall for a halo with room: room $room px, pitch ${staff.pitch}", room >= 1.1f * staff.pitch)
        val side = staff.at(0.42f, 1, dx = -0.9f)
        assertTrue("0.9 of a pitch from the sounding head is inside its halo: $side", isYellowish(side))
        assertFalse("1.15 pitches from it is outside: ${staff.at(0.42f, 1, dx = -1.15f)}", isYellowish(staff.at(0.42f, 1, dx = -1.15f)))
    }

    /**
     * The playback after the last note is that note's feedback, and it lights the child's own notes as the others: with
     * the phrase solved the found note stands where its slot was, and while it sounds its halo shows (0.6 of a pitch
     * above its middle, outside the head and clear of the ✨, is yellow) and while the first note sounds, or nothing,
     * it does not.
     */
    @Test
    fun the_playback_after_the_last_note_lights_the_found_note() {
        needsDevice()
        val done = hearing().copy(solved = true, phrasePlaying = true) // C4 D4, the last index is 1
        show(done)
        val halo = { staffPixels().at(0.58f, 1, dy = -0.6f) }
        assertFalse("no halo while nothing sounds: ${halo()}", isYellowish(halo()))

        compose.runOnIdle { state.value = done.copy(sounding = 0) }
        compose.waitForIdle()
        assertFalse("no halo on the found note while the first one sounds: ${halo()}", isYellowish(halo()))

        compose.runOnIdle { state.value = done.copy(sounding = 1) }
        compose.waitForIdle()
        assertTrue("the found note sounds, so its halo is yellow: ${halo()}", isYellowish(halo()))

        compose.runOnIdle { state.value = done.copy(sounding = null) }
        compose.waitForIdle()
        assertFalse("the halo goes out with the sound: ${halo()}", isYellowish(halo()))
    }

    /**
     * Find has one note and no phrase: its column holds the slot (the pale yellow capsule and its ❓) while the note
     * is asked, since 2026-11-10, and neither once it is found; it has no halo either way.
     */
    @Test
    fun find_draws_its_slot_until_its_note_is_found() {
        needsDevice()
        // The bare half of the D line's place, far from the G: inside the capsule, clear of the ❓ and the note.
        fun spot() = staffPixels(staffDescription).at(0.5f, 8, 0.3f)
        fun mark() = staffPixels(staffDescription).let { it.any(0.5f, 0f, it.totalPitches - 0.1f, ::isRed) }
        show(asking(67))
        assertTrue("asking: the column is the slot's pale yellow away from the note: ${spot()}", isYellowish(spot()))
        assertTrue("asking: a ❓ in the column", mark())

        compose.runOnIdle { state.value = state.value.copy(solved = true) }
        compose.waitForIdle()
        assertFalse("solved: nothing yellow in the column away from the note: ${spot()}", isYellowish(spot()))
        assertFalse("solved: no ❓ down the column", mark())
    }

    /** A finger down is answering: the line says how, not what, and the ❓ gives way to the see-through note. */
    @Test
    fun the_question_mark_gives_way_to_the_finger() {
        needsDevice()
        show(hearing())
        val prompt = "❓ Find the next note"
        compose.onNodeWithText(prompt).assertIsDisplayed()
        assertTrue("the slot of the note to find has a ❓ at rest", staffPixels().any(0.58f, 6f, 8f, ::isRed))

        val staff = compose.onNodeWithContentDescription(hearStaffDescription)
        staff.performTouchInput { down(center) }
        compose.onNodeWithText("👆 Slide, let go").assertIsDisplayed()
        compose.onNodeWithText(prompt).assertDoesNotExist()
        assertFalse("no ❓ in the slot while a finger is down", staffPixels().any(0.58f, 6f, 8f, ::isRed))

        staff.performTouchInput { up() }
        compose.onNodeWithText(prompt).assertIsDisplayed()
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
    }

    /**
     * Find says how to answer before the first touch, as Hear's line does: a staff that says nothing reads as
     * nothing to do (the first child test of Hear, 2026-11-09, and Find had the same blank line).
     */
    @Test
    fun find_says_how_to_answer_before_the_first_touch() {
        show(asking())
        compose.onNodeWithText("👆 Slide, let go").assertIsDisplayed()
    }

    @Test
    fun find_says_how_to_answer_in_croatian() {
        show(asking(), Locale("hr"))
        compose.onNodeWithText("👆 Povuci i pusti").assertIsDisplayed()
    }

    /** Find's one column has the slot Hear's columns have: the ❓ in the middle of the staff, until a finger is down. */
    @Test
    fun find_has_a_slot_for_the_note_to_find() {
        needsDevice()
        show(asking())
        assertTrue("Find's column has a ❓ at rest", staffPixels(staffDescription).any(0.5f, 6f, 8f, ::isRed))
        compose.onNodeWithContentDescription(staffDescription).performTouchInput { down(center) }
        assertFalse("no ❓ while a finger is down", staffPixels(staffDescription).any(0.5f, 6f, 8f, ::isRed))
    }

    // ---- The phone's Back ----

    @Test
    fun back_closes_the_picker_as_ok_does() {
        show(asking())
        assertFalse("with the staff on screen Back is the phone's", back.taken(compose))
        compose.onNodeWithContentDescription("Choose notes").performClick()
        compose.onNodeWithText("Which notes?").assertIsDisplayed()
        back.press(compose)
        compose.onNodeWithText("Which notes?").assertDoesNotExist()
        compose.onNodeWithContentDescription(staffDescription).assertIsDisplayed()
        assertFalse(back.taken(compose))
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

    /** Every fifth flame sets off a small burst over the staff; the streak in between, or none, does not. */
    @Test
    fun five_flames_set_off_a_burst_and_four_do_not() {
        show(asking(60).copy(streak = 5))
        compose.onNodeWithTag("streak_burst", useUnmergedTree = true).assertExists()

        compose.runOnIdle { state.value = state.value.copy(streak = 4) }
        compose.onNodeWithTag("streak_burst", useUnmergedTree = true).assertDoesNotExist()

        compose.runOnIdle { state.value = state.value.copy(streak = 10) }
        compose.onNodeWithTag("streak_burst", useUnmergedTree = true).assertExists()
    }

    /** The burst is drawn over the staff but takes no touch: the slide that answers goes through to it. */
    @Test
    fun the_burst_does_not_take_the_slide() {
        show(asking(60).copy(streak = 5))
        compose.onNodeWithTag("streak_burst", useUnmergedTree = true).assertExists()
        val c4 = compose.onNodeWithContentDescription("Staff position 1").getBoundsInRoot()
        val d4 = compose.onNodeWithContentDescription("Staff position 2").getBoundsInRoot()
        val staff = compose.onNodeWithContentDescription(staffDescription)
        val top = staff.getBoundsInRoot().top
        val x = with(compose.density) { 100.dp.toPx() }
        fun yOf(place: DpRect) = with(compose.density) { ((place.top + place.bottom) / 2 - top).toPx() }

        staff.performTouchInput { down(Offset(x, yOf(d4))) }
        compose.onNodeWithText("👆 Slide, let go").assertIsDisplayed()
        staff.performTouchInput { moveTo(Offset(x, yOf(c4))) }
        staff.performTouchInput { up() }
        assertEquals(listOf(60), tapped)
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
        needsDevice()
        // D4 answered by mistake right under the asked E4: the cross may not reach into E4's
        // place, or the child has nothing clear to aim at next. Whatever the miss changed in
        // the note column is the cross (the arrow beside it is drawn further right), but for the
        // slot's ❓, which a miss takes away: it stood in the middle of the column, seven of the
        // staff's fourteen pitches down and 1.4 tall, far above D4, and those rows are left out.
        show(asking(64))
        val staff = compose.onNodeWithContentDescription(staffDescription)
        val staffBounds = staff.getBoundsInRoot()
        val before = staff.captureToImage().toPixelMap()
        compose.runOnIdle { state.value = state.value.copy(wrongTaps = setOf(62), lastWrongTap = 62, taps = 1) }
        val after = staff.captureToImage().toPixelMap()
        val columns = (after.width * 0.42f).toInt()..(after.width * 0.58f).toInt()
        val pitch = after.height / 14f
        val mark = (6 * pitch).toInt()..(8 * pitch).toInt()
        val changed = (0 until after.height).filter { y -> y !in mark && columns.any { x -> before[x, y] != after[x, y] } }
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

    /** The notes of the default set and five more: ten, which the summary draws as seven keys and three. */
    private val tenNotes = StaffNotes.ALL.take(10).toSet()

    /**
     * Three notes asked, D4 moved up: the summary has the three keys and one accessible node over them,
     * which says "Your notes" and names the grown one and no other. The keys themselves are in the
     * unmerged tree; the merged one holds the block alone.
     */
    @Test
    fun the_summary_shows_the_keys_asked_this_round_with_the_grown_ones_named() {
        show(
            over(8).copy(
                askedThisRound = setOf(60, 62, 64),
                grewThisRound = setOf(62),
                progress = NoteProgress(mapOf(62 to 1)),
            ),
        )
        val block = compose.onNode(hasContentDescription("Your notes", substring = true))
        block.assertExists()
        val description = block.fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString()
        assertTrue("the block is called $description", description.startsWith("Your notes"))
        assertTrue("the block is called $description", "D4" in description)
        assertTrue("only the grown note is named: $description", "C4" !in description && "E4" !in description)
        compose.onNodeWithContentDescription("C4", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("C4").assertDoesNotExist()
    }

    @Test
    fun the_summary_has_no_keys_when_nothing_was_asked() {
        show(over(8))
        compose.onNode(hasContentDescription("Your notes", substring = true)).assertDoesNotExist()
    }

    /** Again is a big button's height, no more and no less: a squeezed one is short, and a minimum that counts too much would let it grow. */
    private fun assertAgainIsWhole(height: Dp, where: String) =
        assertTrue("$where, Again is $height tall and not $BigActionHeight", abs((height - BigActionHeight).value) <= 0.5f)

    /**
     * The summary in the tab of the phone the spec drew it for, 411 x 713 dp: its 915 dp screen less the
     * bars, which leaves the stage 442 dp inside (713 less the column's 4 and 16, the pills' 69, the hero
     * row's 76, the places' 32, three gaps of 14 and the stage's own 32). One row of keys with the trophy at
     * 90 sp is the tightest case: about 15 dp to spare over the least the summary takes (at 100 sp it was 1 dp,
     * too little for a phone whose emoji font draws taller lines). Ten notes asked take a second row, which the
     * trophy gives way to (50 sp, smaller than with five asked), with about 8 dp to spare. The test's own window is
     * the whole emulator, with room to spare, and a column short of height does not overflow: it squeezes
     * the later children, so Again must keep its full height ([BigActionHeight]) and end above the ten places.
     * Nothing scrolls with five asked or with ten: the second row and the 10 dp over the first row (for a star
     * and a sparkle) took about 5 dp more than this stage holds with the trophy at 60 sp, so with two rows it
     * is 50 sp.
     */
    /**
     * The summary fits the stage as it is, with [asked] notes asked: Again whole and above the round's places,
     * the keys clear of the tiles by the 10 dp a star or a sparkle hangs over a key, and nothing scrolls.
     */
    private fun assertTheSummaryFits(asked: String) {
        val keys = compose.onNode(hasContentDescription("Your notes", substring = true)).getUnclippedBoundsInRoot()
        val tiles = compose.onNodeWithContentDescription("Streak of 5").getUnclippedBoundsInRoot()
        val again = compose.onNodeWithText("Again").getUnclippedBoundsInRoot()
        val places = compose.onNodeWithContentDescription("Round progress, 10 of 10 notes").getUnclippedBoundsInRoot()
        assertAgainIsWhole(again.height, "with $asked asked")
        // A learned key's star and a grown key's sparkle hang 10 dp over the key: the tiles must not be under them.
        assertTrue("with $asked asked, the keys $keys start less than 10 dp under the tiles $tiles", keys.top - tiles.bottom >= 10.dp)
        compose.onAllNodes(hasScrollAction()).assertCountEquals(0) // it fits as it is: nothing scrolls
        assertTrue("with $asked asked, the keys $keys run into Again $again", keys.bottom <= again.top)
        assertTrue("with $asked asked, Again $again should end above the places $places", again.bottom <= places.top)
    }

    @Test
    fun the_summary_fits_the_stage_with_ten_notes_asked_and_two_rows() {
        showInWindow(DpSize(411.dp, 713.dp), over(8).copy(askedThisRound = StaffNotes.DEFAULT_PRACTICE))
        assertTheSummaryFits("five")
        val trophyWithFive = tagged("trophy").height
        compose.runOnIdle { state.value = over(8).copy(askedThisRound = tenNotes) }
        compose.waitForIdle()
        assertTheSummaryFits("ten")
        val trophyWithTen = tagged("trophy").height
        assertTrue("the trophy is $trophyWithTen tall with ten notes asked and $trophyWithFive with five", trophyWithTen < trophyWithFive)
    }

    /**
     * A tab of 650 dp, shorter than the design phone's 713 as most phones' are: the stage is about 379 dp inside,
     * short of the summary with the trophy at 90 sp. The trophy gives way to the room left (about 55 sp here), so
     * with the usual five notes asked nothing scrolls and Again stands whole.
     */
    @Test
    fun the_summary_fits_a_shorter_tab_with_five_notes_asked() {
        showInWindow(DpSize(411.dp, 650.dp), over(8).copy(askedThisRound = StaffNotes.DEFAULT_PRACTICE))
        assertTheSummaryFits("five")
    }

    /**
     * At the tab's minimum height, 411 x 623 dp (below it the tab itself scrolls), the stage is about 352 dp
     * inside. With the trophy at its smallest and the tiles at 92 dp the summary may still not fit, ten notes
     * asked surely not; it then scrolls and does not squeeze Again, which a child must always reach: it stays
     * 84 dp tall (a squeezed one was 13 dp with five notes asked and 6 dp with ten) and scrolling brings it into
     * view. At 650 dp with ten asked the tab still fits its column, so the one thing that may scroll is the
     * summary's own column.
     */
    @Test
    fun the_summary_keeps_again_whole_at_the_tab_minimum() {
        showInWindow(DpSize(411.dp, 623.dp), over(8).copy(askedThisRound = StaffNotes.DEFAULT_PRACTICE))
        fun assertAgainWhole(height: Int, asked: String) {
            val again = compose.onNodeWithText("Again")
            assertAgainIsWhole(again.getUnclippedBoundsInRoot().height, "at $height dp with $asked asked")
            val scrolling = compose.onAllNodes(hasScrollAction()).fetchSemanticsNodes().size
            if (height > 623) assertTrue("at $height dp with $asked asked, $scrolling things scroll", scrolling <= 1)
            if (scrolling > 0) {
                again.performScrollTo().assertIsDisplayed()
            } else {
                val places = compose.onNodeWithContentDescription("Round progress, 10 of 10 notes").getUnclippedBoundsInRoot()
                assertTrue("at $height dp with $asked asked, Again should end above the places", again.getUnclippedBoundsInRoot().bottom <= places.top)
            }
        }
        compose.runOnIdle { state.value = over(8).copy(askedThisRound = StaffNotes.DEFAULT_PRACTICE) }
        compose.waitForIdle()
        assertAgainWhole(623, "five")
        listOf(623, 650).forEach { height ->
            compose.runOnIdle { window.value = DpSize(411.dp, height.dp) }
            compose.runOnIdle { state.value = over(8).copy(askedThisRound = tenNotes) }
            compose.waitForIdle()
            assertAgainWhole(height, "ten")
        }
    }

    /**
     * Seven keys of 44 dp and their gaps are 344 dp and the stage is 339 dp inside on a 411 dp phone: the
     * keys shrink together and not the last one alone. The labels sit in the middle of their keys, so
     * their centres are as far apart as the keys are wide, and the same all along the row, give or take the
     * pixel each label is rounded to (the squeezed last key was 3 dp off).
     */
    @Test
    fun a_full_row_of_keys_shrinks_all_its_keys_alike() {
        show(over(8).copy(askedThisRound = tenNotes))
        val centres = listOf("C4", "D4", "E4", "F4", "G4", "A4", "B4").map {
            compose.onNodeWithContentDescription(it, useUnmergedTree = true).getUnclippedBoundsInRoot().let { b -> (b.left + b.right) / 2 }
        }
        val steps = centres.zipWithNext { a, b -> (b - a).value }
        assertTrue("the keys are not as wide as each other: the centres are $steps dp apart", steps.max() - steps.min() < 1f)
    }

    /** Ten first-try notes put a sparkle on each side of the trophy; nine do not. */
    @Test
    fun a_perfect_round_gets_sparkles() {
        show(over(10))
        val sparkles = tagged("trophy_sparkles")
        val trophy = tagged("trophy")
        assertTrue("sparkles $sparkles should reach out on both sides of the trophy $trophy", sparkles.left < trophy.left && trophy.right < sparkles.right)

        compose.runOnIdle { state.value = over(9) }
        compose.waitForIdle()
        compose.onNodeWithTag("trophy_sparkles", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("trophy", useUnmergedTree = true).assertExists()
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

    /** The picker with [progress], over the default notes: C4 D4 E4 F4 G4. */
    private fun pickingWith(progress: NoteProgress) {
        show(asking(60).copy(progress = progress))
        compose.onNodeWithContentDescription("Choose notes").performClick()
    }

    @Test
    fun a_known_key_wears_a_star_and_says_so() {
        pickingWith(NoteProgress(mapOf(60 to 8, 62 to 3)))
        compose.onNodeWithContentDescription("C4, learned").assertExists()
        compose.onNodeWithContentDescription("D4").assertExists()
        compose.onNodeWithContentDescription("D4, learned").assertDoesNotExist()
    }

    /**
     * D4 at 3 of 8: the lower three eighths of the key are D's orange, the rest the pale tint of it.
     * Sampled mid-width, clear of the label (middle of the key) and of the outline. The tint has D's
     * full red as white does, so the two are told apart by green.
     */
    @Test
    fun a_key_fills_from_the_bottom_as_far_as_it_is_learned() {
        needsDevice()
        pickingWith(NoteProgress(mapOf(62 to 3)))
        val key = compose.onNodeWithContentDescription("D4").captureToImage().toPixelMap()
        val d = NoteLetterColors[1]
        val bottom = key[key.width / 2, (key.height * 0.9f).toInt()]
        val top = key[key.width / 2, (key.height * 0.15f).toInt()]
        assertEquals("near the bottom the key is D's colour", d.toArgb(), bottom.toArgb())
        assertTrue("near the top the key is only tinted: green ${top.green} against D's ${d.green}", top.green - d.green > 0.1f)
    }

    /**
     * D4 at 4 of 8 in the [dark] theme or the light one: the key is half filled and the fill's edge runs through
     * the middle of its name. Gives the luminance of every pixel of the name's box above the edge and below it,
     * 2 px clear of the edge, where the two colours of the name meet. The key has one name for a screen reader
     * and for a test, in either tree, however it is drawn.
     */
    private fun halfLearnedLabel(dark: Boolean): Pair<List<Float>, List<Float>> {
        compose.setContent {
            LocaleProvider(Locale.ENGLISH) {
                FluteAssistantTheme(darkTheme = dark) {
                    Box(Modifier.padding(16.dp)) {
                        NoteKey(midi = 62, progress = NoteProgress(mapOf(62 to 4)), modifier = Modifier.testTag("key"))
                    }
                }
            }
        }
        compose.onAllNodesWithContentDescription("D4", useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithText("D4", useUnmergedTree = true).assertCountEquals(1)
        val keyNode = compose.onNodeWithTag("key", useUnmergedTree = true)
        val key = keyNode.getBoundsInRoot()
        val label = compose.onNodeWithContentDescription("D4", useUnmergedTree = true).getBoundsInRoot()
        val image = keyNode.captureToImage().toPixelMap()
        val px = image.height / key.height.value
        val edge = image.height / 2
        val left = ((label.left - key.left).value * px).roundToInt().coerceAtLeast(0)
        val right = ((label.right - key.left).value * px).roundToInt().coerceAtMost(image.width)
        val top = ((label.top - key.top).value * px).roundToInt().coerceAtLeast(0)
        val bottom = ((label.bottom - key.top).value * px).roundToInt().coerceAtMost(image.height)
        fun band(rows: IntRange) = rows.flatMap { y -> (left until right).map { x -> image[x, y].luminance() } }
        return band(top until edge - 2) to band(edge + 2 until bottom)
    }

    /**
     * Night: above the fill the name stands on the dark tint and is the theme's light text, on the fill it is ink.
     * One colour for the whole name lost half of it at 3 to 5 of 8 (ink on the dark tint is about 1.7:1).
     */
    @Test
    fun a_half_learned_key_keeps_its_whole_name_in_dark_mode() {
        needsDevice()
        val (above, below) = halfLearnedLabel(dark = true)
        assertTrue("above the fill the name is light on the dark tint: the brightest is ${above.max()}", above.max() > 0.6f)
        assertTrue("on the fill the name is ink: the darkest is ${below.min()}", below.min() < 0.05f)
    }

    /** Day: the tint is bright as well, so the name is ink on both and looks as it always has. */
    @Test
    fun a_half_learned_key_is_ink_all_over_in_light_mode() {
        needsDevice()
        val (above, below) = halfLearnedLabel(dark = false)
        assertTrue("above the fill the name is ink: the darkest is ${above.min()}", above.min() < 0.05f)
        assertTrue("on the fill the name is ink: the darkest is ${below.min()}", below.min() < 0.05f)
    }

    /**
     * Five dots for C D E F G, only C's chosen note learned: its dot is C's red and D's is the pale tint.
     * The dots are as wide apart as the row says; the first and second dot's centres are sampled.
     */
    @Test
    fun the_pick_dots_are_solid_only_for_known_letters() {
        needsDevice()
        show(asking(60).copy(progress = NoteProgress(mapOf(60 to 8))))
        val dots = compose.onNodeWithTag("pick_dots", useUnmergedTree = true).captureToImage().toPixelMap()
        val dot = dots.height // a dot is as tall as the row and as wide
        val pitch = (dots.width - dot) / 4f // five dots, four gaps
        val first = dots[dot / 2, dot / 2]
        val second = dots[(pitch + dot / 2).toInt(), dot / 2]
        val c = NoteLetterColors[0]
        val d = NoteLetterColors[1]
        assertEquals("C is learned, so its dot is solid", c.toArgb(), first.toArgb())
        assertTrue("D is not, so its dot is paler: green ${second.green} against D's ${d.green}", second.green - d.green > 0.1f)
    }

    @Test
    fun croatian_names_and_copy() {
        show(asking(60), Locale("hr"))
        compose.onNodeWithText("c1").assertIsDisplayed()
        compose.onNodeWithText("🔎 Nađi").assertIsDisplayed()
        compose.onNodeWithText("👂 Slušaj").assertIsDisplayed()
        compose.onNodeWithContentDescription("Odaberi note").assertExists()
        compose.onNodeWithContentDescription("Mjesto 1 u crtovlju").assertExists()
        compose.onNodeWithContentDescription("Odaberi note").performClick()
        compose.onNodeWithText("Koje note?").assertIsDisplayed()
        compose.onNodeWithText("Zvuk").assertIsOn()
        compose.onNodeWithText("U redu").assertIsDisplayed()
    }
}
