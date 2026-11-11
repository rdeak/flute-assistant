// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.debug.session

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

class WavFileTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `float samples round trip at 48 kHz`() {
        val file = folder.newFile("a.wav")
        val samples = FloatArray(3000) { sin(it / 10.0).toFloat() }
        WavWriter(file, 48_000).use { w ->
            w.write(samples.copyOfRange(0, 1000), 1000)
            w.write(samples.copyOfRange(1000, 3000), 2000)
            assertEquals(3000L, w.frames)
        }
        val audio = WavFile.read(file)
        assertEquals(48_000, audio.sampleRate)
        assertArrayEquals(samples, audio.samples, 0f)
        assertEquals(44L + 3000 * 4, file.length())
    }

    @Test
    fun `a chunk is written only up to its count`() {
        val file = folder.newFile("b.wav")
        WavWriter(file, 48_000).use { w -> w.write(floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f), 2) }
        assertArrayEquals(floatArrayOf(0.1f, 0.2f), WavFile.read(file).samples, 0f)
    }

    @Test
    fun `a file the app died in, its header never patched, is read to its end`() {
        val file = folder.newFile("crash.wav")
        val samples = FloatArray(3000) { sin(it / 10.0).toFloat() }
        WavWriter(file, 48_000).write(samples, 3000) // no close: the header still says zero samples
        val audio = WavFile.read(file)
        assertEquals(48_000, audio.sampleRate)
        assertArrayEquals(samples, audio.samples, 0f)
    }

    @Test
    fun `an empty file is a valid wav`() {
        val file = folder.newFile("empty.wav")
        WavWriter(file, 44_100).use { }
        val audio = WavFile.read(file)
        assertEquals(0, audio.samples.size)
        assertEquals(44_100, audio.sampleRate)
    }

    @Test
    fun `a 16 bit mono file is read scaled to one`() {
        val file = pcm16(folder.newFile("pcm.wav"), channels = 1, samples = shortArrayOf(0, 16384, -32768))
        val audio = WavFile.read(file)
        assertEquals(8_000, audio.sampleRate)
        assertArrayEquals(floatArrayOf(0f, 0.5f, -1f), audio.samples, 0f)
    }

    @Test
    fun `stereo is refused`() {
        val file = pcm16(folder.newFile("stereo.wav"), channels = 2, samples = shortArrayOf(0, 0, 0, 0))
        assertThrows(IllegalArgumentException::class.java) { WavFile.read(file) }
    }

    /** A hand-written 16-bit PCM file at 8 kHz, as any other tool writes one. */
    private fun pcm16(file: File, channels: Int, samples: ShortArray): File {
        val data = samples.size * 2
        val b = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + data).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(channels.toShort()).putInt(8_000)
            .putInt(8_000 * channels * 2).putShort((channels * 2).toShort()).putShort(16)
        b.put("data".toByteArray()).putInt(data)
        for (s in samples) b.putShort(s)
        file.writeBytes(b.array())
        return file
    }
}
