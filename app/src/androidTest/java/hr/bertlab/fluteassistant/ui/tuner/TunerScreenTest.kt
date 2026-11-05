// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.tuner

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import hr.bertlab.fluteassistant.domain.tuner.FluteRange
import hr.bertlab.fluteassistant.domain.tuner.NoteMath
import hr.bertlab.fluteassistant.domain.tuner.TunerMode
import hr.bertlab.fluteassistant.ui.common.LocaleProvider
import org.junit.Assert.assertEquals
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

    private fun show(state: TunerUiState, locale: Locale = Locale.ENGLISH) {
        compose.setContent {
            LocaleProvider(locale) {
                TunerContent(
                    state = state,
                    onTargetNoteChange = { chosenNote = it },
                    onModeChange = { chosenMode = it },
                    onPlayNote = { playRequests++ },
                    onRequestPermission = { permissionRequests++ },
                    onOpenSettings = {},
                )
            }
        }
    }

    /** Listening in [mode] with [targetMidi] chosen and [frequency] heard (null for silence). */
    private fun listening(
        frequency: Double?,
        targetMidi: Int = FluteRange.DEFAULT_TARGET,
        mode: TunerMode = TunerMode.TARGET,
    ) = TunerUiState(
        permissionGranted = true,
        isListening = true,
        reading = frequency?.let { NoteMath.frequencyToNote(it) },
        targetMidi = targetMidi,
        mode = mode,
    )

    private fun identifying(frequency: Double?) = listening(frequency, mode = TunerMode.IDENTIFY)

    @Test
    fun identify_mode_names_the_heard_note_without_the_wheel() {
        show(identifying(443.0)) // A4 played 12 cents sharp
        compose.onNodeWithContentDescription("Your sound is A4 but a little high. Go down").assertIsDisplayed()
        compose.onNodeWithText("⬆️ Too high").assertIsDisplayed()
        compose.onNodeWithText("Hear").assertDoesNotExist()
    }

    @Test
    fun identify_mode_keeps_the_ring_through_a_drift() {
        // Sixty cents above A4: the nearest note is A♯4, but the ring has not moved yet.
        show(listening(455.4, mode = TunerMode.IDENTIFY).copy(identifiedMidi = 69))
        compose.onNodeWithContentDescription("Your sound is A4 but a little high. Go down").assertIsDisplayed()
        compose.onNodeWithText("⬆️ Too high").assertIsDisplayed()
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
        compose.onNodeWithText("⬆️ Too high").assertIsDisplayed()
        compose.onNodeWithText("+12 ¢").assertDoesNotExist()
    }

    @Test
    fun measures_against_the_chosen_note() {
        show(listening(440.0, targetMidi = 67)) // a perfect A4, but G4 was chosen
        compose.onNodeWithContentDescription("Your sound is A4, well above G4. Go down").assertIsDisplayed()
        compose.onNodeWithText("⬆️ That's A4").assertIsDisplayed()
        compose.onNodeWithText("👀 Check your fingers").assertIsDisplayed()
    }

    @Test
    fun in_tune_with_the_chosen_note_celebrates() {
        show(listening(440.0, targetMidi = 69))
        compose.onNodeWithText("🎉 Perfect!").assertIsDisplayed()
        compose.onNodeWithContentDescription("Your sound is A4, in its home ring. In tune").assertIsDisplayed()
    }

    @Test
    fun a_different_note_is_named_and_sent_to_the_fingers() {
        show(listening(329.63)) // E4 played, A4 chosen
        compose.onNodeWithText("⬇️ That's E4").assertIsDisplayed()
        compose.onNodeWithContentDescription("Your sound is E4, well below A4. Go up").assertIsDisplayed()
        compose.onNodeWithText("👀 Check your fingers").assertIsDisplayed()
    }

    @Test
    fun the_octave_above_the_chosen_note_asks_for_gentler_air() {
        show(listening(880.0)) // A5 played, A4 chosen: overblown
        compose.onNodeWithText("⬆️ An octave too high").assertIsDisplayed()
        compose.onNodeWithContentDescription("Your sound is A5, well above A4. Go down").assertIsDisplayed()
        compose.onNodeWithText("🍃 Gentler air").assertIsDisplayed()
    }

    @Test
    fun the_octave_below_the_chosen_note_asks_for_faster_air() {
        show(listening(261.63, targetMidi = 72)) // C4 played, C5 chosen: underblown
        compose.onNodeWithText("⬇️ An octave too low").assertIsDisplayed()
        compose.onNodeWithText("💨 Faster air").assertIsDisplayed()
    }

    @Test
    fun a_different_note_is_named_in_croatian_with_its_croatian_name() {
        show(listening(493.88), locale = Locale.forLanguageTag("hr")) // B4 is h1
        compose.onNodeWithText("⬆️ To je h1").assertIsDisplayed()
        compose.onNodeWithText("👀 Provjeri prste").assertIsDisplayed()
    }

    @Test
    fun too_low_advises_rolling_out() {
        show(listening(430.0)) // A4 about 40 cents flat
        compose.onNodeWithText("⬇️ Too low").assertIsDisplayed()
        compose.onNodeWithText("🔄 Roll the flute out a little").assertIsDisplayed()
    }

    @Test
    fun too_high_advises_rolling_in() {
        show(listening(443.0)) // A4 about 12 cents sharp
        compose.onNodeWithText("⬆️ Too high").assertIsDisplayed()
        compose.onNodeWithText("🔄 Roll the flute in a little").assertIsDisplayed()
    }

    @Test
    fun a_note_played_well_flat_is_still_the_chosen_note() {
        show(listening(425.0)) // A4 sixty cents flat: the nearest note is G♯4, the fingers are right
        compose.onNodeWithText("⬇️ Too low").assertIsDisplayed()
        compose.onNodeWithText("🔄 Roll the flute out a little").assertIsDisplayed()
        compose.onNodeWithText("⬇️ That's G♯4").assertDoesNotExist()
        compose.onNodeWithContentDescription("Your sound is A4 but a little low. Go up").assertIsDisplayed()
    }

    @Test
    fun quiet_unclear_sound_asks_to_come_closer() {
        show(listening(null).copy(hearing = Hearing.QUIET_UNCLEAR))
        compose.onNodeWithText("👂 Play A4").assertIsDisplayed()
        compose.onNodeWithText("📱 Come closer to the phone").assertIsDisplayed()
    }

    @Test
    fun loud_unclear_sound_asks_for_a_clear_note() {
        show(identifying(null).copy(hearing = Hearing.LOUD_UNCLEAR))
        compose.onNodeWithText("👂 Play a note").assertIsDisplayed()
        compose.onNodeWithText("🎵 Play one long, clear note").assertIsDisplayed()
    }

    @Test
    fun in_tune_gives_no_advice() {
        show(listening(440.0))
        compose.onNodeWithText("🎉 Perfect!").assertIsDisplayed()
        compose.onNodeWithText("🔄 Roll the flute out a little").assertDoesNotExist()
        compose.onNodeWithText("🔄 Roll the flute in a little").assertDoesNotExist()
        compose.onNodeWithText("👀 Check your fingers").assertDoesNotExist()
    }

    @Test
    fun croatian_advice_uses_the_informal_form() {
        show(listening(430.0), Locale("hr"))
        compose.onNodeWithText("⬇️ Prenisko").assertIsDisplayed()
        compose.onNodeWithText("🔄 Zakreni flautu malo od sebe").assertIsDisplayed()
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
    fun target_mode_offers_to_hear_the_note_and_shows_its_fingering() {
        show(listening(null))
        compose.onNodeWithText("Hear").performClick()
        assertEquals(1, playRequests)
        compose.onNodeWithContentDescription("Fingering for A4").assertExists()
    }

    @Test
    fun identify_mode_shows_the_heard_notes_fingering_and_no_play_button() {
        show(identifying(392.0)) // G4
        compose.onNodeWithText("Hear").assertDoesNotExist()
        compose.onNodeWithContentDescription("Fingering for G4").assertExists()
    }

    @Test
    fun while_the_note_plays_the_status_says_listen() {
        show(listening(null).copy(isPlayingReference = true))
        compose.onNodeWithText("🔊 Listen").assertIsDisplayed()
    }
}
