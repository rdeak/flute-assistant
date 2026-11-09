// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.audio.metronome

import hr.bertlab.fluteassistant.domain.model.BeatStrength
import hr.bertlab.fluteassistant.domain.model.MetronomeConfig
import kotlin.math.max
import kotlin.math.roundToLong

/** One click, positioned on the sample timeline. */
data class ClickEvent(
    /** Absolute audio frame at which the click starts. */
    val frame: Long,
    val strength: BeatStrength,
    /** 0-based index of the beat within the bar. */
    val beatIndex: Int,
    val barIndex: Long,
)

/**
 * Produces the sample-accurate timeline of clicks for a [MetronomeConfig]: one on every beat.
 *
 * Positions are accumulated as doubles and rounded per event, so there is no cumulative
 * drift even at tempos whose beat length is not a whole number of frames. The config is
 * re-read on every call, so tempo and meter changes take effect without restarting the
 * stream; [retime] additionally lets a tempo change move the click that is already scheduled.
 */
class ClickScheduler(private val sampleRate: Int) {
    private var position = 0.0
    private var beatIndex = 0
    private var barIndex = 0L

    // Where the last emitted click sat: the next one is a beat after it.
    private var lastEventPosition = Double.NaN

    fun reset(startFrame: Long = 0L) {
        position = startFrame.toDouble()
        beatIndex = 0
        barIndex = 0L
        lastEventPosition = Double.NaN
    }

    /** Frame at which the next click (the one [next] will return) is due. */
    val nextFrame: Long get() = position.roundToLong()

    /** Returns the next click and advances the timeline by the beat that follows it. */
    fun next(config: MetronomeConfig): ClickEvent {
        val beatsPerBar = config.timeSignature.beatsPerBar

        // The bar may have got shorter since the previous call: keep the index valid.
        if (beatIndex >= beatsPerBar) {
            beatIndex = 0
            barIndex++
        }

        val event = ClickEvent(position.roundToLong(), config.timeSignature.accents[beatIndex], beatIndex, barIndex)
        lastEventPosition = position

        beatIndex++
        if (beatIndex >= beatsPerBar) {
            beatIndex = 0
            barIndex++
        }
        position += beatFrames(config)
        return event
    }

    /**
     * Re-derives the position of the *next* click from the last emitted one using the tempo in
     * [config], so a tempo change is heard at the very next click instead of one interval later.
     * The result is never earlier than [notBefore] (frames already rendered cannot be revisited).
     * @return the new frame of the next click.
     */
    fun retime(config: MetronomeConfig, notBefore: Long): Long {
        if (!lastEventPosition.isNaN()) {
            position = max(lastEventPosition + beatFrames(config), notBefore.toDouble())
        }
        return position.roundToLong()
    }

    private fun beatFrames(config: MetronomeConfig): Double {
        // Defensive: a corrupt persisted value must never produce a zero or negative interval.
        val bpm = config.bpm.coerceIn(MetronomeConfig.MIN_BPM, MetronomeConfig.MAX_BPM)
        return sampleRate * 60.0 / bpm
    }
}
