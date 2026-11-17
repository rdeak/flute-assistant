// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.tuner

import hr.bertlab.fluteassistant.audio.TONE_VOLUME
import hr.bertlab.fluteassistant.domain.listen.FixHold
import hr.bertlab.fluteassistant.domain.listen.Hearing
import hr.bertlab.fluteassistant.domain.listen.HearingTracker
import hr.bertlab.fluteassistant.domain.listen.ListenRules
import hr.bertlab.fluteassistant.domain.listen.PitchFrame
import hr.bertlab.fluteassistant.domain.listen.TunerAdvice
import hr.bertlab.fluteassistant.domain.tuner.FluteRange
import hr.bertlab.fluteassistant.domain.tuner.TunerMode
import hr.bertlab.fluteassistant.fakes.FakeLogger
import hr.bertlab.fluteassistant.fakes.FakeReferenceTonePlayer
import hr.bertlab.fluteassistant.fakes.FakeSettingsStore
import hr.bertlab.fluteassistant.fakes.FakeTunerEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
    private fun steady(freq: Double) = repeat(ListenRules.LOCK_FRAMES) { engine.frames.tryEmit(pitched(freq)) }

    private fun listeningViewModel(): TunerViewModel = TunerViewModel(engine, settings, tonePlayer, logger).apply {
        onPermissionResult(true)
        startListening()
    }

    @Test
    fun `the guide opens on the card it is asked for, moves and closes`() = runTest(dispatcher) {
        val vm = listeningViewModel()
        advanceUntilIdle()
        assertNull(vm.state.value.guideCard)
        vm.openGuide(GuideCard.HEAD_JOINT)
        assertEquals(GuideCard.HEAD_JOINT, vm.state.value.guideCard)
        vm.showGuideCard(GuideCard.EACH_NOTE)
        assertEquals(GuideCard.EACH_NOTE, vm.state.value.guideCard)
        vm.closeGuide(GuideExit.BACK)
        assertNull(vm.state.value.guideCard)
        vm.openGuide(GuideCard.ONE_NOTE_OFF)
        assertEquals(GuideCard.ONE_NOTE_OFF, vm.state.value.guideCard) // the card the line names, not the last one shown
        assertTrue(vm.state.value.isListening) // the microphone stays on underneath
    }

    @Test
    fun `a closed guide is not moved or closed again`() = runTest(dispatcher) {
        val vm = listeningViewModel()
        vm.showGuideCard(GuideCard.HEAD_JOINT)
        assertNull(vm.state.value.guideCard)
        vm.closeGuide(GuideExit.CLOSE)
        assertNull(vm.state.value.guideCard)
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

        repeat(ListenRules.SMOOTHING_WINDOW) { engine.frames.tryEmit(pitched(440.0)) }
        advanceUntilIdle()

        val reading = vm.state.value.reading
        assertNotNull(reading)
        assertEquals(69, reading!!.midi)
        assertEquals(0.0, reading.cents, 0.01)
    }

    @Test
    fun `median smoothing rejects a single outlier`() = runTest(dispatcher) {
        val vm = listeningViewModel()
        advanceUntilIdle()
        steady(440.0)
        listOf(880.0, 439.8, 440.2, 440.1).forEach { engine.frames.tryEmit(pitched(it)) }
        advanceUntilIdle()
        assertEquals(69, vm.state.value.reading!!.midi)
        assertEquals(0.0, vm.state.value.reading!!.cents, 1.0)
    }

    @Test
    fun `a brief blip never shows a note`() = runTest(dispatcher) {
        val vm = listeningViewModel()
        advanceUntilIdle()
        repeat(ListenRules.LOCK_FRAMES - 1) { engine.frames.tryEmit(pitched(440.0)) } // a bump, a syllable
        advanceUntilIdle()
        assertNull(vm.state.value.reading)
    }

    @Test
    fun `frames that disagree leave the reading where it was`() = runTest(dispatcher) {
        val vm = listeningViewModel()
        advanceUntilIdle()
        steady(440.0)
        advanceUntilIdle()
        assertEquals(69, vm.state.value.reading!!.midi)

        listOf(300.0, 330.0, 360.0).forEach { engine.frames.tryEmit(pitched(it)) } // a voice gliding past
        advanceUntilIdle()
        assertEquals(69, vm.state.value.reading!!.midi)
        assertEquals(0.0, vm.state.value.reading!!.cents, 0.01)
    }

    @Test
    fun `a new steady note replaces the old one`() = runTest(dispatcher) {
        val vm = listeningViewModel()
        advanceUntilIdle()
        steady(440.0)
        steady(392.0)
        advanceUntilIdle()
        assertEquals(67, vm.state.value.reading!!.midi)
    }

    @Test
    fun `low confidence frames are ignored`() = runTest(dispatcher) {
        val vm = listeningViewModel()
        advanceUntilIdle()
        steady(440.0)
        advanceUntilIdle()
        assertEquals(69, vm.state.value.reading!!.midi)

        engine.frames.tryEmit(pitched(1000.0, probability = 0.3))
        advanceUntilIdle()
        assertEquals(69, vm.state.value.reading!!.midi)
    }

    @Test
    fun `reading is held briefly through silence then cleared`() = runTest(dispatcher) {
        val vm = listeningViewModel()
        advanceUntilIdle()
        steady(440.0)
        advanceUntilIdle()

        repeat(ListenRules.HOLD_FRAMES - 1) { engine.frames.tryEmit(silence) }
        advanceUntilIdle()
        assertNotNull(vm.state.value.reading)

        engine.frames.tryEmit(silence)
        advanceUntilIdle()
        assertNull(vm.state.value.reading)
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
    fun `settings are offered only when the phone will not ask again`() = runTest(dispatcher) {
        val vm = TunerViewModel(engine, settings, tonePlayer, logger)
        vm.onPermissionAnswer(granted = false, canAskAgain = true)
        assertFalse(vm.state.value.permissionGranted)
        assertFalse("the phone will ask again, so Yes is enough", vm.state.value.micBlocked)
        vm.onPermissionAnswer(granted = false, canAskAgain = false)
        assertTrue("the phone will not ask again: only Settings is left", vm.state.value.micBlocked)
        vm.onPermissionResult(false) // the screen's own check, on coming back to the tab: it knows nothing new
        assertTrue(vm.state.value.micBlocked)
        vm.onPermissionResult(true) // granted in the phone's settings
        assertTrue(vm.state.value.permissionGranted)
        assertFalse(vm.state.value.micBlocked)
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
        assertEquals(69, vm.state.value.reading!!.midi)
    }

    @Test
    fun `stored tuner mode is restored`() {
        assertEquals(TunerMode.IDENTIFY, TunerViewModel(engine, settings, tonePlayer, logger).state.value.mode)
        settings.tunerMode = TunerMode.TARGET
        assertEquals(TunerMode.TARGET, TunerViewModel(engine, settings, tonePlayer, logger).state.value.mode)
    }

    /** A tap on a flute of the Fingers tab (2026-11-18): Tune mode on that note, saved and logged as the child's, the guide closed. */
    @Test
    fun `tune opens a note in tune mode, closing the guide`() {
        val vm = TunerViewModel(engine, settings, tonePlayer, logger)
        vm.openGuide(GuideCard.HEAD_JOINT)
        vm.tune(73)
        assertEquals(TunerMode.TARGET, vm.state.value.mode)
        assertEquals(73, vm.state.value.targetMidi)
        assertNull(vm.state.value.guideCard)
        assertEquals(TunerMode.TARGET, settings.tunerMode)
        assertEquals(73, settings.targetNote)
        assertEquals("fingers", logger.events("tuner.guide").last()["via"])
        assertEquals(listOf(TunerMode.TARGET), logger.events("tuner.mode").map { it["mode"] })
        assertEquals(listOf(73), logger.events("tuner.target").map { it["midi"] })

        // Already in Tune mode, with no guide open: only the note moves, and only the note is logged.
        vm.tune(60)
        assertEquals(60, vm.state.value.targetMidi)
        assertEquals(1, logger.events("tuner.mode").size)
        assertEquals(1, logger.events("tuner.guide").count { it["action"] == "close" })
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
        val vm = listeningViewModel()
        advanceUntilIdle()
        vm.setMode(TunerMode.TARGET)
        vm.setTargetNote(69)

        vm.playTargetNote()
        assertTrue(vm.state.value.isPlayingReference)
        assertEquals(
            listOf(FakeReferenceTonePlayer.Played(440.0, TunerViewModel.REFERENCE_TONE_MS, TONE_VOLUME)),
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
        assertEquals(69, vm.state.value.reading!!.midi)
    }

    @Test
    fun `the chosen note plays only when a tap on the window asks, in tune mode and in silence`() = runTest(dispatcher) {
        val vm = listeningViewModel()
        advanceUntilIdle()
        vm.playTargetNote() // Which? mode: the window is no button
        vm.setMode(TunerMode.TARGET)
        steady(440.0)
        advanceUntilIdle()
        vm.playTargetNote() // the child is playing: the note would blank the reading
        assertTrue(tonePlayer.played.isEmpty())
        assertFalse(vm.state.value.isPlayingReference)
        assertNotNull(vm.state.value.reading)
    }

    /** A flat A4 in Tune mode, then silence until the reading clears: the line still names the head joint. */
    private fun TestScope.fixThenSilence(vm: TunerViewModel) {
        vm.setMode(TunerMode.TARGET)
        steady(430.0)
        advanceUntilIdle()
        assertEquals(TunerLine.Advice(TunerAdvice.HEAD_JOINT_IN), vm.state.value.line)
        silent(ListenRules.HOLD_FRAMES)
        assertNull(vm.state.value.reading)
        assertEquals(TunerLine.Advice(TunerAdvice.HEAD_JOINT_IN), vm.state.value.line)
    }

    /** [n] silent frames, one at a time: the fake engine buffers only so many. */
    private fun TestScope.silent(n: Int) = repeat(n) {
        engine.frames.tryEmit(silence)
        advanceUntilIdle()
    }

    @Test
    fun `a fix on the flute stays on the line through the silence after it, then goes`() = runTest(dispatcher) {
        val vm = listeningViewModel()
        advanceUntilIdle()
        fixThenSilence(vm) // the frame that cleared the reading was the first of the silence
        silent(FixHold.HOLD_FRAMES - 1)
        assertEquals(TunerLine.Advice(TunerAdvice.HEAD_JOINT_IN), vm.state.value.line)
        silent(1)
        assertEquals(TunerLine.PlayNote(69), vm.state.value.line)
    }

    @Test
    fun `a held fix gives way at once to a new sound and to the note playing`() = runTest(dispatcher) {
        val vm = listeningViewModel()
        advanceUntilIdle()
        fixThenSilence(vm)
        steady(440.0)
        advanceUntilIdle()
        assertEquals(TunerLine.InTune, vm.state.value.line)
        silent(ListenRules.HOLD_FRAMES)
        assertEquals("it does not come back", TunerLine.PlayNote(69), vm.state.value.line)

        steady(430.0)
        advanceUntilIdle()
        silent(ListenRules.HOLD_FRAMES)
        vm.playTargetNote()
        assertEquals(TunerLine.Listen, vm.state.value.line)
        advanceTimeBy(TunerViewModel.REFERENCE_TONE_MS + 1L)
        assertEquals("nor after the note", TunerLine.PlayNote(69), vm.state.value.line)
    }

    @Test
    fun `opening the guide and leaving the tab drop a held fix`() = runTest(dispatcher) {
        val vm = listeningViewModel()
        advanceUntilIdle()
        fixThenSilence(vm)
        vm.openGuide(GuideCard.HEAD_JOINT)
        vm.closeGuide(GuideExit.OK)
        assertEquals(TunerLine.PlayNote(69), vm.state.value.line)

        steady(430.0)
        advanceUntilIdle()
        silent(ListenRules.HOLD_FRAMES)
        vm.stopListening()
        assertEquals(TunerLine.PlayNote(69), vm.state.value.line)
    }

    @Test
    fun `stopping listening stops the tone`() = runTest(dispatcher) {
        val vm = listeningViewModel()
        advanceUntilIdle()
        vm.setMode(TunerMode.TARGET)
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
        vm.setMode(TunerMode.TARGET)
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
        repeat(ListenRules.HOLD_FRAMES - 1) { engine.frames.tryEmit(pitched(300.0 + it * 7)) } // a glissando: no three frames agree
        advanceUntilIdle()
        assertNotNull(vm.state.value.reading)
        engine.frames.tryEmit(pitched(300.0 + (ListenRules.HOLD_FRAMES - 1) * 7))
        advanceUntilIdle()
        assertNull(vm.state.value.reading)
    }

    /** A4 played [cents] off, at 440 Hz. */
    private fun a4(cents: Double) = 440.0 * 2.0.pow(cents / 1200.0)

    private fun frames(freq: Double, count: Int) = repeat(count) { engine.frames.tryEmit(pitched(freq)) }

    @Test
    fun `the first note is identified at once`() = runTest(dispatcher) {
        val vm = listeningViewModel(); advanceUntilIdle()
        steady(a4(60.0)) // nearest is B♭4
        advanceUntilIdle()
        assertEquals(70, vm.state.value.homeMidi)
    }

    @Test
    fun `a drift past the halfway point moves the ring only once it lasts`() = runTest(dispatcher) {
        val vm = listeningViewModel(); advanceUntilIdle()
        steady(440.0)
        // Two frames are unsteady while the pitch moves, then each frame is shown nearer B♭4.
        frames(a4(60.0), ListenRules.LOCK_FRAMES - 1 + ListenRules.SWITCH_FRAMES - 1)
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
        frames(a4(60.0), ListenRules.LOCK_FRAMES - 1 + ListenRules.SWITCH_FRAMES - 1)
        steady(440.0)
        frames(a4(60.0), ListenRules.LOCK_FRAMES - 1 + ListenRules.SWITCH_FRAMES - 1)
        advanceUntilIdle()
        assertEquals(69, vm.state.value.homeMidi)
    }

    @Test
    fun `a clear step to another note moves the ring at once`() = runTest(dispatcher) {
        val vm = listeningViewModel(); advanceUntilIdle()
        steady(440.0)
        frames(a4(100.0), ListenRules.LOCK_FRAMES) // B♭4 in tune
        advanceUntilIdle()
        assertEquals(70, vm.state.value.homeMidi)
        assertEquals(TuningStatus.IN_TUNE, vm.state.value.status)
    }

    @Test
    fun `after silence the ring takes the next note at once`() = runTest(dispatcher) {
        val vm = listeningViewModel(); advanceUntilIdle()
        steady(440.0)
        repeat(ListenRules.HOLD_FRAMES) { engine.frames.tryEmit(silence) }
        advanceUntilIdle()
        assertNull(vm.state.value.homeMidi)
        steady(a4(60.0))
        advanceUntilIdle()
        assertEquals(70, vm.state.value.homeMidi)
    }
}
