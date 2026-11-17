// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeepScreenAwakeTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var busy by mutableStateOf(false)
    private var present by mutableStateOf(true)
    private val interactions = mutableIntStateOf(0)
    private lateinit var view: View

    @Before
    fun show() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            view = LocalView.current
            if (present) KeepScreenAwake(busy = busy, interactions = interactions)
        }
        compose.mainClock.advanceTimeByFrame()
    }

    private fun after(ms: Long) = compose.mainClock.advanceTimeBy(ms)

    /** With the clock held, a change reaches the composition once it is applied and a frame passes. */
    private fun nextFrame() {
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
    }

    private fun touch() {
        interactions.intValue++
        nextFrame()
    }

    private fun assertAwake(message: String) = compose.runOnIdle { assertTrue(message, view.keepScreenOn) }

    private fun assertMaySleep(message: String) = compose.runOnIdle { assertFalse(message, view.keepScreenOn) }

    @Test
    fun the_screen_stays_on_for_five_minutes_after_the_app_opens_then_may_sleep() {
        assertAwake("on as the app opens")
        after(SCREEN_AWAKE_MS - SECOND)
        assertAwake("still on a second before the five minutes")
        after(2 * SECOND)
        assertMaySleep("the phone may sleep once five minutes passed with nothing")
    }

    @Test
    fun a_touch_starts_the_five_minutes_again() {
        after(4 * MINUTE)
        touch()
        after(4 * MINUTE)
        assertAwake("four minutes after the touch, eight after the start")
        after(MINUTE + SECOND)
        assertMaySleep("five minutes after the touch")
    }

    @Test
    fun a_touch_after_the_screen_was_let_go_holds_it_on_again() {
        after(SCREEN_AWAKE_MS + SECOND)
        assertMaySleep("let go after five minutes")
        touch()
        assertAwake("held again at the touch")
    }

    @Test
    fun while_busy_it_stays_on_and_the_five_minutes_start_when_that_ends() {
        busy = true
        nextFrame()
        after(20 * MINUTE)
        assertAwake("on through twenty minutes of sound with no touch")
        busy = false
        nextFrame()
        after(4 * MINUTE)
        assertAwake("four minutes after the sound ended")
        after(MINUTE + SECOND)
        assertMaySleep("five minutes after the sound ended")
    }

    @Test
    fun leaving_the_screen_lets_it_go() {
        present = false
        nextFrame()
        assertMaySleep("let go when it leaves the composition")
    }

    private companion object {
        const val SECOND = 1_000L
        const val MINUTE = 60 * SECOND
    }
}
