// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteProgressTest {

    @Test
    fun `a first-try find adds one, up to eight`() {
        var progress = NoteProgress.EMPTY
        repeat(NoteProgress.KNOWN_AT) { progress = progress.found(60) }

        assertEquals(8, progress.countOf(60))
        assertTrue(progress.isKnown(60))

        assertEquals("a ninth find leaves it at eight", 8, progress.found(60).countOf(60))
    }

    @Test
    fun `seven is not yet known`() {
        var progress = NoteProgress.EMPTY
        repeat(NoteProgress.KNOWN_AT - 1) { progress = progress.found(60) }

        assertEquals(7, progress.countOf(60))
        assertFalse(progress.isKnown(60))
    }

    @Test
    fun `a miss takes one away, never below zero, never from a known note`() {
        assertEquals(0, NoteProgress.EMPTY.missed(60).countOf(60))

        val three = NoteProgress.EMPTY.found(60).found(60).found(60)
        assertEquals(2, three.missed(60).countOf(60))

        var known = NoteProgress.EMPTY
        repeat(NoteProgress.KNOWN_AT) { known = known.found(60) }
        assertEquals(8, known.missed(60).countOf(60))
    }

    @Test
    fun `a note never met counts zero and is not known`() {
        assertEquals(0, NoteProgress.EMPTY.countOf(72))
        assertFalse(NoteProgress.EMPTY.isKnown(72))
    }

    @Test
    fun `encode and decode round-trip and leave zeros out`() {
        val encoded = NoteProgress.EMPTY.found(60).found(62).missed(62).encode()

        assertEquals(setOf("60:1"), encoded)
        assertEquals(NoteProgress(mapOf(60 to 1)), NoteProgress.decode(encoded))
    }

    @Test
    fun `decode drops what it cannot read and clamps what it can`() {
        val decoded = NoteProgress.decode(setOf("abc", "60:99", "61:3", "x:y", "64:5", "65:0", "62:1:2"))

        assertEquals(mapOf(60 to 8, 64 to 5), decoded.counts)
        assertEquals(NoteProgress.EMPTY, NoteProgress.decode(setOf("62:-1")))
    }

    @Test
    fun `decode of nothing is empty`() {
        assertEquals(NoteProgress.EMPTY, NoteProgress.decode(null))
        assertEquals(NoteProgress.EMPTY, NoteProgress.decode(emptySet()))
    }
}
