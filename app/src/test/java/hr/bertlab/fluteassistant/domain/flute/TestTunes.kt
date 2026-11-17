// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.flute

import hr.bertlab.fluteassistant.domain.flute.NoteLength.HALF
import hr.bertlab.fluteassistant.domain.flute.NoteLength.QUARTER

/** Short hands for the notes a test writes its tune with, as `TestFrames` has the frames a test feeds it. */
object TestTunes {
    /** A quarter note. */
    fun q(midi: Int) = TuneNote(midi, QUARTER)

    /** A half note. */
    fun h(midi: Int) = TuneNote(midi, HALF)

    /** A quarter note joined to the note before it: a slur, or a tie when that is the same note. */
    fun j(midi: Int) = TuneNote(midi, QUARTER, joined = true)
}
