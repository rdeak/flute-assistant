// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.tuner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hr.bertlab.fluteassistant.audio.TONE_VOLUME
import hr.bertlab.fluteassistant.audio.tuner.ReferenceTonePlayer
import hr.bertlab.fluteassistant.audio.tuner.TunerEngine
import hr.bertlab.fluteassistant.core.log.LogTags
import hr.bertlab.fluteassistant.core.log.Logger
import hr.bertlab.fluteassistant.domain.listen.FixHold
import hr.bertlab.fluteassistant.domain.listen.FrameDecision
import hr.bertlab.fluteassistant.domain.listen.FrameSummary
import hr.bertlab.fluteassistant.domain.listen.Hearing
import hr.bertlab.fluteassistant.domain.listen.HearingTracker
import hr.bertlab.fluteassistant.domain.listen.ListenRules
import hr.bertlab.fluteassistant.domain.listen.PitchFrame
import hr.bertlab.fluteassistant.domain.listen.ReadingTracker
import hr.bertlab.fluteassistant.domain.listen.TunerAdvice
import hr.bertlab.fluteassistant.domain.settings.SettingsStore
import hr.bertlab.fluteassistant.domain.tuner.FluteRange
import hr.bertlab.fluteassistant.domain.tuner.NoteMath
import hr.bertlab.fluteassistant.domain.tuner.TunerMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The tuner's wiring: feeds the microphone's [PitchFrame]s to the rules and mirrors what they say into a stable
 * [TunerUiState].
 * - [ReadingTracker] smooths the frames into the reading and the identified note.
 * - [HearingTracker] tells when sound keeps coming without ever becoming a note ([Hearing]), so the screen can say
 *   what to change.
 * - [FixHold] keeps a fix on the flute on the line a while after the sound stops.
 *
 * It also writes the tuner's structured log under [LogTags.TUNER]: what became of every frame
 * ([FrameDecision], verbose), a `tuner.summary` every [ListenRules.SUMMARY_FRAMES] frames, `tuner.reading`
 * when the shown note changes, `tuner.hearing` when the hearing changes, and the child's
 * choices, so a session on a real phone can be read back without a debugger.
 */
@HiltViewModel
class TunerViewModel @Inject constructor(
    private val engine: TunerEngine,
    private val settings: SettingsStore,
    private val tonePlayer: ReferenceTonePlayer,
    private val logger: Logger,
) : ViewModel() {

    private val _state = MutableStateFlow(
        TunerUiState(targetMidi = FluteRange.clamp(settings.loadTargetNote()), mode = settings.loadTunerMode()),
    )
    val state: StateFlow<TunerUiState> = _state.asStateFlow()

    private var listenJob: Job? = null
    private var playJob: Job? = null
    private val readings = ReadingTracker()
    private val hearingTracker = HearingTracker()
    private val fixHold = FixHold()
    private val summary = FrameSummary()
    private var loggedReadingMidi: Int? = null

    fun onPermissionRequested() {
        _state.update { it.copy(permissionRequested = true) }
    }

    /**
     * The phone's answer to the question itself (the first opening, or the card's "Yes"). A no the
     * phone will not ask about again ([canAskAgain] false) leaves its settings as the only way, so
     * the card offers them from then on ([TunerUiState.micBlocked]); a no it will ask about again
     * takes the offer back.
     */
    fun onPermissionAnswer(granted: Boolean, canAskAgain: Boolean) {
        onPermissionResult(granted)
        if (!granted) _state.update { it.copy(micBlocked = !canAskAgain) }
    }

    /** What the screen finds when it looks (on appearing, on coming back from the phone's settings): a yes ends the block. */
    fun onPermissionResult(granted: Boolean) {
        if (granted != _state.value.permissionGranted) logger.i(LogTags.TUNER, "tuner.permission", "granted" to granted)
        _state.update { it.copy(permissionGranted = granted, micBlocked = it.micBlocked && !granted) }
        if (!granted) stopListening()
    }

    /** Opens the microphone. Safe to call repeatedly; no-op without permission. */
    fun startListening() {
        if (!_state.value.permissionGranted || listenJob?.isActive == true) return
        listenJob = viewModelScope.launch {
            _state.update { it.copy(isListening = true, micFailed = false) }
            try {
                engine.pitchFrames().collect(::onFrame)
                _state.update { it.copy(isListening = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.w(LogTags.TUNER, "tuner.error", "message" to e.message, error = e)
                clearHearing()
                readings.reset()
                _state.update { it.copy(isListening = false, reading = null, micFailed = true) }
            }
        }
    }

    fun stopListening() {
        stopTone()
        listenJob?.cancel()
        listenJob = null
        readings.reset()
        clearHearing()
        _state.update { it.copy(isListening = false, reading = null) }
        if (summary.frames > 0) logSummary() // the last partial second, before the child left
        logReadingIfChanged()
    }

    /** Chooses the note the gauge measures against; clamped to the flute's range and remembered. */
    fun setTargetNote(midi: Int) {
        val clamped = FluteRange.clamp(midi)
        settings.saveTargetNote(clamped)
        logger.d(LogTags.TUNER, "tuner.target", "midi" to clamped)
        _state.update { it.copy(targetMidi = clamped) }
    }

    /**
     * Sounds the chosen note for [REFERENCE_TONE_MS] at [TONE_VOLUME]. Readings pause meanwhile
     * (the microphone would otherwise measure the speaker) and start afresh when it ends. Only when a tap on the
     * window plays the note ([TunerUiState.tapPlaysNote]: Tune mode, in silence); otherwise it does nothing.
     */
    fun playTargetNote() {
        val current = _state.value
        if (!current.tapPlaysNote) return
        logger.d(LogTags.TUNER, "tuner.reference", "midi" to current.targetMidi, "ms" to REFERENCE_TONE_MS, "volume" to TONE_VOLUME)
        tonePlayer.play(
            NoteMath.midiToFrequency(current.targetMidi),
            REFERENCE_TONE_MS,
            TONE_VOLUME,
        )
        readings.reset()
        clearHearing()
        _state.update { it.copy(isPlayingReference = true, reading = null) }
        playJob = viewModelScope.launch {
            delay(REFERENCE_TONE_MS.toLong())
            tonePlayer.stop() // the data has played out; release the track rather than leave it registered as playing
            readings.reset()
            clearHearing()
            _state.update { it.copy(isPlayingReference = false) }
        }
    }

    private fun stopTone() {
        playJob?.cancel()
        playJob = null
        tonePlayer.stop()
        _state.update { it.copy(isPlayingReference = false) }
    }

    /**
     * Opens the guide to tuning the flute on [card]: the one the tuner's line names (`TunerAdvice.guideCard`), the only
     * way in since 2026-11-18, when the How? button went. The microphone stays on underneath.
     */
    fun openGuide(card: GuideCard) {
        logger.d(LogTags.TUNER, "tuner.guide", "action" to "open", "page" to card.ordinal, "via" to "line")
        dropHeldFix() // it has led here; the line starts afresh when the guide closes
        _state.update { it.copy(guideCard = card) }
    }

    /** Shows [card] of an open guide; does nothing while it is closed. */
    fun showGuideCard(card: GuideCard) {
        if (_state.value.guideCard == null) return
        logger.d(LogTags.TUNER, "tuner.guide", "action" to "page", "page" to card.ordinal)
        _state.update { it.copy(guideCard = card) }
    }

    /** Closes the guide, logging the card it was left on and [via] what; does nothing while it is closed. */
    fun closeGuide(via: GuideExit) {
        val card = _state.value.guideCard ?: return
        logger.d(LogTags.TUNER, "tuner.guide", "action" to "close", "page" to card.ordinal, "via" to via.name.lowercase())
        _state.update { it.copy(guideCard = null) }
    }

    /**
     * Opens [midi] in Tune mode, as a tap on a flute of the Fingers tab asks (since 2026-11-18): the mode and the note
     * set as the child would set them, both remembered and logged as theirs, and an open guide closed, so the tab
     * shows the note's window and not a card.
     */
    fun tune(midi: Int) {
        closeGuide(GuideExit.FINGERS)
        if (_state.value.mode != TunerMode.TARGET) setMode(TunerMode.TARGET)
        setTargetNote(midi)
    }

    /** Switches between naming the heard note and measuring against the chosen one; remembered. */
    fun setMode(mode: TunerMode) {
        settings.saveTunerMode(mode)
        logger.d(LogTags.TUNER, "tuner.mode", "mode" to mode)
        _state.update { it.copy(mode = mode) }
    }

    private fun onFrame(frame: PitchFrame) {
        val decision = process(frame)
        if (logger.isVerbose(LogTags.TUNER)) {
            logger.v(
                LogTags.TUNER, "tuner.frame",
                "f" to frame.frequency, "p" to frame.probability, "level" to frame.level, "decision" to decision,
            )
        }
        updateHearing(hearingTracker.onFrame(frame.level, decision), hearingTracker.decidingMean)
        holdFix()
        summary.add(frame, decision)
        if (summary.frames >= ListenRules.SUMMARY_FRAMES) logSummary()
        logReadingIfChanged()
    }

    /** Folds one frame into the state and says what became of it; none is read while the reference tone sounds. */
    private fun process(frame: PitchFrame): FrameDecision {
        if (_state.value.isPlayingReference) return FrameDecision.IGNORED
        val decision = readings.onFrame(frame)
        _state.update { it.copy(reading = readings.reading, identifiedMidi = readings.identifiedMidi) }
        return decision
    }

    /**
     * Mirrors the tracker's verdict into the state; `tuner.hearing` is logged only when it changes,
     * with [levelMean]: the mean that decided it, and for [Hearing.CLEAR] the run's mean as it ended.
     */
    private fun updateHearing(hearing: Hearing, levelMean: Double) {
        if (hearing == _state.value.hearing) return
        _state.update { it.copy(hearing = hearing) }
        logger.d(LogTags.TUNER, "tuner.hearing", "hearing" to hearing, "levelMean" to levelMean)
    }

    /** Ends any unclear-sound run and drops a held fix, for when the listening starts afresh or the microphone is not the child's. */
    private fun clearHearing() {
        hearingTracker.reset()
        updateHearing(Hearing.CLEAR, hearingTracker.decidingMean)
        dropHeldFix()
    }

    /** After a frame: a fix on the flute stays on the line through the silence that follows it ([FixHold]). */
    private fun holdFix() {
        val now = _state.value
        val held = fixHold.onFrame(now.advice, now.isPlainSilence)
        if (held != now.heldFix) _state.update { it.copy(heldFix = held) }
    }

    private fun dropHeldFix() {
        fixHold.clear()
        _state.update { it.copy(heldFix = TunerAdvice.NONE) }
    }

    private fun logSummary() {
        val s = _state.value
        logger.d(
            LogTags.TUNER, "tuner.summary",
            *summary.fields(), "gate" to ListenRules.MIN_PROBABILITY,
            "reading" to s.reading?.midi, "cents" to s.reading?.cents, "target" to s.targetMidi, "mode" to s.mode,
            "hearing" to s.hearing,
        )
        summary.reset()
    }

    /** `tuner.reading` when the shown note changes or clears; a wobble within the note is not logged. */
    private fun logReadingIfChanged() {
        val s = _state.value
        val midi = s.reading?.midi
        if (midi == loggedReadingMidi) return
        loggedReadingMidi = midi
        logger.d(
            LogTags.TUNER, "tuner.reading",
            "midi" to midi, "cents" to s.reading?.cents, "f" to s.reading?.frequency, "target" to s.targetMidi, "mode" to s.mode,
        )
    }

    override fun onCleared() {
        stopTone()
        listenJob?.cancel()
    }

    companion object {
        /** How long the reference note sounds. */
        const val REFERENCE_TONE_MS = 2000
    }
}
