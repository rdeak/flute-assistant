package hr.bertlab.fluteassistant.ui.flute

import hr.bertlab.fluteassistant.domain.flute.NoteLength
import hr.bertlab.fluteassistant.domain.flute.Tune
import hr.bertlab.fluteassistant.domain.flute.TuneItem
import hr.bertlab.fluteassistant.domain.flute.TuneNote
import hr.bertlab.fluteassistant.domain.flute.TuneRest
import hr.bertlab.fluteassistant.domain.model.TimeSignature

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
    /** Goes up by one on every click while the tune plays back; never reset, only its changes key the speed knob's hop (like `MetronomeUiState.beatPulse`). */
    val beatPulse: Long = 0L,
) {
    val isPlaying: Boolean get() = playingIndex != null
    val isEmpty: Boolean get() = tune.isEmpty()
    val atCap: Boolean get() = tune.size >= Tune.MAX_NOTES

    /** Whether a note key or the rest key is under a finger. */
    val isHolding: Boolean get() = heldMidi != null || restHeld

    /** [shown] when it is a note; null for a rest, a breath or nothing yet. */
    val shownMidi: Int? get() = (shown as? Shown.Note)?.midi

    /** The note key drawn pressed: the playing note's, else the held key. */
    val litMidi: Int? get() = playingIndex?.let { (tune.getOrNull(it) as? TuneNote)?.midi } ?: heldMidi

    /** Whether the rest key is drawn pressed: held, or its rest playing. */
    val restLit: Boolean get() = restHeld || playingIndex?.let { tune.getOrNull(it) is TuneRest } == true

    /** The step of the staff ringed in yellow: the playing step, else the held key's. */
    val litIndex: Int? get() = playingIndex ?: heldIndex
}
