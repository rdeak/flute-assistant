// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.audio

/**
 * Writes all of [block] through [write] (block, offset, count, giving the frames written or a
 * negative error), retrying a short write from where it stopped and waiting a millisecond
 * through an empty one (the route is being rebuilt), so no rendered sample is skipped: in a
 * sustained tone that would be a phase jump, a click, and in the metronome it would lose a beat
 * and move every later beat flash off its click. Gives up on an empty write once [running] is
 * false, so a closing thread cannot hang on a stuck route. Returns the block's size when all of
 * it was written, 0 when it gave up, or the error. Inline: a render thread calls it for every
 * block, and must not allocate there.
 */
internal inline fun writeFully(block: FloatArray, running: () -> Boolean, write: (FloatArray, Int, Int) -> Int): Int {
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
