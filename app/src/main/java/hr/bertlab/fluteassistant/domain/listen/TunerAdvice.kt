// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.listen

/**
 * What to change, in a few words: nothing, come closer to the phone or play one long clear note
 * when the sound is unclear, roll the flute out for a flat note and in for a sharp one, faster air
 * for the octave below and gentler air for the octave above the chosen note, or check the fingers
 * for any other note. On the tuning note the tuner names the head joint instead of rolling (since
 * 2026-11-14). The tuner says it on its one line, the flute's listening room on its advice line.
 */
enum class TunerAdvice {
    NONE, COME_CLOSER, CLEAR_NOTE, ROLL_OUT, ROLL_IN, FASTER_AIR, GENTLER_AIR, CHECK_FINGERS,
    /** The flute's only: a written repeat held on instead of started again. */
    PLAY_AGAIN,
    /** The flute's only: a note stopped before it was long enough, to be played again (since 2026-11-11). */
    HOLD_LONGER,
    /** The flute's only: a note faded away before it was long enough, a guess that the air ran out. */
    OUT_OF_AIR,
    /**
     * The flute's only: the right note sounding above its written pitch, or earning the down arrow (since 2026-11-14;
     * from 2026-11-13 a wrong note just above the written one, a guess that it was blown too high).
     */
    TOO_HIGH,
    /** The flute's only: the right note sounding under its written pitch, or earning the up arrow (since 2026-11-14). */
    TOO_LOW,
    /** The flute's only: a note held past its written length, marked too long (since 2026-11-16). */
    TOO_LONG,
    /** The flute's only: a note written joined to the one before, started with the tongue (since 2026-11-16). */
    JOIN,
    /** The tuner's only: the tuning note chosen and played flat, so the head joint goes in a little. */
    HEAD_JOINT_IN,
    /** The tuner's only: the tuning note chosen and played sharp, so the head joint comes out a little. */
    HEAD_JOINT_OUT;

    /**
     * A fix on the flute itself, the head joint or rolling: the tuner's line keeps such words a while after the sound
     * stops ([FixHold]) and opens the guide to tuning from them.
     */
    val isFluteFix: Boolean
        get() = this == HEAD_JOINT_IN || this == HEAD_JOINT_OUT || this == ROLL_IN || this == ROLL_OUT
}
