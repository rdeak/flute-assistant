// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.settings

/**
 * Which colours the app wears: the phone's own day or night setting until the child taps the
 * top bar's moon or sun, and from then on the one they chose. The tap is the only way in and
 * there is no way back to [SYSTEM]: a two-way switch is all a child needs.
 */
enum class ThemeChoice {
    SYSTEM,
    LIGHT,
    DARK;

    /** Whether the app is dark now, given whether the phone is ([systemDark]). */
    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }
}
