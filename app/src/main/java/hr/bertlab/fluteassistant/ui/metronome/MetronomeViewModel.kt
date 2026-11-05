// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.metronome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hr.bertlab.fluteassistant.audio.metronome.MetronomePlayer
import hr.bertlab.fluteassistant.core.MonotonicClock
import hr.bertlab.fluteassistant.domain.metronome.TapTempoCalculator
import hr.bertlab.fluteassistant.domain.model.BeatStrength
import hr.bertlab.fluteassistant.domain.model.MetronomeConfig
import hr.bertlab.fluteassistant.domain.model.Preset
import hr.bertlab.fluteassistant.domain.model.SoundProfile
import hr.bertlab.fluteassistant.domain.model.Subdivision
import hr.bertlab.fluteassistant.domain.model.TimeSignature
import hr.bertlab.fluteassistant.domain.repository.SettingsStore
import hr.bertlab.fluteassistant.domain.usecase.DeletePresetUseCase
import hr.bertlab.fluteassistant.domain.usecase.ObservePresetsUseCase
import hr.bertlab.fluteassistant.domain.usecase.SavePresetUseCase
import hr.bertlab.fluteassistant.playback.BackgroundPlayback
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Unidirectional data flow: the screen renders [state] and calls the intent methods below.
 * Engine calls are forwarded synchronously; persistence goes through the use cases and
 * [SettingsStore] (last-used settings survive process death).
 *
 * The ViewModel is a controller, not the owner, of playback: the engine is an app-wide
 * singleton kept alive by [BackgroundPlayback], so the metronome keeps running when the
 * screen is closed and a new ViewModel simply adopts the session that is already playing.
 */
@HiltViewModel
class MetronomeViewModel @Inject constructor(
    private val player: MetronomePlayer,
    observePresets: ObservePresetsUseCase,
    private val savePreset: SavePresetUseCase,
    private val deletePreset: DeletePresetUseCase,
    private val settings: SettingsStore,
    private val clock: MonotonicClock,
    private val backgroundPlayback: BackgroundPlayback,
) : ViewModel() {

    private val tapTempo = TapTempoCalculator()
    private var tapHintJob: Job? = null

    /** Session id of the playback run we started; events from any other run are ignored. */
    private var activeSession = MetronomePlayer.NO_SESSION

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<MetronomeUiState> = _state.asStateFlow()

    /** Last-used settings, unless the engine is already running: then its settings win. */
    private fun initialState(): MetronomeUiState {
        val running = player.playback.value
        if (running != null) {
            activeSession = running.sessionId
            return MetronomeUiState(config = running.config, volume = running.volume, isPlaying = true)
        }
        // The simplified UI offers no subdivision, sound or volume choice, so a value stored
        // before that change (or by a future advanced mode) must not keep clicking with no way
        // off, or stay silent with no way back up: the volume is the default, not the stored one.
        // The same goes for a tempo outside the ring's range, from an older, wider ring.
        val stored = settings.loadMetronomeConfig()
        val config = stored.copy(
            bpm = stored.bpm.coerceIn(TempoBand.MIN_BPM, TempoBand.MAX_BPM),
            subdivision = Subdivision.NONE,
            sound = SoundProfile.WOODBLOCK,
        )
        return MetronomeUiState(config = config)
    }

    init {
        viewModelScope.launch {
            observePresets().collect { presets -> _state.update { it.copy(presets = presets) } }
        }
        // The engine can stop on its own (audio focus lost, headphones unplugged, output failure).
        viewModelScope.launch {
            player.sessionEnded.collect { session ->
                if (session == activeSession) {
                    activeSession = MetronomePlayer.NO_SESSION
                    _state.update { it.copy(isPlaying = false, currentBeat = -1, currentStrength = null) }
                }
            }
        }
        viewModelScope.launch {
            player.beatEvents.collect { event ->
                if (event.sessionId != activeSession) return@collect
                // Hold the visual until the click is actually audible (compensates output latency).
                val waitMillis = ((event.audibleAtNanos - System.nanoTime()) / 1_000_000)
                    .coerceIn(0L, MAX_VISUAL_WAIT_MS)
                if (waitMillis > 0) delay(waitMillis)
                // Playback may have stopped while we waited: never light an LED on a stopped metronome.
                if (event.sessionId != activeSession || !_state.value.isPlaying) return@collect
                if (event.click.strength != BeatStrength.SUBDIVISION) {
                    _state.update {
                        it.copy(
                            currentBeat = event.click.beatIndex,
                            currentStrength = event.click.strength,
                            beatPulse = it.beatPulse + 1,
                        )
                    }
                }
            }
        }
    }

    // ---- Transport -------------------------------------------------------------------------

    fun togglePlayback() {
        if (_state.value.isPlaying) stop() else start()
    }

    private fun start() {
        val current = _state.value
        val session = player.start(current.config, current.volume)
        if (session == MetronomePlayer.NO_SESSION) {
            _state.update { it.copy(userMessage = MetronomeMessage.AUDIO_UNAVAILABLE) }
            return
        }
        activeSession = session
        backgroundPlayback.start()
        tapHintJob?.cancel()
        _state.update { it.copy(isPlaying = true, currentBeat = -1, currentStrength = null, tapCount = 0) }
    }

    private fun stop() {
        activeSession = MetronomePlayer.NO_SESSION
        player.stop()
        _state.update { it.copy(isPlaying = false, currentBeat = -1, currentStrength = null) }
    }

    // ---- Settings --------------------------------------------------------------------------

    fun setBpm(bpm: Int) = updateConfig { it.withBpm(bpm) }

    fun adjustBpm(delta: Int) = updateConfig { it.withBpm(it.bpm + delta) }

    fun setTimeSignature(timeSignature: TimeSignature) = updateConfig { it.copy(timeSignature = timeSignature) }

    fun setSubdivision(subdivision: Subdivision) = updateConfig { it.copy(subdivision = subdivision) }

    fun setSound(sound: SoundProfile) = updateConfig { it.copy(sound = sound) }

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _state.update { it.copy(volume = clamped) }
        settings.saveVolume(clamped)
        player.setVolume(clamped)
    }

    /** Call on every tap of the Tap Tempo button. */
    fun tapTempo() {
        val bpm = tapTempo.tap(clock.nowMillis())
        _state.update { it.copy(tapCount = tapTempo.tapCount) }
        if (bpm != null) setBpm(bpm)
        tapHintJob?.cancel()
        tapHintJob = viewModelScope.launch {
            delay(TAP_HINT_MS)
            _state.update { it.copy(tapCount = 0) }
        }
    }

    private fun updateConfig(transform: (MetronomeConfig) -> MetronomeConfig) {
        val newState = _state.updateAndGet { it.copy(config = transform(it.config)) }
        settings.saveMetronomeConfig(newState.config)
        if (newState.isPlaying) player.update(newState.config)
    }

    // ---- Presets ---------------------------------------------------------------------------

    fun savePreset(name: String) {
        if (name.isBlank()) return
        val config = _state.value.config
        viewModelScope.launch {
            runCatching { savePreset(name, config) }
                .onFailure { _state.update { it.copy(userMessage = MetronomeMessage.SAVE_FAILED) } }
        }
    }

    fun loadPreset(preset: Preset) = updateConfig { preset.config }

    fun deletePreset(id: Long) {
        viewModelScope.launch {
            runCatching { deletePreset.invoke(id) }
                .onFailure { _state.update { it.copy(userMessage = MetronomeMessage.DELETE_FAILED) } }
        }
    }

    fun dismissMessage() = _state.update { it.copy(userMessage = null) }

    // No onCleared(): closing the screen must not stop the metronome. Playback ends from the
    // Stop button, the notification, the task being swiped away, or the engine itself.

    companion object {
        /** Guards against a bogus audio timestamp freezing the indicator. */
        const val MAX_VISUAL_WAIT_MS = 1_000L
        /** How long the "N taps" hint stays after the last tap. */
        const val TAP_HINT_MS = 2_500L
    }
}
