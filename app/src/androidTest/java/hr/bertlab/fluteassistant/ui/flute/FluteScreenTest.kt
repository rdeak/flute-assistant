package hr.bertlab.fluteassistant.ui.flute

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import hr.bertlab.fluteassistant.domain.flute.NoteLength
import hr.bertlab.fluteassistant.domain.flute.TuneBreath
import hr.bertlab.fluteassistant.domain.flute.TuneNote
import hr.bertlab.fluteassistant.domain.flute.TuneRest
import hr.bertlab.fluteassistant.domain.model.MetronomeConfig
import hr.bertlab.fluteassistant.domain.model.TimeSignature
import hr.bertlab.fluteassistant.ui.common.LocaleProvider
import hr.bertlab.fluteassistant.ui.metronome.MetronomeUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import kotlin.math.abs

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
    fun the_play_label_fits_a_narrow_phone_in_croatian() {
        state = mutableStateOf(FluteUiState(tune = listOf(TuneNote(60, NoteLength.QUARTER))))
        compose.setContent {
            LocaleProvider(Locale("hr")) {
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
                    )
                }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        val node = compose.onNodeWithText("Sviraj", useUnmergedTree = true).fetchSemanticsNode()
        node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        assertFalse("the Play label should shrink to fit, not be cut short", layouts.single().isLineEllipsized(0))
    }

    @Test
    fun croatian_copy() {
        show(FluteUiState(tune = listOf(TuneNote(60, NoteLength.QUARTER), TuneNote(62, NoteLength.QUARTER), TuneNote(64, NoteLength.QUARTER))), Locale("hr"))
        compose.onNodeWithText("Sviraj").assertExists()
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
    fun the_breath_and_rest_keys_end_the_top_row() {
        show(FluteUiState())
        val c6 = compose.onNodeWithContentDescription("C6").getBoundsInRoot()
        val c5 = compose.onNodeWithContentDescription("C5").getBoundsInRoot()
        val b5 = compose.onNodeWithContentDescription("B5").getBoundsInRoot()
        val breath = compose.onNodeWithContentDescription("Breathe").getBoundsInRoot()
        val rest = compose.onNodeWithContentDescription("Rest").getBoundsInRoot()
        assertTrue("the keys should sit in C6's row: $c6 $breath $rest", abs((breath.top - c6.top).value) <= 1f && abs((rest.top - c6.top).value) <= 1f)
        assertTrue("Breathe $breath should come after C6 $c6, Rest $rest after it", c6.right < breath.left && breath.right < rest.left)
        assertTrue("Rest $rest should end where B5 $b5 does", abs((rest.right - b5.right).value) <= 1f)
        assertTrue("C6 $c6 should be as wide as C5 $c5", abs((c6.width - c5.width).value) <= 1f)
        assertTrue("Rest $rest should be two keys wide", rest.width > c5.width * 2)
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
}
