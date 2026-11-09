// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.metronome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hr.bertlab.fluteassistant.audio.metronome.MetronomePlayer
import hr.bertlab.fluteassistant.domain.model.MetronomeConfig
import hr.bertlab.fluteassistant.domain.model.TimeSignature
import hr.bertlab.fluteassistant.domain.repository.SettingsStore
import hr.bertlab.fluteassistant.playback.BackgroundPlayback
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
 * Engine calls are forwarded synchronously; the last-used settings go to [SettingsStore], so
 * they survive process death.
 *
 * The ViewModel is a controller, not the owner, of playback: the engine is an app-wide
 * singleton kept alive by [BackgroundPlayback], so the metronome keeps running when the
 * screen is closed and a new ViewModel simply adopts the session that is already playing.
 */
@HiltViewModel
class MetronomeViewModel @Inject constructor(
    private val player: MetronomePlayer,
    private val settings: SettingsStore,
    private val backgroundPlayback: BackgroundPlayback,
) : ViewModel() {

    /** Session id of the playback run we started; events from any other run are ignored. */
    private var activeSession = MetronomePlayer.NO_SESSION

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<MetronomeUiState> = _state.asStateFlow()

    /** Last-used settings, unless the engine is already running: then its settings win. */
    private fun initialState(): MetronomeUiState {
        val running = player.playback.value
        if (running != null) {
            activeSession = running.sessionId
            return MetronomeUiState(config = running.config, isPlaying = true)
        }
        // A tempo stored by an older, wider ring is brought onto this one.
        val stored = settings.loadMetronomeConfig()
        return MetronomeUiState(config = stored.copy(bpm = stored.bpm.coerceIn(TempoBand.MIN_BPM, TempoBand.MAX_BPM)))
    }

    init {
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

    // ---- Transport -------------------------------------------------------------------------

    fun togglePlayback() {
        if (_state.value.isPlaying) stop() else start()
    }

    private fun start() {
        val session = player.start(_state.value.config)
        if (session == MetronomePlayer.NO_SESSION) {
            _state.update { it.copy(userMessage = MetronomeMessage.AUDIO_UNAVAILABLE) }
            return
        }
        activeSession = session
        backgroundPlayback.start()
        _state.update { it.copy(isPlaying = true, currentBeat = -1, currentStrength = null) }
    }

    private fun stop() {
        activeSession = MetronomePlayer.NO_SESSION
        player.stop()
        _state.update { it.copy(isPlaying = false, currentBeat = -1, currentStrength = null) }
    }

    // ---- Settings --------------------------------------------------------------------------

    fun setBpm(bpm: Int) = updateConfig { it.withBpm(bpm) }

    fun setTimeSignature(timeSignature: TimeSignature) = updateConfig { it.copy(timeSignature = timeSignature) }

    private fun updateConfig(transform: (MetronomeConfig) -> MetronomeConfig) {
        val newState = _state.updateAndGet { it.copy(config = transform(it.config)) }
        settings.saveMetronomeConfig(newState.config)
        if (newState.isPlaying) player.update(newState.config)
    }

    fun dismissMessage() = _state.update { it.copy(userMessage = null) }

    // No onCleared(): closing the screen must not stop the metronome. Playback ends from the
    // Stop button, the notification, the task being swiped away, or the engine itself.

    companion object {
        /** Guards against a bogus audio timestamp freezing the indicator. */
        const val MAX_VISUAL_WAIT_MS = 1_000L
    }
}
