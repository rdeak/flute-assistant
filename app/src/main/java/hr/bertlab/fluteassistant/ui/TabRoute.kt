// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui

import java.io.Serializable

/**
 * Which tab is [open], and where the phone's Back goes from it: to the tab it was opened from ([back]), or, with none,
 * out of the app as on every tab. One value, so the two cannot fall out of step. Since 2026-11-18 a flute of the
 * Fingers tab opens the tuner, and Back there goes back to the Fingers tab (the user: "go back from tuner to fingering
 * when user land on tuner from fingering") until a tab is chosen on the bar or the rail. `MainScreen` keeps it, saved,
 * as the language switch re-creates the Activity.
 */
data class TabRoute(val open: AppTab = AppTab.entries.first(), val back: AppTab? = null) : Serializable {

    /**
     * A tab chosen on the bar or the rail, or gone back to: the child's own way, so Back is the phone's again. The
     * open tab chosen again changes nothing.
     */
    fun select(tab: AppTab): TabRoute = if (tab == open) this else TabRoute(tab)

    /** [tab] opened by the open tab itself: Back goes back to where it was opened from. */
    fun openFromHere(tab: AppTab): TabRoute = if (tab == open) this else TabRoute(tab, back = open)
}
