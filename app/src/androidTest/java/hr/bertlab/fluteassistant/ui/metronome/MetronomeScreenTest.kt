// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.metronome

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import hr.bertlab.fluteassistant.domain.metronome.MetronomeConfig
import hr.bertlab.fluteassistant.domain.metronome.TempoBand
import hr.bertlab.fluteassistant.domain.metronome.TimeSignature
import hr.bertlab.fluteassistant.ui.common.BackKey
import hr.bertlab.fluteassistant.ui.common.LocaleProvider
import hr.bertlab.fluteassistant.ui.common.needsDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class MetronomeScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var state: MutableState<MetronomeUiState>
    private var toggles = 0
    private var chosenBpm: Int? = null
    private var chosenSignature: TimeSignature? = null

    private val back = BackKey()

    private fun show(initial: MetronomeUiState, locale: Locale = Locale.ENGLISH) {
        state = mutableStateOf(initial)
        compose.setContent {
            LocaleProvider(locale) {
                back.Watch()
                MetronomeContent(
                    state = state.value,
                    onTogglePlayback = { toggles++ },
                    onBpmChange = { bpm ->
                        chosenBpm = bpm
                        state.value = state.value.copy(config = state.value.config.withBpm(bpm))
                    },
                    onTimeSignatureChange = { chosenSignature = it },
                )
            }
        }
    }

    @Test
    fun start_button_toggles_playback_and_reflects_the_playing_state() {
        show(MetronomeUiState())
        compose.onNodeWithText("Play").performClick()
        assertEquals(1, toggles)

        compose.runOnIdle { state.value = state.value.copy(isPlaying = true) }
        compose.onNodeWithText("Stop").assertIsDisplayed()
    }

    @Test
    fun the_ring_shows_only_the_tempo_and_tells_a_screen_reader_its_animal() {
        show(MetronomeUiState(config = MetronomeConfig(bpm = 120)))
        compose.onNodeWithText("Rabbit").assertDoesNotExist()
        ring().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Rabbit"))
        compose.runOnIdle { state.value = state.value.copy(config = MetronomeConfig(bpm = 70)) }
        ring().assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Turtle"))
        compose.onNodeWithText("70").assertIsDisplayed()
    }

    private fun ring() = compose.onNodeWithContentDescription("Tempo ring, drag around it to change the speed")

    @Test
    fun tapping_a_band_jumps_to_its_start_and_the_rabbit_again_to_120() {
        show(MetronomeUiState(config = MetronomeConfig(bpm = 97)))
        compose.onNodeWithContentDescription("Turtle").performClick()
        assertEquals(60, chosenBpm)
        compose.onNodeWithContentDescription("Rabbit").performClick()
        assertEquals(100, chosenBpm)
        compose.onNodeWithContentDescription("Rabbit").performClick()
        assertEquals(120, chosenBpm)
    }

    @Test
    fun assistive_technology_can_set_the_tempo_on_the_ring() {
        show(MetronomeUiState(config = MetronomeConfig(bpm = 120)))
        compose.onNodeWithContentDescription("Tempo ring, drag around it to change the speed")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(75f) }
        assertEquals(75, chosenBpm)
    }

    @Test
    fun tapping_the_number_opens_a_wheel_with_the_neighbouring_tempos() {
        show(MetronomeUiState(config = MetronomeConfig(bpm = 120)))
        compose.onNodeWithText("119").assertDoesNotExist()
        compose.onNodeWithText("120").performClick()
        compose.onNodeWithText("119").assertIsDisplayed()
        compose.onNodeWithText("121").assertIsDisplayed()
    }

    @Test
    fun back_closes_the_open_wheel_and_nothing_else() {
        show(MetronomeUiState(config = MetronomeConfig(bpm = 120)))
        assertFalse("with the wheel closed Back is the phone's", back.taken(compose))
        compose.onNodeWithText("120").performClick()
        compose.onNodeWithText("119").assertIsDisplayed()
        back.press(compose)
        compose.onNodeWithText("119").assertDoesNotExist()
        compose.onNodeWithText("120").assertIsDisplayed()
        assertFalse(back.taken(compose))
    }

    @Test
    fun scrolling_the_wheel_changes_the_tempo_one_beat_per_notch() {
        show(MetronomeUiState(config = MetronomeConfig(bpm = 120)))
        compose.onNodeWithText("120").performClick()
        compose.onNodeWithContentDescription("Tempo wheel, scroll to change the speed")
            .performScrollToIndex(123 - TempoBand.MIN_BPM)
        compose.waitForIdle()
        assertEquals(123, chosenBpm)
        compose.onNodeWithText("122").assertIsDisplayed()
        compose.onNodeWithText("124").assertIsDisplayed()
    }

    @Test
    fun tapping_the_centre_value_closes_the_wheel() {
        show(MetronomeUiState(config = MetronomeConfig(bpm = 120)))
        compose.onNodeWithText("120").performClick()
        compose.onNodeWithText("119").assertIsDisplayed()
        compose.onNodeWithText("120").performClick()
        compose.onNodeWithText("119").assertDoesNotExist()
        compose.onNodeWithText("120").assertIsDisplayed()
    }

    @Test
    fun tapping_anywhere_else_closes_the_wheel() {
        show(MetronomeUiState(config = MetronomeConfig(bpm = 120)))
        compose.onNodeWithText("120").performClick()
        compose.onNodeWithText("119").assertIsDisplayed()
        compose.onNodeWithText("Play").performClick()
        compose.onNodeWithText("119").assertDoesNotExist()
        assertEquals(1, toggles)
    }

    @Test
    fun the_open_wheel_follows_a_band() {
        show(MetronomeUiState(config = MetronomeConfig(bpm = 120)))
        compose.onNodeWithText("120").performClick()
        compose.onNodeWithContentDescription("Duck").performClick()
        assertEquals(80, chosenBpm)
        compose.onNodeWithText("119").assertDoesNotExist()
        compose.onNodeWithText("80").assertIsDisplayed()
    }

    @Test
    fun the_beat_buttons_step_one_beat_each_way() {
        show(MetronomeUiState(config = MetronomeConfig(timeSignature = TimeSignature.FOUR_FOUR)))
        compose.onNodeWithContentDescription("Beats in a bar: 4").assertIsDisplayed()

        compose.onNodeWithContentDescription("More beats").performClick()
        assertEquals(TimeSignature.FIVE_FOUR, chosenSignature)
        compose.onNodeWithContentDescription("Fewer beats").performClick()
        assertEquals(TimeSignature.THREE_FOUR, chosenSignature)
    }

    /**
     * 6/8's fourth bubble is a medium accent, a little bigger than a plain one, so a row that counts
     * only the first as bigger comes out too wide and squeezes the last bubble into an oval. The
     * bubbles have no nodes of their own, so the last one is measured from what is drawn.
     */
    @Test
    fun six_beats_stay_round() {
        needsDevice()
        show(MetronomeUiState(config = MetronomeConfig(timeSignature = TimeSignature.SIX_EIGHT)))
        val image = compose.onNodeWithContentDescription("Beats in a bar: 6").captureToImage().toPixelMap()
        val background = image[0, 0]
        fun drawn(x: Int) = (0 until image.height).any { y -> image[x, y] != background }
        val right = (image.width - 1 downTo 0).first { drawn(it) }
        val left = (right downTo 0).first { !drawn(it) } + 1
        val rows = (0 until image.height).filter { y -> (left..right).any { x -> image[x, y] != background } }
        val width = right - left + 1
        val height = rows.last() - rows.first() + 1
        assertTrue("the last bubble is $width px wide and $height px tall", kotlin.math.abs(width - height) <= 2)
    }

    @Test
    fun the_beat_buttons_stop_at_two_and_six() {
        show(MetronomeUiState(config = MetronomeConfig(timeSignature = TimeSignature.SIX_EIGHT)))
        compose.onNodeWithContentDescription("More beats").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Fewer beats").assertIsEnabled()

        compose.runOnIdle { state.value = MetronomeUiState(config = MetronomeConfig(timeSignature = TimeSignature.TWO_FOUR)) }
        compose.onNodeWithContentDescription("Fewer beats").assertIsNotEnabled()
        compose.onNodeWithContentDescription("More beats").assertIsEnabled()
    }

    @Test
    fun the_beats_sit_above_the_ring_and_the_ring_above_the_play_button() {
        show(MetronomeUiState())
        val beats = compose.onNodeWithContentDescription("Beats in a bar: 4").getBoundsInRoot()
        val ring = compose.onNodeWithContentDescription("Tempo ring, drag around it to change the speed").getBoundsInRoot()
        val play = compose.onNodeWithText("Play").getBoundsInRoot()
        assertTrue("beats bottom ${beats.bottom} should be above ring top ${ring.top}", beats.bottom < ring.top)
        assertTrue("ring bottom ${ring.bottom} should be above play top ${play.top}", ring.bottom < play.top)
    }

    @Test
    fun croatian_system_language_shows_croatian_labels() {
        show(MetronomeUiState(), Locale("hr"))
        compose.onNodeWithText("Sviraj").assertIsDisplayed()
        compose.onNodeWithText("Zec").assertDoesNotExist()
        compose.onNodeWithContentDescription("Zec").assertExists()
        compose.onNodeWithContentDescription("Dobe u taktu: 4").assertIsDisplayed()
        compose.onAllNodesWithContentDescription("Više doba").assertCountEquals(1)
        compose.onNodeWithContentDescription("Kornjača").assertExists()
        compose.onNodeWithText("120").performClick()
        compose.onNodeWithContentDescription("Kotačić tempa, zavrti ga za promjenu brzine").assertExists()
    }
}
