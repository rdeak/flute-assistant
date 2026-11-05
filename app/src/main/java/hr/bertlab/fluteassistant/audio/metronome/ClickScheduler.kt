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
    /** 0-based index of the main beat within the bar. */
    val beatIndex: Int,
    /** 0-based index within the beat (0 = the beat itself). */
    val subdivisionIndex: Int,
    val barIndex: Long,
)

/**
 * Produces the sample-accurate timeline of clicks for a [MetronomeConfig].
 *
 * Positions are accumulated as doubles and rounded per event, so there is no cumulative
 * drift even at tempos whose beat length is not a whole number of frames. The config is
 * re-read on every call, so tempo / meter / subdivision changes take effect without
 * restarting the stream; [retime] additionally lets a tempo change move the click that is
 * already scheduled.
 */
class ClickScheduler(private val sampleRate: Int) {
    private var position = 0.0
    private var beatIndex = 0
    private var subdivisionIndex = 0
    private var barIndex = 0L

    // Where the last emitted click sat and what fraction of a beat separates it from the next.
    private var lastEventPosition = Double.NaN
    private var lastSlotFraction = 1.0

    fun reset(startFrame: Long = 0L) {
        position = startFrame.toDouble()
        beatIndex = 0
        subdivisionIndex = 0
        barIndex = 0L
        lastEventPosition = Double.NaN
        lastSlotFraction = 1.0
    }

    /** Frame at which the next click (the one [next] will return) is due. */
    val nextFrame: Long get() = position.roundToLong()

    /** Returns the next click and advances the timeline by the interval that follows it. */
    fun next(config: MetronomeConfig): ClickEvent {
        val offsets = config.subdivision.offsets
        val beatsPerBar = config.timeSignature.beatsPerBar

        // The config may have changed since the previous call: keep the indices valid.
        if (subdivisionIndex >= offsets.size) {
            subdivisionIndex = 0
            beatIndex++
        }
        if (beatIndex >= beatsPerBar) {
            beatIndex = 0
            barIndex++
        }

        val strength = if (subdivisionIndex == 0) {
            config.timeSignature.accents[beatIndex]
        } else {
            BeatStrength.SUBDIVISION
        }
        val event = ClickEvent(position.roundToLong(), strength, beatIndex, subdivisionIndex, barIndex)
        lastEventPosition = position

        // Advance to the next slot.
        val currentOffset = offsets[subdivisionIndex]
        val nextOffset: Double
        if (subdivisionIndex + 1 < offsets.size) {
            subdivisionIndex++
            nextOffset = offsets[subdivisionIndex]
        } else {
            nextOffset = 1.0
            subdivisionIndex = 0
            beatIndex++
            if (beatIndex >= beatsPerBar) {
                beatIndex = 0
                barIndex++
            }
        }
        lastSlotFraction = nextOffset - currentOffset
        position += lastSlotFraction * beatFrames(config)
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
            position = max(lastEventPosition + lastSlotFraction * beatFrames(config), notBefore.toDouble())
        }
        return position.roundToLong()
    }

    private fun beatFrames(config: MetronomeConfig): Double {
        // Defensive: a corrupt persisted value must never produce a zero or negative interval.
        val bpm = config.bpm.coerceIn(MetronomeConfig.MIN_BPM, MetronomeConfig.MAX_BPM)
        return sampleRate * 60.0 / bpm
    }
}
