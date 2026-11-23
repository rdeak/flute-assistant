// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.audio.metronome

import hr.bertlab.fluteassistant.audio.writeFully
import hr.bertlab.fluteassistant.domain.metronome.MetronomeConfig

/**
 * The render thread's loop, pure Kotlin so it can be tested on the JVM: while [running], renders
 * the next block with the settings of the moment ([config]), writes all of it through [write]
 * (block, offset, count, giving the frames written or a negative error) and then hands the
 * clicks that start in it to [onClicks].
 *
 * Every rendered frame reaches the track ([writeFully]), through a short or an empty write too
 * (the route is being rebuilt). The renderer counts the frames it has rendered and a click's
 * audible time is that count against the track's own, so a block rendered and not written would
 * lose its clicks and leave every later beat flash late by the block's length.
 *
 * Returns 0 once [running] is false, or the error of a write that failed. Inline: the thread
 * must not allocate for a block.
 */
internal inline fun MetronomeRenderer.renderWhile(
    running: () -> Boolean,
    block: FloatArray,
    config: () -> MetronomeConfig,
    write: (FloatArray, Int, Int) -> Int,
    onClicks: (List<ClickEvent>) -> Unit,
): Int {
    while (running()) {
        val clicks = render(block, config())
        val written = writeFully(block, running, write)
        if (written <= 0) return written // a failed write, or told to stop while the track took nothing
        if (clicks.isNotEmpty()) onClicks(clicks)
    }
    return 0
}
