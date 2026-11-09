// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.notes

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import hr.bertlab.fluteassistant.domain.notes.NotesGame
import hr.bertlab.fluteassistant.domain.notes.StaffNotes
import hr.bertlab.fluteassistant.ui.common.LocaleProvider
import hr.bertlab.fluteassistant.ui.common.SidewaysGeometry
import hr.bertlab.fluteassistant.ui.common.SidewaysHost
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

/**
 * The notes game on a phone on its side: the question with the panel, the staff and the column,
 * the note picker and the end of the round. Upright is `NotesScreenTest`'s. The sizes are compared
 * with the room rather than asked of `assertIsDisplayed`, as `SidewaysHost` forces its size through
 * the density and what spills past it is still on the emulator's screen.
 */
@RunWith(AndroidJUnit4::class)
class NotesSidewaysTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var state: MutableState<NotesUiState>
    private var phone by mutableStateOf(Phone(915, 411))
    private var sideways by mutableStateOf(true)
    private val tapped = mutableListOf<Int>()
    private val toggled = mutableListOf<Int>()
    private var playAgainRequests = 0
    private var soundToggles = 0
    private val games = mutableListOf<NotesGame>()
    private var hearAgainRequests = 0
    private var phraseShownRequests = 0

    /** The density `SidewaysHost` forces on the content now: what a dp comes to in pixels, and so how a dp length is rounded. */
    private var density = 1f

    private class Phone(val width: Int, val height: Int) {
        val contentWidth: Float get() = width - SidewaysGeometry.railWidth(height.toFloat())
        val cardWidth: Float get() = SidewaysGeometry.cardWidth(contentWidth, height.toFloat())
        val column: Float get() = SidewaysGeometry.columnWidth(height.toFloat())
        override fun toString() = "$width x $height"
    }

    private fun show(
        initial: NotesUiState,
        at: Phone = Phone(915, 411),
        locale: Locale = Locale.ENGLISH,
        fontScale: Float = 1f,
    ) {
        phone = at
        state = mutableStateOf(initial)
        compose.setContent {
            LocaleProvider(locale) {
                SidewaysHost(width = phone.width, height = phone.height, fontScale = fontScale, sideways = sideways) {
                    val forced = LocalDensity.current.density
                    SideEffect { density = forced }
                    NotesContent(
                        state = state.value,
                        onNoteTapped = { tapped += it },
                        onPlayAgain = { playAgainRequests++ },
                        onTogglePracticeNote = { toggled += it },
                        onToggleSound = { soundToggles++ },
                        onSetGame = { games += it },
                        onHearAgain = { hearAgainRequests++ },
                        onPhraseShown = { phraseShownRequests++ },
                    )
                }
            }
        }
    }

    /** A round in progress asking [midi], with the default notes chosen. */
    private fun asking(midi: Int = 60) = NotesUiState(practiceNotes = StaffNotes.DEFAULT_PRACTICE, currentMidi = midi)

    /** A finished round with [score] notes found first time. */
    private fun over(score: Int, bestStreak: Int = 5) = asking(60).copy(
        roundOver = true,
        results = List(score) { true } + List(NotesUiState.ROUND_LENGTH - score) { false },
        taps = 13,
        bestStreak = bestStreak,
        roundMillis = 83_000L,
    )

    private fun on(each: Phone) {
        compose.runOnIdle { phone = each }
        compose.waitForIdle()
    }

    /** A Hear round whose phrase is C4 then D4, with the first on the staff and the second asked. */
    private fun hearing() = asking(62).copy(game = NotesGame.HEAR, phrase = listOf(60, 62), placed = 1)

    private val staffDescription = "Staff, press where the note goes and let go"
    private val hearStaffDescription = "Staff, press where the next note goes and let go"

    private fun staff() = compose.onNodeWithContentDescription(staffDescription)
    private fun hearStaff() = compose.onNodeWithContentDescription(hearStaffDescription)
    private fun places() = compose.onNodeWithContentDescription("Round progress, 0 of 10 notes")
    private fun pick() = compose.onNodeWithContentDescription("Choose notes")
    private fun word(text: String) = compose.onNodeWithText(text)
    private fun findPill() = word("🔎 Find")
    private fun hearPill() = word("👂 Hear")
    private fun yourNotes() = compose.onNode(hasContentDescription("Your notes", substring = true))

    /** The dots under Pick's word, which Pick's own node swallows from the merged tree. */
    private fun dotsOfPick() = compose.onNodeWithTag("pick_dots", useUnmergedTree = true)

    private fun bounds(node: SemanticsNodeInteraction) = node.getUnclippedBoundsInRoot()

    private fun width(node: SemanticsNodeInteraction) = bounds(node).let { it.right - it.left }

    private fun height(node: SemanticsNodeInteraction) = bounds(node).let { it.bottom - it.top }

    private fun assertNear(what: String, expected: Dp, actual: Dp, tolerance: Float = TOLERANCE) =
        assertTrue("$what: expected about $expected, got $actual", abs((expected - actual).value) <= tolerance)

    /** [node] and the [shadow] under it are within the window, which is the phone less the rail. */
    private fun assertInside(phone: Phone, what: String, node: SemanticsNodeInteraction, shadow: Dp = 0.dp) {
        val b = bounds(node)
        val right = phone.contentWidth.dp + SLACK
        val bottom = phone.height.dp + SLACK
        assertTrue("$phone: $what starts at ${b.left} x ${b.top}", b.left >= -SLACK && b.top >= -SLACK)
        assertTrue("$phone: $what ends at ${b.right} x ${b.bottom + shadow}, the room is $right x $bottom", b.right <= right && b.bottom + shadow <= bottom)
    }

    /** What a card may use: it ends where the column begins, less the margin between them. */
    private fun assertInTheCard(phone: Phone, what: String, node: SemanticsNodeInteraction) {
        val cardRight = (8f + phone.cardWidth).dp + SLACK
        assertTrue("$phone: $what ends at ${bounds(node).right}, the card at $cardRight", bounds(node).right <= cardRight)
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
        assertFalse(
            "$phone: \"$text\" is cut short: ${layout.size} of ${layout.layoutInput.constraints}, ${layout.lineCount} lines, ${layout.layoutInput.style.fontSize}",
            layout.hasVisualOverflow,
        )
        (0 until layout.lineCount - 1).forEach { line ->
            val end = layout.getLineEnd(line)
            assertTrue("$phone: \"$text\" is split inside a word at $end", text[end - 1].isWhitespace())
        }
    }

    /**
     * All of [text] on its first line. A title with `maxLines = 1` counts one line however it breaks,
     * so the line's end is what shows a word pushed off it. [assertWholeWords] cannot say for a title
     * only as wide as its words, as `hasVisualOverflow` counts a box narrower than the room offered as cut.
     */
    private fun assertOneWholeLine(phone: Phone, text: String) =
        assertEquals("$phone: \"$text\" is broken", text.length, layoutOf(text).getLineEnd(0))

    private fun openPicker() {
        pick().performClick()
        compose.waitForIdle()
    }

    @Test
    fun the_question_shows_find_the_note_and_pick() {
        show(asking(65)) // F4
        findPill().assertIsDisplayed()
        word("F4").assertIsDisplayed()
        word("Pick").assertIsDisplayed()
        pick().assertIsDisplayed()
        staff().assertIsDisplayed()
        // The note stands left of the staff, where a right hand will not cover it, and is the big round
        // bubble of the mockup, 108 dp across; Find is a pill in the column now, right of the staff.
        assertTrue("the panel is left of the staff", bounds(word("F4")).right <= bounds(staff()).left)
        assertTrue("Find is right of the staff", bounds(findPill()).left >= bounds(staff()).right)
        // The bubble's own box has no node, but its word is 56 sp (a line 76 dp tall) where upright's is 36 sp (49).
        assertTrue("the note's word is ${height(word("F4"))} tall", height(word("F4")) > 70.dp)
        val note = bounds(word("F4"))
        assertNear("the note stands in the middle of the 124 dp panel", 8.dp + 62.dp, (note.left + note.right) / 2, tolerance = 3f)
    }

    /**
     * The two games head the right column on all three screens, stacked as the tuner's modes are: Find
     * over Hear at the column's top (the page's 8 and the column's 6), and Hear's 5 dp shadow clear of the
     * first thing under them, the places, Sound or Pick.
     */
    @Test
    fun the_pills_head_the_column_on_all_three_screens() {
        show(asking(60))
        fun assertThePillsHead(screen: String, first: SemanticsNodeInteraction, each: Phone) {
            val columnLeft = (8f + each.cardWidth + 8f).dp
            val find = bounds(findPill().assertIsDisplayed())
            val hear = bounds(hearPill().assertIsDisplayed())
            listOf("Find" to find, "Hear" to hear).forEach { (name, pill) ->
                assertTrue("$each, $screen: $name starts at ${pill.left}, the column at $columnLeft", pill.left >= columnLeft - SLACK)
                assertTrue("$each, $screen: $name ends at ${pill.right}", pill.right <= each.contentWidth.dp - 8.dp + SLACK)
            }
            assertNear("$each, $screen: Find's top", 14.dp, find.top)
            assertTrue("$each, $screen: Find is over Hear", find.bottom <= hear.top)
            val next = bounds(first)
            assertTrue("$each, $screen: Hear's shadow ends at ${hear.bottom + 5.dp}, the next thing starts at ${next.top}", hear.bottom + 5.dp <= next.top + SLACK)
        }
        val phones = listOf(Phone(915, 411), Phone(800, 360), Phone(640, 360))
        phones.forEach { each ->
            on(each)
            assertThePillsHead("the question", places(), each)
        }
        findPill().assertIsSelected()
        hearPill().assertIsNotSelected()
        openPicker()
        phones.forEach { each ->
            on(each)
            assertThePillsHead("the picker", word("Sound"), each)
        }
        word("OK").performClick()
        compose.runOnIdle { state.value = over(8) }
        phones.forEach { each ->
            on(each)
            assertThePillsHead("the summary", pick(), each)
        }
        hearPill().performClick()
        assertEquals(listOf(NotesGame.HEAR), games)
        compose.runOnIdle { state.value = state.value.copy(game = NotesGame.HEAR) }
        hearPill().assertIsSelected()
        findPill().assertIsNotSelected()
    }

    /**
     * At 360 tall the column holds the pills, the ten places and Pick at their full sizes. A column short of
     * room squeezes its last child rather than spill past the window, so the sizes say whether it held: with
     * pills 38 dp tall it was about 4 dp short and Pick came out about 78 tall. The pills are 30 tall here, 44 at 411.
     */
    @Test
    fun the_column_holds_the_pills_the_places_and_pick_at_360_tall() {
        show(asking(60), at = Phone(640, 360))
        listOf(Phone(640, 360), Phone(800, 360)).forEach { each ->
            on(each)
            assertInside(each, "Find", findPill().assertIsDisplayed(), shadow = 5.dp)
            assertInside(each, "Hear", hearPill().assertIsDisplayed(), shadow = 5.dp)
            assertInside(each, "the places", places().assertIsDisplayed())
            assertInside(each, "Pick", pick().assertIsDisplayed(), shadow = 5.dp)
            assertNear("$each: Find's height", PILL_HEIGHT_SHORT, height(findPill()))
            assertNear("$each: Hear's height", PILL_HEIGHT_SHORT, height(hearPill()))
            assertNear("$each: the places' height", 168.dp, height(places()))
            assertNear("$each: Pick's height", 84.dp, height(pick()))
            assertTrue("$each: Hear's shadow runs into the places", bounds(hearPill()).bottom + 5.dp <= bounds(places()).top + SLACK)
            assertTrue("$each: the places run into Pick", bounds(places()).bottom <= bounds(pick()).top)
            assertTrue("$each: Pick's bottom is above the places'", bounds(pick()).bottom > bounds(places()).bottom)
        }
        on(Phone(915, 411))
        assertNear("915 x 411: Find's height", PILL_HEIGHT, height(findPill()))
        assertNear("915 x 411: Hear's height", PILL_HEIGHT, height(hearPill()))
    }

    /** Hear asks with the speaker bubble in the panel, where Find shows the note: the answer is not on screen. */
    @Test
    fun hear_puts_the_speaker_in_the_panel() {
        show(hearing())
        listOf(Phone(915, 411), Phone(640, 360)).forEach { each ->
            on(each)
            val speaker = compose.onNodeWithContentDescription("Hear it again").assertIsDisplayed()
            val b = bounds(speaker)
            assertTrue("$each: the speaker starts at ${b.left}, the panel at 8", b.left >= 8.dp - SLACK)
            assertTrue("$each: the speaker ends at ${b.right}, the panel at 132", b.right <= 132.dp + SLACK)
            assertTrue("$each: the speaker is left of the staff", b.right <= bounds(hearStaff()).left)
            assertNear("$each: the speaker's width", 108.dp, width(speaker))
            assertInside(each, "the speaker", speaker, shadow = 6.dp)
            word("D4").assertDoesNotExist() // the note asked
            word("C4").assertDoesNotExist() // nor the one on the staff
        }
        compose.onNodeWithContentDescription("Hear it again").performClick()
        assertEquals(1, hearAgainRequests)
        // A finger on the staff is answering, not asking to hear.
        hearStaff().performTouchInput { down(center) }
        compose.onNodeWithContentDescription("Hear it again").assertIsNotEnabled()
        hearStaff().performTouchInput { up() }
        compose.onNodeWithContentDescription("Hear it again").assertIsEnabled()
    }

    /**
     * Hear's prompt in the panel, in each language on the phones that have the least room: its words whole (broken
     * only between words), inside the panel's 8 to 132 dp and left of the staff, at [fontScale] times the text.
     */
    private fun assertTheHearPromptIsWhole(locale: Locale, prompt: String, staffName: String, fontScale: Float = 1f) {
        show(hearing(), locale = locale, fontScale = fontScale)
        listOf(Phone(915, 411), Phone(640, 360)).forEach { each ->
            on(each)
            val node = word(prompt).assertIsDisplayed()
            assertWholeWords(each, prompt)
            assertInside(each, "\"$prompt\"", node)
            val b = bounds(node)
            assertTrue("$each: \"$prompt\" starts at ${b.left}, the panel at 8", b.left >= 8.dp - SLACK)
            assertTrue("$each: \"$prompt\" ends at ${b.right}, the panel at 132", b.right <= 132.dp + SLACK)
            val staffLeft = bounds(compose.onNodeWithContentDescription(staffName)).left
            assertTrue("$each: \"$prompt\" ends at ${b.right}, the staff starts at $staffLeft", b.right <= staffLeft)
        }
    }

    @Test
    fun the_hear_prompt_is_whole_in_the_panel() = assertTheHearPromptIsWhole(Locale.ENGLISH, "❓ Find the next note", hearStaffDescription)

    @Test
    fun the_hear_prompt_is_whole_in_the_panel_in_croatian() =
        assertTheHearPromptIsWhole(Locale("hr"), "❓ Nađi sljedeću notu", "Crtovlje, pritisni gdje ide sljedeća nota i pusti")

    @Test
    fun the_hear_prompt_is_whole_in_the_panel_at_twice_the_text() =
        assertTheHearPromptIsWhole(Locale.ENGLISH, "❓ Find the next note", hearStaffDescription, fontScale = 2f)

    @Test
    fun the_hear_prompt_is_whole_in_the_panel_in_croatian_at_twice_the_text() =
        assertTheHearPromptIsWhole(Locale("hr"), "❓ Nađi sljedeću notu", "Crtovlje, pritisni gdje ide sljedeća nota i pusti", fontScale = 2f)

    @Test
    fun the_hear_prompt_is_whole_in_the_panel_at_large_text() =
        assertTheHearPromptIsWhole(Locale.ENGLISH, "❓ Find the next note", hearStaffDescription, fontScale = 1.5f)

    @Test
    fun the_hear_prompt_is_whole_in_the_panel_in_croatian_at_large_text() =
        assertTheHearPromptIsWhole(Locale("hr"), "❓ Nađi sljedeću notu", "Crtovlje, pritisni gdje ide sljedeća nota i pusti", fontScale = 1.5f)

    /**
     * The sideways staff is upright's `NoteStaff` with the phrase: E F G with two placed and the phrase
     * sounding. It reads Hear's description, E sits in the first of the three columns in its letter's colour
     * (the centre of a head is its fill, whatever line runs through it), and a slide answers nothing until
     * the phrase stops.
     */
    @Test
    fun hear_draws_the_phrase_and_locks_sideways_too() {
        show(asking(67).copy(game = NotesGame.HEAR, phrase = listOf(64, 65, 67), placed = 2, phrasePlaying = true), at = Phone(915, 411))
        val staff = hearStaff().assertIsDisplayed()
        staff().assertDoesNotExist()
        word("👂 Listen").assertIsDisplayed()
        val image = staff.captureToImage().toPixelMap()
        val visible = StaffNotes.visiblePositions(StaffNotes.DEFAULT_PRACTICE)
        val pitch = image.height / (visible.count() + 2f)
        val y = (visible.last - StaffNotes.positionOf(64) + 1.5f) * pitch
        val x = image.width * phraseColumns(3).first()
        assertEquals("E, the given note, at the first column", NoteLetterColors[2].toArgb(), image[x.toInt(), y.toInt()].toArgb())
        fun slide() = staff.performTouchInput {
            down(Offset(width * 0.3f, height * 0.5f))
            moveBy(Offset(0f, 40f))
            up()
        }
        slide()
        assertTrue("the phrase is sounding, so nothing is answered: $tapped", tapped.isEmpty())
        compose.runOnIdle { state.value = state.value.copy(phrasePlaying = false) }
        slide()
        assertEquals(1, tapped.size)
    }

    /**
     * Ten notes asked are two rows of keys under the tiles, 36 x 40 sideways. The card is 312 dp inside at
     * 360 tall, which with the column's 16 dp gaps was short: the keys came out 88 of their 96 dp at 640 wide.
     * Two rows bring the gaps to 10 and the headline takes the height the rest leaves. A squeezed column cuts
     * the second row, so its height says whether it held; the 10 dp over the first row is a learned key's star.
     */
    @Test
    fun the_summary_keys_stay_in_the_card_with_ten_asked_at_360_tall() {
        val ten = StaffNotes.ALL.take(10).toSet()
        show(over(8).copy(askedThisRound = ten), at = Phone(640, 360))
        listOf(8 to "🌟 Great job!", 2 to "💪 Keep practising!").forEach { (score, headline) ->
            compose.runOnIdle { state.value = over(score).copy(askedThisRound = ten) }
            listOf(Phone(640, 360), Phone(800, 360), Phone(915, 411)).forEach { each ->
                on(each)
                val keys = yourNotes().assertIsDisplayed()
                assertInTheCard(each, "the keys", keys)
                assertInTheCardsHeight(each, "the keys", keys)
                assertNear("$each, \"$headline\": the keys' height", 96.dp, height(keys))
                val tiles = bounds(compose.onNodeWithContentDescription("Streak of 5"))
                assertNear("$each: a tile's height", 92.dp, tiles.bottom - tiles.top)
                assertTrue("$each, \"$headline\": the keys start ${bounds(keys).top - tiles.bottom} under the tiles", bounds(keys).top - tiles.bottom >= 10.dp - SLACK)
                val title = word(headline).assertIsDisplayed()
                assertWholeWords(each, headline)
                assertInTheCard(each, "\"$headline\"", title)
                assertInTheCardsHeight(each, "\"$headline\"", title)
                compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy)).assertCountEquals(0)
            }
        }
    }

    @Test
    fun the_ten_places_show_beside_the_staff() {
        show(asking(60))
        places().assertIsDisplayed()
        // Two columns of five, in the right column over Pick: taller than wide, not one row of ten.
        assertNear("the places' width", 68.dp, width(places()))
        assertNear("the places' height", 168.dp, height(places()))
        assertTrue("the places are right of the staff", bounds(places()).left >= bounds(staff()).right)
        assertTrue("Pick is under the places", bounds(pick()).top >= bounds(places()).bottom)
        assertTrue("Pick is right of the staff", bounds(pick()).left >= bounds(staff()).right)
    }

    @Test
    fun the_places_fill_as_the_round_goes() {
        show(asking(60).copy(results = listOf(true, false, true), taps = 4))
        compose.onNodeWithContentDescription("Round progress, 3 of 10 notes").assertIsDisplayed()
        assertNear("the places' height", 168.dp, height(compose.onNodeWithContentDescription("Round progress, 3 of 10 notes")))
    }

    @Test
    fun a_slide_on_the_staff_answers() {
        show(asking(60))
        staff().performTouchInput {
            down(Offset(width * 0.3f, height * 0.5f))
            moveBy(Offset(0f, 40f))
            up()
        }
        assertEquals(1, tapped.size)
    }

    /** Sideways celebrates as upright: the fifth flame sets off the burst over the staff, and the slide still goes through it. */
    @Test
    fun five_flames_set_off_the_burst_over_the_staff_and_the_slide_goes_through() {
        show(asking(60).copy(streak = 5))
        val burst = compose.onNodeWithTag("streak_burst", useUnmergedTree = true).assertExists()
        assertEquals("the burst covers the staff", bounds(staff()), bounds(burst))
        staff().performTouchInput {
            down(Offset(width * 0.3f, height * 0.5f))
            moveBy(Offset(0f, 40f))
            up()
        }
        assertEquals(1, tapped.size)
        compose.runOnIdle { state.value = state.value.copy(streak = 4) }
        compose.onNodeWithTag("streak_burst", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun the_game_does_not_scroll_at_360_tall() {
        show(asking(60))
        val scrolls = SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollBy)
        listOf(Phone(640, 360), Phone(800, 360)).forEach { each ->
            on(each)
            compose.onAllNodes(scrolls).assertCountEquals(0)
        }
        openPicker()
        listOf(Phone(640, 360), Phone(800, 360)).forEach { each ->
            on(each)
            compose.onAllNodes(scrolls).assertCountEquals(0)
        }
        word("OK").performClick()
        compose.runOnIdle { state.value = over(8) }
        listOf(Phone(640, 360), Phone(800, 360)).forEach { each ->
            on(each)
            compose.onAllNodes(scrolls).assertCountEquals(0)
        }
    }

    /** The spec's table: a place is the staff's height over the visible places plus two. */
    @Test
    fun the_places_are_the_size_the_spec_gives() {
        show(asking(60))
        val ordinary = StaffNotes.visiblePositions(StaffNotes.DEFAULT_PRACTICE).count() + 2
        val all = StaffNotes.visiblePositions(StaffNotes.ALL.toSet()).count() + 2
        listOf(Phone(915, 411) to 26.8f, Phone(800, 360) to 23.1f).forEach { (each, expected) ->
            on(each)
            // The staff takes the card's full height: the window less the margins and the card's 10 dp.
            assertNear("$each: the staff's height", (each.height - 36).dp, height(staff()))
            assertNear("$each: a place", expected.dp, height(compose.onNodeWithContentDescription("Staff position 2")), tolerance = 1.5f)
            assertEquals((each.height - 36f) / ordinary, expected, 0.2f)
        }
        compose.runOnIdle { state.value = state.value.copy(practiceNotes = StaffNotes.ALL.toSet()) }
        listOf(Phone(915, 411) to 22.1f, Phone(800, 360) to 19.1f).forEach { (each, expected) ->
            on(each)
            assertNear("$each: a place with all fifteen", expected.dp, height(compose.onNodeWithContentDescription("Staff position 2")), tolerance = 1.5f)
            assertEquals((each.height - 36f) / all, expected, 0.2f)
        }
    }

    @Test
    fun the_staff_stays_clear_of_the_swipe_back_zones() {
        show(asking(60))
        listOf(Phone(915, 411), Phone(800, 360), Phone(640, 360)).forEach { each ->
            on(each)
            // The window's right edge is the screen's; the rail is on the left, so only the right matters.
            assertTrue("$each: the staff ends at ${bounds(staff()).right}", bounds(staff()).right <= each.contentWidth.dp - 30.dp)
            assertTrue("$each: the staff starts at ${bounds(staff()).left}", bounds(staff()).left >= 8.dp)
        }
    }

    @Test
    fun the_picker_hides_the_places_and_ok_closes_it() {
        show(asking(60))
        openPicker()
        word("Which notes?").assertIsDisplayed()
        places().assertDoesNotExist()
        pick().assertDoesNotExist() // one way out: the OK button
        staff().assertDoesNotExist()
        word("Sound").assertIsDisplayed()
        word("OK").assertIsDisplayed()
        word("C4").assertIsOn()
        word("C6").assertIsOff()
        word("C6").performClick()
        assertEquals(listOf(84), toggled)
        word("OK").performClick()
        compose.waitForIdle()
        staff().assertIsDisplayed()
        places().assertIsDisplayed()
        pick().assertIsDisplayed()
    }

    @Test
    fun the_picker_has_bigger_keys_and_the_sound_and_ok_in_the_column() {
        show(asking(60))
        openPicker()
        val c4 = bounds(word("C4"))
        val b4 = bounds(word("B4"))
        val c5 = bounds(word("C5"))
        val c6 = bounds(word("C6"))
        assertNear("a key's width", 78.dp, c4.right - c4.left)
        assertNear("a key's height", 62.dp, c4.bottom - c4.top)
        assertNear("the gap between keys", 10.dp, bounds(word("D4")).left - c4.right)
        assertTrue("C6 is over C5, C5 over C4", c6.bottom <= c5.top && c5.bottom <= c4.top)
        assertEquals("the rows line up on the left", c4.left, c5.left)
        assertTrue("the keys are in the card", b4.right <= (8f + Phone(915, 411).cardWidth).dp)
        // The pills at the top of the right column, Sound under them and OK low, all as wide as the column.
        val sound = bounds(word("Sound"))
        val ok = bounds(word("OK"))
        assertNear("Sound's width", 116.dp, sound.right - sound.left)
        assertNear("OK's width", 116.dp, ok.right - ok.left)
        assertTrue("Sound is right of the keys", sound.left >= b4.right)
        assertTrue("OK is under Sound", ok.top >= sound.bottom)
        assertNear("the pills are at the top of the column", 14.dp, bounds(findPill()).top)
        assertTrue("Sound is under the pills", sound.top >= bounds(hearPill()).bottom)
        assertNear("OK's bottom", 411.dp - 16.dp, ok.bottom)
        word("Sound").performClick()
        assertEquals(1, soundToggles)
        compose.runOnIdle { state.value = state.value.copy(soundOn = false) }
        word("Sound").assertIsOff()
    }

    @Test
    fun the_keys_shrink_to_fit_the_narrowest_phone() {
        show(asking(60))
        openPicker()
        listOf(Phone(915, 411), Phone(800, 360), Phone(640, 360)).forEach { each ->
            on(each)
            // The card's inside is its width less the 16 dp of padding either side, and the forced size rounds the density.
            val inner = each.cardWidth - 32f
            val key = bounds(word("C4"))
            val keyWidth = key.right - key.left
            assertTrue("$each: a key is $keyWidth wide, the room is $inner for seven", 7 * keyWidth.value + 6 * 10 <= inner + 4)
            assertTrue("$each: a key is only $keyWidth wide", keyWidth >= 44.dp)
            assertNear("$each: a key's height", 62.dp, key.bottom - key.top)
            assertInTheCard(each, "the last key", word("B4"))
            assertInTheCard(each, "the first key", word("C6"))
        }
    }

    @Test
    fun the_picker_stays_open_when_the_phone_turns() {
        show(asking(60))
        openPicker()
        word("Which notes?").assertIsDisplayed()
        compose.runOnIdle { sideways = false }
        compose.waitForIdle()
        // Upright draws the same picker with the title over it and OK under the keys (below the fold of a 411 tall window).
        word("Which notes?").assertIsDisplayed()
        word("OK").assertExists()
        staff().assertDoesNotExist()
        compose.runOnIdle { sideways = true }
        compose.waitForIdle()
        word("Which notes?").assertIsDisplayed()
        assertNear("a key's width", 78.dp, width(word("C4")))
        staff().assertDoesNotExist()
    }

    /**
     * The phrase is asked for as it comes on screen, sideways as upright, and a turn shows the same phrase:
     * the ask sits above the sideways branch, so a turn does not ask again (the ViewModel would ignore it,
     * but a turn is not a phrase coming on screen).
     */
    @Test
    fun a_turn_does_not_ask_for_the_phrase_again() {
        show(hearing())
        compose.waitForIdle()
        assertEquals("shown sideways", 1, phraseShownRequests)
        compose.runOnIdle { sideways = false }
        compose.waitForIdle()
        compose.runOnIdle { sideways = true }
        compose.waitForIdle()
        assertEquals("turned upright and back", 1, phraseShownRequests)
    }

    @Test
    fun the_question_stays_when_the_phone_turns() {
        show(asking(62))
        compose.runOnIdle { sideways = false }
        compose.waitForIdle()
        word("D4").assertIsDisplayed()
        compose.runOnIdle { sideways = true }
        compose.waitForIdle()
        word("D4").assertIsDisplayed()
        staff().assertIsDisplayed()
    }

    @Test
    fun the_summary_shows_again_and_pick_and_no_digits() {
        show(over(8))
        word("🌟 Great job!").assertIsDisplayed()
        compose.onNodeWithContentDescription("Right first time: 8 of 10").assertIsDisplayed()
        compose.onNodeWithContentDescription("Streak of 5").assertIsDisplayed()
        compose.onNodeWithContentDescription("Aim: 2 of 3 stars").assertIsDisplayed()
        compose.onNodeWithContentDescription("Speed: Turtle").assertIsDisplayed()
        pick().assertIsDisplayed()
        word("Pick").assertIsDisplayed()
        word("Again").assertIsDisplayed()
        places().assertDoesNotExist()
        staff().assertDoesNotExist()
        word("C4").assertDoesNotExist() // the question's bubble is gone
        // Pick over Again in the right column, as wide as it, Pick 84 tall and Again low.
        assertTrue("Pick is over Again", bounds(pick()).bottom <= bounds(word("Again")).top)
        assertNear("Pick's height", 84.dp, height(pick()))
        assertNear("Pick's width", 116.dp, width(pick()))
        assertNear("Again's width", 116.dp, width(word("Again")))
        assertNear("Again's bottom", 411.dp - 16.dp, bounds(word("Again")).bottom)
        assertTrue("the trophy's card is left of the column", bounds(word("🌟 Great job!")).right <= bounds(pick()).left)
        // Not one digit in any text, as upright; the descriptions keep "8 of 10" for a screen reader.
        val texts = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { it.config[SemanticsProperties.Text] }
            .map { it.text }
        assertTrue("some text is there", texts.isNotEmpty())
        texts.forEach { text -> assertTrue("\"$text\" has a digit", text.none { it.isDigit() }) }
        word("Again").performClick()
        assertEquals(1, playAgainRequests)
        pick().performClick()
        compose.waitForIdle()
        word("Which notes?").assertIsDisplayed()
    }

    @Test
    fun the_summary_has_one_row_of_ten_stars() {
        show(over(8))
        val stars = compose.onNodeWithContentDescription("Right first time: 8 of 10")
        assertTrue("one row, ${width(stars)} wide and ${height(stars)} tall", width(stars) > height(stars) * 6)
        val streak = compose.onNodeWithContentDescription("Streak of 5")
        val aim = compose.onNodeWithContentDescription("Aim: 2 of 3 stars")
        val speed = compose.onNodeWithContentDescription("Speed: Turtle")
        assertTrue("the tiles stand in a row", bounds(streak).right <= bounds(aim).left && bounds(aim).right <= bounds(speed).left)
        assertTrue("the stars are over the tiles", bounds(stars).bottom <= bounds(streak).top)
        assertNear("a tile's height", 92.dp, height(streak))
    }

    /**
     * The stars' size comes from the measured width of the emoji on the phone (a Samsung's are not
     * Google's), so on the narrowest phone, where the room and not the 27 dp cap decides, the row
     * fills the column the trophy leaves and cannot run out of it. The headline fills that column.
     */
    @Test
    fun the_stars_fill_the_room_the_trophy_leaves_on_the_narrowest_phone() {
        show(over(8))
        on(Phone(640, 360))
        val stars = bounds(compose.onNodeWithContentDescription("Right first time: 8 of 10"))
        val room = width(word("🌟 Great job!"))
        val row = stars.right - stars.left
        assertTrue("the stars are $row wide in a column of $room", row <= room + SLACK)
        assertTrue("the stars are $row wide in a column of $room, a guess left too much of it unused", row >= room * 0.93f)
    }

    @Test
    fun fits_every_phone() {
        show(asking(60).copy(streak = 3, results = listOf(true, true, true), taps = 3))
        listOf(Phone(915, 411), Phone(800, 360), Phone(640, 360)).forEach { each ->
            on(each)
            val question = "$each, the question"
            assertInside(each, "$question: Find", findPill().assertIsDisplayed(), shadow = 5.dp)
            assertInside(each, "$question: Hear", hearPill().assertIsDisplayed(), shadow = 5.dp)
            assertInside(each, "$question: the note", word("C4").assertIsDisplayed(), shadow = 6.dp)
            assertInside(each, "$question: the streak", compose.onNodeWithContentDescription("Streak of 3").assertIsDisplayed())
            assertInside(each, "$question: the staff", staff().assertIsDisplayed())
            assertInside(each, "$question: the places", compose.onNodeWithContentDescription("Round progress, 3 of 10 notes").assertIsDisplayed())
            assertInside(each, "$question: Pick", pick().assertIsDisplayed(), shadow = 5.dp)
            assertInTheCard(each, "the staff", staff())
            // Pick is low on the right, as wide as the column.
            assertNear("$each: Pick's width", each.column.dp, width(pick()))
            assertNear("$each: Pick's bottom", each.height.dp - 16.dp, bounds(pick()).bottom)
            assertNear("$each: Pick's right edge", each.contentWidth.dp - 8.dp, bounds(pick()).right)
        }
        openPicker()
        listOf(Phone(915, 411), Phone(800, 360), Phone(640, 360)).forEach { each ->
            on(each)
            val picker = "$each, the picker"
            assertInside(each, "$picker: the title", word("Which notes?").assertIsDisplayed())
            listOf("C6", "C5", "B5", "C4", "B4").forEach { assertInside(each, "$picker: $it", word(it).assertIsDisplayed(), shadow = 4.dp) }
            assertInside(each, "$picker: Sound", word("Sound").assertIsDisplayed())
            assertInside(each, "$picker: OK", word("OK").assertIsDisplayed(), shadow = 8.dp)
            assertNear("$each: OK's bottom", each.height.dp - 16.dp, bounds(word("OK")).bottom)
        }
        word("OK").performClick()
        compose.runOnIdle { state.value = over(8) }
        listOf(Phone(915, 411), Phone(800, 360), Phone(640, 360)).forEach { each ->
            on(each)
            val summary = "$each, the summary"
            assertInside(each, "$summary: the headline", word("🌟 Great job!").assertIsDisplayed())
            assertInTheCard(each, "the headline", word("🌟 Great job!"))
            listOf("Right first time: 8 of 10", "Streak of 5", "Aim: 2 of 3 stars", "Speed: Turtle").forEach {
                val node = compose.onNodeWithContentDescription(it).assertIsDisplayed()
                assertInside(each, "$summary: $it", node)
                assertInTheCard(each, it, node)
            }
            assertInside(each, "$summary: Pick", pick().assertIsDisplayed(), shadow = 5.dp)
            assertInside(each, "$summary: Again", word("Again").assertIsDisplayed(), shadow = 8.dp)
            assertNear("$each: Again's bottom", each.height.dp - 16.dp, bounds(word("Again")).bottom)
        }
    }

    @Test
    fun the_panel_does_not_move_when_the_streak_starts_or_the_line_speaks() {
        show(asking(60))
        listOf(Phone(915, 411), Phone(640, 360)).forEach { each ->
            on(each)
            compose.runOnIdle { state.value = asking(60) }
            compose.waitForIdle()
            val bubble = bounds(word("C4"))
            compose.runOnIdle { state.value = asking(60).copy(streak = 3, results = listOf(true, true, true), taps = 3) }
            compose.waitForIdle()
            assertEquals("$each: the bubble moved with the streak", bubble, bounds(word("C4")))
            compose.runOnIdle { state.value = state.value.copy(wrongTaps = setOf(62), lastWrongTap = 62, taps = 4) }
            compose.waitForIdle()
            word("🙈 Try again").assertIsDisplayed()
            assertEquals("$each: the bubble moved with the line", bubble, bounds(word("C4")))
        }
    }

    @Test
    fun the_pick_button_shows_a_dot_per_letter() {
        show(asking(60))
        val dots = dotsOfPick()
        // c1 to g1 are five letters: five 11 dp dots, 3 dp apart.
        assertNear("the dots' width", (5 * 11 + 4 * 3).dp, width(dots))
        assertNear("a dot's height", 11.dp, height(dots))
        compose.runOnIdle { state.value = state.value.copy(practiceNotes = setOf(60, 62)) }
        compose.waitForIdle()
        assertNear("two letters", (2 * 11 + 3).dp, width(dotsOfPick()))
        assertTrue("the dots are inside Pick", bounds(dotsOfPick()).bottom <= bounds(pick()).bottom)
    }

    /** All fifteen notes chosen: seven letters, so seven dots. */
    private fun allFifteen(state: NotesUiState) = state.copy(practiceNotes = StaffNotes.ALL.toSet())

    /**
     * What a row of [count] dots of [size] dp comes to on this screen: each dot and each 3 dp gap is
     * rounded to whole pixels on its own, so the row is a dp or two off the plain sum (about 4 dp
     * more at the 800 wide phone's density).
     */
    private fun rowOfDots(count: Int, size: Float): Float =
        (count * (size * density).roundToInt() + (count - 1) * (3f * density).roundToInt()) / density

    /**
     * Pick's dots on each phone, [count] of [size] dp. The Row's bounds are capped by its
     * constraints, so its width being the dots and their gaps (and not less) is what shows that no
     * dot was squeezed. And it keeps its distance from Pick's outline: 9 dp either side, less the
     * pixels the rounding above can add to a row, 3.25 either side for seven dots, and a half for
     * the edges themselves.
     */
    private fun assertTheDotsFit(what: String, size: Float, count: Int, each: Phone) {
        val dots = bounds(dotsOfPick())
        val pick = bounds(pick())
        assertNear("$each, $what: the dots' width", rowOfDots(count, size).dp, width(dotsOfPick()), tolerance = 1f)
        assertNear("$each, $what: a dot's height", size.dp, height(dotsOfPick()), tolerance = 1f)
        val least = 9f - 4f / density
        val left = (dots.left - pick.left).value
        val right = (pick.right - dots.right).value
        assertTrue("$each, $what: the dots start $left dp inside Pick, the least is $least", left >= least)
        assertTrue("$each, $what: the dots end $right dp inside Pick, the least is $least", right >= least)
    }

    /** All fifteen notes on [each] phone: seven dots of [size] dp on the question. */
    private fun assertSevenDotsOnTheQuestion(each: Phone, size: Float) {
        show(allFifteen(asking(60)), at = each)
        assertTheDotsFit("the question", size, 7, each)
    }

    @Test
    fun seven_dots_are_11_dp_and_not_squeezed_at_915_wide() = assertSevenDotsOnTheQuestion(Phone(915, 411), 11f)

    @Test
    fun seven_dots_are_9_dp_and_not_squeezed_at_800_wide() = assertSevenDotsOnTheQuestion(Phone(800, 360), 9f)

    @Test
    fun seven_dots_are_9_dp_and_not_squeezed_at_640_wide() = assertSevenDotsOnTheQuestion(Phone(640, 360), 9f)

    @Test
    fun five_dots_stay_11_dp_on_every_phone() {
        show(asking(60))
        listOf(Phone(915, 411), Phone(800, 360), Phone(640, 360)).forEach { each ->
            on(each)
            assertTheDotsFit("the question", 11f, 5, each)
        }
    }

    @Test
    fun seven_dots_are_not_squeezed_on_the_summary() {
        show(allFifteen(over(8)), at = Phone(640, 360))
        assertTheDotsFit("the summary", 9f, 7, phone)
    }

    /**
     * Pick's word keeps its distance from the outline: its box ends at least 6 dp inside Pick's right
     * edge, less a pixel for the rounding of the padding, and it is not cut short or split.
     */
    private fun assertTheWordClearsTheOutline(fontScale: Float) {
        show(asking(60), locale = Locale("hr"), fontScale = fontScale)
        listOf(Phone(640, 360), Phone(800, 360)).forEach { each ->
            on(each)
            val pick = bounds(compose.onNodeWithContentDescription("Odaberi note").assertIsDisplayed())
            val word = bounds(compose.onNodeWithText("Odaberi", useUnmergedTree = true))
            val room = (pick.right - word.right).value
            val least = 6f - 1f / density
            assertTrue("$each at $fontScale x: \"Odaberi\" ends $room dp inside Pick, the least is $least", room >= least)
            assertWholeWords(each, "Odaberi")
        }
    }

    @Test
    fun odaberi_clears_the_outline() = assertTheWordClearsTheOutline(1f)

    @Test
    fun odaberi_clears_the_outline_at_large_text() = assertTheWordClearsTheOutline(1.5f)

    /**
     * At the largest text the word used to wrap in the 100 dp column: its floor was 9 sp, which doubles
     * with the text and was wider than the room the backpack leaves. The floor is 9 dp now, so the
     * word is whole as well as clear of the outline.
     */
    @Test
    fun odaberi_clears_the_outline_at_the_largest_text() = assertTheWordClearsTheOutline(2f)

    /** The feedback line in each of its three states, whole and in the panel, and the [pills]' words whole, on each phone. */
    private fun assertTheFeedbackFits(locale: Locale, fontScale: Float, correct: String, wrong: String, placing: String, pills: List<String>) {
        show(asking(60), locale = locale, fontScale = fontScale)
        val staffName = if (locale.language == "hr") "Crtovlje, pritisni gdje ide nota i pusti" else staffDescription
        listOf(Phone(915, 411), Phone(800, 360), Phone(640, 360)).forEach { each ->
            on(each)
            val staffNode = compose.onNodeWithContentDescription(staffName)
            val bubble = word(if (locale.language == "hr") "c1" else "C4")
            fun assertInThePanel(line: String) {
                val node = word(line).assertIsDisplayed()
                assertWholeWords(each, line)
                assertInside(each, "\"$line\"", node)
                assertTrue("$each: \"$line\" ends at ${bounds(node).right}, the staff starts at ${bounds(staffNode).left}", bounds(node).right <= bounds(staffNode).left)
                assertTrue("$each: \"$line\" is under the bubble", bounds(node).top >= bounds(bubble).bottom)
            }
            pills.forEach { assertWholeWords(each, it) }
            // Wrong and right are states; the line that speaks while a finger is down is reached by one.
            compose.runOnIdle { state.value = asking(60).copy(wrongTaps = setOf(62), lastWrongTap = 62, taps = 1) }
            compose.waitForIdle()
            assertInThePanel(wrong)
            compose.runOnIdle { state.value = asking(60).copy(solved = true, results = listOf(true), taps = 1, streak = 1) }
            compose.waitForIdle()
            assertInThePanel(correct)
            compose.runOnIdle { state.value = asking(60) }
            compose.waitForIdle()
            staffNode.performTouchInput { down(Offset(width * 0.5f, height * 0.5f)) }
            compose.waitForIdle()
            assertInThePanel(placing)
            staffNode.performTouchInput { up() }
            compose.waitForIdle()
        }
    }

    private val englishPills = listOf("🔎 Find", "👂 Hear")
    private val croatianPills = listOf("🔎 Nađi", "👂 Slušaj")

    @Test
    fun the_feedback_lines_fit_the_panel_in_english() =
        assertTheFeedbackFits(Locale.ENGLISH, 1f, "🎉 That's it!", "🙈 Try again", "👆 Slide, let go", englishPills)

    @Test
    fun the_feedback_lines_fit_the_panel_in_croatian() =
        assertTheFeedbackFits(Locale("hr"), 1f, "🎉 Tako je!", "🙈 Probaj opet", "👆 Povuci i pusti", croatianPills)

    @Test
    fun the_feedback_lines_fit_the_panel_in_croatian_at_large_text() =
        assertTheFeedbackFits(Locale("hr"), 1.5f, "🎉 Tako je!", "🙈 Probaj opet", "👆 Povuci i pusti", croatianPills)

    @Test
    fun the_feedback_lines_fit_the_panel_in_english_at_large_text() =
        assertTheFeedbackFits(Locale.ENGLISH, 1.5f, "🎉 That's it!", "🙈 Try again", "👆 Slide, let go", englishPills)

    /** The four headlines, each by its score, whole and inside the card, on each phone. */
    private fun assertTheHeadlinesFit(locale: Locale, fontScale: Float, headlines: Map<Int, String>) {
        show(over(10), locale = locale, fontScale = fontScale)
        listOf(Phone(915, 411), Phone(800, 360), Phone(640, 360)).forEach { each ->
            on(each)
            headlines.forEach { (score, headline) ->
                compose.runOnIdle { state.value = over(score) }
                compose.waitForIdle()
                val node = word(headline).assertIsDisplayed()
                assertWholeWords(each, headline)
                assertInside(each, "\"$headline\"", node)
                assertInTheCard(each, "\"$headline\"", node)
                // Everything the card holds stays in it, whatever the headline took.
                val label = if (locale.language == "hr") "Točno iz prve: $score od 10" else "Right first time: $score of 10"
                val stars = compose.onNodeWithContentDescription(label).assertIsDisplayed()
                assertInside(each, "$each: the stars", stars)
                assertInTheCard(each, "the stars", stars)
                assertTrue("$each: the stars are under \"$headline\"", bounds(stars).top >= bounds(node).bottom - SLACK)
                // The three tiles (their words are not in the tree, a tile being one description): the streak's is in the card, under the stars.
                val streak = compose.onNodeWithContentDescription(if (locale.language == "hr") "Niz od 5" else "Streak of 5").assertIsDisplayed()
                assertInside(each, "$each: the streak tile", streak)
                assertInTheCardsHeight(each, "the streak tile", streak)
                assertTrue("$each: the tiles are under the stars", bounds(streak).top >= bounds(stars).bottom - SLACK)
            }
        }
    }

    @Test
    fun the_headlines_fit_in_english() = assertTheHeadlinesFit(
        Locale.ENGLISH,
        1f,
        mapOf(10 to "🏆 Amazing!", 7 to "🌟 Great job!", 4 to "👍 Good work!", 0 to "💪 Keep practising!"),
    )

    @Test
    fun the_headlines_fit_in_croatian() = assertTheHeadlinesFit(
        Locale("hr"),
        1f,
        mapOf(10 to "🏆 Nevjerojatno!", 7 to "🌟 Odlično!", 4 to "👍 Dobro!", 0 to "💪 Vježbaj dalje!"),
    )

    @Test
    fun the_headlines_fit_in_croatian_at_large_text() = assertTheHeadlinesFit(
        Locale("hr"),
        1.5f,
        mapOf(10 to "🏆 Nevjerojatno!", 7 to "🌟 Odlično!", 4 to "👍 Dobro!", 0 to "💪 Vježbaj dalje!"),
    )

    @Test
    fun the_headlines_fit_in_english_at_large_text() = assertTheHeadlinesFit(
        Locale.ENGLISH,
        1.5f,
        mapOf(10 to "🏆 Amazing!", 7 to "🌟 Great job!", 4 to "👍 Good work!", 0 to "💪 Keep practising!"),
    )

    @Test
    fun the_headlines_fit_in_croatian_at_the_largest_text() = assertTheHeadlinesFit(
        Locale("hr"),
        2f,
        mapOf(10 to "🏆 Nevjerojatno!", 7 to "🌟 Odlično!", 4 to "👍 Dobro!", 0 to "💪 Vježbaj dalje!"),
    )

    @Test
    fun the_headlines_fit_in_english_at_the_largest_text() = assertTheHeadlinesFit(
        Locale.ENGLISH,
        2f,
        mapOf(10 to "🏆 Amazing!", 7 to "🌟 Great job!", 4 to "👍 Good work!", 0 to "💪 Keep practising!"),
    )

    /** The words of the buttons and the picker's title in one language. */
    private class ButtonWords(
        val locale: Locale,
        val choose: String,
        val pick: String,
        val title: String,
        val sound: String,
        val ok: String,
        val again: String,
        val keys: List<String>,
    )

    private val croatianButtons = ButtonWords(Locale("hr"), "Odaberi note", "Odaberi", "Koje note?", "Zvuk", "U redu", "Opet", listOf("c3", "h1", "c1"))
    private val englishButtons = ButtonWords(Locale.ENGLISH, "Choose notes", "Pick", "Which notes?", "Sound", "OK", "Again", listOf("C6", "B4", "C4"))

    /**
     * What a card may use of the height: the page's 8 dp margin and the card's 16 dp padding either
     * side of it, a pixel of slack for the rounding. A node (and its [shadow]) past it is cut off by the
     * card, or runs onto its edge, which the window's own edge, 24 dp further out, would not show.
     */
    private fun assertInTheCardsHeight(phone: Phone, what: String, node: SemanticsNodeInteraction, shadow: Dp = 0.dp) {
        val b = bounds(node)
        val top = 8.dp + 16.dp - SLACK
        val bottom = phone.height.dp - 8.dp - 16.dp + SLACK
        assertTrue("$phone: $what starts at ${b.top}, the card's inside at $top", b.top >= top)
        assertTrue("$phone: $what ends at ${b.bottom + shadow}, the card's inside at $bottom", b.bottom + shadow <= bottom)
    }

    private fun assertTheButtonsFit(words: ButtonWords, fontScale: Float) {
        show(asking(60), locale = words.locale, fontScale = fontScale)
        listOf(Phone(915, 411), Phone(800, 360), Phone(640, 360)).forEach { each ->
            on(each)
            compose.runOnIdle { state.value = asking(60) }
            compose.waitForIdle()
            val pick = compose.onNodeWithContentDescription(words.choose).assertIsDisplayed()
            assertWholeWords(each, words.pick)
            assertInside(each, "Pick", pick, shadow = 5.dp)
            assertInside(each, "the dots", dotsOfPick().assertIsDisplayed())
            assertTrue("$each: the dots are inside Pick", bounds(dotsOfPick()).right <= bounds(pick).right)
        }
        compose.onNodeWithContentDescription(words.choose).performClick()
        compose.waitForIdle()
        listOf(Phone(915, 411), Phone(800, 360), Phone(640, 360)).forEach { each ->
            on(each)
            val title = word(words.title).assertIsDisplayed()
            assertInside(each, "the title", title)
            assertOneWholeLine(each, words.title)
            assertInTheCardsHeight(each, "the title", title)
            assertInside(each, "Sound", word(words.sound).assertIsDisplayed())
            assertWholeWords(each, words.sound)
            word(words.sound).assertIsOn()
            assertInside(each, "OK", word(words.ok).assertIsDisplayed(), shadow = 8.dp)
            assertWholeWords(each, words.ok)
            // The top row, the one the bottom row's default notes are in, and the bottom row: the title must not take the rows' height.
            words.keys.forEach {
                val key = word(it).assertIsDisplayed()
                assertInside(each, "the key $it", key, shadow = 4.dp)
                assertInTheCardsHeight(each, "the key $it", key, shadow = 4.dp)
            }
        }
        word(words.ok).performClick()
        compose.runOnIdle { state.value = over(8) }
        listOf(Phone(915, 411), Phone(800, 360), Phone(640, 360)).forEach { each ->
            on(each)
            assertInside(each, "Pick", compose.onNodeWithContentDescription(words.choose).assertIsDisplayed(), shadow = 5.dp)
            assertWholeWords(each, words.pick)
            assertInside(each, "Again", word(words.again).assertIsDisplayed(), shadow = 8.dp)
            assertWholeWords(each, words.again)
        }
    }

    @Test
    fun the_buttons_fit_in_croatian_at_large_text() = assertTheButtonsFit(croatianButtons, 1.5f)

    @Test
    fun the_buttons_fit_in_croatian_at_the_largest_text() = assertTheButtonsFit(croatianButtons, 2f)

    /** "Which notes?" fits on one line here even without its `autoSize`, which stays as a guard for a longer title or a wider font. */
    @Test
    fun the_buttons_fit_in_english_at_the_largest_text() = assertTheButtonsFit(englishButtons, 2f)

    @Test
    fun the_buttons_fit_in_english_at_large_text() = assertTheButtonsFit(englishButtons, 1.5f)

    private companion object {
        const val TOLERANCE = 2f
        val SLACK = 1.dp

        /** The game pills' height in the column: 44 dp, and 30 under 400 dp tall, where 38 left the column 4 dp short. */
        val PILL_HEIGHT = 44.dp
        val PILL_HEIGHT_SHORT = 30.dp
    }
}
