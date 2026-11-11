// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.tuner

import android.Manifest
import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import hr.bertlab.fluteassistant.fakes.FakeLogger
import hr.bertlab.fluteassistant.fakes.FakeReferenceTonePlayer
import hr.bertlab.fluteassistant.fakes.FakeSettingsStore
import hr.bertlab.fluteassistant.fakes.FakeTunerEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * The stateful [TunerScreen] and the microphone: how often the tab collects the engine's frames
 * (each collection opens the microphone) on each way the permission can already be there or
 * arrive. JVM only: it needs the fakes, which the instrumented build does not see.
 */
@RunWith(AndroidJUnit4::class)
class TunerScreenMicrophoneTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val engine = FakeTunerEngine()
    private val viewModel = TunerViewModel(engine, FakeSettingsStore(), FakeReferenceTonePlayer(), FakeLogger())

    private fun allowMicrophone() =
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(Manifest.permission.RECORD_AUDIO)

    private fun openTab() {
        compose.setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            TunerScreen(state = state, viewModel = viewModel)
        }
        compose.waitForIdle()
    }

    private fun assertListeningAfterOpenings(openings: Int) {
        assertTrue(viewModel.state.value.isListening)
        assertEquals(openings, engine.subscriptions)
    }

    @Test
    fun `the first opening after the app starts opens the microphone once`() {
        allowMicrophone() // allowed on an earlier day; the new view model has not heard of it yet
        openTab()
        assertListeningAfterOpenings(1)
    }

    @Test
    fun `a later opening opens the microphone once`() {
        allowMicrophone()
        viewModel.onPermissionResult(true) // the view model outlives the tab: it knows from the first opening
        openTab()
        assertListeningAfterOpenings(1)
    }

    @Test
    fun `coming back from Settings with the microphone allowed there opens it once`() {
        openTab()
        assertFalse(viewModel.state.value.isListening)
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED) // off to the phone's Settings
        allowMicrophone()
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
        assertListeningAfterOpenings(1)
    }

    @Test
    fun `a yes to the phone's question opens the microphone once`() {
        openTab() // asks the phone, which only pauses the screen
        assertEquals(0, engine.subscriptions)
        allowMicrophone()
        compose.runOnUiThread { viewModel.onPermissionResult(true) } // what the question's answer calls
        compose.waitForIdle()
        assertListeningAfterOpenings(1)
    }

    @Test
    fun `leaving the app closes the microphone and coming back opens it again`() {
        allowMicrophone()
        openTab()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        assertFalse(viewModel.state.value.isListening)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
        assertListeningAfterOpenings(2)
    }
}
