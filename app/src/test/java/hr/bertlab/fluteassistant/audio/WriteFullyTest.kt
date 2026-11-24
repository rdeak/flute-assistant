// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class WriteFullyTest {

    /** A write that records what it was asked and accepts at most [accepts] frames per call, after [empties] empty calls. */
    private class RecordingWrite(private val accepts: Int, private var empties: Int = 0) : (FloatArray, Int, Int) -> Int {
        val calls = mutableListOf<Pair<Int, Int>>()
        override fun invoke(block: FloatArray, offset: Int, count: Int): Int {
            calls += offset to count
            if (empties > 0) {
                empties--
                return 0
            }
            return minOf(accepts, count)
        }
    }

    @Test
    fun `writeFully retries a short write from where it stopped`() {
        val write = RecordingWrite(accepts = 100)
        assertEquals(256, writeFully(FloatArray(256), running = { true }, write = write))
        assertEquals(listOf(0 to 256, 100 to 156, 200 to 56), write.calls)
    }

    @Test
    fun `writeFully waits through an empty write while running`() {
        val write = RecordingWrite(accepts = 256, empties = 2)
        assertEquals(256, writeFully(FloatArray(256), running = { true }, write = write))
        assertEquals(listOf(0 to 256, 0 to 256, 0 to 256), write.calls)
    }

    @Test
    fun `writeFully gives up on an empty write once stopped`() {
        val write = RecordingWrite(accepts = 256, empties = 5)
        assertEquals(0, writeFully(FloatArray(256), running = { false }, write = write))
        assertEquals(1, write.calls.size)
    }

    @Test
    fun `writeFully reports a failed write`() {
        assertEquals(-3, writeFully(FloatArray(256), running = { true }, write = { _, _, _ -> -3 }))
    }
}
