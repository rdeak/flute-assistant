// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.notes

import hr.bertlab.fluteassistant.domain.notes.StaffNotes
import hr.bertlab.fluteassistant.domain.tuner.NoteMath
import hr.bertlab.fluteassistant.fakes.FakeClock
import hr.bertlab.fluteassistant.fakes.FakeReferenceTonePlayer
import hr.bertlab.fluteassistant.fakes.FakeSettingsStore
import hr.bertlab.fluteassistant.ui.tuner.TunerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(seed: Int = 1) = NotesViewModel(settings, tonePlayer, clock, Random(seed))

    private val NotesViewModel.current: Int get() = state.value.currentMidi

    /** A note other than the one asked, from the whole staff. */
    private fun NotesViewModel.otherNote(): Int = StaffNotes.ALL.first { it != current }

    /** Taps the right place and lets the green second pass. */
    private fun TestScope.answerRight(vm: NotesViewModel) {
        vm.onNoteTapped(vm.current)
        advanceTimeBy(NotesViewModel.FEEDBACK_MS + 1L)
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
                TunerViewModel.REFERENCE_TONE_VOLUME,
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
        repeat(NotesUiState.ROUND_LENGTH) { answerRight(vm) }
        assertTrue(vm.state.value.roundOver)
        assertEquals(NotesUiState.ROUND_LENGTH, vm.state.value.answered)
        assertFalse(vm.state.value.solved)

        val before = vm.state.value
        vm.onNoteTapped(vm.current)
        vm.onNoteTapped(vm.otherNote())
        assertEquals(before, vm.state.value)

        vm.playAgain()
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
            if (vm.state.value.roundOver) vm.playAgain() else assertNotEquals(asked, vm.current)
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
        repeat(NotesUiState.ROUND_LENGTH - 1) { answerRight(vm) }
        vm.onNoteTapped(vm.current) // the tenth, still sounding
        val stopsBefore = tonePlayer.stopCount
        vm.playAgain()
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
        repeat(NotesUiState.ROUND_LENGTH - 2) {
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

        vm.playAgain()
        assertNull(vm.state.value.roundMillis)
        clock.now = 100_000L
        answerRight(vm)
        clock.now += 5_000L
        repeat(NotesUiState.ROUND_LENGTH - 1) { answerRight(vm) }
        assertEquals(5_000L, vm.state.value.roundMillis) // the new round timed from its own first tap
    }

    @Test
    fun `play again never opens with the note just answered`() = runTest(dispatcher) {
        val vm = viewModel()
        (StaffNotes.DEFAULT_PRACTICE - setOf(60, 62)).forEach(vm::togglePracticeNote)
        repeat(10) {
            repeat(NotesUiState.ROUND_LENGTH) { answerRight(vm) }
            assertTrue(vm.state.value.roundOver)
            val last = vm.current
            vm.playAgain()
            assertNotEquals(last, vm.current)
        }
    }
}
