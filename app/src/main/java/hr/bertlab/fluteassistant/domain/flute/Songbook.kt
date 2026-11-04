package hr.bertlab.fluteassistant.domain.flute

import hr.bertlab.fluteassistant.domain.model.MetronomeConfig
import hr.bertlab.fluteassistant.domain.model.TimeSignature

/**
 * One saved tune of the flute tab, a "book" on the child's shelf: the steps of the tune with
 * the [bpm] and [timeSignature] it was written at, since a tune's lengths only mean something at
 * its own speed and beat. While a book is open its speed and beat are the metronome's, so the
 * speed knob and the metronome's stage change the open book; a book that is closed keeps its own.
 */
data class Book(
    val tune: List<TuneItem> = emptyList(),
    val bpm: Int = MetronomeConfig.DEFAULT_BPM,
    val timeSignature: TimeSignature = TimeSignature.FOUR_FOUR,
) {
    /** True when the book has no step at all; a book holding only a rest or a breath is not empty. */
    val isEmpty: Boolean get() = tune.isEmpty()

    /** How many of the steps are notes; rests and breaths are not counted. */
    val noteCount: Int get() = tune.count { it is TuneNote }

    /** The keys of the first [Songbook.COVER_NOTES] notes, which the shelf draws on the book's cover. */
    val coverNotes: List<Int>
        get() = tune.filterIsInstance<TuneNote>().take(Songbook.COVER_NOTES).map { it.midi }
}

/** The child's whole shelf: [Songbook.SIZE] [books] and the index of the [open] one, the book on the staff. */
data class Shelf(val open: Int, val books: List<Book>)

/**
 * The shelf of [SIZE] books the flute tab keeps. Each book is stored as one string,
 * `"<bpm>;<time signature name>;<tune>"`, the tune in [Tune]'s own format (which has no `;`).
 * Reading is lenient like [Tune.decode]: whatever cannot be read falls back to the metronome's
 * speed and beat, or to an empty book, so a damaged value never loses the other five.
 */
object Songbook {
    /** Books on the shelf: six fixed places, each with its own picture. */
    const val SIZE = 6

    /** Notes of a book drawn on its cover. */
    const val COVER_NOTES = 5

    private const val FIELD_SEPARATOR = ";"
    private const val FIELDS = 3

    fun encode(book: Book): String =
        listOf(book.bpm, book.timeSignature.name, Tune.encode(book.tune)).joinToString(FIELD_SEPARATOR)

    /**
     * Reads a stored book. A bpm that is missing or not a number, or a time signature that is
     * not known, becomes [config]'s; anything that is not three fields is an empty book at
     * [config]'s speed and beat. The tune is read at the book's own bpm, so a tune from before
     * lengths were snapped (held milliseconds) keeps the rhythm it was played at.
     */
    fun decode(text: String?, config: MetronomeConfig): Book {
        val fields = text?.split(FIELD_SEPARATOR, limit = FIELDS)
        if (fields == null || fields.size != FIELDS) return Book(emptyList(), config.bpm, config.timeSignature)
        val bpm = fields[0].toIntOrNull() ?: config.bpm
        val timeSignature = TimeSignature.entries.firstOrNull { it.name == fields[1] } ?: config.timeSignature
        return Book(Tune.decode(fields[2], legacyBpm = bpm), bpm, timeSignature)
    }

    /**
     * The shelf for the stored [stored] books and [open] index.
     *
     * A null [open] means no shelf was ever stored (a fresh install, or an update from before
     * the songbook): the single tune the old app kept, [legacyTune], if any, becomes the first book at [config]'s speed and beat
     * (the metronome's, which the old tune was played at) and the other books are empty. Once a
     * shelf was stored the old tune is ignored, even if every book on the shelf is empty (the
     * child may have erased it on purpose). [open] is kept on the shelf, and [stored] padded or
     * cut to [SIZE].
     */
    fun restore(open: Int?, stored: List<String?>, legacyTune: String?, config: MetronomeConfig): Shelf {
        if (open == null) {
            val first = Book(Tune.decode(legacyTune ?: "", config.bpm), config.bpm, config.timeSignature)
            return Shelf(0, List(SIZE) { if (it == 0) first else decode(null, config) })
        }
        return Shelf(open.coerceIn(0, SIZE - 1), List(SIZE) { decode(stored.getOrNull(it), config) })
    }
}
