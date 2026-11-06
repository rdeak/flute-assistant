// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.tuner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hr.bertlab.fluteassistant.audio.tuner.PitchFrame
import hr.bertlab.fluteassistant.audio.tuner.ReferenceTonePlayer
import hr.bertlab.fluteassistant.audio.tuner.TunerEngine
import hr.bertlab.fluteassistant.core.log.LogTags
import hr.bertlab.fluteassistant.core.log.Logger
import hr.bertlab.fluteassistant.domain.repository.SettingsStore
import hr.bertlab.fluteassistant.domain.tuner.FluteRange
import hr.bertlab.fluteassistant.domain.tuner.NoteMath
import hr.bertlab.fluteassistant.domain.tuner.NoteReading
import hr.bertlab.fluteassistant.domain.tuner.TunerMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.log2

/**
 * Smooths raw [PitchFrame]s into a stable [TunerUiState]:
 * - shows a reading only once a few consecutive frames agree (a held note, not room noise),
 * - keeps a short median window so a single mis-detected frame does not flick the needle,
 * - ignores low-confidence frames,
 * - holds the last reading for a few silent or unsteady frames so the display does not blink
 *   between notes; a wandering pitch expires the reading after [HOLD_FRAMES] like silence does,
 *   and only a steady note resets that count,
 * - keeps the identified note (the ring of [TunerMode.IDENTIFY]) through a drift just past the
 *   halfway point to a neighbouring note until it has lasted [SWITCH_FRAMES],
 * - tells the screen when sound keeps coming without ever becoming a note ([Hearing], judged
 *   by [HearingTracker]), so it can say what to change.
 *
 * It also writes the tuner's structured log under [LogTags.TUNER]: what became of every frame
 * ([FrameDecision], verbose), a `tuner.summary` every [SUMMARY_FRAMES] frames, `tuner.reading`
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

    // The simplified UI cannot show or change the A4 reference, so a stale stored value must
    // not silently relabel every note: start from the standard 440 Hz (the stored value is
    // left for a future advanced mode, which would call setReferenceA4 explicitly). The
    // target note and the mode, by contrast, are on screen, so the stored ones are honoured.
    private val _state = MutableStateFlow(
        TunerUiState(targetMidi = FluteRange.clamp(settings.loadTargetNote()), mode = settings.loadTunerMode()),
    )
    val state: StateFlow<TunerUiState> = _state.asStateFlow()

    private var listenJob: Job? = null
    private var playJob: Job? = null
    private val recentFrequencies = ArrayDeque<Double>()
    /** Frames in a row without a steady note (silent, uncertain or unsteady); [HOLD_FRAMES] of them clear the reading. */
    private var silentFrames = 0
    /** Shown frames in a row whose note is not the identified one but still within reach of it; see [identify]. */
    private var driftFrames = 0
    private val hearingTracker = HearingTracker()
    private val summary = FrameSummary()
    private var loggedReadingMidi: Int? = null

    fun onPermissionRequested() {
        _state.update { it.copy(permissionRequested = true) }
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted != _state.value.permissionGranted) logger.i(LogTags.TUNER, "tuner.permission", "granted" to granted)
        _state.update { it.copy(permissionGranted = granted) }
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
                _state.update { it.copy(isListening = false, reading = null, micFailed = true) }
            }
        }
    }

    fun stopListening() {
        stopTone()
        listenJob?.cancel()
        listenJob = null
        recentFrequencies.clear()
        silentFrames = 0
        clearHearing()
        _state.update { it.copy(isListening = false, reading = null, level = 0f) }
        if (summary.frames > 0) logSummary() // the last partial second, before the child left
        logReadingIfChanged()
    }

    fun setReferenceA4(hz: Double) {
        val clamped = hz.coerceIn(MIN_A4, MAX_A4)
        settings.saveReferenceA4(clamped)
        _state.update { s ->
            s.copy(
                referenceA4 = clamped,
                reading = s.reading?.let { NoteMath.frequencyToNote(it.frequency, clamped) },
            )
        }
    }

    /** Chooses the note the gauge measures against; clamped to the flute's range and remembered. */
    fun setTargetNote(midi: Int) {
        val clamped = FluteRange.clamp(midi)
        settings.saveTargetNote(clamped)
        logger.d(LogTags.TUNER, "tuner.target", "midi" to clamped)
        _state.update { it.copy(targetMidi = clamped) }
    }

    /**
     * Sounds the chosen note for [REFERENCE_TONE_MS] at [REFERENCE_TONE_VOLUME]. Readings pause meanwhile
     * (the microphone would otherwise measure the speaker) and start afresh when it ends.
     */
    fun playTargetNote() {
        playJob?.cancel()
        val current = _state.value
        logger.d(LogTags.TUNER, "tuner.reference", "midi" to current.targetMidi, "ms" to REFERENCE_TONE_MS, "volume" to REFERENCE_TONE_VOLUME)
        tonePlayer.play(
            NoteMath.midiToFrequency(current.targetMidi, current.referenceA4),
            REFERENCE_TONE_MS,
            REFERENCE_TONE_VOLUME,
        )
        recentFrequencies.clear()
        clearHearing()
        _state.update { it.copy(isPlayingReference = true, reading = null) }
        playJob = viewModelScope.launch {
            delay(REFERENCE_TONE_MS.toLong())
            tonePlayer.stop() // the data has played out; release the track rather than leave it registered as playing
            recentFrequencies.clear()
            silentFrames = 0
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
        val runMean = hearingTracker.levelMean // read before onFrame, which empties the window when the run ends
        val hearing = hearingTracker.onFrame(frame.level, decision)
        updateHearing(hearing, if (hearing == Hearing.CLEAR) runMean else hearingTracker.levelMean)
        summary.add(frame, decision)
        if (summary.frames >= SUMMARY_FRAMES) logSummary()
        logReadingIfChanged()
    }

    /** Folds one frame into the state and says what became of it. */
    private fun process(frame: PitchFrame): FrameDecision {
        if (_state.value.isPlayingReference) {
            _state.update { it.copy(level = frame.level) }
            return FrameDecision.IGNORED
        }
        val frequency = frame.frequency ?: return onUnpitchedFrame(frame, FrameDecision.SILENT)
        if (frame.probability < MIN_PROBABILITY) return onUnpitchedFrame(frame, FrameDecision.LOW_PROBABILITY)

        recentFrequencies.addLast(frequency)
        while (recentFrequencies.size > SMOOTHING_WINDOW) recentFrequencies.removeFirst()
        if (!isSteady()) {
            // Counts towards the hold like silence: a wandering pitch must not keep the last reading up for as long as it lasts.
            silentFrames++
            _state.update {
                it.copy(reading = if (silentFrames >= HOLD_FRAMES) null else it.reading, level = frame.level)
            }
            return FrameDecision.UNSTEADY
        }
        silentFrames = 0
        val median = recentFrequencies.sorted()[recentFrequencies.size / 2]
        val previous = _state.value
        val reading = NoteMath.frequencyToNote(median, previous.referenceA4)
        val identified = identify(previous, reading)
        _state.update { it.copy(reading = reading, identifiedMidi = identified, level = frame.level) }
        return FrameDecision.SHOWN
    }

    /**
     * The identified note once [reading] is shown. The first note after silence is taken at
     * once, and so is a clearly different note (further than [TunerUiState.NEAR_TARGET_CENTS]
     * from the identified one, such as a clean step to the next note). A sound that has only
     * drifted past the halfway point to a neighbour switches the ring after [SWITCH_FRAMES]
     * shown frames there in a row; one frame back on the identified note starts the count again.
     */
    private fun identify(previous: TunerUiState, reading: NoteReading): Int {
        val current = previous.identifiedMidi.takeIf { previous.reading != null }
        if (current == null || reading.midi == current) {
            driftFrames = 0
            return reading.midi
        }
        val reach = abs(NoteMath.centsBetween(reading.frequency, current, previous.referenceA4))
        if (reach > TunerUiState.NEAR_TARGET_CENTS || ++driftFrames >= SWITCH_FRAMES) {
            driftFrames = 0
            return reading.midi
        }
        return current
    }

    /** Silence or a frame too uncertain to trust: hold the reading for a while, then clear it. */
    private fun onUnpitchedFrame(frame: PitchFrame, decision: FrameDecision): FrameDecision {
        silentFrames++
        if (silentFrames >= HOLD_FRAMES) {
            recentFrequencies.clear()
            _state.update { it.copy(reading = null, level = frame.level) }
        } else {
            _state.update { it.copy(level = frame.level) }
        }
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

    /** Ends any unclear-sound run, for when the listening starts afresh or the microphone is not the child's. */
    private fun clearHearing() {
        val runMean = hearingTracker.levelMean
        hearingTracker.reset()
        updateHearing(Hearing.CLEAR, runMean)
    }

    private fun logSummary() {
        val s = _state.value
        logger.d(
            LogTags.TUNER, "tuner.summary",
            *summary.fields(), "gate" to MIN_PROBABILITY,
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

    /**
     * True once the last [LOCK_FRAMES] frames agree within [LOCK_CENTS]: a held note, as
     * opposed to a syllable, a bump or a voice gliding past, whose pitch moves or stops within
     * that time. Until then the previous reading stays rather than jumping to the stray sound.
     */
    private fun isSteady(): Boolean {
        if (recentFrequencies.size < LOCK_FRAMES) return false
        val last = recentFrequencies.takeLast(LOCK_FRAMES)
        return 1200.0 * log2(last.max() / last.min()) <= LOCK_CENTS
    }

    override fun onCleared() {
        stopTone()
        listenJob?.cancel()
    }

    companion object {
        const val SMOOTHING_WINDOW = 5
        /** A `tuner.summary` log line every this many frames, about once a second. */
        const val SUMMARY_FRAMES = 25
        /** A reading needs this many consecutive frames (~130 ms) that agree within [LOCK_CENTS]. */
        const val LOCK_FRAMES = 3
        const val LOCK_CENTS = 30.0
        /**
         * The second of two confidence gates. [YinPitchDetector] already returns a frame only
         * when its aperiodicity dips under its own threshold (0.2, so probability >= 0.8); this
         * gate then needs 0.85, i.e. aperiodicity under 0.15. Breath noise raises a beginner's
         * aperiodicity into exactly that 0.15 to 0.20 band, and on real transverse-flute
         * recordings with breath noise added the detector found the note in nearly every frame
         * below about 9 dB signal-to-noise while this gate discarded all of them, so the screen
         * showed nothing. Left at 0.85 until a phone capture confirms it: read `tuner.summary`
         * (`pitched` high, `shown` 0, `pMax` under `gate`) in the log. The evidence-backed
         * candidate is YIN 0.3 with this gate at 0.70, which showed every note of a soft
         * low-register recording down to 5 dB without new octave errors (see the tuner
         * target-note spec, revision 4).
         */
        const val MIN_PROBABILITY = 0.85
        /**
         * ~0.9 s at 43 ms per frame. The last reading is held through this many silent,
         * uncertain or unsteady frames in a row; a steady note (a shown frame) resets the count,
         * so a glissando or speech after a note clears the reading too instead of holding it.
         */
        const val HOLD_FRAMES = 20
        /** ~0.5 s: how long a drift past the halfway point must last before the identified note switches. */
        const val SWITCH_FRAMES = 12
        const val MIN_A4 = 415.0
        const val MAX_A4 = 466.0
        /** How long the reference note sounds. */
        const val REFERENCE_TONE_MS = 2000
        /** How loud, in 0..1. Fixed: the app has no volume control, the phone's buttons do that. */
        const val REFERENCE_TONE_VOLUME = 0.8f
    }
}

/** What the tuner did with one microphone frame; logged per frame when verbose and counted in the summary. */
enum class FrameDecision { IGNORED, SILENT, LOW_PROBABILITY, UNSTEADY, SHOWN }
