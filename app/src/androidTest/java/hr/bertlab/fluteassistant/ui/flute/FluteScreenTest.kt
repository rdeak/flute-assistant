// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.flute

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import hr.bertlab.fluteassistant.domain.flute.Book
import hr.bertlab.fluteassistant.domain.flute.NoteLength
import hr.bertlab.fluteassistant.domain.flute.StepMark
import hr.bertlab.fluteassistant.domain.flute.TuneBreath
import hr.bertlab.fluteassistant.domain.flute.TuneNote
import hr.bertlab.fluteassistant.domain.flute.TuneRest
import hr.bertlab.fluteassistant.domain.model.MetronomeConfig
import hr.bertlab.fluteassistant.domain.model.TimeSignature
import hr.bertlab.fluteassistant.ui.common.LocaleProvider
import hr.bertlab.fluteassistant.ui.common.noteColor
import hr.bertlab.fluteassistant.ui.metronome.MetronomeUiState
import hr.bertlab.fluteassistant.ui.tuner.Hearing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class FluteScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var state: MutableState<FluteUiState>
    private lateinit var metronome: MutableState<MetronomeUiState>
    private val pressed = mutableListOf<Int>()
    private val released = mutableListOf<Int>()
    private val activated = mutableListOf<Int>()
    private var playToggles = 0
    private var erases = 0
    private val restEvents = mutableListOf<String>()
    private var breaths = 0
    private var chosenBpm: Int? = null
    private var chosenSignature: TimeSignature? = null
    private var speedOpens = 0
    private var speedDones = 0
    private var shelfOpens = 0
    private val chosenBooks = mutableListOf<Int>()
    private var yourTurns = 0
    private var stops = 0
    private var micRequests = 0
    private var settingsOpens = 0
    private var micDismissals = 0

    /**
     * Renders [FluteContent] in [locale]. A [height] puts it in a box that tall (a short screen);
     * a [restoration] renders it through that tester, so a test can emulate the Activity's
     * re-creation with `rememberSaveable` state saved and restored.
     */
    private fun show(
        initial: FluteUiState,
        locale: Locale = Locale.ENGLISH,
        height: Dp? = null,
        restoration: StateRestorationTester? = null,
    ) {
        state = mutableStateOf(initial)
        metronome = mutableStateOf(MetronomeUiState(config = MetronomeConfig(bpm = 120)))
        val content: @Composable () -> Unit = {
            LocaleProvider(locale) {
                val flute: @Composable () -> Unit = {
                    FluteContent(
                        state = state.value,
                        metronome = metronome.value,
                        onKeyPressed = { pressed += it },
                        onKeyReleased = { released += it },
                        onKeyActivated = { activated += it },
                        onRestPressed = { restEvents += "press" },
                        onRestReleased = { restEvents += "release" },
                        onRestActivated = { restEvents += "activate" },
                        onBreath = { breaths++ },
                        onTogglePlayback = { playToggles++ },
                        onEraseLast = { erases++ },
                        onBpmChange = { bpm ->
                            chosenBpm = bpm
                            metronome.value = metronome.value.copy(config = metronome.value.config.withBpm(bpm))
                        },
                        onTimeSignatureChange = { chosenSignature = it },
                        onSpeedOpen = { speedOpens++ },
                        onSpeedDone = { speedDones++ },
                        onShelfOpen = { shelfOpens++ },
                        onBookChosen = { chosenBooks += it },
                        onYourTurn = { yourTurns++ },
                        onStop = { stops++ },
                        onRequestMic = { micRequests++ },
                        onOpenSettings = { settingsOpens++ },
                        onMicDismiss = { micDismissals++ },
                    )
                }
                if (height == null) flute() else Box(Modifier.height(height)) { flute() }
            }
        }
        if (restoration != null) restoration.setContent(content) else compose.setContent(content)
    }

    @Test
    fun shows_the_keys_in_octave_rows_highest_on_top_with_a_hint_before_the_first_tap() {
        show(FluteUiState())
        compose.onNodeWithText("👆 Tap or hold a note").assertIsDisplayed()
        compose.onNodeWithContentDescription("Flute keys").assertExists()
        val c4 = compose.onNodeWithContentDescription("C4").getBoundsInRoot()
        val c5 = compose.onNodeWithContentDescription("C5").getBoundsInRoot()
        val c6 = compose.onNodeWithContentDescription("C6").getBoundsInRoot()
        val d4 = compose.onNodeWithContentDescription("D4").getBoundsInRoot()
        assertTrue("C6 $c6 should sit above C5 $c5", c6.bottom <= c5.top)
        assertTrue("C5 $c5 should sit above C4 $c4", c5.bottom <= c4.top)
        assertTrue("the octave rows should share a left edge: $c4 $c5 $c6", abs((c4.left - c5.left).value) <= 1f)
        assertTrue("the octave rows should share a left edge: $c4 $c5 $c6", abs((c5.left - c6.left).value) <= 1f)
        assertTrue("D4 $d4 should be right of C4 $c4", d4.left >= c4.right)
        // 58 dp, as on the canvas; the layout rounds it to whole pixels (152 px, 57.9 dp, on a 2.625 screen).
        assertTrue("a key should be 58 dp tall, was ${c4.height}", abs((c4.height - 58.dp).value) <= 0.5f)
    }

    @Test
    fun pressing_a_key_reports_press_and_release_separately() {
        show(FluteUiState())
        compose.onNodeWithContentDescription("G4").performTouchInput { down(center) }
        assertEquals(listOf(67), pressed)
        assertTrue(released.isEmpty())
        compose.onNodeWithContentDescription("G4").performTouchInput { up() }
        assertEquals(listOf(67), released)
    }

    @Test
    fun a_press_the_system_cancels_still_lets_go_of_the_key() {
        // Palm rejection or a system gesture ends a touch with a cancel, not an up; the ViewModel
        // must hear the release all the same, or the next piece of the hold could not go on with it.
        show(FluteUiState())
        compose.onNodeWithContentDescription("G4").performTouchInput { down(center) }
        compose.onNodeWithContentDescription("G4").performTouchInput { cancel() }
        assertEquals(listOf(67), pressed)
        assertEquals(listOf(67), released)
        compose.onNodeWithContentDescription("Rest").performTouchInput { down(center) }
        compose.onNodeWithContentDescription("Rest").performTouchInput { cancel() }
        assertEquals(listOf("press", "release"), restEvents)
    }

    @Test
    fun the_held_note_is_on_the_staff_and_named_in_the_line() {
        show(
            FluteUiState(
                tune = listOf(TuneNote(67, NoteLength.QUARTER)),
                heldMidi = 67,
                heldIndex = 0,
                shown = Shown.Note(67),
                heldLength = NoteLength.QUARTER,
            ),
        )
        compose.onAllNodesWithText("G4").assertCountEquals(1) // the key; the line's pill is read by its description, the staff is silent
        compose.onNodeWithContentDescription("Fingering for G4").assertDoesNotExist() // the line took the flute drawing's place
        compose.onNodeWithContentDescription("Your tune, 1 note").assertExists()
        compose.onNode(hasContentDescription("G4, 1 beat") and hasAnyAncestor(hasContentDescription("Your tune, 1 note"))).assertExists()
        compose.onNodeWithText("👆 Tap or hold a note").assertDoesNotExist()
    }

    @Test
    fun play_and_erase_are_disabled_while_the_tune_is_empty() {
        show(FluteUiState())
        compose.onNodeWithText("Play").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Erase the last note").assertIsNotEnabled()
        compose.onNodeWithText("👆 Tap or hold a note").assertIsDisplayed()
        state.value = state.value.copy(tune = listOf(TuneNote(60, NoteLength.QUARTER)))
        compose.waitForIdle()
        compose.onNodeWithText("Play").assertIsEnabled()
        compose.onNodeWithContentDescription("Erase the last note").assertIsEnabled()
        compose.onNodeWithText("Play").performClick()
        assertEquals(1, playToggles)
        compose.onNodeWithContentDescription("Erase the last note").performClick()
        assertEquals(1, erases)
    }

    @Test
    fun the_erase_button_sinks_while_pressed() {
        show(FluteUiState(tune = listOf(TuneNote(60, NoteLength.QUARTER))))
        val erase = compose.onNodeWithContentDescription("Erase the last note")
        // A pixel on the button's top edge: its ink outline at rest, empty once it sinks.
        fun topPixel(): Color {
            val pixels = erase.captureToImage().toPixelMap()
            return pixels[pixels.width / 2, with(compose.density) { 1.dp.roundToPx() }]
        }
        val idle = topPixel()
        erase.performTouchInput { down(center) }
        assertNotEquals("the button should sink while held", idle, topPixel())
        erase.performTouchInput { up() }
        assertEquals(idle, topPixel())
    }

    @Test
    fun the_pill_reads_stop_while_playing() {
        show(FluteUiState(tune = listOf(TuneNote(60, NoteLength.QUARTER), TuneNote(67, NoteLength.QUARTER)), playingIndex = 1, shown = Shown.Note(67)))
        compose.onNodeWithText("Stop").assertIsDisplayed()
        compose.onNodeWithText("Play").assertDoesNotExist()
    }

    @Test
    fun the_line_scrolls_to_the_newest_note() {
        val tune = List(29) { TuneNote(if (it % 2 == 0) 60 else 62, NoteLength.QUARTER) } + TuneNote(84, NoteLength.QUARTER)
        show(FluteUiState(tune = tune, heldMidi = 84, heldIndex = 29, shown = Shown.Note(84)))
        compose.onNode(hasContentDescription("C6, 1 beat") and hasAnyAncestor(hasContentDescription("Your tune, 30 notes"))).assertIsDisplayed()
    }

    @Test
    fun the_two_button_labels_fit_a_narrow_phone_in_croatian() {
        state = mutableStateOf(FluteUiState(tune = listOf(TuneNote(60, NoteLength.QUARTER))))
        compose.setContent {
            LocaleProvider(Locale("hr")) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.3f)) {
                    Box(Modifier.width(360.dp)) {
                        FluteContent(
                            state = state.value,
                            metronome = MetronomeUiState(),
                            onKeyPressed = {},
                            onKeyReleased = {},
                            onKeyActivated = {},
                            onRestPressed = {},
                            onRestReleased = {},
                            onRestActivated = {},
                            onBreath = {},
                            onTogglePlayback = {},
                            onEraseLast = {},
                            onBpmChange = {},
                            onTimeSignatureChange = {},
                            onSpeedOpen = {},
                            onSpeedDone = {},
                            onShelfOpen = {},
                            onBookChosen = {},
                            onYourTurn = {},
                            onStop = {},
                            onRequestMic = {},
                            onOpenSettings = {},
                            onMicDismiss = {},
                        )
                    }
                }
            }
        }
        for (label in listOf("Odsviraj", "Poslušaj me")) {
            val layouts = mutableListOf<TextLayoutResult>()
            val node = compose.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode()
            node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
            assertFalse("the $label label should shrink to fit, not be cut short", layouts.single().isLineEllipsized(0))
        }
    }

    @Test
    fun croatian_copy() {
        show(FluteUiState(tune = listOf(TuneNote(60, NoteLength.QUARTER), TuneNote(62, NoteLength.QUARTER), TuneNote(64, NoteLength.QUARTER))), Locale("hr"))
        compose.onNodeWithText("Odsviraj").assertExists()
        compose.onNodeWithText("Poslušaj me").assertExists()
        compose.onNodeWithContentDescription("Obriši zadnju notu").assertExists()
        compose.onNodeWithContentDescription("c1").assertExists()
        compose.onNodeWithContentDescription("Tvoja pjesmica, 3 note").assertExists()
        compose.onNodeWithContentDescription("d1, 1 doba").assertExists()
    }

    @Test
    fun each_note_in_the_line_says_how_long_it_is() {
        show(FluteUiState(tune = listOf(TuneNote(60, NoteLength.EIGHTH), TuneNote(62, NoteLength.HALF), TuneNote(64, NoteLength.WHOLE))))
        compose.onNodeWithContentDescription("C4, half a beat").assertExists()
        compose.onNodeWithContentDescription("D4, 2 beats").assertExists()
        compose.onNodeWithContentDescription("E4, 4 beats").assertExists()
    }

    @Test
    fun note_lengths_are_said_in_croatian() {
        show(FluteUiState(tune = listOf(TuneNote(60, NoteLength.EIGHTH), TuneNote(62, NoteLength.DOTTED_HALF))), Locale("hr"))
        compose.onNodeWithContentDescription("c1, pola dobe").assertExists()
        compose.onNodeWithContentDescription("d1, 3 dobe").assertExists()
    }

    @Test
    fun holding_a_key_does_not_move_the_keys() {
        show(FluteUiState(tune = listOf(TuneNote(60, NoteLength.QUARTER)), shown = Shown.Note(60)))
        val before = compose.onNodeWithContentDescription("C4").getBoundsInRoot()
        state.value = state.value.copy(tune = listOf(TuneNote(60, NoteLength.HALF)), heldMidi = 60, heldIndex = 0, heldLength = NoteLength.HALF)
        compose.onNodeWithContentDescription("C4, 2 beats").assertExists()
        assertEquals(before, compose.onNodeWithContentDescription("C4").getBoundsInRoot())
        state.value = FluteUiState()
        assertEquals(before, compose.onNodeWithContentDescription("C4").getBoundsInRoot())
    }

    @Test
    fun the_rest_breath_and_erase_keys_stand_one_key_wide_at_the_top_row_end() {
        show(FluteUiState())
        val c6 = compose.onNodeWithContentDescription("C6").getBoundsInRoot()
        val c5 = compose.onNodeWithContentDescription("C5").getBoundsInRoot()
        val g5 = compose.onNodeWithContentDescription("G5").getBoundsInRoot()
        val b5 = compose.onNodeWithContentDescription("B5").getBoundsInRoot()
        val breath = compose.onNodeWithContentDescription("Breathe").getBoundsInRoot()
        val rest = compose.onNodeWithContentDescription("Rest").getBoundsInRoot()
        val erase = compose.onNodeWithContentDescription("Erase the last note").getBoundsInRoot()
        for (key in listOf(breath, rest, erase)) {
            assertTrue("$key should sit in C6's row $c6", abs((key.top - c6.top).value) <= 1f && abs((key.bottom - c6.bottom).value) <= 1f)
            assertTrue("$key should be one key wide, C5 is $c5", abs((key.width - c5.width).value) <= 1f)
        }
        assertTrue(
            "C6 $c6, then Rest $rest, Breathe $breath and the sponge $erase, in that order",
            c6.right < rest.left && rest.right < breath.left && breath.right < erase.left,
        )
        // The three stand apart from c3 by more than a key: a tool, not a note, and a slip off c3 reaches nothing.
        assertTrue("Rest $rest should stand clear of C6 $c6 by more than a key", rest.left - c6.right > c5.width)
        assertTrue("Rest $rest should start where G5 $g5 does", abs((rest.left - g5.left).value) <= 1f)
        assertTrue("the sponge $erase should end where B5 $b5 does", abs((erase.right - b5.right).value) <= 1f)
        assertTrue("C6 $c6 should be as wide as C5 $c5", abs((c6.width - c5.width).value) <= 1f)
    }

    @Test
    fun the_rest_key_reports_press_and_release_and_the_breath_key_marks_on_touch() {
        show(FluteUiState())
        compose.onNodeWithContentDescription("Rest").performTouchInput { down(center) }
        assertEquals(listOf("press"), restEvents)
        compose.onNodeWithContentDescription("Rest").performTouchInput { up() }
        assertEquals(listOf("press", "release"), restEvents)
        compose.onNodeWithContentDescription("Rest").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals("activate", restEvents.last())
        compose.onNodeWithContentDescription("Breathe").performTouchInput { down(center) }
        assertEquals(1, breaths)
        compose.onNodeWithContentDescription("Breathe").performTouchInput { up() }
        compose.onNodeWithContentDescription("Breathe").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(2, breaths)
        assertTrue(pressed.isEmpty())
    }

    @Test
    fun rests_and_breaths_in_the_line_say_what_they_are() {
        show(
            FluteUiState(
                tune = listOf(TuneNote(60, NoteLength.QUARTER), TuneRest(NoteLength.EIGHTH), TuneRest(NoteLength.HALF), TuneBreath),
                shown = Shown.Breath,
            ),
        )
        val line = hasAnyAncestor(hasContentDescription("Your tune, 1 note"))
        compose.onNode(hasContentDescription("Rest, half a beat") and line).assertExists()
        compose.onNode(hasContentDescription("Rest, 2 beats") and line).assertExists()
        compose.onNode(hasContentDescription("Breathe here") and line).assertExists()
        compose.onNodeWithContentDescription("Fingering for C4").assertDoesNotExist()
    }

    @Test
    fun a_held_rest_is_written_on_the_staff_with_its_length() {
        show(FluteUiState(tune = listOf(TuneRest(NoteLength.HALF)), restHeld = true, heldIndex = 0, shown = Shown.Rest, heldLength = NoteLength.HALF))
        compose.onAllNodesWithContentDescription("Rest").assertCountEquals(1) // the key
        compose.onNodeWithContentDescription("Rest, 2 beats").assertExists()
    }

    @Test
    fun rests_and_breaths_in_croatian() {
        show(FluteUiState(tune = listOf(TuneRest(NoteLength.QUARTER), TuneRest(NoteLength.EIGHTH), TuneBreath)), Locale("hr"))
        compose.onNodeWithContentDescription("Stanka, 1 doba").assertExists()
        compose.onNodeWithContentDescription("Stanka, pola dobe").assertExists()
        compose.onNodeWithContentDescription("Ovdje udahni").assertExists()
        compose.onNodeWithContentDescription("Udahni").assertExists()
        compose.onNodeWithContentDescription("Stanka").assertExists()
    }

    @Test
    fun the_line_starts_with_the_metronome_time_signature() {
        show(FluteUiState(timeSignature = TimeSignature.THREE_FOUR))
        compose.onNodeWithContentDescription("Time signature 3/4").assertIsDisplayed()
        compose.onNodeWithText("👆 Tap or hold a note").assertIsDisplayed()
        state.value = state.value.copy(tune = listOf(TuneNote(60, NoteLength.QUARTER)), timeSignature = TimeSignature.SIX_EIGHT)
        compose.onNodeWithContentDescription("Time signature 6/8").assertIsDisplayed()
        compose.onNodeWithContentDescription("Time signature 3/4").assertDoesNotExist()
    }

    @Test
    fun a_note_tied_across_a_bar_line_is_read_once_at_its_whole_length() {
        show(FluteUiState(tune = listOf(TuneNote(60, NoteLength.DOTTED_HALF), TuneNote(69, NoteLength.HALF), TuneRest(NoteLength.WHOLE))))
        compose.onAllNodesWithContentDescription("A4, 2 beats").assertCountEquals(1)
        compose.onAllNodesWithContentDescription("Rest, 4 beats").assertCountEquals(1)
    }

    @Test
    fun the_time_signature_in_croatian() {
        show(FluteUiState(timeSignature = TimeSignature.SIX_EIGHT), Locale("hr"))
        compose.onNodeWithContentDescription("Mjera 6/8").assertExists()
    }

    @Test
    fun a_new_time_signature_rewrites_the_staff_without_moving_the_keys() {
        show(FluteUiState(tune = listOf(TuneNote(60, NoteLength.HALF)), heldMidi = 60, heldIndex = 0, shown = Shown.Note(60), heldLength = NoteLength.HALF))
        val before = compose.onNodeWithContentDescription("C4").getBoundsInRoot()
        state.value = state.value.copy(timeSignature = TimeSignature.SIX_EIGHT)
        compose.onNodeWithContentDescription("Time signature 6/8").assertIsDisplayed()
        compose.onNodeWithContentDescription("C4, 2 beats").assertExists()
        assertEquals(before, compose.onNodeWithContentDescription("C4").getBoundsInRoot())
    }

    @Test
    fun the_speed_knob_names_the_drums_animal_and_sits_at_the_staffs_top_left() {
        show(FluteUiState(tune = listOf(TuneNote(60, NoteLength.QUARTER))))
        val knob = compose.onNodeWithContentDescription("Speed: Rabbit, tap to change")
        knob.assertIsDisplayed()
        val knobBounds = knob.getBoundsInRoot()
        // The tune's row starts NOTES_CLIP (57 dp) into the staff, so the knob, 54 dp from the staff's left
        // edge, ends before it; the time signature stands lower, between the first and third lines.
        val tuneRow = compose.onNodeWithContentDescription("Your tune, 1 note").getBoundsInRoot()
        val signature = compose.onNodeWithContentDescription("Time signature 4/4").getBoundsInRoot()
        assertTrue("the knob $knobBounds should end before the notes start $tuneRow", knobBounds.right <= tuneRow.left + 1.dp)
        assertTrue("the knob $knobBounds should sit above the time signature $signature", knobBounds.bottom <= signature.top + 1.dp)
        assertTrue("the knob $knobBounds should start left of the time signature $signature", knobBounds.left < signature.left)
        assertTrue("the knob should be 54 dp, was ${knobBounds.width}", abs((knobBounds.width - 54.dp).value) <= 0.5f)
        compose.runOnIdle { metronome.value = MetronomeUiState(config = MetronomeConfig(bpm = 60)) }
        compose.onNodeWithContentDescription("Speed: Turtle, tap to change").assertIsDisplayed()
    }

    @Test
    fun the_speed_knob_shows_on_an_empty_tune_too() {
        show(FluteUiState())
        compose.onNodeWithContentDescription("Speed: Rabbit, tap to change").assertIsDisplayed()
        compose.onNodeWithText("👆 Tap or hold a note").assertIsDisplayed()
        compose.onNodeWithContentDescription("Speed: Rabbit, tap to change").performClick()
        assertEquals(1, speedOpens)
    }

    @Test
    fun the_knob_speaks_croatian() {
        show(FluteUiState(), Locale("hr"))
        compose.onNodeWithContentDescription("Brzina: Zec, dodirni za promjenu").assertIsDisplayed()
    }

    /** The knob as it is drawn; the emoji have no semantics, so its pixels are how a test sees them. */
    private fun knobPixels(animal: String): PixelMap =
        compose.onNodeWithContentDescription("Speed: $animal, tap to change").captureToImage().toPixelMap()

    private fun setSpeed(bpm: Int) = compose.runOnIdle { metronome.value = MetronomeUiState(config = MetronomeConfig(bpm = bpm)) }

    /** Where the badge starts in a [knob] of 54 dp: 28 dp from its top and from its left. */
    private fun badgeStart(knob: PixelMap) = knob.width * 28 / 54

    @Test
    fun the_speed_changes_only_the_knobs_badge_and_the_drum_stays_as_it_is() {
        show(FluteUiState())
        val rabbit = knobPixels("Rabbit")
        setSpeed(60)
        val turtle = knobPixels("Turtle")
        val start = badgeStart(rabbit)
        for (x in 0 until start) {
            for (y in 0 until start) assertEquals("($x, $y) is the drum's", rabbit[x, y], turtle[x, y])
        }
        val badge = (start until rabbit.width).flatMap { x -> (start until rabbit.height).map { y -> x to y } }
        assertTrue("the badge should show the speed's animal", badge.any { (x, y) -> rabbit[x, y] != turtle[x, y] })
    }

    @Test
    fun the_knob_keeps_its_drum_and_loses_its_animal_while_the_tune_runs() {
        show(FluteUiState(tune = twoNotes, isListening = true, listenIndex = 0))
        val rabbit = knobPixels("Rabbit")
        setSpeed(60)
        val turtle = knobPixels("Turtle")
        for (x in 0 until rabbit.width) {
            for (y in 0 until rabbit.height) assertEquals("($x, $y) should not follow the speed", rabbit[x, y], turtle[x, y])
        }
        // An empty disc is one colour inside its ring; the drum is not.
        val middle = rabbit.width / 3 until rabbit.width * 2 / 3
        val colours = middle.flatMap { x -> middle.map { y -> rabbit[x, y] } }.toSet()
        assertTrue("the drum should stay on the knob, found ${colours.size} colour(s)", colours.size > 1)
    }

    @Test
    fun a_tap_on_the_knobs_badge_opens_the_speed_panel() {
        show(FluteUiState())
        compose.onNodeWithContentDescription("Speed: Rabbit, tap to change").performTouchInput {
            click(Offset(width * 0.78f, height * 0.78f)) // the badge's middle, 42 dp in
        }
        assertEquals(1, speedOpens)
    }

    @Test
    fun the_knobs_badge_sinks_with_the_knob() {
        show(FluteUiState())
        val knob = compose.onNodeWithContentDescription("Speed: Rabbit, tap to change")
        val top = knob.getBoundsInRoot().top
        val rest = knob.captureToImage().toPixelMap()
        knob.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(200) // past the press delay of a scrolling column
        assertTrue("the knob should sink", knob.getBoundsInRoot().top > top)
        // The capture follows the sunk knob, so a badge that went with it lands on the same pixels.
        val pressed = knob.captureToImage().toPixelMap()
        knob.performTouchInput { up() }
        val start = badgeStart(rest)
        val width = minOf(rest.width, pressed.width)
        val height = minOf(rest.height, pressed.height)
        val badge = (start until width).flatMap { x -> (start until height).map { y -> x to y } }
        val moved = badge.count { (x, y) ->
            val a = rest[x, y]
            val b = pressed[x, y]
            abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue) > 0.5f
        }
        assertTrue("$moved of ${badge.size} pixels of the badge stayed behind", moved < badge.size / 12)
    }

    @Test
    fun the_book_button_shows_the_open_books_sweet_and_opens_the_shelf() {
        show(FluteUiState(openBook = 1))
        compose.onNodeWithContentDescription("Tune: Cookie, tap to change").assertIsEnabled().performClick()
        assertEquals(1, shelfOpens)
    }

    @Test
    fun the_book_button_is_inert_while_the_tune_runs() {
        show(FluteUiState(tune = listOf(TuneNote(60, NoteLength.QUARTER)), playingIndex = 0))
        compose.onNodeWithContentDescription("Tune: Lollipop, tap to change").assertIsNotEnabled().performClick()
        assertEquals(0, shelfOpens)
        compose.onNodeWithContentDescription("Lollipop, open now").assertDoesNotExist()
    }

    @Test
    fun the_corner_buttons_tell_a_screen_reader_they_are_off_once_the_tune_starts() {
        show(FluteUiState(tune = listOf(TuneNote(60, NoteLength.QUARTER))))
        val book = "Tune: Lollipop, tap to change"
        val knob = "Speed: Rabbit, tap to change"
        assertTrue("the book at rest", accessibilityEnabled(book))
        assertTrue("the knob at rest", accessibilityEnabled(knob))
        state.value = state.value.copy(playingIndex = 0)
        compose.waitForIdle()
        compose.onNodeWithContentDescription(book).assertIsNotEnabled()
        compose.onNodeWithContentDescription(knob).assertIsNotEnabled()
        assertFalse("a screen reader is told the book is on while the tune runs", accessibilityEnabled(book))
        assertFalse("a screen reader is told the knob is on while the tune runs", accessibilityEnabled(knob))
    }

    /**
     * Whether Android's accessibility layer reports the button described as [description] as
     * enabled, read from the merged button a screen reader focuses. Not from a `uiautomator dump`
     * matched by `content-desc`: when a merged button has children, Compose adds a child node
     * holding only the description (`SemanticsNode.emitFakeNodes`), and that child always reads
     * enabled. The book's cover has its sweet as a child; the knob loses its animal while the tune
     * runs, which is why a dump showed the knob off and the book on.
     */
    private fun accessibilityEnabled(description: String): Boolean {
        val node = compose.onNodeWithContentDescription(description).fetchSemanticsNode()
        val view = (node.root as ViewRootForTest).view
        var enabled: Boolean? = null
        compose.runOnUiThread {
            enabled = view.accessibilityNodeProvider!!.createAccessibilityNodeInfo(node.id)!!.isEnabled
        }
        return enabled!!
    }

    @Test
    fun the_book_button_speaks_croatian() {
        show(FluteUiState(openBook = 3), locale = Locale("hr"))
        compose.onNodeWithContentDescription("Pjesmica: Krafna, dodirni za promjenu").assertExists()
    }

    @Test
    fun the_book_button_sits_under_the_clef_left_of_the_notes() {
        show(FluteUiState(tune = listOf(TuneNote(60, NoteLength.QUARTER))))
        // The node is the book's cover, the pressable part; its page edges stand 6 dp further right.
        val cover = compose.onNodeWithContentDescription("Tune: Lollipop, tap to change").getBoundsInRoot()
        val bookRight = cover.right + 6.dp
        val knob = compose.onNodeWithContentDescription("Speed: Rabbit, tap to change").getBoundsInRoot()
        // The tune's row starts NOTES_CLIP (57 dp) into the staff; the book, the knob's twin one corner
        // down, stands in the same column left of it, under the time signature, beside the names.
        val tuneRow = compose.onNodeWithContentDescription("Your tune, 1 note").getBoundsInRoot()
        val signature = compose.onNodeWithContentDescription("Time signature 4/4").getBoundsInRoot()
        assertTrue("the book $cover should sit below the time signature $signature", cover.top >= signature.bottom - 1.dp)
        assertTrue("the book, page edges and all, ending at $bookRight, should end before the notes start $tuneRow", bookRight <= tuneRow.left + 1.dp)
        assertTrue("the book, ending at $bookRight, should end no further right than the knob $knob", bookRight <= knob.right + 1.dp)
        assertTrue("the cover should be 48 dp wide, was ${cover.width}", abs((cover.width - 48.dp).value) <= 0.5f)
        assertTrue("the cover should be 64 dp tall, was ${cover.height}", abs((cover.height - 64.dp).value) <= 0.5f)
    }

    // ---- The shelf ----

    /** Six books, all empty but the cookie's, which holds three notes. */
    private val threeNoteCookie = List(6) { Book() }.toMutableList().also {
        it[1] = Book(listOf(TuneNote(60, NoteLength.QUARTER), TuneNote(62, NoteLength.QUARTER), TuneNote(64, NoteLength.QUARTER)))
    }

    @Test
    fun the_shelf_takes_the_stage_with_six_books() {
        show(FluteUiState(tune = listOf(TuneNote(67, NoteLength.HALF)), books = threeNoteCookie))
        compose.onNodeWithContentDescription("Tune: Lollipop, tap to change").performClick()
        assertEquals(1, shelfOpens)
        compose.onNodeWithContentDescription("Flute keys").assertDoesNotExist()
        compose.onNodeWithContentDescription("Your tune, 1 note").assertDoesNotExist()
        compose.onNodeWithText("Play").assertDoesNotExist()
        listOf("Lollipop, open now", "Cookie, 3 notes", "Cupcake, empty", "Donut, empty", "Ice cream, empty", "Candy, empty")
            .forEach { compose.onNodeWithContentDescription(it).assertExists() }
    }

    @Test
    fun a_tap_on_a_book_chooses_it_and_closes_the_shelf() {
        show(FluteUiState(tune = listOf(TuneNote(67, NoteLength.HALF)), books = threeNoteCookie))
        compose.onNodeWithContentDescription("Tune: Lollipop, tap to change").performClick()
        compose.onNodeWithContentDescription("Cookie, 3 notes").performClick()
        assertEquals(listOf(1), chosenBooks)
        compose.onNodeWithContentDescription("Flute keys").assertExists()
        compose.onNodeWithContentDescription("Cookie, 3 notes").assertDoesNotExist()
    }

    @Test
    fun a_tap_on_the_open_book_closes_the_shelf() {
        show(FluteUiState(tune = listOf(TuneNote(67, NoteLength.HALF)), books = threeNoteCookie))
        compose.onNodeWithContentDescription("Tune: Lollipop, tap to change").performClick()
        compose.onNodeWithContentDescription("Lollipop, open now").performClick()
        assertEquals(listOf(0), chosenBooks)
        compose.onNodeWithContentDescription("Flute keys").assertExists()
        compose.onNodeWithContentDescription("Lollipop, open now").assertDoesNotExist()
    }

    @Test
    fun the_shelf_survives_re_creation() {
        val restoration = StateRestorationTester(compose)
        show(FluteUiState(), restoration = restoration)
        compose.onNodeWithContentDescription("Tune: Lollipop, tap to change").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithContentDescription("Lollipop, open now").assertExists()
        compose.onNodeWithContentDescription("Flute keys").assertDoesNotExist()
    }

    @Test
    fun the_shelf_speaks_croatian() {
        show(FluteUiState(books = threeNoteCookie), locale = Locale("hr"))
        compose.onNodeWithContentDescription("Pjesmica: Lizalica, dodirni za promjenu").performClick()
        compose.onNodeWithContentDescription("Lizalica, otvorena pjesmica").assertExists()
        compose.onNodeWithContentDescription("Keks, 3 note").assertExists()
        compose.onNodeWithContentDescription("Muffin, prazna pjesmica").assertExists()
    }

    @Test
    fun the_shelf_fits_a_short_phone_without_scrolling() {
        // 470 dp is about what a 360 x 640 dp phone leaves the tab: above the shelf's minimum, so all six show.
        show(FluteUiState(books = threeNoteCookie), height = 470.dp)
        compose.onNodeWithContentDescription("Tune: Lollipop, tap to change").performClick()
        compose.onNodeWithContentDescription("Lollipop, open now").assertIsDisplayed()
        compose.onNodeWithContentDescription("Candy, empty").assertIsDisplayed()
    }

    @Test
    fun the_shelf_reaches_its_last_book_on_a_short_screen() {
        // Below the shelf's minimum the books keep their height and the shelf scrolls.
        show(FluteUiState(books = threeNoteCookie), height = 340.dp)
        compose.onNodeWithContentDescription("Tune: Lollipop, tap to change").performClick()
        compose.onNodeWithContentDescription("Candy, empty").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun tapping_the_knob_opens_the_drums_stage_in_place_and_ok_brings_the_tune_back() {
        show(FluteUiState(tune = listOf(TuneNote(60, NoteLength.QUARTER))))
        compose.onNodeWithContentDescription("Speed: Rabbit, tap to change").performClick()
        assertEquals(1, speedOpens)
        compose.onNodeWithContentDescription("Tempo ring, drag around it to change the speed").assertIsDisplayed()
        compose.onNodeWithContentDescription("Beats in a bar: 4").assertIsDisplayed()
        compose.onNodeWithText("OK").assertIsDisplayed()
        compose.onNodeWithContentDescription("Flute keys").assertDoesNotExist()
        compose.onNodeWithText("Play").assertDoesNotExist()
        compose.onNodeWithContentDescription("Erase the last note").assertDoesNotExist()
        compose.onNodeWithContentDescription("Your tune, 1 note").assertDoesNotExist()

        compose.onNodeWithContentDescription("More beats").performClick()
        assertEquals(TimeSignature.FIVE_FOUR, chosenSignature)
        compose.onNodeWithContentDescription("Duck").performClick()
        assertEquals(80, chosenBpm)

        compose.onNodeWithText("OK").performClick()
        assertEquals(1, speedDones)
        compose.onNodeWithContentDescription("Tempo ring, drag around it to change the speed").assertDoesNotExist()
        compose.onNodeWithContentDescription("Flute keys").assertExists()
        compose.onNodeWithText("Play").assertIsDisplayed()
        compose.onNodeWithContentDescription("Your tune, 1 note").assertExists()
        compose.onNodeWithContentDescription("Speed: Duck, tap to change").assertIsDisplayed()
    }

    @Test
    fun the_speed_panel_survives_recreation() {
        val restoration = StateRestorationTester(compose)
        show(FluteUiState(tune = listOf(TuneNote(60, NoteLength.QUARTER))), restoration = restoration)
        compose.onNodeWithContentDescription("Speed: Rabbit, tap to change").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithContentDescription("Tempo ring, drag around it to change the speed").assertIsDisplayed()
        compose.onNodeWithText("OK").assertIsDisplayed()
    }

    @Test
    fun the_speed_panel_fits_a_short_screen() {
        // Short, but above the drum's 420 dp minimum, so the panel does not scroll: the ring shrinks.
        show(FluteUiState(tune = listOf(TuneNote(60, NoteLength.QUARTER))), height = 500.dp)
        compose.onNodeWithContentDescription("Speed: Rabbit, tap to change").performClick()
        compose.onNodeWithText("OK").assertIsDisplayed()
        val ring = compose.onNodeWithContentDescription("Tempo ring, drag around it to change the speed")
        ring.assertIsDisplayed()
        assertTrue("the ring should shrink below 304 dp on a short screen, was ${ring.getBoundsInRoot().height}", ring.getBoundsInRoot().height < 304.dp)
    }

    // ---- Your turn ----

    private val twoNotes = listOf(TuneNote(60, NoteLength.QUARTER), TuneNote(67, NoteLength.QUARTER))

    @Test
    fun your_turn_sits_beside_play_and_needs_a_note() {
        show(FluteUiState(tune = listOf(TuneRest(NoteLength.QUARTER))))
        compose.onNodeWithText("Play").assertIsEnabled()
        compose.onNodeWithText("Listen to me").assertIsDisplayed()
        compose.onNodeWithText("Listen to me").assertIsNotEnabled()
        val play = compose.onNodeWithText("Play").getBoundsInRoot()
        val yourTurn = compose.onNodeWithText("Listen to me").getBoundsInRoot()
        val keys = compose.onNodeWithContentDescription("Flute keys").getBoundsInRoot()
        assertTrue("Your turn $yourTurn should sit right of Play $play", yourTurn.left >= play.right)
        assertEquals("both big buttons share the height", play.height, yourTurn.height)
        assertTrue("both big buttons share the width: $play $yourTurn", abs((play.width - yourTurn.width).value) <= 1f)
        assertTrue(
            "Play $play and Your turn $yourTurn should span the keys' width $keys",
            abs((play.left - keys.left).value) <= 1f && abs((yourTurn.right - keys.right).value) <= 1f,
        )
        state.value = state.value.copy(tune = twoNotes)
        compose.onNodeWithText("Listen to me").assertIsEnabled()
        compose.onNodeWithText("Listen to me").performClick()
        assertEquals(1, yourTurns)
    }

    @Test
    fun while_the_tune_runs_the_keys_and_the_sponge_are_gone_and_stop_is_wide() {
        show(FluteUiState(tune = twoNotes, playingIndex = 0, shown = Shown.Note(60)))
        fun checkRunning() {
            compose.onNodeWithText("Stop").assertIsDisplayed()
            compose.onNodeWithText("Play").assertDoesNotExist()
            compose.onNodeWithText("Listen to me").assertDoesNotExist()
            compose.onNodeWithContentDescription("Flute keys").assertDoesNotExist()
            compose.onNodeWithContentDescription("Erase the last note").assertDoesNotExist()
            val stop = compose.onNodeWithText("Stop").getBoundsInRoot()
            val root = compose.onRoot().getBoundsInRoot()
            assertTrue("Stop $stop should span the row of $root", stop.width > root.width * 0.8f)
        }
        checkRunning()
        compose.onNodeWithText("Stop").performClick()
        assertEquals(1, stops)
        state.value = FluteUiState(tune = twoNotes, isListening = true, listenIndex = 0)
        checkRunning()
        compose.onNodeWithText("Stop").performClick()
        assertEquals(2, stops)
        assertEquals(0, playToggles)
    }

    @Test
    fun the_staff_grows_while_the_tune_runs() {
        show(FluteUiState(tune = twoNotes))
        val rest = compose.onNodeWithContentDescription("Your tune, 2 notes").getBoundsInRoot()
        assertTrue("at rest the staff is ${rest.height}, expected $TuneStaffHeight", abs((rest.height - TuneStaffHeight).value) <= 1f)
        state.value = state.value.copy(isListening = true, listenIndex = 0)
        val running = compose.onNodeWithContentDescription("Your tune, 2 notes").getBoundsInRoot()
        val expected = tuneStaffHeight(RunningStaffScale)
        assertTrue("running the staff is ${running.height}, expected $expected", abs((running.height - expected).value) <= 1f)
    }

    @Test
    fun marks_and_the_waiting_animal_read_on_the_staff() {
        show(FluteUiState(tune = twoNotes, isListening = true, listenIndex = 1, marks = mapOf(0 to StepMark.FIRST_TRY)))
        compose.onNodeWithContentDescription("C4, 1 beat, got it").assertExists()
        compose.onNodeWithContentDescription("G4, 1 beat, play this one").assertExists()
        state.value = state.value.copy(wrong = WrongNote(index = 1, midi = 69))
        compose.onNodeWithContentDescription("G4, 1 beat, heard A4").assertExists()
        state.value = state.value.copy(isListening = false, listenIndex = null, wrong = null, marks = mapOf(0 to StepMark.FIRST_TRY, 1 to StepMark.RETRY))
        compose.onNodeWithContentDescription("C4, 1 beat, got it").assertExists()
        compose.onNodeWithContentDescription("G4, 1 beat, got it after a retry").assertExists()
        compose.onNodeWithContentDescription("Flute keys").assertExists()
    }

    /**
     * The pixels of the marks row under the first beat of a captured step: where its star, the
     * note heard or the listening dots are drawn, [StaffGeometry.MARKS_TOP] down and
     * [StaffGeometry.BEAT_WIDTH] wide, at the running scale.
     */
    private fun marksRow(step: PixelMap): List<Color> {
        val s = RunningStaffScale
        val density = compose.density
        val top = with(density) { (StaffGeometry.MARKS_TOP * s).dp.roundToPx() }
        val bottom = minOf(step.height, with(density) { ((StaffGeometry.MARKS_TOP + StaffGeometry.MARKS_HEIGHT) * s).dp.roundToPx() })
        val right = minOf(step.width, with(density) { (StaffGeometry.BEAT_WIDTH * s).dp.roundToPx() })
        return (0 until right).flatMap { x -> (top until bottom).map { y -> step[x, y] } }
    }

    private fun Color.near(other: Color) = abs(red - other.red) + abs(green - other.green) + abs(blue - other.blue) < 0.15f

    @Test
    fun the_note_heard_now_shows_live_under_the_waited_step_and_dots_while_nothing_is_heard() {
        show(FluteUiState(tune = twoNotes, isListening = true, listenIndex = 1, marks = mapOf(0 to StepMark.FIRST_TRY)))
        val waited = compose.onNodeWithContentDescription("G4, 1 beat, play this one")
        val a = noteColor(69)
        val silent = marksRow(waited.captureToImage().toPixelMap())
        val card = silent.first()
        assertTrue("in silence the row holds the listening dots, not an A bubble", silent.none { it.near(a) })
        assertTrue("in silence the row shows the listening dots' ring, not nothing", silent.count { !it.near(card) } > silent.size / 20)
        state.value = state.value.copy(heardNow = 69)
        val hearing = marksRow(waited.captureToImage().toPixelMap())
        assertTrue("the A heard now should fill a bubble under the waited step", hearing.count { it.near(a) } > hearing.size / 3)
        compose.onNodeWithContentDescription("G4, 1 beat, play this one").assertExists() // a screen reader is not told of the passing note
        state.value = state.value.copy(heardNow = null, isListening = false, listenIndex = null)
        val rest = marksRow(compose.onNodeWithContentDescription("G4, 1 beat").captureToImage().toPixelMap())
        assertTrue("at rest the row under a step with no mark is empty", rest.all { it.near(rest.first()) })
    }

    @Test
    fun the_staff_keeps_the_note_just_played_in_view_before_the_lit_one() {
        // Thirty quarter notes, far more than the running staff shows at once, so the lit one lands mid-tune.
        val tune = MutableList(30) { TuneNote(if (it % 2 == 0) 60 else 62, NoteLength.QUARTER) }
        tune[18] = TuneNote(84, NoteLength.QUARTER) // C6: the note before the one just played
        tune[19] = TuneNote(83, NoteLength.QUARTER) // B5: just played, with its star
        tune[20] = TuneNote(81, NoteLength.QUARTER) // A5: waited on now
        show(FluteUiState(tune = tune, isListening = true, listenIndex = 20, marks = mapOf(19 to StepMark.FIRST_TRY)))
        val line = hasAnyAncestor(hasContentDescription("Your tune, 30 notes"))
        assertWhollyShown(hasContentDescription("B5, 1 beat, got it") and line)
        assertWhollyShown(hasContentDescription("A5, 1 beat, play this one") and line)
        assertNotWhollyShown(hasContentDescription("C6, 1 beat") and line)
    }

    /** The one node matching [matcher] is on screen with none of it clipped away by the staff's edge. */
    private fun assertWhollyShown(matcher: SemanticsMatcher) {
        val node = compose.onNode(matcher).fetchSemanticsNode()
        assertTrue("${matcher.description}: ${node.boundsInRoot} shows less than its ${node.size}", node.boundsInRoot.width >= node.size.width - 1f)
    }

    /** No node matching [matcher] is on screen in full: a step scrolled off the staff may still exist, unplaced or clipped to a sliver. */
    private fun assertNotWhollyShown(matcher: SemanticsMatcher) {
        val whole = compose.onAllNodes(matcher).fetchSemanticsNodes()
            .filter { it.layoutInfo.isPlaced && it.boundsInRoot.width >= it.size.width - 1f }
        assertTrue("${matcher.description} is wholly on screen", whole.isEmpty())
    }

    @Test
    fun the_advice_line_speaks_only_while_the_sound_is_unclear() {
        show(FluteUiState(tune = twoNotes, isListening = true, listenIndex = 0, hearing = Hearing.LOUD_UNCLEAR))
        compose.onNodeWithText("🎵 Play one long, clear note").assertIsDisplayed()
        state.value = state.value.copy(hearing = Hearing.QUIET_UNCLEAR)
        compose.onNodeWithText("📱 Come closer to the phone").assertIsDisplayed()
        state.value = state.value.copy(hearing = Hearing.CLEAR)
        compose.onNodeWithText("📱 Come closer to the phone").assertDoesNotExist()
        compose.onNodeWithText("🎵 Play one long, clear note").assertDoesNotExist()
        state.value = FluteUiState(tune = twoNotes, playingIndex = 0, shown = Shown.Note(60))
        compose.onNodeWithText("🎵 Play one long, clear note").assertDoesNotExist()
    }

    /** A faded button's shadow, [depth] deep, must show right down under the middle of its bottom edge. */
    private fun assertWholeShadowUnder(button: SemanticsNodeInteraction, depth: Dp) {
        val bounds = button.getBoundsInRoot()
        val screen = compose.onRoot().captureToImage().toPixelMap()
        val pixels = { dp: Dp -> with(compose.density) { dp.toPx() }.roundToInt() }
        val x = pixels((bounds.left + bounds.right) / 2)
        val shadow = screen[x, pixels(bounds.bottom + depth - 1.dp)]
        val under = screen[x, pixels(bounds.bottom + depth + 3.dp)]
        assertTrue(
            "the shadow's last row $shadow should be darker than what is under it $under",
            shadow.luminance() < under.luminance() - 0.1f,
        )
    }

    @Test
    fun the_faded_knob_and_book_keep_their_whole_shadow_while_the_tune_runs() {
        show(FluteUiState(tune = twoNotes, isListening = true, listenIndex = 0))
        assertWholeShadowUnder(compose.onNodeWithContentDescription("Speed: Rabbit, tap to change"), 5.dp)
        assertWholeShadowUnder(compose.onNodeWithContentDescription("Tune: Lollipop, tap to change"), 5.dp)
    }

    @Test
    fun the_faded_keys_and_buttons_of_an_empty_tune_keep_their_whole_shadow() {
        show(FluteUiState())
        assertWholeShadowUnder(compose.onNodeWithContentDescription("Erase the last note"), 5.dp)
        assertWholeShadowUnder(compose.onNodeWithText("Play"), 8.dp)
        assertWholeShadowUnder(compose.onNodeWithText("Listen to me"), 8.dp)
    }

    @Test
    fun the_knob_is_inert_while_the_tune_runs() {
        show(FluteUiState(tune = twoNotes, isListening = true, listenIndex = 0))
        val knob = compose.onNodeWithContentDescription("Speed: Rabbit, tap to change")
        knob.assertIsDisplayed()
        knob.assertIsNotEnabled()
        knob.performClick()
        assertEquals(0, speedOpens)
        state.value = FluteUiState(tune = twoNotes)
        compose.onNodeWithContentDescription("Speed: Rabbit, tap to change").assertIsEnabled()
    }

    @Test
    fun a_denied_microphone_shows_the_card_with_a_way_back() {
        show(FluteUiState(tune = twoNotes, micDenied = true))
        compose.onNodeWithText("Can I listen?").assertIsDisplayed()
        compose.onNodeWithText("Yes").assertIsDisplayed()
        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.onNodeWithText("Not now").assertIsDisplayed()
        compose.onNodeWithContentDescription("Flute keys").assertDoesNotExist()
        compose.onNodeWithText("Play").assertDoesNotExist()
        compose.onNodeWithText("Listen to me").assertDoesNotExist()
        compose.onNodeWithText("Yes").performClick()
        assertEquals(1, micRequests)
        compose.onNodeWithText("Settings").performClick()
        assertEquals(1, settingsOpens)
        compose.onNodeWithText("Not now").performClick()
        assertEquals(1, micDismissals)
        state.value = state.value.copy(micDenied = false)
        compose.onNodeWithContentDescription("Flute keys").assertExists()
        compose.onNodeWithText("Listen to me").assertIsDisplayed()
    }

    @Test
    fun croatian_mic_card() {
        show(FluteUiState(tune = twoNotes, micDenied = true), Locale("hr"))
        compose.onNodeWithText("Smijem li slušati?").assertIsDisplayed()
        compose.onNodeWithText("Da").assertIsDisplayed()
        compose.onNodeWithText("Postavke").assertIsDisplayed()
        compose.onNodeWithText("Ne sad").assertIsDisplayed()
    }
}
