// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hr.bertlab.fluteassistant.audio.tuner.ReferenceTonePlayer
import hr.bertlab.fluteassistant.core.MonotonicClock
import hr.bertlab.fluteassistant.core.log.LogTags
import hr.bertlab.fluteassistant.core.log.Logger
import hr.bertlab.fluteassistant.domain.notes.NoteProgress
import hr.bertlab.fluteassistant.domain.notes.NotesGame
import hr.bertlab.fluteassistant.domain.notes.Phrases
import hr.bertlab.fluteassistant.domain.notes.StaffNotes
import hr.bertlab.fluteassistant.domain.repository.SettingsStore
import hr.bertlab.fluteassistant.domain.tuner.NoteMath
import hr.bertlab.fluteassistant.ui.tuner.TunerViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.max
import kotlin.random.Random

/**
 * One round of the notes tab, in either game, of [NotesUiState.ROUND_LENGTH] answered notes.
 * Find asks a note from the chosen set and judges each tap on the staff; Hear plays a short
 * phrase and asks for it back, note by note. Both sound what is found, and after the tenth
 * note wait for [resetRound]. The chosen notes and the game are remembered through [SettingsStore],
 * and so is the sticker book ([NotesUiState.progress]), which both games feed.
 */
@HiltViewModel
class NotesViewModel internal constructor(
    private val settings: SettingsStore,
    private val tonePlayer: ReferenceTonePlayer,
    private val clock: MonotonicClock,
    private val logger: Logger,
    private val random: Random,
) : ViewModel() {

    @Inject
    constructor(settings: SettingsStore, tonePlayer: ReferenceTonePlayer, clock: MonotonicClock, logger: Logger) :
        this(settings, tonePlayer, clock, logger, Random.Default)

    private val _state: MutableStateFlow<NotesUiState>
    val state: StateFlow<NotesUiState>

    /**
     * The wait after a found note: Find's green second, after which the next note comes, and Hear's
     * placed note, whose tone is released when it has played out. A phrase starting cancels it, so the
     * release cannot cut the phrase's first tone.
     */
    private var feedbackJob: Job? = null

    /** Hear's playback of the phrase, heard or just answered; after an answer, when it ends the next phrase comes. */
    private var playbackJob: Job? = null

    /** The [NotesUiState.phraseId] last played, so the screen showing a phrase again does not play it again. */
    private var heardId = -1

    /** When the round's first tap happened, so the summary can say how long the round took; null before it. */
    private var roundStartedAt: Long? = null

    /**
     * True while the note this game started is still sounding. The player is a singleton the
     * tuner shares, so once [releasePlayer] has handed it back the feedback job must not stop
     * whatever the tuner plays next.
     */
    private var tonePending = false

    init {
        val notes = StaffNotes.sanitize(settings.loadPracticeNotes())
        _state = MutableStateFlow(
            openingState(
                notes = notes,
                game = settings.loadNotesGame(),
                soundOn = settings.loadNoteSound(),
                progress = settings.loadNoteProgress(),
                previous = null,
            ),
        )
        state = _state.asStateFlow()
    }

    /** The child tapped the place of [midi] on the staff. Ignored while the last answer still shows or a phrase plays. */
    fun onNoteTapped(midi: Int) {
        val current = _state.value
        if (current.solved || current.roundOver || current.phrasePlaying) return
        val now = clock.nowMillis()
        val startedAt = roundStartedAt ?: now.also { roundStartedAt = it }
        if (midi == current.currentMidi) {
            val firstTry = current.wrongTaps.isEmpty()
            val streak = if (firstTry) current.streak + 1 else 0
            val results = current.results + firstTry
            val progress = if (firstTry) moveBook(current.progress, midi, found = true) else current.progress
            val grew = progress.countOf(midi) > current.progress.countOf(midi)
            // Find ends with every note; Hear only with the phrase's last, whose playback is the feedback.
            val lastNote = !current.hearing || current.placed == current.phrase.size - 1
            _state.update {
                val scored = it.copy(
                    taps = it.taps + 1,
                    results = results,
                    streak = streak,
                    bestStreak = max(it.bestStreak, streak),
                    progress = progress,
                    grewThisRound = if (grew) it.grewThisRound + midi else it.grewThisRound,
                    askedThisRound = it.askedThisRound + midi,
                )
                if (lastNote) {
                    scored.copy(
                        solved = true,
                        roundMillis = if (results.size >= NotesUiState.ROUND_LENGTH) now - startedAt else it.roundMillis,
                    )
                } else {
                    // The next column is live at once: no lock, no green second.
                    scored.copy(
                        placed = it.placed + 1,
                        currentMidi = it.phrase[it.placed + 1],
                        wrongTaps = emptySet(),
                        lastWrongTap = null,
                    )
                }
            }
            when {
                !current.hearing -> {
                    if (current.soundOn) soundNote(midi, FEEDBACK_MS)
                    feedbackJob?.cancel()
                    feedbackJob = viewModelScope.launch {
                        delay(FEEDBACK_MS.toLong())
                        if (tonePending) releasePlayer() // the data has played out; release the track
                        advance()
                    }
                }
                lastNote -> playPhrase(lead = false)
                else -> if (current.soundOn) {
                    soundNote(midi, NOTE_MS)
                    // Release the track when the note has played out, as Find does; the next column is live meanwhile.
                    feedbackJob?.cancel()
                    feedbackJob = viewModelScope.launch {
                        delay(NOTE_MS.toLong())
                        if (tonePending) releasePlayer()
                    }
                }
            }
        } else {
            // Only the first miss for a note costs it a step; the rest of the crosses cost nothing more.
            val progress = if (current.wrongTaps.isEmpty()) {
                moveBook(current.progress, current.currentMidi, found = false)
            } else {
                current.progress
            }
            // A miss spoils the phrase for the staircase; Find has no phrase to spoil.
            _state.update {
                it.copy(
                    wrongTaps = it.wrongTaps + midi,
                    lastWrongTap = midi,
                    taps = it.taps + 1,
                    streak = 0,
                    phraseClean = it.phraseClean && !it.hearing,
                    progress = progress,
                )
            }
        }
    }

    /**
     * [book] after [midi] was found on the first try ([found]) or missed for the first time, saved
     * at once when it changed. Find and Hear both come through here, so they feed one book. A
     * known note no longer changes, so `notes.known` is logged the one time a note reaches
     * [NoteProgress.KNOWN_AT].
     */
    private fun moveBook(book: NoteProgress, midi: Int, found: Boolean): NoteProgress {
        val moved = if (found) book.found(midi) else book.missed(midi)
        if (moved == book) return book
        settings.saveNoteProgress(moved)
        if (moved.isKnown(midi) && !book.isKnown(midi)) logger.i(LogTags.NOTES, "notes.known", "midi" to midi)
        return moved
    }

    private fun soundNote(midi: Int, durationMs: Int) {
        tonePlayer.play(NoteMath.midiToFrequency(midi), durationMs, TunerViewModel.REFERENCE_TONE_VOLUME)
        tonePending = true
    }

    /**
     * Plays the phrase on screen, tone by tone, from the start; one already playing is cut. The
     * phrase sounds whether Sound is on or not: it is the question, and a silent reward is
     * nothing. A phrase just answered ([NotesUiState.solved]) moves on when it has played, the
     * playback being its feedback. With [lead] the playback first waits [LEAD_MS], a breath for a
     * phrase that has just come on screen; the phrase counts as playing from the start of it, but
     * no note is [NotesUiState.sounding] yet. Each note is `sounding` while its tone lasts.
     */
    private fun playPhrase(lead: Boolean) {
        // A placed note's release is still due; it would cut the first tone short.
        feedbackJob?.cancel()
        playbackJob?.cancel()
        val shown = _state.value
        val answered = shown.solved
        heardId = shown.phraseId
        // At once, not in the job: a tap between here and the job's first run must not get in.
        // A note of the playback just cut may still be lit; none is until the new one sounds.
        _state.update { it.copy(phrasePlaying = true, sounding = null) }
        playbackJob = viewModelScope.launch {
            if (lead) delay(LEAD_MS.toLong())
            for ((index, midi) in shown.phrase.withIndex()) {
                _state.update { it.copy(sounding = index) }
                tonePlayer.play(NoteMath.midiToFrequency(midi), NOTE_MS, TunerViewModel.REFERENCE_TONE_VOLUME)
                tonePending = true
                delay(NOTE_MS.toLong())
                _state.update { it.copy(sounding = null) }
                delay(GAP_MS.toLong())
            }
            // No suspension point from here on: a playback cut earlier never gets this far, so
            // it cannot move a fresh round on.
            releasePlayer()
            if (answered) advance() else _state.update { it.copy(phrasePlaying = false) }
        }
    }

    /**
     * The screen shows a phrase and wants it played, once per [NotesUiState.phraseId]: showing
     * the same one again (the screen came back) plays nothing.
     */
    fun onPhraseShown() {
        val current = _state.value
        if (!current.hearing || current.roundOver || current.phraseId == heardId) return
        playPhrase(lead = true)
    }

    /** The speaker bubble: the phrase on screen is played again, at once and from the start if it is playing. */
    fun hearAgain() {
        val current = _state.value
        if (!current.hearing || current.roundOver) return
        playPhrase(lead = false)
    }

    /** Ends the round after the tenth note; otherwise puts the next note, or the next phrase, up. */
    private fun advance() {
        val current = _state.value
        val next = when {
            current.results.size >= NotesUiState.ROUND_LENGTH ->
                current.copy(
                    solved = false,
                    wrongTaps = emptySet(),
                    lastWrongTap = null,
                    phrasePlaying = false,
                    sounding = null,
                    roundOver = true,
                )
            current.hearing -> {
                val length = Phrases.nextLength(current.phraseLength, current.phraseClean)
                val left = NotesUiState.ROUND_LENGTH - current.answered
                val phrase = Phrases.draw(
                    current.practiceNotes,
                    Phrases.lengthFor(length, left),
                    random,
                    avoidFirst = current.phrase.first(),
                )
                logPhrase(phrase, current.phraseId + 1)
                current.copy(
                    solved = false,
                    wrongTaps = emptySet(),
                    lastWrongTap = null,
                    phrasePlaying = false,
                    sounding = null,
                    phrase = phrase,
                    placed = 1,
                    currentMidi = phrase[1],
                    phraseId = current.phraseId + 1,
                    phraseLength = length,
                    phraseClean = true,
                )
            }
            else -> current.copy(
                solved = false,
                wrongTaps = emptySet(),
                lastWrongTap = null,
                currentMidi = draw(current.practiceNotes, avoid = current.currentMidi),
            )
        }
        _state.value = next
        if (next.roundOver) {
            logger.d(
                LogTags.NOTES, "notes.round",
                "game" to next.game.name,
                "notes" to next.practiceNotes.size,
                "score" to next.score,
                "bestStreak" to next.bestStreak,
                "millis" to next.roundMillis,
            )
        }
    }

    /**
     * Throws the round away: score, streak, taps and any feedback in progress go, the chosen
     * notes and the game stay, and a new note or phrase is drawn. Called when the child leaves
     * the tab, so coming back always starts from zero, and by Again on the summary; the new round
     * does not open with the note just answered.
     */
    fun resetRound() = startRound(_state.value.practiceNotes, _state.value.game)

    /**
     * Adds or removes [midi] from the notes asked, saved at once; the last note stays. Any
     * change starts a fresh round from the new set, so the score always belongs to one set
     * of notes.
     */
    fun togglePracticeNote(midi: Int) {
        if (midi !in StaffNotes.ALL) return
        val current = _state.value
        val notes = if (midi in current.practiceNotes) current.practiceNotes - midi else current.practiceNotes + midi
        if (notes.isEmpty()) return
        settings.savePracticeNotes(notes)
        startRound(notes, current.game)
    }

    /**
     * Switches to [game], remembered, and starts a fresh round of it over the same notes. The
     * game already open is left alone: a tap on its pill must not throw the round away.
     */
    fun setGame(game: NotesGame) {
        if (game == _state.value.game) return
        settings.saveNotesGame(game)
        startRound(_state.value.practiceNotes, game)
    }

    /** A fresh round of [game] over [notes], not opening with what is on screen; any feedback or playback in progress is cut. */
    private fun startRound(notes: Set<Int>, game: NotesGame) {
        feedbackJob?.cancel()
        playbackJob?.cancel()
        if (tonePending) releasePlayer()
        roundStartedAt = null
        val previous = _state.value
        _state.value = openingState(notes, game, previous.soundOn, previous.progress, previous)
    }

    /**
     * What a round opens with over [notes] in [game]. [previous] is the screen it replaces, null
     * at the start: the round does not open with its note, or its phrase's first note, again,
     * and the sound switch, the progress and the phrase counter go on. Hear opens with two notes,
     * the first given; nothing sounds here.
     */
    private fun openingState(
        notes: Set<Int>,
        game: NotesGame,
        soundOn: Boolean,
        progress: NoteProgress,
        previous: NotesUiState?,
    ): NotesUiState {
        val phraseId = previous?.phraseId ?: 0
        if (game == NotesGame.FIND) {
            return NotesUiState(
                practiceNotes = notes,
                currentMidi = draw(notes, avoid = previous?.currentMidi),
                soundOn = soundOn,
                phraseId = phraseId,
                progress = progress,
            )
        }
        val phrase = Phrases.draw(notes, Phrases.MIN_LENGTH, random, avoidFirst = previous?.phrase?.firstOrNull())
        logPhrase(phrase, phraseId + 1)
        return NotesUiState(
            practiceNotes = notes,
            currentMidi = phrase[1],
            soundOn = soundOn,
            game = game,
            phrase = phrase,
            placed = 1,
            phraseId = phraseId + 1,
            progress = progress,
        )
    }

    /**
     * A phrase drawn, with the [phraseId] it goes on screen with: every reset and every note switched in the
     * picker draws one, so the id tells a phrase nobody saw from the next one, which replaced it.
     */
    private fun logPhrase(phrase: List<Int>, phraseId: Int) {
        logger.d(
            LogTags.NOTES, "notes.phrase",
            "length" to phrase.size,
            "notes" to phrase.joinToString(" "),
            "phraseId" to phraseId,
        )
    }

    /** Switches the sound of a found note on or off; remembered. */
    fun toggleSound() {
        val enabled = !_state.value.soundOn
        settings.saveNoteSound(enabled)
        // The phrase sounds with the switch off, so a phrase playing is not cut by it.
        if (!enabled && tonePending && !_state.value.phrasePlaying) releasePlayer()
        _state.update { it.copy(soundOn = enabled) }
    }

    /**
     * The screen went away: silences what sounds, cuts a phrase playing and hands the player
     * back. A phrase just answered moves on at once, as Find's feedback does after this, so the
     * round is not left waiting for a playback that is no more. A phrase only being heard is
     * forgotten as heard, so it plays again when the screen comes back to it.
     */
    fun stopSound() {
        val cut = playbackJob?.isActive == true
        playbackJob?.cancel()
        releasePlayer()
        if (!cut) return
        if (_state.value.solved) {
            advance()
        } else {
            heardId = -1
            _state.update { it.copy(phrasePlaying = false, sounding = null) }
        }
    }

    /** Stops the tone and hands the shared player back; the tuner may sound its own next. */
    private fun releasePlayer() {
        tonePlayer.stop()
        tonePending = false
    }

    /** A random note from [notes], not [avoid] unless it is the only one. */
    private fun draw(notes: Set<Int>, avoid: Int?): Int {
        val candidates = (notes - setOfNotNull(avoid)).ifEmpty { notes }.sorted()
        return candidates[random.nextInt(candidates.size)]
    }

    override fun onCleared() {
        feedbackJob?.cancel()
        playbackJob?.cancel()
        if (tonePending) releasePlayer()
    }

    companion object {
        /** How long the right place stays green and the note sounds before the next note. */
        const val FEEDBACK_MS = 1200

        /**
         * How long each note of Hear's phrase sounds, in the playback and when the child places one. A
         * note a second is a teacher's dictation pace. From the first child test of Hear (2026-11-09:
         * "note is too short ... it should last longer"); it was 450.
         */
        const val NOTE_MS = 900

        /**
         * The silence between two notes of the playback. It doubled from 100 with [NOTE_MS] (the first child
         * test of Hear, 2026-11-09): at the slower pace two notes must still be heard as two, so the silence
         * between them grew in step.
         */
        const val GAP_MS = 200

        /**
         * The breath before a phrase that has just come on screen, so it does not run into the one
         * before it (the playback of the answer just given). Hear again and the playback of an answer
         * start at once. From the first child test of Hear (2026-11-09).
         */
        const val LEAD_MS = 600
    }
}
