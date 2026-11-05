// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.metronome

import androidx.lifecycle.ViewModelStore
import hr.bertlab.fluteassistant.audio.metronome.BeatEvent
import hr.bertlab.fluteassistant.audio.metronome.ClickEvent
import hr.bertlab.fluteassistant.domain.model.BeatStrength
import hr.bertlab.fluteassistant.domain.model.MetronomeConfig
import hr.bertlab.fluteassistant.domain.model.SoundProfile
import hr.bertlab.fluteassistant.domain.model.Subdivision
import hr.bertlab.fluteassistant.domain.model.TimeSignature
import hr.bertlab.fluteassistant.domain.usecase.DeletePresetUseCase
import hr.bertlab.fluteassistant.domain.usecase.ObservePresetsUseCase
import hr.bertlab.fluteassistant.domain.usecase.SavePresetUseCase
import hr.bertlab.fluteassistant.fakes.FakeBackgroundPlayback
import hr.bertlab.fluteassistant.fakes.FakeClock
import hr.bertlab.fluteassistant.fakes.FakeMetronomePlayer
import hr.bertlab.fluteassistant.fakes.FakePresetRepository
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
    private val repository = FakePresetRepository()
    private val settings = FakeSettingsStore()
    private val clock = FakeClock()
    private val backgroundPlayback = FakeBackgroundPlayback()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun createViewModel() = MetronomeViewModel(
        player = player,
        observePresets = ObservePresetsUseCase(repository),
        savePreset = SavePresetUseCase(repository),
        deletePreset = DeletePresetUseCase(repository),
        settings = settings,
        clock = clock,
        backgroundPlayback = backgroundPlayback,
    )

    private fun beat(
        index: Int,
        strength: BeatStrength,
        session: Long = player.lastSession,
        audibleAtNanos: Long = System.nanoTime(),
    ) = BeatEvent(
        click = ClickEvent(frame = 0, strength = strength, beatIndex = index, subdivisionIndex = 0, barIndex = 0),
        audibleAtNanos = audibleAtNanos,
        sessionId = session,
    )

    @Test
    fun `toggle starts and stops the player with the current config`() = runTest(dispatcher) {
        val vm = createViewModel()
        vm.setBpm(100)
        vm.setVolume(0.6f)

        vm.togglePlayback()
        assertTrue(vm.state.value.isPlaying)
        assertEquals(100, player.started?.bpm)
        assertEquals(0.6f, player.startVolume)

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
        vm.adjustBpm(+5)
        vm.setSubdivision(Subdivision.TRIPLET)
        vm.setSound(SoundProfile.BELL)

        assertEquals(3, player.updates.size)
        val last = player.updates.last()
        assertEquals(125, last.bpm)
        assertEquals(TimeSignature.THREE_FOUR, last.timeSignature)
        assertEquals(Subdivision.TRIPLET, last.subdivision)
        assertEquals(SoundProfile.BELL, last.sound)
    }

    @Test
    fun `bpm is clamped to the supported range`() = runTest(dispatcher) {
        val vm = createViewModel()
        vm.setBpm(5)
        assertEquals(MetronomeConfig.MIN_BPM, vm.state.value.config.bpm)
        vm.adjustBpm(+10_000)
        assertEquals(MetronomeConfig.MAX_BPM, vm.state.value.config.bpm)
    }

    @Test
    fun `tap tempo sets the bpm after two taps`() = runTest(dispatcher) {
        val vm = createViewModel()
        clock.now = 1_000
        vm.tapTempo()
        assertEquals(1, vm.state.value.tapCount)
        assertEquals(MetronomeConfig.DEFAULT_BPM, vm.state.value.config.bpm)

        clock.now = 1_500
        vm.tapTempo()
        assertEquals(2, vm.state.value.tapCount)
        assertEquals(120, vm.state.value.config.bpm)

        clock.now = 1_900
        vm.tapTempo()
        assertEquals(133, vm.state.value.config.bpm) // 60000 / 450
    }

    @Test
    fun `volume is forwarded to the player`() = runTest(dispatcher) {
        val vm = createViewModel()
        vm.setVolume(1.7f)
        assertEquals(1f, vm.state.value.volume)
        assertEquals(1f, player.volume)
    }

    @Test
    fun `presets can be saved, loaded and deleted`() = runTest(dispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.setBpm(84)
        vm.setTimeSignature(TimeSignature.SIX_EIGHT)
        vm.savePreset("  Slow jig ")
        advanceUntilIdle()

        val presets = vm.state.value.presets
        assertEquals(1, presets.size)
        assertEquals("Slow jig", presets[0].name)
        assertEquals(84, presets[0].config.bpm)
        assertEquals(TimeSignature.SIX_EIGHT, presets[0].config.timeSignature)

        vm.setBpm(200)
        vm.setTimeSignature(TimeSignature.FOUR_FOUR)
        vm.loadPreset(presets[0])
        assertEquals(84, vm.state.value.config.bpm)
        assertEquals(TimeSignature.SIX_EIGHT, vm.state.value.config.timeSignature)

        vm.deletePreset(presets[0].id)
        advanceUntilIdle()
        assertTrue(vm.state.value.presets.isEmpty())
    }

    @Test
    fun `saving with a blank name is ignored`() = runTest(dispatcher) {
        val vm = createViewModel()
        vm.savePreset("   ")
        advanceUntilIdle()
        assertTrue(vm.state.value.presets.isEmpty())
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
    fun `subdivision ticks do not move the beat indicator`() = runTest(dispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.togglePlayback()
        player.events.tryEmit(beat(2, BeatStrength.WEAK))
        advanceUntilIdle()
        val pulse = vm.state.value.beatPulse

        player.events.tryEmit(
            BeatEvent(
                ClickEvent(0, BeatStrength.SUBDIVISION, beatIndex = 2, subdivisionIndex = 1, barIndex = 0),
                audibleAtNanos = System.nanoTime(),
                sessionId = player.lastSession,
            ),
        )
        advanceUntilIdle()
        assertEquals(2, vm.state.value.currentBeat)
        assertEquals(pulse, vm.state.value.beatPulse)
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
        settings.config = MetronomeConfig(bpm = 96, timeSignature = TimeSignature.THREE_FOUR, sound = SoundProfile.BELL)
        settings.volume = 0.3f
        val vm = createViewModel()
        assertEquals(96, vm.state.value.config.bpm)
        assertEquals(TimeSignature.THREE_FOUR, vm.state.value.config.timeSignature)
        // No volume control on screen: a stored value (maybe 0) must not stick with no way back.
        assertEquals(MetronomeUiState().volume, vm.state.value.volume)

        vm.setBpm(140)
        vm.setSubdivision(Subdivision.SHUFFLE)
        vm.setVolume(0.9f)
        assertEquals(140, settings.config.bpm)
        assertEquals(Subdivision.SHUFFLE, settings.config.subdivision)
        assertEquals(0.9f, settings.volume)
    }

    @Test
    fun `tap hint clears after a pause`() = runTest(dispatcher) {
        val vm = createViewModel()
        clock.now = 1_000
        vm.tapTempo()
        assertEquals(1, vm.state.value.tapCount)
        advanceTimeBy(MetronomeViewModel.TAP_HINT_MS + 1)
        assertEquals(0, vm.state.value.tapCount)
    }

    @Test
    fun `persistence failures surface as messages`() = runTest(dispatcher) {
        repository.failWrites = true
        val vm = createViewModel()
        vm.savePreset("x")
        advanceUntilIdle()
        assertEquals(MetronomeMessage.SAVE_FAILED, vm.state.value.userMessage)
        vm.dismissMessage()
        vm.deletePreset(1)
        advanceUntilIdle()
        assertEquals(MetronomeMessage.DELETE_FAILED, vm.state.value.userMessage)
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
        player.start(running, 0.5f)
        val vm = createViewModel()
        advanceUntilIdle()

        assertTrue(vm.state.value.isPlaying)
        assertEquals(running, vm.state.value.config)
        assertEquals(0.5f, vm.state.value.volume)

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
    fun `hidden options are reset on load`() = runTest(dispatcher) {
        settings.config = MetronomeConfig(bpm = 96, subdivision = Subdivision.TRIPLET, sound = SoundProfile.BELL)
        val vm = createViewModel()
        assertEquals(96, vm.state.value.config.bpm)
        assertEquals(Subdivision.NONE, vm.state.value.config.subdivision)
        assertEquals(SoundProfile.WOODBLOCK, vm.state.value.config.sound)
    }

    @Test
    fun `a stored tempo outside the ring is brought onto it`() = runTest(dispatcher) {
        settings.config = MetronomeConfig(bpm = 250)
        assertEquals(TempoBand.MAX_BPM, createViewModel().state.value.config.bpm)
        settings.config = MetronomeConfig(bpm = 30)
        assertEquals(TempoBand.MIN_BPM, createViewModel().state.value.config.bpm)
    }
}
