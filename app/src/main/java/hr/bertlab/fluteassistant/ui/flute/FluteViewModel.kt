package hr.bertlab.fluteassistant.ui.flute

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hr.bertlab.fluteassistant.audio.flute.FluteTonePlayer
import hr.bertlab.fluteassistant.audio.tuner.PitchFrame
import hr.bertlab.fluteassistant.audio.tuner.TunerEngine
import hr.bertlab.fluteassistant.core.MonotonicClock
import hr.bertlab.fluteassistant.core.log.LogTags
import hr.bertlab.fluteassistant.core.log.Logger
import hr.bertlab.fluteassistant.domain.flute.Book
import hr.bertlab.fluteassistant.domain.flute.NoteLength
import hr.bertlab.fluteassistant.domain.flute.Pulse
import hr.bertlab.fluteassistant.domain.flute.Songbook
import hr.bertlab.fluteassistant.domain.flute.StepMark
import hr.bertlab.fluteassistant.domain.flute.Tune
import hr.bertlab.fluteassistant.domain.flute.TuneBreath
import hr.bertlab.fluteassistant.domain.flute.TuneCheck
import hr.bertlab.fluteassistant.domain.flute.TuneItem
import hr.bertlab.fluteassistant.domain.flute.TuneNote
import hr.bertlab.fluteassistant.domain.flute.TuneRest
import hr.bertlab.fluteassistant.domain.flute.Verdict
import hr.bertlab.fluteassistant.domain.model.MetronomeConfig
import hr.bertlab.fluteassistant.domain.notes.StaffNotes
import hr.bertlab.fluteassistant.domain.repository.SettingsStore
import hr.bertlab.fluteassistant.domain.tuner.NoteMath
import hr.bertlab.fluteassistant.ui.metronome.TempoBand
import hr.bertlab.fluteassistant.ui.tuner.FrameSummary
import hr.bertlab.fluteassistant.ui.tuner.Hearing
import hr.bertlab.fluteassistant.ui.tuner.HearingTracker
import hr.bertlab.fluteassistant.ui.tuner.NoteOnsetTracker
import hr.bertlab.fluteassistant.ui.tuner.TunerViewModel
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
 * the staff is written in, is also read when the tab opens. The tune is saved to the open book of
 * [SettingsStore]'s shelf (with the metronome's speed and beat) on every change, and the open
 * book's tune is restored, sanitised, on creation. Activity-scoped, so the tune survives tab
 * switches and rotation; [enterTab] and [leaveTab] follow the tab's visibility so the player is
 * open only while the flute is on screen.
 *
 * While the tune plays, [FluteUiState.beatPulse] counts the metronome's clicks (the first with the
 * first note), which the speed knob's animal hops on as it walks the staff, and
 * [FluteUiState.playingHalfBeat] says how far into the playing step the last click fell, so a long
 * note is hopped along, one beat's place per click, never twice in one place; the clicks are timed
 * from each step's own start, so they never drift from the notes. The knob opens the metronome's own ring in
 * place: [openSpeed] stops the playback and ends a held key, [closeSpeed] (OK) reads the
 * time signature again, which may have changed there.
 *
 * The book button opens the shelf of [Songbook.SIZE] books in place: [openShelf] ends the
 * playback, the listening and a held key, and [openBook] switches to a book of it
 * ([FluteUiState.openBook], [FluteUiState.books]). The book left keeps the metronome's speed and
 * beat as they are at that moment, so the screen must call the metronome's setters only after
 * [openBook] returns; what it returns is the [Pulse] those setters must take, or null when the
 * metronome stays as it is (the open book picked again, an empty book, an index off the shelf).
 * The marks of the last "Your turn" go with a switch, as with any write to the tune.
 *
 * "Your turn" ([startListening]) opens the tuner's microphone ([TunerEngine]) and waits for the
 * child to play the tune on a real flute: [NoteOnsetTracker] turns frames into notes as the
 * tuner's "Which?" mode does and says when a new one begins, [TuneCheck] judges each against the
 * note waited on ([FluteUiState.listenIndex]; the same letter in any octave is right, the check
 * waits for the right note, pitch only) and keeps the marks ([FluteUiState.marks]: a star for a
 * first try, a faint one after a stumble); a wrong note shows the note heard under the step
 * ([FluteUiState.wrong]) until the next note is judged. The last note judged ends it by itself; Stop ([stopRun]), erase,
 * the speed knob, the book button, a key and leaving the tab end it early and keep the marks so
 * far; any change to the tune clears them. [HearingTracker] judges sound that never becomes a note
 * ([FluteUiState.hearing]) for the advice line. Nothing is timed: the child sets the
 * pace. [onMicResult] receives the phone's answer about the microphone; a no shows the
 * microphone card ([FluteUiState.micDenied]) until [dismissMic].
 *
 * Logs under [LogTags.FLUTE]: `flute.note` per note ended (midi, heldMs, length, added: false
 * at the cap), `flute.rest` per rest ended (heldMs, length, added), `flute.rejoin` (midi, none
 * for the rest, gapMs) when a key let go of goes on, `flute.breath` (added: false
 * at the cap or right after a breath), `flute.play` (notes, rests, bpm, time), `flute.play_done`,
 * `flute.stop` (reason: pill, key, erase, leave, speed or book), `flute.erase` (left),
 * `flute.speed_open` and `flute.speed_done` (bpm, time), `flute.shelf_open` (book: the open
 * one's index), `flute.book_open` (from, to, notes: the steps in the book opened, bpm and time:
 * what the metronome must be set to, none for an empty book, which leaves it alone) and
 * `flute.shelf_close` (book, when the open book was picked again and nothing changed); while listening, `flute.listen` (notes), `flute.heard`
 * (index, expected, heard, verdict: right, retry, ignored or wrong), `flute.listen_summary` every
 * [TunerViewModel.SUMMARY_FRAMES] frames (the tuner's summary fields, gate, expected, hearing),
 * `flute.hearing` (hearing, levelMean) when it changes, `flute.listen_done` (stars, retries),
 * `flute.listen_stop` (reason: pill, key, erase, leave, speed or book), `flute.listen_error` (message)
 * and `flute.mic` (granted).
 */
@HiltViewModel
class FluteViewModel @Inject constructor(
    private val settings: SettingsStore,
    private val tonePlayer: FluteTonePlayer,
    private val engine: TunerEngine,
    private val clock: MonotonicClock,
    private val logger: Logger,
) : ViewModel() {

    private val _state: MutableStateFlow<FluteUiState>

    init {
        val shelf = settings.loadShelf()
        _state = MutableStateFlow(
            FluteUiState(
                tune = Tune.sanitize(shelf.books[shelf.open].tune),
                timeSignature = settings.loadMetronomeConfig().timeSignature,
                openBook = shelf.open,
                books = shelf.books,
            ),
        )
    }

    val state: StateFlow<FluteUiState> = _state.asStateFlow()

    /** When the held key (a note or the rest) went down, so its release can record how long it was held. */
    private var pressedAt: Long? = null
    /** The pulse the held key's note or rest is counted in, read at the press. */
    private var pressPulse: Pulse = Pulse(MetronomeConfig.DEFAULT_BPM)
    /** Grows the held key's note or rest at each of [Tune.growAtMs] while the key stays down. */
    private var growJob: Job? = null
    private var playbackJob: Job? = null
    private var activationJob: Job? = null

    /** The microphone, while "Your turn" listens. */
    private var listenJob: Job? = null
    /** The check of the tune in progress; null when not listening. */
    private var check: TuneCheck? = null
    private val tracker = NoteOnsetTracker()
    private val hearingTracker = HearingTracker()
    private val listenSummary = FrameSummary()
    /** Frames since the last note was counted while its sound goes on; [FINISH_FRAMES] of them end the run. */
    private var finishFrames = 0

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

    /** The tab left the screen: silence everything, close the microphone, keep the tune and its marks, give the output back. */
    fun leaveTab() {
        stopPlayback(reason = "leave")
        stopListening(reason = "leave")
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
        stopListening(reason = "key")
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
        stopListening(reason = "key")
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
        stopListening(reason = "key")
        endHeld()
        activationJob?.cancel()
        val current = _state.value
        val added = !current.atCap && current.tune.lastOrNull() != TuneBreath
        _state.update { s -> s.copy(tune = if (added) s.tune + TuneBreath else s.tune, shown = Shown.Breath) }
        if (added) save()
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
            var startsAt = 0 // half beats from the start of the tune to this step
            for ((index, item) in items.withIndex()) {
                val length = when (item) {
                    is TuneNote -> item.length
                    is TuneRest -> item.length
                    TuneBreath -> continue // a sign for the player: no time
                }
                val duration = Tune.durationMs(length, pulse)
                // The metronome's clicks that fall in this step, as half beats into it, timed from the
                // step's own start, so a hop always lands with the note it belongs to (the first with
                // the first note). Each moves the animal to that beat's place over the step.
                val clicks = (0 until length.halfBeats).filter { (startsAt + it) % pulse.halfBeatsPerClick == 0 }
                _state.update {
                    it.copy(
                        playingIndex = index,
                        playingHalfBeat = 0,
                        shown = if (item is TuneNote) Shown.Note(item.midi) else Shown.Rest,
                        beatPulse = if (clicks.firstOrNull() == 0) it.beatPulse + 1 else it.beatPulse,
                    )
                }
                if (item is TuneNote) tonePlayer.noteOn(NoteMath.midiToFrequency(item.midi), TunerViewModel.REFERENCE_TONE_VOLUME)
                // What happens inside the step, by its time from the step's start: the later clicks
                // (a half beat number) and, for a note, its end (null) a short gap before the next step.
                val inside: List<Pair<Long, Int?>> = clicks.filter { it > 0 }.map { (it * pulse.quarterMs / 2).roundToLong() to it } +
                    listOfNotNull(if (item is TuneNote) duration - Tune.gapMs(duration) to null else null)
                var waited = 0L
                for ((at, halfBeat) in inside.sortedBy { it.first }) {
                    delay(at - waited)
                    waited = at
                    if (halfBeat == null) {
                        tonePlayer.noteOff()
                    } else {
                        _state.update { it.copy(playingHalfBeat = halfBeat, beatPulse = it.beatPulse + 1) }
                    }
                }
                delay(duration - waited)
                startsAt += length.halfBeats
            }
            playbackJob = null
            _state.update { it.copy(playingIndex = null) }
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
        val check = TuneCheck(_state.value.tune).also { this.check = it }
        tracker.reset()
        hearingTracker.reset()
        listenSummary.reset()
        finishFrames = 0
        _state.update {
            it.copy(
                isListening = true,
                listenIndex = check.index,
                marks = emptyMap(),
                wrong = null,
                hearing = Hearing.CLEAR,
                heardNow = null,
                repeatHeld = false,
                finishing = false,
                beatPulse = it.beatPulse + 1, // the animal lands on the first note with a hop
            )
        }
        logger.d(LogTags.FLUTE, "flute.listen", "notes" to _state.value.tune.count { it is TuneNote })
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

    /** OK on the metronome's ring: the tune is back, and its staff is written in the time signature chosen there. */
    fun closeSpeed() {
        syncTimeSignature()
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
     * beat as they are now, and the staff shows the book at [index]. Returns the speed and beat the
     * metronome must take for it (its bpm brought onto the ring's range), or null when it must stay
     * as it is: the open book picked again (nothing changes), an empty book (the child writes it at
     * the speed and beat the metronome has; the book records them when it is left) or an [index] off
     * the shelf. The metronome's config is read here, so the screen must call the metronome's
     * setters only after this returns.
     */
    fun openBook(index: Int): Pulse? {
        if (index !in 0 until Songbook.SIZE) return null
        val open = _state.value.openBook
        if (index == open) {
            logger.d(LogTags.FLUTE, "flute.shelf_close", "book" to open)
            return null
        }
        stopPlayback(reason = "book")
        stopListening(reason = "book")
        endHeld() // before the tune is read: a held note is part of the book left
        val config = settings.loadMetronomeConfig()
        val leaving = Book(_state.value.tune, config.bpm, config.timeSignature)
        settings.saveBook(open, leaving)
        settings.saveOpenBook(index)
        val target = _state.value.books[index]
        _state.update { s ->
            s.copy(
                tune = Tune.sanitize(target.tune),
                openBook = index,
                books = s.books.toMutableList().also { it[open] = leaving },
                timeSignature = if (target.isEmpty) s.timeSignature else target.timeSignature,
                marks = emptyMap(),
                wrong = null,
                shown = null,
            )
        }
        letGo = null
        val pulse = if (target.isEmpty) null else {
            Pulse(target.bpm.coerceIn(TempoBand.MIN_BPM, TempoBand.MAX_BPM), target.timeSignature)
        }
        logger.d(
            LogTags.FLUTE,
            "flute.book_open",
            "from" to open,
            "to" to index,
            "notes" to target.tune.size,
            "bpm" to pulse?.bpm,
            "time" to pulse?.timeSignature?.label,
        )
        return pulse
    }

    /** Drops the last step of the tune; ignored while a key is held or the tune is empty. Stops playback and listening first. */
    fun eraseLast() {
        stopPlayback(reason = "erase")
        stopListening(reason = "erase")
        val current = _state.value
        if (current.isHolding || current.isEmpty) return
        _state.update { it.copy(tune = it.tune.dropLast(1)) }
        letGo = null // the step let go of last may be the one erased
        save()
        logger.d(LogTags.FLUTE, "flute.erase", "left" to _state.value.tune.size)
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
        _state.update { it.copy(playingIndex = null) }
        logger.d(LogTags.FLUTE, "flute.stop", "reason" to reason)
    }

    /**
     * Ends the listening early, saying what ended it (`pill`, `key`, `erase`, `leave`, `speed` or
     * `book`); the marks so far stay. Not listening: nothing happens.
     */
    private fun stopListening(reason: String) {
        if (!_state.value.isListening) return
        endListening()
        logger.d(LogTags.FLUTE, "flute.listen_stop", "reason" to reason)
    }

    /** Closes the microphone and clears everything of the listening but the marks; no log line of its own. */
    private fun endListening() {
        listenJob?.cancel()
        listenJob = null
        if (listenSummary.frames > 0) logListenSummary() // the last partial second
        hearingTracker.reset()
        check = null
        _state.update {
            it.copy(
                isListening = false, listenIndex = null, wrong = null, hearing = Hearing.CLEAR,
                heardNow = null, repeatHeld = false, finishing = false,
            )
        }
    }

    /**
     * One microphone frame while listening: a note that began is judged; sound that stays unclear
     * is reported; a summary line now and then; then what the phone hears now is shown, or, once
     * the last note is counted, the run is ended when its sound ends.
     */
    private fun onListenFrame(frame: PitchFrame) {
        val result = tracker.onFrame(frame)
        val runMean = hearingTracker.levelMean // read before onFrame, which empties the window when the run ends
        val hearing = hearingTracker.onFrame(frame.level, result.decision)
        updateHearing(hearing, if (hearing == Hearing.CLEAR) runMean else hearingTracker.levelMean)
        listenSummary.add(frame, result.decision)
        if (listenSummary.frames >= TunerViewModel.SUMMARY_FRAMES) logListenSummary()
        val wasFinishing = _state.value.finishing
        result.onset?.let(::judge)
        when {
            !_state.value.isListening -> Unit
            wasFinishing -> if (tracker.sounding == null || ++finishFrames >= FINISH_FRAMES) endListening()
            !_state.value.finishing -> followSound()
        }
    }

    /**
     * What the step waited on hears right now ([FluteUiState.heardNow]): the sounding note,
     * unless it is the tail of the note just counted and something else is waited on; and
     * whether that tail is a written repeat held on instead of started again
     * ([FluteUiState.repeatHeld]), once it has gone on for [REPEAT_ADVICE_FRAMES].
     */
    private fun followSound() {
        val check = check ?: return
        val index = check.index ?: return
        val expected = (_state.value.tune[index] as TuneNote).midi
        val sounding = tracker.sounding
        val previous = check.previous
        val ofPrevious = sounding != null && previous != null && TuneCheck.sameLetter(sounding, previous)
        val ofExpected = sounding != null && TuneCheck.sameLetter(sounding, expected)
        val heardNow = sounding.takeUnless { ofPrevious && !ofExpected }
        val repeatHeld = ofPrevious && ofExpected && tracker.framesSinceOnset >= REPEAT_ADVICE_FRAMES
        val current = _state.value
        if (heardNow != current.heardNow || repeatHeld != current.repeatHeld) {
            _state.update { it.copy(heardNow = heardNow, repeatHeld = repeatHeld) }
        }
    }

    /** A new note began ([onset]): judge it against the note waited on and show what it earned. */
    private fun judge(onset: NoteOnsetTracker.Onset) {
        val check = check ?: return
        val index = check.index ?: return
        val expected = (_state.value.tune[index] as TuneNote).midi
        val heard = onset.midi
        val verdict = check.judge(heard, onset.fresh)
        logger.d(
            LogTags.FLUTE, "flute.heard",
            "index" to index, "expected" to expected, "heard" to heard, "fresh" to onset.fresh, "verdict" to verdict.name.lowercase(),
        )
        for (forgiven in check.forgiven) logger.d(LogTags.FLUTE, "flute.repeat_forgiven", "index" to forgiven)
        when (verdict) {
            Verdict.RIGHT, Verdict.RETRY -> {
                val marks = check.marks.toMap()
                val done = check.isDone
                _state.update {
                    it.copy(
                        marks = marks,
                        // Done: the animal stays over the last note while its sound ends.
                        listenIndex = if (done) index else check.index,
                        finishing = done,
                        wrong = null,
                        heardNow = null,
                        repeatHeld = false,
                        beatPulse = it.beatPulse + 1,
                    )
                }
                if (done) {
                    finishFrames = 0
                    logger.d(
                        LogTags.FLUTE,
                        "flute.listen_done",
                        "stars" to marks.count { it.value == StepMark.FIRST_TRY },
                        "retries" to marks.count { it.value == StepMark.RETRY },
                    )
                }
            }
            // The note heard stays under the step until the next note is judged: no timer, so there
            // is time to read it; an ignored note (the last step's tail or octave pop) leaves it.
            Verdict.WRONG -> _state.update { it.copy(wrong = WrongNote(index, heard)) }
            Verdict.IGNORED -> Unit
        }
    }

    /** Mirrors the tracker's verdict into the state; `flute.hearing` only when it changes, with the mean that decided it. */
    private fun updateHearing(hearing: Hearing, levelMean: Double) {
        if (hearing == _state.value.hearing) return
        _state.update { it.copy(hearing = hearing) }
        logger.d(LogTags.FLUTE, "flute.hearing", "hearing" to hearing, "levelMean" to levelMean)
    }

    private fun logListenSummary() {
        val s = _state.value
        logger.d(
            LogTags.FLUTE, "flute.listen_summary",
            *listenSummary.fields(), "gate" to TunerViewModel.MIN_PROBABILITY,
            "expected" to s.listenIndex?.let { (s.tune.getOrNull(it) as? TuneNote)?.midi }, "hearing" to s.hearing,
        )
        listenSummary.reset()
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

    /**
     * Every write to the tune comes through here: it is saved to the open book, with the
     * metronome's speed and beat it is written at, and the marks of the last "Your turn", keyed
     * by step, no longer fit it.
     */
    private fun save() {
        if (_state.value.marks.isNotEmpty()) _state.update { it.copy(marks = emptyMap()) }
        val config = settings.loadMetronomeConfig()
        settings.saveBook(_state.value.openBook, Book(_state.value.tune, config.bpm, config.timeSignature))
    }

    override fun onCleared() {
        playbackJob?.cancel()
        activationJob?.cancel()
        growJob?.cancel()
        listenJob?.cancel()
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

        /**
         * Counted frames a note must go on after being counted, with its written repeat waited
         * on, before the advice line asks the child to stop and play it again: about a second,
         * longer than any note the child means as one before a repeat at the ring's tempos.
         */
        const val REPEAT_ADVICE_FRAMES = 25

        /**
         * Frames after the last note is counted after which the run ends even though its sound
         * goes on: about 1.5 s, so a child holding the last note long still gets the keys back.
         */
        const val FINISH_FRAMES = 35
    }
}
