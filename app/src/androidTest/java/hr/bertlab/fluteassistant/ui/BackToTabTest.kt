// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import hr.bertlab.fluteassistant.ui.common.BackKey
import hr.bertlab.fluteassistant.ui.tuner.GuideCard
import hr.bertlab.fluteassistant.ui.tuner.GuideExit
import hr.bertlab.fluteassistant.ui.tuner.TunerActions
import hr.bertlab.fluteassistant.ui.tuner.TunerContent
import hr.bertlab.fluteassistant.ui.tuner.listening
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The phone's Back after the Fingers tab's flute opened the tuner goes back to the Fingers tab (2026-11-18, the user: "go
 * back from tuner to fingering when user land on tuner from fingering"). `MainScreen` needs Hilt view models, so this
 * composes its [BackToTab] as `MainScreen` does, before the tab, with the tuner itself after it.
 */
@RunWith(AndroidJUnit4::class)
class BackToTabTest {

    @get:Rule
    val compose = createComposeRule()

    private val back = BackKey()

    /** Where Back went, through the route `MainScreen` keeps: the tuner, opened by a flute of the Fingers tab. */
    private val went = mutableListOf<AppTab>()
    private var route by mutableStateOf(TabRoute(AppTab.FINGERS).openFromHere(AppTab.TUNER))

    private val exits = mutableListOf<GuideExit>()
    private var tuner by mutableStateOf(listening(null, targetMidi = 79))

    private fun show() {
        compose.setContent {
            back.Watch()
            BackToTab(tab = route.back) { tab ->
                went += tab
                route = route.select(tab)
            }
            TunerContent(
                tuner,
                TunerActions(onCloseGuide = { exit ->
                    exits += exit
                    tuner = tuner.copy(guideCard = null)
                }),
            )
        }
    }

    @Test
    fun back_goes_to_the_tab_that_opened_this_one_and_then_is_the_phones() {
        show()
        back.press(compose)
        assertEquals(listOf(AppTab.FINGERS), went)
        assertFalse("once back, Back leaves the app as on every tab", back.taken(compose))
    }

    /** The guide is a panel over the tuner: Back closes it first, as its ✕ does, and only the next Back leaves the tab. */
    @Test
    fun the_tuners_guide_takes_back_before_the_way_back() {
        show()
        compose.runOnIdle { tuner = tuner.copy(guideCard = GuideCard.HEAD_JOINT) }
        back.press(compose)
        assertEquals(listOf(GuideExit.BACK), exits)
        assertTrue("the tuner is still on screen", went.isEmpty())
        back.press(compose)
        assertEquals(listOf(AppTab.FINGERS), went)
    }

    /** A tab chosen on the bar (or the tuner opened from it) has no way back: Back is the phone's. */
    @Test
    fun with_no_way_back_back_is_the_phones() {
        route = TabRoute(AppTab.TUNER)
        show()
        assertFalse(back.taken(compose))
    }
}
