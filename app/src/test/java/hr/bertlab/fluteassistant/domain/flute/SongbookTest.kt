// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.flute

import hr.bertlab.fluteassistant.domain.flute.NoteLength.HALF
import hr.bertlab.fluteassistant.domain.flute.NoteLength.QUARTER
import hr.bertlab.fluteassistant.domain.flute.NoteLength.WHOLE
import hr.bertlab.fluteassistant.domain.model.MetronomeConfig
import hr.bertlab.fluteassistant.domain.model.TimeSignature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SongbookTest {

    @Test
    fun `encode and decode round-trip a book`() {
        val book = Book(
            listOf(TuneNote(60, QUARTER), TuneRest(HALF), TuneBreath, TuneNote(72, WHOLE)),
            bpm = 96,
            timeSignature = TimeSignature.THREE_FOUR,
        )
        assertEquals(book, Songbook.decode(Songbook.encode(book), MetronomeConfig()))
    }

    @Test
    fun `decode falls back to the metronome for a missing or broken book`() {
        val config = MetronomeConfig(bpm = 80, timeSignature = TimeSignature.SIX_EIGHT)
        assertEquals(Book(emptyList(), 80, TimeSignature.SIX_EIGHT), Songbook.decode(null, config))
        assertEquals(Book(emptyList(), 80, TimeSignature.SIX_EIGHT), Songbook.decode("garbage", config))
        assertEquals(
            Book(listOf(TuneNote(60, QUARTER)), 80, TimeSignature.SIX_EIGHT),
            Songbook.decode("x;NINE_NINE;60:2", config),
        )
    }

    @Test
    fun `decode reads an old tune's ms at the book's own bpm`() {
        // 1000 ms at 60 bpm is one beat: a quarter; at the metronome's 120 it would be a half.
        assertEquals(
            listOf(TuneNote(60, QUARTER)),
            Songbook.decode("60;FOUR_FOUR;60:1000", MetronomeConfig(bpm = 120)).tune,
        )
    }

    @Test
    fun `cover notes are the first five notes, rests and breaths skipped`() {
        val tune = listOf(
            TuneRest(QUARTER), TuneNote(60, QUARTER), TuneBreath, TuneNote(62, QUARTER), TuneNote(64, QUARTER),
            TuneNote(65, QUARTER), TuneNote(67, QUARTER), TuneNote(69, QUARTER),
        )
        assertEquals(listOf(60, 62, 64, 65, 67), Book(tune).coverNotes)
        assertEquals(6, Book(tune).noteCount)
        assertTrue(Book().isEmpty)
        assertFalse(Book(listOf(TuneRest(QUARTER))).isEmpty)
    }

    @Test
    fun `restore moves the old tune into the first book at the metronome's speed and beat`() {
        val config = MetronomeConfig(bpm = 90, timeSignature = TimeSignature.THREE_FOUR)
        val shelf = Songbook.restore(open = null, stored = List(6) { null }, legacyTune = "60:2,67:4", config = config)
        assertEquals(0, shelf.open)
        assertEquals(Book(listOf(TuneNote(60, QUARTER), TuneNote(67, HALF)), 90, TimeSignature.THREE_FOUR), shelf.books[0])
        assertTrue(shelf.books.drop(1).all { it.isEmpty })
        assertEquals(6, shelf.books.size)
    }

    @Test
    fun `restore with nothing stored at all gives six empty books, the first open`() {
        val config = MetronomeConfig(bpm = 70, timeSignature = TimeSignature.TWO_FOUR)
        val shelf = Songbook.restore(open = null, stored = List(6) { null }, legacyTune = null, config = config)
        assertEquals(0, shelf.open)
        assertEquals(Songbook.SIZE, shelf.books.size)
        assertTrue(shelf.books.all { it == Book(emptyList(), 70, TimeSignature.TWO_FOUR) })
    }

    @Test
    fun `restore ignores the old tune once a shelf was stored, even with every book empty`() {
        val shelf = Songbook.restore(open = 3, stored = List(6) { null }, legacyTune = "60:2", config = MetronomeConfig())
        assertEquals(3, shelf.open)
        assertTrue(shelf.books.all { it.isEmpty })
    }

    @Test
    fun `restore keeps a stored open index on the shelf`() {
        val none = List(6) { null }
        assertEquals(5, Songbook.restore(open = 9, stored = none, legacyTune = null, config = MetronomeConfig()).open)
        assertEquals(0, Songbook.restore(open = -1, stored = none, legacyTune = null, config = MetronomeConfig()).open)
        assertEquals(5, Songbook.restore(open = 5, stored = none, legacyTune = null, config = MetronomeConfig()).open)
    }

    @Test
    fun `restore reads each stored book and pads a short shelf with empty ones`() {
        val config = MetronomeConfig(bpm = 100)
        val stored = listOf("96;THREE_FOUR;60:2", null, "70;TWO_FOUR;72:8")
        val shelf = Songbook.restore(open = 2, stored = stored, legacyTune = null, config = config)
        assertEquals(Songbook.SIZE, shelf.books.size)
        assertEquals(Book(listOf(TuneNote(60, QUARTER)), 96, TimeSignature.THREE_FOUR), shelf.books[0])
        assertEquals(Book(emptyList(), 100, TimeSignature.FOUR_FOUR), shelf.books[1])
        assertEquals(Book(listOf(TuneNote(72, WHOLE)), 70, TimeSignature.TWO_FOUR), shelf.books[2])
        assertTrue(shelf.books.drop(3).all { it == Book(emptyList(), 100, TimeSignature.FOUR_FOUR) })
    }
}
