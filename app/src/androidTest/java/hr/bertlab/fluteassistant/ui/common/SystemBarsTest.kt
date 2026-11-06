// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemBarsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var enabled by mutableStateOf(true)
    private var present by mutableStateOf(true)

    private fun show() {
        compose.setContent {
            if (present) HideSystemBars(enabled)
        }
    }

    private fun barsShowing(): Boolean {
        val insets = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
        return insets?.isVisible(WindowInsetsCompat.Type.statusBars()) == true
    }

    private fun waitForBars(showing: Boolean) = compose.waitUntil(BAR_TIMEOUT_MS) { barsShowing() == showing }

    /** What the system does on entering and leaving split screen, which re-creates nothing here. */
    private fun split(on: Boolean) = compose.runOnUiThread {
        compose.activity.onMultiWindowModeChanged(on, compose.activity.resources.configuration)
    }

    @Test
    fun enabled_hides_the_bars() {
        show()
        waitForBars(showing = false)
    }

    @Test
    fun they_come_back_when_it_is_no_longer_enabled() {
        show()
        waitForBars(showing = false)
        enabled = false
        waitForBars(showing = true)
    }

    @Test
    fun they_come_back_when_it_leaves_the_screen() {
        show()
        waitForBars(showing = false)
        present = false
        waitForBars(showing = true)
    }

    @Test
    fun a_split_screen_brings_them_back_and_leaving_it_hides_them_again() {
        show()
        waitForBars(showing = false)
        split(on = true)
        waitForBars(showing = true)
        split(on = false)
        waitForBars(showing = false)
    }

    private companion object {
        const val BAR_TIMEOUT_MS = 5_000L
    }
}
