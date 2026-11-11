// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.listen

/**
 * The thresholds that turn microphone frames into a note. The tuner's view model smooths frames
 * into a reading with them and [NoteOnsetTracker] (the flute's "Your turn") hears notes with
 * them, so both hear a note by the same rules. One frame is about 43 ms.
 */
object ListenRules {
    /** The median of this many counted frames names the note, so a single mis-detected frame does not move it. */
    const val SMOOTHING_WINDOW = 5
    /** A summary log line (`tuner.summary`, `flute.listen_summary`) every this many frames, about once a second. */
    const val SUMMARY_FRAMES = 25
    /** A reading needs this many consecutive frames (~130 ms) that agree within [LOCK_CENTS]. */
    const val LOCK_FRAMES = 3
    const val LOCK_CENTS = 30.0
    /**
     * The second of two confidence gates. `YinPitchDetector` already returns a frame only
     * when its aperiodicity dips under its own threshold (0.2, so probability >= 0.8); this
     * gate then needs 0.85, i.e. aperiodicity under 0.15. Breath noise raises a beginner's
     * aperiodicity into exactly that 0.15 to 0.20 band, and on real transverse-flute
     * recordings with breath noise added the detector found the note in nearly every frame
     * below about 9 dB signal-to-noise while this gate discarded all of them, so the screen
     * showed nothing. Left at 0.85 until a phone capture confirms it: read `tuner.summary`
     * (`pitched` high, `shown` 0, `pMax` under `gate`) in the log. The evidence-backed
     * candidate is YIN 0.3 with this gate at 0.70, which showed every note of a soft
     * low-register recording down to 5 dB without new octave errors (see the tuner
     * target-note spec, revision 4).
     */
    const val MIN_PROBABILITY = 0.85
    /**
     * ~0.9 s. The last reading is held through this many silent, uncertain or unsteady frames
     * in a row; a steady note (a shown frame) resets the count, so a glissando or speech after
     * a note clears the reading too instead of holding it.
     */
    const val HOLD_FRAMES = 20
    /** ~0.5 s: how long a drift past the halfway point must last before the identified note switches. */
    const val SWITCH_FRAMES = 12
    /**
     * A pitch this close to a note still counts as that note, flat or sharp, even when its
     * nearest note is the semitone neighbour. An embouchure drift stays under half a semitone,
     * while a wrong fingering lands near a whole other note, so the neighbour played in tune
     * (100 cents away) still counts as another note. The tuner applies the band around the
     * chosen note and around the octave above and below it, and when identifying keeps the ring
     * on a note while the sound stays this close. The pitch window is unchanged: it pins at fifty.
     */
    const val NEAR_CENTS = 70.0
}
