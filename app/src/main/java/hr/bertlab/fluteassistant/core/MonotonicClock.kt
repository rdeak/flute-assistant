// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.core

/** A monotonic millisecond clock, abstracted so ViewModels can be unit-tested with fake time. */
fun interface MonotonicClock {
    fun nowMillis(): Long
}
