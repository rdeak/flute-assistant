// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.flute

import hr.bertlab.fluteassistant.domain.flute.Book
import hr.bertlab.fluteassistant.domain.flute.Held
import hr.bertlab.fluteassistant.domain.flute.Listening
import hr.bertlab.fluteassistant.domain.flute.NoteLength
import hr.bertlab.fluteassistant.domain.flute.Songbook
import hr.bertlab.fluteassistant.domain.flute.TuneBreath
import hr.bertlab.fluteassistant.domain.flute.TuneNote
import hr.bertlab.fluteassistant.domain.flute.TuneRest
import hr.bertlab.fluteassistant.domain.listen.Hearing
import hr.bertlab.fluteassistant.domain.listen.TunerAdvice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FluteUiStateTest {

    @Test
    fun `lit note and index follow playback first, then the held key`() {
        val tune = listOf(TuneNote(60, NoteLength.QUARTER), TuneNote(67, NoteLength.QUARTER))
        val playing = FluteUiState(tune = tune, held = Held(72, index = 1), playing = Playing(0))
        assertEquals(60, playing.litMidi)
        assertEquals(0, playing.litIndex)
        val held = FluteUiState(tune = tune, held = Held(72, index = 1))
        assertEquals(72, held.litMidi)
        assertEquals(1, held.litIndex)
        assertNull(FluteUiState().litMidi)
        assertNull(FluteUiState().litIndex)
    }

    @Test
    fun `empty and playing flags`() {
        assertTrue(FluteUiState().isEmpty)
        assertFalse(FluteUiState().isPlaying)
        assertTrue(FluteUiState(tune = listOf(TuneNote(60, NoteLength.QUARTER)), playing = Playing(0)).isPlaying)
    }

    @Test
    fun `the rest key is lit while held or while its rest plays`() {
        val tune = listOf(TuneNote(60, NoteLength.QUARTER), TuneRest(NoteLength.HALF), TuneBreath)
        assertFalse(FluteUiState(tune = tune).restLit)
        assertTrue(FluteUiState(tune = tune, held = Held(midi = null, index = 1)).restLit)
        assertTrue(FluteUiState(tune = tune, playing = Playing(1)).restLit)
        assertNull(FluteUiState(tune = tune, playing = Playing(1)).litMidi)
        assertEquals(1, FluteUiState(tune = tune, playing = Playing(1)).litIndex)
        assertFalse(FluteUiState(tune = tune, playing = Playing(0)).restLit)
        assertEquals(60, FluteUiState(tune = tune, playing = Playing(0)).litMidi)
    }

    @Test
    fun `running covers playback and listening, and the lit step follows the listened one`() {
        val tune = listOf(TuneNote(60, NoteLength.QUARTER), TuneNote(67, NoteLength.QUARTER))
        assertTrue(FluteUiState(tune = tune, playing = Playing(0)).isRunning)
        val listening = FluteUiState(tune = tune, listening = Listening(index = 1))
        assertTrue(listening.isRunning)
        assertEquals(1, listening.litIndex)
        assertEquals(1, listening.animalIndex)
        assertEquals(0, FluteUiState(tune = tune, playing = Playing(0), listening = Listening(index = 1)).animalIndex)
        assertNull(FluteUiState(tune = tune, held = Held(midi = null, index = 1)).animalIndex)
        assertEquals(1, FluteUiState(tune = tune, held = Held(midi = null, index = 1)).litIndex)
        assertFalse(FluteUiState().isRunning)
    }

    @Test
    fun `the animal stands on the last click's beat only while the tune plays`() {
        val tune = listOf(TuneNote(60, NoteLength.HALF), TuneNote(67, NoteLength.QUARTER))
        assertEquals(2, FluteUiState(tune = tune, playing = Playing(0, halfBeat = 2)).animalHalfBeat)
        // Your turn waits over the head of the step.
        assertEquals(0, FluteUiState(tune = tune, listening = Listening(index = 0)).animalHalfBeat)
    }

    @Test
    fun `only a tune with a note can be listened to`() {
        assertFalse(FluteUiState().canListen)
        assertFalse(FluteUiState(tune = listOf(TuneRest(NoteLength.QUARTER), TuneBreath)).canListen)
        assertTrue(FluteUiState(tune = listOf(TuneRest(NoteLength.QUARTER), TuneNote(60, NoteLength.QUARTER))).canListen)
    }

    @Test
    fun `advice follows the hearing, and a repeat held on instead of started again comes first`() {
        assertEquals(TunerAdvice.NONE, FluteUiState(listening = Listening(index = null, hearing = Hearing.CLEAR)).advice)
        assertEquals(TunerAdvice.COME_CLOSER, FluteUiState(listening = Listening(index = null, hearing = Hearing.QUIET_UNCLEAR)).advice)
        assertEquals(TunerAdvice.CLEAR_NOTE, FluteUiState(listening = Listening(index = null, hearing = Hearing.LOUD_UNCLEAR)).advice)
        assertEquals(TunerAdvice.PLAY_AGAIN, FluteUiState(listening = Listening(index = null, repeatHeld = true)).advice)
        assertEquals(TunerAdvice.PLAY_AGAIN, FluteUiState(listening = Listening(index = null, hearing = Hearing.LOUD_UNCLEAR, repeatHeld = true)).advice)
    }

    @Test
    fun `while the last note sounds the animal stays over it but nothing is lit or waited on`() {
        val tune = listOf(TuneNote(60, NoteLength.QUARTER), TuneNote(67, NoteLength.QUARTER))
        val finishing = FluteUiState(tune = tune, listening = Listening(index = 1, finishing = true))
        assertTrue(finishing.isRunning)
        assertEquals(1, finishing.animalIndex)
        assertNull(finishing.litIndex)
        assertFalse(finishing.waiting)
        assertTrue(FluteUiState(tune = tune, listening = Listening(index = 1)).waiting)
    }

    @Test
    fun `the shelf shows the open book's live tune and the others as stored`() {
        val books = List(6) { Book() }.toMutableList().also { it[2] = Book(listOf(TuneNote(64, NoteLength.QUARTER))) }
        val state = FluteUiState(tune = listOf(TuneNote(60, NoteLength.QUARTER)), openBook = 0, books = books)
        assertEquals(listOf(TuneNote(60, NoteLength.QUARTER)), state.shelf[0].tune)
        assertEquals(books[2], state.shelf[2])
        assertEquals(Songbook.SIZE, state.shelf.size)
    }

    @Test
    fun `the shelf keeps the open book's speed and beat as stored`() {
        val stored = Book(listOf(TuneNote(64, NoteLength.QUARTER)), bpm = 90)
        val books = List(6) { Book() }.toMutableList().also { it[3] = stored }
        val state = FluteUiState(tune = listOf(TuneNote(60, NoteLength.HALF)), openBook = 3, books = books)
        assertEquals(stored.copy(tune = state.tune), state.shelf[3])
        assertEquals(books[0], state.shelf[0])
    }

    @Test
    fun `a fresh state is on the first of six empty books`() {
        val state = FluteUiState()
        assertEquals(0, state.openBook)
        assertEquals(List(Songbook.SIZE) { Book() }, state.books)
    }
}
