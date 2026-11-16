// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.flute

import hr.bertlab.fluteassistant.audio.tuner.AudioRecordTunerEngine
import hr.bertlab.fluteassistant.domain.tuner.NoteMath
import java.util.Random
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * A child's flute in a room, synthesised for the JVM: three B4s with tongued or breath-sized
 * gaps, a reverberant room and breath-like noise, at [RATE], as the microphone would deliver it.
 * [ListenPathTest] runs the listening path over it; the session replay tests record it as a
 * session and replay that.
 */
object FluteSound {

    const val RATE = 48_000
    private val rate = RATE
    private val hop = AudioRecordTunerEngine.HOP_SIZE

    /** The engine's samples: the synthesis as floats. */
    fun render(samples: DoubleArray): FloatArray = FloatArray(samples.size) { samples[it].toFloat() }

    /**
     * Three B4s (MIDI 71), the first two 400 ms, the third 1500 ms, [gapsMs] of silence between
     * them, in a room: a tone at [toneDb], a reverberant copy [roomDb] under it decaying over
     * [rt60] seconds, and breath-like noise at [floorDb] (RMS), louder at [pauseDb] in the gaps
     * when given (a noisy inhale). The second note is at [secondDb] when given. Every note
     * wobbles by [wobbleDb] either way at 5 Hz; the last note sags by [sagCents] and fades by
     * [fadeDb] over its length; a burst of noise at [blinkDb] covers two frames in the middle of
     * the first note. Every note is [cents] off the B (a flute blown high or low).
     */
    fun sound(
        gapsMs: Int,
        toneDb: Double = -22.0,
        secondDb: Double = toneDb,
        roomDb: Double = -10.0,
        rt60: Double = 0.4,
        floorDb: Double = -50.0,
        pauseDb: Double = floorDb,
        wobbleDb: Double = 0.0,
        sagCents: Double = 0.0,
        fadeDb: Double = 0.0,
        blinkDb: Double? = null,
        cents: Double = 0.0,
    ): DoubleArray {
        val leadMs = 500 // the room alone, as the child picks up the flute
        val noteMs = intArrayOf(400, 400, 1500)
        val total = (leadMs + noteMs.sum() + 2 * gapsMs + 400) * rate / 1000
        val dry = DoubleArray(total)
        val starts = IntArray(3)
        var at = leadMs * rate / 1000
        for ((n, ms) in noteMs.withIndex()) {
            starts[n] = at
            val db = if (n == 1) secondDb else toneDb
            note(dry, at, ms, 10.0.pow(db / 20.0), wobbleDb, if (n == 2) sagCents else 0.0, if (n == 2) fadeDb else 0.0, cents)
            at += (ms + gapsMs) * rate / 1000
        }
        val wet = reverb(dry, roomDb, rt60)
        val noise = Random(7)
        var lp = 0.0
        // The lowpass below leaves the noise at 0.229 RMS, so this scale gives the dB asked for.
        fun rms(db: Double) = 10.0.pow(db / 20.0) / 0.229
        val floor = rms(floorDb)
        val pause = rms(pauseDb)
        val blink = blinkDb?.let(::rms)
        val blinkFrom = starts[0] + 180 * rate / 1000
        val blinkTo = blinkFrom + 2 * hop
        val firstGap = starts[0] + noteMs[0] * rate / 1000 until starts[1]
        val secondGap = starts[1] + noteMs[1] * rate / 1000 until starts[2]
        return DoubleArray(total) { i ->
            lp = 0.9 * lp + 0.1 * noise.nextGaussian() // lowpassed: breath, not hiss
            val a = when {
                blink != null && i in blinkFrom until blinkTo -> blink
                i in firstGap || i in secondGap -> pause
                else -> floor
            }
            wet[i] + a * lp
        }
    }

    /**
     * A line of notes on one breath (2026-11-16, legato): after 500 ms of the room, one tone stepping through [notes]
     * (MIDI, ms), its phase going on, swelling in over 30 ms and stopping over 10 ms at the end; at each change of note
     * the air stops for [changeStopMs] (a tongue, with 10 ms ramps either side), or without a stop the tone dips
     * [changeDipDb] (a breath or the fingers moving: 20 ms at full depth, 10 ms ramps in dB either side), or neither (a
     * clean slur); then 400 ms of the room. The room and the breath noise as in [sound], at [toneDb], [roomDb], [rt60]
     * and [floorDb]; [notesDb], when given, is each note's own level in place of [toneDb], and [dipAfterMs] moves the
     * dip that far after the change, onto the new note; every note is [cents] off its pitch (a flute blown high or low).
     */
    fun line(
        notes: List<Pair<Int, Int>>,
        changeStopMs: Int = 0,
        changeDipDb: Double = 0.0,
        toneDb: Double = -22.0,
        roomDb: Double = -10.0,
        rt60: Double = 0.4,
        floorDb: Double = -50.0,
        notesDb: List<Double>? = null,
        dipAfterMs: Int = 0,
        cents: Double = 0.0,
    ): DoubleArray {
        val leadMs = 500
        val total = (leadMs + notes.sumOf { it.second } + 400) * rate / 1000
        val dry = DoubleArray(total)
        val start = leadMs * rate / 1000
        var at = start
        var phase = 0.0
        val changes = mutableListOf<Int>()
        for ((n, note) in notes.withIndex()) {
            if (n > 0) changes += at
            val f = NoteMath.midiToFrequency(note.first) * 2.0.pow(cents / 1200.0)
            val amp = 10.0.pow((notesDb?.get(n) ?: toneDb) / 20.0)
            repeat(note.second * rate / 1000) {
                phase += 2 * PI * f / rate
                dry[at++] = amp * (sin(phase) + 0.32 * sin(2 * phase) + 0.13 * sin(3 * phase) + 0.05 * sin(4 * phase)) / 1.5
            }
        }
        val end = at
        val ramp = 0.010 * rate
        val stopHalf = changeStopMs * rate / 2000.0
        for (i in start until end) {
            var env = min(1.0, (i - start) / (0.030 * rate)) * min(1.0, (end - i) / ramp)
            for (c in changes) {
                val d = Math.abs(i - c - if (changeStopMs > 0) 0 else dipAfterMs * rate / 1000).toDouble()
                env *= when {
                    changeStopMs > 0 -> ((d - stopHalf) / ramp).coerceIn(0.0, 1.0)
                    changeDipDb > 0 -> 10.0.pow(-changeDipDb * (1.0 - ((d - ramp) / ramp).coerceIn(0.0, 1.0)) / 20.0)
                    else -> 1.0
                }
            }
            dry[i] *= env
        }
        val wet = reverb(dry, roomDb, rt60)
        val noise = Random(7)
        var lp = 0.0
        val floor = 10.0.pow(floorDb / 20.0) / 0.229
        return DoubleArray(total) { i ->
            lp = 0.9 * lp + 0.1 * noise.nextGaussian()
            wet[i] + floor * lp
        }
    }

    /**
     * A flute-ish tone: the fundamental with weaker harmonics, a 30 ms swell, a 10 ms stop,
     * wobbling [wobbleDb] either way at 5 Hz, drifting by [sagCents] and fading by [fadeDb] over
     * its length.
     */
    private fun note(out: DoubleArray, at: Int, ms: Int, amp: Double, wobbleDb: Double, sagCents: Double, fadeDb: Double, cents: Double) {
        val n = ms * rate / 1000
        var phase = 0.0
        for (i in 0 until n) {
            val f = NoteMath.midiToFrequency(71) * 2.0.pow((cents + sagCents * i / n) / 1200.0)
            phase += 2 * PI * f / rate
            val env = min(1.0, i / (0.030 * rate)) * min(1.0, (n - i) / (0.010 * rate))
            val loudness = 10.0.pow((wobbleDb * sin(2 * PI * 5 * i / rate) + fadeDb * i / n) / 20.0)
            val v = sin(phase) + 0.32 * sin(2 * phase) + 0.13 * sin(3 * phase) + 0.05 * sin(4 * phase)
            if (at + i < out.size) out[at + i] += amp * env * loudness * v / 1.5
        }
    }

    /** The room: early reflections and a feedback comb [roomDb] under the direct sound, decaying to -60 dB in [rt60] seconds. */
    private fun reverb(dry: DoubleArray, roomDb: Double, rt60: Double): DoubleArray {
        val out = dry.copyOf()
        val gain = 10.0.pow(roomDb / 20.0)
        val delays = intArrayOf(11, 17, 23, 31, 41).map { it * rate / 1000 }
        for (d in delays) {
            val feedback = 10.0.pow(-3.0 * d / rate / rt60)
            for (i in d until out.size) out[i] += gain / delays.size * dry[i - d] + feedback * (out[i - d] - dry[i - d]) / delays.size
        }
        return out
    }
}
