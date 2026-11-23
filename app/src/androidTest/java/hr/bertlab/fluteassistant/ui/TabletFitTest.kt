// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui

import hr.bertlab.fluteassistant.domain.fingers.FingerPick
import hr.bertlab.fluteassistant.ui.fingers.FingersContent
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.domain.flute.NoteLength
import hr.bertlab.fluteassistant.domain.flute.TuneNote
import hr.bertlab.fluteassistant.domain.metronome.MetronomeConfig
import hr.bertlab.fluteassistant.domain.notes.NotesRound
import hr.bertlab.fluteassistant.domain.notes.StaffNotes
import hr.bertlab.fluteassistant.domain.tuner.TunerMode
import hr.bertlab.fluteassistant.ui.tuner.GuideCard
import hr.bertlab.fluteassistant.ui.tuner.TunerActions
import hr.bertlab.fluteassistant.ui.common.assertWhole
import hr.bertlab.fluteassistant.ui.common.LocaleProvider
import hr.bertlab.fluteassistant.ui.common.TabletHost
import hr.bertlab.fluteassistant.ui.common.TabletSize
import hr.bertlab.fluteassistant.ui.flute.FluteActions
import hr.bertlab.fluteassistant.ui.flute.FluteContent
import hr.bertlab.fluteassistant.ui.flute.FluteUiState
import hr.bertlab.fluteassistant.ui.flute.Playing
import hr.bertlab.fluteassistant.ui.metronome.MetronomeContent
import hr.bertlab.fluteassistant.ui.metronome.MetronomeUiState
import hr.bertlab.fluteassistant.ui.notes.NotesActions
import hr.bertlab.fluteassistant.ui.notes.NotesContent
import hr.bertlab.fluteassistant.ui.tuner.TunerContent
import hr.bertlab.fluteassistant.ui.tuner.TunerUiState
import hr.bertlab.fluteassistant.ui.tuner.listening
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Every tab and panel inside the logical areas a tablet lays them out in, which are sizes no phone
 * test has seen, at text scales 1 and 2 and in both languages (spec `2026-11-06-tablet-design.md`).
 * `assertIsDisplayed` does not prove a fit, so every node's unclipped bounds are compared with the
 * room and every text is asked for its own layout.
 */
@RunWith(AndroidJUnit4::class)
class TabletFitTest {

    @get:Rule
    val compose = createComposeRule()

    private var size by mutableStateOf(TabletSize.TEN_UPRIGHT)
    private var textScale by mutableStateOf(1f)
    private var locale by mutableStateOf(Locale.ENGLISH)

    private val tune = listOf(60, 60, 67, 67, 69, 69, 67).map { TuneNote(it, NoteLength.QUARTER) }

    /**
     * Shows [screen], [opens] what it needs open once, and holds it to every size, text scale and
     * language. A panel's own words, [stays], must still be there each time, or the rest of the run
     * would be testing the screen under it.
     */
    private fun assertFitsEverywhere(opens: () -> Unit = {}, @StringRes stays: Int? = null, screen: @Composable () -> Unit) {
        compose.setContent {
            LocaleProvider(locale) {
                TabletHost(size, textScale) {
                    Box(Modifier.fillMaxSize().testTag(ROOM)) { screen() }
                }
            }
        }
        opens()
        for (each in TabletSize.entries) {
            for (scale in listOf(1f, 2f)) {
                for (language in listOf(Locale.ENGLISH, Locale.forLanguageTag("hr"))) {
                    compose.runOnIdle {
                        size = each
                        textScale = scale
                        locale = language
                    }
                    compose.waitForIdle()
                    val case = "$each, text $scale, ${language.language}"
                    if (stays != null) assertStillOpen(case, localized(language, stays))
                    assertFits(case)
                }
            }
        }
    }

    private fun localized(language: Locale, @StringRes id: Int): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(context.resources.configuration).apply { setLocale(language) }
        return context.createConfigurationContext(configuration).getString(id)
    }

    private fun assertStillOpen(case: String, words: String) {
        val found = compose.onAllNodes(hasText(words, substring = true) or hasContentDescription(words, substring = true), useUnmergedTree = true)
        assertTrue("$case: \"$words\" has gone, so the panel has closed", found.fetchSemanticsNodes().isNotEmpty())
    }

    private fun assertFits(case: String) {
        val nodes = compose.onAllNodes(hasAnyAncestor(hasTestTag(ROOM)), useUnmergedTree = true)
        val count = nodes.fetchSemanticsNodes().size
        assertTrue("$case: nothing on screen", count > 0)
        for (index in 0 until count) {
            val node = nodes[index]
            assertInside(case, node)
            if (SemanticsActions.GetTextLayoutResult in node.fetchSemanticsNode().config) assertWhole(case, node, textScale)
        }
    }

    private fun assertInside(case: String, node: SemanticsNodeInteraction) {
        val bounds = node.getUnclippedBoundsInRoot()
        val right = size.logicalWidth.dp + SLACK
        val bottom = size.logicalHeight.dp + SLACK
        val what = describe(node)
        assertTrue("$case: $what starts at ${bounds.left} x ${bounds.top}", bounds.left >= -SLACK && bounds.top >= -SLACK)
        assertTrue("$case: $what ends at ${bounds.right} x ${bounds.bottom}, the room is $right x $bottom", bounds.right <= right && bounds.bottom <= bottom)
    }

    private fun describe(node: SemanticsNodeInteraction) = node.fetchSemanticsNode().config.toString().take(120)

    @Composable
    private fun Flute(state: FluteUiState) {
        FluteContent(
            state = state,
            metronome = MetronomeUiState(config = MetronomeConfig(bpm = 120)),
            actions = FluteActions(),
        )
    }

    @Composable
    private fun Metronome(state: MetronomeUiState) {
        MetronomeContent(state = state, onTogglePlayback = {}, onBpmChange = {}, onTimeSignatureChange = {})
    }

    @Composable
    private fun Tuner(state: TunerUiState) {
        TunerContent(state, TunerActions())
    }

    @Composable
    private fun Notes(state: NotesRound) {
        NotesContent(state = state, actions = NotesActions())
    }

    @Composable
    private fun Fingers(state: FingerPick) {
        FingersContent(state, onLetter = {}, onSign = {}, onTune = {})
    }

    private val asking = NotesRound(practiceNotes = StaffNotes.DEFAULT_PRACTICE, currentMidi = 60)

    @Test
    fun flute_writing_fits() = assertFitsEverywhere { Flute(FluteUiState(tune = tune)) }

    @Test
    fun a_running_tune_fits() = assertFitsEverywhere { Flute(FluteUiState(tune = tune, playing = Playing(0))) }

    @Test
    fun the_speed_panel_fits() = assertFitsEverywhere(
        opens = { compose.onNodeWithContentDescription("Speed: Rabbit, tap to change").performClick() },
        stays = R.string.fewer_beats,
    ) { Flute(FluteUiState(tune = tune)) }

    @Test
    fun the_shelf_fits() = assertFitsEverywhere(
        opens = { compose.onNodeWithContentDescription("Tune: Lollipop, tap to change").performClick() },
        stays = R.string.book_cookie,
    ) { Flute(FluteUiState(tune = tune)) }

    @Test
    fun the_metronome_at_rest_fits() = assertFitsEverywhere { Metronome(MetronomeUiState()) }

    @Test
    fun the_metronome_playing_fits() = assertFitsEverywhere { Metronome(MetronomeUiState(isPlaying = true)) }

    @Test
    fun the_tuner_in_which_fits() = assertFitsEverywhere {
        Tuner(listening(null, mode = TunerMode.IDENTIFY))
    }

    @Test
    fun the_tuner_in_tune_fits() = assertFitsEverywhere {
        Tuner(listening(null))
    }

    @Test
    fun the_tuning_guide_fits() = assertFitsEverywhere(stays = R.string.tune_guide_warm_up_title) {
        // The card's title, as only the upright guide has the "How to tune" heading.
        Tuner(listening(null).copy(guideCard = GuideCard.WARM_UP))
    }

    @Test
    fun the_microphone_card_fits() = assertFitsEverywhere { Tuner(TunerUiState(permissionGranted = false, micBlocked = true)) }

    @Test
    fun the_notes_question_fits() = assertFitsEverywhere { Notes(asking) }

    @Test
    fun the_fingers_tab_fits() = assertFitsEverywhere { Fingers(FingerPick()) }

    @Test
    fun the_fingers_tab_with_the_sign_waiting_fits() = assertFitsEverywhere { Fingers(FingerPick(8, signWaiting = true)) }

    @Test
    fun the_picker_fits() = assertFitsEverywhere(
        opens = { compose.onNodeWithContentDescription("Choose notes").performClick() },
        stays = R.string.choose_notes_title,
    ) { Notes(asking) }

    @Test
    fun the_round_summary_fits() = assertFitsEverywhere {
        Notes(
            asking.copy(
                roundOver = true,
                results = List(NotesRound.ROUND_LENGTH) { true },
                bestStreak = 5,
                roundMillis = 83_000L,
            ),
        )
    }

    private companion object {
        const val ROOM = "the_tab_area"

        val SLACK = 2.dp
    }
}
