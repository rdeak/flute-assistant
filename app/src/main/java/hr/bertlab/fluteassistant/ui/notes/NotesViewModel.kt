// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hr.bertlab.fluteassistant.audio.TONE_VOLUME
import hr.bertlab.fluteassistant.audio.tuner.ReferenceTonePlayer
import hr.bertlab.fluteassistant.core.MonotonicClock
import hr.bertlab.fluteassistant.core.log.LogTags
import hr.bertlab.fluteassistant.core.log.Logger
import hr.bertlab.fluteassistant.domain.notes.NoteProgress
import hr.bertlab.fluteassistant.domain.notes.NotesGame
import hr.bertlab.fluteassistant.domain.notes.NotesRound
import hr.bertlab.fluteassistant.domain.notes.StaffNotes
import hr.bertlab.fluteassistant.domain.settings.SettingsStore
import hr.bertlab.fluteassistant.domain.tuner.NoteMath
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

/**
 * The notes tab: it keeps the current [NotesRound], whose rules are the round's own
 * ([NotesRound.tapped], [NotesRound.next], [NotesRound.open]), and does what a round cannot:
 * it sounds what is found, plays Hear's phrase, waits out the feedback, and after the tenth
 * note waits for [resetRound]. The chosen notes and the game are remembered through
 * [SettingsStore], and so is the sticker book ([NotesRound.progress]), which both games feed.
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

    private val _state: MutableStateFlow<NotesRound>
    val state: StateFlow<NotesRound>

    /**
     * The wait after a found note: Find's green second, after which the next note comes, and Hear's
     * placed note, whose tone is released when it has played out. A phrase starting cancels it, so the
     * release cannot cut the phrase's first tone.
     */
    private var feedbackJob: Job? = null

    /** Hear's playback of the phrase, heard or just answered; after an answer, when it ends the next phrase comes. */
    private var playbackJob: Job? = null

    /** The [NotesRound.phraseId] last played, so the screen showing a phrase again does not play it again. */
    private var heardId = -1

    /**
     * True while the note this game started is still sounding. The player is a singleton the
     * tuner shares, so once [releasePlayer] has handed it back the feedback job must not stop
     * whatever the tuner plays next.
     */
    private var tonePending = false

    init {
        val notes = StaffNotes.sanitize(settings.loadPracticeNotes())
        _state = MutableStateFlow(
            open(notes, settings.loadNotesGame(), settings.loadNoteSound(), settings.loadNoteProgress(), previous = null),
        )
        state = _state.asStateFlow()
    }

    /**
     * The child tapped the place of [midi] on the staff ([NotesRound.tapped]; ignored while the last
     * answer still shows or a phrase plays). A note found sounds, and the round moves on when its
     * feedback is over: Find's green second, or the playback of Hear's finished phrase.
     */
    fun onNoteTapped(midi: Int) {
        val before = _state.value
        val after = before.tapped(midi, clock.nowMillis())
        if (after === before) return
        _state.value = after
        if (after.progress != before.progress) {
            // Find and Hear both come through here, so they feed one book. A known note no longer
            // changes, so `notes.known` is logged the one time a note reaches NoteProgress.KNOWN_AT.
            settings.saveNoteProgress(after.progress)
            val asked = before.currentMidi
            if (after.progress.isKnown(asked) && !before.progress.isKnown(asked)) logger.i(LogTags.NOTES, "notes.known", "midi" to asked)
        }
        if (midi != before.currentMidi) return // a miss: its cross is all there is
        when {
            !before.hearing -> {
                if (before.soundOn) soundNote(midi, FEEDBACK_MS)
                feedbackJob?.cancel()
                feedbackJob = viewModelScope.launch {
                    delay(FEEDBACK_MS.toLong())
                    if (tonePending) releasePlayer() // the data has played out; release the track
                    advance()
                }
            }
            after.solved -> playPhrase(lead = false) // the phrase's last note: its playback is the feedback
            else -> if (before.soundOn) {
                soundNote(midi, NOTE_MS)
                // Release the track when the note has played out, as Find does; the next column is live meanwhile.
                feedbackJob?.cancel()
                feedbackJob = viewModelScope.launch {
                    delay(NOTE_MS.toLong())
                    if (tonePending) releasePlayer()
                }
            }
        }
    }

    private fun soundNote(midi: Int, durationMs: Int) {
        tonePlayer.play(NoteMath.midiToFrequency(midi), durationMs, TONE_VOLUME)
        tonePending = true
    }

    /**
     * Plays the phrase on screen, tone by tone, from the start; one already playing is cut. The
     * phrase sounds whether Sound is on or not: it is the question, and a silent reward is
     * nothing. A phrase just answered ([NotesRound.solved]) moves on when it has played, the
     * playback being its feedback. With [lead] the playback first waits [LEAD_MS], a breath for a
     * phrase that has just come on screen; the phrase counts as playing from the start of it, but
     * no note is [NotesRound.sounding] yet. Each note is `sounding` while its tone lasts.
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
                tonePlayer.play(NoteMath.midiToFrequency(midi), NOTE_MS, TONE_VOLUME)
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
     * The screen shows a phrase and wants it played, once per [NotesRound.phraseId]: showing
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

    /** Ends the round after the tenth note; otherwise puts the next note, or the next phrase, up ([NotesRound.next]). */
    private fun advance() {
        val before = _state.value
        val next = before.next(random)
        if (next.phraseId != before.phraseId) logPhrase(next)
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
        val previous = _state.value
        _state.value = open(notes, game, previous.soundOn, previous.progress, previous)
    }

    /** A fresh round ([NotesRound.open]), nothing sounding yet; Hear's first phrase is logged. */
    private fun open(notes: Set<Int>, game: NotesGame, soundOn: Boolean, progress: NoteProgress, previous: NotesRound?): NotesRound =
        NotesRound.open(notes, game, soundOn, progress, random, previous).also { if (it.hearing) logPhrase(it) }

    /**
     * The phrase [round] drew, with the id it goes on screen with: every reset and every note switched in the
     * picker draws one, so the id tells a phrase nobody saw from the next one, which replaced it.
     */
    private fun logPhrase(round: NotesRound) {
        logger.d(
            LogTags.NOTES, "notes.phrase",
            "length" to round.phrase.size,
            "notes" to round.phrase.joinToString(" "),
            "phraseId" to round.phraseId,
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
