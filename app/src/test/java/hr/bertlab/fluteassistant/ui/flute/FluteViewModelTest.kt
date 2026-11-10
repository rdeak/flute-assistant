// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.flute

import hr.bertlab.fluteassistant.audio.TONE_VOLUME
import hr.bertlab.fluteassistant.domain.flute.Book
import hr.bertlab.fluteassistant.domain.flute.ListenRun
import hr.bertlab.fluteassistant.domain.flute.NoteLength.DOTTED_HALF
import hr.bertlab.fluteassistant.domain.flute.NoteLength.EIGHTH
import hr.bertlab.fluteassistant.domain.flute.NoteLength.HALF
import hr.bertlab.fluteassistant.domain.flute.NoteLength.QUARTER
import hr.bertlab.fluteassistant.domain.flute.NoteLength.WHOLE
import hr.bertlab.fluteassistant.domain.flute.Pulse
import hr.bertlab.fluteassistant.domain.flute.StepMark
import hr.bertlab.fluteassistant.domain.flute.TuneBreath
import hr.bertlab.fluteassistant.domain.flute.TuneItem
import hr.bertlab.fluteassistant.domain.flute.TuneNote
import hr.bertlab.fluteassistant.domain.flute.TuneRest
import hr.bertlab.fluteassistant.domain.flute.TuneWriter
import hr.bertlab.fluteassistant.domain.flute.Verdict
import hr.bertlab.fluteassistant.domain.flute.WrongNote
import hr.bertlab.fluteassistant.domain.listen.FrameDecision
import hr.bertlab.fluteassistant.domain.listen.Hearing
import hr.bertlab.fluteassistant.domain.listen.HearingTracker
import hr.bertlab.fluteassistant.domain.listen.ListenRules
import hr.bertlab.fluteassistant.domain.listen.NoteOnsetTracker
import hr.bertlab.fluteassistant.domain.listen.PitchFrame
import hr.bertlab.fluteassistant.domain.listen.TunerAdvice
import hr.bertlab.fluteassistant.domain.metronome.MetronomeConfig
import hr.bertlab.fluteassistant.domain.metronome.TimeSignature
import hr.bertlab.fluteassistant.domain.settings.MetronomeSettings
import hr.bertlab.fluteassistant.domain.tuner.NoteMath
import hr.bertlab.fluteassistant.fakes.FakeClock
import hr.bertlab.fluteassistant.fakes.FakeFluteTonePlayer
import hr.bertlab.fluteassistant.fakes.FakeListenRecorder
import hr.bertlab.fluteassistant.fakes.FakeLogger
import hr.bertlab.fluteassistant.fakes.FakeSettingsStore
import hr.bertlab.fluteassistant.fakes.FakeTunerEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FluteViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val settings = FakeSettingsStore()
    private val tonePlayer = FakeFluteTonePlayer()
    private val clock = FakeClock(now = 10_000L)
    private val logger = FakeLogger()
    private val engine = FakeTunerEngine()
    private val recorder = FakeListenRecorder()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** The one owner of the metronome's tempo and beat, as the app has it: made over the store when first asked for. */
    private val metronome by lazy { MetronomeSettings(settings) }

    private fun viewModel() = FluteViewModel(settings, metronome, tonePlayer, engine, clock, logger, recorder)

    private fun freq(midi: Int) = NoteMath.midiToFrequency(midi)

    private fun on(midi: Int) = FakeFluteTonePlayer.NoteOn(freq(midi), TONE_VOLUME)

    private val FluteViewModel.current: FluteUiState get() = state.value

    /** The length of a step the test knows is a note. */
    private val TuneItem.length get() = (this as TuneNote).length

    /** Presses [midi], holds it for [heldMs] of fake time and lets go. */
    private fun FluteViewModel.tap(midi: Int, heldMs: Long) {
        onKeyPressed(midi)
        clock.now += heldMs
        onKeyReleased(midi)
    }

    /** A two-note tune at the default 120 bpm (c1 a quarter, g1 a half note) with playback just started. */
    private fun playingViewModel(): FluteViewModel = viewModel().apply {
        tap(60, 500)
        tap(67, 1000)
        togglePlayback()
    }

    @Test
    fun `starts empty and silent`() {
        val vm = viewModel()
        assertEquals(FluteUiState(), vm.current)
        assertTrue(tonePlayer.notesOn.isEmpty())
    }

    @Test
    fun `a stored tune is restored and sanitized`() {
        settings.tune = listOf(TuneNote(60, HALF), TuneNote(61, QUARTER), TuneNote(84, WHOLE))
        val vm = viewModel()
        assertEquals(listOf(TuneNote(60, HALF), TuneNote(84, WHOLE)), vm.current.tune)
    }

    @Test
    fun `a tune written is kept in the open book with the metronome's speed and beat`() {
        settings.openBook = 2
        settings.config = MetronomeConfig(bpm = 100, timeSignature = TimeSignature.THREE_FOUR)
        val vm = viewModel()
        vm.tap(60, 600) // one beat at 100 bpm: a quarter
        assertEquals(Book(listOf(TuneNote(60, QUARTER)), 100, TimeSignature.THREE_FOUR), settings.books[2])
        assertTrue(settings.books[0].isEmpty)
    }

    @Test
    fun `creation opens the stored open book`() {
        settings.books[4] = Book(listOf(TuneNote(67, HALF)))
        settings.openBook = 4
        assertEquals(listOf(TuneNote(67, HALF)), viewModel().current.tune)
    }

    @Test
    fun `entering the tab opens the player and leaving closes it`() {
        val vm = viewModel()
        vm.enterTab()
        assertEquals(1, tonePlayer.openCount)
        vm.leaveTab()
        assertEquals(1, tonePlayer.closeCount)
    }

    @Test
    fun `a press sounds the note at once, shows it and adds it as an eighth`() {
        val vm = viewModel()
        vm.onKeyPressed(67)
        assertEquals(on(67), tonePlayer.sounding)
        assertEquals(67, vm.current.held?.midi)
        assertEquals(0, vm.current.heldIndex)
        assertEquals(listOf(TuneNote(67, EIGHTH)), vm.current.tune)
        assertEquals(vm.current.tune, settings.tune)
    }

    @Test
    fun `a release stops the sound and records the length the hold made`() {
        val vm = viewModel()
        vm.tap(67, 450) // 0.9 of a beat at 120 bpm
        assertEquals(1, tonePlayer.offCount)
        assertNull(tonePlayer.sounding)
        assertNull(vm.current.held?.midi)
        assertNull(vm.current.heldIndex)
        assertEquals(listOf(TuneNote(67, QUARTER)), vm.current.tune)
        assertEquals(vm.current.tune, settings.tune)
        val event = logger.events("flute.note").single()
        assertEquals(67, event["midi"])
        assertEquals(450L, event["heldMs"])
        assertEquals(QUARTER, event["length"])
        assertEquals(true, event["added"])
    }

    @Test
    fun `a quick tap is an eighth and a long hold stays a whole note`() {
        val vm = viewModel()
        vm.tap(67, 50)
        vm.tap(69, 7000)
        assertEquals(listOf(TuneNote(67, EIGHTH), TuneNote(69, WHOLE)), vm.current.tune)
    }

    @Test
    fun `a held note grows beat by beat and keeps its length when let go`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onKeyPressed(67)
        advanceTimeBy(374)
        assertEquals(EIGHTH, vm.current.tune[0].length)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(QUARTER, vm.current.tune[0].length)
        advanceTimeBy(375)
        runCurrent()
        assertEquals(HALF, vm.current.tune[0].length)
        advanceTimeBy(500)
        runCurrent()
        assertEquals(DOTTED_HALF, vm.current.tune[0].length)
        advanceTimeBy(500)
        runCurrent()
        assertEquals(WHOLE, vm.current.tune[0].length)
        clock.now += 1800
        vm.onKeyReleased(67)
        assertEquals(listOf(TuneNote(67, WHOLE)), vm.current.tune)
        assertNull(vm.current.held)
        advanceTimeBy(5000)
        assertEquals(listOf(TuneNote(67, WHOLE)), vm.current.tune)
    }

    @Test
    fun `beats are counted at the metronome's tempo, kept in the ring's range`() {
        settings.config = MetronomeConfig(bpm = 60)
        val vm = viewModel()
        vm.tap(67, 450) // under 3/4 of a one-second beat
        assertEquals(EIGHTH, vm.current.tune[0].length)
        metronome.update { MetronomeConfig(bpm = 30) } // a tempo below the ring: counted at 40
        vm.tap(69, 1200) // 0.8 of a 1.5 s beat
        assertEquals(QUARTER, vm.current.tune[1].length)
    }

    @Test
    fun `releasing a key that is not sounding is ignored`() {
        val vm = viewModel()
        vm.onKeyPressed(67)
        vm.onKeyReleased(69)
        assertEquals(0, tonePlayer.offCount)
        assertEquals(67, vm.current.held?.midi)
    }

    @Test
    fun `the newest press wins`() {
        val vm = viewModel()
        vm.onKeyPressed(67)
        clock.now += 400
        vm.onKeyPressed(69)
        assertEquals(on(69), tonePlayer.sounding)
        assertEquals(listOf(TuneNote(67, QUARTER), TuneNote(69, EIGHTH)), vm.current.tune)
        assertEquals(1, vm.current.heldIndex)
        clock.now += 500
        vm.onKeyReleased(67)
        assertEquals(0, tonePlayer.offCount)
        assertEquals(69, vm.current.held?.midi)
        vm.onKeyReleased(69)
        assertEquals(1, tonePlayer.offCount)
        assertEquals(QUARTER, vm.current.tune[1].length)
    }

    @Test
    fun `a press outside the keys is ignored`() {
        val vm = viewModel()
        vm.onKeyPressed(61)
        assertTrue(tonePlayer.notesOn.isEmpty())
        assertTrue(vm.current.tune.isEmpty())
    }

    @Test
    fun `at the cap a press still sounds but is not added`() {
        settings.tune = List(200) { TuneNote(60, QUARTER) }
        val vm = viewModel()
        vm.onKeyPressed(67)
        assertEquals(on(67), tonePlayer.sounding)
        assertEquals(67, vm.current.held?.midi)
        assertNull(vm.current.heldIndex)
        assertNull(vm.current.litIndex)
        assertEquals(200, vm.current.tune.size)
        clock.now += 400
        vm.onKeyReleased(67)
        assertEquals(1, tonePlayer.offCount)
        assertEquals(200, vm.current.tune.size)
        assertEquals(TuneNote(60, QUARTER), vm.current.tune.last())
        assertEquals(false, logger.events("flute.note").single()["added"])
    }

    @Test
    fun `erase removes the last note and is ignored while a key is held or the tune is empty`() {
        val vm = viewModel()
        vm.eraseLast()
        assertTrue(logger.events("flute.erase").isEmpty())
        vm.tap(67, 300)
        vm.tap(69, 300)
        vm.eraseLast()
        assertEquals(listOf(TuneNote(67, EIGHTH)), vm.current.tune)
        assertEquals(vm.current.tune, settings.tune)
        assertEquals(1, logger.events("flute.erase").size)
        vm.onKeyPressed(72)
        vm.eraseLast()
        assertEquals(2, vm.current.tune.size)
    }

    @Test
    fun `play sounds each note for its length at the tempo with a gap, lighting it as it goes`() = runTest(dispatcher) {
        val vm = playingViewModel()
        assertTrue(vm.current.isPlaying)
        assertEquals(0, vm.current.playing?.index)
        assertEquals(on(60), tonePlayer.sounding)
        assertEquals(60, vm.current.litMidi)
        advanceTimeBy(421) // a beat is 500 ms, the last 80 of them silent
        assertEquals(3, tonePlayer.offCount)
        assertEquals(0, vm.current.playing?.index)
        advanceTimeBy(80)
        assertEquals(1, vm.current.playing?.index)
        assertEquals(on(67), tonePlayer.sounding)
        advanceTimeBy(920) // two beats
        assertEquals(4, tonePlayer.offCount)
        advanceTimeBy(80)
        assertNull(vm.current.playing?.index)
        assertFalse(vm.current.isPlaying)
        val play = logger.events("flute.play").single()
        assertEquals(2, play["notes"])
        assertEquals(120, play["bpm"])
        assertEquals(1, logger.events("flute.play_done").size)
    }

    @Test
    fun `playback follows the metronome's tempo when it changes`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.tap(60, 500)
        metronome.update { MetronomeConfig(bpm = 60) }
        vm.togglePlayback()
        advanceTimeBy(919)
        assertEquals(on(60), tonePlayer.sounding)
        advanceTimeBy(2)
        assertNull(tonePlayer.sounding)
        assertEquals(QUARTER, vm.current.tune[0].length)
    }

    @Test
    fun `stop ends playback at once`() = runTest(dispatcher) {
        val vm = playingViewModel()
        advanceTimeBy(100)
        vm.togglePlayback()
        assertFalse(vm.current.isPlaying)
        assertNull(tonePlayer.sounding)
        assertEquals(1, logger.events("flute.stop").size)
        val notesOn = tonePlayer.notesOn.size
        advanceTimeBy(5000)
        assertEquals(notesOn, tonePlayer.notesOn.size)
    }

    @Test
    fun `a press during playback stops it and plays the key`() = runTest(dispatcher) {
        val vm = playingViewModel()
        advanceTimeBy(100)
        vm.onKeyPressed(72)
        assertFalse(vm.current.isPlaying)
        assertEquals(on(72), tonePlayer.sounding)
        assertEquals(3, vm.current.tune.size)
        assertEquals(2, vm.current.heldIndex)
    }

    @Test
    fun `erase during playback stops it first`() = runTest(dispatcher) {
        val vm = playingViewModel()
        advanceTimeBy(100)
        vm.eraseLast()
        assertFalse(vm.current.isPlaying)
        assertEquals(1, vm.current.tune.size)
    }

    @Test
    fun `play with a key held releases it first`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onKeyPressed(60)
        clock.now += 500
        vm.togglePlayback()
        assertEquals(QUARTER, vm.current.tune[0].length)
        assertNull(vm.current.held?.midi)
        assertEquals(0, vm.current.playing?.index)
    }

    @Test
    fun `play does nothing while the tune is empty`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.togglePlayback()
        assertFalse(vm.current.isPlaying)
        assertTrue(tonePlayer.notesOn.isEmpty())
    }

    @Test
    fun `leaving the tab silences the flute but keeps the tune`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onKeyPressed(60)
        clock.now += 350
        vm.leaveTab()
        assertEquals(1, tonePlayer.offCount)
        assertNull(vm.current.held?.midi)
        assertEquals(listOf(TuneNote(60, EIGHTH)), vm.current.tune)
        assertEquals(1, tonePlayer.closeCount)
        assertEquals(vm.current.tune, settings.tune)
        vm.enterTab()
        vm.togglePlayback()
        advanceTimeBy(100)
        vm.leaveTab()
        assertFalse(vm.current.isPlaying)
        assertNull(tonePlayer.sounding)
        assertEquals(2, tonePlayer.closeCount)
        assertEquals(1, vm.current.tune.size)
    }

    @Test
    fun `stop says why`() = runTest(dispatcher) {
        val vm = playingViewModel()
        advanceTimeBy(100)
        vm.togglePlayback()
        assertEquals("pill", logger.events("flute.stop").single()["reason"])
        vm.togglePlayback()
        advanceTimeBy(100)
        vm.onKeyPressed(72)
        assertEquals("key", logger.events("flute.stop")[1]["reason"])
        vm.onKeyReleased(72)
        vm.togglePlayback()
        advanceTimeBy(100)
        vm.eraseLast()
        assertEquals("erase", logger.events("flute.stop")[2]["reason"])
    }

    @Test
    fun `leaving the tab mid-playback logs why it stopped`() = runTest(dispatcher) {
        val vm = playingViewModel()
        advanceTimeBy(100)
        vm.leaveTab()
        assertEquals("leave", logger.events("flute.stop").single()["reason"])
        assertTrue(logger.events("flute.play_done").isEmpty())
    }

    @Test
    fun `a release after leaving the tab is ignored`() {
        val vm = viewModel()
        vm.onKeyPressed(60)
        clock.now += 350
        vm.leaveTab()
        vm.onKeyReleased(60)
        assertEquals(1, tonePlayer.offCount)
        assertEquals(listOf(TuneNote(60, EIGHTH)), vm.current.tune)
        assertEquals(1, logger.events("flute.note").size)
    }

    @Test
    fun `a real press on an activated key is not released by the activation`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onKeyActivated(67)
        advanceTimeBy(100)
        vm.onKeyPressed(67) // a finger lands on the same key before the activation's beat is up
        advanceTimeBy(501)
        assertEquals(67, vm.current.held?.midi)
        assertEquals(0, tonePlayer.offCount)
        assertEquals(2, vm.current.tune.size)
    }

    @Test
    fun `activating a key sounds it for one beat and adds a quarter note`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onKeyActivated(67)
        assertEquals(on(67), tonePlayer.sounding)
        advanceTimeBy(501)
        assertEquals(1, tonePlayer.offCount)
        assertEquals(listOf(TuneNote(67, QUARTER)), vm.current.tune)
    }

    /** Holds the rest key for [heldMs] of fake time and lets go. */
    private fun FluteViewModel.rest(heldMs: Long) {
        onRestPressed()
        clock.now += heldMs
        onRestReleased()
    }

    @Test
    fun `the rest key adds a silent rest that grows like a note`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onRestPressed()
        assertEquals(listOf(TuneRest(EIGHTH)), vm.current.tune)
        assertTrue(vm.current.held?.isRest == true)
        assertTrue(vm.current.restLit)
        assertEquals(0, vm.current.heldIndex)
        assertTrue(tonePlayer.notesOn.isEmpty())
        advanceTimeBy(751)
        runCurrent()
        assertEquals(listOf(TuneRest(HALF)), vm.current.tune)
        clock.now += 1000
        vm.onRestReleased()
        assertEquals(listOf(TuneRest(HALF)), vm.current.tune)
        assertFalse(vm.current.held?.isRest == true)
        assertEquals(vm.current.tune, settings.tune)
        assertEquals(0, tonePlayer.offCount)
        val event = logger.events("flute.rest").single()
        assertEquals(1000L, event["heldMs"])
        assertEquals(HALF, event["length"])
        assertEquals(true, event["added"])
        assertTrue(logger.events("flute.note").isEmpty())
    }

    @Test
    fun `a rest takes over a held note and silences it`() {
        val vm = viewModel()
        vm.onKeyPressed(67)
        clock.now += 500
        vm.onRestPressed()
        assertNull(tonePlayer.sounding)
        assertNull(vm.current.held?.midi)
        assertEquals(listOf(TuneNote(67, QUARTER), TuneRest(EIGHTH)), vm.current.tune)
        vm.onKeyReleased(67) // the finger on the note lifts after the rest took over
        assertTrue(vm.current.held?.isRest == true)
        assertEquals(1, tonePlayer.offCount)
    }

    @Test
    fun `a note takes over a held rest`() {
        val vm = viewModel()
        vm.onRestPressed()
        clock.now += 500
        vm.onKeyPressed(60)
        assertEquals(on(60), tonePlayer.sounding)
        assertFalse(vm.current.held?.isRest == true)
        assertEquals(listOf(TuneRest(QUARTER), TuneNote(60, EIGHTH)), vm.current.tune)
        vm.onRestReleased()
        assertEquals(60, vm.current.held?.midi)
        assertEquals(1, logger.events("flute.rest").size)
    }

    @Test
    fun `a breath is a mark with no sound, never twice in a row`() {
        val vm = viewModel()
        vm.tap(60, 500)
        vm.onBreath()
        assertEquals(listOf(TuneNote(60, QUARTER), TuneBreath), vm.current.tune)
        assertEquals(vm.current.tune, settings.tune)
        vm.onBreath()
        assertEquals(2, vm.current.tune.size)
        assertEquals(listOf(true, false), logger.events("flute.breath").map { it["added"] })
        assertEquals(1, tonePlayer.notesOn.size)
    }

    @Test
    fun `a breath ends a held note`() {
        val vm = viewModel()
        vm.onKeyPressed(60)
        clock.now += 500
        vm.onBreath()
        assertNull(tonePlayer.sounding)
        assertNull(vm.current.held?.midi)
        assertEquals(listOf(TuneNote(60, QUARTER), TuneBreath), vm.current.tune)
    }

    @Test
    fun `play is silent through a rest and goes straight past a breath`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.tap(60, 500)
        vm.rest(1000)
        vm.onBreath()
        vm.tap(67, 500)
        vm.togglePlayback()
        assertEquals(0, vm.current.playing?.index)
        advanceTimeBy(501)
        assertEquals(1, vm.current.playing?.index)
        assertNull(tonePlayer.sounding)
        assertTrue(vm.current.restLit)
        assertNull(vm.current.litMidi)
        assertEquals(1, vm.current.litIndex)
        advanceTimeBy(998) // a half rest: two whole beats of silence, no gap to take out
        assertEquals(1, vm.current.playing?.index)
        assertEquals(3, tonePlayer.notesOn.size) // the two taps, then c1 played back: nothing for the rest
        advanceTimeBy(2)
        assertEquals(3, vm.current.playing?.index) // the breath took no time
        assertEquals(on(67), tonePlayer.sounding)
        assertFalse(vm.current.restLit)
        advanceTimeBy(500)
        assertFalse(vm.current.isPlaying)
        val play = logger.events("flute.play").single()
        assertEquals(2, play["notes"])
        assertEquals(1, play["rests"])
    }

    @Test
    fun `erase removes rests and breaths and waits while the rest key is held`() {
        val vm = viewModel()
        vm.tap(60, 500)
        vm.rest(500)
        vm.onBreath()
        vm.eraseLast()
        assertEquals(listOf(TuneNote(60, QUARTER), TuneRest(QUARTER)), vm.current.tune)
        vm.onRestPressed()
        vm.eraseLast()
        assertEquals(3, vm.current.tune.size)
        clock.now += 100
        vm.onRestReleased()
        vm.eraseLast()
        vm.eraseLast()
        assertEquals(listOf(TuneNote(60, QUARTER)), vm.current.tune)
    }

    @Test
    fun `activating the rest key adds a quarter rest`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onRestActivated()
        assertTrue(vm.current.held?.isRest == true)
        advanceTimeBy(501)
        assertFalse(vm.current.held?.isRest == true)
        assertEquals(listOf(TuneRest(QUARTER)), vm.current.tune)
        assertTrue(tonePlayer.notesOn.isEmpty())
    }

    @Test
    fun `at the cap a rest or a breath is not added`() {
        settings.tune = List(200) { TuneNote(60, QUARTER) }
        val vm = viewModel()
        vm.onRestPressed()
        assertTrue(vm.current.held?.isRest == true)
        assertNull(vm.current.heldIndex)
        vm.onRestReleased()
        vm.onBreath()
        assertEquals(200, vm.current.tune.size)
        assertEquals(TuneNote(60, QUARTER), vm.current.tune.last())
        assertEquals(false, logger.events("flute.rest").single()["added"])
        assertEquals(false, logger.events("flute.breath").single()["added"])
    }

    @Test
    fun `leaving the tab or pressing play ends a held rest`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onRestPressed()
        clock.now += 500
        vm.leaveTab()
        assertFalse(vm.current.held?.isRest == true)
        assertEquals(listOf(TuneRest(QUARTER)), vm.current.tune)
        assertEquals(0, tonePlayer.offCount)
        vm.enterTab()
        vm.onRestPressed()
        clock.now += 500
        vm.togglePlayback()
        assertFalse(vm.current.held?.isRest == true)
        assertEquals(listOf(TuneRest(QUARTER), TuneRest(QUARTER)), vm.current.tune)
        assertTrue(vm.current.isPlaying)
    }

    @Test
    fun `in six eight the flute counts a click as an eighth note, like the metronome`() = runTest(dispatcher) {
        settings.config = MetronomeConfig(bpm = 120, timeSignature = TimeSignature.SIX_EIGHT)
        val vm = viewModel()
        vm.tap(60, 500) // one click
        vm.tap(62, 1000) // two clicks
        assertEquals(listOf(TuneNote(60, EIGHTH), TuneNote(62, QUARTER)), vm.current.tune)
        vm.togglePlayback()
        advanceTimeBy(501)
        assertEquals(1, vm.current.playing?.index) // the eighth lasted one click
        advanceTimeBy(1000)
        assertFalse(vm.current.isPlaying) // and the quarter two
        val play = logger.events("flute.play").single()
        assertEquals(120, play["bpm"])
        assertEquals("6/8", play["time"])
    }

    @Test
    fun `a screen reader's quarter note lasts two clicks in six eight`() = runTest(dispatcher) {
        settings.config = MetronomeConfig(bpm = 120, timeSignature = TimeSignature.SIX_EIGHT)
        val vm = viewModel()
        vm.onKeyActivated(67)
        advanceTimeBy(999)
        assertEquals(67, vm.current.held?.midi)
        advanceTimeBy(2)
        assertEquals(listOf(TuneNote(67, QUARTER)), vm.current.tune)
    }

    @Test
    fun `in six eight a held note grows at the pace of the eighth-note clicks`() = runTest(dispatcher) {
        settings.config = MetronomeConfig(bpm = 120, timeSignature = TimeSignature.SIX_EIGHT)
        val vm = viewModel()
        vm.onKeyPressed(67)
        assertEquals(EIGHTH, vm.current.tune[0].length) // one click
        advanceTimeBy(751)
        runCurrent()
        assertEquals(QUARTER, vm.current.tune[0].length) // two clicks of 500 ms make a beat
        advanceTimeBy(750)
        runCurrent()
        assertEquals(HALF, vm.current.tune[0].length) // four
    }

    @Test
    fun `the knob hops on every beat of the playing tune and stops with it`() = runTest(dispatcher) {
        val vm = playingViewModel()
        assertEquals(1L, vm.current.beatPulse) // the first beat, with the first note
        advanceTimeBy(499)
        assertEquals(1L, vm.current.beatPulse)
        advanceTimeBy(1)
        runCurrent() // advanceTimeBy stops short of the tasks due at its end
        assertEquals(2L, vm.current.beatPulse) // 500 ms: a beat at 120
        advanceTimeBy(500)
        runCurrent()
        assertEquals(3L, vm.current.beatPulse)
        advanceTimeBy(5000) // the tune is 1500 ms long
        assertFalse(vm.current.isPlaying)
        val afterEnd = vm.current.beatPulse
        advanceTimeBy(5000)
        assertEquals(afterEnd, vm.current.beatPulse)
    }

    @Test
    fun `stop ends the hops too`() = runTest(dispatcher) {
        val vm = playingViewModel()
        advanceTimeBy(100)
        vm.togglePlayback()
        val stopped = vm.current.beatPulse
        advanceTimeBy(5000)
        assertEquals(stopped, vm.current.beatPulse)
    }

    @Test
    fun `in six eight the knob hops on every click`() = runTest(dispatcher) {
        settings.config = MetronomeConfig(bpm = 120, timeSignature = TimeSignature.SIX_EIGHT)
        val vm = viewModel()
        vm.tap(60, 2000) // a half note: four clicks
        vm.togglePlayback()
        assertEquals(1L, vm.current.beatPulse)
        advanceTimeBy(500) // one click is an eighth, 500 ms
        runCurrent()
        assertEquals(2L, vm.current.beatPulse)
        advanceTimeBy(500)
        runCurrent()
        assertEquals(3L, vm.current.beatPulse)
    }

    @Test
    fun `on a long note the animal goes on a beat with each click, from the note's head`() = runTest(dispatcher) {
        val vm = playingViewModel() // c1 a quarter, g1 a half note, 500 ms a click
        assertEquals(0, vm.current.playing?.index)
        assertEquals(0, vm.current.playing?.halfBeat)
        advanceTimeBy(500)
        runCurrent()
        assertEquals(1, vm.current.playing?.index) // the half note begins on a click, over its head
        assertEquals(0, vm.current.playing?.halfBeat)
        assertEquals(2L, vm.current.beatPulse)
        advanceTimeBy(500)
        runCurrent()
        assertEquals(1, vm.current.playing?.index) // its second beat: a hop to the next place, not the same one
        assertEquals(2, vm.current.playing?.halfBeat)
        assertEquals(3L, vm.current.beatPulse)
        // The note still sounds past its last click and ends a short gap (80 ms) before the step does.
        assertEquals(on(67), tonePlayer.sounding)
        advanceTimeBy(419)
        runCurrent()
        assertEquals(on(67), tonePlayer.sounding)
        advanceTimeBy(1)
        runCurrent()
        assertNull(tonePlayer.sounding)
    }

    @Test
    fun `in six eight a quarter note is two clicks, two places`() = runTest(dispatcher) {
        settings.config = MetronomeConfig(bpm = 120, timeSignature = TimeSignature.SIX_EIGHT)
        val vm = viewModel()
        vm.tap(60, 2000) // a half note: four clicks of an eighth
        vm.togglePlayback()
        val places = mutableListOf(vm.current.playing?.halfBeat)
        repeat(3) {
            advanceTimeBy(500)
            runCurrent()
            places += vm.current.playing?.halfBeat
        }
        assertEquals(listOf(0, 1, 2, 3), places)
    }

    @Test
    fun `an eighth off the click moves the animal on without a hop`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.tap(60, 250) // two eighths and a quarter at 120 bpm
        vm.tap(62, 250)
        vm.tap(64, 500)
        vm.togglePlayback()
        assertEquals(1L, vm.current.beatPulse)
        advanceTimeBy(250)
        runCurrent()
        assertEquals(1, vm.current.playing?.index)
        assertEquals(0, vm.current.playing?.halfBeat)
        assertEquals(1L, vm.current.beatPulse) // half way through the beat: no click
        advanceTimeBy(250)
        runCurrent()
        assertEquals(2, vm.current.playing?.index)
        assertEquals(2L, vm.current.beatPulse)
    }

    @Test
    fun `opening the speed panel stops playback and says why`() = runTest(dispatcher) {
        val vm = playingViewModel()
        advanceTimeBy(100)
        vm.openSpeed()
        assertFalse(vm.current.isPlaying)
        assertNull(tonePlayer.sounding)
        assertEquals("speed", logger.events("flute.stop").single()["reason"])
        val open = logger.events("flute.speed_open").single()
        assertEquals(120, open["bpm"])
        assertEquals(TimeSignature.FOUR_FOUR.label, open["time"])
    }

    @Test
    fun `opening the speed panel ends a held key`() {
        val vm = viewModel()
        vm.onKeyPressed(67)
        clock.now += 1000
        vm.openSpeed()
        assertNull(vm.current.held?.midi)
        assertNull(tonePlayer.sounding)
        assertEquals(HALF, vm.current.tune.single().length)
    }

    @Test
    fun `the speed panel sets the metronome's tempo and beat, which the next note is counted in`() {
        val vm = viewModel()
        vm.setBpm(60)
        vm.setTimeSignature(TimeSignature.THREE_FOUR)
        assertEquals(MetronomeConfig(bpm = 60, timeSignature = TimeSignature.THREE_FOUR), metronome.config.value)
        assertEquals(metronome.config.value, settings.config)
        vm.tap(67, 450) // under 3/4 of a one-second beat
        assertEquals(EIGHTH, vm.current.tune[0].length)
    }

    @Test
    fun `closing the speed panel logs the speed and beat chosen there`() {
        val vm = viewModel()
        vm.setBpm(90) // what the panel's ring and beat buttons do
        vm.setTimeSignature(TimeSignature.THREE_FOUR)
        vm.closeSpeed()
        val done = logger.events("flute.speed_done").single()
        assertEquals(90, done["bpm"])
        assertEquals("3/4", done["time"])
    }

    @Test
    fun `a key let go of and pressed again at once goes on with the same note`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onKeyPressed(67)
        clock.now += 300
        advanceTimeBy(300)
        vm.onKeyReleased(67) // the touch screen reported the hold as over: a cancel or a bounce
        assertEquals(listOf(TuneNote(67, EIGHTH)), vm.current.tune)
        clock.now += 20
        advanceTimeBy(20)
        vm.onKeyPressed(67) // and the same finger as down again
        assertEquals(on(67), tonePlayer.sounding)
        assertEquals(67, vm.current.held?.midi)
        assertEquals(0, vm.current.heldIndex)
        assertEquals(listOf(TuneNote(67, EIGHTH)), vm.current.tune) // still the one note
        advanceTimeBy(55) // ¾ of a beat after the first press: the hold grows from where it was
        runCurrent()
        assertEquals(QUARTER, vm.current.tune[0].length)
        clock.now += 680 // a second held all told
        advanceTimeBy(680)
        vm.onKeyReleased(67)
        assertEquals(listOf(TuneNote(67, HALF)), vm.current.tune)
        assertEquals(vm.current.tune, settings.tune)
        assertEquals(2, tonePlayer.offCount)
        val rejoin = logger.events("flute.rejoin").single()
        assertEquals(67, rejoin["midi"])
        assertEquals(20L, rejoin["gapMs"])
        assertEquals(listOf(300L, 1000L), logger.events("flute.note").map { it["heldMs"] })
    }

    @Test
    fun `a note gone on with shows the length its whole hold has made`() {
        val vm = viewModel()
        vm.tap(67, 370)
        clock.now += 10 // 380 ms into the hold: it passed ¾ of a beat during the gap
        vm.onKeyPressed(67)
        assertEquals(listOf(TuneNote(67, QUARTER)), vm.current.tune)
    }

    @Test
    fun `pressed again after a pause, the same key is a new note`() {
        val vm = viewModel()
        vm.tap(67, 300)
        clock.now += TuneWriter.HOLD_REJOIN_MS + 1
        vm.tap(67, 300)
        assertEquals(listOf(TuneNote(67, EIGHTH), TuneNote(67, EIGHTH)), vm.current.tune)
        assertTrue(logger.events("flute.rejoin").isEmpty())
    }

    @Test
    fun `a key let go of is not gone on with after another key, an erase or the rest`() {
        val vm = viewModel()
        vm.tap(67, 300)
        clock.now += 10
        vm.tap(69, 10)
        clock.now += 10
        vm.tap(67, 300) // a new g1 (an eighth), not the first one gone on with (a quarter by now)
        assertEquals(listOf(TuneNote(67, EIGHTH), TuneNote(69, EIGHTH), TuneNote(67, EIGHTH)), vm.current.tune)
        vm.eraseLast()
        clock.now += 10
        vm.tap(67, 300)
        assertEquals(listOf(TuneNote(67, EIGHTH), TuneNote(69, EIGHTH), TuneNote(67, EIGHTH)), vm.current.tune)
        clock.now += 10
        vm.onRestPressed() // the rest is not the note let go of
        assertEquals(4, vm.current.tune.size)
        assertTrue(logger.events("flute.rejoin").isEmpty())
    }

    @Test
    fun `the rest key let go of and pressed again at once goes on with the same rest`() {
        val vm = viewModel()
        vm.rest(300)
        clock.now += 10
        vm.onRestPressed()
        assertTrue(vm.current.held?.isRest == true)
        assertEquals(0, vm.current.heldIndex)
        assertEquals(listOf(TuneRest(EIGHTH)), vm.current.tune)
        clock.now += 690
        vm.onRestReleased()
        assertEquals(listOf(TuneRest(HALF)), vm.current.tune)
        assertNull(logger.events("flute.rejoin").single()["midi"])
        assertTrue(tonePlayer.notesOn.isEmpty())
    }

    @Test
    fun `the rest key pressed again after a pause goes on with its rest from the length it has`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.rest(300) // an eighth rest: 250 ms of the 500 ms beat
        clock.now += 500 // far past HOLD_REJOIN_MS: a pause the child did not ask silence for
        advanceTimeBy(500)
        vm.onRestPressed()
        assertTrue(vm.current.held?.isRest == true)
        assertEquals(0, vm.current.heldIndex)
        assertEquals(listOf(TuneRest(EIGHTH)), vm.current.tune) // still the one rest
        advanceTimeBy(126) // 250 ms into the rest, it grows at ¾ of a beat: 125 ms on
        runCurrent()
        assertEquals(listOf(TuneRest(QUARTER)), vm.current.tune)
        clock.now += 300 // 550 ms of rest all told: a quarter
        advanceTimeBy(174)
        vm.onRestReleased()
        assertEquals(listOf(TuneRest(QUARTER)), vm.current.tune)
        assertEquals(vm.current.tune, settings.tune)
        val rejoin = logger.events("flute.rejoin").single()
        assertNull(rejoin["midi"])
        assertEquals(500L, rejoin["gapMs"])
        assertEquals(listOf(300L, 550L), logger.events("flute.rest").map { it["heldMs"] })
    }

    @Test
    fun `a whole rest pressed again stays one whole rest`() {
        val vm = viewModel()
        vm.rest(2000) // four beats: as long as a rest gets
        clock.now += 500
        vm.rest(500)
        assertEquals(listOf(TuneRest(WHOLE)), vm.current.tune) // a second rest takes a note or a breath before it
        assertEquals(1, logger.events("flute.rejoin").size)
        vm.onBreath()
        clock.now += 100
        vm.rest(300)
        assertEquals(listOf(TuneRest(WHOLE), TuneBreath, TuneRest(EIGHTH)), vm.current.tune)
    }

    @Test
    fun `a rest let go of is not gone on with after a note, a breath or an erase`() {
        val vm = viewModel()
        vm.rest(300)
        clock.now += 100
        vm.tap(60, 300)
        clock.now += 100
        vm.rest(300)
        assertEquals(listOf(TuneRest(EIGHTH), TuneNote(60, EIGHTH), TuneRest(EIGHTH)), vm.current.tune)
        clock.now += 100
        vm.onBreath()
        clock.now += 100
        vm.rest(300)
        val withBreath = listOf(TuneRest(EIGHTH), TuneNote(60, EIGHTH), TuneRest(EIGHTH), TuneBreath, TuneRest(EIGHTH))
        assertEquals(withBreath, vm.current.tune)
        vm.eraseLast()
        clock.now += 100
        vm.rest(300)
        assertEquals(withBreath, vm.current.tune)
        assertTrue(logger.events("flute.rejoin").isEmpty())
    }

    @Test
    fun `at the cap a note gone on with is still not added`() {
        settings.tune = List(200) { TuneNote(60, QUARTER) }
        val vm = viewModel()
        vm.tap(67, 300)
        clock.now += 10
        vm.onKeyPressed(67)
        assertEquals(on(67), tonePlayer.sounding)
        assertEquals(67, vm.current.held?.midi)
        assertNull(vm.current.heldIndex)
        clock.now += 690
        vm.onKeyReleased(67)
        assertEquals(200, vm.current.tune.size)
        assertEquals(listOf(false, false), logger.events("flute.note").map { it["added"] })
        assertEquals(1, logger.events("flute.rejoin").size)
    }

    // ---- Your turn: the flute listens ----

    private fun q(midi: Int) = TuneNote(midi, QUARTER)

    /** A frame of a note at a real flute's level at the phone (the recorded sessions: 0.67 and over), clear of the tracker's floor. */
    private fun pitched(midi: Int) = PitchFrame(freq(midi), 0.97, level = 0.7f)
    private val silentFrame = PitchFrame(null, 0.0, level = 0f)

    private fun TestScope.emit(frame: PitchFrame) {
        engine.frames.tryEmit(frame)
        runCurrent()
    }

    /** One note the child plays: enough agreeing frames to lock, then the gap that ends it. */
    private fun TestScope.play(midi: Int) {
        repeat(ListenRules.LOCK_FRAMES) { emit(pitched(midi)) }
        repeat(NoteOnsetTracker.ONSET_GAP_FRAMES) { emit(silentFrame) }
    }

    /** A view model with [items] as its tune (c1 d1 e1 by default), listening. */
    private fun TestScope.listeningViewModel(vararg items: TuneItem = arrayOf(q(60), q(62), q(64))): FluteViewModel {
        settings.tune = items.toList()
        return viewModel().apply {
            startListening()
            runCurrent()
        }
    }

    @Test
    fun `your turn waits on the first note, skipping a leading rest, and opens the microphone`() = runTest(dispatcher) {
        val vm = listeningViewModel(TuneRest(QUARTER), q(60), q(62))
        assertTrue(vm.current.isListening)
        assertTrue(vm.current.isRunning)
        assertEquals(1, vm.current.listening?.index)
        assertEquals(1, vm.current.litIndex)
        assertTrue(vm.current.marks.isEmpty())
        assertEquals(1, engine.subscriptions)
        assertEquals(2, logger.events("flute.listen").single()["notes"])
        assertEquals(1L, vm.current.beatPulse)
    }

    @Test
    fun `the right note earns a star and the animal moves on, the octave too`() = runTest(dispatcher) {
        val vm = listeningViewModel()
        val pulse = vm.current.beatPulse
        play(72)
        assertEquals(mapOf(0 to StepMark.FIRST_TRY), vm.current.marks)
        assertEquals(1, vm.current.listening?.index)
        assertEquals(pulse + 1, vm.current.beatPulse)
        val heard = logger.events("flute.heard").single()
        assertEquals(0, heard["index"])
        assertEquals(60, heard["expected"])
        assertEquals(72, heard["heard"])
        assertEquals("right", heard["verdict"])
    }

    @Test
    fun `a wrong note shows the note heard until the right note, which then earns a faint star`() = runTest(dispatcher) {
        val vm = listeningViewModel()
        play(65)
        assertEquals(WrongNote(index = 0, midi = 65), vm.current.wrong)
        assertEquals(0, vm.current.listening?.index)
        assertTrue(vm.current.marks.isEmpty())
        assertEquals("wrong", logger.events("flute.heard").single()["verdict"])
        advanceTimeBy(10_000) // no timer takes it away: the child reads it at their own pace
        assertEquals(WrongNote(index = 0, midi = 65), vm.current.wrong)
        play(60)
        assertNull(vm.current.wrong)
        assertEquals(mapOf(0 to StepMark.RETRY), vm.current.marks)
        assertEquals(1, vm.current.listening?.index)
        assertEquals("retry", logger.events("flute.heard").last()["verdict"])
    }

    @Test
    fun `another wrong note replaces the note heard, the previous note heard again leaves it`() = runTest(dispatcher) {
        val vm = listeningViewModel()
        play(60)
        play(65)
        assertEquals(WrongNote(index = 1, midi = 65), vm.current.wrong)
        play(67)
        assertEquals(WrongNote(index = 1, midi = 67), vm.current.wrong)
        play(60) // the step just judged, ignored
        assertEquals("ignored", logger.events("flute.heard").last()["verdict"])
        assertEquals(WrongNote(index = 1, midi = 67), vm.current.wrong)
        play(62)
        assertNull(vm.current.wrong)
        assertEquals(mapOf(0 to StepMark.FIRST_TRY, 1 to StepMark.RETRY), vm.current.marks)
    }

    @Test
    fun `the previous note heard again is ignored, a written repeat counts only once started again`() = runTest(dispatcher) {
        val vm = listeningViewModel(q(60), q(60), q(62))
        repeat(ListenRules.LOCK_FRAMES) { emit(pitched(60)) }
        assertEquals(1, vm.current.listening?.index)
        // The note just counted pops up an octave without a gap: a step inside one note, not the repeat.
        repeat(ListenRules.LOCK_FRAMES) { emit(pitched(72)) }
        assertEquals(1, vm.current.listening?.index)
        assertEquals("ignored", logger.events("flute.heard").last()["verdict"])
        // Let go of and started again: the repeat, played.
        repeat(NoteOnsetTracker.ONSET_GAP_FRAMES) { emit(silentFrame) }
        play(60)
        assertEquals(2, vm.current.listening?.index)
        assertEquals(mapOf(0 to StepMark.FIRST_TRY, 1 to StepMark.FIRST_TRY), vm.current.marks)
    }

    @Test
    fun `a repeat the phone could not hear is forgiven when the next note comes, and said so`() = runTest(dispatcher) {
        val vm = listeningViewModel(q(60), q(60), q(62))
        play(60)
        play(62)
        assertEquals(mapOf(0 to StepMark.FIRST_TRY, 1 to StepMark.RETRY, 2 to StepMark.FIRST_TRY), vm.current.marks)
        assertEquals(1, logger.events("flute.repeat_forgiven").single()["index"])
        assertEquals("right", logger.events("flute.heard").last()["verdict"])
    }

    @Test
    fun `the note heard now shows under the waited step and goes with the sound`() = runTest(dispatcher) {
        val vm = listeningViewModel()
        assertNull(vm.current.heardNow)
        repeat(ListenRules.LOCK_FRAMES) { emit(pitched(65)) } // a wrong note, still sounding
        assertEquals(65, vm.current.heardNow)
        assertEquals(WrongNote(index = 0, midi = 65), vm.current.wrong)
        repeat(NoteOnsetTracker.ONSET_GAP_FRAMES) { emit(silentFrame) }
        assertNull("silence: nothing is heard now", vm.current.heardNow)
        assertEquals("the wrong note stays to be read", WrongNote(index = 0, midi = 65), vm.current.wrong)
        repeat(ListenRules.LOCK_FRAMES) { emit(pitched(60)) } // the right note, counted, still sounding
        assertEquals(1, vm.current.listening?.index)
        assertNull("the tail of the note just counted is not what the next step hears", vm.current.heardNow)
    }

    @Test
    fun `the note heard now stays shown when it is the written repeat waited on`() = runTest(dispatcher) {
        val vm = listeningViewModel(q(60), q(60))
        repeat(ListenRules.LOCK_FRAMES) { emit(pitched(60)) }
        assertEquals(1, vm.current.listening?.index)
        assertEquals(60, vm.current.heardNow)
    }

    @Test
    fun `holding the note on instead of starting it again asks to stop and play it again`() = runTest(dispatcher) {
        val vm = listeningViewModel(q(60), q(60))
        repeat(ListenRules.LOCK_FRAMES) { emit(pitched(60)) }
        assertEquals(TunerAdvice.NONE, vm.current.advice)
        repeat(ListenRun.REPEAT_ADVICE_FRAMES - 1) { emit(pitched(60)) }
        assertEquals(TunerAdvice.NONE, vm.current.advice)
        emit(pitched(60))
        assertEquals(TunerAdvice.PLAY_AGAIN, vm.current.advice)
        assertEquals(1, vm.current.listening?.index)
        repeat(NoteOnsetTracker.ONSET_GAP_FRAMES) { emit(silentFrame) }
        assertEquals(TunerAdvice.NONE, vm.current.advice)
        play(60) // started again: the repeat
        assertFalse(vm.current.isListening)
    }

    @Test
    fun `the last note ends listening once its sound ends, and keeps the stars`() = runTest(dispatcher) {
        val vm = listeningViewModel()
        play(60)
        play(62)
        repeat(ListenRules.LOCK_FRAMES) { emit(pitched(64)) } // the last note, counted, still sounding
        assertEquals(3, vm.current.marks.size)
        assertTrue("the run goes on while the last note sounds", vm.current.isListening)
        assertTrue(vm.current.listening?.finishing == true)
        assertEquals("the animal stays over the last note", 2, vm.current.animalIndex)
        assertNull("but nothing is waited on", vm.current.litIndex)
        val done = logger.events("flute.listen_done").single()
        assertEquals(3, done["stars"])
        assertEquals(0, done["retries"])
        repeat(NoteOnsetTracker.ONSET_GAP_FRAMES) { emit(silentFrame) }
        assertFalse(vm.current.isListening)
        assertFalse(vm.current.isRunning)
        assertFalse(vm.current.listening?.finishing == true)
        assertNull(vm.current.listening?.index)
        assertEquals(3, vm.current.marks.size)
        assertTrue(logger.events("flute.listen_stop").isEmpty())
        val after = vm.current
        play(60) // the microphone is closed: nothing changes
        assertEquals(after, vm.current)
    }

    @Test
    fun `the last note ends listening by itself when the child plays on`() = runTest(dispatcher) {
        val vm = listeningViewModel(q(60))
        repeat(ListenRules.LOCK_FRAMES) { emit(pitched(60)) }
        assertTrue(vm.current.listening?.finishing == true)
        repeat(ListenRun.FINISH_FRAMES - 1) { emit(pitched(60)) }
        assertTrue(vm.current.isListening)
        emit(pitched(60))
        assertFalse(vm.current.isListening)
        assertFalse(vm.current.listening?.finishing == true)
    }

    @Test
    fun `stop while the last note sounds ends the run at once`() = runTest(dispatcher) {
        val vm = listeningViewModel(q(60))
        repeat(ListenRules.LOCK_FRAMES) { emit(pitched(60)) }
        vm.stopRun()
        assertFalse(vm.current.isListening)
        assertFalse(vm.current.listening?.finishing == true)
        assertEquals("pill", logger.events("flute.listen_stop").single()["reason"])
    }

    @Test
    fun `stop, erase, the speed knob and leaving the tab end listening and say why`() = runTest(dispatcher) {
        fun ended(by: FluteViewModel.() -> Unit): Pair<FluteUiState, Any?> {
            logger.events.clear()
            val vm = listeningViewModel()
            play(60)
            vm.by()
            runCurrent()
            return vm.current to logger.events("flute.listen_stop").single()["reason"]
        }
        val (stopped, pill) = ended { stopRun() }
        assertEquals("pill", pill)
        assertFalse(stopped.isListening)
        assertNull(stopped.listening)
        assertNull(stopped.wrong)
        assertEquals(1, stopped.marks.size)
        val (erased, erase) = ended { eraseLast() }
        assertEquals("erase", erase)
        assertEquals(2, erased.tune.size)
        assertTrue(erased.marks.isEmpty())
        val (speed, knob) = ended { openSpeed() }
        assertEquals("speed", knob)
        assertFalse(speed.isListening)
        val (left, leave) = ended { leaveTab() }
        assertEquals("leave", leave)
        assertFalse(left.isListening)
        assertEquals(1, left.marks.size)
    }

    @Test
    fun `any write to the tune clears the marks`() = runTest(dispatcher) {
        val vm = listeningViewModel()
        play(60)
        vm.stopRun()
        assertEquals(1, vm.current.marks.size)
        vm.onKeyPressed(67)
        assertTrue(vm.current.marks.isEmpty())
        vm.onKeyReleased(67)
        val finished = listeningViewModel()
        play(60)
        play(62)
        play(64)
        assertEquals(3, finished.current.marks.size)
        finished.eraseLast()
        assertTrue(finished.current.marks.isEmpty())
    }

    @Test
    fun `play is refused while listening and your turn is refused while playing or without a note`() = runTest(dispatcher) {
        val vm = listeningViewModel()
        vm.togglePlayback()
        assertFalse(vm.current.isPlaying)
        assertTrue(vm.current.isListening)
        vm.stopRun()
        val playing = playingViewModel()
        playing.startListening()
        runCurrent()
        assertFalse(playing.current.isListening)
        playing.togglePlayback()
        settings.tune = listOf(TuneRest(QUARTER))
        val subscriptions = engine.subscriptions
        val rests = viewModel()
        rests.startListening()
        runCurrent()
        assertFalse(rests.current.isListening)
        assertEquals(subscriptions, engine.subscriptions)
    }

    @Test
    fun `unclear sound is reported and cleared`() = runTest(dispatcher) {
        val vm = listeningViewModel()
        repeat(HearingTracker.UNCLEAR_FRAMES) { emit(PitchFrame(null, 0.0, level = 0.3f)) }
        assertEquals(Hearing.QUIET_UNCLEAR, vm.current.listening?.hearing)
        assertEquals(Hearing.QUIET_UNCLEAR, logger.events("flute.hearing").last()["hearing"])
        repeat(HearingTracker.UNCLEAR_FRAMES) { emit(PitchFrame(null, 0.0, level = 0.6f)) }
        assertEquals(Hearing.LOUD_UNCLEAR, vm.current.listening?.hearing)
        vm.stopRun()
        assertNull(vm.current.listening)
    }

    @Test
    fun `a microphone failure ends listening cleanly`() = runTest(dispatcher) {
        engine.flowOverride = flow { throw IllegalStateException("Microphone is busy") }
        settings.tune = listOf(q(60))
        val vm = viewModel()
        vm.startListening()
        runCurrent()
        assertFalse(vm.current.isListening)
        assertFalse(vm.current.isRunning)
        assertEquals("Microphone is busy", logger.events("flute.listen_error").single()["message"])
    }

    @Test
    fun `a summary line is logged every SUMMARY_FRAMES frames`() = runTest(dispatcher) {
        listeningViewModel()
        repeat(ListenRules.SUMMARY_FRAMES) { emit(silentFrame) }
        val summary = logger.events("flute.listen_summary").single()
        assertEquals(ListenRules.SUMMARY_FRAMES, summary["silent"])
        assertEquals(60, summary["expected"])
        assertEquals(ListenRules.MIN_PROBABILITY, summary["gate"])
    }

    @Test
    fun `the microphone result starts listening or marks the denial`() = runTest(dispatcher) {
        settings.tune = listOf(q(60))
        val vm = viewModel()
        vm.onMicResult(false)
        assertTrue(vm.current.micDenied)
        assertEquals(false, logger.events("flute.mic").single()["granted"])
        vm.dismissMic()
        assertFalse(vm.current.micDenied)
        vm.onMicResult(true)
        runCurrent()
        assertTrue(vm.current.isListening)
        assertFalse(vm.current.micDenied)
    }

    @Test
    fun `settings are offered only when the phone will not ask again`() = runTest(dispatcher) {
        settings.tune = listOf(q(60))
        val vm = viewModel()
        vm.onMicResult(granted = false, canAskAgain = true)
        assertTrue(vm.current.micDenied)
        assertFalse("the phone will ask again, so Yes is enough", vm.current.micBlocked)
        vm.onMicResult(granted = false, canAskAgain = false)
        assertTrue("the phone will not ask again: only Settings is left", vm.current.micBlocked)
        vm.onMicResult(granted = true, canAskAgain = true)
        runCurrent()
        assertFalse(vm.current.micBlocked)
    }

    // ---- The songbook: opening the shelf and switching books ----

    @Test
    fun `creation fills the open book and the books of the state from the shelf`() {
        settings.books[2] = Book(listOf(TuneNote(64, HALF)), bpm = 90, timeSignature = TimeSignature.TWO_FOUR)
        settings.openBook = 2
        val vm = viewModel()
        assertEquals(2, vm.current.openBook)
        assertEquals(settings.books.toList(), vm.current.books)
    }

    @Test
    fun `opening another book shows its tune and gives the metronome its speed and beat`() {
        settings.books[1] = Book(listOf(TuneNote(62, HALF)), bpm = 70, timeSignature = TimeSignature.THREE_FOUR)
        val vm = viewModel()
        vm.openBook(1)
        assertEquals(MetronomeConfig(bpm = 70, timeSignature = TimeSignature.THREE_FOUR), metronome.config.value)
        assertEquals("stored too, for the next start", metronome.config.value, settings.config)
        assertEquals(listOf(TuneNote(62, HALF)), vm.current.tune)
        assertEquals(1, vm.current.openBook)
        assertEquals(1, settings.openBook)
    }

    @Test
    fun `the book left keeps the metronome's speed and beat as they are now`() {
        val vm = viewModel()
        vm.tap(60, 500)
        metronome.update { MetronomeConfig(bpm = 150, timeSignature = TimeSignature.TWO_FOUR) } // changed on the metronome tab
        vm.openBook(3)
        assertEquals(Book(listOf(TuneNote(60, QUARTER)), 150, TimeSignature.TWO_FOUR), settings.books[0])
        assertEquals(settings.books[0], vm.current.books[0])
    }

    @Test
    fun `opening an empty book leaves the metronome alone and shows an empty staff`() {
        settings.config = MetronomeConfig(timeSignature = TimeSignature.SIX_EIGHT)
        val vm = viewModel()
        vm.tap(60, 500)
        vm.openBook(5)
        assertTrue(vm.current.isEmpty)
        assertEquals(5, vm.current.openBook)
        assertEquals(5, settings.openBook)
        assertEquals(MetronomeConfig(timeSignature = TimeSignature.SIX_EIGHT), metronome.config.value)
        assertEquals(MetronomeConfig(timeSignature = TimeSignature.SIX_EIGHT), settings.config)
    }

    @Test
    fun `opening a book brings its bpm onto the ring`() {
        settings.books[1] = Book(listOf(TuneNote(60, QUARTER)), bpm = 30)
        viewModel().openBook(1)
        assertEquals(40, metronome.config.value.bpm)
        settings.books[2] = Book(listOf(TuneNote(60, QUARTER)), bpm = 260)
        viewModel().openBook(2)
        assertEquals(200, metronome.config.value.bpm)
    }

    @Test
    fun `tapping the open book changes nothing and says so`() {
        val vm = viewModel()
        vm.tap(60, 500)
        val before = vm.current
        vm.openBook(0)
        assertEquals(before, vm.current)
        assertEquals(MetronomeConfig(), metronome.config.value)
        assertEquals(listOf(TuneNote(60, QUARTER)), vm.current.tune)
        assertEquals(0, logger.events("flute.shelf_close").single()["book"])
        assertTrue(logger.events("flute.book_open").isEmpty())
    }

    @Test
    fun `an index off the shelf changes nothing`() {
        val vm = viewModel()
        vm.tap(60, 500)
        val before = vm.current
        vm.openBook(-1)
        vm.openBook(6)
        assertEquals(before, vm.current)
        assertEquals(MetronomeConfig(), metronome.config.value)
        assertEquals(0, settings.openBook)
        assertTrue(logger.events("flute.book_open").isEmpty())
        assertTrue(logger.events("flute.shelf_close").isEmpty())
    }

    @Test
    fun `a book opened is sanitized like the one opened at creation`() {
        settings.books[1] = Book(listOf(TuneNote(60, HALF), TuneNote(61, QUARTER), TuneNote(84, WHOLE)))
        val vm = viewModel()
        vm.openBook(1)
        assertEquals(listOf(TuneNote(60, HALF), TuneNote(84, WHOLE)), vm.current.tune)
    }

    @Test
    fun `a switch logs where from, where to and what the metronome was set to`() {
        settings.books[1] = Book(
            listOf(TuneNote(62, HALF), TuneRest(QUARTER), TuneNote(64, QUARTER)),
            bpm = 70,
            timeSignature = TimeSignature.THREE_FOUR,
        )
        val vm = viewModel()
        vm.openBook(1)
        val opened = logger.events("flute.book_open").single()
        assertEquals(0, opened["from"])
        assertEquals(1, opened["to"])
        assertEquals(3, opened["notes"])
        assertEquals(70, opened["bpm"])
        assertEquals("3/4", opened["time"])
        vm.openBook(5)
        val empty = logger.events("flute.book_open").last()
        assertEquals(1, empty["from"])
        assertEquals(5, empty["to"])
        assertEquals(0, empty["notes"])
        assertNull(empty["bpm"])
        assertNull(empty["time"])
    }

    @Test
    fun `switching back restores the first tune`() {
        val vm = viewModel()
        vm.tap(60, 500)
        vm.openBook(1)
        vm.tap(67, 1000)
        vm.openBook(0)
        assertEquals(listOf(TuneNote(60, QUARTER)), vm.current.tune)
        assertEquals(0, vm.current.openBook)
        assertEquals(listOf(TuneNote(67, HALF)), settings.books[1].tune)
        assertEquals(listOf(TuneNote(67, HALF)), vm.current.books[1].tune)
    }

    @Test
    fun `opening the shelf stops playback and listening and says why`() = runTest(dispatcher) {
        val playing = playingViewModel()
        advanceTimeBy(100)
        playing.openShelf()
        assertFalse(playing.current.isPlaying)
        assertNull(tonePlayer.sounding)
        assertEquals("book", logger.events("flute.stop").single()["reason"])
        assertEquals(0, logger.events("flute.shelf_open").single()["book"])
        logger.events.clear()
        val listening = listeningViewModel()
        play(60)
        listening.openShelf()
        runCurrent()
        assertFalse(listening.current.isListening)
        assertEquals("book", logger.events("flute.listen_stop").single()["reason"])
        assertEquals(0, logger.events("flute.shelf_open").single()["book"])
    }

    @Test
    fun `opening the shelf ends a held key and keeps its note in the book left`() {
        val vm = viewModel()
        vm.onKeyPressed(60)
        clock.now += 500
        vm.openShelf()
        assertNull(vm.current.held)
        assertNull(tonePlayer.sounding)
        vm.openBook(1)
        assertEquals(listOf(TuneNote(60, QUARTER)), settings.books[0].tune)
        assertTrue(vm.current.isEmpty)
    }

    @Test
    fun `a switch clears the marks of the last Your turn`() = runTest(dispatcher) {
        val vm = listeningViewModel()
        play(60)
        vm.stopRun()
        assertEquals(1, vm.current.marks.size)
        vm.openBook(1)
        assertTrue(vm.current.marks.isEmpty())
        assertNull(vm.current.wrong)
    }

    @Test
    fun `a switch while listening ends it with the reason book`() = runTest(dispatcher) {
        val vm = listeningViewModel()
        play(60)
        vm.openBook(1)
        runCurrent()
        assertFalse(vm.current.isListening)
        assertTrue(vm.current.marks.isEmpty())
        assertEquals("book", logger.events("flute.listen_stop").single()["reason"])
    }

    @Test
    fun `wiping a tune to nothing leaves its book empty and open`() {
        val vm = viewModel()
        vm.tap(60, 500)
        vm.eraseLast()
        assertTrue(settings.books[0].isEmpty)
        assertEquals(0, vm.current.openBook)
        assertTrue(vm.current.isEmpty)
    }

    // ---- Sessions: what a debug build records of a run (ui/flute/CLAUDE.md) ----

    @Test
    fun `listening starts the recorder with the tune, the pulse and the floor before the microphone and the log line`() = runTest(dispatcher) {
        var subscriptionsAtStart = -1
        var listenLinesAtStart = -1
        recorder.onStart = {
            subscriptionsAtStart = engine.subscriptions
            listenLinesAtStart = logger.events("flute.listen").size
        }
        val vm = listeningViewModel(q(60), TuneRest(QUARTER), q(62))
        val start = recorder.started.single()
        assertEquals(listOf(q(60), TuneRest(QUARTER), q(62)), start.tune)
        val config = settings.loadMetronomeConfig()
        assertEquals(Pulse(config.bpm, config.timeSignature), start.pulse)
        assertNull("the room has not been heard yet", start.floor)
        assertEquals("the first hop must be in the audio", 0, subscriptionsAtStart)
        assertEquals("the flute.listen line must be in the session", 0, listenLinesAtStart)
        play(60)
        emit(silentFrame) // silence with nothing sounding teaches the tracker the room's floor
        vm.stopRun()
        vm.startListening()
        runCurrent()
        assertEquals("the next run starts with the floor the last one left", 0f, recorder.started[1].floor)
    }

    @Test
    fun `every processed frame reaches the recorder with what the phone did`() = runTest(dispatcher) {
        listeningViewModel(q(71), TuneRest(QUARTER), q(72))
        repeat(ListenRules.LOCK_FRAMES) { emit(pitched(71)) }
        assertEquals(listOf(FrameDecision.UNSTEADY, FrameDecision.UNSTEADY, FrameDecision.SHOWN), recorder.frames.map { it.decision })
        val locked = recorder.frames.last()
        assertEquals(pitched(71), locked.frame)
        assertEquals(71, locked.sounding)
        assertEquals(NoteOnsetTracker.Onset(71, fresh = true), locked.onset)
        assertEquals(Verdict.RIGHT, locked.verdict)
        assertEquals("the index waited on before the judgement", 0, locked.waited)
        assertEquals(71, locked.expected)
        assertFalse(locked.finishing)
        assertEquals(Hearing.CLEAR, locked.hearing)
        assertNull(locked.floor)
        emit(pitched(71))
        val tail = recorder.frames.last()
        assertEquals("the rest is skipped", 2, tail.waited)
        assertEquals(72, tail.expected)
        assertNull(tail.verdict)
        assertNull(tail.onset)
        assertNull("the counted note's tail is not shown while another is waited on", tail.heardNow)
        assertEquals(TunerAdvice.NONE, tail.advice)
        assertFalse(tail.repeatHeld)
    }

    @Test
    fun `the recorder ends with the marks when the run is done or stopped, after the last frame`() = runTest(dispatcher) {
        val done = listeningViewModel(q(60))
        repeat(ListenRules.LOCK_FRAMES) { emit(pitched(60)) }
        repeat(NoteOnsetTracker.ONSET_GAP_FRAMES) { emit(silentFrame) }
        assertFalse(done.current.isListening)
        assertEquals(mapOf(0 to StepMark.FIRST_TRY), recorder.ended.single())
        assertEquals("the frame that ended the run is in", 5, recorder.frames.size)
        val stopped = listeningViewModel(q(60), q(62))
        play(60)
        stopped.stopRun()
        assertEquals(listOf(mapOf(0 to StepMark.FIRST_TRY), mapOf(0 to StepMark.FIRST_TRY)), recorder.ended)
    }

    @Test
    fun `the stop reason and the last summary are logged before the run ends, so the session has them`() = runTest(dispatcher) {
        val vm = listeningViewModel()
        play(60)
        var stopLinesAtEnd = -1
        var summaryLinesAtEnd = -1
        recorder.onEnd = {
            stopLinesAtEnd = logger.events("flute.listen_stop").size
            summaryLinesAtEnd = logger.events("flute.listen_summary").size
        }
        vm.stopRun()
        assertEquals(1, stopLinesAtEnd)
        assertEquals(1, summaryLinesAtEnd)
    }
}
