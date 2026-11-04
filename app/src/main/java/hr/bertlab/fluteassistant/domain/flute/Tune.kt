package hr.bertlab.fluteassistant.domain.flute

import hr.bertlab.fluteassistant.domain.model.TimeSignature
import hr.bertlab.fluteassistant.domain.notes.StaffNotes
import kotlin.math.ceil
import kotlin.math.roundToLong

/**
 * How long a note of the child's tune lasts, in beats of the metronome's tempo: the five
 * lengths a beginner reads (eighth, quarter, half, dotted half and whole note), kept as
 * [halfBeats] so every length is a whole number.
 */
enum class NoteLength(val halfBeats: Int) {
    EIGHTH(1),
    QUARTER(2),
    HALF(4),
    DOTTED_HALF(6),
    WHOLE(8),
    ;

    /** Whole quarter-note beats: none for an eighth. */
    val wholeBeats: Int get() = halfBeats / 2

    companion object {
        /**
         * The beats after the press at which a held key's note grows into the next length:
         * halfway between two lengths, so a hold always makes the nearest one.
         */
        val GROW_AT_BEATS: List<Double> = listOf(0.75, 1.5, 2.5, 3.5)

        /**
         * The length a key held for [beats] makes: the nearest of the five, so under ¾ of a beat
         * is an eighth, then a quarter up to 1½ beats, a half up to 2½, a dotted half up to 3½,
         * and anything longer a whole note.
         */
        fun ofBeats(beats: Double): NoteLength {
            val grown = GROW_AT_BEATS.count { beats >= it }
            return entries[grown]
        }

        /** The stored [halfBeats], or the nearest length to it. */
        fun ofHalfBeats(halfBeats: Int): NoteLength = ofBeats(halfBeats / 2.0)
    }
}

/**
 * The metronome's pulse as the flute counts it: [bpm] clicks a minute, each click one beat of
 * [timeSignature] (a quarter note in x/4, an eighth in x/8, a half in x/2), so a bar of the
 * flute's tune lasts exactly as long as the metronome's bar, [TimeSignature.beatsPerBar] clicks.
 */
data class Pulse(val bpm: Int, val timeSignature: TimeSignature = TimeSignature.FOUR_FOUR) {
    /** How long one click lasts. */
    val clickMs: Double get() = 60_000.0 / bpm

    /** How long a quarter note lasts, the unit [NoteLength] counts in: two clicks in 6/8, one in 4/4, half of one in 2/2. */
    val quarterMs: Double get() = clickMs * timeSignature.noteValue / 4

    /** How many half beats of the flute's beat (a quarter note) one click lasts: 2 in x/4, 1 in x/8, 4 in x/2. */
    val halfBeatsPerClick: Int get() = 8 / timeSignature.noteValue
}

/** One step of the child's tune: a [TuneNote], a [TuneRest] or a [TuneBreath]. */
sealed interface TuneItem

/** One note of the child's tune: which key, and how long it lasts ([NoteLength], in beats). */
data class TuneNote(val midi: Int, val length: NoteLength) : TuneItem

/** A silence the child made on purpose with the rest key, as long as the key was held, like a note. */
data class TuneRest(val length: NoteLength) : TuneItem

/** A sign for the player that this is the place to take a breath: no sound and no time, playback goes past it. */
data object TuneBreath : TuneItem

/**
 * The tune the flute tab records: a line of [TuneItem]s, one per key press. A note or a rest
 * is as long as its key was held, snapped to the metronome's [Pulse] ([lengthOf]); a breath has
 * no length. It is played back at the metronome's pulse, so the rhythm stays when the tempo
 * changes, with a short gap ([gapMs]) taken out of the end of each note so a repeated note is
 * heard twice. The time between presses is not recorded: a silence is only ever a rest the
 * child put there.
 *
 * Stored as one string, `"60:2,r:2,b,67:4"`: each note's key and its length in half beats, a
 * rest as `r` and its length, a breath as `b`. The decoder is lenient: entries it cannot read
 * are dropped, a third field per entry is accepted and ignored, and a note length above
 * [NoteLength.WHOLE]'s is a held time in milliseconds from before lengths were snapped (never
 * under 300), turned into a length at the tempo given. Older builds drop rests and breaths.
 */
object Tune {
    /** Far beyond what a child makes on purpose; rests and breaths count too. A press at the cap sounds but is not added. */
    const val MAX_NOTES = 200

    /** The silence after each note, never more than a quarter of it. */
    const val GAP_MS = 80

    private const val NOTE_SEPARATOR = ","
    private const val FIELD_SEPARATOR = ":"
    private const val REST = "r"
    private const val BREATH = "b"

    /** How long a note of [length] lasts at [pulse], gap included, in ms. */
    fun durationMs(length: NoteLength, pulse: Pulse): Long = (length.halfBeats * pulse.quarterMs / 2).roundToLong()

    /** The silence at the end of a note lasting [durationMs]. */
    fun gapMs(durationMs: Long): Long = minOf(GAP_MS.toLong(), durationMs / 4)

    /** The length a key held for [heldMs] at [pulse] makes. */
    fun lengthOf(heldMs: Long, pulse: Pulse): NoteLength = NoteLength.ofBeats(heldMs / pulse.quarterMs)

    /**
     * The ms after the press at which a held key's note grows to its next length at [pulse],
     * rounded up, so the length shown while the key is held never runs ahead of [lengthOf].
     */
    fun growAtMs(pulse: Pulse): List<Long> = NoteLength.GROW_AT_BEATS.map { ceil(it * pulse.quarterMs).toLong() }

    /**
     * Keeps only the notes the keys offer ([StaffNotes.ALL]), every rest, and a breath unless the
     * step before it is already one, then caps the count.
     */
    fun sanitize(items: List<TuneItem>): List<TuneItem> = items
        .filter { it !is TuneNote || it.midi in StaffNotes.ALL }
        .fold(mutableListOf<TuneItem>()) { kept, item ->
            if (item != TuneBreath || kept.lastOrNull() != TuneBreath) kept += item
            kept
        }
        .take(MAX_NOTES)

    fun encode(items: List<TuneItem>): String = items.joinToString(NOTE_SEPARATOR) {
        when (it) {
            is TuneNote -> "${it.midi}$FIELD_SEPARATOR${it.length.halfBeats}"
            is TuneRest -> "$REST$FIELD_SEPARATOR${it.length.halfBeats}"
            TuneBreath -> BREATH
        }
    }

    /** Reads a stored tune; old held times in ms become lengths at [legacyBpm]. */
    fun decode(text: String, legacyBpm: Int): List<TuneItem> = sanitize(
        text.split(NOTE_SEPARATOR).mapNotNull { entry ->
            val fields = entry.split(FIELD_SEPARATOR).map(String::trim)
            if (fields[0] == BREATH) return@mapNotNull TuneBreath
            if (fields.size < 2) return@mapNotNull null
            val value = fields[1].toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
            if (fields[0] == REST) return@mapNotNull TuneRest(NoteLength.ofHalfBeats(value))
            val midi = fields[0].toIntOrNull() ?: return@mapNotNull null
            val length = if (value <= NoteLength.WHOLE.halfBeats) {
                NoteLength.ofHalfBeats(value)
            } else {
                lengthOf(value.toLong(), Pulse(legacyBpm)) // held when a beat was always a quarter note
            }
            TuneNote(midi, length)
        },
    )
}
