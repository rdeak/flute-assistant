// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.tuner

import hr.bertlab.fluteassistant.audio.tuner.PitchFrame
import hr.bertlab.fluteassistant.fakes.FakeLogger
import hr.bertlab.fluteassistant.fakes.FakeReferenceTonePlayer
import hr.bertlab.fluteassistant.fakes.FakeSettingsStore
import hr.bertlab.fluteassistant.domain.tuner.FluteRange
import hr.bertlab.fluteassistant.domain.tuner.NoteMath
import hr.bertlab.fluteassistant.domain.tuner.TunerMode
import hr.bertlab.fluteassistant.fakes.FakeTunerEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.pow

@OptIn(ExperimentalCoroutinesApi::class)
class TunerViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val engine = FakeTunerEngine()
    private val settings = FakeSettingsStore()
    private val tonePlayer = FakeReferenceTonePlayer()
    private val logger = FakeLogger()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun pitched(freq: Double, probability: Double = 0.97) = PitchFrame(freq, probability, level = 0.4f)
    private val silence = PitchFrame(null, 0.0, level = 0f)
    private fun quiet(level: Float = 0.3f) = PitchFrame(null, 0.0, level)

    /** A held note: enough agreeing frames for the tuner to trust it. */
    private fun steady(freq: Double) = repeat(TunerViewModel.LOCK_FRAMES) { engine.frames.tryEmit(pitched(freq)) }

    private fun listeningViewModel(): TunerViewModel = TunerViewModel(engine, settings, tonePlayer, logger).apply {
        onPermissionResult(true)
        startListening()
    }

    @Test
    fun `does not listen without permission`() = runTest(dispatcher) {
        val vm = TunerViewModel(engine, settings, tonePlayer, logger)
        vm.startListening()
        advanceUntilIdle()
        assertFalse(vm.state.value.isListening)
        assertEquals(0, engine.subscriptions)
    }

    @Test
    fun `stable frames produce a note reading`() = runTest(dispatcher) {
        val vm = listeningViewModel()
        advanceUntilIdle()
        assertTrue(vm.state.value.isListening)

        repeat(TunerViewModel.SMOOTHING_WINDOW) { engine.frames.tryEmit(pitched(440.0)) }
        advanceUntilIdle()

        val reading = vm.state.value.reading
        assertNotNull(reading)
        assertEquals("A4", reading!!.label)
        assertEquals(0.0, reading.cents, 0.01)
        assertEquals(0.4f, vm.state.value.level)
    }

    @Test
    fun `median smoothing rejects a single outlier`() = runTest(dispatcher) {
        val vm = listeningViewModel()
        advanceUntilIdle()
        steady(440.0)
        listOf(880.0, 439.8, 440.2, 440.1).forEach { engine.frames.tryEmit(pitched(it)) }
        advanceUntilIdle()
        assertEquals("A4", vm.state.value.reading!!.label)
        assertEquals(0.0, vm.state.value.reading!!.cents, 1.0)
    }

    @Test
    fun `a brief blip never shows a note`() = runTest(dispatcher) {
        val vm = listeningViewModel()
        advanceUntilIdle()
        repeat(TunerViewModel.LOCK_FRAMES - 1) { engine.frames.tryEmit(pitched(440.0)) } // a bump, a syllable
        advanceUntilIdle()
        assertNull(vm.state.value.reading)
    }

    @Test
    fun `frames that disagree leave the reading where it was`() = runTest(dispatcher) {
        val vm = listeningViewModel()
        advanceUntilIdle()
        steady(440.0)
        advanceUntilIdle()
        assertEquals("A4", vm.state.value.reading!!.label)

        listOf(300.0, 330.0, 360.0).forEach { engine.frames.tryEmit(pitched(it)) } // a voice gliding past
        advanceUntilIdle()
        assertEquals("A4", vm.state.value.reading!!.label)
        assertEquals(0.0, vm.state.value.reading!!.cents, 0.01)
    }

    @Test
    fun `a new steady note replaces the old one`() = runTest(dispatcher) {
        val vm = listeningViewModel()
        advanceUntilIdle()
        steady(440.0)
        steady(392.0)
        advanceUntilIdle()
        assertEquals("G4", vm.state.value.reading!!.label)
    }

    @Test
    fun `low confidence frames are ignored`() = runTest(dispatcher) {
        val vm = listeningViewModel()
        advanceUntilIdle()
        steady(440.0)
        advanceUntilIdle()
        assertEquals("A4", vm.state.value.reading!!.label)

        engine.frames.tryEmit(pitched(1000.0, probability = 0.3))
        advanceUntilIdle()
        assertEquals("A4", vm.state.value.reading!!.label)
    }

    @Test
    fun `reading is held briefly through silence then cleared`() = runTest(dispatcher) {
        val vm = listeningViewModel()
        advanceUntilIdle()
        steady(440.0)
        advanceUntilIdle()

        repeat(TunerViewModel.HOLD_FRAMES - 1) { engine.frames.tryEmit(silence) }
        advanceUntilIdle()
        assertNotNull(vm.state.value.reading)

        engine.frames.tryEmit(silence)
        advanceUntilIdle()
        assertNull(vm.state.value.reading)
    }

    @Test
    fun `changing the reference pitch recalculates the reading`() = runTest(dispatcher) {
        val vm = listeningViewModel()
        advanceUntilIdle()
        steady(440.0)
        advanceUntilIdle()

        vm.setReferenceA4(442.0)
        assertEquals(442.0, vm.state.value.referenceA4, 0.0)
        assertEquals(-7.85, vm.state.value.reading!!.cents, 0.05)

        vm.setReferenceA4(9999.0)
        assertEquals(TunerViewModel.MAX_A4, vm.state.value.referenceA4, 0.0)
        assertEquals(TunerViewModel.MAX_A4, settings.referenceA4, 0.0)
    }

    @Test
    fun `stored reference pitch is reset to standard on load`() {
        // The simplified UI cannot show or change A4, so a stale 442 or 466 must not silently
        // relabel every note; only an explicit setReferenceA4 (future advanced mode) changes it.
        settings.referenceA4 = 442.0
        val vm = TunerViewModel(engine, settings, tonePlayer, logger)
        assertEquals(NoteMath.DEFAULT_A4, vm.state.value.referenceA4, 0.0)
    }

    @Test
    fun `stop listening clears the reading`() = runTest(dispatcher) {
        val vm = listeningViewModel()
        advanceUntilIdle()
        steady(440.0)
        advanceUntilIdle()

        vm.stopListening()
        advanceUntilIdle()
        assertFalse(vm.state.value.isListening)
        assertNull(vm.state.value.reading)
    }

    @Test
    fun `an engine failure tells the screen and keeps its cause in the log`() = runTest(dispatcher) {
        engine.flowOverride = flow { throw IllegalStateException("Microphone busy") }
        val vm = listeningViewModel()
        advanceUntilIdle()
        assertFalse(vm.state.value.isListening)
        assertTrue(vm.state.value.micFailed)
        assertEquals("Microphone busy", logger.events("tuner.error").single()["message"])
    }

    @Test
    fun `listening again clears the failure`() = runTest(dispatcher) {
        engine.flowOverride = flow { throw IllegalStateException("Microphone busy") }
        val vm = listeningViewModel()
        advanceUntilIdle()
        engine.flowOverride = null
        vm.startListening()
        advanceUntilIdle()
        assertTrue(vm.state.value.isListening)
        assertFalse(vm.state.value.micFailed)
        vm.stopListening()
    }

    @Test
    fun `remembers that the permission prompt was shown`() = runTest(dispatcher) {
        val vm = TunerViewModel(engine, settings, tonePlayer, logger)
        assertFalse(vm.state.value.permissionRequested)
        vm.onPermissionRequested()
        assertTrue(vm.state.value.permissionRequested)
    }

    @Test
    fun `revoking permission stops listening`() = runTest(dispatcher) {
        val vm = listeningViewModel()
        advanceUntilIdle()
        vm.onPermissionResult(false)
        advanceUntilIdle()
        assertFalse(vm.state.value.isListening)
        assertFalse(vm.state.value.permissionGranted)
    }

    @Test
    fun `stored target note is restored and clamped`() {
        settings.targetNote = 62
        assertEquals(62, TunerViewModel(engine, settings, tonePlayer, logger).state.value.targetMidi)
        settings.targetNote = 10
        assertEquals(FluteRange.LOWEST_MIDI, TunerViewModel(engine, settings, tonePlayer, logger).state.value.targetMidi)
    }

    @Test
    fun `choosing a target note is saved and clamped`() {
        val vm = TunerViewModel(engine, settings, tonePlayer, logger)
        vm.setTargetNote(72)
        assertEquals(72, vm.state.value.targetMidi)
        assertEquals(72, settings.targetNote)
        vm.setTargetNote(999)
        assertEquals(FluteRange.HIGHEST_MIDI, vm.state.value.targetMidi)
        assertEquals(FluteRange.HIGHEST_MIDI, settings.targetNote)
    }

    @Test
    fun `deviation is measured from the chosen note`() = runTest(dispatcher) {
        val vm = listeningViewModel()
        advanceUntilIdle()
        steady(440.0)
        advanceUntilIdle()

        vm.setTargetNote(67)
        assertEquals(200.0, vm.state.value.centsOffTarget!!, 0.05)
        assertEquals("A4", vm.state.value.reading!!.label)
    }

    @Test
    fun `reference pitch moves the deviation from the target`() = runTest(dispatcher) {
        val vm = listeningViewModel()
        advanceUntilIdle()
        steady(440.0)
        advanceUntilIdle()

        vm.setTargetNote(69)
        vm.setReferenceA4(442.0)
        assertEquals(-7.85, vm.state.value.centsOffTarget!!, 0.05)
    }

    @Test
    fun `stored tuner mode is restored`() {
        assertEquals(TunerMode.IDENTIFY, TunerViewModel(engine, settings, tonePlayer, logger).state.value.mode)
        settings.tunerMode = TunerMode.TARGET
        assertEquals(TunerMode.TARGET, TunerViewModel(engine, settings, tonePlayer, logger).state.value.mode)
    }

    @Test
    fun `choosing a mode is saved`() {
        val vm = TunerViewModel(engine, settings, tonePlayer, logger)
        vm.setMode(TunerMode.TARGET)
        assertEquals(TunerMode.TARGET, vm.state.value.mode)
        assertEquals(TunerMode.TARGET, settings.tunerMode)
    }

    @Test
    fun `playing the target note sounds it and pauses the readings`() = runTest(dispatcher) {
        settings.volume = 0.7f // left over from an old build; nothing can change it now, so it is ignored
        val vm = listeningViewModel()
        advanceUntilIdle()
        vm.setTargetNote(69)

        vm.playTargetNote()
        assertTrue(vm.state.value.isPlayingReference)
        assertEquals(
            listOf(FakeReferenceTonePlayer.Played(440.0, TunerViewModel.REFERENCE_TONE_MS, TunerViewModel.REFERENCE_TONE_VOLUME)),
            tonePlayer.played,
        )

        steady(440.0) // the speaker, not the child: ignored
        advanceTimeBy(TunerViewModel.REFERENCE_TONE_MS / 2L)
        assertNull(vm.state.value.reading)

        advanceTimeBy(TunerViewModel.REFERENCE_TONE_MS + 1L)
        assertFalse(vm.state.value.isPlayingReference)
        assertEquals(1, tonePlayer.stopCount) // the finished track is released, not left registered as playing
        steady(440.0)
        advanceUntilIdle()
        assertEquals("A4", vm.state.value.reading!!.label)
    }

    @Test
    fun `stopping listening stops the tone`() = runTest(dispatcher) {
        val vm = listeningViewModel()
        advanceUntilIdle()
        vm.playTargetNote()
        vm.stopListening()
        assertFalse(vm.state.value.isPlayingReference)
        assertEquals(1, tonePlayer.stopCount)
    }

    @Test
    fun `unclear sound reaches the state and clears when listening stops`() = runTest(dispatcher) {
        val vm = listeningViewModel(); advanceUntilIdle()
        repeat(HearingTracker.UNCLEAR_FRAMES) { engine.frames.tryEmit(quiet()) }
        advanceUntilIdle()
        assertEquals(Hearing.QUIET_UNCLEAR, vm.state.value.hearing)
        assertEquals(TunerAdvice.COME_CLOSER, vm.state.value.advice)
        vm.stopListening()
        assertEquals(Hearing.CLEAR, vm.state.value.hearing)
    }

    @Test
    fun `an engine failure does not leave the unclear-sound advice next to the error`() = runTest(dispatcher) {
        engine.flowOverride = flow {
            repeat(HearingTracker.UNCLEAR_FRAMES) { emit(quiet()) }
            throw IllegalStateException("Microphone busy")
        }
        val vm = listeningViewModel(); advanceUntilIdle()
        assertTrue(vm.state.value.micFailed)
        assertEquals(Hearing.CLEAR, vm.state.value.hearing)
        assertEquals(TunerAdvice.NONE, vm.state.value.advice)
    }

    @Test
    fun `the reference tone neither counts as unclear sound nor leaves a stale hearing`() = runTest(dispatcher) {
        val vm = listeningViewModel(); advanceUntilIdle()
        repeat(HearingTracker.UNCLEAR_FRAMES) { engine.frames.tryEmit(quiet()) }
        advanceUntilIdle()
        vm.playTargetNote()
        assertEquals(Hearing.CLEAR, vm.state.value.hearing)
        repeat(HearingTracker.UNCLEAR_FRAMES) { engine.frames.tryEmit(quiet(0.6f)) } // the speaker, ignored
        advanceTimeBy(TunerViewModel.REFERENCE_TONE_MS + 1L)
        assertEquals(Hearing.CLEAR, vm.state.value.hearing)
    }

    @Test
    fun `a wandering pitch clears the reading after the hold`() = runTest(dispatcher) {
        val vm = listeningViewModel(); advanceUntilIdle()
        steady(440.0)
        repeat(TunerViewModel.HOLD_FRAMES - 1) { engine.frames.tryEmit(pitched(300.0 + it * 7)) } // a glissando: no three frames agree
        advanceUntilIdle()
        assertNotNull(vm.state.value.reading)
        engine.frames.tryEmit(pitched(300.0 + (TunerViewModel.HOLD_FRAMES - 1) * 7))
        advanceUntilIdle()
        assertNull(vm.state.value.reading)
    }

    /** A4 played [cents] off, at 440 Hz. */
    private fun a4(cents: Double) = 440.0 * 2.0.pow(cents / 1200.0)

    private fun frames(freq: Double, count: Int) = repeat(count) { engine.frames.tryEmit(pitched(freq)) }

    @Test
    fun `the first note is identified at once`() = runTest(dispatcher) {
        val vm = listeningViewModel(); advanceUntilIdle()
        steady(a4(60.0)) // nearest is A♯4
        advanceUntilIdle()
        assertEquals(70, vm.state.value.homeMidi)
    }

    @Test
    fun `a drift past the halfway point moves the ring only once it lasts`() = runTest(dispatcher) {
        val vm = listeningViewModel(); advanceUntilIdle()
        steady(440.0)
        // Two frames are unsteady while the pitch moves, then each frame is shown nearer A♯4.
        frames(a4(60.0), TunerViewModel.LOCK_FRAMES - 1 + TunerViewModel.SWITCH_FRAMES - 1)
        advanceUntilIdle()
        assertEquals(70, vm.state.value.reading!!.midi)
        assertEquals(69, vm.state.value.homeMidi)
        assertEquals(TuningStatus.HIGH, vm.state.value.status)

        frames(a4(60.0), 1)
        advanceUntilIdle()
        assertEquals(70, vm.state.value.homeMidi)
        assertEquals(TuningStatus.LOW, vm.state.value.status)
    }

    @Test
    fun `back on the note the drift starts again`() = runTest(dispatcher) {
        val vm = listeningViewModel(); advanceUntilIdle()
        steady(440.0)
        frames(a4(60.0), TunerViewModel.LOCK_FRAMES - 1 + TunerViewModel.SWITCH_FRAMES - 1)
        steady(440.0)
        frames(a4(60.0), TunerViewModel.LOCK_FRAMES - 1 + TunerViewModel.SWITCH_FRAMES - 1)
        advanceUntilIdle()
        assertEquals(69, vm.state.value.homeMidi)
    }

    @Test
    fun `a clear step to another note moves the ring at once`() = runTest(dispatcher) {
        val vm = listeningViewModel(); advanceUntilIdle()
        steady(440.0)
        frames(a4(100.0), TunerViewModel.LOCK_FRAMES) // A♯4 in tune
        advanceUntilIdle()
        assertEquals(70, vm.state.value.homeMidi)
        assertEquals(TuningStatus.IN_TUNE, vm.state.value.status)
    }

    @Test
    fun `after silence the ring takes the next note at once`() = runTest(dispatcher) {
        val vm = listeningViewModel(); advanceUntilIdle()
        steady(440.0)
        repeat(TunerViewModel.HOLD_FRAMES) { engine.frames.tryEmit(silence) }
        advanceUntilIdle()
        assertNull(vm.state.value.homeMidi)
        steady(a4(60.0))
        advanceUntilIdle()
        assertEquals(70, vm.state.value.homeMidi)
    }
}
