// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class NotesRoundTest {

    private fun state(results: List<Boolean>, taps: Int) =
        NotesRound(practiceNotes = setOf(60), currentMidi = 60, results = results, taps = taps)

    @Test
    fun `score counts first-tap answers`() {
        val s = state(results = listOf(true, false, true), taps = 5)
        assertEquals(3, s.answered)
        assertEquals(2, s.score)
    }

    /**
     * A round remembers how far each note was learned when it began: the summary's keys start from there and rise
     * to where the round took them. Answers move [NotesRound.progress] and leave it alone.
     */
    @Test
    fun `a round remembers the progress it began with`() {
        val began = NoteProgress(mapOf(60 to 3))
        val round = NotesRound.open(setOf(60), NotesGame.FIND, soundOn = false, began, Random(1), previous = null)
        assertEquals(began, round.startProgress)
        val answered = round.tapped(60, now = 0L)
        assertEquals(4, answered.progress.countOf(60))
        assertEquals(began, answered.startProgress)
        assertEquals(10, NotesRound.ROUND_LENGTH)
    }

    // ---- The rules: a round is judged and moved on with no view model, clock or coroutine ----

    private val notes = setOf(60, 62, 64, 65, 67)
    private fun find(midi: Int = 60) = NotesRound(practiceNotes = notes, currentMidi = midi)
    private fun hear(vararg phrase: Int) =
        NotesRound(practiceNotes = notes, currentMidi = phrase[1], game = NotesGame.HEAR, phrase = phrase.toList(), placed = 1)

    @Test
    fun `the right place on the first tap scores, keeps the streak and moves the note up in the book`() {
        val after = find(60).tapped(60, now = 1_000)
        assertTrue(after.solved)
        assertEquals(listOf(true), after.results)
        assertEquals(1, after.streak)
        assertEquals(1, after.bestStreak)
        assertEquals(1, after.taps)
        assertEquals(1, after.progress.countOf(60))
        assertEquals(setOf(60), after.grewThisRound)
        assertEquals(setOf(60), after.askedThisRound)
        assertEquals(1_000L, after.startedAt)
    }

    @Test
    fun `a wrong place gets a cross and ends the streak, and only the first miss costs the note a step`() {
        val known = find(60).copy(streak = 3, bestStreak = 3, progress = NoteProgress(mapOf(60 to 2)))
        val once = known.tapped(62, now = 0)
        assertEquals(setOf(62), once.wrongTaps)
        assertEquals(62, once.lastWrongTap)
        assertEquals(0, once.streak)
        assertEquals(3, once.bestStreak)
        assertEquals(1, once.progress.countOf(60))
        val twice = once.tapped(64, now = 0)
        assertEquals(setOf(62, 64), twice.wrongTaps)
        assertEquals("the second cross costs nothing more", 1, twice.progress.countOf(60))
        val found = twice.tapped(60, now = 0)
        assertEquals("found, but not on the first tap", listOf(false), found.results)
        assertEquals(1, found.progress.countOf(60))
        assertTrue(found.grewThisRound.isEmpty())
        assertEquals(3, found.taps)
    }

    @Test
    fun `a tap is ignored while the answer shows, a phrase plays or the round is over`() {
        for (locked in listOf(find().copy(solved = true), find().copy(phrasePlaying = true), find().copy(roundOver = true))) {
            assertSame(locked, locked.tapped(60, now = 0))
        }
    }

    @Test
    fun `in Hear a note placed makes the next column live, and the last one answers the phrase`() {
        val placed = hear(60, 62, 64).tapped(62, now = 0)
        assertFalse(placed.solved)
        assertEquals(2, placed.placed)
        assertEquals(64, placed.currentMidi)
        assertEquals(listOf(true), placed.results)
        val done = placed.tapped(64, now = 0)
        assertTrue(done.solved)
        assertEquals(listOf(true, true), done.results)
    }

    @Test
    fun `a miss spoils Hear's phrase for the staircase, and Find has none to spoil`() {
        assertFalse(hear(60, 62).tapped(65, now = 0).phraseClean)
        assertTrue(find(60).tapped(65, now = 0).phraseClean)
    }

    @Test
    fun `the tenth note found says how long the round took, from its first tap`() {
        val nine = find(60).copy(results = List(9) { true }, startedAt = 1_000)
        assertEquals(41_000L, nine.tapped(60, now = 42_000).roundMillis)
        assertNull(find(60).tapped(60, now = 42_000).roundMillis)
    }

    @Test
    fun `the next note is never the one just asked, and the tenth ends the round`() {
        val random = Random(1)
        val next = find(60).tapped(60, now = 0).next(random)
        assertFalse(next.solved)
        assertNotEquals(60, next.currentMidi)
        assertTrue(next.currentMidi in notes)
        val over = find(60).copy(results = List(10) { true }, solved = true).next(random)
        assertTrue(over.roundOver)
        assertFalse(over.solved)
    }

    @Test
    fun `Hear's next phrase is a note longer after a clean one and opens on another note`() {
        val random = Random(1)
        val next = hear(60, 62).tapped(62, now = 0).next(random)
        assertEquals(3, next.phraseLength)
        assertEquals(3, next.phrase.size)
        assertNotEquals(60, next.phrase.first())
        assertEquals(1, next.placed)
        assertEquals(next.phrase[1], next.currentMidi)
        assertEquals(1, next.phraseId)
        val spoiled = hear(60, 62).copy(phraseLength = 3, phraseClean = false, solved = true).next(random)
        assertEquals(2, spoiled.phraseLength)
        assertTrue(spoiled.phraseClean)
    }

    @Test
    fun `a round opens on another note than the one it replaces, and Hear opens with two notes`() {
        val random = Random(1)
        val first = NotesRound.open(setOf(60, 62), NotesGame.FIND, soundOn = false, NoteProgress.EMPTY, random, previous = null)
        assertFalse(first.soundOn)
        val second = NotesRound.open(setOf(60, 62), NotesGame.FIND, soundOn = true, first.progress, random, previous = first)
        assertNotEquals(first.currentMidi, second.currentMidi)
        val hear = NotesRound.open(notes, NotesGame.HEAR, soundOn = true, NoteProgress.EMPTY, random, previous = second)
        assertEquals(2, hear.phrase.size)
        assertEquals(1, hear.placed)
        assertEquals(hear.phrase[1], hear.currentMidi)
        assertEquals("the phrase counter goes on across rounds", second.phraseId + 1, hear.phraseId)
    }
}
