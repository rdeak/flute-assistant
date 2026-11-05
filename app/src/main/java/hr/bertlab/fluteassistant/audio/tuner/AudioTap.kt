// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.audio.tuner

/**
 * Hears the microphone's samples as [AudioRecordTunerEngine] reads them, on the engine's IO
 * thread: [opened] once the microphone is open, [samples] with each hop before its frame is
 * emitted (the array is reused, so take what is needed before returning), [closed] when the
 * microphone is released. The debug build's session recorder keeps the samples of a "Your
 * turn" run (`ui/flute/CLAUDE.md`); the release build binds [None].
 */
interface AudioTap {
    fun opened(sampleRate: Int, source: String)
    fun samples(chunk: FloatArray, count: Int)
    fun closed()

    /** Hears nothing: the release build's tap. */
    object None : AudioTap {
        override fun opened(sampleRate: Int, source: String) = Unit
        override fun samples(chunk: FloatArray, count: Int) = Unit
        override fun closed() = Unit
    }
}
