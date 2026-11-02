package hr.bertlab.fluteassistant.ui.flute

import hr.bertlab.fluteassistant.domain.flute.NoteLength
import hr.bertlab.fluteassistant.domain.flute.Tune
import hr.bertlab.fluteassistant.domain.flute.TuneItem
import hr.bertlab.fluteassistant.domain.flute.TuneNote
import hr.bertlab.fluteassistant.domain.flute.TuneRest
import hr.bertlab.fluteassistant.domain.model.TimeSignature

/** What the bubble and the flute drawing show: a note with its fingering, or the sign of a rest or a breath. */
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
    /** What the bubble and the flute show: the held key, the last one pressed, or the step playing now; null before the first tap. */
    val shown: Shown? = null,
    /** The step of [tune] playing; null when not playing. */
    val playingIndex: Int? = null,
    /** How long the held key's note or rest has grown so far ([NoteLength.ofBeats]); null when no key is held. */
    val heldLength: NoteLength? = null,
    /** The metronome tab's time signature, which the tune line is written in (bar lines, ties). */
    val timeSignature: TimeSignature = TimeSignature.FOUR_FOUR,
) {
    val isPlaying: Boolean get() = playingIndex != null
    val isEmpty: Boolean get() = tune.isEmpty()
    val atCap: Boolean get() = tune.size >= Tune.MAX_NOTES

    /** Whether a note key or the rest key is under a finger. */
    val isHolding: Boolean get() = heldMidi != null || restHeld

    /** The note the bubble names and the flute fingers; null for a rest, a breath or nothing yet. */
    val shownMidi: Int? get() = (shown as? Shown.Note)?.midi

    /**
     * The hold dots filled under the bubble: one per metronome click of [heldLength] in
     * [timeSignature] (a quarter note fills one in 4/4, two in 6/8); null when no key is held.
     */
    val holdBeats: Int? get() = heldLength?.clicksIn(timeSignature)

    /** How many hold dots there are: a whole note's clicks, four in 4/4, eight in 6/8. */
    val maxHoldBeats: Int get() = NoteLength.WHOLE.clicksIn(timeSignature)

    /** The note key drawn pressed: the playing note's, else the held key. */
    val litMidi: Int? get() = playingIndex?.let { (tune.getOrNull(it) as? TuneNote)?.midi } ?: heldMidi

    /** Whether the rest key is drawn pressed: held, or its rest playing. */
    val restLit: Boolean get() = restHeld || playingIndex?.let { tune.getOrNull(it) is TuneRest } == true

    /** The pill of the tune line ringed in yellow: the playing step, else the held key's. */
    val litIndex: Int? get() = playingIndex ?: heldIndex
}
