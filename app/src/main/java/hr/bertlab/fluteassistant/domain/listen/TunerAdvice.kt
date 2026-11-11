// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.listen

/**
 * The one short line under the status line saying what to change: nothing, come closer to the
 * phone or play one long clear note when the sound is unclear, roll the flute out for a flat
 * note and in for a sharp one, faster air for the octave below and gentler air for the octave
 * above the chosen note, or check the fingers for any other note.
 */
enum class TunerAdvice {
    NONE, COME_CLOSER, CLEAR_NOTE, ROLL_OUT, ROLL_IN, FASTER_AIR, GENTLER_AIR, CHECK_FINGERS,
    /** The flute's only: a written repeat held on instead of started again. */
    PLAY_AGAIN,
}
