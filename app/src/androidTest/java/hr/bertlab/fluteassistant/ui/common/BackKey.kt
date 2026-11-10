// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeTestRule

/**
 * The phone's Back for a screen test. [Watch] goes inside the test's content and remembers the
 * window's back dispatcher; [taken] says whether something on screen would take a Back press, and
 * [press] presses it. A Back that nothing takes closes the Activity (on the phone: the app), which
 * would end the test's own, so [press] refuses one: check [taken] where leaving is what is expected.
 */
class BackKey {
    private var dispatcher: OnBackPressedDispatcher? = null

    @Composable
    fun Watch() {
        dispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current) { "the test's window has no Back" }
            .onBackPressedDispatcher
    }

    fun taken(compose: ComposeTestRule): Boolean = compose.runOnIdle { checkNotNull(dispatcher).hasEnabledCallbacks() }

    fun press(compose: ComposeTestRule) {
        check(taken(compose)) { "nothing on screen takes Back: pressing it would close the Activity" }
        compose.runOnIdle { checkNotNull(dispatcher).onBackPressed() }
        compose.waitForIdle()
    }
}
