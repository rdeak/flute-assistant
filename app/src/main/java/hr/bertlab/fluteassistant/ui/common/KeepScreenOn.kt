// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/**
 * Sets [WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON] on the host activity's window while
 * [enabled] is true, so the screen stays on while the metronome plays or the tuner listens.
 */
@Composable
fun KeepScreenOn(enabled: Boolean) {
    val activity = LocalActivity.current
    DisposableEffect(enabled, activity) {
        val window = activity?.window
        if (enabled) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            if (enabled) window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
