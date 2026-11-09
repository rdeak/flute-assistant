// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.notes

import kotlin.math.abs
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhrasesTest {

    @Test
    fun `the staircase climbs on a clean phrase and steps down on a miss, between two and four`() {
        assertEquals(3, Phrases.nextLength(2, true))
        assertEquals(4, Phrases.nextLength(4, true))
        assertEquals(2, Phrases.nextLength(3, false))
        assertEquals(2, Phrases.nextLength(2, false))
    }

    @Test
    fun `the last phrase is as long as the notes left plus the given one`() {
        assertEquals(4, Phrases.lengthFor(4, 3))
        assertEquals(2, Phrases.lengthFor(4, 1))
        assertEquals(3, Phrases.lengthFor(3, 9))
    }

    @Test
    fun `a phrase has the asked length and only chosen notes`() {
        val notes = StaffNotes.DEFAULT_PRACTICE
        for (seed in 0 until 200) {
            for (length in Phrases.MIN_LENGTH..Phrases.MAX_LENGTH) {
                val phrase = Phrases.draw(notes, length, Random(seed))
                assertEquals("seed $seed length $length", length, phrase.size)
                assertTrue("seed $seed length $length: $phrase", phrase.all { it in notes })
            }
        }
    }

    @Test
    fun `every step is a second or a third and never a repeat`() {
        val notes = StaffNotes.DEFAULT_PRACTICE
        for (seed in 0 until 200) {
            for (length in Phrases.MIN_LENGTH..Phrases.MAX_LENGTH) {
                val phrase = Phrases.draw(notes, length, Random(seed))
                for ((a, b) in phrase.zipWithNext()) {
                    val places = abs(StaffNotes.positionOf(a) - StaffNotes.positionOf(b))
                    assertTrue("seed $seed: $phrase", places in 1..Phrases.REACH)
                }
            }
        }
    }

    @Test
    fun `the first note avoids the one asked for`() {
        for (seed in 0 until 50) {
            val phrase = Phrases.draw(setOf(60, 62, 64), 2, Random(seed), avoidFirst = 60)
            assertNotEquals("seed $seed: $phrase", 60, phrase.first())
        }
    }

    @Test
    fun `far-apart notes still make a phrase`() {
        for (seed in 0 until 50) {
            val phrase = Phrases.draw(setOf(60, 69), 4, Random(seed))
            val (a, b) = if (phrase.first() == 60) 60 to 69 else 69 to 60
            assertEquals("seed $seed", listOf(a, b, a, b), phrase)
        }
    }

    @Test
    fun `one chosen note repeats`() {
        for (seed in 0 until 50) {
            assertEquals(listOf(64, 64, 64), Phrases.draw(setOf(64), 3, Random(seed)))
        }
    }
}
