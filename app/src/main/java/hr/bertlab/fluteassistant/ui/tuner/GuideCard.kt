// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.tuner

import hr.bertlab.fluteassistant.domain.listen.TunerAdvice

/**
 * The four cards of the tuner's guide to tuning the flute, in the order the child goes through them
 * (spec `2026-11-14-tuning-guide-design.md`): warm the flute up, set the head joint on the tuning note,
 * check each note, and leave the head joint when one note is still off. The dots and ‹ › follow [ordinal].
 */
enum class GuideCard {
    WARM_UP, HEAD_JOINT, EACH_NOTE, ONE_NOTE_OFF;

    /** The card before this one; null on the first. */
    val previous: GuideCard? get() = entries.getOrNull(ordinal - 1)

    /** The card after this one; null on the last, where OK takes the place of ›. */
    val next: GuideCard? get() = entries.getOrNull(ordinal + 1)
}

/**
 * The card the tuner's line opens when it names a fix on the flute (since 2026-11-18, when the How? button went): the
 * head joint on "Tune on a1", rolling on "One note still off?". Null for every other line, which opens nothing.
 */
val TunerAdvice.guideCard: GuideCard?
    get() = when (this) {
        TunerAdvice.HEAD_JOINT_IN, TunerAdvice.HEAD_JOINT_OUT -> GuideCard.HEAD_JOINT
        TunerAdvice.ROLL_IN, TunerAdvice.ROLL_OUT -> GuideCard.ONE_NOTE_OFF
        else -> null
    }

/**
 * How the guide was left: OK on the last card, its ✕, the phone's Back, or a flute tapped on the Fingers tab, which
 * opens its note in Tune mode (`TunerViewModel.tune`, since 2026-11-18). Logged in lower case.
 */
enum class GuideExit { OK, CLOSE, BACK, FINGERS }
