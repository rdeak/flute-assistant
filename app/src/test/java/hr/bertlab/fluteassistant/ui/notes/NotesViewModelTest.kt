// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.notes

import hr.bertlab.fluteassistant.audio.TONE_VOLUME
import hr.bertlab.fluteassistant.core.log.LogTags
import hr.bertlab.fluteassistant.domain.notes.NoteProgress
import hr.bertlab.fluteassistant.domain.notes.NotesGame
import hr.bertlab.fluteassistant.domain.notes.NotesRound
import hr.bertlab.fluteassistant.domain.notes.Phrases
import hr.bertlab.fluteassistant.domain.notes.StaffNotes
import hr.bertlab.fluteassistant.domain.tuner.NoteMath
import hr.bertlab.fluteassistant.fakes.FakeClock
import hr.bertlab.fluteassistant.fakes.FakeLogger
import hr.bertlab.fluteassistant.fakes.FakeReferenceTonePlayer
import hr.bertlab.fluteassistant.fakes.FakeSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val settings = FakeSettingsStore()
    private val tonePlayer = FakeReferenceTonePlayer()
    private val clock = FakeClock(now = 10_000L)
    private val logger = FakeLogger()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(seed: Int = 1) = NotesViewModel(settings, tonePlayer, clock, logger, Random(seed))

    /** A view model on the Hear game, its first phrase on the staff. */
    private fun hearing(seed: Int = 1) = viewModel(seed).also { it.setGame(NotesGame.HEAR) }

    private val NotesViewModel.current: Int get() = state.value.currentMidi

    /** A note other than the one asked, from the whole staff. */
    private fun NotesViewModel.otherNote(): Int = StaffNotes.ALL.first { it != current }

    /** Taps the right place and lets the green second pass. */
    private fun TestScope.answerRight(vm: NotesViewModel) {
        vm.onNoteTapped(vm.current)
        advanceTimeBy(NotesViewModel.FEEDBACK_MS + 1L)
    }

    /** Places every note of the phrase right, then lets the whole phrase play back and the next one appear. */
    private fun TestScope.answerPhrase(vm: NotesViewModel) {
        repeat(Phrases.MAX_LENGTH) { if (!vm.state.value.solved) vm.onNoteTapped(vm.current) }
        check(vm.state.value.solved) { "the phrase was not finished" }
        advanceTimeBy(vm.state.value.phrase.size * (NotesViewModel.NOTE_MS + NotesViewModel.GAP_MS) + 1L)
    }

    /** Places the phrase with one wrong tap on the way, then lets the playback pass. */
    private fun TestScope.answerPhraseWithAMiss(vm: NotesViewModel) {
        vm.onNoteTapped(vm.otherNote())
        answerPhrase(vm)
    }

    @Test
    fun `first note comes from the practice set`() {
        assertTrue(viewModel().current in StaffNotes.DEFAULT_PRACTICE)
    }

    @Test
    fun `stored practice notes are restored and sanitized`() {
        settings.practiceNotes = setOf(69, 61, 96)
        val vm = viewModel()
        assertEquals(setOf(69), vm.state.value.practiceNotes)
        assertEquals(69, vm.current)
    }

    @Test
    fun `the right tap turns green, sounds the note and moves on`() = runTest(dispatcher) {
        val vm = viewModel()
        val asked = vm.current
        vm.onNoteTapped(asked)
        assertTrue(vm.state.value.solved)
        assertEquals(listOf(true), vm.state.value.results)
        assertEquals(1, vm.state.value.streak)
        assertEquals(
            FakeReferenceTonePlayer.Played(
                NoteMath.midiToFrequency(asked),
                NotesViewModel.FEEDBACK_MS,
                TONE_VOLUME,
            ),
            tonePlayer.played.single(),
        )

        advanceTimeBy(NotesViewModel.FEEDBACK_MS + 1L)
        assertFalse(vm.state.value.solved)
        assertTrue(vm.state.value.wrongTaps.isEmpty())
        assertEquals(1, tonePlayer.stopCount)
        assertNotEquals(asked, vm.current)
        assertTrue(vm.current in StaffNotes.DEFAULT_PRACTICE)
    }

    @Test
    fun `a wrong tap is marked, resets the streak and the note stays`() = runTest(dispatcher) {
        val vm = viewModel()
        answerRight(vm)
        assertEquals(1, vm.state.value.streak)

        val asked = vm.current
        val wrong = vm.otherNote()
        vm.onNoteTapped(wrong)
        assertEquals(setOf(wrong), vm.state.value.wrongTaps)
        assertEquals(wrong, vm.state.value.lastWrongTap)
        assertEquals(0, vm.state.value.streak)
        assertEquals(2, vm.state.value.taps)
        assertEquals(asked, vm.current)
        assertFalse(vm.state.value.solved)
        assertEquals(1, tonePlayer.played.size) // nothing new sounded

        vm.onNoteTapped(asked)
        assertEquals(listOf(true, false), vm.state.value.results)
        assertTrue(vm.state.value.solved)
        assertEquals(0, vm.state.value.streak)

        // The next note starts without crosses or an arrow.
        advanceTimeBy(NotesViewModel.FEEDBACK_MS.toLong() + 1)
        assertTrue(vm.state.value.wrongTaps.isEmpty())
        assertNull(vm.state.value.lastWrongTap)
    }

    @Test
    fun `taps while the note sounds are ignored`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onNoteTapped(vm.current)
        vm.onNoteTapped(vm.current)
        vm.onNoteTapped(vm.otherNote())
        assertEquals(1, vm.state.value.taps)
        assertEquals(1, vm.state.value.results.size)
        assertTrue(vm.state.value.wrongTaps.isEmpty())
    }

    @Test
    fun `ten notes end the round and play again starts fresh`() = runTest(dispatcher) {
        val vm = viewModel()
        repeat(NotesRound.ROUND_LENGTH) { answerRight(vm) }
        assertTrue(vm.state.value.roundOver)
        assertEquals(NotesRound.ROUND_LENGTH, vm.state.value.answered)
        assertFalse(vm.state.value.solved)

        val before = vm.state.value
        vm.onNoteTapped(vm.current)
        vm.onNoteTapped(vm.otherNote())
        assertEquals(before, vm.state.value)

        vm.resetRound()
        val fresh = vm.state.value
        assertFalse(fresh.roundOver)
        assertTrue(fresh.results.isEmpty())
        assertEquals(0, fresh.taps)
        assertEquals(0, fresh.streak)
        assertEquals(0, fresh.bestStreak)
        assertTrue(fresh.currentMidi in fresh.practiceNotes)
    }

    @Test
    fun `best streak survives a miss`() = runTest(dispatcher) {
        val vm = viewModel()
        repeat(3) { answerRight(vm) }
        vm.onNoteTapped(vm.otherNote())
        assertEquals(0, vm.state.value.streak)
        assertEquals(3, vm.state.value.bestStreak)
    }

    @Test
    fun `a single practice note repeats`() = runTest(dispatcher) {
        val vm = viewModel()
        (StaffNotes.DEFAULT_PRACTICE - 60).forEach(vm::togglePracticeNote)
        assertEquals(setOf(60), vm.state.value.practiceNotes)
        repeat(3) {
            assertEquals(60, vm.current)
            answerRight(vm)
        }
        assertEquals(60, vm.current)
    }

    @Test
    fun `switching off the current note draws another`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onNoteTapped(vm.otherNote()) // a wrong tap on it so far
        val asked = vm.current
        vm.togglePracticeNote(asked)
        assertFalse(asked in vm.state.value.practiceNotes)
        assertTrue(vm.current in vm.state.value.practiceNotes)
        assertEquals(vm.state.value.practiceNotes, settings.practiceNotes)
        assertTrue(vm.state.value.wrongTaps.isEmpty())
    }

    @Test
    fun `the last note cannot be switched off and nothing resets`() = runTest(dispatcher) {
        val vm = viewModel()
        (StaffNotes.DEFAULT_PRACTICE - 60).forEach(vm::togglePracticeNote)
        answerRight(vm)
        val before = vm.state.value
        vm.togglePracticeNote(60)
        assertEquals(before, vm.state.value)
        assertEquals(setOf(60), settings.practiceNotes)
    }

    @Test
    fun `switching on a note is saved`() {
        val vm = viewModel()
        vm.togglePracticeNote(84)
        assertTrue(84 in vm.state.value.practiceNotes)
        assertTrue(84 in settings.practiceNotes)
        val before = vm.state.value
        vm.togglePracticeNote(61) // not a note the staff offers
        assertEquals(before, vm.state.value)
    }

    @Test
    fun `the next note is never the one just asked`() = runTest(dispatcher) {
        val vm = viewModel()
        (StaffNotes.DEFAULT_PRACTICE - setOf(60, 62)).forEach(vm::togglePracticeNote)
        repeat(20) {
            val asked = vm.current
            answerRight(vm)
            if (vm.state.value.roundOver) vm.resetRound() else assertNotEquals(asked, vm.current)
        }
    }

    @Test
    fun `stopSound silences the tone and hands the player over`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onNoteTapped(vm.current)
        vm.stopSound()
        assertEquals(1, tonePlayer.stopCount)

        // The tuner may be sounding its own note by now on the shared player: the pending
        // feedback must still move the game on, but must not stop a tone it no longer owns.
        advanceTimeBy(NotesViewModel.FEEDBACK_MS + 1L)
        assertEquals(1, tonePlayer.stopCount)
        assertFalse(vm.state.value.solved)
        assertEquals(1, vm.state.value.answered)
    }

    @Test
    fun `play again mid-feedback drops the old feedback`() = runTest(dispatcher) {
        val vm = viewModel()
        repeat(NotesRound.ROUND_LENGTH - 1) { answerRight(vm) }
        vm.onNoteTapped(vm.current) // the tenth, still sounding
        val stopsBefore = tonePlayer.stopCount
        vm.resetRound()
        val first = vm.current
        assertEquals(stopsBefore + 1, tonePlayer.stopCount)

        advanceTimeBy(NotesViewModel.FEEDBACK_MS + 1L)
        assertEquals(first, vm.current) // the old job did not skip the new round's first note
        assertEquals(0, vm.state.value.answered)
        assertEquals(stopsBefore + 1, tonePlayer.stopCount)
    }

    @Test
    fun `changing the chosen notes resets the round`() = runTest(dispatcher) {
        val vm = viewModel()
        answerRight(vm)
        answerRight(vm)
        vm.onNoteTapped(vm.otherNote()) // a wrong tap pending on the third note

        vm.togglePracticeNote(84)
        val fresh = vm.state.value
        assertTrue(84 in fresh.practiceNotes)
        assertTrue(fresh.results.isEmpty())
        assertEquals(0, fresh.taps)
        assertEquals(0, fresh.streak)
        assertEquals(0, fresh.bestStreak)
        assertTrue(fresh.wrongTaps.isEmpty())
        assertFalse(fresh.roundOver)
        assertTrue(fresh.currentMidi in fresh.practiceNotes)
    }

    @Test
    fun `changing the chosen notes mid-feedback resets at once`() = runTest(dispatcher) {
        val vm = viewModel()
        val asked = vm.current
        vm.onNoteTapped(asked) // sounding
        val stopsBefore = tonePlayer.stopCount

        vm.togglePracticeNote(asked)
        assertFalse(vm.state.value.solved)
        assertFalse(asked in vm.state.value.practiceNotes)
        assertTrue(vm.current in vm.state.value.practiceNotes)
        assertEquals(stopsBefore + 1, tonePlayer.stopCount)

        advanceTimeBy(NotesViewModel.FEEDBACK_MS + 1L) // the old feedback job must be gone
        assertEquals(0, vm.state.value.answered)
        assertEquals(stopsBefore + 1, tonePlayer.stopCount)
    }

    @Test
    fun `the note sound can be switched off and is remembered`() = runTest(dispatcher) {
        val vm = viewModel()
        assertTrue(vm.state.value.soundOn)
        vm.toggleSound()
        assertFalse(vm.state.value.soundOn)
        assertFalse(settings.noteSound)

        vm.onNoteTapped(vm.current) // right, but silent
        assertTrue(vm.state.value.solved)
        assertTrue(tonePlayer.played.isEmpty())
        advanceTimeBy(NotesViewModel.FEEDBACK_MS + 1L)
        assertEquals(1, vm.state.value.answered) // the game still moves on
        assertEquals(0, tonePlayer.stopCount) // nothing was sounding, nothing to stop

        vm.toggleSound()
        assertTrue(vm.state.value.soundOn)
        assertTrue(settings.noteSound)
        vm.onNoteTapped(vm.current)
        assertEquals(1, tonePlayer.played.size)
    }

    @Test
    fun `stored sound setting is restored`() {
        settings.noteSound = false
        assertFalse(viewModel().state.value.soundOn)
    }

    @Test
    fun `the round's time runs from the first tap to the last note found`() = runTest(dispatcher) {
        val vm = viewModel()
        assertNull(vm.state.value.roundMillis)
        clock.now = 20_000L
        vm.onNoteTapped(vm.otherNote()) // a wrong first tap starts the clock too
        clock.now += 3_000L
        answerRight(vm) // note 1 found 3 s in; the feedback delay is dispatcher time, not clock time
        repeat(NotesRound.ROUND_LENGTH - 2) {
            clock.now += 3_000L
            answerRight(vm)
        }
        assertNull(vm.state.value.roundMillis) // nine answered
        clock.now += 3_000L
        vm.onNoteTapped(vm.current) // the tenth, found 30 s after the first tap
        assertEquals(30_000L, vm.state.value.roundMillis)
        advanceTimeBy(NotesViewModel.FEEDBACK_MS + 1L)
        assertTrue(vm.state.value.roundOver)
        assertEquals(30_000L, vm.state.value.roundMillis)

        vm.resetRound()
        assertNull(vm.state.value.roundMillis)
        clock.now = 100_000L
        answerRight(vm)
        clock.now += 5_000L
        repeat(NotesRound.ROUND_LENGTH - 1) { answerRight(vm) }
        assertEquals(5_000L, vm.state.value.roundMillis) // the new round timed from its own first tap
    }

    @Test
    fun `play again never opens with the note just answered`() = runTest(dispatcher) {
        val vm = viewModel()
        (StaffNotes.DEFAULT_PRACTICE - setOf(60, 62)).forEach(vm::togglePracticeNote)
        repeat(10) {
            repeat(NotesRound.ROUND_LENGTH) { answerRight(vm) }
            assertTrue(vm.state.value.roundOver)
            val last = vm.current
            vm.resetRound()
            assertNotEquals(last, vm.current)
        }
    }

    @Test
    fun `the stored game is restored and a change is saved and resets the round`() = runTest(dispatcher) {
        settings.notesGame = NotesGame.HEAR
        val vm = viewModel()
        assertTrue(vm.state.value.hearing)
        assertEquals(2, vm.state.value.phrase.size)
        assertEquals(1, vm.state.value.placed)

        answerPhrase(vm)
        assertEquals(1, vm.state.value.results.size)

        vm.setGame(NotesGame.FIND)
        assertEquals(NotesGame.FIND, settings.notesGame)
        assertFalse(vm.state.value.hearing)
        assertTrue(vm.state.value.phrase.isEmpty())
        assertTrue(vm.state.value.results.isEmpty())
        assertTrue(vm.current in vm.state.value.practiceNotes)
    }

    @Test
    fun `a hear round opens with two notes, the first given`() {
        val vm = hearing()
        val state = vm.state.value
        assertEquals(NotesGame.HEAR, settings.notesGame)
        assertEquals(2, state.phrase.size)
        assertEquals(1, state.placed)
        assertEquals(state.phrase[1], vm.current)
        assertTrue(state.phrase.first() in StaffNotes.DEFAULT_PRACTICE)
        assertEquals(Phrases.MIN_LENGTH, state.phraseLength)
        assertTrue(state.phraseClean)
        assertFalse(state.solved)
        assertFalse(state.phrasePlaying)
        assertTrue(tonePlayer.played.isEmpty()) // opening a round plays nothing by itself
    }

    @Test
    fun `placing the next note moves the column on at once`() = runTest(dispatcher) {
        val vm = hearing()
        answerPhrase(vm) // clean, so the next phrase has three notes
        assertEquals(3, vm.state.value.phrase.size)

        val phrase = vm.state.value.phrase
        vm.onNoteTapped(phrase[1])
        val state = vm.state.value
        assertEquals(2, state.placed)
        assertEquals(phrase[2], vm.current)
        assertFalse(state.solved)
        assertEquals(listOf(true, true), state.results)
        assertEquals(2, state.streak)
    }

    @Test
    fun `a wrong tap in hear is marked, spoils the phrase and the note stays`() = runTest(dispatcher) {
        val vm = hearing()
        answerPhrase(vm)
        val phrase = vm.state.value.phrase

        val wrong = vm.otherNote()
        vm.onNoteTapped(wrong)
        var state = vm.state.value
        assertEquals(setOf(wrong), state.wrongTaps)
        assertEquals(wrong, state.lastWrongTap)
        assertEquals(0, state.streak)
        assertFalse(state.phraseClean)
        assertEquals(1, state.placed)
        assertEquals(phrase[1], vm.current)

        vm.onNoteTapped(phrase[1]) // found at last, not on the first try
        state = vm.state.value
        assertEquals(2, state.placed)
        assertTrue(state.wrongTaps.isEmpty())
        assertNull(state.lastWrongTap)
        assertEquals(listOf(true, false), state.results)
        assertFalse(state.phraseClean) // the phrase stays spoiled to its end
    }

    @Test
    fun `the last note of a clean phrase lengthens the next one`() = runTest(dispatcher) {
        val vm = hearing()
        answerPhrase(vm)
        assertEquals(3, vm.state.value.phrase.size)
        answerPhrase(vm)
        assertEquals(4, vm.state.value.phrase.size)
        answerPhrase(vm)
        assertEquals(4, vm.state.value.phrase.size)
    }

    @Test
    fun `a miss shortens the next phrase, never under two`() = runTest(dispatcher) {
        val vm = hearing()
        answerPhraseWithAMiss(vm) // a miss in the shortest phrase
        assertEquals(2, vm.state.value.phrase.size)

        answerPhrase(vm)
        answerPhrase(vm)
        assertEquals(4, vm.state.value.phrase.size)
        answerPhraseWithAMiss(vm)
        assertEquals(3, vm.state.value.phrase.size)
        answerPhraseWithAMiss(vm)
        assertEquals(2, vm.state.value.phrase.size)
        assertTrue(vm.state.value.phrase.size >= Phrases.MIN_LENGTH)
    }

    @Test
    fun `the last phrase is cut to what is left`() = runTest(dispatcher) {
        val vm = hearing()
        repeat(3) { answerPhrase(vm) } // 1 + 2 + 3 answered, the staircase at four
        assertEquals(6, vm.state.value.answered)
        assertEquals(4, vm.state.value.phrase.size)

        answerPhrase(vm) // three more: nine in all
        assertEquals(9, vm.state.value.answered)
        assertEquals(Phrases.lengthFor(4, NotesRound.ROUND_LENGTH - 9), vm.state.value.phrase.size)
        assertEquals(2, vm.state.value.phrase.size)
        assertFalse(vm.state.value.roundOver)

        answerPhrase(vm)
        assertTrue(vm.state.value.roundOver)
    }

    @Test
    fun `ten answered notes end the round in hear too`() = runTest(dispatcher) {
        val vm = hearing()
        var phrases = 0
        while (!vm.state.value.roundOver && phrases++ < NotesRound.ROUND_LENGTH) answerPhrase(vm)
        val state = vm.state.value
        assertTrue(state.roundOver)
        assertEquals(NotesRound.ROUND_LENGTH, state.results.size)
        assertFalse(state.solved)
        assertFalse(state.phrasePlaying)
        assertNotNull(state.roundMillis)

        val round = logger.events("notes.round").single()
        assertEquals("HEAR", round["game"])
        assertEquals(StaffNotes.DEFAULT_PRACTICE.size, round["notes"])
        assertEquals(10, round["score"])
        assertEquals(10, round["bestStreak"])
        assertEquals(state.roundMillis, round["millis"])
    }

    @Test
    fun `a find round is logged when it ends too`() = runTest(dispatcher) {
        val vm = viewModel()
        repeat(NotesRound.ROUND_LENGTH) { answerRight(vm) }
        val round = logger.events("notes.round").single()
        assertEquals("FIND", round["game"])
        assertEquals(10, round["score"])
    }

    @Test
    fun `each phrase is logged with its notes`() = runTest(dispatcher) {
        val vm = hearing()
        val first = logger.events("notes.phrase").first()
        assertEquals(2, first["length"])
        assertEquals(vm.state.value.phrase.joinToString(" "), first["notes"])
        assertEquals(vm.state.value.phraseId, first["phraseId"])

        answerPhrase(vm)
        val second = logger.events("notes.phrase")[1]
        assertEquals(3, second["length"])
        assertEquals(vm.state.value.phrase.joinToString(" "), second["notes"])
        assertEquals(vm.state.value.phraseId, second["phraseId"])
        assertEquals(setOf("FA.Notes"), logger.events("notes.phrase").map { it.tag }.toSet())
    }

    /** A phrase drawn and never shown (the picker's toggle, the tab left) is told apart in the log by its id. */
    @Test
    fun `a phrase drawn by a reset is logged with its own id`() = runTest(dispatcher) {
        val vm = hearing()
        val shown = vm.state.value.phraseId
        vm.resetRound()
        val ids = logger.events("notes.phrase").map { it["phraseId"] }
        assertEquals(listOf(shown, shown + 1), ids.takeLast(2))
        assertEquals(vm.state.value.phraseId, ids.last())
    }

    @Test
    fun `taps are ignored while the phrase plays, then the next phrase follows`() = runTest(dispatcher) {
        val vm = hearing()
        val first = vm.state.value
        vm.onNoteTapped(vm.current) // the last note of the phrase
        advanceTimeBy(1)
        assertTrue(vm.state.value.solved)
        assertTrue(vm.state.value.phrasePlaying)

        val playing = vm.state.value
        vm.onNoteTapped(vm.current)
        vm.onNoteTapped(vm.otherNote())
        assertEquals(playing, vm.state.value)

        advanceTimeBy(first.phrase.size * (NotesViewModel.NOTE_MS + NotesViewModel.GAP_MS) + 1L)
        val next = vm.state.value
        assertFalse(next.phrasePlaying)
        assertFalse(next.solved)
        assertEquals(first.phraseId + 1, next.phraseId)
        assertEquals(1, next.placed)
        assertEquals(next.phrase[1], vm.current)
    }

    @Test
    fun `a phrase never opens with the note the last one opened with`() = runTest(dispatcher) {
        val vm = viewModel()
        (StaffNotes.DEFAULT_PRACTICE - setOf(60, 62, 64)).forEach(vm::togglePracticeNote) // three notes left, C D E: a repeat would show
        vm.setGame(NotesGame.HEAR)
        repeat(12) {
            val opened = vm.state.value.phrase.first()
            answerPhrase(vm)
            if (vm.state.value.roundOver) vm.resetRound() // a new round does not open with it either
            assertNotEquals(opened, vm.state.value.phrase.first())
        }
    }

    @Test
    fun `a fresh hear round starts at two notes again and keeps the game`() = runTest(dispatcher) {
        val vm = hearing()
        repeat(3) { answerPhrase(vm) }
        assertEquals(4, vm.state.value.phrase.size)
        val phraseId = vm.state.value.phraseId

        vm.resetRound() // what the tab bar does when the tab is left
        var state = vm.state.value
        assertTrue(state.hearing)
        assertEquals(NotesGame.HEAR, settings.notesGame)
        assertEquals(2, state.phrase.size)
        assertEquals(Phrases.MIN_LENGTH, state.phraseLength)
        assertTrue(state.phraseClean)
        assertTrue(state.results.isEmpty())
        assertEquals(phraseId + 1, state.phraseId)

        answerPhrase(vm)
        vm.resetRound()
        state = vm.state.value
        assertTrue(state.hearing)
        assertEquals(2, state.phrase.size)

        vm.togglePracticeNote(84) // a change of notes starts over in the same game
        state = vm.state.value
        assertTrue(state.hearing)
        assertEquals(2, state.phrase.size)
        assertTrue(state.phrase.all { it in state.practiceNotes })
    }

    @Test
    fun `changing the game mid-playback drops the old playback`() = runTest(dispatcher) {
        val vm = hearing()
        vm.onNoteTapped(vm.current)
        advanceTimeBy(1)
        assertTrue(vm.state.value.phrasePlaying)

        vm.setGame(NotesGame.FIND)
        assertFalse(vm.state.value.phrasePlaying)
        assertFalse(vm.state.value.solved)
        val fresh = vm.state.value

        advanceTimeBy(10 * (NotesViewModel.NOTE_MS + NotesViewModel.GAP_MS) + 1L)
        assertEquals(fresh, vm.state.value) // the old playback did not move the new round on
    }

    @Test
    fun `choosing the game already open keeps the round`() = runTest(dispatcher) {
        val find = viewModel()
        answerRight(find)
        val findBefore = find.state.value
        find.setGame(NotesGame.FIND)
        assertEquals(findBefore, find.state.value)

        val hear = hearing()
        answerPhrase(hear)
        val hearBefore = hear.state.value
        hear.setGame(NotesGame.HEAR)
        assertEquals(hearBefore, hear.state.value)
    }

    @Test
    fun `the phrase counter goes on across a change of game`() = runTest(dispatcher) {
        val vm = hearing()
        val hear = vm.state.value.phraseId
        vm.setGame(NotesGame.FIND)
        assertEquals(hear, vm.state.value.phraseId)
        vm.setGame(NotesGame.HEAR)
        assertEquals(hear + 1, vm.state.value.phraseId)
    }

    /** How long the phrase on screen takes to play when it starts at once (hear again, or after the last note), in milliseconds. */
    private val NotesViewModel.playbackMillis: Long
        get() = state.value.phrase.size * (NotesViewModel.NOTE_MS + NotesViewModel.GAP_MS).toLong()

    /** How long a phrase that has just come on screen (`onPhraseShown`) takes: the breath, then the playback. */
    private val NotesViewModel.shownMillis: Long
        get() = NotesViewModel.LEAD_MS + playbackMillis

    /** The tones the player is asked for when [phrase] is heard. */
    private fun tonesOf(phrase: List<Int>) = phrase.map {
        FakeReferenceTonePlayer.Played(
            NoteMath.midiToFrequency(it),
            NotesViewModel.NOTE_MS,
            TONE_VOLUME,
        )
    }

    @Test
    fun `a fresh phrase waits to be shown before it plays`() = runTest(dispatcher) {
        val vm = hearing()
        val phrase = vm.state.value.phrase
        val length = vm.shownMillis
        advanceTimeBy(length) // time alone sounds nothing
        assertTrue(tonePlayer.played.isEmpty())
        assertFalse(vm.state.value.phrasePlaying)

        vm.onPhraseShown()
        assertTrue(vm.state.value.phrasePlaying)
        advanceTimeBy(length - 1)
        assertTrue(vm.state.value.phrasePlaying)
        advanceTimeBy(2)
        assertFalse(vm.state.value.phrasePlaying)
        assertEquals(tonesOf(phrase), tonePlayer.played)
        assertEquals(1, tonePlayer.stopCount)
    }

    @Test
    fun `showing the same phrase twice plays it once`() = runTest(dispatcher) {
        val vm = hearing()
        val phrase = vm.state.value.phrase
        vm.onPhraseShown()
        vm.onPhraseShown() // the same instant
        advanceTimeBy(vm.shownMillis + 1)
        vm.onPhraseShown() // the screen came back to the same phrase
        advanceTimeBy(vm.playbackMillis + 1)
        assertEquals(tonesOf(phrase), tonePlayer.played)
        assertFalse(vm.state.value.phrasePlaying)
    }

    @Test
    fun `hear again plays the phrase once more`() = runTest(dispatcher) {
        val vm = hearing()
        val phrase = vm.state.value.phrase
        vm.onPhraseShown()
        advanceTimeBy(vm.shownMillis + 1)
        assertEquals(phrase.size, tonePlayer.played.size)

        vm.hearAgain()
        assertTrue(vm.state.value.phrasePlaying)
        advanceTimeBy(vm.playbackMillis + 1)
        assertEquals(tonesOf(phrase + phrase), tonePlayer.played)
        assertFalse(vm.state.value.phrasePlaying)
        assertEquals(2, tonePlayer.stopCount)
    }

    @Test
    fun `hear again in the middle of the phrase starts it over`() = runTest(dispatcher) {
        val vm = hearing()
        val phrase = vm.state.value.phrase
        vm.onPhraseShown()
        advanceTimeBy(NotesViewModel.LEAD_MS + NotesViewModel.NOTE_MS + NotesViewModel.GAP_MS + 1L) // the second tone has sounded
        assertEquals(2, tonePlayer.played.size)

        vm.hearAgain()
        advanceTimeBy(vm.playbackMillis + 1)
        assertEquals(tonesOf(phrase.take(2) + phrase), tonePlayer.played) // the old one stopped where it was
        assertFalse(vm.state.value.phrasePlaying)
    }

    @Test
    fun `hear again does nothing in find`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.hearAgain()
        vm.onPhraseShown()
        advanceTimeBy(2000)
        assertTrue(tonePlayer.played.isEmpty())
        assertFalse(vm.state.value.phrasePlaying)
    }

    @Test
    fun `the phrase sounds with the sound switch off, the placed note does not`() = runTest(dispatcher) {
        settings.noteSound = false
        val vm = hearing()
        val first = vm.state.value.phrase
        vm.onPhraseShown()
        advanceTimeBy(vm.shownMillis + 1)
        assertEquals(tonesOf(first), tonePlayer.played)

        answerPhrase(vm) // the phrase just answered sounds again
        val heard = tonePlayer.played.size
        val phrase = vm.state.value.phrase
        vm.onNoteTapped(phrase[1]) // right, not the last of three
        assertEquals(2, vm.state.value.placed)
        assertEquals(heard, tonePlayer.played.size)
    }

    @Test
    fun `a right tap while the phrase plays is ignored`() = runTest(dispatcher) {
        val vm = hearing()
        fun assertTapsAreIgnored() {
            val playing = vm.state.value
            vm.onNoteTapped(vm.current)
            vm.onNoteTapped(vm.otherNote())
            assertTrue(vm.state.value.results.isEmpty())
            assertEquals(playing, vm.state.value)
        }
        vm.onPhraseShown()
        advanceTimeBy(1)
        assertTrue(vm.state.value.phrasePlaying)
        assertNull(vm.state.value.sounding) // the breath: the phrase plays, no note yet
        assertTapsAreIgnored()

        advanceTimeBy(NotesViewModel.LEAD_MS + 1L)
        assertEquals(0, vm.state.value.sounding) // a note is sounding now
        assertTrue(vm.state.value.phrasePlaying)
        assertTapsAreIgnored()
    }

    @Test
    fun `the finished phrase plays back and then the next phrase comes`() = runTest(dispatcher) {
        val vm = hearing()
        val first = vm.state.value
        vm.onNoteTapped(vm.current) // the last note of a phrase of two
        assertTrue(vm.state.value.solved)
        assertTrue(vm.state.value.phrasePlaying)

        advanceTimeBy(vm.playbackMillis - 1)
        assertEquals(first.phraseId, vm.state.value.phraseId)
        assertTrue(vm.state.value.phrasePlaying)
        assertEquals(tonesOf(first.phrase), tonePlayer.played) // the placed notes were not sounded on top of it

        advanceTimeBy(2)
        assertEquals(first.phraseId + 1, vm.state.value.phraseId)
        assertFalse(vm.state.value.phrasePlaying)
        assertFalse(vm.state.value.solved)
        assertEquals(1, tonePlayer.stopCount)
    }

    @Test
    fun `hear again while the answer plays restarts it and the next phrase still comes once`() = runTest(dispatcher) {
        val vm = hearing()
        val first = vm.state.value
        vm.onNoteTapped(vm.current)
        advanceTimeBy(vm.playbackMillis - 100)

        vm.hearAgain()
        advanceTimeBy(200) // where the first playback would have ended
        assertEquals(first.phraseId, vm.state.value.phraseId)
        assertTrue(vm.state.value.phrasePlaying)

        advanceTimeBy(vm.playbackMillis)
        assertEquals(first.phraseId + 1, vm.state.value.phraseId)
        assertFalse(vm.state.value.phrasePlaying)

        advanceTimeBy(5000)
        assertEquals(first.phraseId + 1, vm.state.value.phraseId) // the restarted one was the only one to move on
    }

    @Test
    fun `leaving the tab stops the phrase`() = runTest(dispatcher) {
        val vm = hearing()
        vm.onPhraseShown()
        advanceTimeBy(NotesViewModel.LEAD_MS + 1L) // the breath is over, the first tone sounds
        val heard = tonePlayer.played.size
        assertEquals(1, heard)
        val stops = tonePlayer.stopCount

        vm.resetRound()
        assertEquals(stops + 1, tonePlayer.stopCount)
        assertFalse(vm.state.value.phrasePlaying)

        advanceTimeBy(2000)
        assertEquals(heard, tonePlayer.played.size)
        assertEquals(stops + 1, tonePlayer.stopCount)
    }

    @Test
    fun `changing the notes mid-playback stops it and draws a new phrase`() = runTest(dispatcher) {
        val vm = hearing()
        vm.onPhraseShown()
        advanceTimeBy(NotesViewModel.LEAD_MS + 1L) // the breath is over, the first tone sounds
        val heard = tonePlayer.played.size
        assertEquals(1, heard)
        val stops = tonePlayer.stopCount

        vm.togglePracticeNote(69)
        assertEquals(stops + 1, tonePlayer.stopCount)
        val state = vm.state.value
        assertFalse(state.phrasePlaying)
        assertEquals(2, state.phrase.size)
        assertTrue(state.phrase.all { it in state.practiceNotes })
        assertTrue(69 in state.practiceNotes)
        assertTrue(state.results.isEmpty())

        advanceTimeBy(2000)
        assertEquals(heard, tonePlayer.played.size) // no tone of the old phrase
        assertEquals(state, vm.state.value)
    }

    @Test
    fun `the screen's stop sound ends the playback`() = runTest(dispatcher) {
        val vm = hearing()
        vm.onPhraseShown()
        advanceTimeBy(NotesViewModel.LEAD_MS + 1L) // the breath is over, the first tone sounds
        val heard = tonePlayer.played.size
        assertTrue(vm.state.value.phrasePlaying)
        assertEquals(1, heard)
        val stops = tonePlayer.stopCount

        vm.stopSound()
        assertEquals(stops + 1, tonePlayer.stopCount)
        assertFalse(vm.state.value.phrasePlaying)

        advanceTimeBy(2000)
        assertEquals(heard, tonePlayer.played.size)
        assertEquals(stops + 1, tonePlayer.stopCount)
    }

    @Test
    fun `the screen going away mid-answer moves the round on rather than strand it`() = runTest(dispatcher) {
        val vm = hearing()
        val first = vm.state.value
        vm.onNoteTapped(vm.current)
        advanceTimeBy(1)
        assertTrue(vm.state.value.solved)

        vm.stopSound()
        val state = vm.state.value
        assertFalse(state.solved)
        assertFalse(state.phrasePlaying)
        assertEquals(first.phraseId + 1, state.phraseId)

        advanceTimeBy(2000)
        assertEquals(state, vm.state.value) // the cut playback did not move it on a second time
        assertEquals(1, tonePlayer.played.size)
    }

    @Test
    fun `a placed note is released after its length without locking the staff`() = runTest(dispatcher) {
        val vm = hearing()
        answerPhrase(vm) // the next phrase has three notes
        val phrase = vm.state.value.phrase
        val heard = tonePlayer.played.size
        val stops = tonePlayer.stopCount

        vm.onNoteTapped(phrase[1])
        assertEquals(
            FakeReferenceTonePlayer.Played(
                NoteMath.midiToFrequency(phrase[1]),
                NotesViewModel.NOTE_MS,
                TONE_VOLUME,
            ),
            tonePlayer.played.last(),
        )
        assertEquals(heard + 1, tonePlayer.played.size)
        assertFalse(vm.state.value.solved) // the next column is live at once
        assertFalse(vm.state.value.phrasePlaying)
        assertEquals(stops, tonePlayer.stopCount)

        advanceTimeBy(NotesViewModel.NOTE_MS - 1L)
        assertEquals(stops, tonePlayer.stopCount)
        advanceTimeBy(2)
        assertEquals(stops + 1, tonePlayer.stopCount)
    }

    @Test
    fun `a placed note's release does not cut the phrase that follows it`() = runTest(dispatcher) {
        val vm = hearing()
        answerPhrase(vm)
        val phrase = vm.state.value.phrase
        vm.onNoteTapped(phrase[1])
        advanceTimeBy(100)
        vm.onNoteTapped(vm.current) // the last note, soon after: the phrase plays
        val stops = tonePlayer.stopCount

        advanceTimeBy(NotesViewModel.NOTE_MS + 50L) // where the placed note's release would have fallen
        assertEquals(stops, tonePlayer.stopCount)
        assertTrue(vm.state.value.phrasePlaying)

        advanceTimeBy(vm.playbackMillis)
        assertEquals(stops + 1, tonePlayer.stopCount)
        assertFalse(vm.state.value.phrasePlaying)
    }

    @Test
    fun `switching the sound off mid-phrase does not cut the phrase`() = runTest(dispatcher) {
        val vm = hearing()
        val phrase = vm.state.value.phrase
        vm.onPhraseShown()
        advanceTimeBy(NotesViewModel.LEAD_MS + 1L) // the breath is over, the first tone sounds
        assertEquals(1, tonePlayer.played.size)
        val stops = tonePlayer.stopCount

        vm.toggleSound()
        assertEquals(stops, tonePlayer.stopCount)
        assertTrue(vm.state.value.phrasePlaying)

        advanceTimeBy(vm.playbackMillis)
        assertEquals(tonesOf(phrase), tonePlayer.played)
    }

    @Test
    fun `a phrase that comes on screen waits a breath, then sounds each note for its length`() = runTest(dispatcher) {
        val vm = hearing()
        val phrase = vm.state.value.phrase
        assertEquals(2, phrase.size)
        val started = testScheduler.currentTime

        vm.onPhraseShown()
        runCurrent()
        assertTrue(vm.state.value.phrasePlaying)
        assertNull(vm.state.value.sounding)
        assertTrue(tonePlayer.played.isEmpty())

        advanceTimeBy(NotesViewModel.LEAD_MS - 1L)
        runCurrent()
        assertNull(vm.state.value.sounding) // the breath is not over yet
        assertTrue(tonePlayer.played.isEmpty())

        advanceTimeBy(1)
        runCurrent()
        assertEquals(tonesOf(phrase.take(1)), tonePlayer.played) // one tone, NOTE_MS long
        assertEquals(0, vm.state.value.sounding)

        advanceTimeBy(NotesViewModel.NOTE_MS - 1L)
        runCurrent()
        assertEquals(0, vm.state.value.sounding) // still the first note
        advanceTimeBy(1)
        runCurrent()
        assertNull(vm.state.value.sounding) // it has played out; the gap
        assertEquals(1, tonePlayer.played.size)

        advanceTimeBy(NotesViewModel.GAP_MS - 1L)
        runCurrent()
        assertNull(vm.state.value.sounding)
        assertEquals(1, tonePlayer.played.size)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(tonesOf(phrase), tonePlayer.played)
        assertEquals(1, vm.state.value.sounding)
        assertTrue(vm.state.value.phrasePlaying)

        advanceTimeBy((NotesViewModel.NOTE_MS + NotesViewModel.GAP_MS) - 1L)
        runCurrent()
        assertTrue(vm.state.value.phrasePlaying) // the last gap is still to pass
        advanceTimeBy(1)
        runCurrent()
        assertFalse(vm.state.value.phrasePlaying)
        assertNull(vm.state.value.sounding)
        assertEquals(vm.shownMillis, testScheduler.currentTime - started)
        assertEquals(1, tonePlayer.stopCount)
    }

    @Test
    fun `hear again starts at once`() = runTest(dispatcher) {
        val vm = hearing()
        val phrase = vm.state.value.phrase
        vm.onPhraseShown()
        advanceTimeBy(vm.shownMillis + 1)
        assertEquals(tonesOf(phrase), tonePlayer.played)
        assertFalse(vm.state.value.phrasePlaying)
        assertNull(vm.state.value.sounding)

        vm.hearAgain()
        runCurrent()
        assertEquals(tonesOf(phrase + phrase.take(1)), tonePlayer.played) // a new tone at once, no breath
        assertEquals(0, vm.state.value.sounding)
        assertTrue(vm.state.value.phrasePlaying)

        advanceTimeBy((NotesViewModel.NOTE_MS + NotesViewModel.GAP_MS).toLong())
        runCurrent()
        assertEquals(1, vm.state.value.sounding)
        vm.hearAgain() // cut in the middle of the second note: the old note goes dark at once
        // A window only this test sees: production's Main.immediate runs the job body inline, so no frame shows it dark.
        assertNull(vm.state.value.sounding)
        runCurrent()
        assertEquals(0, vm.state.value.sounding)
    }

    @Test
    fun `hear again during the breath starts at once and the breath sounds nothing later`() = runTest(dispatcher) {
        val vm = hearing()
        val phrase = vm.state.value.phrase
        vm.onPhraseShown()
        advanceTimeBy(NotesViewModel.LEAD_MS / 2L)
        assertTrue(tonePlayer.played.isEmpty())

        vm.hearAgain()
        runCurrent()
        assertEquals(tonesOf(phrase.take(1)), tonePlayer.played)
        assertEquals(0, vm.state.value.sounding)

        advanceTimeBy(vm.playbackMillis + NotesViewModel.LEAD_MS)
        assertEquals(tonesOf(phrase), tonePlayer.played) // the cut breath did not start a second playback
        assertFalse(vm.state.value.phrasePlaying)
        assertNull(vm.state.value.sounding)
    }

    @Test
    fun `the finished phrase lights its notes in turn with no breath first`() = runTest(dispatcher) {
        val vm = hearing()
        val phrase = vm.state.value.phrase
        assertEquals(2, phrase.size)
        vm.onNoteTapped(vm.current) // the last note of a phrase of two
        runCurrent()
        assertEquals(tonesOf(phrase.take(1)), tonePlayer.played) // a tone at once
        assertEquals(0, vm.state.value.sounding)

        advanceTimeBy((NotesViewModel.NOTE_MS + NotesViewModel.GAP_MS).toLong())
        runCurrent()
        assertEquals(tonesOf(phrase), tonePlayer.played)
        assertEquals(1, vm.state.value.sounding)
    }

    /** A view model on Hear whose first phrase was shown and whose first note is sounding now. */
    private fun TestScope.soundingFirstNote(): NotesViewModel {
        val vm = hearing()
        vm.onPhraseShown()
        advanceTimeBy(NotesViewModel.LEAD_MS.toLong())
        runCurrent()
        assertEquals(0, vm.state.value.sounding)
        return vm
    }

    @Test
    fun `a cut playback leaves no note lit`() = runTest(dispatcher) {
        val cuts = listOf<Pair<String, (NotesViewModel) -> Unit>>(
            "the tab left" to { it.resetRound() },
            "the screen gone" to { it.stopSound() },
            "other notes picked" to { it.togglePracticeNote(69) },
        )
        for ((name, cut) in cuts) {
            val vm = soundingFirstNote()
            cut(vm)
            assertNull(name, vm.state.value.sounding)
            advanceTimeBy(5_000)
            assertNull("$name, later", vm.state.value.sounding) // the cut playback lights nothing more
        }
    }

    @Test
    fun `the screen going away while the finished phrase plays leaves no note lit`() = runTest(dispatcher) {
        val vm = hearing()
        val first = vm.state.value
        vm.onNoteTapped(vm.current)
        runCurrent()
        assertEquals(0, vm.state.value.sounding)

        vm.stopSound() // the answer moves on at once
        assertEquals(first.phraseId + 1, vm.state.value.phraseId)
        assertNull(vm.state.value.sounding)

        // And the round's last phrase: the summary comes up with nothing lit.
        val last = hearing()
        while (last.state.value.answered + (last.state.value.phrase.size - last.state.value.placed) < NotesRound.ROUND_LENGTH) {
            answerPhrase(last)
        }
        repeat(Phrases.MAX_LENGTH) { if (!last.state.value.solved) last.onNoteTapped(last.current) }
        assertEquals(NotesRound.ROUND_LENGTH, last.state.value.answered)
        runCurrent()
        assertEquals(0, last.state.value.sounding)

        last.stopSound()
        assertTrue(last.state.value.roundOver)
        assertNull(last.state.value.sounding)
    }

    @Test
    fun `a placed note sounds without lighting anything`() = runTest(dispatcher) {
        val vm = hearing()
        answerPhrase(vm) // clean, so the next phrase has three notes
        val phrase = vm.state.value.phrase
        assertEquals(3, phrase.size)
        val heard = tonePlayer.played.size

        vm.onNoteTapped(phrase[1]) // right, not the last of three
        runCurrent()
        assertEquals(tonesOf(listOf(phrase[1])), tonePlayer.played.drop(heard)) // one tone of NOTE_MS
        assertNull(vm.state.value.sounding)

        advanceTimeBy(NotesViewModel.NOTE_MS + 1L)
        assertNull(vm.state.value.sounding)
    }

    /** A view model that asks only [midi], the book as stored holding [count] of it. */
    private fun asking(midi: Int, count: Int, game: NotesGame = NotesGame.FIND): NotesViewModel {
        settings.practiceNotes = setOf(midi)
        settings.noteProgress = NoteProgress(mapOf(midi to count))
        return viewModel().also { if (game == NotesGame.HEAR) it.setGame(game) }
    }

    @Test
    fun `stored progress is restored`() {
        settings.noteProgress = NoteProgress(mapOf(60 to 3))
        assertEquals(NoteProgress(mapOf(60 to 3)), viewModel().state.value.progress)
    }

    @Test
    fun `a first-try find grows the note and is saved`() = runTest(dispatcher) {
        val vm = viewModel()
        val asked = vm.current
        answerRight(vm)
        assertEquals(1, vm.state.value.progress.countOf(asked))
        assertEquals(1, settings.noteProgress.countOf(asked))
        assertTrue(asked in vm.state.value.grewThisRound)
        assertTrue(asked in vm.state.value.askedThisRound)
    }

    @Test
    fun `a miss costs the note asked one step, once`() = runTest(dispatcher) {
        val vm = asking(60, count = 3)
        StaffNotes.ALL.filter { it != 60 }.take(3).forEach(vm::onNoteTapped)
        assertEquals(2, vm.state.value.progress.countOf(60))
        assertEquals(2, settings.noteProgress.countOf(60)) // saved at the first miss

        vm.onNoteTapped(60)
        assertEquals(2, vm.state.value.progress.countOf(60)) // finding it late is not a find
        assertEquals(2, settings.noteProgress.countOf(60))
        assertTrue(60 !in vm.state.value.grewThisRound)
        assertTrue(60 in vm.state.value.askedThisRound) // asked all the same
    }

    @Test
    fun `a known note never loses its star`() = runTest(dispatcher) {
        val vm = asking(60, count = NoteProgress.KNOWN_AT)
        vm.onNoteTapped(vm.otherNote())
        assertEquals(NoteProgress.KNOWN_AT, vm.state.value.progress.countOf(60))
        assertEquals(NoteProgress.KNOWN_AT, settings.noteProgress.countOf(60))
        assertTrue(logger.events("notes.known").isEmpty())

        answerRight(vm)
        answerRight(vm) // and found at the first try, a known note stays where it is
        assertEquals(NoteProgress.KNOWN_AT, vm.state.value.progress.countOf(60))
        assertTrue(60 !in vm.state.value.grewThisRound)
        assertTrue(logger.events("notes.known").isEmpty())
    }

    @Test
    fun `reaching eight is logged once`() = runTest(dispatcher) {
        val vm = asking(60, count = NoteProgress.KNOWN_AT - 1)
        answerRight(vm)
        assertTrue(vm.state.value.progress.isKnown(60))
        val known = logger.events("notes.known").single()
        assertEquals('i', known.level)
        assertEquals(LogTags.NOTES, known.tag)
        assertEquals(60, known["midi"])

        answerRight(vm) // known already: nothing to log again
        assertEquals(1, logger.events("notes.known").size)
    }

    @Test
    fun `hear feeds the same book`() = runTest(dispatcher) {
        val vm = hearing()
        answerPhrase(vm) // the next phrase has three notes, so its first placement is not the last
        val placed = vm.current
        val before = vm.state.value.progress.countOf(placed)

        vm.onNoteTapped(placed)
        assertFalse(vm.state.value.solved)
        assertEquals(before + 1, vm.state.value.progress.countOf(placed))
        assertEquals(before + 1, settings.noteProgress.countOf(placed))
        assertTrue(placed in vm.state.value.grewThisRound)
        assertTrue(placed in vm.state.value.askedThisRound)
    }

    @Test
    fun `a miss in hear costs the note asked one step, once`() = runTest(dispatcher) {
        val vm = asking(60, count = 3, game = NotesGame.HEAR)
        assertEquals(60, vm.current)

        StaffNotes.ALL.filter { it != 60 }.take(2).forEach(vm::onNoteTapped)
        assertEquals(2, vm.state.value.progress.countOf(60))
        assertEquals(2, settings.noteProgress.countOf(60))

        vm.onNoteTapped(60)
        assertEquals(2, vm.state.value.progress.countOf(60))
        assertTrue(60 in vm.state.value.askedThisRound)
        assertTrue(60 !in vm.state.value.grewThisRound)
    }

    @Test
    fun `progress survives a new round, a new game and new notes`() = runTest(dispatcher) {
        val vm = viewModel()
        repeat(3) { answerRight(vm) }
        val progress = vm.state.value.progress
        assertTrue(progress.counts.isNotEmpty())
        assertTrue(vm.state.value.grewThisRound.isNotEmpty())
        assertTrue(vm.state.value.askedThisRound.isNotEmpty())

        vm.resetRound()
        assertEquals(progress, vm.state.value.progress)
        assertTrue(vm.state.value.grewThisRound.isEmpty())
        assertTrue(vm.state.value.askedThisRound.isEmpty())

        repeat(2) { answerRight(vm) }
        val later = vm.state.value.progress
        vm.setGame(NotesGame.HEAR)
        assertEquals(later, vm.state.value.progress)
        assertTrue(vm.state.value.grewThisRound.isEmpty())
        assertTrue(vm.state.value.askedThisRound.isEmpty())

        answerPhrase(vm)
        val heard = vm.state.value.progress
        assertTrue(vm.state.value.grewThisRound.isNotEmpty())
        vm.togglePracticeNote(69)
        assertEquals(heard, vm.state.value.progress)
        assertTrue(vm.state.value.grewThisRound.isEmpty())
        assertTrue(vm.state.value.askedThisRound.isEmpty())
        assertEquals(heard, settings.noteProgress)
    }

    @Test
    fun `a phrase cut while being heard plays again when the screen returns`() = runTest(dispatcher) {
        val vm = hearing()
        val phrase = vm.state.value.phrase
        vm.onPhraseShown()
        advanceTimeBy(NotesViewModel.LEAD_MS + 1L)
        assertEquals(1, tonePlayer.played.size) // one tone has sounded
        vm.stopSound()
        assertFalse(vm.state.value.phrasePlaying)

        vm.onPhraseShown() // the screen came back to the same phrase
        assertTrue(vm.state.value.phrasePlaying)
        advanceTimeBy(vm.shownMillis + 1)
        assertEquals(tonesOf(phrase.take(1) + phrase), tonePlayer.played)
        assertFalse(vm.state.value.phrasePlaying)
    }

    @Test
    fun `a phrase cut during its breath plays again when the screen returns`() = runTest(dispatcher) {
        val vm = hearing()
        val phrase = vm.state.value.phrase
        vm.onPhraseShown()
        advanceTimeBy(NotesViewModel.LEAD_MS - 1L)
        runCurrent()
        assertTrue(tonePlayer.played.isEmpty()) // still the breath: nothing has sounded
        vm.stopSound()
        assertFalse(vm.state.value.phrasePlaying)
        advanceTimeBy(5_000)
        assertTrue(tonePlayer.played.isEmpty()) // the cut breath sounds nothing later

        vm.onPhraseShown() // the screen came back to the same phrase
        assertTrue(vm.state.value.phrasePlaying)
        advanceTimeBy(NotesViewModel.LEAD_MS - 1L)
        runCurrent()
        assertTrue(tonePlayer.played.isEmpty()) // it waits its breath again
        advanceTimeBy(1)
        runCurrent()
        assertEquals(tonesOf(phrase.take(1)), tonePlayer.played)
        advanceTimeBy(vm.playbackMillis + 1)
        assertEquals(tonesOf(phrase), tonePlayer.played)
        assertFalse(vm.state.value.phrasePlaying)
    }

    @Test
    fun `two quick placements each sound whole`() = runTest(dispatcher) {
        val vm = hearing()
        answerPhrase(vm)
        answerPhrase(vm) // two clean phrases, so the staircase has reached four notes
        val phrase = vm.state.value.phrase
        assertEquals(4, phrase.size)
        val heard = tonePlayer.played.size
        val stops = tonePlayer.stopCount

        vm.onNoteTapped(phrase[1])
        advanceTimeBy(100)
        vm.onNoteTapped(phrase[2]) // neither is the last note: the second sounds 100 ms into the first
        assertEquals(tonesOf(phrase.subList(1, 3)), tonePlayer.played.drop(heard)) // both NOTE_MS long
        assertEquals(stops, tonePlayer.stopCount)

        advanceTimeBy(NotesViewModel.NOTE_MS - 1L) // past where the first note's release would have fallen (800 ms in)
        assertEquals(stops, tonePlayer.stopCount)
        advanceTimeBy(2) // the second note's own release, NOTE_MS after its tap
        assertEquals(stops + 1, tonePlayer.stopCount)
    }
}
