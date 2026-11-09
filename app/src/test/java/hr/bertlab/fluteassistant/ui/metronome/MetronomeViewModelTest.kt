// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.metronome

import androidx.lifecycle.ViewModelStore
import hr.bertlab.fluteassistant.audio.metronome.BeatEvent
import hr.bertlab.fluteassistant.audio.metronome.ClickEvent
import hr.bertlab.fluteassistant.domain.model.BeatStrength
import hr.bertlab.fluteassistant.domain.model.MetronomeConfig
import hr.bertlab.fluteassistant.domain.model.TimeSignature
import hr.bertlab.fluteassistant.fakes.FakeBackgroundPlayback
import hr.bertlab.fluteassistant.fakes.FakeMetronomePlayer
import hr.bertlab.fluteassistant.fakes.FakeSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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
class MetronomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val player = FakeMetronomePlayer()
    private val settings = FakeSettingsStore()
    private val backgroundPlayback = FakeBackgroundPlayback()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun createViewModel() = MetronomeViewModel(
        player = player,
        settings = settings,
        backgroundPlayback = backgroundPlayback,
    )

    private fun beat(
        index: Int,
        strength: BeatStrength,
        session: Long = player.lastSession,
        audibleAtNanos: Long = System.nanoTime(),
    ) = BeatEvent(
        click = ClickEvent(frame = 0, strength = strength, beatIndex = index, barIndex = 0),
        audibleAtNanos = audibleAtNanos,
        sessionId = session,
    )

    @Test
    fun `toggle starts and stops the player with the current config`() = runTest(dispatcher) {
        val vm = createViewModel()
        vm.setBpm(100)

        vm.togglePlayback()
        assertTrue(vm.state.value.isPlaying)
        assertEquals(100, player.started?.bpm)

        vm.togglePlayback()
        assertFalse(vm.state.value.isPlaying)
        assertEquals(1, player.stopCount)
        assertEquals(-1, vm.state.value.currentBeat)
    }

    @Test
    fun `config changes while playing are forwarded to the player`() = runTest(dispatcher) {
        val vm = createViewModel()
        vm.setTimeSignature(TimeSignature.THREE_FOUR) // not playing: no forwarding
        assertTrue(player.updates.isEmpty())

        vm.togglePlayback()
        vm.setBpm(125)
        vm.setTimeSignature(TimeSignature.SIX_EIGHT)

        assertEquals(2, player.updates.size)
        assertEquals(125, player.updates.first().bpm)
        assertEquals(TimeSignature.THREE_FOUR, player.updates.first().timeSignature)
        assertEquals(MetronomeConfig(bpm = 125, timeSignature = TimeSignature.SIX_EIGHT), player.updates.last())
    }

    @Test
    fun `bpm is clamped to the supported range`() = runTest(dispatcher) {
        val vm = createViewModel()
        vm.setBpm(5)
        assertEquals(MetronomeConfig.MIN_BPM, vm.state.value.config.bpm)
        vm.setBpm(10_000)
        assertEquals(MetronomeConfig.MAX_BPM, vm.state.value.config.bpm)
    }

    @Test
    fun `beat events drive the visual indicator`() = runTest(dispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.togglePlayback()

        player.events.tryEmit(beat(0, BeatStrength.STRONG))
        advanceUntilIdle()
        assertEquals(0, vm.state.value.currentBeat)
        assertEquals(BeatStrength.STRONG, vm.state.value.currentStrength)
        val pulseAfterFirst = vm.state.value.beatPulse

        player.events.tryEmit(beat(1, BeatStrength.WEAK))
        advanceUntilIdle()
        assertEquals(1, vm.state.value.currentBeat)
        assertEquals(pulseAfterFirst + 1, vm.state.value.beatPulse)
    }

    @Test
    fun `beat events that arrive after stop do not light the indicator`() = runTest(dispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.togglePlayback()
        val session = player.lastSession
        vm.togglePlayback() // stop; a click queued in the audio buffer still arrives afterwards

        player.events.tryEmit(beat(1, BeatStrength.WEAK, session = session))
        advanceUntilIdle()
        assertEquals(-1, vm.state.value.currentBeat)
        assertEquals(0L, vm.state.value.beatPulse)
    }

    @Test
    fun `beat events from a previous session are ignored after a quick restart`() = runTest(dispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.togglePlayback()
        val oldSession = player.lastSession
        vm.togglePlayback()
        vm.togglePlayback() // new session
        advanceUntilIdle()

        player.events.tryEmit(beat(3, BeatStrength.WEAK, session = oldSession))
        advanceUntilIdle()
        assertEquals(-1, vm.state.value.currentBeat)

        player.events.tryEmit(beat(3, BeatStrength.WEAK))
        advanceUntilIdle()
        assertEquals(3, vm.state.value.currentBeat)
    }

    @Test
    fun `visual update waits until the click is audible`() = runTest(dispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.togglePlayback()

        player.events.tryEmit(beat(0, BeatStrength.STRONG, audibleAtNanos = System.nanoTime() + 300_000_000L))
        advanceTimeBy(100)
        assertEquals(-1, vm.state.value.currentBeat)
        advanceTimeBy(300)
        assertEquals(0, vm.state.value.currentBeat)
    }

    @Test
    fun `engine stopping by itself resets the transport state`() = runTest(dispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.togglePlayback()
        assertTrue(vm.state.value.isPlaying)

        player.simulateEngineStop() // e.g. headphones unplugged
        advanceUntilIdle()
        assertFalse(vm.state.value.isPlaying)
        assertEquals(-1, vm.state.value.currentBeat)
    }

    @Test
    fun `a refused start surfaces a message and stays stopped`() = runTest(dispatcher) {
        player.refuseStart = true
        val vm = createViewModel()
        vm.togglePlayback()
        assertFalse(vm.state.value.isPlaying)
        assertEquals(MetronomeMessage.AUDIO_UNAVAILABLE, vm.state.value.userMessage)
        vm.dismissMessage()
        assertNull(vm.state.value.userMessage)
    }

    @Test
    fun `last used settings are restored and persisted`() = runTest(dispatcher) {
        settings.config = MetronomeConfig(bpm = 96, timeSignature = TimeSignature.THREE_FOUR)
        val vm = createViewModel()
        assertEquals(96, vm.state.value.config.bpm)
        assertEquals(TimeSignature.THREE_FOUR, vm.state.value.config.timeSignature)

        vm.setBpm(140)
        vm.setTimeSignature(TimeSignature.TWO_FOUR)
        assertEquals(MetronomeConfig(bpm = 140, timeSignature = TimeSignature.TWO_FOUR), settings.config)
    }

    @Test
    fun `starting playback keeps it alive in the background`() = runTest(dispatcher) {
        val vm = createViewModel()
        vm.togglePlayback()
        assertEquals(1, backgroundPlayback.startCount)
    }

    @Test
    fun `a refused start does not ask for background playback`() = runTest(dispatcher) {
        player.refuseStart = true
        val vm = createViewModel()
        vm.togglePlayback()
        assertEquals(0, backgroundPlayback.startCount)
    }

    @Test
    fun `a new view model adopts the session the player is already running`() = runTest(dispatcher) {
        // The screen was closed with Back while the metronome kept running in the background,
        // then the app was opened again from the notification.
        val running = MetronomeConfig(bpm = 72, timeSignature = TimeSignature.THREE_FOUR)
        player.start(running)
        val vm = createViewModel()
        advanceUntilIdle()

        assertTrue(vm.state.value.isPlaying)
        assertEquals(running, vm.state.value.config)

        player.events.tryEmit(beat(1, BeatStrength.WEAK))
        advanceUntilIdle()
        assertEquals(1, vm.state.value.currentBeat)

        vm.togglePlayback()
        assertFalse(vm.state.value.isPlaying)
        assertEquals(1, player.stopCount)
    }

    @Test
    fun `closing the screen leaves playback running`() = runTest(dispatcher) {
        val vm = createViewModel()
        vm.togglePlayback()

        ViewModelStore().apply { put("metronome", vm); clear() } // what the Activity does on finish

        assertTrue(player.playing)
        assertEquals(0, player.stopCount)
    }

    @Test
    fun `a stored tempo outside the ring is brought onto it`() = runTest(dispatcher) {
        settings.config = MetronomeConfig(bpm = 250)
        assertEquals(TempoBand.MAX_BPM, createViewModel().state.value.config.bpm)
        settings.config = MetronomeConfig(bpm = 30)
        assertEquals(TempoBand.MIN_BPM, createViewModel().state.value.config.bpm)
    }
}
