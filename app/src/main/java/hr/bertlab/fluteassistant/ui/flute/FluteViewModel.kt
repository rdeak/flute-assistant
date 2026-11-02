package hr.bertlab.fluteassistant.ui.flute

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hr.bertlab.fluteassistant.audio.flute.FluteTonePlayer
import hr.bertlab.fluteassistant.core.MonotonicClock
import hr.bertlab.fluteassistant.core.log.LogTags
import hr.bertlab.fluteassistant.core.log.Logger
import hr.bertlab.fluteassistant.domain.flute.NoteLength
import hr.bertlab.fluteassistant.domain.flute.Pulse
import hr.bertlab.fluteassistant.domain.flute.Tune
import hr.bertlab.fluteassistant.domain.flute.TuneBreath
import hr.bertlab.fluteassistant.domain.flute.TuneItem
import hr.bertlab.fluteassistant.domain.flute.TuneNote
import hr.bertlab.fluteassistant.domain.flute.TuneRest
import hr.bertlab.fluteassistant.domain.model.MetronomeConfig
import hr.bertlab.fluteassistant.domain.notes.StaffNotes
import hr.bertlab.fluteassistant.domain.repository.SettingsStore
import hr.bertlab.fluteassistant.domain.tuner.NoteMath
import hr.bertlab.fluteassistant.ui.metronome.TempoBand
import hr.bertlab.fluteassistant.ui.tuner.TunerViewModel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The flute tab: a key pressed sounds through [FluteTonePlayer] until it is released and is
 * appended to the tune; its [NoteLength] grows while the key is held, in beats of the
 * metronome's tempo (an eighth at first, then a quarter at ¾ of a beat and so on, see
 * [Tune.growAtMs]), and the release records the length the hold makes ([Tune.lengthOf]), the
 * same one the screen was showing. The rest key works the same way without a sound: held, it
 * grows a [TuneRest]. The breath key adds a [TuneBreath], a sign for the player with no length
 * (never two in a row). One Play button plays the tune back at the metronome's tempo, silent
 * through each rest and straight past each breath; erase drops the last step. A press of any
 * key ends whatever key was held before, so the newest press wins. The tempo is the metronome tab's, read from
 * [SettingsStore] at each press and at Play, counted in its time signature's beats as the
 * metronome clicks them ([Pulse]: in 6/8 a click is an eighth note); the time signature, which
 * the staff is written in, is also read when the tab opens. The tune is saved through [SettingsStore] on every
 * change and restored, sanitised, on creation. Activity-scoped, so the tune survives tab
 * switches and rotation; [enterTab] and [leaveTab] follow the tab's visibility so the player is
 * open only while the flute is on screen.
 *
 * Logs under [LogTags.FLUTE]: `flute.note` per note ended (midi, heldMs, length, added: false
 * at the cap), `flute.rest` per rest ended (heldMs, length, added), `flute.breath` (added: false
 * at the cap or right after a breath), `flute.play` (notes, rests, bpm, time), `flute.play_done`,
 * `flute.stop` (reason: pill, key, erase or leave), `flute.erase` (left).
 */
@HiltViewModel
class FluteViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val tonePlayer: FluteTonePlayer,
    private val clock: MonotonicClock,
    private val logger: Logger,
) : ViewModel() {

    private val _state = MutableStateFlow(
        FluteUiState(tune = Tune.sanitize(settings.loadTune()), timeSignature = settings.loadMetronomeConfig().timeSignature),
    )
    val state: StateFlow<FluteUiState> = _state.asStateFlow()

    /** When the held key (a note or the rest) went down, so its release can record how long it was held. */
    private var pressedAt: Long? = null
    /** The pulse the held key's note or rest is counted in, read at the press. */
    private var pressPulse: Pulse = Pulse(MetronomeConfig.DEFAULT_BPM)
    /** Grows the held key's note or rest at each of [Tune.growAtMs] while the key stays down. */
    private var growJob: Job? = null
    private var playbackJob: Job? = null
    private var activationJob: Job? = null

    /**
     * The tab came on screen: open the output now so the first press is not late, and write the
     * staff in the metronome tab's time signature, which may have changed there meanwhile.
     */
    fun enterTab() {
        _state.update { it.copy(timeSignature = settings.loadMetronomeConfig().timeSignature) }
        tonePlayer.open()
    }

    /** The tab left the screen: silence everything, keep the tune, give the output back. */
    fun leaveTab() {
        stopPlayback(reason = "leave")
        endHeld()
        tonePlayer.close()
    }

    /** A finger went down on the key of [midi]: sound it at once and add it to the tune. */
    fun onKeyPressed(midi: Int) {
        if (midi !in StaffNotes.ALL) return
        stopPlayback(reason = "key")
        val now = clock.nowMillis()
        finishHeld(now) // a held note: the new note takes the voice over, no note off, so nothing clicks
        activationJob?.cancel()
        tonePlayer.noteOn(NoteMath.midiToFrequency(midi), TunerViewModel.REFERENCE_TONE_VOLUME)
        startHold(TuneNote(midi, NoteLength.EIGHTH), now)
    }

    /** The finger lifted off (or slid off) the key of [midi]; ignored unless that key is the one sounding. */
    fun onKeyReleased(midi: Int) {
        if (midi != _state.value.heldMidi) return
        release(heldFor(clock.nowMillis()))
    }

    /** A screen reader activated the key of [midi]: sound it for one beat and add it, a quarter note. */
    fun onKeyActivated(midi: Int) {
        onKeyPressed(midi)
        if (_state.value.heldMidi != midi) return
        val beat = Tune.durationMs(NoteLength.QUARTER, pressPulse)
        activationJob = viewModelScope.launch {
            delay(beat)
            if (_state.value.heldMidi == midi) release(beat)
        }
    }

    /** A finger went down on the rest key: silence a held note and add a rest that grows while the key stays down. */
    fun onRestPressed() {
        stopPlayback(reason = "key")
        endHeld()
        activationJob?.cancel()
        startHold(TuneRest(NoteLength.EIGHTH), clock.nowMillis())
    }

    /** The finger lifted off (or slid off) the rest key; ignored unless the rest is the one held. */
    fun onRestReleased() {
        if (!_state.value.restHeld) return
        record(heldFor(clock.nowMillis()))
    }

    /** A screen reader activated the rest key: a rest of one beat, a quarter rest. */
    fun onRestActivated() {
        onRestPressed()
        val beat = Tune.durationMs(NoteLength.QUARTER, pressPulse)
        activationJob = viewModelScope.launch {
            delay(beat)
            if (_state.value.restHeld) record(beat)
        }
    }

    /** The breath key: ends a held key and marks a breath, unless the last step is one already. */
    fun onBreath() {
        stopPlayback(reason = "key")
        endHeld()
        activationJob?.cancel()
        val current = _state.value
        val added = !current.atCap && current.tune.lastOrNull() != TuneBreath
        _state.update { s -> s.copy(tune = if (added) s.tune + TuneBreath else s.tune, shown = Shown.Breath) }
        if (added) save()
        logger.d(LogTags.FLUTE, "flute.breath", "added" to added)
    }

    /** Plays the tune from the first step, or stops a playback in progress. Nothing to play: nothing happens. */
    fun togglePlayback() {
        if (_state.value.isPlaying) {
            stopPlayback(reason = "pill")
            return
        }
        if (_state.value.isEmpty) return
        endHeld()
        activationJob?.cancel()
        val items = _state.value.tune
        val pulse = pulse()
        logger.d(
            LogTags.FLUTE,
            "flute.play",
            "notes" to items.count { it is TuneNote },
            "rests" to items.count { it is TuneRest },
            "bpm" to pulse.bpm,
            "time" to pulse.timeSignature.label,
        )
        // Undispatched, so the first note sounds and lights before this call returns.
        playbackJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            for ((index, item) in items.withIndex()) {
                when (item) {
                    is TuneNote -> {
                        val duration = Tune.durationMs(item.length, pulse)
                        val gap = Tune.gapMs(duration)
                        _state.update { it.copy(playingIndex = index, shown = Shown.Note(item.midi)) }
                        tonePlayer.noteOn(NoteMath.midiToFrequency(item.midi), TunerViewModel.REFERENCE_TONE_VOLUME)
                        delay(duration - gap)
                        tonePlayer.noteOff()
                        delay(gap)
                    }
                    is TuneRest -> {
                        _state.update { it.copy(playingIndex = index, shown = Shown.Rest) }
                        delay(Tune.durationMs(item.length, pulse))
                    }
                    TuneBreath -> Unit // a sign for the player: no time
                }
            }
            playbackJob = null
            _state.update { it.copy(playingIndex = null) }
            logger.d(LogTags.FLUTE, "flute.play_done")
        }
    }

    /** Drops the last step of the tune; ignored while a key is held or the tune is empty. Stops playback first. */
    fun eraseLast() {
        stopPlayback(reason = "erase")
        val current = _state.value
        if (current.isHolding || current.isEmpty) return
        _state.update { it.copy(tune = it.tune.dropLast(1)) }
        save()
        logger.d(LogTags.FLUTE, "flute.erase", "left" to _state.value.tune.size)
    }

    /** Ends a playback in progress, saying what ended it; nothing playing: nothing happens. */
    private fun stopPlayback(reason: String) {
        if (!_state.value.isPlaying) return
        playbackJob?.cancel()
        playbackJob = null
        tonePlayer.noteOff()
        _state.update { it.copy(playingIndex = null) }
        logger.d(LogTags.FLUTE, "flute.stop", "reason" to reason)
    }

    /**
     * The key of [first] (a note or a rest, an eighth so far) went down at [now]: add it, show it,
     * and grow it at each of [Tune.growAtMs] while the key stays down. At the cap it is shown but
     * not added.
     */
    private fun startHold(first: TuneItem, now: Long) {
        pressedAt = now
        pressPulse = pulse()
        val heldMidi = (first as? TuneNote)?.midi
        _state.update { s ->
            val held = s.copy(
                heldMidi = heldMidi,
                restHeld = first is TuneRest,
                shown = heldMidi?.let(Shown::Note) ?: Shown.Rest,
                heldLength = NoteLength.EIGHTH,
            )
            if (s.atCap) held.copy(heldIndex = null) else held.copy(tune = s.tune + first, heldIndex = s.tune.size)
        }
        save()
        val pulse = pressPulse
        growJob = viewModelScope.launch {
            var waited = 0L
            for (at in Tune.growAtMs(pulse)) {
                delay(at - waited)
                waited = at
                grow(Tune.lengthOf(at, pulse))
            }
        }
    }

    /** Silences the held note key and records how long it was held. */
    private fun release(heldMs: Long) {
        tonePlayer.noteOff()
        record(heldMs)
    }

    /** Ends whatever key is held now, silencing a note; nothing held: nothing happens. */
    private fun endHeld() {
        if (_state.value.heldMidi != null) tonePlayer.noteOff()
        finishHeld(clock.nowMillis())
    }

    /** Ends the held key's note or rest in the tune, at [now], without touching the sound; nothing held: nothing happens. */
    private fun finishHeld(now: Long) {
        if (!_state.value.isHolding) return
        record(heldFor(now))
    }

    private fun heldFor(now: Long): Long = (now - (pressedAt ?: now)).coerceAtLeast(0)

    /** The held key's note or rest reached [length]: show it on the staff, its shape and its room growing. */
    private fun grow(length: NoteLength) {
        _state.update { s -> s.copy(tune = s.withHeldLength(length), heldLength = length) }
    }

    /** Records the held note or rest at the length [heldMs] makes and lets go of the key. */
    private fun record(heldMs: Long) {
        growJob?.cancel()
        growJob = null
        val length = Tune.lengthOf(heldMs, pressPulse)
        val held = _state.value
        val added = held.heldIndex != null // false at the cap, where the press was not added
        _state.update { s ->
            s.copy(tune = s.withHeldLength(length), heldMidi = null, restHeld = false, heldIndex = null, heldLength = null)
        }
        pressedAt = null
        save()
        val midi = held.heldMidi
        if (midi != null) {
            logger.d(LogTags.FLUTE, "flute.note", "midi" to midi, "heldMs" to heldMs, "length" to length, "added" to added)
        } else {
            logger.d(LogTags.FLUTE, "flute.rest", "heldMs" to heldMs, "length" to length, "added" to added)
        }
    }

    /** The tune with the held key's note or rest set to [length]; unchanged at the cap, where it was not added. */
    private fun FluteUiState.withHeldLength(length: NoteLength): List<TuneItem> {
        val index = heldIndex ?: return tune
        val resized = when (val item = tune[index]) {
            is TuneNote -> item.copy(length = length)
            is TuneRest -> item.copy(length = length)
            TuneBreath -> return tune
        }
        return tune.toMutableList().also { it[index] = resized }
    }

    /**
     * The metronome tab's pulse: its tempo, in its ring's range (a stored one from an older build
     * may lie outside), counted in its time signature's beats as the metronome clicks them.
     */
    private fun pulse(): Pulse {
        val config = settings.loadMetronomeConfig()
        return Pulse(config.bpm.coerceIn(TempoBand.MIN_BPM, TempoBand.MAX_BPM), config.timeSignature)
    }

    private fun save() = settings.saveTune(_state.value.tune)

    override fun onCleared() {
        playbackJob?.cancel()
        activationJob?.cancel()
        growJob?.cancel()
        tonePlayer.close()
    }
}
