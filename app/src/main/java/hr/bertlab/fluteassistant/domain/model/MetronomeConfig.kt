// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.model

/**
 * How loud / which sample a single click should use.
 *
 * STRONG      - first beat of the bar (accent sound, highest pitch)
 * MEDIUM      - secondary accent in compound meters (e.g. beat 4 of 6/8)
 * WEAK        - any other main beat
 * SUBDIVISION - an in-between tick (8th / 16th / triplet / shuffle)
 */
enum class BeatStrength { STRONG, MEDIUM, WEAK, SUBDIVISION }

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
 * strength of every main beat in the bar. Persisted by name, so the order is free to change.
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

/**
 * Rhythmic subdivision of one beat. [offsets] are the positions inside the beat
 * (0.0 = on the beat, 0.5 = halfway) at which a click is played. The first offset is
 * always 0.0 so the main beat itself is part of every pattern.
 */
enum class Subdivision(val offsets: List<Double>) {
    NONE(listOf(0.0)),
    EIGHTH(listOf(0.0, 0.5)),
    SIXTEENTH(listOf(0.0, 0.25, 0.5, 0.75)),
    TRIPLET(listOf(0.0, 1.0 / 3.0, 2.0 / 3.0)),
    /** Swing feel: the off-beat 8th is delayed to the last triplet position. */
    SHUFFLE(listOf(0.0, 2.0 / 3.0)),
}

/** Click sound family. Each profile has an accent, beat and subdivision variant. */
enum class SoundProfile { WOODBLOCK, BEEP, BELL }

/** Everything the metronome engine needs to produce a click pattern. */
data class MetronomeConfig(
    val bpm: Int = DEFAULT_BPM,
    val timeSignature: TimeSignature = TimeSignature.FOUR_FOUR,
    val subdivision: Subdivision = Subdivision.NONE,
    val sound: SoundProfile = SoundProfile.WOODBLOCK,
) {
    /** Returns a copy with [bpm] clamped to the supported range. */
    fun withBpm(newBpm: Int): MetronomeConfig = copy(bpm = newBpm.coerceIn(MIN_BPM, MAX_BPM))

    companion object {
        const val MIN_BPM = 30
        const val MAX_BPM = 300
        const val DEFAULT_BPM = 120
    }
}
