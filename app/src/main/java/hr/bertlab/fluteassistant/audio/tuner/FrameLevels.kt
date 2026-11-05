package hr.bertlab.fluteassistant.audio.tuner

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The loudness figures of one analysis window, on the dB-style 0..1 scale of
 * [PitchFrame.level] (-60 dBFS is 0, 0 dBFS is 1). Pure Kotlin, shared by
 * [AudioRecordTunerEngine] and the tests that frame synthetic sound the way it does.
 *
 * [level] is the RMS of the whole window (85 ms at 48 kHz), what the tuner's meter and the
 * gates read. [minLevel] is the quietest [STRETCH] inside the newest hop: a tongued repeat stops
 * the air for 40 to 80 ms, which the window's RMS barely notices (the window is twice as long
 * and the room rings on), but a stretch of 21 ms inside the stop falls to the room's floor.
 * The flute's `NoteOnsetTracker` reads it to hear a note started again on the same pitch.
 */
object FrameLevels {
    /** Samples per block, 10.7 ms at 48 kHz: the step the quietest stretch is searched by. */
    const val BLOCK = 512
    /** Samples per stretch, two blocks (21 ms): long enough for a steady RMS of the lowest flute note, short enough to fit inside a tongued stop. */
    const val STRETCH = 2 * BLOCK

    fun level(window: FloatArray): Float = toLevel(rms(window, 0, window.size))

    /**
     * The lowest level of any [STRETCH] (stepped by [BLOCK]) inside the last [hop] samples of
     * [window]; the window's [level] when the hop holds no whole stretch.
     */
    fun minLevel(window: FloatArray, hop: Int): Float {
        var quietest = Double.MAX_VALUE
        var start = max(0, window.size - hop)
        while (start + STRETCH <= window.size) {
            quietest = min(quietest, rms(window, start, start + STRETCH))
            start += BLOCK
        }
        return if (quietest == Double.MAX_VALUE) level(window) else toLevel(quietest)
    }

    private fun rms(window: FloatArray, from: Int, to: Int): Double {
        var energy = 0.0
        for (i in from until to) energy += window[i].toDouble() * window[i]
        return sqrt(energy / (to - from))
    }

    /** Rough dB-style mapping: -60 dBFS -> 0, 0 dBFS -> 1. */
    private fun toLevel(rms: Double): Float {
        val db = 20.0 * log10(rms + 1e-9)
        return min(1f, max(0f, ((db + 60.0) / 60.0).toFloat()))
    }
}
