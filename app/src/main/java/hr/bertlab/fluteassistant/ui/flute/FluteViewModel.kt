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
import hr.bertlab.fluteassistant.domain.flute.FluteNotes
import hr.bertlab.fluteassistant.domain.flute.ListenLevel
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
 * at the cap or right after a breath), `flute.press_scrolled` (midi, ms, dy: a press taken back because the finger began
 * scrolling the keys, since 2026-11-16), `flute.join` (waiting: true when the ⌒ key set a join, false when its
 * second tap cancelled one or there was nothing to join from), `flute.join_cancel` (reason: rest, breath, erase,
 * play, listen, book, speed or leave: a waiting join dropped), `flute.play` (notes, rests, bpm, time), `flute.play_done`,
 * `flute.stop` (reason: pill, key, erase, leave, speed or book), `flute.erase` (left),
 * `flute.speed_open` and `flute.speed_done` (bpm, time), `flute.shelf_open` (book: the open
 * one's index), `flute.book_open` (from, to, notes: the steps in the book opened, bpm and time:
 * what the metronome must be set to, none for an empty book, which leaves it alone) and
 * `flute.shelf_close` (book, when the open book was picked again and nothing changed); while
 * listening, `flute.listen` (notes, listenLevel: practice or exam; not `level`, which the session recorder stamps on
 * every line), `flute.level` (listenLevel, restarted: the run on started
 * again under it, since nothing of it was judged yet), `flute.heard` (index, expected, heard, fresh, verdict:
 * right, retry, ignored or wrong: the pitch, at the note's onset; cents: where it locked, from
 * the expected note), `flute.held` (index, heldMs: the note became long enough and earned its
 * mark; mark: first_try, retry, high or low; cents: the median of its pitch off the written note),
 * `flute.short` (index, heldMs, needMs: it stopped before that; faded: it had faded away first;
 * mark: short when the run moved on, none when it waits), `flute.changed` (index, mark: long or, in Exam, the whole
 * note's high, low, first_try or retry; soundedMs, writtenMs, cents: the mark of a note already marked changed, since
 * 2026-11-16), `flute.repeat_forgiven` (index), `flute.listen_summary` every
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
            FluteUiState(tune = writer.tune, openBook = shelf.open, books = shelf.books, level = settings.loadListenLevel()),
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
    /** The marks of the last Listen to me as they were before the key held now was pressed: a press taken back restores them. */
    private var marksBeforePress: Map<Int, StepMark> = emptyMap()
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
        cancelJoin(reason = "leave")
        tonePlayer.close()
    }

    /** A finger went down on the key of [midi]: sound it at once and add it to the tune, or go on with its note ([TuneWriter]). */
    fun onKeyPressed(midi: Int) {
        if (midi !in FluteNotes.ALL) return
        marksBeforePress = _state.value.marks // a press taken back gives them back
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

    /**
     * The finger on the key of [midi] began scrolling the keys within [ms] of its press, [dy] dp from where it landed
     * (since 2026-11-16): the press is taken back, the sound stopped and nothing else changes, not the tune, the stored
     * book, the marks of the last Listen to me or a waiting join. A press that cannot be taken back (it ended another
     * key's hold) is let go as a note.
     */
    fun onKeyScrolled(midi: Int, ms: Long, dy: Float) {
        if (midi != writer.held?.midi) return
        if (!writer.takeBack()) return onKeyReleased(midi)
        tonePlayer.noteOff()
        growJob?.cancel()
        growJob = null
        written()
        _state.update { it.copy(marks = marksBeforePress) }
        storeBook()
        logger.d(LogTags.FLUTE, "flute.press_scrolled", "midi" to midi, "ms" to ms, "dy" to dy)
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
        cancelJoin(reason = "rest") // a slur does not run over a rest
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
        cancelJoin(reason = "breath")
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
        cancelJoin(reason = "play")
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
            var sounding: Int? = null
            for (step in Tune.playSteps(items, pulse)) {
                _state.update {
                    it.copy(playing = Playing(step.index), beatPulse = if (step.clickAtStart) it.beatPulse + 1 else it.beatPulse)
                }
                // A joined note goes on from the note before with no new attack; a tie (the same note) is one sound.
                when {
                    step.midi == null -> Unit
                    !step.joined -> tonePlayer.noteOn(NoteMath.midiToFrequency(step.midi), TONE_VOLUME)
                    step.midi != sounding -> tonePlayer.glide(NoteMath.midiToFrequency(step.midi), TONE_VOLUME)
                }
                sounding = step.midi
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
        cancelJoin(reason = "listen")
        beginRun()
        listenJob = viewModelScope.launch { listen() }
    }

    /**
     * The child chooses how closely "Your turn" judges (since 2026-11-16): saved, and the next run is judged by it. While
     * a run is on and no note of it was judged yet ([FluteUiState.choosesLevel]) that run starts again at once under the
     * new level: its session ends with no marks and a new one opens, the microphone closes and opens again (so the new
     * session has its own audio), and the room under the stage stays as it is meanwhile.
     */
    fun chooseLevel(level: ListenLevel) {
        val current = _state.value
        if (level == current.level) return
        settings.saveListenLevel(level)
        _state.update { it.copy(level = level) }
        // A run starting again already (its first choice closed it and the new one has not begun) takes this level too.
        val pending = current.isListening && run == null && listenJob != null
        val restart = current.isListening && current.choosesLevel && run != null
        logger.d(LogTags.FLUTE, "flute.level", "listenLevel" to level.name.lowercase(), "restarted" to (restart || pending))
        if (!restart) return
        val old = listenJob
        old?.cancel() // now, so a Stop before the new run begins leaves no microphone open
        closeRun() // frames still on their way are dropped: there is no run to judge them
        listenJob = viewModelScope.launch {
            old?.join() // the microphone is closed before it opens again
            beginRun(hop = false)
            listen()
        }
    }

    /**
     * A new run of the tune at the level in force; its session opens first, so the flute.listen line and the first hop are
     * in it. The animal lands on the first note, or on a rest that opens the tune, with a hop; not when the run starts again
     * at another level ([hop] false), where it stands already: never twice in one place.
     */
    private fun beginRun(hop: Boolean = true) {
        val level = _state.value.level
        val run = ListenRun(writer.tune, pulse(), floor = roomFloor, run = level.rules).also { this.run = it }
        listenSummary.reset()
        _state.update { it.copy(listening = run.listening, marks = emptyMap(), beatPulse = if (hop) it.beatPulse + 1 else it.beatPulse) }
        recorder.start(writer.tune, pulse(), roomFloor, level)
        logger.d(LogTags.FLUTE, "flute.listen", "notes" to writer.tune.count { it is TuneNote }, "listenLevel" to level.name.lowercase())
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
        if (granted) startListening() else cancelJoin(reason = "listen") // Listen to me was tapped, as when it starts
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
        cancelJoin(reason = "speed")
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
        cancelJoin(reason = "book")
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
        cancelJoin(reason = "book")
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
        if (cancelJoin(reason = "erase")) return // a waiting join goes first, and nothing is erased
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
        // over: the run had ended and only its screen, with the speed line, still stood (not a run starting again at another level).
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
        closeRun()
        if (!keepScreen) _state.update { it.copy(listening = null) }
    }

    /** The run is done with: its last partial second logged, its session ended with its marks, the room's floor kept. The screen is left alone. */
    private fun closeRun() {
        val shown = _state.value.listening
        if (listenSummary.frames > 0) logListenSummary(shown?.index, shown?.hearing ?: Hearing.CLEAR) // the last partial second
        run?.let {
            recorder.end(it.marks)
            roomFloor = it.floor ?: roomFloor
        }
        run = null
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
        _state.update {
            // A step earned, a mark changed (or a repeat forgiven): its mark shows. The animal hops when the run says, to
            // another place: a click inside the note it walks, the next note, or back to the head of a note played again.
            val marks = if (step.advanced || step.changed != null || step.forgiven.isNotEmpty()) run.marks.toMap() else it.marks
            it.copy(listening = listening, marks = marks, beatPulse = if (step.hop) it.beatPulse + 1 else it.beatPulse)
        }
        if (step.done) {
            val pace = step.pace
            logger.d(
                LogTags.FLUTE,
                "flute.listen_done",
                "stars" to run.marks.count { it.value == StepMark.FIRST_TRY },
                "retries" to run.marks.count { it.value == StepMark.RETRY },
                "high" to run.marks.count { it.value == StepMark.HIGH },
                "low" to run.marks.count { it.value == StepMark.LOW },
                "short" to run.marks.count { it.value == StepMark.SHORT },
                "long" to run.marks.count { it.value == StepMark.LONG },
                "tongued" to run.marks.count { it.value == StepMark.TONGUED },
                "bpm" to pace?.bpm, "pairs" to pace?.pairs, "knob" to pace?.knobBpm, "speed" to (pace?.speaks == true),
            )
        }
        recorder.frame(step.row)
        // A run that ended at another animal's speed keeps its screen, with that line, until it is stopped.
        if (run.isOver) endListening(keepScreen = run.listening.pace != null)
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
            logger.d(
                LogTags.FLUTE, "flute.note",
                "midi" to ended.midi, "heldMs" to ended.heldMs, "length" to ended.length, "added" to ended.added, "joined" to ended.joined,
            )
        } else {
            logger.d(LogTags.FLUTE, "flute.rest", "heldMs" to ended.heldMs, "length" to ended.length, "added" to ended.added)
        }
    }

    /** Shows what the writer holds: the tune, the key under a finger, and whether a join waits. */
    private fun written() {
        _state.update { it.copy(tune = writer.tune, held = writer.held, joinWaiting = writer.joinWaiting) }
    }

    /**
     * The ⌒ key (since 2026-11-16): the next note pressed is joined to the last one, or a second tap cancels the join.
     * It never ends a held key, unlike the breath key: a tap while a key is held joins the held note to the next, so that
     * note goes on sounding and growing until the next press ends it.
     */
    fun onJoin() {
        stopPlayback(reason = "key")
        stopListening(reason = "key")
        val waiting = writer.join()
        written()
        logger.d(LogTags.FLUTE, "flute.join", "waiting" to waiting)
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
        storeBook()
    }

    /** Stores the open book: the tune with the metronome's speed and beat. */
    private fun storeBook() {
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
