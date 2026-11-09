// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.model

/**
 * How loud / which sample a single click should use.
 *
 * STRONG - first beat of the bar (accent sound, highest pitch)
 * MEDIUM - secondary accent in compound meters (e.g. beat 4 of 6/8)
 * WEAK   - any other beat
 */
enum class BeatStrength { STRONG, MEDIUM, WEAK }

private fun simpleMeter(beats: Int): List<BeatStrength> =
    List(beats) { index -> if (index == 0) BeatStrength.STRONG else BeatStrength.WEAK }

/** Compound meter: a strong first beat, then a medium accent at the start of every later group of three. */
private fun compoundMeter(beats: Int): List<BeatStrength> =
    List(beats) { index ->
        when {
            index == 0 -> BeatStrength.STRONG
            index % 3 == 0 -> BeatStrength.MEDIUM
            else -> BeatStrength.WEAK
        }
    }

/**
 * Supported time signatures, in the order the UI offers them: the three a beginner meets
 * first, then the rest. [accents] has exactly [beatsPerBar] entries and describes the
 * strength of every beat in the bar. Persisted by name, so the order is free to change.
 */
enum class TimeSignature(
    val beatsPerBar: Int,
    val noteValue: Int,
    val accents: List<BeatStrength>,
) {
    TWO_FOUR(2, 4, simpleMeter(2)),
    THREE_FOUR(3, 4, simpleMeter(3)),
    FOUR_FOUR(4, 4, simpleMeter(4)),
    FIVE_FOUR(5, 4, simpleMeter(5)),
    THREE_EIGHT(3, 8, simpleMeter(3)),
    SIX_EIGHT(6, 8, compoundMeter(6)),
    /** Grouped 2 + 2 + 3, the usual textbook (and folk-dance) reading. */
    SEVEN_EIGHT(
        7, 8,
        listOf(
            BeatStrength.STRONG, BeatStrength.WEAK,
            BeatStrength.MEDIUM, BeatStrength.WEAK,
            BeatStrength.MEDIUM, BeatStrength.WEAK, BeatStrength.WEAK,
        ),
    ),
    NINE_EIGHT(9, 8, compoundMeter(9)),
    TWELVE_EIGHT(12, 8, compoundMeter(12)),
    TWO_TWO(2, 2, simpleMeter(2));

    val label: String get() = "$beatsPerBar/$noteValue"
}

/** Everything the metronome engine needs to produce a click pattern: one click on every beat. */
data class MetronomeConfig(
    val bpm: Int = DEFAULT_BPM,
    val timeSignature: TimeSignature = TimeSignature.FOUR_FOUR,
) {
    /** Returns a copy with [bpm] clamped to the supported range. */
    fun withBpm(newBpm: Int): MetronomeConfig = copy(bpm = newBpm.coerceIn(MIN_BPM, MAX_BPM))

    companion object {
        const val MIN_BPM = 30
        const val MAX_BPM = 300
        const val DEFAULT_BPM = 120
    }
}
