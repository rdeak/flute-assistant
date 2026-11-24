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
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import hr.bertlab.fluteassistant.domain.flute.Book
import hr.bertlab.fluteassistant.domain.flute.Held
import hr.bertlab.fluteassistant.domain.flute.Listening
import hr.bertlab.fluteassistant.domain.flute.NoteLength
import hr.bertlab.fluteassistant.domain.flute.Pace
import hr.bertlab.fluteassistant.domain.flute.Pitch
import hr.bertlab.fluteassistant.domain.flute.StepMark
import hr.bertlab.fluteassistant.domain.flute.TuneBreath
import hr.bertlab.fluteassistant.domain.flute.TuneNote
import hr.bertlab.fluteassistant.domain.flute.TuneRest
import hr.bertlab.fluteassistant.domain.flute.Walk
import hr.bertlab.fluteassistant.domain.flute.WrongNote
import hr.bertlab.fluteassistant.domain.listen.Hearing
import hr.bertlab.fluteassistant.domain.metronome.MetronomeConfig
import hr.bertlab.fluteassistant.domain.metronome.TimeSignature
import hr.bertlab.fluteassistant.domain.notes.StaffNotes
import hr.bertlab.fluteassistant.ui.common.BackKey
import hr.bertlab.fluteassistant.ui.common.LocaleProvider
import hr.bertlab.fluteassistant.ui.common.assertWhole
import hr.bertlab.fluteassistant.ui.common.isNear
import hr.bertlab.fluteassistant.ui.common.needsDevice
import hr.bertlab.fluteassistant.ui.common.noteColor
import hr.bertlab.fluteassistant.ui.metronome.MetronomeUiState
import hr.bertlab.fluteassistant.ui.theme.DarkFluteColors
import hr.bertlab.fluteassistant.ui.theme.FluteAssistantTheme
import hr.bertlab.fluteassistant.ui.theme.InkOnFill
import hr.bertlab.fluteassistant.ui.theme.LightFluteColors
import hr.bertlab.fluteassistant.ui.theme.NoteLetterColors
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
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
    private var signs = 0
    private val octaves = mutableListOf<Int>()
    private var erases = 0
    private val stepTaps = mutableListOf<Int>()
    private var stageTaps = 0
    private val restEvents = mutableListOf<String>()
    private var breaths = 0
    private var joins = 0
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
    private val back = BackKey()

    /**
     * Renders [FluteContent] in [locale]. A [height] puts it in a box that tall (a short screen);
     * a [restoration] renders it through that tester, so a test can emulate the Activity's
     * re-creation with `rememberSaveable` state saved and restored. The staff's [timeSignature] is
     * the metronome's, as on the phone.
     */
    private fun show(
        initial: FluteUiState,
        locale: Locale = Locale.ENGLISH,
        height: Dp? = null,
        restoration: StateRestorationTester? = null,
        timeSignature: TimeSignature = TimeSignature.FOUR_FOUR,
        dark: Boolean = false,
    ) {
        state = mutableStateOf(initial)
        metronome = mutableStateOf(MetronomeUiState(config = MetronomeConfig(bpm = 120, timeSignature = timeSignature)))
        val content: @Composable () -> Unit = {
            LocaleProvider(locale) {
                back.Watch()
                val flute: @Composable () -> Unit = {
                    FluteContent(
                        state = state.value,
                        metronome = metronome.value,
                        actions = FluteActions(
                            onKeyPressed = { pressed += it },
                            onKeyReleased = { released += it },
                            onKeyActivated = { activated += it },
                            onRestPressed = { restEvents += "press" },
                            onRestReleased = { restEvents += "release" },
                            onRestActivated = { restEvents += "activate" },
                            onBreath = { breaths++ },
                            onJoin = { joins++ },
                            onSign = { signs++ },
                            onOctave = { octaves += it },
                            onTogglePlayback = { playToggles++ },
                            // As the view model: an erase with a step chosen removes it and lets the choice go.
                            onEraseLast = {
                                erases++
                                state.value = state.value.copy(selected = null)
                            },
                            onStepTapped = { stepTaps += it },
                            onStageTapped = { stageTaps++ },
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
                        ),
                    )
                }
                val sized: @Composable () -> Unit = { if (height == null) flute() else Box(Modifier.height(height)) { flute() } }
                if (dark) FluteAssistantTheme(darkTheme = true) { sized() } else sized()
            }
        }
        if (restoration != null) restoration.setContent(content) else compose.setContent(content)
    }

    /** The metronome takes [timeSignature], as its tab or the flute's speed panel sets it. */
    private fun setTimeSignature(timeSignature: TimeSignature) {
        metronome.value = metronome.value.copy(config = metronome.value.config.copy(timeSignature = timeSignature))
    }

    @Test
    fun shows_the_letter_keys_with_a_hint_before_the_first_tap() {
        show(FluteUiState())
        compose.onNodeWithText("👆 Tap or hold a note").assertIsDisplayed()
        compose.onNodeWithContentDescription("Flute keys").assertExists()
        val c4 = compose.onNodeWithContentDescription("C4").getBoundsInRoot()
        val d4 = compose.onNodeWithContentDescription("D4").getBoundsInRoot()
        assertTrue("D4 $d4 should be right of C4 $c4", d4.left >= c4.right)
        // 58 dp, as on the canvas; the layout rounds it to whole pixels (152 px, 57.9 dp, on a 2.625 screen).
        assertTrue("a key should be 58 dp tall, was ${c4.height}", abs((c4.height - 58.dp).value) <= 0.5f)
    }

    @Test
    fun pressing_a_key_reports_press_and_release_separately() {
        show(FluteUiState())
        compose.onNodeWithContentDescription("G4").performTouchInput { down(center) }
        assertEquals(listOf(4), pressed)
        assertTrue(released.isEmpty())
        compose.onNodeWithContentDescription("G4").performTouchInput { up() }
        assertEquals(listOf(4), released)
    }

    @Test
    fun a_press_the_system_cancels_still_lets_go_of_the_key() {
        // Palm rejection or a system gesture ends a touch with a cancel, not an up; the ViewModel
        // must hear the release all the same, or the next piece of the hold could not go on with it.
        show(FluteUiState())
        compose.onNodeWithContentDescription("G4").performTouchInput { down(center) }
        compose.onNodeWithContentDescription("G4").performTouchInput { cancel() }
        assertEquals(listOf(4), pressed)
        assertEquals(listOf(4), released)
        compose.onNodeWithContentDescription("Rest").performTouchInput { down(center) }
        compose.onNodeWithContentDescription("Rest").performTouchInput { cancel() }
        assertEquals(listOf("press", "release"), restEvents)
    }

    @Test
    fun the_held_note_is_on_the_staff_and_named_in_the_line() {
        show(
            FluteUiState(
                tune = listOf(TuneNote(67, NoteLength.QUARTER)),
                held = Held(67, index = 0),
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
        needsDevice()
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
        show(FluteUiState(tune = listOf(TuneNote(60, NoteLength.QUARTER), TuneNote(67, NoteLength.QUARTER)), playing = Playing(1)))
        compose.onNodeWithText("Stop").assertIsDisplayed()
        compose.onNodeWithText("Play").assertDoesNotExist()
    }

    @Test
    fun the_line_scrolls_to_the_newest_note() {
        val tune = List(29) { TuneNote(if (it % 2 == 0) 60 else 62, NoteLength.QUARTER) } + TuneNote(84, NoteLength.QUARTER)
        show(FluteUiState(tune = tune, held = Held(84, index = 29)))
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
                            actions = FluteActions(),
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

    /** Upright the advice is one line that shrinks to 14 sp and no further: every line, the longest Croatian ones too, shows whole. */
    @Test
    fun every_advice_shows_whole_on_a_narrow_phone_in_croatian_at_large_text() {
        val twoNotes = listOf(TuneNote(60, NoteLength.QUARTER), TuneNote(62, NoteLength.QUARTER))
        val short = Walk(0, 0, Walk.Phase.SHORT)
        val lines = listOf(
            Listening(index = 0, hearing = Hearing.LOUD_UNCLEAR) to "🎵 Odsviraj jedan dug, čist ton",
            Listening(index = 0, hearing = Hearing.QUIET_UNCLEAR) to "📱 Dođi bliže mobitelu",
            Listening(index = 0, repeatHeld = true) to "🔁 Stani, pa odsviraj opet",
            Listening(index = 0, walk = short) to "⏳ Prekratko, drži dulje",
            Listening(index = 0, walk = short, faded = true) to "😮‍💨 Možda nema zraka? Udahni",
            Listening(index = 0, pitch = Pitch.HIGH) to "⬇️ Malo niže",
            Listening(index = 0, wrong = WrongNote(0, 64)) to "👀 Provjeri prste",
            Listening(index = 0, tongued = true) to "✂️ Poveži ih, bez jezika",
        )
        state = mutableStateOf(FluteUiState(tune = twoNotes, listening = lines.first().first))
        compose.setContent {
            LocaleProvider(Locale("hr")) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 1.3f)) {
                    Box(Modifier.width(360.dp)) {
                        FluteContent(state = state.value, metronome = MetronomeUiState(), actions = FluteActions())
                    }
                }
            }
        }
        for ((listening, line) in lines) {
            compose.runOnIdle { state.value = state.value.copy(listening = listening) }
            compose.waitForIdle()
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(line, useUnmergedTree = true).fetchSemanticsNode()
                .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
            val layout = layouts.single()
            assertFalse("\"$line\" runs past its one line", layout.hasVisualOverflow)
            assertEquals("\"$line\" shows whole", line.length, layout.getLineEnd(0, visibleEnd = true))
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
        show(FluteUiState(tune = listOf(TuneNote(60, NoteLength.QUARTER))))
        val before = compose.onNodeWithContentDescription("C4").getBoundsInRoot()
        state.value = state.value.copy(tune = listOf(TuneNote(60, NoteLength.HALF)), held = Held(60, index = 0))
        compose.onNodeWithContentDescription("C4, 2 beats").assertExists()
        assertEquals(before, compose.onNodeWithContentDescription("C4").getBoundsInRoot())
        state.value = FluteUiState()
        assertEquals(before, compose.onNodeWithContentDescription("C4").getBoundsInRoot())
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
        show(FluteUiState(tune = listOf(TuneRest(NoteLength.HALF)), held = Held(midi = null, index = 0)))
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
        show(FluteUiState(), timeSignature = TimeSignature.THREE_FOUR)
        compose.onNodeWithContentDescription("Time signature 3/4").assertIsDisplayed()
        compose.onNodeWithText("👆 Tap or hold a note").assertIsDisplayed()
        state.value = state.value.copy(tune = listOf(TuneNote(60, NoteLength.QUARTER)))
        setTimeSignature(TimeSignature.SIX_EIGHT)
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
        show(FluteUiState(), Locale("hr"), timeSignature = TimeSignature.SIX_EIGHT)
        compose.onNodeWithContentDescription("Mjera 6/8").assertExists()
    }

    @Test
    fun a_new_time_signature_rewrites_the_staff_without_moving_the_keys() {
        show(FluteUiState(tune = listOf(TuneNote(60, NoteLength.HALF)), held = Held(60, index = 0)))
        val before = compose.onNodeWithContentDescription("C4").getBoundsInRoot()
        setTimeSignature(TimeSignature.SIX_EIGHT)
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
        // The clef's tip stands over the top line, where the signature's box starts, by StaffGeometry's numbers (the font's
        // clef since 2026-11-24: 9.2 dp over; the drawn one stood a space over). The knob's shadow, 5 dp under it, ends
        // over the tip with white between (it lay on the tip until 2026-11-10).
        val shadowEnd = knobBounds.bottom + 5.dp
        val tip = signature.top - (StaffGeometry.TOP_LINE_Y - StaffGeometry.CLEF_TIP_Y).dp
        assertTrue("the knob's shadow, ending at $shadowEnd, should end over the clef's tip at $tip", shadowEnd + 3.dp <= tip)
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
        needsDevice()
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
        needsDevice()
        show(FluteUiState(tune = twoNotes, listening = Listening(index = 0)))
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
        needsDevice()
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
        show(FluteUiState(tune = listOf(TuneNote(60, NoteLength.QUARTER)), playing = Playing(0)))
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
        state.value = state.value.copy(playing = Playing(0))
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

    /** The cover of the shelf's book that a screen reader hears as [description], as it is drawn. */
    private fun coverPixels(description: String): PixelMap =
        compose.onNodeWithContentDescription(description).captureToImage().toPixelMap()

    /**
     * An empty book is the one to tap for a new tune, and faded means "cannot be pressed" everywhere else in the
     * app, so its sweet is drawn whole: the same picture as when the book holds notes. The sweet stands over the
     * label, in the cover's upper half, and the label is the same size either way, so it does not move.
     */
    @Test
    fun an_empty_book_shows_its_sweet_whole() {
        needsDevice()
        show(FluteUiState(tune = listOf(TuneNote(67, NoteLength.HALF)), books = List(6) { Book() }))
        compose.onNodeWithContentDescription("Tune: Lollipop, tap to change").performClick()
        val empty = coverPixels("Cookie, empty")
        compose.runOnIdle { state.value = state.value.copy(books = threeNoteCookie) }
        val full = coverPixels("Cookie, 3 notes")
        var differing = 0
        for (y in empty.height * 22 / 100 until empty.height * 54 / 100) {
            for (x in empty.width * 15 / 100 until empty.width * 85 / 100) if (empty[x, y] != full[x, y]) differing++
        }
        assertEquals("an empty book's sweet should be drawn as a full one's, not faded", 0, differing)
    }

    /**
     * The longest unbroken stretch of dark ink along any row of a cover's label: the label stands in the lower half,
     * clear of the spine at the start (12 dp of ink) and of the cover's outline at the other edge.
     */
    private fun longestInkRun(cover: PixelMap): Int {
        var longest = 0
        for (y in cover.height * 56 / 100 until cover.height * 78 / 100) {
            var run = 0
            for (x in cover.width * 12 / 100 until cover.width * 95 / 100) {
                run = if (cover[x, y].luminance() < 0.3f) run + 1 else 0
                longest = maxOf(longest, run)
            }
        }
        return longest
    }

    /** An empty book's label is a dashed, pale outline, as every empty place in the app is; a full one's is solid ink. */
    @Test
    fun an_empty_books_label_is_dashed() {
        needsDevice()
        show(FluteUiState(tune = listOf(TuneNote(67, NoteLength.HALF)), books = threeNoteCookie))
        compose.onNodeWithContentDescription("Tune: Lollipop, tap to change").performClick()
        val full = coverPixels("Cookie, 3 notes")
        val empty = coverPixels("Cupcake, empty")
        assertTrue("a full book's label has a solid outline, ${longestInkRun(full)} of ${full.width} px", longestInkRun(full) > full.width / 3)
        assertTrue("an empty book's label has no solid outline, ${longestInkRun(empty)} of ${empty.width} px", longestInkRun(empty) < empty.width / 10)
    }

    // ---- The phone's Back ----

    @Test
    fun back_leaves_the_app_while_the_child_writes() {
        show(FluteUiState(tune = twoNotes))
        assertFalse("nothing is open, so Back is the phone's, as before", back.taken(compose))
    }

    @Test
    fun back_closes_the_speed_panel_as_ok_does() {
        show(FluteUiState(tune = twoNotes))
        compose.onNodeWithContentDescription("Speed: Rabbit, tap to change").performClick()
        back.press(compose)
        assertEquals(1, speedDones)
        compose.onNodeWithContentDescription("Flute keys").assertExists()
        assertFalse("the panel is closed, so Back is the phone's again", back.taken(compose))
    }

    @Test
    fun back_closes_an_open_wheel_before_the_speed_panel() {
        show(FluteUiState(tune = twoNotes))
        compose.onNodeWithContentDescription("Speed: Rabbit, tap to change").performClick()
        compose.onNodeWithText("120").performClick()
        compose.onNodeWithText("119").assertIsDisplayed()
        back.press(compose)
        compose.onNodeWithText("119").assertDoesNotExist()
        assertEquals(0, speedDones)
        compose.onNodeWithContentDescription("Tempo ring, drag around it to change the speed").assertIsDisplayed()
        back.press(compose)
        assertEquals(1, speedDones)
    }

    @Test
    fun back_closes_the_shelf_on_the_open_book() {
        show(FluteUiState(tune = listOf(TuneNote(67, NoteLength.HALF)), books = threeNoteCookie))
        compose.onNodeWithContentDescription("Tune: Lollipop, tap to change").performClick()
        back.press(compose)
        assertEquals(listOf(0), chosenBooks)
        compose.onNodeWithContentDescription("Flute keys").assertExists()
        assertFalse(back.taken(compose))
    }

    @Test
    fun back_is_not_now_on_the_microphone_card() {
        show(FluteUiState(tune = twoNotes, micDenied = true))
        back.press(compose)
        assertEquals(1, micDismissals)
    }

    @Test
    fun back_stops_a_tune_that_runs() {
        show(FluteUiState(tune = twoNotes, listening = Listening(index = 0)))
        back.press(compose)
        assertEquals(1, stops)
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
        show(FluteUiState(tune = twoNotes, playing = Playing(0)))
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
        state.value = FluteUiState(tune = twoNotes, listening = Listening(index = 0))
        checkRunning()
        compose.onNodeWithText("Stop").performClick()
        assertEquals(2, stops)
        assertEquals(0, playToggles)
    }

    @Test
    fun the_staff_grows_while_the_tune_runs() {
        show(FluteUiState(tune = twoNotes), height = 653.dp)
        val rest = compose.onNodeWithContentDescription("Your tune, 2 notes").getBoundsInRoot()
        // On a tab of 653 dp (a Galaxy A55) the staff's box is the released 257 and the 35 the tab has to spare.
        val box = TuneStaffHeight + 35.dp
        assertTrue("at rest the staff is ${rest.height}, expected $box", abs((rest.height - box).value) <= 1f)
        state.value = state.value.copy(listening = Listening(index = 0))
        val running = compose.onNodeWithContentDescription("Your tune, 2 notes").getBoundsInRoot()
        val expected = tuneStaffHeight(RunningStaffScale)
        assertTrue("running the staff is ${running.height}, expected $expected", abs((running.height - expected).value) <= 1f)
    }

    /**
     * What a Galaxy A55 with three navigation buttons leaves the tab, about 652 dp (2026-11-10): the
     * stage, the keys and the Play row needed 642 of it (610 since 2026-11-18). The height check counted 664, so that phone
     * was laid out as one too short: its spare height lay under the Play row instead of round the
     * keys, and Stop stood 100 dp over Play's place.
     */
    @Test
    fun a_phone_that_only_just_holds_the_tab_is_laid_out_like_a_taller_one() {
        show(FluteUiState(tune = twoNotes), height = 652.dp)
        val keys = compose.onNodeWithContentDescription("Flute keys").getBoundsInRoot()
        // Since 2026-11-18 what the tab has over its least (618) goes to the staff's box first, up to 63 dp: 34 here.
        val expected = FluteKeysMinHeight
        assertTrue("the keys' room should take the spare height: ${keys.height}, expected $expected", abs((keys.height - expected).value) <= 1f)
        val play = compose.onNodeWithText("Play").getBoundsInRoot()
        state.value = state.value.copy(listening = Listening(index = 0))
        val stop = compose.onNodeWithText("Stop").getBoundsInRoot()
        assertTrue("Stop ($stop) should stand where Play ($play) was", abs((stop.bottom - play.bottom).value) <= 1f)
    }

    @Test
    fun marks_and_the_waiting_animal_read_on_the_staff() {
        show(FluteUiState(tune = twoNotes, marks = mapOf(0 to StepMark.FIRST_TRY), listening = Listening(index = 1)))
        compose.onNodeWithContentDescription("C4, 1 beat, got it").assertExists()
        compose.onNodeWithContentDescription("G4, 1 beat, play this one").assertExists()
        state.value = state.value.copy(listening = Listening(index = 1, wrong = WrongNote(index = 1, midi = 69)))
        compose.onNodeWithContentDescription("G4, 1 beat, heard A4").assertExists()
        state.value = state.value.copy(listening = null, marks = mapOf(0 to StepMark.FIRST_TRY, 1 to StepMark.RETRY))
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
        needsDevice()
        show(FluteUiState(tune = twoNotes, marks = mapOf(0 to StepMark.FIRST_TRY), listening = Listening(index = 1)))
        val waited = compose.onNodeWithContentDescription("G4, 1 beat, play this one")
        val a = noteColor(69)
        val silent = marksRow(waited.captureToImage().toPixelMap())
        val card = silent.first()
        assertTrue("in silence the row holds the listening dots, not an A bubble", silent.none { it.near(a) })
        assertTrue("in silence the row shows the listening dots' ring, not nothing", silent.count { !it.near(card) } > silent.size / 20)
        state.value = state.value.copy(listening = Listening(index = 1, heardNow = 69))
        val hearing = marksRow(waited.captureToImage().toPixelMap())
        assertTrue("the A heard now should fill a bubble under the waited step", hearing.count { it.near(a) } > hearing.size / 3)
        compose.onNodeWithContentDescription("G4, 1 beat, play this one").assertExists() // a screen reader is not told of the passing note
        state.value = state.value.copy(listening = null)
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
        show(FluteUiState(tune = tune, marks = mapOf(19 to StepMark.FIRST_TRY), listening = Listening(index = 20)))
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
        show(FluteUiState(tune = twoNotes, listening = Listening(index = 0, hearing = Hearing.LOUD_UNCLEAR)))
        compose.onNodeWithText("🎵 Play one long, clear note").assertIsDisplayed()
        state.value = state.value.copy(listening = Listening(index = 0, hearing = Hearing.QUIET_UNCLEAR))
        compose.onNodeWithText("📱 Come closer to the phone").assertIsDisplayed()
        state.value = state.value.copy(listening = Listening(index = 0))
        compose.onNodeWithText("📱 Come closer to the phone").assertDoesNotExist()
        compose.onNodeWithText("🎵 Play one long, clear note").assertDoesNotExist()
        state.value = FluteUiState(tune = twoNotes, playing = Playing(0))
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
        needsDevice()
        show(FluteUiState(tune = twoNotes, listening = Listening(index = 0)))
        assertWholeShadowUnder(compose.onNodeWithContentDescription("Speed: Rabbit, tap to change"), 5.dp)
        assertWholeShadowUnder(compose.onNodeWithContentDescription("Tune: Lollipop, tap to change"), 5.dp)
    }

    @Test
    fun the_faded_keys_and_buttons_of_an_empty_tune_keep_their_whole_shadow() {
        needsDevice()
        show(FluteUiState())
        assertWholeShadowUnder(compose.onNodeWithContentDescription("Erase the last note"), 5.dp)
        assertWholeShadowUnder(compose.onNodeWithText("Play"), 8.dp)
        assertWholeShadowUnder(compose.onNodeWithText("Listen to me"), 8.dp)
    }

    @Test
    fun the_knob_is_inert_while_the_tune_runs() {
        show(FluteUiState(tune = twoNotes, listening = Listening(index = 0)))
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
        // Settings leaves the app, so it waits until the phone will not ask again.
        compose.onNodeWithText("Settings").assertDoesNotExist()
        compose.onNodeWithText("Not now").assertIsDisplayed()
        compose.onNodeWithContentDescription("Flute keys").assertDoesNotExist()
        compose.onNodeWithText("Play").assertDoesNotExist()
        compose.onNodeWithText("Listen to me").assertDoesNotExist()
        compose.onNodeWithText("Yes").performClick()
        assertEquals(1, micRequests)
        state.value = state.value.copy(micBlocked = true)
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
        show(FluteUiState(tune = twoNotes, micDenied = true, micBlocked = true), Locale("hr"))
        compose.onNodeWithText("Smijem li slušati?").assertIsDisplayed()
        compose.onNodeWithText("Da").assertIsDisplayed()
        compose.onNodeWithText("Postavke").assertIsDisplayed()
        compose.onNodeWithText("Ne sad").assertIsDisplayed()
    }

    // ---- The room under the stage while the flute listens (the user's "3C" of 2026-11-10) ----

    @Test
    fun listening_asks_for_the_first_note_until_one_is_judged() {
        show(FluteUiState(tune = twoNotes, listening = Listening(index = 0)))
        compose.onNodeWithText("🪈 Play C4").assertIsDisplayed()
        compose.onNodeWithContentDescription("Fingering for C4").assertDoesNotExist()
        state.value = FluteUiState(tune = twoNotes, listening = Listening(index = 1), marks = mapOf(0 to StepMark.FIRST_TRY))
        compose.onNodeWithText("🪈 Play C4").assertDoesNotExist()
        compose.onNodeWithText("🪈 Play G4").assertDoesNotExist()
    }

    /**
     * After a wrong note the room shows how the wanted note is fingered, with "Check your fingers", between the
     * stage and Stop and as wide as the stage, until the right note is played.
     */
    @Test
    fun a_wrong_note_brings_the_wanted_notes_fingering() {
        show(FluteUiState(tune = twoNotes, listening = Listening(index = 0, wrong = WrongNote(0, 64))))
        val fingering = compose.onNodeWithContentDescription("Fingering for C4").assertIsDisplayed().getBoundsInRoot()
        compose.onNodeWithText("👀 Check your fingers").assertIsDisplayed()
        compose.onNodeWithText("🪈 Play C4").assertDoesNotExist()
        val staff = compose.onNodeWithContentDescription("Your tune, 2 notes").getBoundsInRoot()
        val stop = compose.onNodeWithText("Stop").getBoundsInRoot()
        val line = compose.onNodeWithText("👀 Check your fingers").getBoundsInRoot()
        assertTrue("the fingering $fingering stands under the staff $staff", fingering.top >= staff.bottom)
        assertTrue("the line $line stands under the fingering $fingering and over Stop $stop", line.top >= fingering.bottom && line.bottom <= stop.top)
        assertTrue("the fingering is ${fingering.width} wide", fingering.width > 300.dp)
        state.value = FluteUiState(tune = twoNotes, listening = Listening(index = 1), marks = mapOf(0 to StepMark.RETRY))
        compose.onNodeWithContentDescription("Fingering for C4").assertDoesNotExist()
        compose.onNodeWithText("👀 Check your fingers").assertDoesNotExist()
    }

    @Test
    fun playing_the_tune_back_asks_for_nothing() {
        show(FluteUiState(tune = twoNotes, playing = Playing(0)))
        compose.onNodeWithText("🪈 Play C4").assertDoesNotExist()
        compose.onNodeWithContentDescription("Fingering for C4").assertDoesNotExist()
    }

    /** A note cut short: the line asks to hold it longer, and no fingering shows, the fingers were right (2026-11-11). */
    @Test
    fun a_note_cut_short_asks_to_hold_it_longer_with_no_fingering() {
        show(FluteUiState(tune = twoNotes, listening = Listening(index = 0, walk = Walk(0, 0, Walk.Phase.SHORT))))
        compose.onNodeWithText("⏳ Too short, hold longer").assertIsDisplayed()
        compose.onNodeWithContentDescription("Fingering for C4").assertDoesNotExist()
        compose.onNodeWithText("🪈 Play C4").assertDoesNotExist()
        state.value = FluteUiState(tune = twoNotes, listening = Listening(index = 0, walk = Walk(0, 0, Walk.Phase.SOUNDING)))
        compose.onNodeWithText("⏳ Too short, hold longer").assertDoesNotExist()
    }

    @Test
    fun the_listening_room_speaks_croatian() {
        show(FluteUiState(tune = twoNotes, listening = Listening(index = 0)), Locale("hr"))
        compose.onNodeWithText("🪈 Odsviraj c1").assertIsDisplayed()
        state.value = FluteUiState(tune = twoNotes, listening = Listening(index = 0, wrong = WrongNote(0, 64)))
        compose.onNodeWithContentDescription("Hvat za c1").assertIsDisplayed()
        compose.onNodeWithText("👀 Provjeri prste").assertIsDisplayed()
        state.value = FluteUiState(tune = twoNotes, listening = Listening(index = 0, walk = Walk(0, 0, Walk.Phase.SHORT)))
        compose.onNodeWithText("⏳ Prekratko, drži dulje").assertIsDisplayed()
    }

    /** The guess says maybe (2026-11-13): out of air for a note that faded. A note blown high has the tuner's words, and no fingering: the fingers were right (2026-11-14). */
    @Test
    fun the_why_lines_say_maybe_in_both_languages() {
        val faded = Listening(index = 0, walk = Walk(0, 0, Walk.Phase.SHORT), faded = true)
        val high = Listening(index = 0, pitch = Pitch.HIGH)
        show(FluteUiState(tune = twoNotes, listening = faded))
        compose.onNodeWithText("😮‍💨 Maybe out of air? Big breath first").assertIsDisplayed()
        state.value = FluteUiState(tune = twoNotes, listening = high)
        compose.onNodeWithText("⬇️ A little lower").assertIsDisplayed()
        compose.onNodeWithContentDescription("Fingering for C4").assertDoesNotExist() // the fingers were right
    }

    @Test
    fun the_why_lines_speak_croatian() {
        show(FluteUiState(tune = twoNotes, listening = Listening(index = 0, walk = Walk(0, 0, Walk.Phase.SHORT), faded = true)), Locale("hr"))
        compose.onNodeWithText("😮‍💨 Možda nema zraka? Udahni").assertIsDisplayed()
        state.value = FluteUiState(tune = twoNotes, listening = Listening(index = 0, pitch = Pitch.HIGH))
        compose.onNodeWithText("⬇️ Malo niže").assertIsDisplayed()
    }

    // ---- Feedback, not stars (2026-11-14) ----

    @Test
    fun a_note_blown_high_or_low_has_the_tuners_words_and_no_fingering() {
        val sounding = Walk(0, 0, Walk.Phase.SOUNDING)
        show(FluteUiState(tune = twoNotes, listening = Listening(index = 0, heardNow = 60, pitch = Pitch.HIGH, walk = sounding)))
        compose.onNodeWithText("⬇️ A little lower").assertIsDisplayed()
        compose.onNodeWithContentDescription("Fingering", substring = true).assertDoesNotExist()
        state.value = state.value.copy(listening = Listening(index = 0, heardNow = 60, pitch = Pitch.LOW, walk = sounding))
        compose.onNodeWithText("⬆️ A little higher").assertIsDisplayed()
    }

    @Test
    fun the_marks_say_what_each_note_was() {
        val three = listOf(TuneNote(60, NoteLength.QUARTER), TuneNote(67, NoteLength.QUARTER), TuneNote(69, NoteLength.QUARTER))
        show(FluteUiState(tune = three, marks = mapOf(0 to StepMark.HIGH, 1 to StepMark.LOW, 2 to StepMark.SHORT)))
        compose.onNodeWithContentDescription("C4, 1 beat, too high").assertExists()
        compose.onNodeWithContentDescription("G4, 1 beat, too low").assertExists()
        compose.onNodeWithContentDescription("A4, 1 beat, let go too early").assertExists()
    }

    @Test
    fun an_ended_run_says_how_its_speed_was_and_waits_for_the_pill() {
        val slower = Listening(index = 1, finishing = true, pace = Pace(bpm = 64, pairs = 7, knobBpm = 100, speaks = true))
        show(FluteUiState(tune = twoNotes, listening = slower, marks = mapOf(0 to StepMark.FIRST_TRY, 1 to StepMark.FIRST_TRY)))
        compose.onNodeWithText("🐢 Slower than the 🐇").assertIsDisplayed()
        compose.onNodeWithText("Stop").assertIsDisplayed()
        compose.onNodeWithContentDescription("play this one", substring = true).assertDoesNotExist()
        state.value = state.value.copy(listening = slower.copy(pace = Pace(bpm = 104, pairs = 7, knobBpm = 60, speaks = true)))
        compose.onNodeWithText("🐇 Faster than the 🐢").assertIsDisplayed()
    }

    // ---- A note held too long (2026-11-16): the hand under it ----

    @Test
    fun a_note_held_too_long_shows_the_hand() {
        show(FluteUiState(tune = twoNotes, marks = mapOf(0 to StepMark.LONG)))
        compose.onNodeWithContentDescription("C4, 1 beat, held too long").assertExists()
        compose.runOnIdle { state.value = FluteUiState(tune = twoNotes, listening = Listening(index = 1, remark = StepMark.LONG), marks = mapOf(0 to StepMark.LONG)) }
        compose.onNodeWithText("✋ Too long, let go sooner").assertIsDisplayed()
    }

    @Test
    fun a_note_held_too_long_says_so_in_croatian() {
        show(FluteUiState(tune = twoNotes, listening = Listening(index = 1, remark = StepMark.LONG), marks = mapOf(0 to StepMark.LONG)), Locale("hr"))
        compose.onNodeWithText("✋ Predugo, pusti ranije").assertIsDisplayed()
        compose.onNodeWithContentDescription("c1, 1 doba, predugo").assertExists()
    }

    // ---- Legato (2026-11-16): the slur, a joined note's words, ✂️ ----

    private val slurred = listOf(TuneNote(60, NoteLength.QUARTER), TuneNote(62, NoteLength.QUARTER, joined = true), TuneNote(64, NoteLength.QUARTER))

    @Test
    fun a_joined_note_is_described_as_joined() {
        show(FluteUiState(tune = slurred))
        compose.onNodeWithContentDescription("D4, 1 beat, joined to the note before").assertExists()
        compose.onNodeWithContentDescription("E4, 1 beat").assertExists()
    }

    @Test
    fun a_tongued_mark_is_described() {
        show(FluteUiState(tune = slurred, marks = mapOf(0 to StepMark.FIRST_TRY, 1 to StepMark.TONGUED)))
        compose.onNodeWithContentDescription("D4, 1 beat, joined to the note before, tongued, not joined").assertExists()
    }

    @Test
    fun a_joined_note_is_described_in_croatian() {
        show(FluteUiState(tune = slurred, marks = mapOf(1 to StepMark.TONGUED)), locale = Locale("hr"))
        compose.onNodeWithContentDescription(", spojeno s notom prije, jezikom, nije spojeno", substring = true).assertExists()
    }

    /** Ink pixels of the staff in the box [x0]..[x1] by [y0]..[y1], in dp from the tune row's left and the staff's top. */
    private fun inkIn(row: PixelMap, x0: Float, x1: Float, y0: Float, y1: Float): Int {
        val d = compose.density
        val ink = LightFluteColors.text
        var count = 0
        for (x in with(d) { x0.dp.roundToPx() } until with(d) { x1.dp.roundToPx() }) {
            for (y in with(d) { y0.dp.roundToPx() } until with(d) { y1.dp.roundToPx() }) if (row[x, y].near(ink)) count++
        }
        return count
    }

    @Test
    fun slurs_and_the_waiting_arc_are_drawn() {
        needsDevice()
        show(FluteUiState(tune = slurred))
        val row = compose.onNodeWithContentDescription("Your tune, 3 notes").captureToImage().toPixelMap()
        // The first head stands NOTES_START - NOTES_CLIP + HEAD_X into the row, a beat (29 dp) apart; c¹ at 165, d¹ at 156.
        val first = StaffGeometry.NOTES_START - StaffGeometry.NOTES_CLIP + StaffGeometry.HEAD_X
        val beat = StaffGeometry.BEAT_WIDTH
        // Since 2026-11-18 the staff's box has what the screen spares over it, up to 63 dp, and the staff stands that much lower.
        val room = roomOf(row)
        assertTrue("a slur under c and d", inkIn(row, first + 6, first + beat - 6, 171f + room, 186f + room) > 0)
        // From 166: d¹'s head, the font's at 0.9 since 2026-11-24, reaches 164.7 with its edge.
        assertEquals("nothing under d and e, not joined", 0, inkIn(row, first + beat + 6, first + 2 * beat - 6, 166f + room, 180f + room))
        state.value = state.value.copy(joinWaiting = true)
        val waiting = compose.onNodeWithContentDescription("Your tune, 3 notes").captureToImage().toPixelMap()
        // From 10.5 right of e¹'s centre: its head's edge, the font's since 2026-11-24, reaches 151.9 at 6 (the final review).
        assertTrue("a dashed arc runs on from e", inkIn(waiting, first + 2 * beat + 10.5f, first + 2 * beat + 26, 152f + room, 168f + room) > 0)
    }

    @Test
    fun a_slur_stays_under_its_notes_when_the_staff_has_scrolled() {
        needsDevice()
        // Twelve quarters, then e joined to f: the pair starts 383 dp into the row, past a phone's width, so the staff
        // scrolls to show it. The slur's layer is moved by the row's scroll and not drawn again: it must move with it.
        val tune = List(12) { TuneNote(60, NoteLength.QUARTER) } +
            TuneNote(64, NoteLength.QUARTER) + TuneNote(65, NoteLength.QUARTER, joined = true)
        show(FluteUiState(tune = tune))
        compose.waitForIdle()
        val rowNode = compose.onNodeWithContentDescription("Your tune, 14 notes")
        val rowLeft = rowNode.getBoundsInRoot().left
        val e = (compose.onNodeWithContentDescription("E4, 1 beat").getBoundsInRoot().left - rowLeft).value
        val f = (compose.onNodeWithContentDescription("F4, 1 beat, joined to the note before").getBoundsInRoot().left - rowLeft).value
        val unscrolled = StaffGeometry.NOTES_START - StaffGeometry.NOTES_CLIP + 12 * StaffGeometry.BEAT_WIDTH + 3 * StaffGeometry.BAR_SLOT
        assertTrue("the staff must have scrolled for this to test anything: e stands $e dp into the row", e < unscrolled - 1f)
        // e¹ at 147 and f¹ at 138: the slur hangs under both heads, below the staff's bottom line and over the names;
        // all of it lower by the room the box has over the staff (the final review of 2026-11-18: with the room left out
        // the window held a staff line, and the test could not fail).
        val row = rowNode.captureToImage().toPixelMap()
        val room = roomOf(row)
        val ink = inkIn(row, e + StaffGeometry.HEAD_X + 8, f + StaffGeometry.HEAD_X - 8, 155f + room, 172f + room)
        assertTrue("the slur under e and f, where they stand now", ink > 0)
    }

    // ---- The ⌒ key (2026-11-16): left of 💤, faded with nothing to join, down while a join waits ----

    @Test
    fun the_join_key_is_faded_with_nothing_to_join_and_selected_while_a_join_waits() {
        show(FluteUiState())
        compose.onNodeWithContentDescription("Join to the next note").assertIsNotEnabled()
        state.value = state.value.copy(tune = listOf(TuneNote(60, NoteLength.QUARTER)))
        // No "not selected" for a screen reader while no join waits: the key says selected only while one does.
        compose.onNodeWithContentDescription("Join to the next note").assertIsEnabled()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
        state.value = state.value.copy(joinWaiting = true)
        compose.onNodeWithContentDescription("Join to the next note").assertIsSelected()
        state.value = state.value.copy(tune = listOf(TuneNote(60, NoteLength.QUARTER), TuneRest(NoteLength.QUARTER)), joinWaiting = false)
        compose.onNodeWithContentDescription("Join to the next note").assertIsNotEnabled()
    }

    @Test
    fun tapping_the_join_key_reports_it() {
        show(FluteUiState(tune = listOf(TuneNote(60, NoteLength.QUARTER))))
        compose.onNodeWithContentDescription("Join to the next note").performTouchInput { down(center) }
        assertEquals("on the finger going down", 1, joins)
        compose.onNodeWithContentDescription("Join to the next note").performTouchInput { up() }
        compose.onNodeWithContentDescription("Join to the next note").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(2, joins)
    }

    @Test
    fun the_join_key_is_named_for_a_screen_reader_in_croatian() {
        show(FluteUiState(tune = listOf(TuneNote(60, NoteLength.QUARTER))), Locale("hr"))
        compose.onNodeWithContentDescription("Spoji sa sljedećom notom").assertExists()
    }

    // ---- The ♯♭ key (2026-11-17; first in the tools' row since 2026-11-18): the next note its letter's sharp or flat ----

    private val signKey = "Sharp or flat for the next note"

    @Test
    fun while_the_sign_waits_the_keys_show_their_signed_notes_and_d_a_fade() {
        show(FluteUiState())
        compose.onNodeWithContentDescription(signKey).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
        compose.onNodeWithContentDescription("E♭4").assertDoesNotExist()
        state.value = state.value.copy(signWaiting = true)
        compose.onNodeWithContentDescription(signKey).assertIsSelected()
        // C6 takes its sharp since the range runs to C7 (2026-11-18).
        for (name in listOf("C♯4", "E♭4", "F♯4", "G♯4", "B♭4")) {
            compose.onNodeWithContentDescription(name).assertIsEnabled()
        }
        for (name in listOf("D4", "A4")) compose.onNodeWithContentDescription(name).assertIsNotEnabled()
        compose.onNodeWithContentDescription("C4").assertDoesNotExist()
        state.value = state.value.copy(signWaiting = false)
        compose.onNodeWithContentDescription("C4").assertIsEnabled()
        compose.onNodeWithContentDescription("D4").assertIsEnabled()
    }

    @Test
    fun a_signed_key_held_reports_one_press_and_one_release() {
        // The key's face goes from E♭ back to E while the finger is down (the press spent the sign): the hold must go on.
        show(FluteUiState(signWaiting = true))
        compose.onNodeWithContentDescription("E♭4").performTouchInput { down(center) }
        assertEquals(listOf(2), pressed)
        compose.runOnIdle { state.value = state.value.copy(signWaiting = false) }
        compose.waitForIdle()
        assertTrue("let go of while still held: $released", released.isEmpty())
        compose.onNodeWithContentDescription("E4").performTouchInput { up() }
        assertEquals(listOf(2), pressed)
        assertEquals(listOf(2), released)
    }

    @Test
    fun a_key_held_while_the_sign_is_tapped_is_not_let_go() {
        // D fades the moment the sign waits; the note under the finger must go on until the finger lifts.
        show(FluteUiState())
        compose.onNodeWithContentDescription("D4").performTouchInput { down(center) }
        compose.runOnIdle { state.value = state.value.copy(signWaiting = true) }
        compose.waitForIdle()
        assertTrue("let go of while still held: $released", released.isEmpty())
        compose.onNodeWithContentDescription("D4").performTouchInput { up() }
        assertEquals(listOf(1), pressed)
        assertEquals(listOf(1), released)
    }

    @Test
    fun while_the_sign_waits_croatian_names_are_whole_at_large_text() = assertSignedNamesWhole(Locale("hr"), 1.3f)

    /** The final review of 2026-11-17: a signed name shrinks to fit at the largest text, as the black keys' did. */
    @Test
    fun while_the_sign_waits_the_names_are_whole_at_the_largest_text() {
        assertSignedNamesWhole(Locale("hr"), 2f)
    }

    @Test
    fun while_the_sign_waits_english_names_are_whole_at_the_largest_text() {
        assertSignedNamesWhole(Locale.ENGLISH, 2f)
    }

    /** The black notes' names are longer than the white ones' ("gis2" against "g2"): a 360 dp phone at [fontScale]. */
    private fun assertSignedNamesWhole(locale: Locale, fontScale: Float) {
        state = mutableStateOf(FluteUiState(signWaiting = true))
        compose.setContent {
            LocaleProvider(locale) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = fontScale)) {
                    Box(Modifier.width(360.dp)) {
                        FluteContent(state = state.value, metronome = MetronomeUiState(), actions = FluteActions())
                    }
                }
            }
        }
        val names = if (locale == Locale.ENGLISH) {
            listOf("C♯4", "E♭4", "F♯4", "G♯4", "B♭4")
        } else {
            listOf("cis1", "es1", "fis1", "gis1", "b1")
        }
        for (name in names) {
            assertWhole("360 dp, $locale, text $fontScale", compose.onNodeWithText(name, useUnmergedTree = true), textScale = fontScale)
        }
    }

    @Test
    fun a_held_signed_note_lights_its_white_key() {
        // The final review of 2026-11-17: E's key wrote an E♭ and is held; it is E's key that is down, not none.
        show(FluteUiState(tune = listOf(TuneNote(63, NoteLength.EIGHTH)), held = Held(63, index = 0, key = 2)))
        val e = compose.onNodeWithText("E4", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val d = compose.onNodeWithText("D4", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("E's key should be down for its E♭: its name sits lower than D's ($e, $d)", (e.top - d.top).value > 2f)
    }

    @Test
    fun a_key_held_while_the_sign_waits_is_not_faded() {
        needsDevice()
        // D is held when ♯♭ is tapped: it still sounds, so it is drawn whole; A, not held, fades.
        show(FluteUiState(tune = listOf(TuneNote(62, NoteLength.EIGHTH)), held = Held(62, index = 0), signWaiting = true))
        fun fill(name: String): Color = compose.onNodeWithContentDescription(name).captureToImage().toPixelMap()
            .let { it[(it.width * 0.14f).toInt(), it.height / 2] }
        assertTrue("held D should be whole, is ${fill("D4")}", fill("D4").near(noteColor(62)))
        assertFalse("A, not held, should be faded, is ${fill("A4")}", fill("A4").near(noteColor(69)))
    }

    @Test
    fun tapping_the_sign_key_reports_it() {
        show(FluteUiState())
        compose.onNodeWithContentDescription(signKey).performTouchInput { down(center) }
        assertEquals("on the finger going down", 1, signs)
        compose.onNodeWithContentDescription(signKey).performTouchInput { up() }
        compose.onNodeWithContentDescription(signKey).performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(2, signs)
    }

    @Test
    fun the_sign_key_is_named_for_a_screen_reader_in_croatian() {
        show(FluteUiState(signWaiting = true), Locale("hr"))
        compose.onNodeWithContentDescription("Povisilica ili snizilica za sljedeću notu").assertIsSelected()
        compose.onNodeWithContentDescription("b1").assertIsEnabled() // h1's key writes b1 (B♭4)
        compose.onNodeWithContentDescription("es1").assertIsEnabled()
    }

    // ---- Sharps and flats on the staff (2026-11-16) ----

    private val signed = listOf(TuneNote(66, NoteLength.QUARTER), TuneNote(65, NoteLength.QUARTER), TuneNote(67, NoteLength.QUARTER))

    @Test
    fun a_black_note_is_named_with_its_sign() {
        show(FluteUiState(tune = signed))
        compose.onNodeWithContentDescription("F♯4, 1 beat").assertExists()
        compose.onNodeWithContentDescription("F4, 1 beat").assertExists() // a natural adds nothing to the name
    }

    @Test
    fun a_black_note_is_named_with_its_sign_in_croatian() {
        show(FluteUiState(tune = signed), Locale("hr"))
        compose.onNodeWithContentDescription("fis1, 1 doba").assertExists()
    }

    @Test
    fun a_signed_step_is_ten_dp_wider() {
        show(FluteUiState(tune = signed))
        val sharp = compose.onNodeWithContentDescription("F♯4, 1 beat").getBoundsInRoot()
        val natural = compose.onNodeWithContentDescription("F4, 1 beat").getBoundsInRoot()
        val plain = compose.onNodeWithContentDescription("G4, 1 beat").getBoundsInRoot()
        assertEquals(29f, plain.width.value, 0.5f)
        assertEquals(39f, sharp.width.value, 0.5f)
        assertEquals(39f, natural.width.value, 0.5f)
    }

    /**
     * The room the box of a captured row or step has over the staff, in dp (since 2026-11-18): the staff stands that
     * much lower in it than `StaffGeometry`'s numbers say.
     */
    private fun roomOf(captured: PixelMap): Float = captured.height / compose.density.density - StaffGeometry.TOTAL_HEIGHT

    /** Ink pixels of [step] in its first [width] dp, within 10 dp of [y] on the staff, the staff's lines left out. */
    private fun signInk(step: PixelMap, width: Float, y: Float): Int {
        val d = compose.density
        val ink = LightFluteColors.text
        val room = roomOf(step)
        val lines = (0..4).map { StaffGeometry.TOP_LINE_Y + room + it * StaffGeometry.LINE_GAP }
        var count = 0
        for (x in with(d) { 2.dp.roundToPx() } until with(d) { width.dp.roundToPx() }) {
            for (py in with(d) { (y + room - 10f).dp.roundToPx() } until with(d) { (y + room + 10f).dp.roundToPx() }) {
                val inDp = py / d.density
                if (lines.any { kotlin.math.abs(it - inDp) < 1.6f }) continue
                if (step[x, py].near(ink)) count++
            }
        }
        return count
    }

    @Test
    fun the_signs_are_drawn_before_their_heads() {
        needsDevice()
        show(FluteUiState(tune = signed))
        val f = StaffGeometry.yOf(StaffNotes.positionOf(65))
        assertTrue("the sharp", signInk(compose.onNodeWithContentDescription("F♯4, 1 beat").captureToImage().toPixelMap(), 13f, f) > 0)
        assertTrue("the natural", signInk(compose.onNodeWithContentDescription("F4, 1 beat").captureToImage().toPixelMap(), 13f, f) > 0)
        // That a plain step draws no sign is held by its width (`a_signed_step_is_ten_dp_wider`): its head starts about
        // 5 dp in, so a pixel window before it would be too narrow to say anything.
    }

    // ---- The staff's symbols from the music font (spec 2026-11-24-flute-staff-music-symbols-design.md) ----

    private fun tuneRow() = compose.onNodeWithContentDescription("Your tune", substring = true).captureToImage().toPixelMap()

    /** The row's pixel at [x], [y] in dp from its left and the staff's top. */
    private fun PixelMap.at(x: Float, y: Float) = this[with(compose.density) { x.dp.roundToPx() }, with(compose.density) { y.dp.roundToPx() }]

    /**
     * The symbols drawn in ink (the sharp, an eighth's stem and flag, a rest) are the night's ink at night, with none of the
     * day's, each read in its own box with the staff's lines left out (they are night ink themselves, and would pass the
     * test with no symbol drawn; the final review). Not the whole staff: where a head's dark edge meets the dark card the
     * smoothed pixels come within a hair of the day ink, and the name pills write in the day's ink on their fills by design.
     */
    @Test
    fun the_staffs_symbols_are_in_the_night_ink() {
        needsDevice()
        show(FluteUiState(tune = listOf(TuneNote(66, NoteLength.EIGHTH), TuneRest(NoteLength.QUARTER), TuneNote(65, NoteLength.HALF))), dark = true)
        val row = tuneRow()
        val room = roomOf(row)
        val d = compose.density
        // F♯4's step has a sign's room before its head; the rest's step follows an eighth's room on.
        val head = StaffGeometry.NOTES_START - StaffGeometry.NOTES_CLIP + StaffGeometry.ACCIDENTAL_ROOM + StaffGeometry.HEAD_X
        val y = StaffGeometry.yOf(StaffNotes.positionOf(65)) + room
        val rest = head + StaffGeometry.BEAT_WIDTH
        val boxes = mapOf(
            "the sharp" to listOf(head - StaffGeometry.SIGN_X - 6f, head - StaffGeometry.SIGN_X + 6f, y - 16f, y + 16f),
            "the stem and the flag" to listOf(head + 7f, head + 19f, y - StaffGeometry.STEM_REACH, y - 22f),
            "the rest" to listOf(rest - 8f, rest + 8f, StaffGeometry.MIDDLE_LINE_Y + room - 18f, StaffGeometry.MIDDLE_LINE_Y + room + 18f),
        )
        val lines = (0..4).map { StaffGeometry.TOP_LINE_Y + room + it * StaffGeometry.LINE_GAP }
        boxes.forEach { (name, box) ->
            val (x0, x1, y0, y1) = box
            val rows = (with(d) { y0.dp.roundToPx() } until with(d) { y1.dp.roundToPx() }).filter { py ->
                lines.none { kotlin.math.abs(it - py / d.density) < 1.6f }
            }
            val pixels = (with(d) { x0.dp.roundToPx() } until with(d) { x1.dp.roundToPx() }).flatMap { x -> rows.map { py -> row[x, py] } }
            assertTrue("$name has the night ink", pixels.any { it.isNear(DarkFluteColors.text) })
            assertTrue("$name has none of the day ink", pixels.none { it.isNear(LightFluteColors.text) })
        }
    }

    /** A2 (the user's pick, 2026-11-24): the letter's colour in the heads, a half note's in its hollow; the stem in ink. */
    @Test
    fun a_filled_head_is_its_letters_colour_an_open_heads_hollow_too_and_the_stem_ink() {
        needsDevice()
        show(FluteUiState(tune = listOf(TuneNote(67, NoteLength.QUARTER), TuneNote(67, NoteLength.HALF))))
        val row = tuneRow()
        val first = StaffGeometry.NOTES_START - StaffGeometry.NOTES_CLIP + StaffGeometry.HEAD_X
        val y = StaffGeometry.yOf(StaffNotes.positionOf(67)) + roomOf(row)
        val g = NoteLetterColors[4]
        assertTrue("the quarter's head is G's colour", row.at(first, y).near(g))
        assertTrue("the half note's hollow is G's colour", row.at(first + StaffGeometry.BEAT_WIDTH, y).near(g))
        assertTrue("the quarter's stem is ink", row.at(first + StaffGeometry.STEM_X, y - 25f).near(LightFluteColors.text))
    }

    /** E's yellow is the lit disc's: a chosen (or lit) E keeps its ink edge, or it would vanish into the disc. */
    @Test
    fun a_lit_e_keeps_its_ink_edge() {
        needsDevice()
        show(FluteUiState(tune = listOf(TuneNote(64, NoteLength.QUARTER)), selected = 0))
        val row = tuneRow()
        val first = StaffGeometry.NOTES_START - StaffGeometry.NOTES_CLIP + StaffGeometry.HEAD_X
        val y = StaffGeometry.yOf(StaffNotes.positionOf(64)) + roomOf(row)
        val d = compose.density
        val edge = (with(d) { (first + StaffGeometry.HEAD_RX - 1.5f).dp.roundToPx() }..with(d) { (first + StaffGeometry.HEAD_RX + 1f).dp.roundToPx() })
            .count { row[it, with(d) { y.dp.roundToPx() }].near(LightFluteColors.outline) }
        assertTrue("the edge right of E's head", edge > 0)
    }

    /** Pixels near [color] along the row [y] from [x0] to [x1], in dp. */
    private fun PixelMap.countNear(color: Color, x0: Float, x1: Float, y: Float, tolerance: Float = 0.08f): Int {
        val d = compose.density
        val py = with(d) { y.dp.roundToPx() }
        return (with(d) { x0.dp.roundToPx() }..with(d) { x1.dp.roundToPx() }).count { this[it, py].isNear(color, tolerance) }
    }

    /**
     * At night a chosen (or lit) half E is an open head on the yellow disc: its ring keeps the dark ink a bright fill
     * takes (`InkOnFill`), not the night's light ink, which would vanish on the yellow (the final review, 2026-11-24).
     */
    @Test
    fun at_night_a_chosen_half_e_keeps_dark_ink_on_its_disc() {
        needsDevice()
        show(FluteUiState(tune = listOf(TuneNote(64, NoteLength.HALF)), selected = 0), dark = true)
        val row = tuneRow()
        val first = StaffGeometry.NOTES_START - StaffGeometry.NOTES_CLIP + StaffGeometry.HEAD_X
        val y = StaffGeometry.yOf(StaffNotes.positionOf(64)) + roomOf(row) - 3f // over the staff line through E
        assertTrue("dark ink round the head", row.countNear(InkOnFill, first + StaffGeometry.HEAD_RX - 4f, first + StaffGeometry.HEAD_RX + 1f, y) > 0)
    }

    /** The same for a chosen rest at night: its symbol in the fill's dark ink on the disc. */
    @Test
    fun at_night_a_chosen_rest_keeps_dark_ink_on_its_disc() {
        needsDevice()
        show(FluteUiState(tune = listOf(TuneRest(NoteLength.QUARTER)), selected = 0), dark = true)
        val row = tuneRow()
        val first = StaffGeometry.NOTES_START - StaffGeometry.NOTES_CLIP + StaffGeometry.HEAD_X
        val room = roomOf(row)
        val dark = (-12..12 step 2).sumOf { dy ->
            row.countNear(InkOnFill, first - 6f, first + 6f, StaffGeometry.MIDDLE_LINE_Y + room + dy)
        }
        assertTrue("the rest in dark ink on its disc", dark > 0)
    }

    /**
     * The half note's hollow is all the letter's colour: no hairline of the card between the colour and the ring (the
     * oval is measured on the font's outline; the final review asked for a pixel check, as the Fingers tab's whole note has).
     */
    @Test
    fun the_half_notes_hollow_is_all_colour() {
        needsDevice()
        show(FluteUiState(tune = listOf(TuneNote(64, NoteLength.HALF))))
        val row = tuneRow()
        val d = compose.density
        val cx = with(d) { (StaffGeometry.NOTES_START - StaffGeometry.NOTES_CLIP + StaffGeometry.HEAD_X).dp.toPx() }
        val cy = with(d) { (StaffGeometry.yOf(StaffNotes.positionOf(64)) + roomOf(row)).dp.toPx() }
        val (rx, ry) = with(d) { (StaffGeometry.HEAD_RX * 0.8f).dp.toPx() } to with(d) { (StaffGeometry.HEAD_RY * 0.75f).dp.toPx() }
        val yellow = NoteLetterColors[2]
        var pale = 0
        for (x in (cx - rx).toInt()..(cx + rx).toInt()) for (y in (cy - ry).toInt()..(cy + ry).toInt()) {
            val (dx, dy) = (x - cx) / rx to (y - cy) / ry
            val pixel = row[x, y]
            if (dx * dx + dy * dy <= 1f && pixel.red > 0.6f && pixel.blue > yellow.blue + 0.08f) pale++
        }
        assertEquals("pixels of the yellow paled by the card inside the head", 0, pale)
    }

    // ---- The staff's room for a tune above c³ (2026-11-18) ----

    private val upToC7 = listOf(TuneNote(72, NoteLength.QUARTER), TuneNote(96, NoteLength.QUARTER))

    @Test
    fun a_tune_up_to_c4_leaves_the_keys_where_they_stand() {
        show(FluteUiState(tune = twoNotes))
        val staff = compose.onNodeWithContentDescription("Your tune, 2 notes").getBoundsInRoot()
        val keys = compose.onNodeWithContentDescription("Flute keys").getBoundsInRoot()
        val play = compose.onNodeWithText("Play").getBoundsInRoot()
        state.value = FluteUiState(tune = upToC7)
        val high = compose.onNodeWithContentDescription("Your tune, 2 notes").getBoundsInRoot()
        assertTrue("the staff's box keeps its height: $staff, $high", abs((high.height - staff.height).value) <= 0.5f && abs((high.top - staff.top).value) <= 0.5f)
        assertEquals(keys, compose.onNodeWithContentDescription("Flute keys").getBoundsInRoot())
        assertEquals(play, compose.onNodeWithText("Play").getBoundsInRoot())
        compose.onNode(hasContentDescription("C7, 1 beat") and hasAnyAncestor(hasContentDescription("Your tune, 2 notes"))).assertIsDisplayed()
    }

    @Test
    fun on_a_653_dp_tab_the_box_is_292_and_a_tune_up_to_c4_is_fitted_into_it() {
        assertTheBoxStands(tab = 653.dp, box = 292.dp)
    }

    @Test
    fun on_a_tab_too_short_the_box_is_the_released_257_and_the_tab_scrolls() {
        assertTheBoxStands(tab = 600.dp, box = TuneStaffHeight)
    }

    /** On a [tab] that tall the staff's box is [box], whatever the tune, and the keys and Play never move under it. */
    private fun assertTheBoxStands(tab: Dp, box: Dp) {
        show(FluteUiState(tune = twoNotes), height = tab)
        val staff = compose.onNodeWithContentDescription("Your tune, 2 notes").getUnclippedBoundsInRoot()
        val keys = compose.onNodeWithContentDescription("Flute keys").getUnclippedBoundsInRoot()
        val play = compose.onNodeWithText("Play").getUnclippedBoundsInRoot()
        assertTrue("the staff's box is ${staff.height}, expected $box", abs((staff.height - box).value) <= 1f)
        state.value = FluteUiState(tune = upToC7)
        val high = compose.onNodeWithContentDescription("Your tune, 2 notes").getUnclippedBoundsInRoot()
        assertTrue("with a tune up to c4 the box is ${high.height}, expected $box", abs((high.height - box).value) <= 1f)
        assertEquals(keys, compose.onNodeWithContentDescription("Flute keys").getUnclippedBoundsInRoot())
        assertEquals(play, compose.onNodeWithText("Play").getUnclippedBoundsInRoot())
    }

    @Test
    fun a_tune_up_to_c4_keeps_the_listening_room_while_it_runs() {
        show(FluteUiState(tune = twoNotes, listening = Listening(index = 0)), height = 653.dp)
        val ordinary = compose.onNodeWithContentDescription("Your tune, 2 notes").getUnclippedBoundsInRoot()
        val stop = compose.onNodeWithText("Stop").getBoundsInRoot()
        val animal = compose.onNodeWithTag(WALKING_ANIMAL_TAG).getUnclippedBoundsInRoot()
        val animalY = (animal.top + animal.bottom) / 2 - ordinary.top
        assertTrue("over a tune up to c3 the animal walks at 27 as before, was $animalY", abs((animalY - 27.dp).value) <= 1f)
        state.value = FluteUiState(tune = upToC7, listening = Listening(index = 0))
        val high = compose.onNodeWithContentDescription("Your tune, 2 notes").getUnclippedBoundsInRoot()
        assertTrue("a tune up to c4 runs ${high.height} tall, no taller than ${ordinary.height}", high.height <= ordinary.height + 1.dp)
        assertTrue("Stop stays where it was", abs((compose.onNodeWithText("Stop").getBoundsInRoot().top - stop.top).value) <= 1f)
        val animalHigh = compose.onNodeWithTag(WALKING_ANIMAL_TAG).getUnclippedBoundsInRoot()
        val animalYHigh = (animalHigh.top + animalHigh.bottom) / 2 - high.top
        assertTrue("over a tune up to c4 the animal walks higher, at $animalYHigh", abs((animalYHigh - 20.86.dp).value) <= 1f)
    }

    // ---- The octave pills (2026-11-18): c¹–h¹ … c⁴ over one row of letter keys named for the chosen octave ----

    private val tools = listOf("Sharp or flat for the next note", "Join to the next note", "Rest", "Breathe", "Erase the last note")

    @Test
    fun the_pills_stand_over_the_letters_and_the_tools_under_them() {
        show(FluteUiState())
        fun bounds(name: String) = compose.onNodeWithContentDescription(name).getBoundsInRoot()
        val pills = listOf("Octave 4", "Octave 5", "Octave 6", "Octave 7").map(::bounds)
        val letters = listOf("C4", "D4", "E4", "F4", "G4", "A4", "B4").map(::bounds)
        val (sign, join, rest, breath, erase) = tools.map(::bounds)
        val c = letters.first()
        for (key in letters) assertTrue("$key should share C4's row $c", abs((key.top - c.top).value) <= 1f && abs((key.width - c.width).value) <= 1f)
        for (key in listOf(sign, join, rest, breath, erase)) {
            assertTrue("$key should be one key wide in the row under the letters", abs((key.width - c.width).value) <= 1f && abs((key.top - sign.top).value) <= 1f)
        }
        assertTrue("the tools $sign under the letters $c", sign.top >= c.bottom)
        for ((pill, next) in pills.zipWithNext()) assertTrue("$pill left of $next, level", pill.right <= next.left && abs((pill.top - next.top).value) <= 1f)
        assertTrue("the pills ${pills.first()} over the letters $c", pills.first().bottom <= c.top + 1.dp)
        assertTrue("the pills span the keys", abs((pills.first().left - c.left).value) <= 1f && abs((pills.last().right - letters.last().right).value) <= 1f)
        // The tools stand in columns 1 2, 4 5 and 7.
        assertTrue("♯♭ under C", abs((sign.left - letters[0].left).value) <= 1f)
        assertTrue("⌒ under D", abs((join.left - letters[1].left).value) <= 1f)
        assertTrue("💤 under F", abs((rest.left - letters[3].left).value) <= 1f)
        assertTrue("😮‍💨 under G", abs((breath.left - letters[4].left).value) <= 1f)
        assertTrue("🧽 under B", abs((erase.right - letters[6].right).value) <= 1f)
        assertTrue("a key should be 58 dp tall, was ${c.height}", abs((c.height - 58.dp).value) <= 0.5f)
        assertTrue("a key should be 48 dp wide on a 412 dp phone, was ${c.width}", c.width >= 47.dp)
        // The pill is 40 dp tall with a 3 dp shadow (32 until 2026-11-19: "very hard to read"), and the 8 dp under it are its too.
        assertTrue("a pill is touched over at least 50 dp, was ${pills.first().height}", pills.first().height >= 50.dp)
        val name = compose.onNodeWithText("C4–B4", useUnmergedTree = true).getBoundsInRoot()
        assertTrue("the pill's name is drawn at about 22 dp: its line is ${name.height} tall", name.height >= 24.dp)
        compose.onNodeWithContentDescription("Octave 4").assertIsSelected()
        compose.onNodeWithContentDescription("Octave 5").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
    }

    @Test
    fun a_pill_renames_the_keys() {
        show(FluteUiState())
        val before = compose.onNodeWithContentDescription("D4").getBoundsInRoot()
        state.value = state.value.copy(octave = 3)
        for (name in listOf("C6", "D6", "E6", "F6", "G6", "A6", "B6")) compose.onNodeWithContentDescription(name).assertIsEnabled()
        compose.onNodeWithContentDescription("D4").assertDoesNotExist()
        compose.onNodeWithContentDescription("Octave 6").assertIsSelected()
        assertEquals("the same key, named d3 now", before, compose.onNodeWithContentDescription("D6").getBoundsInRoot())
        compose.onNodeWithText("D6", useUnmergedTree = true).assertExists() // the name is on the key, not only said
    }

    @Test
    fun in_octave_four_only_c_is_a_key() {
        show(FluteUiState(octave = 4))
        compose.onNodeWithContentDescription("C7").assertIsEnabled()
        for (name in listOf("D7", "E7", "F7", "G7", "A7", "B7")) compose.onNodeWithContentDescription(name).assertIsNotEnabled()
        state.value = state.value.copy(signWaiting = true)
        compose.onNodeWithContentDescription("C7").assertIsNotEnabled()
    }

    @Test
    fun tapping_a_pill_reports_its_octave() {
        show(FluteUiState())
        compose.onNodeWithContentDescription("Octave 6").performTouchInput { down(center) }
        assertEquals("on the finger going down", listOf(3), octaves)
        compose.onNodeWithContentDescription("Octave 6").performTouchInput { up() }
        compose.onNodeWithContentDescription("Octave 7").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf(3, 4), octaves)
    }

    @Test
    fun a_key_held_while_the_octave_changes_is_not_let_go() {
        // The key's name goes from C4 to C5 under the finger; it still sounds C4, and the same finger lets it go.
        show(FluteUiState())
        compose.onNodeWithContentDescription("C4").performTouchInput { down(center) }
        compose.runOnIdle { state.value = state.value.copy(octave = 2) }
        compose.waitForIdle()
        assertTrue("let go of while still held: $released", released.isEmpty())
        compose.onNodeWithContentDescription("C5").performTouchInput { up() }
        assertEquals(listOf(0), pressed)
        assertEquals(listOf(0), released)
    }

    @Test
    fun the_pills_show_the_notes_of_their_octave() {
        // The user, 2026-11-18: "replace 1, 2, 3, 4 labels with c1-h1, c2-h2, c3-h3, and c4".
        show(FluteUiState())
        for (range in listOf("C4–B4", "C5–B5", "C6–B6", "C7")) compose.onNodeWithText(range, useUnmergedTree = true).assertExists()
        compose.onNodeWithText("4", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun in_croatian_the_pills_show_c1_to_h1_and_the_keys_carry_the_octave() {
        show(FluteUiState(octave = 3), Locale("hr"))
        for (range in listOf("c1–h1", "c2–h2", "c3–h3", "c4")) compose.onNodeWithText(range, useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("Oktava 3").assertIsSelected()
        compose.onNodeWithContentDescription("Oktava 1").assertExists()
        compose.onNodeWithContentDescription("d3").assertIsEnabled()
        compose.onNodeWithContentDescription("h3").assertIsEnabled()
    }

    // A step chosen on the staff for the keys to change (since 2026-11-24, spec 2026-11-24-flute-edit-a-note-design.md).

    @Test
    fun a_tap_on_a_step_chooses_it() {
        show(FluteUiState(tune = listOf(TuneNote(60, NoteLength.QUARTER), TuneNote(64, NoteLength.HALF))))
        compose.onNodeWithContentDescription("E4, 2 beats").performClick()
        assertEquals(listOf(1), stepTaps)
    }

    @Test
    fun the_chosen_step_is_selected_and_the_sponge_says_so() {
        show(FluteUiState(tune = listOf(TuneNote(60, NoteLength.QUARTER), TuneNote(64, NoteLength.HALF)), selected = 1))
        compose.onNodeWithContentDescription("E4, 2 beats").assertIsSelected()
        // Unchosen steps say nothing about it, or a screen reader says "not selected" on every one.
        compose.onNodeWithContentDescription("C4, 1 beat").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
        compose.onNodeWithContentDescription("Erase the chosen note").assertIsEnabled()
        compose.onNodeWithContentDescription("Erase the last note").assertDoesNotExist()
    }

    @Test
    fun holding_erase_with_a_chosen_note_erases_once() {
        show(FluteUiState(tune = List(6) { TuneNote(60 + it, NoteLength.QUARTER) }, selected = 2))
        compose.onNodeWithContentDescription("Erase the chosen note").assertExists()
        // Found by either name: the first erase lets the choice go and the key is renamed under the finger.
        val sponge = compose.onNode(hasContentDescription("Erase the", substring = true))
        compose.mainClock.autoAdvance = false
        sponge.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1_500)
        sponge.performTouchInput { up() }
        assertEquals("the first erase lets the choice go, and the hold goes on no further", 1, erases)
        // The same hold with nothing chosen repeats, so the clock did reach the repeats.
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Erase the last note").assertExists()
        compose.mainClock.autoAdvance = false
        sponge.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(1_500)
        sponge.performTouchInput { up() }
        assertTrue("held with nothing chosen it repeats", erases > 2)
    }

    @Test
    fun a_breath_can_be_chosen_and_shows_it() {
        show(FluteUiState(tune = listOf(TuneNote(60, NoteLength.QUARTER), TuneBreath, TuneNote(64, NoteLength.HALF))))
        compose.onNodeWithContentDescription("Breathe here").performClick()
        assertEquals(listOf(1), stepTaps)
        state.value = state.value.copy(selected = 1)
        compose.onNodeWithContentDescription("Breathe here").assertIsSelected()
    }

    @Test
    fun steps_cannot_be_chosen_while_the_tune_runs() {
        show(FluteUiState(tune = listOf(TuneNote(60, NoteLength.QUARTER), TuneNote(64, NoteLength.HALF)), playing = Playing(0)))
        compose.onNodeWithContentDescription("E4, 2 beats").performClick()
        assertTrue(stepTaps.isEmpty())
    }

    @Test
    fun choosing_and_changing_a_step_does_not_scroll_the_staff() {
        val tune = List(24) { TuneNote(if (it == 3) 76 else 60, NoteLength.QUARTER) } // E5 is the fourth step
        show(FluteUiState(tune = tune))
        compose.onNodeWithContentDescription("Your tune, 24 notes").performTouchInput { swipeRight() } // back to the start
        compose.waitForIdle()
        val before = compose.onNodeWithContentDescription("E5, 1 beat").getBoundsInRoot().left
        state.value = state.value.copy(selected = 3)
        compose.waitForIdle()
        state.value = state.value.copy(held = Held(76, index = 3, replaces = true))
        compose.waitForIdle()
        assertEquals(before, compose.onNodeWithContentDescription("E5, 1 beat").getBoundsInRoot().left)
    }

    @Test
    fun a_step_let_go_leaves_the_staff_where_it_is_until_a_key_writes_at_the_end() {
        val tune = List(24) { TuneNote(if (it == 3) 76 else 60, NoteLength.QUARTER) } // E5 is the fourth step
        show(FluteUiState(tune = tune))
        compose.onNodeWithContentDescription("Your tune, 24 notes").performTouchInput { swipeRight() } // back to the start
        compose.waitForIdle()
        fun e5() = compose.onNodeWithContentDescription("E5, 1 beat").getBoundsInRoot().left
        val before = e5()
        state.value = state.value.copy(selected = 2)
        compose.waitForIdle()
        state.value = state.value.copy(selected = null) // the same tap again lets it go
        compose.waitForIdle()
        assertEquals("let go", before, e5())
        state.value = state.value.copy(selected = 5)
        compose.waitForIdle()
        state.value = state.value.copy(tune = tune.filterIndexed { index, _ -> index != 5 }, selected = null) // 🧽 on it
        compose.waitForIdle()
        assertEquals("erased", before, e5())
        // The next key writes at the end, and the staff follows it there again.
        state.value = state.value.copy(tune = state.value.tune + TuneNote(67, NoteLength.EIGHTH), held = Held(67, index = 23))
        compose.waitForIdle()
        compose.onNodeWithContentDescription("G4, half a beat").assertIsDisplayed()
    }

    @Test
    fun a_tap_beside_the_notes_lets_the_chosen_step_go() {
        show(FluteUiState(tune = listOf(TuneNote(60, NoteLength.QUARTER), TuneNote(64, NoteLength.HALF)), selected = 1))
        compose.onNodeWithContentDescription("Your tune, 2 notes").performTouchInput { click(Offset(width - 4f, height / 2f)) }
        assertEquals(1, stageTaps)
        compose.onNodeWithContentDescription("E4, 2 beats").performClick()
        assertEquals("a note's tap is the note's", 1, stageTaps)
        assertEquals(listOf(1), stepTaps)
    }

    @Test
    fun the_stage_takes_no_tap_while_the_tune_runs() {
        show(FluteUiState(tune = listOf(TuneNote(60, NoteLength.QUARTER), TuneNote(64, NoteLength.HALF)), playing = Playing(0)))
        compose.onNodeWithContentDescription("Your tune, 2 notes").performTouchInput { click(Offset(width - 4f, height / 2f)) }
        assertEquals(0, stageTaps)
    }
}
