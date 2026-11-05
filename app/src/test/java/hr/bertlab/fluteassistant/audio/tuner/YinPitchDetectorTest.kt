// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.audio.tuner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class YinPitchDetectorTest {

    private val sampleRate = 48_000
    private val detector = YinPitchDetector(sampleRate = sampleRate, bufferSize = 4096)

    private fun sine(freq: Double, amplitude: Double = 0.5, harmonics: List<Double> = emptyList()): FloatArray =
        FloatArray(4096) { i ->
            val t = i.toDouble() / sampleRate
            var v = sin(2 * PI * freq * t)
            harmonics.forEachIndexed { index, h -> v += h * sin(2 * PI * freq * (index + 2) * t) }
            (amplitude * v).toFloat()
        }

    private fun assertPitch(expected: Double, buffer: FloatArray, toleranceCents: Double = 2.0) {
        val estimate = detector.detect(buffer)
        assertNotNull("no pitch detected for $expected Hz", estimate)
        val cents = 1200.0 * kotlin.math.log2(estimate!!.frequency / expected)
        assertTrue(
            "expected $expected Hz, got ${estimate.frequency} Hz (${"%.2f".format(cents)} cents)",
            kotlin.math.abs(cents) <= toleranceCents,
        )
        assertTrue("probability too low: ${estimate.probability}", estimate.probability > 0.8)
    }

    @Test
    fun `detects concert A`() = assertPitch(440.0, sine(440.0))

    @Test
    fun `detects low A2`() = assertPitch(110.0, sine(110.0))

    @Test
    fun `detects middle C`() = assertPitch(261.6256, sine(261.6256))

    @Test
    fun `detects high flute register`() {
        assertPitch(1760.0, sine(1760.0))
        assertPitch(2093.0, sine(2093.0), toleranceCents = 3.0)
    }

    @Test
    fun `detects a note rich in harmonics`() =
        assertPitch(523.25, sine(523.25, harmonics = listOf(0.6, 0.4, 0.25)))

    @Test
    fun `tolerates noise`() {
        val random = Random(42)
        val buffer = sine(392.0).also { b -> for (i in b.indices) b[i] += (random.nextFloat() - 0.5f) * 0.1f }
        assertPitch(392.0, buffer, toleranceCents = 3.0)
    }

    @Test
    fun `silence yields no pitch`() {
        assertNull(detector.detect(FloatArray(4096)))
    }

    @Test
    fun `white noise yields no pitch`() {
        val random = Random(7)
        val buffer = FloatArray(4096) { (random.nextFloat() - 0.5f) * 0.8f }
        assertNull(detector.detect(buffer))
    }

    @Test
    fun `buffer size must match`() {
        val result = runCatching { detector.detect(FloatArray(100)) }
        assertTrue(result.isFailure)
        assertEquals(IllegalArgumentException::class, result.exceptionOrNull()!!::class)
    }

    // The flute-tuned detector (what the engine uses): searches only the flute's range and
    // treats microphone hiss and a quiet room as silence.

    @Test
    fun `flute detector ignores voices and hum below the flute`() {
        val flute = YinPitchDetector.forFlute(sampleRate)
        assertNull("50 Hz mains hum", flute.detect(sine(50.0, harmonics = listOf(0.5, 0.3))))
        assertNull("an adult voice", flute.detect(sine(150.0, harmonics = listOf(0.6, 0.4))))
    }

    @Test
    fun `flute detector still hears the lowest flute note played flat`() {
        val flute = YinPitchDetector.forFlute(sampleRate)
        val c4HalfFlat = 254.18 // C4 fifty cents low
        val estimate = flute.detect(sine(c4HalfFlat))
        assertNotNull(estimate)
        assertEquals(c4HalfFlat, estimate!!.frequency, 1.0)
    }

    @Test
    fun `flute detector treats a very quiet signal as silence`() {
        val flute = YinPitchDetector.forFlute(sampleRate)
        assertNull(flute.detect(sine(440.0, amplitude = 0.0005))) // about -69 dBFS: hiss, a quiet room
        assertNotNull(flute.detect(sine(440.0, amplitude = 0.01))) // about -43 dBFS: a soft note
    }
}
