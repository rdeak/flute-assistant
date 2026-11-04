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
import kotlin.math.roundToLong

/**
 * The flute tab: a key pressed sounds through [FluteTonePlayer] until it is released and is
 * appended to the tune; its [NoteLength] grows while the key is held, in beats of the
 * metronome's tempo (an eighth at first, then a quarter at ¾ of a beat and so on, see
 * [Tune.growAtMs]), and the release records the length the hold makes ([Tune.lengthOf]), the
 * same one the screen was showing. The rest key works the same way without a sound: held, it
 * grows a [TuneRest]. The breath key adds a [TuneBreath], a sign for the player with no length
 * (never two in a row). One Play button plays the tune back at the metronome's tempo, silent
 * through each rest and straight past each breath; erase drops the last step. A press of any
 * key ends whatever key was held before, so the newest press wins. A key let go of and pressed
 * again within [HOLD_REJOIN_MS], nothing else having happened, goes on with the same note
 * ([rejoin]): a touch screen can report one hold as several pieces. The rest key goes on with its
 * rest however long the pause, from the length the rest has: more of the rest key is only more
 * of the same silence, so no piece the screen makes of a hold is ever a rest of its own. The tempo is the metronome tab's, read from
 * [SettingsStore] at each press and at Play, counted in its time signature's beats as the
 * metronome clicks them ([Pulse]: in 6/8 a click is an eighth note); the time signature, which
 * the staff is written in, is also read when the tab opens. The tune is saved through [SettingsStore] on every
 * change and restored, sanitised, on creation. Activity-scoped, so the tune survives tab
 * switches and rotation; [enterTab] and [leaveTab] follow the tab's visibility so the player is
 * open only while the flute is on screen.
 *
 * While the tune plays, [FluteUiState.beatPulse] counts the metronome's clicks (the first with the
 * first note), which the speed knob's animal hops on. The knob opens the metronome's own ring in
 * place: [openSpeed] stops the playback and ends a held key, [closeSpeed] (OK) reads the
 * time signature again, which may have changed there.
 *
 * Logs under [LogTags.FLUTE]: `flute.note` per note ended (midi, heldMs, length, added: false
 * at the cap), `flute.rest` per rest ended (heldMs, length, added), `flute.rejoin` (midi, none
 * for the rest, gapMs) when a key let go of goes on, `flute.breath` (added: false
 * at the cap or right after a breath), `flute.play` (notes, rests, bpm, time), `flute.play_done`,
 * `flute.stop` (reason: pill, key, erase, leave or speed), `flute.erase` (left), `flute.speed_open`
 * and `flute.speed_done` (bpm, time).
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
     * The key the finger let go of last, while a press of the same key may still go on with its
     * hold (a note within [HOLD_REJOIN_MS], the rest at any time): the key (a note's midi, null
     * for the rest), its step in the tune (null at the cap), when and in which pulse it went down,
     * when it was let go and the length that made. Cleared as soon as anything else happens: a new
     * hold, a hold ended by something other than the finger ([endHeld]: another key, a breath,
     * Play, the speed panel, leaving the tab) or an erase.
     */
    private data class LetGo(
        val midi: Int?,
        val index: Int?,
        val pressedAt: Long,
        val pulse: Pulse,
        val at: Long,
        val length: NoteLength,
    )
    private var letGo: LetGo? = null

    /**
     * The tab came on screen: open the output now so the first press is not late, and write the
     * staff in the metronome tab's time signature, which may have changed there meanwhile.
     */
    fun enterTab() {
        syncTimeSignature()
        tonePlayer.open()
    }

    /** The tab left the screen: silence everything, keep the tune, give the output back. */
    fun leaveTab() {
        stopPlayback(reason = "leave")
        endHeld()
        tonePlayer.close()
    }

    /**
     * A finger went down on the key of [midi]: sound it at once and add it to the tune, or, if
     * that key was let go of a moment ago and nothing happened since, go on with its note ([rejoin]).
     */
    fun onKeyPressed(midi: Int) {
        if (midi !in StaffNotes.ALL) return
        stopPlayback(reason = "key")
        val now = clock.nowMillis()
        val again = rejoinable(midi, now) // asked before a held key is ended: a press over a held key is a new note
        finishHeld(now) // a held note: the new note takes the voice over, no note off, so nothing clicks
        activationJob?.cancel()
        tonePlayer.noteOn(NoteMath.midiToFrequency(midi), TunerViewModel.REFERENCE_TONE_VOLUME)
        if (again != null) rejoin(again, now) else startHold(TuneNote(midi, NoteLength.EIGHTH), now)
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

    /**
     * A finger went down on the rest key: silence a held note and add a rest that grows while the
     * key stays down, or go on with the rest let go of a moment ago ([rejoin]).
     */
    fun onRestPressed() {
        stopPlayback(reason = "key")
        val now = clock.nowMillis()
        val again = rejoinable(midi = null, now)
        endHeld()
        activationJob?.cancel()
        if (again != null) rejoin(again, now) else startHold(TuneRest(NoteLength.EIGHTH), now)
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
            // One hop per click, from the start of the tune (absolute times, like Tune.growAtMs, so
            // the rounding never adds up); the first lands with the first note.
            val hops = launch(start = CoroutineStart.UNDISPATCHED) {
                var waited = 0L
                var click = 0L
                while (true) {
                    val at = (click * pulse.clickMs).roundToLong()
                    delay(at - waited)
                    waited = at
                    _state.update { it.copy(beatPulse = it.beatPulse + 1) }
                    click++
                }
            }
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
            hops.cancel() // a child of this job, which would not complete while the hops run
            playbackJob = null
            _state.update { it.copy(playingIndex = null) }
            logger.d(LogTags.FLUTE, "flute.play_done")
        }
    }

    /**
     * The speed knob was tapped and the metronome's ring is about to take the stage: end the
     * playback (the tempo is about to change) and any held key.
     */
    fun openSpeed() {
        stopPlayback(reason = "speed")
        endHeld()
        val pulse = pulse()
        logger.d(LogTags.FLUTE, "flute.speed_open", "bpm" to pulse.bpm, "time" to pulse.timeSignature.label)
    }

    /** OK on the metronome's ring: the tune is back, and its staff is written in the time signature chosen there. */
    fun closeSpeed() {
        syncTimeSignature()
        val pulse = pulse()
        logger.d(LogTags.FLUTE, "flute.speed_done", "bpm" to pulse.bpm, "time" to pulse.timeSignature.label)
    }

    /** Drops the last step of the tune; ignored while a key is held or the tune is empty. Stops playback first. */
    fun eraseLast() {
        stopPlayback(reason = "erase")
        val current = _state.value
        if (current.isHolding || current.isEmpty) return
        _state.update { it.copy(tune = it.tune.dropLast(1)) }
        letGo = null // the step let go of last may be the one erased
        save()
        logger.d(LogTags.FLUTE, "flute.erase", "left" to _state.value.tune.size)
    }

    /**
     * Ends a playback in progress, saying what ended it (`pill`, `key`, `erase`, `leave` or
     * `speed`); its beat hops end with it. Nothing playing: nothing happens.
     */
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
        letGo = null
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
        growFrom(elapsedMs = 0)
    }

    /**
     * The hold a press of the key [midi] (null: the rest key) at [now] goes on with: the one the
     * finger let go of, if it was this key, nothing happened since and no key is held; a note
     * only within [HOLD_REJOIN_MS] (later it is a repeat of the note), the rest however long ago
     * (more silence is the same silence); else null.
     */
    private fun rejoinable(midi: Int?, now: Long): LetGo? {
        val last = letGo ?: return null
        if (last.midi != midi || _state.value.isHolding) return null
        val inTime = midi == null || now - last.at <= HOLD_REJOIN_MS
        return if (inTime) last else null
    }

    /**
     * The key let go of ([again]) is down again at [now]: the same hold goes on. Let go of within
     * [HOLD_REJOIN_MS], it goes on from the press that began it, at the length the whole hold has
     * made so far, gap included: a touch screen can report one hold as several (its palm rejection
     * cancelling a flat finger and starting it over, or a bounce), and each piece used to become a
     * note of its own. Let go of longer ago (only the rest key gets here), it goes on from the
     * length its rest has, as if the key had been held just that long: the pause was not silence
     * the child asked for. Either way it grows from there.
     */
    private fun rejoin(again: LetGo, now: Long) {
        val gap = now - again.at
        val elapsed = if (gap <= HOLD_REJOIN_MS) now - again.pressedAt else Tune.durationMs(again.length, again.pulse)
        pressedAt = now - elapsed
        pressPulse = again.pulse
        letGo = null
        val length = Tune.lengthOf(elapsed, pressPulse)
        _state.update { s ->
            val held = s.copy(
                heldMidi = again.midi,
                restHeld = again.midi == null,
                shown = again.midi?.let(Shown::Note) ?: Shown.Rest,
                heldIndex = again.index,
                heldLength = length,
            )
            held.copy(tune = held.withHeldLength(length))
        }
        growFrom(elapsed)
        logger.d(LogTags.FLUTE, "flute.rejoin", "midi" to again.midi, "gapMs" to gap)
    }

    /** Grows the held key's note or rest at each of [Tune.growAtMs] still ahead, [elapsedMs] into its hold, while the key stays down. */
    private fun growFrom(elapsedMs: Long) {
        val pulse = pressPulse
        growJob = viewModelScope.launch {
            var waited = elapsedMs
            for (at in Tune.growAtMs(pulse)) {
                if (at <= waited) continue
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

    /**
     * Ends whatever key is held now, silencing a note, and lets no press go on with a key let go
     * of before: something other than the finger is acting on the tune. Nothing held: only the latter.
     */
    private fun endHeld() {
        if (_state.value.heldMidi != null) tonePlayer.noteOff()
        finishHeld(clock.nowMillis())
        letGo = null
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

    /** Records the held note or rest at the length [heldMs] makes and lets go of the key, which a press of it may go on with ([rejoinable]). */
    private fun record(heldMs: Long) {
        growJob?.cancel()
        growJob = null
        val length = Tune.lengthOf(heldMs, pressPulse)
        val held = _state.value
        val added = held.heldIndex != null // false at the cap, where the press was not added
        _state.update { s ->
            s.copy(tune = s.withHeldLength(length), heldMidi = null, restHeld = false, heldIndex = null, heldLength = null)
        }
        val now = clock.nowMillis()
        letGo = LetGo(held.heldMidi, held.heldIndex, pressedAt ?: (now - heldMs), pressPulse, at = now, length = length)
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

    /** The staff is written in the metronome tab's time signature, which may have changed there meanwhile. */
    private fun syncTimeSignature() {
        _state.update { it.copy(timeSignature = settings.loadMetronomeConfig().timeSignature) }
    }

    private fun save() = settings.saveTune(_state.value.tune)

    override fun onCleared() {
        playbackJob?.cancel()
        activationJob?.cancel()
        growJob?.cancel()
        tonePlayer.close()
    }

    companion object {
        /**
         * A note key let go of and pressed again within this long is one hold, not two notes. A
         * touch screen can report one hold as several: its palm rejection cancels a flat finger and
         * starts it over, or the contact bounces, a few frames apart. A child's quickest deliberate
         * repeat of a note takes well over 100 ms, and Android itself takes an up and a down under
         * 40 ms (`ViewConfiguration.getDoubleTapMinTime`) for jitter rather than a second tap. The
         * rest key is not bound by it (see [rejoinable]): within it a hold's gap counts as held,
         * past it the rest goes on from the length it has.
         */
        const val HOLD_REJOIN_MS = 60L
    }
}
