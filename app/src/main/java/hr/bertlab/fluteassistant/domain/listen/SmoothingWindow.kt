// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.listen

import hr.bertlab.fluteassistant.domain.tuner.NoteMath

/**
 * The last [size] frequencies heard, newest last: whether the newest of them agree ([isSteady], a held note as opposed
 * to a syllable, a bump or a voice gliding past) and their [median], so one mis-detected frame does not move the note
 * shown. The tuner's [ReadingTracker] and the flute's [NoteOnsetTracker] each keep one.
 */
internal class SmoothingWindow(private val size: Int = ListenRules.SMOOTHING_WINDOW) {
    private val recent = ArrayDeque<Double>()

    fun add(frequency: Double) {
        recent.addLast(frequency)
        while (recent.size > size) recent.removeFirst()
    }

    fun clear() = recent.clear()

    /** True once the last [frames] frequencies agree within [cents]. */
    fun isSteady(frames: Int = ListenRules.LOCK_FRAMES, cents: Double = ListenRules.LOCK_CENTS): Boolean {
        if (recent.size < frames) return false
        val last = recent.takeLast(frames)
        return NoteMath.centsApart(last.max(), last.min()) <= cents
    }

    /** The middle frequency of the window, the upper of the two middle ones when their count is even. */
    fun median(): Double = recent.sorted()[recent.size / 2]
}
