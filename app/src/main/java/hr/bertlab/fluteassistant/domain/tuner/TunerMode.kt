// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.tuner

/** What the tuner measures against. Persisted by name, so the entries must not be renamed. */
enum class TunerMode {
    /** Name the nearest note to whatever is played and show the distance to it. */
    IDENTIFY,
    /** Measure the played pitch against the note the child chose in the wheel. */
    TARGET,
}
