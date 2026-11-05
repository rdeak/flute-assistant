// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hr.bertlab.fluteassistant.audio.tuner.ReferenceTonePlayer
import hr.bertlab.fluteassistant.core.MonotonicClock
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
 * One round of the note reading game: asks a note from the chosen set, judges each tap on
 * the staff, sounds the note when it is found, and after [NotesUiState.ROUND_LENGTH] notes
 * waits for [playAgain]. The chosen notes are remembered through [SettingsStore].
 */
@HiltViewModel
class NotesViewModel internal constructor(
    private val settings: SettingsStore,
    private val tonePlayer: ReferenceTonePlayer,
    private val clock: MonotonicClock,
    private val random: Random,
) : ViewModel() {

    @Inject
    constructor(settings: SettingsStore, tonePlayer: ReferenceTonePlayer, clock: MonotonicClock) :
        this(settings, tonePlayer, clock, Random.Default)

    private val _state: MutableStateFlow<NotesUiState>
    val state: StateFlow<NotesUiState>

    private var feedbackJob: Job? = null

    /** When the round's first tap happened, so the summary can say how long the round took; null before it. */
    private var roundStartedAt: Long? = null

    /**
     * True while the note this game started is still sounding. The player is a singleton the
     * tuner shares, so once [stopSound] has handed it back the feedback job must not stop
     * whatever the tuner plays next.
     */
    private var tonePending = false

    init {
        val notes = StaffNotes.sanitize(settings.loadPracticeNotes())
        _state = MutableStateFlow(
            NotesUiState(practiceNotes = notes, currentMidi = draw(notes, avoid = null), soundOn = settings.loadNoteSound()),
        )
        state = _state.asStateFlow()
    }

    /** The child tapped the place of [midi] on the staff. Ignored while the last answer still shows. */
    fun onNoteTapped(midi: Int) {
        val current = _state.value
        if (current.solved || current.roundOver) return
        val now = clock.nowMillis()
        val startedAt = roundStartedAt ?: now.also { roundStartedAt = it }
        if (midi == current.currentMidi) {
            val firstTry = current.wrongTaps.isEmpty()
            val streak = if (firstTry) current.streak + 1 else 0
            val results = current.results + firstTry
            _state.update {
                it.copy(
                    solved = true,
                    taps = it.taps + 1,
                    results = results,
                    streak = streak,
                    bestStreak = max(it.bestStreak, streak),
                    roundMillis = if (results.size >= NotesUiState.ROUND_LENGTH) now - startedAt else it.roundMillis,
                )
            }
            if (current.soundOn) {
                tonePlayer.play(NoteMath.midiToFrequency(midi), FEEDBACK_MS, TunerViewModel.REFERENCE_TONE_VOLUME)
                tonePending = true
            }
            feedbackJob?.cancel()
            feedbackJob = viewModelScope.launch {
                delay(FEEDBACK_MS.toLong())
                if (tonePending) stopSound() // the data has played out; release the track
                advance()
            }
        } else {
            _state.update { it.copy(wrongTaps = it.wrongTaps + midi, lastWrongTap = midi, taps = it.taps + 1, streak = 0) }
        }
    }

    private fun advance() {
        _state.update {
            if (it.results.size >= NotesUiState.ROUND_LENGTH) {
                it.copy(solved = false, wrongTaps = emptySet(), lastWrongTap = null, roundOver = true)
            } else {
                it.copy(
                    solved = false,
                    wrongTaps = emptySet(),
                    lastWrongTap = null,
                    currentMidi = draw(it.practiceNotes, avoid = it.currentMidi),
                )
            }
        }
    }

    /** Starts a fresh round with the same notes, not opening with the note just answered. */
    fun playAgain() = resetRound()

    /**
     * Throws the round away: score, streak, taps and any feedback in progress go, the chosen
     * notes stay, and a new note is drawn. Called when the child leaves the tab, so coming
     * back always starts from zero, and by [playAgain].
     */
    fun resetRound() = startRound(_state.value.practiceNotes)

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
        startRound(notes)
    }

    /** A fresh round over [notes], not opening with the note on screen; any feedback in progress is cut. */
    private fun startRound(notes: Set<Int>) {
        feedbackJob?.cancel()
        if (tonePending) stopSound()
        roundStartedAt = null
        _state.update { NotesUiState(practiceNotes = notes, currentMidi = draw(notes, avoid = it.currentMidi), soundOn = it.soundOn) }
    }

    /** Switches the sound of a found note on or off; remembered. */
    fun toggleSound() {
        val enabled = !_state.value.soundOn
        settings.saveNoteSound(enabled)
        if (!enabled && tonePending) stopSound()
        _state.update { it.copy(soundOn = enabled) }
    }

    /** Silences the note early (the screen went away mid-feedback) and hands the player back. */
    fun stopSound() {
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
        if (tonePending) stopSound()
    }

    companion object {
        /** How long the right place stays green and the note sounds before the next note. */
        const val FEEDBACK_MS = 1200
    }
}
