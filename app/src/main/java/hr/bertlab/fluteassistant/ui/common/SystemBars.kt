// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.core.app.MultiWindowModeChangedInfo
import androidx.core.app.OnMultiWindowModeChangedProvider
import androidx.core.util.Consumer
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Hides the status and navigation bars while [enabled], which gives a phone on its side the height
 * the running staff needs; a swipe from the edge brings them back for a moment. In split screen
 * they stay, as there they are not ours to take. The activity handles that change itself, so the
 * listener is what notices it: `enabled` does not change when a wide pane is dragged to full screen.
 */
@Composable
fun HideSystemBars(enabled: Boolean) {
    val activity = LocalActivity.current
    DisposableEffect(enabled, activity) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        fun update(split: Boolean) {
            if (enabled && !split) {
                controller?.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller?.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        val windowModes = activity as? OnMultiWindowModeChangedProvider
        val onWindowMode = Consumer<MultiWindowModeChangedInfo> { update(it.isInMultiWindowMode) }
        windowModes?.addOnMultiWindowModeChangedListener(onWindowMode)
        update(activity?.isInMultiWindowMode == true)
        onDispose {
            windowModes?.removeOnMultiWindowModeChangedListener(onWindowMode)
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

/**
 * Draws the status and navigation bars' icons for the app's own theme: light icons while [dark],
 * dark ones otherwise. `enableEdgeToEdge` in `onCreate` reads the phone's night setting, which
 * the child's moon or sun can overrule, so this applies it again with the app's answer whenever
 * that changes. The scrims are the library's own defaults, which only a three-button bar below
 * Android 10 ever shows.
 */
@Composable
fun SystemBarsFollowTheme(dark: Boolean) {
    val activity = LocalActivity.current as? ComponentActivity
    DisposableEffect(dark, activity) {
        activity?.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
            navigationBarStyle = SystemBarStyle.auto(LightScrim, DarkScrim) { dark },
        )
        onDispose { }
    }
}

/** `enableEdgeToEdge`'s own navigation bar scrims, which it does not export. */
private val LightScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DarkScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
