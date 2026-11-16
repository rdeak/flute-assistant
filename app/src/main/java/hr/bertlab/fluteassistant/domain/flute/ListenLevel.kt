// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.flute

/**
 * How closely "Your turn" judges the child, chosen by the child in the listening room (since 2026-11-16, spec
 * `docs/superpowers/specs/2026-11-16-flute-listen-levels-design.md`): each level is a preset of the run's own
 * [ListenRun.Rules], so a session records it and a replay moves it like any other rule.
 */
enum class ListenLevel {
    /** The rules a child starts with: today's numbers, and a note held too long is told so. */
    PRACTICE,

    /**
     * Asks for more: a closer pitch ([ListenRun.EXAM_TUNE_CENTS]), less room either way ([ListenRun.EXAM_LENGTH_SLACK],
     * [ListenRun.EXAM_LONG_SLACK]), and a mark that follows the whole note ([ListenRun.Rules.wholeNote]).
     */
    EXAM,
    ;

    val rules: ListenRun.Rules
        get() = when (this) {
            PRACTICE -> ListenRun.Rules()
            EXAM -> ListenRun.Rules(
                tuneCents = ListenRun.EXAM_TUNE_CENTS,
                lengthSlack = ListenRun.EXAM_LENGTH_SLACK,
                longSlack = ListenRun.EXAM_LONG_SLACK,
                wholeNote = true,
            )
        }
}
