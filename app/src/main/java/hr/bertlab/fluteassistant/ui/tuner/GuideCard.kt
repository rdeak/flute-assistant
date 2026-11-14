// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.tuner

/**
 * The four cards of the tuner's guide to tuning the flute, in the order the child goes through them
 * (spec `2026-11-14-tuning-guide-design.md`): warm the flute up, set the head joint on the tuning note,
 * check each note, and leave the head joint when one note is still off. The dots and ‹ › follow [ordinal].
 */
enum class GuideCard { WARM_UP, HEAD_JOINT, EACH_NOTE, ONE_NOTE_OFF }

/** How the guide was left: OK on the last card, its ✕, or the phone's Back. Logged in lower case. */
enum class GuideExit { OK, CLOSE, BACK }
