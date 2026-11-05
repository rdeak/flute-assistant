// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.notes

import hr.bertlab.fluteassistant.ui.metronome.TempoBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotesUiStateTest {

    private fun state(results: List<Boolean>, taps: Int) =
        NotesUiState(practiceNotes = setOf(60), currentMidi = 60, results = results, taps = taps)

    @Test
    fun `score counts first-tap answers`() {
        val s = state(results = listOf(true, false, true), taps = 5)
        assertEquals(3, s.answered)
        assertEquals(2, s.score)
    }

    @Test
    fun `the round's speed is an animal for the time per note`() {
        fun speed(millis: Long?) = NotesUiState(practiceNotes = setOf(60), currentMidi = 60, roundMillis = millis).speed()
        assertNull(speed(null))
        assertEquals(TempoBand.CHEETAH, speed(20_000L))
        assertEquals(TempoBand.RABBIT, speed(30_000L))
        assertEquals(TempoBand.DUCK, speed(60_000L))
        assertEquals(TempoBand.TURTLE, speed(100_000L))
        assertEquals(TempoBand.SNAIL, speed(100_010L))
    }

    @Test
    fun `aim is one to three stars from the accuracy`() {
        assertEquals(1, state(results = emptyList(), taps = 0).aimStars())
        assertEquals(3, state(results = List(10) { true }, taps = 11).aimStars())
        assertEquals(2, state(results = List(10) { true }, taps = 14).aimStars())
        assertEquals(1, state(results = List(10) { true }, taps = 15).aimStars())
    }

    @Test
    fun `accuracy is correct taps out of all taps`() {
        assertEquals(67, state(results = listOf(true, false), taps = 3).accuracyPercent)
        assertNull(state(results = emptyList(), taps = 0).accuracyPercent)
        assertEquals(100, state(results = List(10) { true }, taps = 10).accuracyPercent)
        assertEquals(10, NotesUiState.ROUND_LENGTH)
    }
}
