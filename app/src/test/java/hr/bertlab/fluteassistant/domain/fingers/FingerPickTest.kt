// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.fingers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FingerPickTest {
    @Test
    fun `it opens on c`() {
        assertEquals(FingerPick(0, false), FingerPick())
        assertEquals(listOf(60, 72, 84, 96), FingerPick().notes)
    }

    @Test
    fun `a letter key shows its white note`() {
        val whites = listOf(0, 2, 4, 5, 7, 9, 11)
        for (letter in 0..6) assertEquals(FingerPick(whites[letter]), FingerPick().tapLetter(letter))
    }

    @Test
    fun `the sign then a letter shows that letter's black note and spends the sign`() {
        val waiting = FingerPick().tapSign()
        assertEquals(FingerPick(0, true), waiting)
        assertEquals(FingerPick(1), waiting.tapLetter(0)) // C sharp
        assertEquals(FingerPick(3), waiting.tapLetter(2)) // E flat
        assertEquals(FingerPick(6), waiting.tapLetter(3)) // F sharp
        assertEquals(FingerPick(8), waiting.tapLetter(4)) // G sharp
        assertEquals(FingerPick(10), waiting.tapLetter(6)) // B flat
    }

    @Test
    fun `d and a do nothing while the sign waits`() {
        val waiting = FingerPick(4, true)
        assertEquals(waiting, waiting.tapLetter(1))
        assertEquals(waiting, waiting.tapLetter(5))
        assertNull(waiting.faceOf(1))
        assertNull(waiting.faceOf(5))
    }

    @Test
    fun `a second tap on the sign cancels it`() = assertEquals(FingerPick(4), FingerPick(4).tapSign().tapSign())

    @Test
    fun `while the sign waits the keys show the black notes`() =
        assertEquals(listOf(1, null, 3, 6, 8, null, 10), (0..6).map { FingerPick(0, true).faceOf(it) })

    @Test
    fun `a black note names its own key and the others show their letters`() {
        val cSharp = FingerPick(1)
        assertEquals(0, cSharp.letter)
        assertEquals(listOf(1, 2, 4, 5, 7, 9, 11), (0..6).map { cSharp.faceOf(it) })
        assertEquals(3, FingerPick(3).faceOf(2)) // E flat is on E's key
        assertEquals(2, FingerPick(3).letter)
    }

    @Test
    fun `the chosen note's key is in the ring, and no letter key while the sign waits`() {
        val cSharp = FingerPick(1)
        assertEquals(listOf(true, false, false, false, false, false, false), (0..6).map { cSharp.isChosen(it) })
        assertEquals(List(7) { false }, (0..6).map { cSharp.tapSign().isChosen(it) })
    }

    @Test
    fun `a tap on the key of the black note on screen shows the white note again`() =
        assertEquals(FingerPick(0), FingerPick(1).tapLetter(0))

    @Test
    fun `leaving drops a waiting sign and keeps the note`() {
        assertEquals(FingerPick(9), FingerPick(9, true).leave())
        assertEquals(FingerPick(9), FingerPick(9).leave())
        // A black note stays too: a tap on its flute opens the tuner, and coming back finds C♯ (2026-11-18).
        assertEquals(FingerPick(1), FingerPick(1).leave())
    }
}
