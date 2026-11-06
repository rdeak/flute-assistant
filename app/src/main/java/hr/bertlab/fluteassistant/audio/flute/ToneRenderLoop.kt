// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.audio.flute

import java.util.Queue

/**
 * Writes all of [block] through [write] (block, offset, count, giving the frames written or a
 * negative error), retrying a short write from where it stopped and waiting a millisecond
 * through an empty one (the route is being rebuilt), so a sustained tone never skips samples:
 * that would be a phase jump, a click. Gives up on an empty write once [running] is false, so
 * a closing thread cannot hang on a stuck route. Returns the block's size when all of it was
 * written, 0 when it gave up, or the error.
 */
internal fun writeFully(block: FloatArray, running: () -> Boolean, write: (FloatArray, Int, Int) -> Int): Int {
    var offset = 0
    while (offset < block.size) {
        val written = write(block, offset, block.size - offset)
        when {
            written < 0 -> return written
            written == 0 -> {
                if (!running()) return 0
                Thread.sleep(1)
            }
            else -> offset += written
        }
    }
    return block.size
}

/** What the UI asks of the flute voice; queued for the render thread, which applies them before each block. */
internal sealed interface ToneCommand {
    data class On(val frequencyHz: Double, val amplitude: Float) : ToneCommand
    data object Off : ToneCommand
}

/**
 * The render thread's loop body, pure Kotlin so the close path can be tested on the JVM: while
 * [running] it applies the queued commands and renders the next block into [block]; once it is
 * not, it lets a sounding note release (bounded), then writes one track buffer's worth of
 * silence so the end of the fade is played out rather than flushed with the track, and then
 * says it is done. A retired loop never touches the queue: that belongs to whichever loop is
 * running now (when the child leaves the tab and comes straight back, the successor is already
 * serving the keys; a turn of the phone does not close the player).
 */
internal class ToneRenderLoop(
    private val synth: SustainedToneSynth,
    private val commands: Queue<ToneCommand>,
    sampleRate: Int,
    blockFrames: Int,
    bufferFrames: Int,
) {
    val block = FloatArray(blockFrames)

    /** After close, at most this many blocks are rendered to let a sounding note release. */
    private val releaseBlocks = (sampleRate.toLong() * SustainedToneSynth.RELEASE_MS * 2 / 1000 / blockFrames).toInt() + 2
    private var released = 0

    /** Then this many silent blocks: the track buffer still holds the end of the fade, and pause + flush would drop it. */
    private val tailBlocks = bufferFrames / blockFrames + 1
    private var tailed = 0

    /** Fills [block] with the next audio; false once, after [running] went false, the release and the silent tail are written. */
    fun next(running: Boolean): Boolean {
        if (running) {
            applyCommands()
            synth.render(block)
            return true
        }
        if (!synth.isSilent && released < releaseBlocks) {
            released++
            synth.noteOff()
            synth.render(block)
            return true
        }
        if (tailed < tailBlocks) {
            tailed++
            block.fill(0f)
            return true
        }
        return false
    }

    private fun applyCommands() {
        while (true) {
            when (val command = commands.poll() ?: return) {
                is ToneCommand.On -> synth.noteOn(command.frequencyHz, command.amplitude)
                ToneCommand.Off -> synth.noteOff()
            }
        }
    }
}
