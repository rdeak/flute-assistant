// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.metronome

import hr.bertlab.fluteassistant.domain.model.MetronomeConfig
import kotlin.math.roundToInt

/**
 * Derives a tempo from the intervals between successive user taps.
 *
 * - Keeps at most [maxTaps] timestamps so the estimate follows the most recent taps.
 * - A pause longer than [resetAfterMillis] starts a brand-new sequence.
 * - The result is clamped to the metronome's supported BPM range.
 *
 * Pure Kotlin: timestamps are supplied by the caller (any monotonic millisecond clock).
 */
class TapTempoCalculator(
    private val maxTaps: Int = 8,
    private val resetAfterMillis: Long = 2_000L,
    private val minBpm: Int = MetronomeConfig.MIN_BPM,
    private val maxBpm: Int = MetronomeConfig.MAX_BPM,
) {
    private val taps = ArrayDeque<Long>()

    val tapCount: Int get() = taps.size

    /**
     * Registers a tap that happened at [timeMillis].
     * @return the estimated BPM, or null while fewer than two taps are in the sequence.
     */
    fun tap(timeMillis: Long): Int? {
        val last = taps.lastOrNull()
        if (last != null && timeMillis - last > resetAfterMillis) taps.clear()

        taps.addLast(timeMillis)
        while (taps.size > maxTaps) taps.removeFirst()
        if (taps.size < 2) return null

        val intervals = taps.size - 1
        val averageIntervalMillis = (taps.last() - taps.first()).toDouble() / intervals
        if (averageIntervalMillis <= 0.0) return null

        return (60_000.0 / averageIntervalMillis).roundToInt().coerceIn(minBpm, maxBpm)
    }

    fun reset() = taps.clear()
}
