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
import hr.bertlab.fluteassistant.domain.flute.Pace
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
 *   goes on with the same note. The breath key adds a breath, erase drops the last step. A hold is
 *   counted at the writing pace ([TuneWriter.WRITING_PULSE], since 2026-11-17), never the metronome's;
 *   Play and Your turn count in the metronome's [Pulse], read from [MetronomeSettings] (the one owner
 *   of the tempo and the beat, which the speed panel sets through [setBpm] and [setTimeSignature]).
 * - **Play.** [Tune.playSteps] says when each step sounds and where the metronome's clicks fall;
 *   [FluteUiState.beatPulse] counts them for the animal's hop and
 *   [FluteUiState.playingHalfBeat] says how far into the playing step the last one fell.
 * - **Your turn.** [startListening] opens the tuner's microphone ([TunerEngine]) and feeds a
 *   [ListenRun], which judges the child's playing note by note, each note's pitch and, at the
 *   metronome's pulse as it was at the start, its length (the knob ends a run, so the pulse
 *   cannot change during one); the run says when the animal hops, and this counts the hops on
 *   [FluteUiState.beatPulse] with no timer of its own. The last note earned ends it by
 *   itself; Stop ([stopRun]), erase, the speed knob, the book button, a key and leaving the tab
 *   end it early and keep the marks so far; any change to the tune clears them. [onMicResult]
 *   receives the phone's answer about the microphone; a no shows the microphone card
 *   ([FluteUiState.micDenied]) until [dismissMic], with Settings on it once the phone will not ask
 *   again ([FluteUiState.micBlocked]).
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
 * at the cap, joined: joined to the note before), `flute.rest` per rest ended (heldMs, length, added), `flute.rejoin` (midi, none
 * for the rest, gapMs) when a key let go of goes on, `flute.breath` (added: false
 * at the cap or right after a breath), `flute.join` (waiting: true when the ⌒ key set a join, false when its
 * second tap cancelled one or there was nothing to join from), `flute.join_cancel` (reason: rest, breath, erase,
 * play, listen, book, speed or leave: a waiting join dropped), `flute.sign` (waiting: true when the ♯♭ key set a sign, false when its
 * second tap dropped it, since 2026-11-17), `flute.sign_cancel` (reason: erase, play, listen, book, speed or leave: a waiting sign
 * dropped), `flute.play` (notes, rests, bpm, time), `flute.play_done`,
 * `flute.stop` (reason: pill, key, erase, leave, speed or book), `flute.erase` (left),
 * `flute.speed_open` and `flute.speed_done` (bpm, time), `flute.shelf_open` (book: the open
 * one's index), `flute.book_open` (from, to, notes: the steps in the book opened, bpm and time:
 * what the metronome must be set to, none for an empty book, which leaves it alone) and
 * `flute.shelf_close` (book, when the open book was picked again and nothing changed); while
 * listening, `flute.listen` (notes), `flute.heard` (index, expected, heard, fresh, verdict:
 * right, retry, ignored or wrong: the pitch, at the note's onset; cents: where it locked, from
 * the expected note), `flute.held` (index, heldMs: the note became long enough and earned its
 * mark; mark: first_try, retry, high or low; cents: the median of its pitch off the written note),
 * `flute.short` (index, heldMs, needMs: it stopped before that; faded: it had faded away first;
 * mark: short when the run moved on, none when it waits), `flute.changed` (index, mark: long; soundedMs, writtenMs,
 * cents: the mark of a note already marked changed, since 2026-11-16), `flute.repeat_forgiven` (index), `flute.listen_summary` every
 * [ListenRules.SUMMARY_FRAMES] frames (the tuner's summary fields, gate, expected, hearing),
 * `flute.hearing` (hearing, levelMean) when it changes, `flute.listen_done` (stars, retries, high,
 * low, short, long, tongued: the marks of each kind; bpm, pairs, knob: the child's speed, the note pairs it rests
 * on and the knob's; speed: whether the line about it shows), `flute.listen_stop` (reason: pill,
 * key, erase, leave, speed or book; over: the run had ended and its screen stood), `flute.listen_error`
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
        _state = MutableStateFlow(
            FluteUiState(tune = writer.tune, openBook = shelf.open, books = shelf.books),
        )
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
        clearStage(reason = "leave")
        tonePlayer.close()
    }

    /**
     * A finger went down on the white key [key]: sound the note it writes at once and add it to the tune, or go on with its
     * note ([TuneWriter]). While the ♯♭ key waits it writes its letter's black note, and D, A and C6 do nothing but go
     * on with a hold the touch screen split ([TuneWriter.takes]). The keys are the white ones only (since 2026-11-17).
     */
    fun onKeyPressed(key: Int) {
        if (key !in StaffNotes.ALL || !writer.takes(key, clock.nowMillis())) return
        stopRunning(reason = "key")
        activationJob?.cancel()
        press(key)
        // Sounded after the press, so a key that goes on with its hold sounds the note it wrote, signed or not. Over a held
        // note the new one takes the voice, with no note off, so nothing clicks.
        writer.held?.midi?.let { tonePlayer.noteOn(NoteMath.midiToFrequency(it), TONE_VOLUME) }
    }

    /** The finger lifted off (or slid off) the key [key]; ignored unless that key is the one held. */
    fun onKeyReleased(key: Int) {
        if (!holdsNote(key)) return
        tonePlayer.noteOff()
        ended(writer.release(clock.nowMillis()))
    }

    /** A screen reader activated the key [key]: sound the note it writes for one beat of the writing pace and add it, a quarter note. */
    fun onKeyActivated(key: Int) {
        onKeyPressed(key)
        if (!holdsNote(key)) return
        activationJob = viewModelScope.launch {
            delay(TuneWriter.TAP_MS)
            if (holdsNote(key)) {
                tonePlayer.noteOff()
                ended(writer.release(clock.nowMillis(), heldMs = TuneWriter.TAP_MS))
            }
        }
    }

    /** A finger went down on the rest key: silence a held note and add a rest that grows while the key stays down, or go on with its rest. */
    fun onRestPressed() {
        stopRunning(reason = "key")
        activationJob?.cancel()
        cancelJoin(reason = "rest") // a slur does not run over a rest; a waiting sign stays for the next note key
        if (writer.held?.midi != null) tonePlayer.noteOff()
        press(key = null)
    }

    /** The finger lifted off (or slid off) the rest key; ignored unless the rest is the one held. */
    fun onRestReleased() {
        if (writer.held?.isRest != true) return
        ended(writer.release(clock.nowMillis()))
    }

    /** A screen reader activated the rest key: a rest of one beat of the writing pace, a quarter rest. */
    fun onRestActivated() {
        onRestPressed()
        activationJob = viewModelScope.launch {
            delay(TuneWriter.TAP_MS)
            if (writer.held?.isRest == true) ended(writer.release(clock.nowMillis(), heldMs = TuneWriter.TAP_MS))
        }
    }

    /** The breath key: ends a held key and marks a breath, unless the last step is one already. */
    fun onBreath() {
        stopRunning(reason = "key")
        endHeld()
        activationJob?.cancel()
        cancelJoin(reason = "breath") // the sign stays, as for the rest key
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
        dropWaiting(reason = "play")
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
                // A tie is the note before going on; a joined note glides on from it with no new attack.
                when {
                    step.midi == null || step.tied -> Unit
                    step.joined -> tonePlayer.glide(NoteMath.midiToFrequency(step.midi), TONE_VOLUME)
                    else -> tonePlayer.noteOn(NoteMath.midiToFrequency(step.midi), TONE_VOLUME)
                }
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
     * note (a rest before it is walked on its clicks as in Play, a breath passed over), marking each
     * note once it is long enough; the last note earned ends it, after any rest behind it is walked.
     * Nothing is played. Nothing happens while the tune plays back, while it is listening already, or
     * when the tune holds no note.
     */
    fun startListening() {
        val current = _state.value
        if (current.isPlaying || current.isListening || !current.canListen) return
        endHeld()
        activationJob?.cancel()
        dropWaiting(reason = "listen")
        beginRun()
        listenJob = viewModelScope.launch { listen() }
    }

    /**
     * A new run of the tune; its session opens first, so the flute.listen line and the first hop are in it. The animal
     * lands on the first note, or on a rest that opens the tune, with a hop.
     */
    private fun beginRun() {
        val run = ListenRun(writer.tune, pulse(), floor = roomFloor).also { this.run = it }
        listenSummary.reset()
        _state.update { it.copy(listening = run.listening, marks = emptyMap(), beatPulse = it.beatPulse + 1) }
        recorder.start(writer.tune, pulse(), roomFloor)
        logger.d(LogTags.FLUTE, "flute.listen", "notes" to writer.tune.count { it is TuneNote })
    }

    /** Hears the microphone until the job is cancelled; a failure ends the listening. */
    private suspend fun listen() {
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

    /** The one wide Stop while the tune runs: ends the playback or the listening, whichever is on. */
    fun stopRun() {
        if (_state.value.isPlaying) stopPlayback(reason = "pill") else stopListening(reason = "pill")
    }

    /** The phone's answer about the microphone, asked for by "Your turn": a yes goes on to listen, a no shows the microphone card. */
    fun onMicResult(granted: Boolean, canAskAgain: Boolean = true) {
        logger.i(LogTags.FLUTE, "flute.mic", "granted" to granted)
        _state.update { it.copy(micDenied = !granted, micBlocked = !granted && !canAskAgain) }
        if (granted) startListening() else dropWaiting(reason = "listen") // Listen to me was tapped, as when it starts
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
        clearStage(reason = "speed")
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
        clearStage(reason = "book")
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
        clearStage(reason = "book") // before the tune is read: a held note is part of the book left
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
        stopRunning(reason = "erase")
        // A waiting join or sign goes first, both of them, and nothing is erased.
        if (dropWaiting(reason = "erase")) return
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
        // over: the run had ended and only its screen, with the speed line, still stood.
        logger.d(LogTags.FLUTE, "flute.listen_stop", "reason" to reason, "over" to (run == null && listenJob == null)) // before the session closes, so it is in it
        endListening()
    }

    /**
     * Closes the microphone and clears everything of the listening but the marks; no log line of its own. With
     * [keepScreen] (a finished run with a speed line, since 2026-11-14) the screen stays as the run ended until it is
     * stopped, which then only clears it: the microphone, the recording and the room's floor are done with here.
     */
    private fun endListening(keepScreen: Boolean = false) {
        listenJob?.cancel()
        listenJob = null
        val shown = _state.value.listening
        if (listenSummary.frames > 0) logListenSummary(shown?.index, shown?.hearing ?: Hearing.CLEAR) // the last partial second
        run?.let {
            recorder.end(it.marks)
            roomFloor = it.floor ?: roomFloor
        }
        run = null
        if (!keepScreen) _state.update { it.copy(listening = null) }
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
        logJudged(step)
        _state.update {
            // The animal hops when the run says, to another place: a click inside the note it walks, the next note, or
            // back to the head of a note played again.
            val marks = if (step.marksChanged) run.marks.toMap() else it.marks
            it.copy(listening = listening, marks = marks, beatPulse = if (step.hop) it.beatPulse + 1 else it.beatPulse)
        }
        if (step.done) logListenDone(run.marks, step.pace)
        recorder.frame(step.row)
        // A run that ended at another animal's speed keeps its screen, with that line, until it is stopped.
        if (run.isOver) endListening(keepScreen = run.listening.ended)
    }

    /** What the run judged on one frame, a line each: `flute.heard`, `flute.repeat_forgiven`, `flute.short`, `flute.held`, `flute.changed`. */
    private fun logJudged(step: ListenRun.Step) {
        step.heard?.let {
            logger.d(
                LogTags.FLUTE, "flute.heard",
                "index" to it.index, "expected" to it.expected, "heard" to it.onset.midi, "fresh" to it.onset.fresh,
                "verdict" to it.verdict.name.lowercase(), "cents" to step.cents, "stopDrop" to it.stopDrop,
            )
        }
        for (forgiven in step.forgiven) logger.d(LogTags.FLUTE, "flute.repeat_forgiven", "index" to forgiven)
        step.short?.let {
            logger.d(
                LogTags.FLUTE, "flute.short",
                "index" to it.index, "heldMs" to it.heldMs, "needMs" to it.needMs, "faded" to it.faded, "mark" to it.mark?.name?.lowercase(),
            )
        }
        step.held?.let {
            logger.d(LogTags.FLUTE, "flute.held", "index" to it.index, "heldMs" to it.heldMs, "mark" to it.mark?.name?.lowercase(), "cents" to it.cents)
        }
        step.changed?.let {
            logger.d(
                LogTags.FLUTE, "flute.changed",
                "index" to it.index, "mark" to it.mark.name.lowercase(), "soundedMs" to it.soundedMs, "writtenMs" to it.writtenMs, "cents" to it.cents,
            )
        }
    }

    /** `flute.listen_done`: how many steps earned each of the [marks], and the child's speed ([pace]). */
    private fun logListenDone(marks: Map<Int, StepMark>, pace: Pace?) {
        fun count(mark: StepMark) = marks.count { it.value == mark }
        logger.d(
            LogTags.FLUTE,
            "flute.listen_done",
            "stars" to count(StepMark.FIRST_TRY),
            "retries" to count(StepMark.RETRY),
            "high" to count(StepMark.HIGH),
            "low" to count(StepMark.LOW),
            "short" to count(StepMark.SHORT),
            "long" to count(StepMark.LONG),
            "tongued" to count(StepMark.TONGUED),
            "bpm" to pace?.bpm, "pairs" to pace?.pairs, "knob" to pace?.knobBpm, "speed" to (pace?.speaks == true),
        )
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
     * The white key [key] (null: the rest key) went down: the writer adds its step or goes on with the
     * one let go of, and it grows from there while the key stays down. A new step is a write to
     * the tune and is saved; a hold that only goes on is not.
     */
    private fun press(key: Int?) {
        val press = writer.press(key, clock.nowMillis()) ?: return // a key not taken; onKeyPressed asks first
        growJob?.cancel()
        written()
        press.ended?.let(::logEnded)
        if (press.rejoinGapMs == null) {
            save()
        } else {
            // The note gone on with, as flute.note names it: E♭ for E's key when the sign wrote it.
            logger.d(LogTags.FLUTE, "flute.rejoin", "midi" to writer.held?.midi, "gapMs" to press.rejoinGapMs)
        }
        val pulse = TuneWriter.WRITING_PULSE
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
            logger.d(
                LogTags.FLUTE, "flute.note",
                "midi" to ended.midi, "heldMs" to ended.heldMs, "length" to ended.length, "added" to ended.added, "joined" to ended.joined,
            )
        } else {
            logger.d(LogTags.FLUTE, "flute.rest", "heldMs" to ended.heldMs, "length" to ended.length, "added" to ended.added)
        }
    }

    /** Shows what the writer holds: the tune, the key under a finger, and whether a join or a sign waits. */
    private fun written() {
        _state.update { it.copy(tune = writer.tune, held = writer.held, joinWaiting = writer.joinWaiting, signWaiting = writer.signWaiting) }
    }

    /**
     * The ⌒ key (since 2026-11-16): the next note pressed is joined to the last one, or a second tap cancels the join.
     * It never ends a held key, unlike the breath key: a tap while a key is held joins the held note to the next, so that
     * note goes on sounding and growing until the next press ends it.
     */
    fun onJoin() {
        stopRunning(reason = "key")
        val waiting = writer.join()
        written()
        logger.d(LogTags.FLUTE, "flute.join", "waiting" to waiting)
    }

    /**
     * The ♯♭ key (since 2026-11-17): the next note key pressed writes its letter's sharp or flat, or a second tap drops it.
     * It never ends a held key: a tap while a key is held waits for the next press.
     */
    fun onSign() {
        stopRunning(reason = "key")
        val waiting = writer.sign()
        written()
        logger.d(LogTags.FLUTE, "flute.sign", "waiting" to waiting)
    }

    /** Whether the note key [key] is the one held now. */
    private fun holdsNote(key: Int): Boolean = writer.held?.let { it.midi != null && it.key == key } == true

    /** Ends a playback or a listening in progress, whichever is on, saying what ended it. */
    private fun stopRunning(reason: String) {
        stopPlayback(reason)
        stopListening(reason)
    }

    /**
     * Something other than a key takes the tab ([reason]: `leave`, `speed` or `book`): the playback or the listening
     * ends, then a held key, and a waiting join or sign is dropped.
     */
    private fun clearStage(reason: String) {
        stopRunning(reason)
        endHeld()
        dropWaiting(reason)
    }

    /** Drops a waiting join and a waiting sign, each logged with what dropped it; says whether either waited. */
    private fun dropWaiting(reason: String): Boolean {
        val join = cancelJoin(reason)
        val sign = cancelSign(reason)
        return join || sign
    }

    /** Drops a waiting sign, logged with what dropped it; says whether one waited. */
    private fun cancelSign(reason: String): Boolean {
        if (!writer.cancelSign()) return false
        written()
        logger.d(LogTags.FLUTE, "flute.sign_cancel", "reason" to reason)
        return true
    }

    /** Drops a waiting join, logged with what dropped it; says whether one waited. */
    private fun cancelJoin(reason: String): Boolean {
        if (!writer.cancelJoin()) return false
        written()
        logger.d(LogTags.FLUTE, "flute.join_cancel", "reason" to reason)
        return true
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
