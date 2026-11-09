// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.notes

/**
 * How well the child knows each note of [StaffNotes.ALL], as a count per MIDI number: a find on
 * the first try adds one, a miss takes one away, and a note at [KNOWN_AT] is known for good (a
 * miss no longer costs it anything). A note never met is simply absent, and a count of zero is
 * never kept, so two progresses that mean the same are equal.
 */
data class NoteProgress(val counts: Map<Int, Int> = emptyMap()) {

    /** The count of [midi], 0 when it has none. */
    fun countOf(midi: Int): Int = counts[midi] ?: 0

    /** Whether [midi] has reached [KNOWN_AT]. */
    fun isKnown(midi: Int): Boolean = countOf(midi) >= KNOWN_AT

    /** One more for [midi], at most [KNOWN_AT]. */
    fun found(midi: Int): NoteProgress =
        NoteProgress(counts + (midi to (countOf(midi) + 1).coerceAtMost(KNOWN_AT)))

    /** One less for [midi], at least 0; a known note keeps its count. */
    fun missed(midi: Int): NoteProgress = when {
        isKnown(midi) -> this
        countOf(midi) <= 1 -> NoteProgress(counts - midi)
        else -> NoteProgress(counts + (midi to countOf(midi) - 1))
    }

    /** `"$midi:$count"` for every count above 0: the shape stored in the settings. */
    fun encode(): Set<String> =
        counts.filterValues { it > 0 }.map { (midi, count) -> "$midi:$count" }.toSet()

    companion object {
        /** The count at which a note is known. */
        const val KNOWN_AT = 8

        /** Nothing known yet. */
        val EMPTY = NoteProgress()

        /**
         * Reads what [encode] wrote, leniently as the practice notes are read: null or empty is
         * [EMPTY], an entry that is not `midi:count` or whose midi is not in [StaffNotes.ALL] is
         * dropped, a count is clamped to `0..KNOWN_AT`, and a zero is not kept.
         */
        fun decode(entries: Set<String>?): NoteProgress {
            if (entries.isNullOrEmpty()) return EMPTY
            val counts = entries.mapNotNull { entry ->
                val parts = entry.split(':')
                if (parts.size != 2) return@mapNotNull null
                val midi = parts[0].toIntOrNull()?.takeIf { it in StaffNotes.ALL } ?: return@mapNotNull null
                val count = parts[1].toIntOrNull()?.coerceIn(0, KNOWN_AT) ?: return@mapNotNull null
                if (count > 0) midi to count else null
            }.toMap()
            return NoteProgress(counts)
        }
    }
}
