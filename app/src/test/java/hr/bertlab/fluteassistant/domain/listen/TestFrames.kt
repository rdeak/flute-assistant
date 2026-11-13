// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.listen

import hr.bertlab.fluteassistant.domain.tuner.NoteMath
import kotlin.math.ceil
import kotlin.math.pow

/**
 * Hand-made microphone frames for the tests of what frames become: the tracker's, "Your turn"'s and the flute view
 * model's. Real sound is `ListenPath`'s.
 */
object TestFrames {

    /**
     * A frame of [midi], [cents] off it, at a real flute's [level] at the phone (the recorded sessions judged every
     * note at 0.67 or over), its quietest stretch at [minLevel] (a tongued stop or a breath dipping falls there).
     */
    fun pitched(
        midi: Int,
        cents: Double = 0.0,
        probability: Double = 0.97,
        level: Float = 0.7f,
        minLevel: Float = level,
        hopMs: Double = PitchFrame.DEFAULT_HOP_MS,
    ) = PitchFrame(NoteMath.midiToFrequency(midi) * 2.0.pow(cents / 1200.0), probability, level, minLevel, hopMs)

    /** A frame of silence. */
    val silent = PitchFrame(null, 0.0, level = 0f)

    /** The frames a note must sound to have sounded [ms], its first frame counted in full. */
    fun framesFor(ms: Double, hopMs: Double = PitchFrame.DEFAULT_HOP_MS): Int = ceil(ms / hopMs).toInt()
}
