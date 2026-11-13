// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.listen

import hr.bertlab.fluteassistant.audio.tuner.AudioRecordTunerEngine
import org.junit.Assert.assertEquals
import org.junit.Test

/** A frame's length: one place says how long a hop lasts, and the default is the engine's own hop. */
class PitchFrameTest {

    @Test
    fun `a hop lasts its samples at the rate`() {
        assertEquals(2048 * 1000.0 / 48_000, PitchFrame.hopMsAt(48_000), 0.0)
        assertEquals(46.44, PitchFrame.hopMsAt(44_100), 0.005)
        assertEquals("a short read is a short frame", 1024 * 1000.0 / 48_000, PitchFrame.hopMsAt(48_000, samples = 1024), 0.0)
    }

    @Test
    fun `the default is the engine's hop at the rate it asks for first`() {
        assertEquals(AudioRecordTunerEngine.HOP_SIZE, PitchFrame.HOP_SAMPLES)
        assertEquals(PitchFrame.hopMsAt(48_000), PitchFrame.DEFAULT_HOP_MS, 0.0)
        assertEquals(PitchFrame.DEFAULT_HOP_MS, PitchFrame(null, 0.0, level = 0f).hopMs, 0.0)
    }
}
