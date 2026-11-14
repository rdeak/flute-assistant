// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.tuner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasContentDescriptionExactly
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import hr.bertlab.fluteassistant.domain.tuner.TunerMode
import hr.bertlab.fluteassistant.ui.common.BackKey
import hr.bertlab.fluteassistant.ui.common.LocaleProvider
import hr.bertlab.fluteassistant.ui.common.SidewaysGeometry
import hr.bertlab.fluteassistant.ui.common.SidewaysHost
import hr.bertlab.fluteassistant.ui.common.assertWhole
import hr.bertlab.fluteassistant.ui.common.needsDevice
import hr.bertlab.fluteassistant.ui.theme.FluteAssistantTheme
import hr.bertlab.fluteassistant.ui.theme.FluteTheme
import hr.bertlab.fluteassistant.ui.theme.Sun
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * The guide to tuning the flute (spec `2026-11-14-tuning-guide-design.md`): its four pictures, and
 * the guide in the tuner's place upright and sideways. The pictures are judged by eye on the emulator;
 * here they must draw in every language and theme and say nothing to a screen reader.
 */
@RunWith(AndroidJUnit4::class)
class TuningGuideTest {

    @get:Rule
    val compose = createComposeRule()

    // ---- The pictures ----

    @Test
    fun every_picture_draws_and_says_nothing() {
        var card by mutableStateOf(GuideCard.WARM_UP)
        var locale by mutableStateOf(Locale.ENGLISH)
        var dark by mutableStateOf(false)
        compose.setContent {
            LocaleProvider(locale) {
                FluteAssistantTheme(darkTheme = dark) {
                    GuidePicture(card, Modifier.size(320.dp, 300.dp))
                }
            }
        }
        for (language in listOf(Locale.ENGLISH, Locale("hr"))) {
            for (night in listOf(false, true)) {
                for (each in GuideCard.entries) {
                    compose.runOnIdle {
                        card = each
                        locale = language
                        dark = night
                    }
                    compose.waitForIdle()
                    val case = "$each in $language${if (night) " at night" else ""}"
                    val picture = compose.onNodeWithTag("guide_picture").fetchSemanticsNode()
                    assertTrue("$case: the picture has a size", picture.size.width > 0 && picture.size.height > 0)
                    assertFalse("$case: the picture has a description", SemanticsProperties.ContentDescription in picture.config)
                    assertTrue(
                        "$case: words in the picture reach a screen reader",
                        compose.onAllNodes(hasText("", substring = true)).fetchSemanticsNodes().isEmpty(),
                    )
                }
            }
        }
    }

    /** Card 3's ring writes its note as every other ring in the app does: the octave figure small and raised (C⁵, c²). */
    @Test
    fun the_note_ring_raises_the_octave() {
        var locale by mutableStateOf(Locale.ENGLISH)
        compose.setContent {
            LocaleProvider(locale) {
                FluteAssistantTheme { NoteRing(72, filled = false) }
            }
        }
        for ((language, label) in listOf(Locale.ENGLISH to "C5", Locale("hr") to "c2")) {
            compose.runOnIdle { locale = language }
            compose.waitForIdle()
            val text = compose.onNodeWithText(label).fetchSemanticsNode().config[SemanticsProperties.Text].single()
            val raised = text.spanStyles.filter { it.item.baselineShift != null }.map { text.text.substring(it.start, it.end) }
            assertEquals("$language: the raised part of $label", listOf(label.takeLast(1)), raised)
        }
    }

    /**
     * Card 4's roll ring passes in front of the yellow head joint at the ring's right edge (grid x 89). At
     * night its near-white line all but vanishes on yellow, so the ring would read as passing behind the flute
     * on both sides: something dark must outline it there, in both themes.
     */
    @Test
    fun the_roll_ring_shows_in_front_of_the_head_joint_at_night() {
        // Reads pixels: on the JVM a capture after other tests in the class came back blank.
        needsDevice()
        var dark by mutableStateOf(true)
        compose.setContent {
            FluteAssistantTheme(darkTheme = dark) {
                Box(Modifier.background(FluteTheme.colors.card).testTag("flute")) {
                    FluteSide(Modifier.width(340.dp), headColor = Sun, rollRing = true)
                }
            }
        }
        for (night in listOf(true, false)) {
            compose.runOnIdle { dark = night }
            compose.waitForIdle()
            val pixels = compose.onNodeWithTag("flute").captureToImage().toPixelMap()
            val unit = pixels.width / 340f
            // Inside the tube, clear of its outlines (59 and 81) and of the lip plate's (44 to 82, outlined to 83.5):
            // only the ring is there, its line about 85.5 to 92.5 and whatever outlines it either side.
            val dark = (84..96).sumOf { x ->
                (63..77).count { y -> pixels[(x * unit).toInt(), (y * unit).toInt()].luminance() < 0.2f }
            }
            val probe = (84..96).joinToString { x -> "%.2f".format(pixels[(x * unit).toInt(), (70 * unit).toInt()].luminance()) }
            assertTrue("${if (night) "night" else "day"}: nothing dark outlines the ring on the head joint (${pixels.width} px, row 70: $probe)", dark > 0)
        }
    }

    /** Card 2's pulled-out flute is drawn at the pushed one's scale, so it is longer by its open joint. */
    @Test
    fun the_pulled_flute_is_drawn_at_the_same_scale() {
        val pushed = fluteWidth(jointGap = 0f)
        val pulled = fluteWidth(jointGap = 22f)
        assertEquals(250f, pushed.value, 0.01f)
        assertEquals(250f * 362f / 340f, pulled.value, 0.01f)
    }

    // ---- The guide in the tuner's place ----

    private val cards = mutableListOf<GuideCard>()
    private val exits = mutableListOf<GuideExit>()
    private val back = BackKey()

    private var state by mutableStateOf(guide(GuideCard.WARM_UP))
    private var locale by mutableStateOf(Locale.ENGLISH)
    private var scale by mutableStateOf(1f)
    private var sideways by mutableStateOf(true)
    private var upright by mutableStateOf(DpSize(412.dp, 653.dp))
    private var phone by mutableStateOf(Phone(915, 411))

    private class Phone(val width: Int, val height: Int) {
        val contentWidth: Float get() = width - SidewaysGeometry.railWidth(height.toFloat())
        override fun toString() = "$width x $height"
    }

    private fun guide(card: GuideCard?) =
        TunerUiState(permissionGranted = true, isListening = true, mode = TunerMode.TARGET, guideCard = card)

    @Composable
    private fun Tuner() = TunerContent(state, {}, {}, {}, {}, {}, onGuideCard = { cards += it }, onCloseGuide = { exits += it })

    /** The tuner on a phone on its side, or, with [sideways] false, the upright layout in the same room. */
    private fun showOnItsSide() {
        compose.setContent {
            back.Watch()
            LocaleProvider(locale) {
                SidewaysHost(width = phone.width, height = phone.height, fontScale = scale, sideways = sideways) { Tuner() }
            }
        }
    }

    /** The tuner upright in a tab of [upright]'s size. */
    private fun showUpright() {
        compose.setContent {
            LocaleProvider(locale) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(scale) then DeviceConfigurationOverride.ForcedSize(upright)) { Tuner() }
            }
        }
    }

    private fun set(change: () -> Unit) {
        compose.runOnIdle(change)
        compose.waitForIdle()
    }

    private fun both(check: (String) -> Unit) = listOf(false, true).forEach { turned ->
        set { sideways = turned }
        check(if (turned) "sideways" else "upright")
    }

    @Test
    fun the_guide_takes_the_tuners_place() {
        showOnItsSide()
        both { how ->
            compose.onNodeWithText("🌡️ Warm up first").assertIsDisplayed()
            assertEquals("$how: the status line", 0, compose.onAllNodes(hasText("👂 Play A4")).fetchSemanticsNodes().size)
            assertEquals("$how: How?", 0, compose.onAllNodes(hasText("How?")).fetchSemanticsNodes().size)
        }
    }

    @Test
    fun next_and_back_move_through_the_cards() {
        showOnItsSide()
        both { how ->
            set { state = guide(GuideCard.WARM_UP) }
            compose.onNodeWithContentDescription("Back").assertIsNotEnabled()
            compose.onNodeWithContentDescription("Next").assertIsEnabled().performClick()
            assertEquals(how, GuideCard.HEAD_JOINT, cards.last())
            set { state = guide(GuideCard.EACH_NOTE) }
            compose.onNodeWithContentDescription("Back").assertIsEnabled().performClick()
            assertEquals(how, GuideCard.HEAD_JOINT, cards.last())
        }
    }

    @Test
    fun ok_closes_on_the_last_card() {
        showOnItsSide()
        set { state = guide(GuideCard.ONE_NOTE_OFF) }
        set { sideways = false }
        assertEquals(0, compose.onAllNodes(hasContentDescriptionExactly("Next")).fetchSemanticsNodes().size)
        compose.onNodeWithText("OK").performClick()
        assertEquals(listOf(GuideExit.OK), exits)
        set { sideways = true }
        assertEquals(0, compose.onAllNodes(hasContentDescriptionExactly("Next")).fetchSemanticsNodes().size)
        compose.onNodeWithContentDescription("OK").performClick()
        assertEquals(listOf(GuideExit.OK, GuideExit.OK), exits)
    }

    @Test
    fun close_and_back_close_the_guide() {
        showOnItsSide()
        both { how ->
            exits.clear()
            set { state = guide(GuideCard.HEAD_JOINT) }
            compose.onNodeWithContentDescription("Close").performClick()
            assertEquals(how, listOf(GuideExit.CLOSE), exits)
            back.press(compose)
            assertEquals(how, listOf(GuideExit.CLOSE, GuideExit.BACK), exits)
        }
        set { state = guide(null) }
        assertFalse("with the guide closed, Back is the phone's", back.taken(compose))
    }

    @Test
    fun card_two_names_the_tuning_note_in_each_language() {
        showOnItsSide()
        set { state = guide(GuideCard.HEAD_JOINT) }
        compose.onNodeWithText("🎯 Tune on A4").assertIsDisplayed()
        set { locale = Locale("hr") }
        compose.onNodeWithText("🎯 Ugodi na a1").assertIsDisplayed()
    }

    /** Review Focus 3: turning the phone keeps the card on screen and does not close the guide. */
    @Test
    fun a_turn_keeps_the_card() {
        showOnItsSide()
        set { state = guide(GuideCard.EACH_NOTE) }
        compose.onNodeWithText("👂 Check each note").assertIsDisplayed()
        set { sideways = false }
        compose.onNodeWithText("👂 Check each note").assertIsDisplayed()
        set { sideways = true }
        compose.onNodeWithText("👂 Check each note").assertIsDisplayed()
        assertTrue(exits.isEmpty())
    }

    /** Review Focus 4: the buttons are named in both languages; the picture says nothing. */
    @Test
    fun screen_readers_hear_the_buttons_not_the_pictures() {
        showOnItsSide()
        set { state = guide(GuideCard.EACH_NOTE) }
        mapOf(Locale.ENGLISH to listOf("Back", "Next", "Close"), Locale("hr") to listOf("Natrag", "Dalje", "Zatvori")).forEach { (language, names) ->
            set { locale = language }
            both { how -> names.forEach { compose.onNodeWithContentDescription(it).assertExists("$how in $language: $it") } }
        }
        set {
            locale = Locale.ENGLISH
            sideways = true
            state = guide(GuideCard.ONE_NOTE_OFF)
        }
        compose.onNodeWithContentDescription("OK").assertExists()
        val picture = compose.onNodeWithTag("guide_picture").fetchSemanticsNode()
        assertFalse(SemanticsProperties.ContentDescription in picture.config)
        assertFalse(SemanticsProperties.Text in picture.config)
    }

    // ---- Fit ----

    private fun bounds(node: SemanticsNodeInteraction) = node.getUnclippedBoundsInRoot()

    /** The Stage's 20 dp padding and 3 dp border, each side. */
    private val stageChrome = 46.dp

    /** The guide's button names in [language]: Back, Next, Close, OK. */
    private class Names(val back: String, val next: String, val close: String, val ok: String)

    private fun names(language: Locale) =
        if (language.language == "hr") Names("Natrag", "Dalje", "Zatvori", "U redu") else Names("Back", "Next", "Close", "OK")

    private fun nextOrOk(card: GuideCard, onItsSide: Boolean, language: Locale) = names(language).let { name ->
        when {
            card != GuideCard.ONE_NOTE_OFF -> compose.onNodeWithContentDescription(name.next)
            onItsSide -> compose.onNodeWithContentDescription(name.ok)
            else -> compose.onNodeWithText(name.ok)
        }
    }

    private val languages = listOf(Locale.ENGLISH, Locale("hr"))
    private val scales = listOf(1f, 1.5f, 2f)

    @Test
    fun every_card_fits_upright() {
        showUpright()
        for (room in listOf(DpSize(412.dp, 653.dp), DpSize(360.dp, 653.dp), DpSize(360.dp, 480.dp))) for (language in languages) for (textScale in scales) for (card in GuideCard.entries) {
            set {
                upright = room
                locale = language
                scale = textScale
                state = guide(card)
            }
            val case = "$card at ${room.width} x ${room.height}, $language, ${textScale}x"
            assertWhole(case, compose.onNodeWithTag("guide_title", useUnmergedTree = true), textScale)
            assertWhole(case, compose.onNodeWithTag("guide_words", useUnmergedTree = true), textScale)
            val stage = bounds(compose.onNodeWithTag("guide_stage"))
            val picture = bounds(compose.onNodeWithTag("guide_picture"))
            val inside = stage.bottom - stage.top - stageChrome
            assertTrue("$case: the picture is ${picture.bottom - picture.top} of $inside", picture.bottom - picture.top >= inside * 0.4f - 1.dp)
            val next = bounds(nextOrOk(card, onItsSide = false, language))
            assertTrue("$case: the last button ends at ${next.bottom}", next.bottom <= room.height + 1.dp && next.right <= room.width + 1.dp)
            assertWhole(case, compose.onNodeWithTag("guide_heading", useUnmergedTree = true), textScale)
            if (card == GuideCard.ONE_NOTE_OFF) assertWhole(case, compose.onNodeWithText(names(language).ok, useUnmergedTree = true), textScale)
        }
    }

    @Test
    fun every_card_fits_sideways() {
        showOnItsSide()
        for (each in listOf(Phone(915, 411), Phone(800, 360), Phone(640, 360))) for (language in languages) for (textScale in scales) for (card in GuideCard.entries) {
            set {
                phone = each
                locale = language
                scale = textScale
                state = guide(card)
            }
            val case = "$card on $each, $language, ${textScale}x"
            assertWhole(case, compose.onNodeWithTag("guide_title", useUnmergedTree = true), textScale)
            assertWhole(case, compose.onNodeWithTag("guide_words", useUnmergedTree = true), textScale)
            val stage = bounds(compose.onNodeWithTag("guide_stage"))
            val picture = bounds(compose.onNodeWithTag("guide_picture"))
            val inside = stage.right - stage.left - stageChrome
            assertTrue("$case: the picture is ${picture.right - picture.left} of $inside", picture.right - picture.left >= inside * 0.4f - 1.dp)
            val name = names(language)
            listOf(compose.onNodeWithContentDescription(name.close), compose.onNodeWithContentDescription(name.back), nextOrOk(card, onItsSide = true, language)).forEach { node ->
                val b = bounds(node)
                assertTrue("$case: a button ends at ${b.right} x ${b.bottom}", b.right <= each.contentWidth.dp + 1.dp && b.bottom + 5.dp <= each.height.dp + 1.dp)
            }
        }
    }

    private operator fun Dp.times(share: Float): Dp = (value * share).dp
}
