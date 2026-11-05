// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.metronome

import androidx.annotation.StringRes
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.domain.model.BeatStrength
import hr.bertlab.fluteassistant.domain.model.MetronomeConfig
import hr.bertlab.fluteassistant.domain.model.Preset
import hr.bertlab.fluteassistant.domain.model.TimeSignature
import kotlin.math.roundToInt

/** One-shot messages the screen shows in a snackbar (mapped to string resources in the UI). */
enum class MetronomeMessage { AUDIO_UNAVAILABLE, SAVE_FAILED, DELETE_FAILED }

/** Immutable snapshot of everything the metronome screen renders. */
data class MetronomeUiState(
    val config: MetronomeConfig = MetronomeConfig(),
    val isPlaying: Boolean = false,
    /** Master volume in [0, 1]. Not part of presets and not on screen (the phone's buttons set loudness). */
    val volume: Float = 0.8f,
    /** Index of the beat currently sounding, or -1 while stopped. */
    val currentBeat: Int = -1,
    val currentStrength: BeatStrength? = null,
    /** Increments on every main beat; the UI keys its flash animation on it. */
    val beatPulse: Long = 0L,
    val presets: List<Preset> = emptyList(),
    /** Taps in the current tap-tempo sequence (not shown by the simplified UI; kept for an advanced mode). */
    val tapCount: Int = 0,
    val userMessage: MetronomeMessage? = null,
)

/**
 * The ring's five speeds, each an animal a child knows to be slow or fast instead of a BPM
 * figure or an Italian marking. A band runs from [fromBpm] to the next band's start; each
 * takes a fifth of the ring however many tempos it spans ([dialFraction]), so the slow
 * tempos a beginner lives in get as much room as the fast ones.
 */
enum class TempoBand(val fromBpm: Int, val emoji: String, @StringRes val labelRes: Int) {
    SNAIL(40, "\uD83D\uDC0C", R.string.band_snail), // snail
    TURTLE(60, "\uD83D\uDC22", R.string.band_turtle), // turtle
    DUCK(80, "\uD83E\uDD86", R.string.band_duck), // duck
    RABBIT(100, "\uD83D\uDC07", R.string.band_rabbit), // rabbit
    CHEETAH(130, "\uD83D\uDC06", R.string.band_cheetah); // leopard

    /** Tempo a tap on the band jumps to: its start, except that a second tap on the rabbit goes on to 120. */
    fun jumpFrom(bpm: Int): Int = if (this == RABBIT && bpm == fromBpm) RABBIT_SECOND_BPM else fromBpm

    companion object {
        /** The ring's range: slower than a snail or faster than a cheetah is not for a child's practice. */
        const val MIN_BPM = 40
        const val MAX_BPM = 200
        private const val RABBIT_SECOND_BPM = 120
    }
}

fun tempoBand(bpm: Int): TempoBand = TempoBand.entries.lastOrNull { bpm >= it.fromBpm } ?: TempoBand.SNAIL

/** Where [bpm] sits along the ring, 0 at the slow end to 1 at the fast end. */
fun dialFraction(bpm: Int): Float {
    val clamped = bpm.coerceIn(TempoBand.MIN_BPM, TempoBand.MAX_BPM)
    val band = tempoBand(clamped)
    val bandEnd = TempoBand.entries.getOrNull(band.ordinal + 1)?.fromBpm ?: TempoBand.MAX_BPM
    val within = (clamped - band.fromBpm).toFloat() / (bandEnd - band.fromBpm)
    return (band.ordinal + within) / TempoBand.entries.size
}

/** The tempo at [fraction] of the way along the ring; the inverse of [dialFraction]. */
fun bpmAtDialFraction(fraction: Float): Int {
    val scaled = fraction.coerceIn(0f, 1f) * TempoBand.entries.size
    val band = TempoBand.entries[scaled.toInt().coerceAtMost(TempoBand.entries.size - 1)]
    val bandEnd = TempoBand.entries.getOrNull(band.ordinal + 1)?.fromBpm ?: TempoBand.MAX_BPM
    return (band.fromBpm + (scaled - band.ordinal) * (bandEnd - band.fromBpm)).roundToInt()
        .coerceIn(TempoBand.MIN_BPM, TempoBand.MAX_BPM)
}

/*
 * The − / + beat buttons step through one bar per beat count, two to six: a child counts
 * bubbles, not fractions, so 3/8 and 2/2 are not offered (they click like 3/4 and 2/4) and
 * six beats are the lilting 6/8. The longer bars stay in the engine for an advanced mode; a
 * stored one still shows, and the buttons step from it onto this ladder.
 */
private val BEAT_LADDER = listOf(
    TimeSignature.TWO_FOUR,
    TimeSignature.THREE_FOUR,
    TimeSignature.FOUR_FOUR,
    TimeSignature.FIVE_FOUR,
    TimeSignature.SIX_EIGHT,
)

/** One beat fewer, or null at two. */
fun TimeSignature.fewerBeats(): TimeSignature? = BEAT_LADDER.lastOrNull { it.beatsPerBar < beatsPerBar }

/** One beat more, or null at six. */
fun TimeSignature.moreBeats(): TimeSignature? = BEAT_LADDER.firstOrNull { it.beatsPerBar > beatsPerBar }
