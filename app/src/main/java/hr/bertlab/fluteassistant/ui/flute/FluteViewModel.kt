// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.flute

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hr.bertlab.fluteassistant.audio.TONE_VOLUME
import hr.bertlab.fluteassistant.audio.flute.FluteTonePlayer
import hr.bertlab.fluteassistant.audio.tuner.TunerEngine
import hr.bertlab.fluteassistant.core.MonotonicClock
import hr.bertlab.fluteassistant.core.log.LogTags
import hr.bertlab.fluteassistant.core.log.Logger
import hr.bertlab.fluteassistant.domain.flute.Book
import hr.bertlab.fluteassistant.domain.flute.ListenRecorder
import hr.bertlab.fluteassistant.domain.flute.ListenRun
import hr.bertlab.fluteassistant.domain.flute.NoteLength
import hr.bertlab.fluteassistant.domain.flute.Pulse
import hr.bertlab.fluteassistant.domain.flute.Songbook
import hr.bertlab.fluteassistant.domain.flute.StepMark
import hr.bertlab.fluteassistant.domain.flute.Tune
import hr.bertlab.fluteassistant.domain.flute.TuneNote
import hr.bertlab.fluteassistant.domain.flute.TuneRest
import hr.bertlab.fluteassistant.domain.flute.TuneWriter
import hr.bertlab.fluteassistant.domain.listen.FrameSummary
import hr.bertlab.fluteassistant.domain.listen.Hearing
import hr.bertlab.fluteassistant.domain.listen.ListenRules
import hr.bertlab.fluteassistant.domain.listen.PitchFrame
import hr.bertlab.fluteassistant.domain.metronome.TempoBand
import hr.bertlab.fluteassistant.domain.metronome.TimeSignature
import hr.bertlab.fluteassistant.domain.notes.StaffNotes
import hr.bertlab.fluteassistant.domain.settings.MetronomeSettings
import hr.bertlab.fluteassistant.domain.settings.SettingsStore
import hr.bertlab.fluteassistant.domain.tuner.NoteMath
import kotlinx.coroutines.CancellationException
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
 * The flute tab. The rules are plain classes in `domain/flute`, and this class gives them the
 * clock, the sound, the microphone, the storage and the log:
 *
 * - **Writing.** [TuneWriter] turns key presses and releases into the tune: a key sounds through
 *   [FluteTonePlayer] while it is held, its note (or the rest key's rest) grows at each of
 *   [Tune.growAtMs] and takes the length the hold made; a key let go of and pressed again at once
 *   goes on with the same note. The breath key adds a breath, erase drops the last step. Time is
 *   counted in the metronome's [Pulse], read from [MetronomeSettings] at each press and at Play
 *   (the one owner of the tempo and the beat, which the speed panel sets through [setBpm] and
 *   [setTimeSignature]).
 * - **Play.** [Tune.playSteps] says when each step sounds and where the metronome's clicks fall;
 *   [FluteUiState.beatPulse] counts them for the animal's hop and
 *   [FluteUiState.playingHalfBeat] says how far into the playing step the last one fell.
 * - **Your turn.** [startListening] opens the tuner's microphone ([TunerEngine]) and feeds a
 *   [ListenRun], which judges the child's playing note by note. The last note judged ends it by
 *   itself; Stop ([stopRun]), erase, the speed knob, the book button, a key and leaving the tab
 *   end it early and keep the marks so far; any change to the tune clears them. [onMicResult]
 *   receives the phone's answer about the microphone; a no shows the microphone card
 *   ([FluteUiState.micDenied]) until [dismissMic].
 * - **Books.** The tune is saved to the open book of [SettingsStore]'s shelf (with the
 *   metronome's speed and beat) on every change ([save]), and the open book's tune is restored,
 *   sanitised, on creation. [openBook] switches to another of the [Songbook.SIZE] books: the book
 *   left keeps the metronome's speed and beat as they are at that moment, and the metronome takes
 *   the opened book's.
 *
 * Activity-scoped, so the tune survives tab switches and rotation; [enterTab] and [leaveTab]
 * follow the tab's visibility so the player is open only while the flute is on screen.
 * [openSpeed] and [openShelf] end the playback, the listening and a held key before the
 * metronome's ring or the shelf takes the stage.
 *
 * Logs under [LogTags.FLUTE]: `flute.note` per note ended (midi, heldMs, length, added: false
 * at the cap), `flute.rest` per rest ended (heldMs, length, added), `flute.rejoin` (midi, none
 * for the rest, gapMs) when a key let go of goes on, `flute.breath` (added: false
 * at the cap or right after a breath), `flute.play` (notes, rests, bpm, time), `flute.play_done`,
 * `flute.stop` (reason: pill, key, erase, leave, speed or book), `flute.erase` (left),
 * `flute.speed_open` and `flute.speed_done` (bpm, time), `flute.shelf_open` (book: the open
 * one's index), `flute.book_open` (from, to, notes: the steps in the book opened, bpm and time:
 * what the metronome must be set to, none for an empty book, which leaves it alone) and
 * `flute.shelf_close` (book, when the open book was picked again and nothing changed); while
 * listening, `flute.listen` (notes), `flute.heard` (index, expected, heard, fresh, verdict:
 * right, retry, ignored or wrong), `flute.repeat_forgiven` (index), `flute.listen_summary` every
 * [ListenRules.SUMMARY_FRAMES] frames (the tuner's summary fields, gate, expected, hearing),
 * `flute.hearing` (hearing, levelMean) when it changes, `flute.listen_done` (stars, retries),
 * `flute.listen_stop` (reason: pill, key, erase, leave, speed or book), `flute.listen_error`
 * (message) and `flute.mic` (granted).
 */
@HiltViewModel
class FluteViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val metronome: MetronomeSettings,
    private val tonePlayer: FluteTonePlayer,
    private val engine: TunerEngine,
    private val clock: MonotonicClock,
    private val logger: Logger,
    private val recorder: ListenRecorder,
) : ViewModel() {

    private val writer: TuneWriter
    private val _state: MutableStateFlow<FluteUiState>

    init {
        val shelf = settings.loadShelf()
        writer = TuneWriter(Tune.sanitize(shelf.books[shelf.open].tune))
        _state = MutableStateFlow(FluteUiState(tune = writer.tune, openBook = shelf.open, books = shelf.books))
    }

    val state: StateFlow<FluteUiState> = _state.asStateFlow()

    /** Grows the held key's note or rest at each of [Tune.growAtMs] while the key stays down. */
    private var growJob: Job? = null
    private var playbackJob: Job? = null
    private var activationJob: Job? = null

    /** The microphone, while "Your turn" listens. */
    private var listenJob: Job? = null
    /** The "Your turn" in progress; null when not listening. */
    private var run: ListenRun? = null
    /** The room's own level as the last run left it, which the next one starts from. */
    private var roomFloor: Float? = null
    private val listenSummary = FrameSummary()

    /** The tab came on screen: open the output now so the first press is not late. */
    fun enterTab() {
        tonePlayer.open()
    }

    /** The tab left the screen: silence everything, close the microphone, keep the tune and its marks, give the output back. */
    fun leaveTab() {
        stopPlayback(reason = "leave")
        stopListening(reason = "leave")
        endHeld()
        tonePlayer.close()
    }

    /** A finger went down on the key of [midi]: sound it at once and add it to the tune, or go on with its note ([TuneWriter]). */
    fun onKeyPressed(midi: Int) {
        if (midi !in StaffNotes.ALL) return
        stopPlayback(reason = "key")
        stopListening(reason = "key")
        activationJob?.cancel()
        // Over a held note the new one takes the voice, with no note off, so nothing clicks.
        tonePlayer.noteOn(NoteMath.midiToFrequency(midi), TONE_VOLUME)
        press(midi)
    }

    /** The finger lifted off (or slid off) the key of [midi]; ignored unless that key is the one sounding. */
    fun onKeyReleased(midi: Int) {
        if (midi != writer.held?.midi) return
        tonePlayer.noteOff()
        ended(writer.release(clock.nowMillis()))
    }

    /** A screen reader activated the key of [midi]: sound it for one beat and add it, a quarter note. */
    fun onKeyActivated(midi: Int) {
        onKeyPressed(midi)
        if (writer.held?.midi != midi) return
        val beat = Tune.durationMs(NoteLength.QUARTER, writer.pulse)
        activationJob = viewModelScope.launch {
            delay(beat)
            if (writer.held?.midi == midi) {
                tonePlayer.noteOff()
                ended(writer.release(clock.nowMillis(), heldMs = beat))
            }
        }
    }

    /** A finger went down on the rest key: silence a held note and add a rest that grows while the key stays down, or go on with its rest. */
    fun onRestPressed() {
        stopPlayback(reason = "key")
        stopListening(reason = "key")
        activationJob?.cancel()
        if (writer.held?.midi != null) tonePlayer.noteOff()
        press(midi = null)
    }

    /** The finger lifted off (or slid off) the rest key; ignored unless the rest is the one held. */
    fun onRestReleased() {
        if (writer.held?.isRest != true) return
        ended(writer.release(clock.nowMillis()))
    }

    /** A screen reader activated the rest key: a rest of one beat, a quarter rest. */
    fun onRestActivated() {
        onRestPressed()
        val beat = Tune.durationMs(NoteLength.QUARTER, writer.pulse)
        activationJob = viewModelScope.launch {
            delay(beat)
            if (writer.held?.isRest == true) ended(writer.release(clock.nowMillis(), heldMs = beat))
        }
    }

    /** The breath key: ends a held key and marks a breath, unless the last step is one already. */
    fun onBreath() {
        stopPlayback(reason = "key")
        stopListening(reason = "key")
        endHeld()
        activationJob?.cancel()
        val added = writer.breath()
        if (added) {
            written()
            save()
        }
        logger.d(LogTags.FLUTE, "flute.breath", "added" to added)
    }

    /** Plays the tune from the first step, or stops a playback in progress. Nothing to play, or the flute listening: nothing happens. */
    fun togglePlayback() {
        if (_state.value.isPlaying) {
            stopPlayback(reason = "pill")
            return
        }
        if (_state.value.isEmpty || _state.value.isListening) return
        endHeld()
        activationJob?.cancel()
        val items = writer.tune
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
            for (step in Tune.playSteps(items, pulse)) {
                _state.update {
                    it.copy(playing = Playing(step.index), beatPulse = if (step.clickAtStart) it.beatPulse + 1 else it.beatPulse)
                }
                if (step.midi != null) tonePlayer.noteOn(NoteMath.midiToFrequency(step.midi), TONE_VOLUME)
                var waited = 0L
                for (moment in step.inside) {
                    delay(moment.atMs - waited)
                    waited = moment.atMs
                    if (moment.halfBeat == null) {
                        tonePlayer.noteOff()
                    } else {
                        _state.update { it.copy(playing = Playing(step.index, moment.halfBeat), beatPulse = it.beatPulse + 1) }
                    }
                }
                delay(step.durationMs - waited)
            }
            playbackJob = null
            _state.update { it.copy(playing = null) }
            logger.d(LogTags.FLUTE, "flute.play_done")
        }
    }

    /**
     * "Your turn": opens the microphone and waits for the child to play the tune, from its first
     * note (rests and breaths before it are passed over), marking each note as it comes; the last
     * note judged ends it. Nothing is played. Nothing happens while the tune plays back, while it
     * is listening already, or when the tune holds no note.
     */
    fun startListening() {
        val current = _state.value
        if (current.isPlaying || current.isListening || !current.canListen) return
        endHeld()
        activationJob?.cancel()
        val run = ListenRun(writer.tune, floor = roomFloor).also { this.run = it }
        listenSummary.reset()
        // The animal lands on the first note with a hop.
        _state.update { it.copy(listening = run.listening, marks = emptyMap(), beatPulse = it.beatPulse + 1) }
        // The session opens first, so the flute.listen line and the first hop are in it.
        recorder.start(writer.tune, pulse(), roomFloor)
        logger.d(LogTags.FLUTE, "flute.listen", "notes" to writer.tune.count { it is TuneNote })
        listenJob = viewModelScope.launch {
            try {
                // Every frame, in order (the engine buffers, it does not conflate): the tracker
                // counts frames, a gap being two in a row, and the main thread is busiest right
                // after a judged note, when the staff scrolls and the animal hops, which is just
                // when the next note's gap frames arrive.
                engine.pitchFrames().collect(::onListenFrame)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.w(LogTags.FLUTE, "flute.listen_error", "message" to e.message, error = e)
                endListening()
            }
        }
    }

    /** The one wide Stop while the tune runs: ends the playback or the listening, whichever is on. */
    fun stopRun() {
        if (_state.value.isPlaying) stopPlayback(reason = "pill") else stopListening(reason = "pill")
    }

    /** The phone's answer about the microphone, asked for by "Your turn": a yes goes on to listen, a no shows the microphone card. */
    fun onMicResult(granted: Boolean) {
        logger.i(LogTags.FLUTE, "flute.mic", "granted" to granted)
        _state.update { it.copy(micDenied = !granted) }
        if (granted) startListening()
    }

    /** "Not now" on the microphone card: back to the tune. */
    fun dismissMic() {
        _state.update { it.copy(micDenied = false) }
    }

    /**
     * The speed knob was tapped and the metronome's ring is about to take the stage: end the
     * playback (the tempo is about to change), the listening and any held key.
     */
    fun openSpeed() {
        stopPlayback(reason = "speed")
        stopListening(reason = "speed")
        endHeld()
        val pulse = pulse()
        logger.d(LogTags.FLUTE, "flute.speed_open", "bpm" to pulse.bpm, "time" to pulse.timeSignature.label)
    }

    /** The speed panel's ring: the metronome's tempo, for both tabs ([MetronomeSettings]). */
    fun setBpm(bpm: Int) = metronome.update { it.withBpm(bpm) }

    /** The speed panel's beat buttons: the metronome's beat, for both tabs. */
    fun setTimeSignature(timeSignature: TimeSignature) = metronome.update { it.copy(timeSignature = timeSignature) }

    /** OK on the metronome's ring: the tune is back, its staff in the time signature chosen there (the screen reads the metronome's). */
    fun closeSpeed() {
        val pulse = pulse()
        logger.d(LogTags.FLUTE, "flute.speed_done", "bpm" to pulse.bpm, "time" to pulse.timeSignature.label)
    }

    /**
     * The book button was tapped and the shelf is about to take the stage: end the playback, the
     * listening and any held key, as [openSpeed] does (the tone player stays open).
     */
    fun openShelf() {
        stopPlayback(reason = "book")
        stopListening(reason = "book")
        endHeld()
        logger.d(LogTags.FLUTE, "flute.shelf_open", "book" to _state.value.openBook)
    }

    /**
     * A book of the shelf was picked: the book left keeps its tune with the metronome's speed and
     * beat as they are now, the staff shows the book at [index], and the metronome takes that
     * book's speed and beat (its bpm brought onto the ring's range). The metronome stays as it is
     * for an empty book: the child writes it at the speed and beat the metronome has, and the book
     * records them when it is left. Nothing changes when the open book is picked again or [index]
     * is off the shelf.
     */
    fun openBook(index: Int) {
        if (index !in 0 until Songbook.SIZE) return
        val open = _state.value.openBook
        if (index == open) {
            logger.d(LogTags.FLUTE, "flute.shelf_close", "book" to open)
            return
        }
        stopPlayback(reason = "book")
        stopListening(reason = "book")
        endHeld() // before the tune is read: a held note is part of the book left
        val config = metronome.config.value
        val leaving = Book(writer.tune, config.bpm, config.timeSignature)
        settings.saveBook(open, leaving)
        settings.saveOpenBook(index)
        val target = _state.value.books[index]
        writer.open(Tune.sanitize(target.tune))
        _state.update { s ->
            s.copy(
                tune = writer.tune,
                openBook = index,
                books = s.books.toMutableList().also { it[open] = leaving },
                marks = emptyMap(),
            )
        }
        val pulse = if (target.isEmpty) null else {
            Pulse(target.bpm.coerceIn(TempoBand.MIN_BPM, TempoBand.MAX_BPM), target.timeSignature)
        }
        if (pulse != null) metronome.update { it.withBpm(pulse.bpm).copy(timeSignature = pulse.timeSignature) }
        logger.d(
            LogTags.FLUTE,
            "flute.book_open",
            "from" to open,
            "to" to index,
            "notes" to target.tune.size,
            "bpm" to pulse?.bpm,
            "time" to pulse?.timeSignature?.label,
        )
    }

    /** Drops the last step of the tune; ignored while a key is held or the tune is empty. Stops playback and listening first. */
    fun eraseLast() {
        stopPlayback(reason = "erase")
        stopListening(reason = "erase")
        if (!writer.erase()) return
        written()
        save()
        logger.d(LogTags.FLUTE, "flute.erase", "left" to writer.tune.size)
    }

    /**
     * Ends a playback in progress, saying what ended it (`pill`, `key`, `erase`, `leave`, `speed`
     * or `book`); its beat hops end with it. Nothing playing: nothing happens.
     */
    private fun stopPlayback(reason: String) {
        if (!_state.value.isPlaying) return
        playbackJob?.cancel()
        playbackJob = null
        tonePlayer.noteOff()
        _state.update { it.copy(playing = null) }
        logger.d(LogTags.FLUTE, "flute.stop", "reason" to reason)
    }

    /**
     * Ends the listening early, saying what ended it (`pill`, `key`, `erase`, `leave`, `speed` or
     * `book`); the marks so far stay. Not listening: nothing happens.
     */
    private fun stopListening(reason: String) {
        if (!_state.value.isListening) return
        logger.d(LogTags.FLUTE, "flute.listen_stop", "reason" to reason) // before the session closes, so it is in it
        endListening()
    }

    /** Closes the microphone and clears everything of the listening but the marks; no log line of its own. */
    private fun endListening() {
        listenJob?.cancel()
        listenJob = null
        val shown = _state.value.listening
        if (listenSummary.frames > 0) logListenSummary(shown?.index, shown?.hearing ?: Hearing.CLEAR) // the last partial second
        recorder.end(run?.marks.orEmpty())
        roomFloor = run?.floor ?: roomFloor
        run = null
        _state.update { it.copy(listening = null) }
    }

    /** One microphone frame while listening: the run judges it, and this logs, shows and records what it made of it. */
    private fun onListenFrame(frame: PitchFrame) {
        val run = run ?: return
        val waited = run.listening.index
        val step = run.onFrame(frame)
        val listening = run.listening
        if (step.hearingMean != null) {
            logger.d(LogTags.FLUTE, "flute.hearing", "hearing" to listening.hearing, "levelMean" to step.hearingMean)
        }
        listenSummary.add(frame, step.row.decision)
        if (listenSummary.frames >= ListenRules.SUMMARY_FRAMES) logListenSummary(waited, listening.hearing)
        step.heard?.let {
            logger.d(
                LogTags.FLUTE, "flute.heard",
                "index" to it.index, "expected" to it.expected, "heard" to it.onset.midi, "fresh" to it.onset.fresh,
                "verdict" to it.verdict.name.lowercase(),
            )
        }
        for (forgiven in step.forgiven) logger.d(LogTags.FLUTE, "flute.repeat_forgiven", "index" to forgiven)
        _state.update {
            // A step earned: its mark shows and the animal hops on.
            if (step.advanced) {
                it.copy(listening = listening, marks = run.marks.toMap(), beatPulse = it.beatPulse + 1)
            } else {
                it.copy(listening = listening)
            }
        }
        if (step.done) {
            logger.d(
                LogTags.FLUTE,
                "flute.listen_done",
                "stars" to run.marks.count { it.value == StepMark.FIRST_TRY },
                "retries" to run.marks.count { it.value == StepMark.RETRY },
            )
        }
        recorder.frame(step.row)
        if (run.isOver) endListening()
    }

    /** `flute.listen_summary`, with the step waited on ([index]) and what the microphone makes of the sound. */
    private fun logListenSummary(index: Int?, hearing: Hearing) {
        logger.d(
            LogTags.FLUTE, "flute.listen_summary",
            *listenSummary.fields(), "gate" to ListenRules.MIN_PROBABILITY,
            "expected" to index?.let { (writer.tune.getOrNull(it) as? TuneNote)?.midi }, "hearing" to hearing,
        )
        listenSummary.reset()
    }

    /**
     * The key [midi] (null: the rest key) went down: the writer adds its step or goes on with the
     * one let go of, and it grows from there while the key stays down. A new step is a write to
     * the tune and is saved; a hold that only goes on is not.
     */
    private fun press(midi: Int?) {
        val press = writer.press(midi, clock.nowMillis(), pulse())
        growJob?.cancel()
        written()
        press.ended?.let(::logEnded)
        if (press.rejoinGapMs == null) {
            save()
        } else {
            logger.d(LogTags.FLUTE, "flute.rejoin", "midi" to midi, "gapMs" to press.rejoinGapMs)
        }
        val pulse = writer.pulse
        growJob = viewModelScope.launch {
            var waited = press.elapsedMs
            for (at in Tune.growAtMs(pulse)) {
                if (at <= waited) continue
                delay(at - waited)
                waited = at
                writer.grow(Tune.lengthOf(at, pulse))
                written()
            }
        }
    }

    /**
     * Ends whatever key is held now, silencing a note, and lets no press go on with a key let go
     * of before: something other than the finger is acting on the tune. Nothing held: only the latter.
     */
    private fun endHeld() {
        if (writer.held?.midi != null) tonePlayer.noteOff()
        ended(writer.release(clock.nowMillis()))
        writer.forget()
    }

    /** A hold ended ([ended], null when no key was held): its step stops growing, shows the length it made and is saved. */
    private fun ended(ended: TuneWriter.Ended?) {
        if (ended == null) return
        growJob?.cancel()
        growJob = null
        written()
        save()
        logEnded(ended)
    }

    private fun logEnded(ended: TuneWriter.Ended) {
        if (ended.midi != null) {
            logger.d(LogTags.FLUTE, "flute.note", "midi" to ended.midi, "heldMs" to ended.heldMs, "length" to ended.length, "added" to ended.added)
        } else {
            logger.d(LogTags.FLUTE, "flute.rest", "heldMs" to ended.heldMs, "length" to ended.length, "added" to ended.added)
        }
    }

    /** Shows what the writer holds: the tune, and the key under a finger. */
    private fun written() {
        _state.update { it.copy(tune = writer.tune, held = writer.held) }
    }

    /**
     * The metronome tab's pulse: its tempo, in its ring's range (a stored one from an older build
     * may lie outside), counted in its time signature's beats as the metronome clicks them.
     */
    private fun pulse(): Pulse {
        val config = metronome.config.value
        return Pulse(config.bpm.coerceIn(TempoBand.MIN_BPM, TempoBand.MAX_BPM), config.timeSignature)
    }

    /**
     * Every write to the tune comes through here: it is saved to the open book, with the
     * metronome's speed and beat it is written at, and the marks of the last "Your turn", keyed
     * by step, no longer fit it.
     */
    private fun save() {
        if (_state.value.marks.isNotEmpty()) _state.update { it.copy(marks = emptyMap()) }
        val config = metronome.config.value
        settings.saveBook(_state.value.openBook, Book(writer.tune, config.bpm, config.timeSignature))
    }

    override fun onCleared() {
        playbackJob?.cancel()
        activationJob?.cancel()
        growJob?.cancel()
        listenJob?.cancel()
        tonePlayer.close()
    }
}
