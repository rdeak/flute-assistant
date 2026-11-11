// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.audio.tuner

import hr.bertlab.fluteassistant.domain.listen.PitchFrame
import kotlinx.coroutines.flow.Flow

/** Microphone pitch-tracking engine. */
interface TunerEngine {
    /**
     * A cold flow: collecting it opens the microphone and emits a [PitchFrame] roughly every
     * 40 ms, every frame in order, buffered rather than conflated, because the flute's "Your turn"
     * counts frames (a gap is two in a row) and a busy main thread must not lose it one;
     * cancelling the collector releases the microphone.
     * Fails with [SecurityException] when RECORD_AUDIO has not been granted.
     */
    fun pitchFrames(): Flow<PitchFrame>
}
