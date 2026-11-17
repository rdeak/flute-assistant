// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.IntState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.delay

/** How long the screen stays on after the last touch, or after the phone stopped sounding or hearing a note. */
const val SCREEN_AWAKE_MS: Long = 5 * 60 * 1_000L

/**
 * Keeps the phone from dimming and locking while the app is in front (the user's call, 2026-11-17): a child
 * plays with both hands on the flute, often reading the staff from a stand. It holds while [busy] and for
 * [SCREEN_AWAKE_MS] after that ends or after the last of [interactions] (a count that goes up on every touch,
 * key and return to the front), then lets the phone's own timeout take over, so a phone left open still locks
 * itself. The hold only counts while the window shows: in the background the phone sleeps as usual.
 */
@Composable
fun KeepScreenAwake(busy: Boolean, interactions: IntState) {
    val view = LocalView.current
    val interaction = interactions.intValue
    LaunchedEffect(view, busy, interaction) {
        view.keepScreenOn = true
        if (!busy) {
            delay(SCREEN_AWAKE_MS)
            view.keepScreenOn = false
        }
    }
    DisposableEffect(view) {
        onDispose { view.keepScreenOn = false }
    }
}
