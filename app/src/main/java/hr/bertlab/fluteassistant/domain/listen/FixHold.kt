// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.listen

/**
 * Keeps a fix on the flute ([TunerAdvice.isFluteFix]: the head joint, rolling) on the tuner's line for a while after
 * the sound stops. The reading clears about 0.9 s after it, and a child who stops playing to free a hand would find
 * the words gone, and with them the tap that opens the guide (the final review of 2026-11-18). Only plain silence
 * keeps it: other words, a new sound or the note playing take the line at once.
 *
 * The tuner's view model feeds it once a frame, so the hold is counted in frames like every other one ([HOLD_FRAMES])
 * and outlives a turn of the phone.
 */
class FixHold(private val frames: Int = HOLD_FRAMES) {

    /** The fix the line still says in silence, or [TunerAdvice.NONE]. */
    var held: TunerAdvice = TunerAdvice.NONE
        private set

    private var silentFrames = 0

    /** One frame: what the tuner advises now, and whether there is plain [silent]ce, nothing said and nothing playing. Returns [held]. */
    fun onFrame(advice: TunerAdvice, silent: Boolean): TunerAdvice {
        when {
            advice.isFluteFix -> {
                held = advice
                silentFrames = 0
            }
            held == TunerAdvice.NONE -> Unit
            !silent || ++silentFrames > frames -> held = TunerAdvice.NONE
        }
        return held
    }

    /** Drops what is held, for when the listening starts afresh or the guide it led to opens. */
    fun clear() {
        held = TunerAdvice.NONE
    }

    companion object {
        /** Silent frames a fix stays on the line: ~5 s at 43 ms per frame, time to put the flute down and tap the line. */
        const val HOLD_FRAMES = 117
    }
}
