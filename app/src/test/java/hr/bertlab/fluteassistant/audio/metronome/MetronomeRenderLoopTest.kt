// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.audio.metronome

import hr.bertlab.fluteassistant.domain.metronome.MetronomeConfig
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class MetronomeRenderLoopTest {

    private val sampleRate = 48_000
    private val blockFrames = 480 // divides one second of audio evenly
    private val config = MetronomeConfig(bpm = 120)

    /**
     * A track that takes of each write what [accepts] says (given the write's number and the
     * frames on offer; a negative number is an error), keeps what it took and lets the loop run
     * until it holds [frames].
     */
    private class FakeTrack(val frames: Int, private val accepts: (write: Int, offered: Int) -> Int) {
        val audio = FloatArray(frames)
        val clicks = mutableListOf<ClickEvent>()
        var held = 0
        private var writes = 0

        fun write(block: FloatArray, offset: Int, count: Int): Int {
            val taken = accepts(writes++, count)
            if (taken > 0) {
                block.copyInto(audio, held, offset, offset + taken)
                held += taken
            }
            return taken
        }
    }

    private fun play(track: FakeTrack): Int = MetronomeRenderer(sampleRate).renderWhile(
        running = { track.held < track.frames },
        block = FloatArray(blockFrames),
        config = { config },
        write = track::write,
        onClicks = { track.clicks += it },
    )

    /** One second as the renderer makes it when nothing gets in its way: the clicks and the audio. */
    private fun undisturbed(): Pair<List<ClickEvent>, FloatArray> {
        val renderer = MetronomeRenderer(sampleRate)
        val audio = FloatArray(sampleRate)
        val clicks = mutableListOf<ClickEvent>()
        val block = FloatArray(blockFrames)
        for (start in 0 until sampleRate step blockFrames) {
            clicks += renderer.render(block, config)
            block.copyInto(audio, start)
        }
        return clicks to audio
    }

    private fun assertUndisturbed(track: FakeTrack) {
        val (clicks, audio) = undisturbed()
        assertEquals(listOf(0L, 24_000L), clicks.map { it.frame })
        assertEquals(clicks, track.clicks)
        assertArrayEquals(audio, track.audio, 0f)
    }

    @Test
    fun `the track gets every block and the loop hands on every click`() {
        val track = FakeTrack(sampleRate) { _, offered -> offered }
        assertEquals(0, play(track))
        assertUndisturbed(track)
    }

    @Test
    fun `a block the track does not take is offered again, not dropped`() {
        // The route is being rebuilt: the first write and every third after it take nothing.
        val track = FakeTrack(sampleRate) { write, offered -> if (write % 3 == 0) 0 else offered }
        assertEquals(0, play(track))
        assertUndisturbed(track)
    }

    @Test
    fun `a block the track takes part of is finished from where it stopped`() {
        val track = FakeTrack(sampleRate) { _, offered -> minOf(100, offered) }
        assertEquals(0, play(track))
        assertUndisturbed(track)
    }

    @Test
    fun `a failed write ends the loop with its error`() {
        val track = FakeTrack(sampleRate) { write, offered -> if (write == 3) -6 else offered }
        assertEquals(-6, play(track))
        assertEquals(3 * blockFrames, track.held)
    }

    @Test
    fun `told to stop while the track takes nothing, the loop ends`() {
        var asked = 0
        val ended = MetronomeRenderer(sampleRate).renderWhile(
            running = { ++asked <= 3 },
            block = FloatArray(blockFrames),
            config = { config },
            write = { _, _, _ -> 0 },
            onClicks = { fail("no block reached the track") },
        )
        assertEquals(0, ended)
    }
}
