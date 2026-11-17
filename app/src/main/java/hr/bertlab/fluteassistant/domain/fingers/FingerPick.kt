// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.fingers

import hr.bertlab.fluteassistant.domain.tuner.NoteSpelling

/**
 * What the Fingers tab shows: the chosen note as a pitch class (0 = C to 11 = B) and whether the ♯♭ key waits
 * (spec `2026-11-17-fingers-tab-design.md`, part 3, "What the keys do"). Seven letter keys, 0 = C to 6 = B, and ♯♭,
 * which works as on the flute tab: one tap makes it wait, the next letter shows that letter's black note in the app's
 * one spelling (C♯ E♭ F♯ G♯ B♭) and spends it; D and A have none and do nothing; a second tap cancels it. It opens on
 * C. Pure Kotlin: the view model holds it, the screen shows it.
 */
data class FingerPick(val pitchClass: Int = 0, val signWaiting: Boolean = false) {

    /** The notes on the screen, low to high. */
    val notes: List<Int> get() = FingerChart.notesOf(pitchClass)

    /** The letter the note is written on, 0 = C to 6 = B. */
    val letter: Int get() = NoteSpelling.letterOf(pitchClass)

    /** Whether the key [letter] is the one in the ring: the chosen note's own key, and no letter key while ♯♭ waits. */
    fun isChosen(letter: Int): Boolean = letter == this.letter && !signWaiting

    /**
     * The pitch class the key [letter] shows: while the sign waits, its black note, or null when it has none (D and A,
     * which are off); otherwise its white note, but for the chosen note's own key, which names the chosen note.
     */
    fun faceOf(letter: Int): Int? = when {
        signWaiting -> NoteSpelling.blackOf(letter)
        letter == this.letter -> pitchClass
        else -> NoteSpelling.whiteOf(letter)
    }

    /** A tap on the key [letter]: its black note while the sign waits (nothing for D and A), else its white note. */
    fun tapLetter(letter: Int): FingerPick = when {
        !signWaiting -> FingerPick(NoteSpelling.whiteOf(letter))
        else -> NoteSpelling.blackOf(letter)?.let { FingerPick(it) } ?: this
    }

    /** A tap on ♯♭: it waits, or a second tap cancels it. */
    fun tapSign(): FingerPick = copy(signWaiting = !signWaiting)

    /** Leaving the tab drops a waiting sign; the note stays. */
    fun leave(): FingerPick = copy(signWaiting = false)
}
