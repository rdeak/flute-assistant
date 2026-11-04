package hr.bertlab.fluteassistant.ui.flute

import hr.bertlab.fluteassistant.domain.flute.NoteLength
import hr.bertlab.fluteassistant.domain.flute.StepMark
import hr.bertlab.fluteassistant.domain.flute.Tune
import hr.bertlab.fluteassistant.domain.flute.TuneItem
import hr.bertlab.fluteassistant.domain.flute.TuneNote
import hr.bertlab.fluteassistant.domain.flute.TuneRest
import hr.bertlab.fluteassistant.domain.model.TimeSignature
import hr.bertlab.fluteassistant.ui.tuner.Hearing
import hr.bertlab.fluteassistant.ui.tuner.TunerAdvice

/** The step last shown: a note, a rest or a breath (held, last pressed, or playing). */
sealed interface Shown {
    data class Note(val midi: Int) : Shown
    data object Rest : Shown
    data object Breath : Shown
}

/** Immutable snapshot of everything the flute tab renders. */
data class FluteUiState(
    /** The child's tune, oldest first; saved on every change. */
    val tune: List<TuneItem> = emptyList(),
    /** The note key under a finger right now, sounding; null when none. */
    val heldMidi: Int? = null,
    /** Whether the rest key is under a finger right now, its rest growing. */
    val restHeld: Boolean = false,
    /** Where the held key's note or rest sits in [tune]; null at the cap, where a press is not added. */
    val heldIndex: Int? = null,
    /** The held key, the last one pressed, or the step playing now; null before the first tap. No view shows it since the staff (2026-11-02). */
    val shown: Shown? = null,
    /** The step of [tune] playing; null when not playing. */
    val playingIndex: Int? = null,
    /** How long the held key's note or rest has grown so far ([NoteLength.ofBeats]); null when no key is held. */
    val heldLength: NoteLength? = null,
    /** The metronome tab's time signature, which the staff is written in (bar lines, ties). */
    val timeSignature: TimeSignature = TimeSignature.FOUR_FOUR,
    /**
     * Goes up by one on every click while the tune plays back, and on every step the animal hops
     * to while the flute listens; never reset, only its changes key the animal's hop (like
     * `MetronomeUiState.beatPulse`).
     */
    val beatPulse: Long = 0L,
    /** Whether "Your turn" is on: the microphone is open and the child plays the tune. */
    val isListening: Boolean = false,
    /** The step of [tune] the flute waits for the child to play; null when not listening. */
    val listenIndex: Int? = null,
    /** What each step earned in the last "Your turn", by its index in [tune]; cleared by a new run and by any change to the tune. */
    val marks: Map<Int, StepMark> = emptyMap(),
    /** The step whose ❌ shows now, for a moment after a wrong note; null otherwise. */
    val wrongIndex: Int? = null,
    /** What the microphone makes of sound that yields no note while listening; [Hearing.CLEAR] otherwise. */
    val hearing: Hearing = Hearing.CLEAR,
    /** True after the phone said no to the microphone on this tab, while the microphone card is shown. */
    val micDenied: Boolean = false,
) {
    val isPlaying: Boolean get() = playingIndex != null
    val isEmpty: Boolean get() = tune.isEmpty()
    val atCap: Boolean get() = tune.size >= Tune.MAX_NOTES

    /** Whether the tune is running, by the phone (Play) or by the child (Your turn): the keys hide and the staff grows. */
    val isRunning: Boolean get() = isPlaying || isListening

    /** Whether there is a note to check: rests and breaths alone give "Your turn" nothing to do. */
    val canListen: Boolean get() = tune.any { it is TuneNote }

    /** Whether a note key or the rest key is under a finger. */
    val isHolding: Boolean get() = heldMidi != null || restHeld

    /** [shown] when it is a note; null for a rest, a breath or nothing yet. */
    val shownMidi: Int? get() = (shown as? Shown.Note)?.midi

    /** The note key drawn pressed: the playing note's, else the held key. */
    val litMidi: Int? get() = playingIndex?.let { (tune.getOrNull(it) as? TuneNote)?.midi } ?: heldMidi

    /** Whether the rest key is drawn pressed: held, or its rest playing. */
    val restLit: Boolean get() = restHeld || playingIndex?.let { tune.getOrNull(it) is TuneRest } == true

    /** The step of the staff ringed in yellow: the playing step, else the one waited for, else the held key's. */
    val litIndex: Int? get() = playingIndex ?: listenIndex ?: heldIndex

    /** The step the knob's animal stands under while the tune runs: the playing step, else the one waited for; null at rest. */
    val animalIndex: Int? get() = playingIndex ?: listenIndex

    /** The one line under the staff while listening: come closer or play one clear note when the sound stays unclear, else nothing. */
    val advice: TunerAdvice
        get() = when (hearing) {
            Hearing.CLEAR -> TunerAdvice.NONE
            Hearing.QUIET_UNCLEAR -> TunerAdvice.COME_CLOSER
            Hearing.LOUD_UNCLEAR -> TunerAdvice.CLEAR_NOTE
        }
}
