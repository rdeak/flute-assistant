// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.tuner

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasNoClickAction
import hr.bertlab.fluteassistant.ui.common.needsDevice
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import hr.bertlab.fluteassistant.ui.common.assertWhole
import hr.bertlab.fluteassistant.ui.common.isNear
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import hr.bertlab.fluteassistant.domain.listen.Hearing
import hr.bertlab.fluteassistant.domain.tuner.FluteRange
import hr.bertlab.fluteassistant.domain.tuner.NoteMath
import hr.bertlab.fluteassistant.domain.tuner.TunerMode
import hr.bertlab.fluteassistant.domain.listen.TunerAdvice
import hr.bertlab.fluteassistant.ui.common.A55Tab
import hr.bertlab.fluteassistant.ui.common.LocaleProvider
import hr.bertlab.fluteassistant.ui.common.UprightHost
import hr.bertlab.fluteassistant.ui.theme.NoteLetterColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class TunerScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private var permissionRequests = 0
    private var chosenNote: Int? = null
    private var chosenMode: TunerMode? = null
    private var playRequests = 0
    private val opened = mutableListOf<GuideCard>()
    private val actions = TunerActions(
        onTargetNoteChange = { chosenNote = it },
        onModeChange = { chosenMode = it },
        onPlayNote = { playRequests++ },
        onRequestPermission = { permissionRequests++ },
        onOpenGuide = { opened += it },
    )

    /** What is on the screen: a test sets these after [show] to move the tuner on. */
    private var state by mutableStateOf(TunerUiState())
    private var locale by mutableStateOf(Locale.ENGLISH)
    private var fontScale by mutableStateOf(1f)

    /** The tuner showing [state] in [locale]: on a tab of [size] when one is given, else on the emulator's own. */
    private fun show(state: TunerUiState, locale: Locale = Locale.ENGLISH, size: DpSize? = null) {
        this.state = state
        this.locale = locale
        compose.setContent {
            LocaleProvider(this.locale) {
                if (size == null) {
                    TunerContent(this.state, actions)
                } else {
                    UprightHost(size, fontScale) { TunerContent(this.state, actions) }
                }
            }
        }
    }

    private fun identifying(frequency: Double?) = listening(frequency, mode = TunerMode.IDENTIFY)

    @Test
    fun the_card_asks_without_a_way_out_of_the_app() {
        show(TunerUiState(permissionGranted = false))
        compose.onNodeWithText("Can I listen?").assertIsDisplayed()
        compose.onNodeWithText("Yes").assertIsDisplayed()
        compose.onNodeWithText("Settings").assertDoesNotExist()
    }

    @Test
    fun the_card_offers_settings_once_the_phone_will_not_ask_again() {
        show(TunerUiState(permissionGranted = false, micBlocked = true))
        compose.onNodeWithText("Yes").assertIsDisplayed()
        compose.onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    fun a_microphone_failure_says_so_in_plain_words() {
        show(listening(null).copy(isListening = false, micFailed = true))
        compose.onNodeWithText("I can't listen right now.").assertIsDisplayed()
        // The one line (2026-11-18): the failure takes it, so no prompt stands beside it (Review Focus 5).
        compose.onNodeWithText("👂 Play A4").assertDoesNotExist()
    }

    /** The failure takes the one line in its own box, so the window and the flute stay where they stood (Review Focus 5). */
    @Test
    fun a_microphone_failure_moves_nothing() {
        show(listening(null), size = A55Tab)
        val window = compose.onNodeWithContentDescription("Home ring for A4, nothing heard yet").getUnclippedBoundsInRoot()
        val flute = compose.onNodeWithContentDescription("Fingering for A4").getUnclippedBoundsInRoot()
        val line = compose.onNodeWithText("👂 Play A4").getUnclippedBoundsInRoot()
        compose.runOnIdle { state = state.copy(isListening = false, micFailed = true) }
        assertEquals("the window", window, compose.onNodeWithContentDescription("Home ring for A4, nothing heard yet").getUnclippedBoundsInRoot())
        assertEquals("the flute", flute, compose.onNodeWithContentDescription("Fingering for A4").getUnclippedBoundsInRoot())
        assertEquals("the line's box", line, compose.onNodeWithText("I can't listen right now.").getUnclippedBoundsInRoot())
    }

    @Test
    fun a_microphone_failure_speaks_the_childs_language() {
        show(listening(null).copy(isListening = false, micFailed = true), locale = Locale.forLanguageTag("hr"))
        compose.onNodeWithText("Sada ne mogu slušati.").assertIsDisplayed()
    }

    @Test
    fun identify_mode_names_the_heard_note_without_the_wheel() {
        show(identifying(443.0)) // A4 played 12 cents sharp
        compose.onNodeWithContentDescription("Your sound is A4 but a little high. Go down").assertIsDisplayed()
        compose.onNodeWithText("🔄 Roll the flute in a little").assertIsDisplayed()
        compose.onNodeWithText("⬇️ A little lower").assertDoesNotExist()
    }

    @Test
    fun identify_mode_keeps_the_ring_through_a_drift() {
        // Sixty cents above A4: the nearest note is B♭4, but the ring has not moved yet.
        show(listening(455.4, mode = TunerMode.IDENTIFY).copy(identifiedMidi = 69))
        compose.onNodeWithContentDescription("Your sound is A4 but a little high. Go down").assertIsDisplayed()
        compose.onNodeWithText("🔄 Roll the flute in a little").assertIsDisplayed()
        compose.onNodeWithContentDescription("Fingering for A4").assertExists()
    }

    @Test
    fun identify_mode_measures_from_the_nearest_note() {
        show(listening(440.0, targetMidi = 67, mode = TunerMode.IDENTIFY)) // the chosen G4 is ignored
        compose.onNodeWithText("🎉 Perfect!").assertIsDisplayed()
    }

    @Test
    fun identify_mode_silence_asks_for_any_note() {
        show(identifying(null))
        compose.onNodeWithContentDescription("Listening, no note heard yet").assertIsDisplayed()
        compose.onNodeWithText("👂 Play a note").assertIsDisplayed()
    }

    @Test
    fun tapping_a_mode_pill_reports_the_mode() {
        show(identifying(null))
        compose.onNodeWithText("Tune").performClick()
        assertEquals(TunerMode.TARGET, chosenMode)
    }

    @Test
    fun croatian_identify_mode_uses_the_school_names() {
        show(identifying(493.88), Locale("hr")) // B4 is h1 in Croatia
        compose.onNodeWithContentDescription("Tvoj ton je h1, u svojoj kućici. Savršeno").assertIsDisplayed()
        compose.onNodeWithText("Koji?").assertIsDisplayed()
    }

    @Test
    fun asks_for_the_microphone_until_permission_is_granted() {
        show(TunerUiState(permissionGranted = false))
        compose.onNodeWithText("Can I listen?").assertIsDisplayed()
        compose.onNodeWithText("Tune").assertDoesNotExist()
        compose.onNodeWithText("Yes").performClick()
        assertEquals(1, permissionRequests)
    }

    @Test
    fun shows_the_target_note_alone_and_too_high() {
        show(listening(443.0)) // A4 chosen and played 12 cents sharp
        compose.onNodeWithContentDescription("Your sound is A4 but a little high. Go down").assertIsDisplayed()
        compose.onNodeWithText("🔧 Pull the head joint out a little").assertIsDisplayed()
    }

    @Test
    fun measures_against_the_chosen_note() {
        show(listening(440.0, targetMidi = 67)) // a perfect A4, but G4 was chosen
        compose.onNodeWithContentDescription("Your sound is A4, well above G4. Go down").assertIsDisplayed()
        compose.onNodeWithText("👀 Check your fingers").assertIsDisplayed()
    }

    @Test
    fun in_tune_with_the_chosen_note_celebrates() {
        show(listening(440.0, targetMidi = 69))
        compose.onNodeWithText("🎉 Perfect!").assertIsDisplayed()
        compose.onNodeWithContentDescription("Your sound is A4, in its home ring. In tune").assertIsDisplayed()
    }

    @Test
    fun a_different_note_shows_in_the_window_and_sends_to_the_fingers() {
        show(listening(329.63)) // E4 played, A4 chosen: the window names E4, the one line says what to do
        compose.onNodeWithContentDescription("Your sound is E4, well below A4. Go up").assertIsDisplayed()
        compose.onNodeWithText("👀 Check your fingers").assertIsDisplayed()
    }

    @Test
    fun the_octave_above_the_chosen_note_asks_for_gentler_air() {
        show(listening(880.0)) // A5 played, A4 chosen: overblown
        compose.onNodeWithContentDescription("Your sound is A5, well above A4. Go down").assertIsDisplayed()
        compose.onNodeWithText("🍃 Gentler air").assertIsDisplayed()
    }

    @Test
    fun the_octave_below_the_chosen_note_asks_for_faster_air() {
        show(listening(261.63, targetMidi = 72)) // C4 played, C5 chosen: underblown
        compose.onNodeWithText("💨 Faster air").assertIsDisplayed()
    }

    @Test
    fun a_different_note_is_named_in_croatian_with_its_croatian_name() {
        show(listening(493.88), locale = Locale.forLanguageTag("hr")) // B4 is h1
        compose.onNodeWithContentDescription("Tvoj ton je h1, puno viši od a1. Idi dolje").assertIsDisplayed()
        compose.onNodeWithText("👀 Provjeri prste").assertIsDisplayed()
    }

    @Test
    fun too_low_on_the_tuning_note_advises_pushing_the_head_joint_in() {
        show(listening(430.0)) // A4 about 40 cents flat
        compose.onNodeWithText("🔧 Push the head joint in a little").assertIsDisplayed()
    }

    @Test
    fun too_high_on_the_tuning_note_advises_pulling_the_head_joint_out() {
        show(listening(443.0)) // A4 about 12 cents sharp
        compose.onNodeWithText("🔧 Pull the head joint out a little").assertIsDisplayed()
    }

    @Test
    fun too_low_on_another_note_still_advises_rolling_out() {
        show(listening(NoteMath.midiToFrequency(72) * 0.985, targetMidi = 72)) // C5 about 26 cents flat
        compose.onNodeWithText("🔄 Roll the flute out a little").assertIsDisplayed()
    }

    @Test
    fun a_note_played_well_flat_is_still_the_chosen_note() {
        show(listening(425.0)) // A4 sixty cents flat: the nearest note is G♯4, the fingers are right
        compose.onNodeWithText("🔧 Push the head joint in a little").assertIsDisplayed()
        compose.onNodeWithContentDescription("Your sound is A4 but a little low. Go up").assertIsDisplayed()
    }

    /**
     * The head joint's lines are longer than the rolling ones they replace on A4: on a narrow phone, at
     * normal, large and the largest text, in both languages, they shrink rather than get cut (Review Focus 1). Since
     * 2026-11-18 they are the one line's words, in its headline look, so its floor is in dp.
     */
    @Test
    fun the_head_joint_advice_stays_whole_on_a_narrow_phone() {
        show(listening(430.0), size = DpSize(360.dp, 653.dp))
        val lines = mapOf(
            Locale.ENGLISH to listOf("🔧 Push the head joint in a little", "🔧 Pull the head joint out a little"),
            Locale("hr") to listOf("🔧 Gurni glavu flaute malo unutra", "🔧 Izvuci glavu flaute malo van"),
        )
        for ((language, texts) in lines) {
            for (scale in listOf(1f, 1.5f, 2f)) {
                listOf(430.0, 443.0).zip(texts).forEach { (frequency, text) ->
                    compose.runOnIdle {
                        locale = language
                        fontScale = scale
                        state = listening(frequency)
                    }
                    compose.waitForIdle()
                    assertWhole("$language at $scale", compose.onNodeWithText(text).assertIsDisplayed(), scale)
                }
            }
        }
    }

    @Test
    fun quiet_unclear_sound_asks_to_come_closer() {
        show(listening(null).copy(hearing = Hearing.QUIET_UNCLEAR))
        compose.onNodeWithText("📱 Come closer to the phone").assertIsDisplayed()
        compose.onNodeWithText("👂 Play A4").assertDoesNotExist()
    }

    @Test
    fun loud_unclear_sound_asks_for_a_clear_note() {
        show(identifying(null).copy(hearing = Hearing.LOUD_UNCLEAR))
        compose.onNodeWithText("🎵 Play one long, clear note").assertIsDisplayed()
    }

    @Test
    fun croatian_advice_uses_the_informal_form() {
        show(listening(430.0), Locale("hr"))
        compose.onNodeWithText("🔧 Gurni glavu flaute malo unutra").assertIsDisplayed()
    }

    @Test
    fun silence_names_the_note_to_play() {
        show(listening(null))
        compose.onNodeWithText("👂 Play A4").assertIsDisplayed()
        compose.onNodeWithContentDescription("Home ring for A4, nothing heard yet").assertIsDisplayed()
    }

    @Test
    fun the_arrows_move_the_target_a_half_step() {
        show(listening(null, targetMidi = 69))
        compose.onNodeWithContentDescription("Higher note").performClick()
        assertEquals(70, chosenNote)
        compose.onNodeWithContentDescription("Lower note").performClick()
        assertEquals(68, chosenNote)
    }

    @Test
    fun the_arrows_stop_at_the_ends_of_the_flute() {
        show(listening(null, targetMidi = FluteRange.LOWEST_MIDI))
        compose.onNodeWithContentDescription("Lower note").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Higher note").assertIsEnabled()
    }

    @Test
    fun croatian_names_the_target_note_the_school_way() {
        show(listening(493.88, targetMidi = 71), Locale("hr")) // B4, called h1 in Croatia
        compose.onNodeWithContentDescription("Tvoj ton je h1, u svojoj kućici. Savršeno").assertIsDisplayed()
        compose.onNodeWithText("🎉 Savršeno!").assertIsDisplayed()
    }

    @Test
    fun croatian_silence_names_the_note_to_play() {
        show(listening(null, targetMidi = 69), Locale("hr"))
        compose.onNodeWithText("👂 Odsviraj a1").assertIsDisplayed()
    }

    @Test
    fun target_mode_shows_the_chosen_notes_fingering() {
        show(listening(null))
        compose.onNodeWithContentDescription("Fingering for A4").assertExists()
    }

    @Test
    fun identify_mode_shows_the_heard_notes_fingering_and_its_window_is_no_button() {
        show(identifying(392.0)) // G4
        compose.onNodeWithContentDescription("Fingering for G4").assertExists()
        compose.onNodeWithContentDescription("Your sound is G4", substring = true).assert(hasNoClickAction())
    }

    @Test
    fun while_the_note_plays_the_status_says_listen() {
        show(listening(null).copy(isPlayingReference = true))
        compose.onNodeWithText("🔊 Listen").assertIsDisplayed()
    }

    // ---- The calmer Tune view (2026-11-18): no Hear or How? buttons, one line, taps on what is there ----

    @Test
    fun there_are_no_hear_or_how_buttons() {
        show(listening(null))
        listOf("Hear", "How?").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
    }

    /**
     * Hear is a tap on the window, in Tune mode, only in silence: not while the note plays (Review Focus 1), nor while
     * the child plays, so a finger on the bubble cannot start the note and blank the reading (the user, 2026-11-18).
     */
    @Test
    fun a_tap_on_the_window_plays_the_chosen_note() {
        show(listening(null))
        compose.onNodeWithContentDescription("Home ring for A4, nothing heard yet")
            .assert(hasClickAction())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        assertEquals(1, playRequests)
        compose.runOnIdle { state = state.copy(isPlayingReference = true) }
        compose.onNodeWithContentDescription("Home ring for A4, nothing heard yet").assert(hasNoClickAction())
        compose.runOnIdle { state = listening(430.0) }
        compose.onNodeWithContentDescription("Your sound is A4 but a little low. Go up").assert(hasNoClickAction())
        compose.runOnIdle { state = listening(392.0) }
        compose.onNodeWithContentDescription("Your sound is G4", substring = true).assert(hasNoClickAction())
        compose.runOnIdle { state = listening(null) }
        compose.onNodeWithContentDescription("Home ring for A4, nothing heard yet").assert(hasClickAction())
        compose.runOnIdle { state = identifying(null) }
        compose.onNodeWithContentDescription("Listening, no note heard yet").assert(hasNoClickAction())
    }

    @Test
    fun the_head_joint_line_opens_the_guide_on_its_card() {
        show(listening(430.0))
        compose.onNodeWithText("🔧 Push the head joint in a little").performClick()
        assertEquals(listOf(GuideCard.HEAD_JOINT), opened)
    }

    /** A screen reader hears the line's action as words, with no emoji to read out ("wrench", the final review). */
    @Test
    fun the_line_says_how_to_tune_to_a_screen_reader_in_words() {
        show(listening(430.0))
        compose.onNodeWithText("🔧 Push the head joint in a little")
            .assert(SemanticsMatcher("its action is named How to tune") { it.config[SemanticsActions.OnClick].label == "How to tune" })
        compose.runOnIdle { locale = Locale("hr") }
        compose.onNodeWithText("🔧 Gurni glavu flaute malo unutra")
            .assert(SemanticsMatcher("its action is named Kako ugoditi flautu") { it.config[SemanticsActions.OnClick].label == "Kako ugoditi flautu" })
    }

    @Test
    fun a_roll_line_opens_the_guide_on_one_note_off() {
        show(listening(NoteMath.midiToFrequency(72) * 0.985, targetMidi = 72)) // C5 flat
        compose.onNodeWithText("🔄 Roll the flute out a little").performClick()
        assertEquals(listOf(GuideCard.ONE_NOTE_OFF), opened)
    }

    /**
     * A child who reads "🔧 Push the head joint in a little" stops playing to free a hand; the line keeps its words and
     * its tap a while, so the guide can still be opened (the final review of 2026-11-18). How long is `FixHold`'s rule
     * and the view model's wiring, both tested on the JVM; here, a held fix is the line and opens the guide.
     */
    @Test
    fun a_fix_held_after_the_sound_stops_is_still_the_line_and_opens_the_guide() {
        show(listening(null).copy(heldFix = TunerAdvice.HEAD_JOINT_IN))
        compose.onNodeWithText("🔧 Push the head joint in a little").assertIsDisplayed().performClick()
        assertEquals(listOf(GuideCard.HEAD_JOINT), opened)
        compose.onNodeWithText("👂 Play A4").assertDoesNotExist()
    }

    /** A line that names no fix on the flute opens nothing (Review Focus 2). */
    @Test
    fun a_line_without_a_fix_on_the_flute_is_not_a_button() {
        show(listening(392.0)) // G4 for A4
        compose.onNodeWithText("👀 Check your fingers").assert(hasNoClickAction())
        compose.runOnIdle { state = listening(null) }
        compose.onNodeWithText("👂 Play A4").assert(hasNoClickAction())
        compose.runOnIdle { state = listening(440.0) }
        compose.onNodeWithText("🎉 Perfect!").assert(hasNoClickAction())
    }

    /** Which? keeps the arrows' room, so the line and the window stay put when the mode changes (Review Focus 3). */
    @Test
    fun the_line_and_the_window_keep_their_places_when_the_mode_changes() {
        show(listening(null), size = A55Tab)
        val line = compose.onNodeWithText("👂 Play A4").getUnclippedBoundsInRoot()
        val window = compose.onNodeWithContentDescription("Home ring for A4, nothing heard yet").getUnclippedBoundsInRoot()
        compose.runOnIdle { state = identifying(null) }
        val ask = compose.onNodeWithText("👂 Play a note").getUnclippedBoundsInRoot()
        val listening = compose.onNodeWithContentDescription("Listening, no note heard yet").getUnclippedBoundsInRoot()
        assertEquals("the line's top", line.top.value, ask.top.value, 1f)
        assertEquals("the window's top", window.top.value, listening.top.value, 1f)
        assertEquals("the window's left", window.left.value, listening.left.value, 1f)
        assertEquals("the window's height", (window.bottom - window.top).value, (listening.bottom - listening.top).value, 1f)
    }

    /**
     * The flute is in the note's colour while it helps and grey while the right note sounds (the rule is
     * `TunerUiState.fingeringLit`, tested on the JVM; this is its wiring, read from the pixels).
     */
    @Test
    fun the_flute_is_grey_while_the_right_note_sounds() {
        needsDevice()
        show(listening(null), size = A55Tab)
        val noteFill = NoteLetterColors[5] // A's
        fun lit(): Int {
            compose.mainClock.advanceTimeBy(1_000)
            val image = compose.onNodeWithContentDescription("Fingering for A4").captureToImage().toPixelMap()
            var count = 0
            for (x in 0 until image.width) for (y in 0 until image.height) if (image[x, y].isNear(noteFill)) count++
            return count
        }
        assertTrue("silence: lit", lit() > 50)
        compose.runOnIdle { state = listening(430.0) }
        assertEquals("A4 flat: grey", 0, lit())
        compose.runOnIdle { state = listening(392.0) }
        assertTrue("G4 for A4: lit", lit() > 50)
    }

    /**
     * Tune mode at [size], a flat A4 heard: the one line over the card, and in the card the window, at least its 120 dp,
     * over the flute and standing on it nowhere. What a short tab squeezes is the window, down to its least, under which
     * it would stand on the flute: the tab scrolls before that (`TunerMinHeightLessFingering` and the flute's own height).
     */
    private fun assertTheWindowClearOfTheFlute(size: DpSize) {
        show(listening(430.0), size = size)
        val line = compose.onNodeWithText("🔧 Push the head joint in a little").getUnclippedBoundsInRoot()
        val card = compose.onNodeWithTag(TUNER_STAGE_TAG).getUnclippedBoundsInRoot()
        val window = compose.onNodeWithContentDescription("Your sound is A4", substring = true).getUnclippedBoundsInRoot()
        val fingering = compose.onNodeWithContentDescription("Fingering for A4").getUnclippedBoundsInRoot()
        assertTrue("the line ends at ${line.bottom}, the card starts at ${card.top}", line.bottom <= card.top)
        assertTrue("the card starts at ${card.top}, the window at ${window.top}", card.top <= window.top)
        assertTrue("the window is ${window.bottom - window.top} tall", window.bottom - window.top >= 120.dp - 0.5.dp)
        assertTrue("the window ends at ${window.bottom}, the fingering starts at ${fingering.top}", window.bottom <= fingering.top + 1.dp)
    }

    /** A tab a little shorter than the tuner needs (395 dp on a 411 dp phone) scrolls, so the window keeps its least. */
    @Test
    fun on_a_tab_just_short_of_the_tuner_the_window_keeps_its_least() {
        assertTheWindowClearOfTheFlute(DpSize(411.dp, 390.dp))
        compose.onAllNodes(hasScrollAction()).assertCountEquals(1)
    }

    /**
     * The fingering's height follows the stage's width, so the least height does too (the review of 2026-11-17: it was
     * one number, right only for a 412 dp phone). A wider and short tab, a pane 590 dp wide whose flute is 100 dp tall,
     * scrolls at a height a 412 dp phone still fits (it needs 430 dp).
     */
    @Test
    fun on_a_wide_tab_just_short_of_the_tuner_the_window_keeps_its_least() {
        assertTheWindowClearOfTheFlute(DpSize(590.dp, 425.dp))
        compose.onAllNodes(hasScrollAction()).assertCountEquals(1)
        val fingering = compose.onNodeWithContentDescription("Fingering for A4").getUnclippedBoundsInRoot()
        assertEquals("the flute fills the stage and is 340:66", 100.5f, (fingering.bottom - fingering.top).value, 1.5f)
    }

    /** A narrow phone's flute is lower, so its tab fits at a height where a 411 dp phone's scrolls: nothing scrolls at 360 x 390. */
    @Test
    fun a_narrow_tab_fits_where_a_wider_one_scrolls() {
        assertTheWindowClearOfTheFlute(DpSize(360.dp, 390.dp))
        compose.onAllNodes(hasScrollAction()).assertCountEquals(0)
    }

    /**
     * On the user's phone (the A55's 411 x 653 dp tab) a flat A4's window stood on the flute (2026-11-18: "elements are
     * figthing for attention and overlapping"): it now ends clear of it.
     */
    @Test
    fun on_the_users_phone_the_window_stands_clear_of_the_flute() {
        show(listening(430.0), size = A55Tab)
        val window = compose.onNodeWithContentDescription("Your sound is A4", substring = true).getUnclippedBoundsInRoot()
        val fingering = compose.onNodeWithContentDescription("Fingering for A4").getUnclippedBoundsInRoot()
        assertTrue("the window ends at ${window.bottom}, the flute starts at ${fingering.top}", window.bottom + 8.dp <= fingering.top)
    }

    /**
     * From the top: the mode pills, the one line ("👂 Odsviraj a1", on top since 2026-11-17: "move ... label to top so it
     * does not overlap with flute"), then the card with the window over the flute (2026-11-18: one order on every tab, and
     * no Hear or How? buttons).
     */
    @Test
    fun the_line_stands_over_the_card_and_the_flute_under_the_window() {
        show(listening(null), Locale("hr"), size = A55Tab)
        val pill = compose.onNodeWithText("Ugodi").getUnclippedBoundsInRoot()
        val prompt = compose.onNodeWithText("👂 Odsviraj a1").getUnclippedBoundsInRoot()
        val card = compose.onNodeWithTag(TUNER_STAGE_TAG).getUnclippedBoundsInRoot()
        val window = compose.onNodeWithContentDescription("Kućica za a1, još ništa ne čujem").getUnclippedBoundsInRoot()
        val flute = compose.onNodeWithContentDescription("Hvat za a1").getUnclippedBoundsInRoot()
        assertTrue("the pill ends at ${pill.bottom}, the prompt starts at ${prompt.top}", pill.bottom <= prompt.top)
        assertTrue("the prompt ends at ${prompt.bottom}, the card starts at ${card.top}", prompt.bottom <= card.top)
        assertTrue("the card starts at ${card.top}, the window at ${window.top}", card.top <= window.top)
        assertTrue("the window ends at ${window.bottom}, the flute starts at ${flute.top}", window.bottom <= flute.top)
        assertTrue("the flute ends at ${flute.bottom}, the card at ${card.bottom}", flute.bottom <= card.bottom)
    }
}
