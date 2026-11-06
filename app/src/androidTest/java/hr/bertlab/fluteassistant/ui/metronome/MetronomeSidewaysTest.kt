// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.metronome

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import hr.bertlab.fluteassistant.domain.model.MetronomeConfig
import hr.bertlab.fluteassistant.domain.model.TimeSignature
import hr.bertlab.fluteassistant.ui.AppTab
import hr.bertlab.fluteassistant.ui.SideRail
import hr.bertlab.fluteassistant.ui.common.LocalSideways
import hr.bertlab.fluteassistant.ui.common.LocaleProvider
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
 * The metronome on a phone on its side: beats and ring side by side at rest, and while it plays the
 * ring goes and the beats fill the card. The upright tab is `MetronomeScreenTest`'s.
 */
@RunWith(AndroidJUnit4::class)
class MetronomeSidewaysTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var state: MutableState<MetronomeUiState>
    private var phone by mutableStateOf(Phone(915, 411))
    private var toggles = 0

    private class Phone(val width: Int, val height: Int) {
        val contentWidth: Float get() = width - SidewaysGeometry.railWidth(height.toFloat())
        val cardWidth: Float get() = SidewaysGeometry.cardWidth(contentWidth, height.toFloat())
        override fun toString() = "$width x $height"
    }

    private fun show(
        initial: MetronomeUiState = MetronomeUiState(),
        sideways: Boolean = true,
        locale: Locale = Locale.ENGLISH,
        fontScale: Float = 1f,
    ) {
        state = mutableStateOf(initial)
        compose.setContent {
            LocaleProvider(locale) {
                SidewaysHost(width = phone.width, height = phone.height, fontScale = fontScale, sideways = sideways) {
                    content()
                }
            }
        }
    }

    @Composable
    private fun content() {
        MetronomeContent(
            state = state.value,
            onTogglePlayback = { toggles++ },
            onBpmChange = { bpm -> state.value = state.value.copy(config = state.value.config.withBpm(bpm)) },
            onTimeSignatureChange = { signature -> state.value = state.value.copy(config = state.value.config.copy(timeSignature = signature)) },
        )
    }

    private fun ring() = compose.onNodeWithContentDescription(RING)
    private fun beats(count: Int = 4) = compose.onNodeWithContentDescription("Beats in a bar: $count")
    private fun fewer() = compose.onNodeWithContentDescription("Fewer beats")
    private fun more() = compose.onNodeWithContentDescription("More beats")

    private fun bounds(node: SemanticsNodeInteraction) = node.getUnclippedBoundsInRoot()

    private fun width(node: SemanticsNodeInteraction) = bounds(node).let { it.right - it.left }

    private fun height(node: SemanticsNodeInteraction) = bounds(node).let { it.bottom - it.top }

    /** The forced size rounds the density down, which leaves sizes a dp or two off the ones asked for. */
    private fun assertNear(what: String, expected: Dp, actual: Dp, tolerance: Float = TOLERANCE) =
        assertTrue("$what: expected about $expected, got $actual", abs((expected - actual).value) <= tolerance)

    /**
     * A size that follows the width grows or shrinks with the rounding of the density (one percent
     * was seen), so the tolerance is a share of the size, not a dp or two. For 6/8 the geometry's unit
     * is also a little over what is drawn: it counts only the first bubble as bigger, and the
     * row counts the fourth's medium accent too.
     */
    private fun assertNearBySize(what: String, expected: Dp, actual: Dp) =
        assertNear(what, expected, actual, tolerance = TOLERANCE + expected.value * RELATIVE_TOLERANCE)

    /**
     * The first bubble's size, read off the beats' height: its column is the animal's room, a 6 dp gap,
     * the first bubble (1.16 times the others) and what upright's column has to spare for the pop.
     * Bubbles have no node of their own, as the beats clear their children's semantics.
     */
    private fun bubbleOf(beats: SemanticsNodeInteraction, animalRoom: Dp): Dp =
        ((height(beats) - animalRoom - 6.dp - COLUMN_SPARE).value / 1.16f).dp

    @Test
    fun at_rest_shows_the_ring_the_steps_and_play() {
        show()
        ring().assertIsDisplayed()
        fewer().assertIsDisplayed()
        more().assertIsDisplayed()
        beats().assertIsDisplayed()
        compose.onNodeWithText("120").assertIsDisplayed()
        compose.onNodeWithText("Play").assertIsDisplayed()
        compose.onNodeWithText("Stop").assertDoesNotExist()

        // Beats on the left, the ring on the right, Play low on the right, the order upright reads top down.
        val beats = bounds(beats())
        val ring = bounds(ring())
        val play = bounds(compose.onNodeWithText("Play"))
        assertTrue("the beats end at ${beats.right}, the ring starts at ${ring.left}", beats.right <= ring.left)
        assertTrue("the ring ends at ${ring.right}, Play starts at ${play.left}", ring.right <= play.left)
        assertNear("the ring", 304.dp, ring.right - ring.left)
        assertNear("the ring's height", 304.dp, ring.bottom - ring.top)
        assertNear("Play's width", 116.dp, play.right - play.left)
        assertNear("Play's height", 104.dp, play.bottom - play.top)
        assertNear("Play's bottom", phone.height.dp - 16.dp, play.bottom)
        assertNear("the bubbles", 68.dp, bubbleOf(beats(), animalRoom = 40.dp))
    }

    @Test
    fun the_steps_sit_under_the_beats_centred_56_dp_and_28_apart() {
        show()
        val beats = bounds(beats())
        val fewer = bounds(fewer())
        val more = bounds(more())
        assertNear("the minus", 56.dp, fewer.right - fewer.left)
        assertNear("the minus's height", 56.dp, fewer.bottom - fewer.top)
        assertNear("the plus", 56.dp, more.right - more.left)
        assertNear("the gap between them", 28.dp, more.left - fewer.right)
        assertNear("their middle", (beats.left + beats.right) / 2, (fewer.left + more.right) / 2)
        assertTrue("the steps start at ${fewer.top}, the beats end at ${beats.bottom}", fewer.top >= beats.bottom)
    }

    @Test
    fun the_steps_still_step_and_play_still_plays() {
        show()
        fewer().performClick()
        assertEquals(TimeSignature.THREE_FOUR, state.value.config.timeSignature)
        compose.onNodeWithText("Play").performClick()
        assertEquals(1, toggles)
    }

    @Test
    fun playing_hides_the_ring_and_steps_and_keeps_the_figure() {
        show(MetronomeUiState(isPlaying = true))
        ring().assertDoesNotExist()
        fewer().assertDoesNotExist()
        more().assertDoesNotExist()
        compose.onNodeWithText("120").assertIsDisplayed()
        compose.onNodeWithText("Stop").assertIsDisplayed()
        compose.onNodeWithText("Play").assertDoesNotExist()
        beats().assertIsDisplayed()
    }

    private fun well() = compose.onNodeWithTag(FIGURE_WELL_TAG)

    @Test
    fun the_figure_sits_in_a_104_dp_well_above_stop() {
        show(MetronomeUiState(isPlaying = true))
        val well = bounds(well().assertIsDisplayed())
        val figure = bounds(compose.onNodeWithText("120"))
        val stop = bounds(compose.onNodeWithText("Stop"))
        assertNear("the well's width", 104.dp, well.right - well.left)
        assertNear("the well's height", 104.dp, well.bottom - well.top)
        assertTrue("the well ends at ${well.bottom}, Stop starts at ${stop.top}", well.bottom <= stop.top)
        assertTrue("the figure $figure is in the well $well", figure.left >= well.left - SLACK && figure.right <= well.right + SLACK && figure.top >= well.top - SLACK && figure.bottom <= well.bottom + SLACK)
        assertNear("the well is centred over Stop", (stop.left + stop.right) / 2, (well.left + well.right) / 2)
        assertNear("Stop's width", 116.dp, stop.right - stop.left)
        assertNear("Stop's height", 104.dp, stop.bottom - stop.top)
        assertNear("Stop's bottom", phone.height.dp - 16.dp, stop.bottom)
    }

    /** The biggest figure at the biggest text: "200" is shrunk by its size steps to stay inside the well, on the short column too. */
    @Test
    fun the_figure_stays_whole_in_its_well_at_200_bpm_and_large_text() {
        show(MetronomeUiState(isPlaying = true, config = MetronomeConfig(bpm = 200)), fontScale = 1.5f)
        listOf(Phone(915, 411), Phone(640, 360)).forEach { each ->
            compose.runOnIdle { phone = each }
            compose.waitForIdle()
            val well = bounds(well().assertIsDisplayed())
            val figure = bounds(compose.onNodeWithText("200").assertIsDisplayed())
            assertWholeWord("200")
            assertTrue("$each: the figure $figure is in the well $well", figure.left >= well.left - SLACK && figure.right <= well.right + SLACK && figure.top >= well.top - SLACK && figure.bottom <= well.bottom + SLACK)
        }
    }

    @Test
    fun the_figure_cannot_be_pressed() {
        show(MetronomeUiState(isPlaying = true))
        compose.onNodeWithText("120").assertHasNoClickAction()
        compose.onNodeWithText("120").performClick()
        assertEquals(0, toggles)
        compose.onNodeWithText("120").assertIsDisplayed()
        ring().assertDoesNotExist()
    }

    @Test
    fun stop_brings_the_ring_back() {
        show(MetronomeUiState(isPlaying = true))
        ring().assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(isPlaying = false) }
        ring().assertIsDisplayed()
        fewer().assertIsDisplayed()
        compose.onNodeWithText("Play").assertIsDisplayed()
        compose.onNodeWithText("Stop").assertDoesNotExist()
    }

    /** A wheel left open must not come back with the ring when Stop is pressed after a start from elsewhere. */
    @Test
    fun starting_closes_an_open_wheel() {
        show()
        compose.onNodeWithText("120").performClick()
        compose.onNodeWithText("119").assertIsDisplayed()
        compose.runOnIdle { state.value = state.value.copy(isPlaying = true) }
        compose.onNodeWithText("119").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(isPlaying = false) }
        compose.onNodeWithText("119").assertDoesNotExist()
        compose.onNodeWithText("120").assertIsDisplayed()
    }

    @Test
    fun pressing_play_with_the_wheel_open_closes_it() {
        show()
        compose.onNodeWithText("120").performClick()
        compose.onNodeWithText("119").assertIsDisplayed()
        compose.onNodeWithText("Play").performClick()
        compose.onNodeWithText("119").assertDoesNotExist()
        assertEquals(1, toggles)
    }

    @Test
    fun upright_keeps_the_ring_while_it_plays() {
        state = mutableStateOf(MetronomeUiState(isPlaying = true))
        compose.setContent { LocaleProvider(Locale.ENGLISH) { content() } }
        ring().assertIsDisplayed()
        fewer().assertIsDisplayed()
        more().assertIsDisplayed()
        compose.onNodeWithText("Stop").assertIsDisplayed()
    }

    @Test
    fun the_takeover_bubbles_are_144_dp_with_four_beats_and_97_with_six_on_the_emulators_phone() {
        show(MetronomeUiState(isPlaying = true))
        assertNearBySize("four beats", 144.dp, bubbleOf(beats(4), animalRoom = 72.dp))
        compose.runOnIdle {
            state.value = state.value.copy(config = MetronomeConfig(timeSignature = TimeSignature.SIX_EIGHT))
        }
        assertNearBySize("six beats", 97.dp, bubbleOf(beats(6), animalRoom = 72.dp))
    }

    @Test
    fun fits_every_phone() {
        show()
        listOf(Phone(915, 411), Phone(800, 360), Phone(640, 360)).forEach { each ->
            listOf(TimeSignature.FOUR_FOUR, TimeSignature.SIX_EIGHT).forEach { signature ->
                listOf(false, true).forEach { playing ->
                    assertFits(each, signature, playing)
                }
            }
        }
    }

    @Test
    fun fits_at_large_text_in_croatian() {
        show(locale = Locale("hr"), fontScale = 1.5f)
        listOf(Phone(915, 411), Phone(640, 360)).forEach { each ->
            listOf(false, true).forEach { playing ->
                assertFits(each, TimeSignature.SIX_EIGHT, playing, play = "Sviraj", stop = "Stani", ring = "Prsten tempa, povuci po njemu za promjenu brzine")
            }
        }
    }

    private fun assertFits(
        each: Phone,
        signature: TimeSignature,
        playing: Boolean,
        play: String = "Play",
        stop: String = "Stop",
        ring: String = RING,
    ) {
        compose.runOnIdle {
            phone = each
            state.value = MetronomeUiState(config = MetronomeConfig(timeSignature = signature), isPlaying = playing)
        }
        compose.waitForIdle()
        val label = "$each, ${signature.beatsPerBar} beats, ${if (playing) "playing" else "at rest"}"
        val right = each.contentWidth.dp + SLACK
        val bottom = each.height.dp + SLACK

        fun inside(what: String, node: SemanticsNodeInteraction, shadow: Dp = 0.dp) {
            val b = bounds(node)
            assertTrue("$label: $what starts at ${b.left} x ${b.top}", b.left >= -SLACK && b.top >= -SLACK)
            assertTrue("$label: $what ends at ${b.right} x ${b.bottom + shadow}, the room is $right x $bottom", b.right <= right && b.bottom + shadow <= bottom)
        }

        val main = compose.onNodeWithText(if (playing) stop else play).assertIsDisplayed()
        inside("the main button", main, shadow = 8.dp)
        assertWholeWord(if (playing) stop else play)
        inside("the beats", compose.onNodeWithContentDescription(beatsDescription(signature.beatsPerBar, play)))
        val beats = compose.onNodeWithContentDescription(beatsDescription(signature.beatsPerBar, play))
        if (playing) {
            inside("the figure", compose.onNodeWithText("120"))
            val innerWidth = each.cardWidth - SidewaysGeometry.CARD_CHROME
            val expected = SidewaysGeometry.bigBeatUnit(innerWidth, signature.beatsPerBar)
            val unit = bubbleOf(beats, animalRoom = 72.dp)
            assertNearBySize("$label: the takeover's bubbles", expected.dp, unit)
            assertTrue("$label: the bubbles are $unit, not past upright's 52", unit > 52.dp)
        } else {
            inside("the ring", compose.onNodeWithContentDescription(ring))
            val expected = SidewaysGeometry.ringSize(each.cardWidth, each.height.toFloat())
            assertNearBySize("$label: the ring", expected.dp, width(compose.onNodeWithContentDescription(ring)))
            val steps = listOf("Fewer beats", "More beats").map { compose.onNodeWithContentDescription(stepDescription(it, play)) }
            steps.forEach { inside("a step", it, shadow = 4.dp) }
            val ringBounds = bounds(compose.onNodeWithContentDescription(ring))
            assertTrue("$label: the beats overlap the ring", bounds(beats).right <= ringBounds.left + SLACK)
            assertTrue("$label: the ring overlaps Play", ringBounds.right <= bounds(main).left + SLACK)
        }
    }

    /** The descriptions are English or Croatian by the caller's word for Play. */
    private fun beatsDescription(count: Int, play: String) = if (play == "Play") "Beats in a bar: $count" else "Dobe u taktu: $count"

    private fun stepDescription(english: String, play: String) =
        if (play == "Play") english else if (english.startsWith("Fewer")) "Manje doba" else "Više doba"

    /** A word cut short still reads as displayed, so this asks the word's own layout. */
    private fun assertWholeWord(word: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(word, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertFalse("$word is cut short", layouts.single().hasVisualOverflow)
    }

    /**
     * Review Focus 5: a child who taps the drum on another tab lands on the takeover, whose Stop is
     * under the thumb, while the rail still says the drum plays. The rail sits beside the content
     * in a host of its own, as `SidewaysHost` takes the rail off the width itself.
     */
    @Test
    fun the_drum_tab_leads_to_the_takeover() {
        state = mutableStateOf(MetronomeUiState(isPlaying = true))
        compose.setContent {
            LocaleProvider(Locale.ENGLISH) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(915.dp, 411.dp))) {
                    CompositionLocalProvider(LocalSideways provides true) {
                        Row(Modifier.fillMaxSize()) {
                            SideRail(
                                selected = AppTab.METRONOME,
                                onSelect = {},
                                drumBeating = state.value.isPlaying,
                                beatPulse = 0L,
                            )
                            Box(Modifier.weight(1f).fillMaxHeight()) { content() }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("Metronome")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Playing"))
        compose.onNodeWithText("Stop").assertIsDisplayed()
        ring().assertDoesNotExist()
    }

    private companion object {
        const val RING = "Tempo ring, drag around it to change the speed"
        const val TOLERANCE = 2f
        const val RELATIVE_TOLERANCE = 0.02f

        /** Rounding of the forced density, in dp. */
        val SLACK = 1.dp

        /** What upright's 112 dp column has to spare under its animal, the gap and the biggest bubble. */
        val COLUMN_SPARE = 5.68.dp
    }
}
