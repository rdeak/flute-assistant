// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.flute

import hr.bertlab.fluteassistant.domain.flute.Book
import hr.bertlab.fluteassistant.domain.flute.Held
import hr.bertlab.fluteassistant.domain.flute.Listening
import hr.bertlab.fluteassistant.domain.flute.Pace
import hr.bertlab.fluteassistant.domain.flute.StepMark
import hr.bertlab.fluteassistant.domain.flute.Walk
import hr.bertlab.fluteassistant.domain.flute.NoteLength
import hr.bertlab.fluteassistant.domain.flute.Songbook
import hr.bertlab.fluteassistant.domain.flute.TuneBreath
import hr.bertlab.fluteassistant.domain.flute.TuneNote
import hr.bertlab.fluteassistant.domain.flute.TuneRest
import hr.bertlab.fluteassistant.domain.flute.WrongNote
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
        assertEquals("C's key", 0, playing.litLetter)
        assertEquals(0, playing.litIndex)
        val held = FluteUiState(tune = tune, held = Held(72, index = 1))
        assertEquals("the held key, by its letter", 0, held.litLetter)
        assertEquals(1, held.litIndex)
        assertNull(FluteUiState().litLetter)
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
        assertNull(FluteUiState(tune = tune, playing = Playing(1)).litLetter)
        assertEquals(1, FluteUiState(tune = tune, playing = Playing(1)).litIndex)
        assertFalse(FluteUiState(tune = tune, playing = Playing(0)).restLit)
        assertEquals(0, FluteUiState(tune = tune, playing = Playing(0)).litLetter)
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

    // ---- The room under the stage while the flute listens (the user's "3C" of 2026-11-10) ----

    private val lamb = listOf(TuneRest(NoteLength.QUARTER), TuneNote(71, NoteLength.QUARTER), TuneNote(69, NoteLength.QUARTER), TuneNote(67, NoteLength.QUARTER))

    /** Until the first note of a run is judged, the room says which note to play: a waiting screen says what it waits for. */
    @Test
    fun `the note to start with is asked for until a note is judged`() {
        val waiting = FluteUiState(tune = lamb, listening = Listening(index = 1))
        assertEquals(71, waiting.promptMidi)
        assertNull("a star was earned: the child has the idea", waiting.copy(listening = Listening(index = 2), marks = mapOf(1 to hr.bertlab.fluteassistant.domain.flute.StepMark.FIRST_TRY)).promptMidi)
        assertNull("a wrong note has its own line", waiting.copy(listening = Listening(index = 1, wrong = hr.bertlab.fluteassistant.domain.flute.WrongNote(1, 62))).promptMidi)
        assertNull("advice has the line", waiting.copy(listening = Listening(index = 1, hearing = Hearing.QUIET_UNCLEAR)).promptMidi)
        assertNull("playing back asks for nothing", FluteUiState(tune = lamb, playing = Playing(1)).promptMidi)
        assertNull(FluteUiState(tune = lamb).promptMidi)
    }

    /** While a wrong note stands, the room shows how the note that was wanted is fingered. */
    @Test
    fun `a wrong note brings the fingering of the note that was wanted`() {
        val missed = FluteUiState(tune = lamb, listening = Listening(index = 2, wrong = hr.bertlab.fluteassistant.domain.flute.WrongNote(2, 62)))
        assertEquals(69, missed.fingeringMidi)
        assertEquals(TunerAdvice.CHECK_FINGERS, missed.advice)
        assertNull(FluteUiState(tune = lamb, listening = Listening(index = 2)).fingeringMidi)
        assertNull(FluteUiState(tune = lamb, playing = Playing(1)).fingeringMidi)
        assertNull(FluteUiState(tune = lamb).fingeringMidi)
    }

    // ---- The walk of Your turn (2026-11-11): each note held for its length ----

    @Test
    fun `the animal stands where the walk is`() {
        val tune = listOf(TuneNote(60, NoteLength.HALF), TuneNote(67, NoteLength.QUARTER))
        val walking = FluteUiState(tune = tune, listening = Listening(index = 0, walk = Walk(0, 2, Walk.Phase.SOUNDING)))
        assertEquals(0, walking.animalIndex)
        assertEquals(2, walking.animalHalfBeat)
        assertEquals(0, walking.litIndex)
        assertTrue("the note is still waited on while it sounds", walking.waiting)
    }

    @Test
    fun `while an earned note finishes its time its star shows and nothing is waited on`() {
        val tune = listOf(TuneNote(60, NoteLength.HALF), TuneNote(67, NoteLength.QUARTER))
        val done = FluteUiState(
            tune = tune, marks = mapOf(0 to StepMark.FIRST_TRY),
            listening = Listening(index = 1, walk = Walk(0, 2, Walk.Phase.DONE)),
        )
        assertEquals(0, done.animalIndex)
        assertEquals(2, done.animalHalfBeat)
        assertEquals("the ring stays with the animal", 0, done.litIndex)
        assertFalse(done.waiting)
    }

    @Test
    fun `a rest walked is ringed and not waited on`() {
        // Rests behave as in Play (2026-11-13): ringed, the animal on its clicks, nothing waited on under it.
        val tune = listOf(TuneNote(60, NoteLength.QUARTER), TuneRest(NoteLength.QUARTER), TuneNote(62, NoteLength.QUARTER))
        val resting = FluteUiState(tune = tune, listening = Listening(index = 2, walk = Walk(1, 0, Walk.Phase.REST)))
        assertEquals(1, resting.litIndex)
        assertEquals(1, resting.animalIndex)
        assertFalse(resting.waiting)
        val ending = FluteUiState(tune = tune.take(2), listening = Listening(index = 0, walk = Walk(1, 0, Walk.Phase.REST)))
        assertEquals("the rest that ends the tune is ringed too", 1, ending.litIndex)
        assertFalse(ending.waiting)
    }

    @Test
    fun `a note cut short asks to hold it longer with no fingering`() {
        val short = FluteUiState(tune = lamb, listening = Listening(index = 1, walk = Walk(1, 0, Walk.Phase.SHORT)))
        assertEquals(TunerAdvice.HOLD_LONGER, short.advice)
        assertNull(short.fingeringMidi)
        assertNull(short.promptMidi)
        assertEquals(1, short.animalIndex)
        assertTrue("it waits to be played again", short.waiting)
    }

    @Test
    fun `the prompt goes once the first note sounds`() {
        val sounding = FluteUiState(tune = lamb, listening = Listening(index = 1, walk = Walk(1, 0, Walk.Phase.SOUNDING)))
        assertNull(sounding.promptMidi)
    }

    @Test
    fun `a note cut short and marked is not waited on, and an ended run rings nothing`() {
        val cut = FluteUiState(tune = lamb, listening = Listening(index = 2, walk = Walk(1, 0, Walk.Phase.CUT)))
        assertFalse(cut.waiting)
        assertEquals("the animal stands on the note let go too early", 1, cut.animalIndex)
        val pace = Pace(bpm = 64, pairs = 7, knobBpm = 100, speaks = true)
        // A run whose last rest was walked: without the speed line its animal's step would be ringed.
        val ended = FluteUiState(tune = lamb, listening = Listening(index = 3, walk = Walk(3, 0, Walk.Phase.REST), pace = pace))
        assertNull(ended.litIndex)
        assertFalse(ended.waiting)
        assertEquals(pace, ended.pace)
    }

    @Test
    fun `the tab is busy while it plays or hears a note, not while it listens to silence`() {
        assertFalse(FluteUiState(tune = lamb).isBusy)
        assertTrue(FluteUiState(tune = lamb, playing = Playing(0)).isBusy)
        assertFalse(FluteUiState(tune = lamb, listening = Listening(index = 0)).isBusy)
        assertTrue(FluteUiState(tune = lamb, listening = Listening(index = 0, heardNow = 67)).isBusy)
    }

    @Test
    fun `the lit letter is in the chosen octave`() {
        val c5 = listOf(TuneNote(72, NoteLength.QUARTER))
        assertEquals(0, FluteUiState(tune = c5, playing = Playing(0), octave = 2).litLetter)
        assertNull("c2 plays while the keys show octave one", FluteUiState(tune = c5, playing = Playing(0)).litLetter)
        assertEquals("E's key for an E flat", 2, FluteUiState(tune = listOf(TuneNote(63, NoteLength.QUARTER)), playing = Playing(0)).litLetter)
        // A held key is its letter, whatever the pills say now.
        assertEquals(0, FluteUiState(tune = listOf(TuneNote(60, NoteLength.EIGHTH)), held = Held(60, index = 0), octave = 2).litLetter)
        assertNull(FluteUiState().litLetter)
    }

    @Test
    fun `the join key follows the selection`() {
        val tune = listOf(TuneRest(NoteLength.QUARTER), TuneNote(62, NoteLength.QUARTER), TuneNote(64, NoteLength.QUARTER))
        assertTrue(FluteUiState(tune = tune).canJoin)
        assertFalse(FluteUiState(tune = tune, selected = 1).canJoin)
        assertTrue(FluteUiState(tune = tune, selected = 2).canJoin)
        // A key held on the chosen step: the next note is written at the end, not after the held one.
        assertFalse(FluteUiState(tune = tune, selected = 2, held = Held(65, index = 2, replaces = true)).canJoin)
        assertTrue("a key held at the end joins its note to the next", FluteUiState(tune = tune, held = Held(64, index = 2)).canJoin)
    }
}
