// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.audio.flute

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs

class ToneRenderLoopTest {

    private val sampleRate = 48_000
    private val commands = ConcurrentLinkedQueue<ToneCommand>()
    private val synth = SustainedToneSynth(sampleRate)
    private val loop = ToneRenderLoop(synth, commands, sampleRate, blockFrames = 256, bufferFrames = 1024)

    private fun run(running: Boolean, blocks: Int) = repeat(blocks) { assertTrue(loop.next(running)) }

    @Test
    fun `while running it applies the queued commands and renders`() {
        assertTrue(loop.next(running = true))
        assertTrue(loop.block.all { it == 0f })
        commands.add(ToneCommand.On(440.0, 0.5f))
        run(running = true, blocks = 10) // 53 ms, past the attack
        assertTrue(commands.isEmpty())
        assertTrue(loop.block.any { abs(it) > 0.1f })
        commands.add(ToneCommand.Off)
        run(running = true, blocks = 40) // 213 ms, past the release
        assertTrue(synth.isSilent)
        assertTrue(loop.block.all { it == 0f })
    }

    /** Rotation: this loop was closed with a note sounding while its successor already serves the keys. */
    @Test
    fun `a retired loop leaves the queue to its successor`() {
        commands.add(ToneCommand.On(440.0, 0.5f))
        run(running = true, blocks = 10)
        commands.add(ToneCommand.On(660.0, 0.5f)) // a press meant for the loop that replaced this one
        var blocks = 0
        while (loop.next(running = false)) assertTrue("the retired loop should end", ++blocks < 1000)
        assertEquals(listOf<ToneCommand>(ToneCommand.On(660.0, 0.5f)), commands.toList())
        assertTrue(synth.isSilent)
    }

    /** The track's buffer still holds the end of the fade when the last sample is rendered; flushing it would click. */
    @Test
    fun `after the fade reaches silence it writes one track buffer of silence before it ends`() {
        commands.add(ToneCommand.On(440.0, 0.5f))
        run(running = true, blocks = 10)
        var silentBlocks = 0
        var blocks = 0
        while (true) {
            val wasSilent = synth.isSilent
            if (!loop.next(running = false)) break
            assertTrue("the retired loop should end", ++blocks < 1000)
            if (wasSilent) {
                assertTrue(loop.block.all { it == 0f })
                silentBlocks++
            }
        }
        assertEquals(1024 / 256 + 1, silentBlocks)
    }

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
