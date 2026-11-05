// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.tuner

import hr.bertlab.fluteassistant.audio.tuner.PitchFrame
import hr.bertlab.fluteassistant.core.log.LogTags
import hr.bertlab.fluteassistant.domain.tuner.TunerMode
import hr.bertlab.fluteassistant.fakes.FakeLogger
import hr.bertlab.fluteassistant.fakes.FakeReferenceTonePlayer
import hr.bertlab.fluteassistant.fakes.FakeSettingsStore
import hr.bertlab.fluteassistant.fakes.FakeTunerEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** What the tuner writes to the log, so a session on a real phone can be read back frame by frame. */
@OptIn(ExperimentalCoroutinesApi::class)
class TunerViewModelLoggingTest {

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
    private val silence = PitchFrame(null, 0.0, level = 0.1f)

    private fun listeningViewModel(): TunerViewModel = TunerViewModel(engine, settings, tonePlayer, logger).apply {
        onPermissionResult(true)
        startListening()
    }

    private fun steady(freq: Double) = repeat(TunerViewModel.LOCK_FRAMES) { engine.frames.tryEmit(pitched(freq)) }

    @Test
    fun `a summary of frame decisions is logged about once a second`() = runTest(dispatcher) {
        listeningViewModel()
        advanceUntilIdle()
        val lowProbability = 20
        repeat(lowProbability) { engine.frames.tryEmit(pitched(440.0, probability = 0.82)) } // found, but under the gate
        repeat(TunerViewModel.SUMMARY_FRAMES - lowProbability) { engine.frames.tryEmit(silence) }
        advanceUntilIdle()

        val summary = logger.events("tuner.summary").single()
        assertEquals(LogTags.TUNER, summary.tag)
        assertEquals(TunerViewModel.SUMMARY_FRAMES, summary["frames"])
        assertEquals(lowProbability, summary["lowProb"])
        assertEquals(TunerViewModel.SUMMARY_FRAMES - lowProbability, summary["silent"])
        assertEquals(0, summary["shown"])
        assertEquals(0.82, summary["pMin"] as Double, 1e-9)
        assertEquals(0.82, summary["pMax"] as Double, 1e-9)
        assertEquals(TunerViewModel.MIN_PROBABILITY, summary["gate"] as Double, 1e-9)
        assertNull(summary["reading"])
        assertEquals(settings.targetNote, summary["target"])
        assertEquals(TunerMode.IDENTIFY, summary["mode"])
    }

    @Test
    fun `the reading is logged when it changes note, not on every wobble`() = runTest(dispatcher) {
        listeningViewModel()
        advanceUntilIdle()
        steady(440.0)
        steady(441.0) // the same note, a few cents different
        advanceUntilIdle()
        assertEquals(listOf(69), logger.events("tuner.reading").map { it["midi"] })

        steady(392.0)
        repeat(TunerViewModel.HOLD_FRAMES) { engine.frames.tryEmit(silence) }
        advanceUntilIdle()
        assertEquals(listOf(69, 67, null), logger.events("tuner.reading").map { it["midi"] })
        assertEquals(69, logger.events("tuner.reading")[1]["target"])
    }

    @Test
    fun `a microphone failure is logged with its message`() = runTest(dispatcher) {
        engine.flowOverride = flow { throw IllegalStateException("Microphone is busy") }
        listeningViewModel()
        advanceUntilIdle()

        val failure = logger.events("tuner.error").single()
        assertEquals('w', failure.level)
        assertEquals("Microphone is busy", failure["message"])
        assertTrue(failure.error is IllegalStateException)
    }

    @Test
    fun `every frame is logged only while verbose logging is on`() = runTest(dispatcher) {
        listeningViewModel()
        advanceUntilIdle()
        engine.frames.tryEmit(pitched(440.0, probability = 0.82))
        advanceUntilIdle()
        assertTrue(logger.events("tuner.frame").isEmpty())

        logger.verbose = true
        engine.frames.tryEmit(pitched(440.0, probability = 0.82))
        advanceUntilIdle()
        val frame = logger.events("tuner.frame").single()
        assertEquals(440.0, frame["f"] as Double, 1e-9)
        assertEquals(0.82, frame["p"] as Double, 1e-9)
        assertEquals(0.4f, frame["level"])
        assertEquals(FrameDecision.LOW_PROBABILITY, frame["decision"])
    }

    @Test
    fun `the child's choices are logged`() = runTest(dispatcher) {
        val vm = listeningViewModel()
        advanceUntilIdle()
        vm.setTargetNote(72)
        vm.setMode(TunerMode.TARGET)
        vm.playTargetNote()
        advanceUntilIdle()

        assertEquals(72, logger.events("tuner.target").single()["midi"])
        assertEquals(TunerMode.TARGET, logger.events("tuner.mode").single()["mode"])
        assertEquals(72, logger.events("tuner.reference").single()["midi"])
    }

    @Test
    fun `the hearing is logged when it changes, with the level that decided it`() = runTest(dispatcher) {
        listeningViewModel(); advanceUntilIdle()
        repeat(HearingTracker.UNCLEAR_FRAMES) { engine.frames.tryEmit(PitchFrame(null, 0.0, level = 0.3f)) }
        advanceUntilIdle()
        val event = logger.events("tuner.hearing").single()
        assertEquals(Hearing.QUIET_UNCLEAR, event["hearing"])
        assertEquals(0.3, event["levelMean"] as Double, 1e-6)
        // Holds because SUMMARY_FRAMES == HearingTracker.UNCLEAR_FRAMES (both 25): the summary fires on the frame that flips the hearing.
        assertEquals(Hearing.QUIET_UNCLEAR, logger.events("tuner.summary").single()["hearing"])

        // The note that ends the run is logged with the mean that was in force, not the emptied window's 0.0:
        // two unsteady frames at 0.4 pushed two of the 25 quiet frames out before the third frame was shown.
        steady(440.0)
        advanceUntilIdle()
        val events = logger.events("tuner.hearing")
        assertEquals(2, events.size)
        assertEquals(Hearing.CLEAR, events[1]["hearing"])
        assertEquals((23 * 0.3 + 2 * 0.4) / 25, events[1]["levelMean"] as Double, 1e-6)
    }
}
