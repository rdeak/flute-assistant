// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.domain.settings

import hr.bertlab.fluteassistant.domain.flute.Book
import hr.bertlab.fluteassistant.domain.flute.Shelf
import hr.bertlab.fluteassistant.domain.metronome.MetronomeConfig
import hr.bertlab.fluteassistant.domain.notes.NoteProgress
import hr.bertlab.fluteassistant.domain.notes.NotesGame
import hr.bertlab.fluteassistant.domain.tuner.TunerMode

/**
 * Small key-value persistence for "where the user left off": the last metronome settings,
 * the note the tuner tunes to and its mode, the notes the reading game asks, which of its two games is open and how well each note is known, the flute tab's
 * shelf of tunes, the day or night choice. Synchronous by design (tiny values, read once at
 * ViewModel creation).
 */
interface SettingsStore {
    fun loadMetronomeConfig(): MetronomeConfig
    fun saveMetronomeConfig(config: MetronomeConfig)
    /** The note the tuner measures against, as a MIDI number (see FluteRange); not clamped here. */
    fun loadTargetNote(): Int
    fun saveTargetNote(midi: Int)
    fun loadTunerMode(): TunerMode
    fun saveTunerMode(mode: TunerMode)
    /** The notes the reading game asks, as MIDI numbers (see StaffNotes); not sanitised here. */
    fun loadPracticeNotes(): Set<Int>
    fun savePracticeNotes(notes: Set<Int>)
    /** Whether the reading game sounds the note when it is found; true by default. */
    fun loadNoteSound(): Boolean
    fun saveNoteSound(enabled: Boolean)
    /** Which game the notes tab shows; [NotesGame.FIND] until the other one is chosen. */
    fun loadNotesGame(): NotesGame
    fun saveNotesGame(game: NotesGame)
    /** The sticker book: how well each note is known, decoded leniently (see NoteProgress). */
    fun loadNoteProgress(): NoteProgress
    fun saveNoteProgress(progress: NoteProgress)
    /**
     * The flute tab's shelf: its books, as stored (decoded leniently, see Songbook), and the open
     * one; the caller sanitises a tune. The first load after the update that added the shelf moves
     * the old single `tune` into book 0 and stores the result, so that never runs again.
     */
    fun loadShelf(): Shelf
    /** Stores [book] as the book at [index], 0 until Songbook.SIZE. */
    fun saveBook(index: Int, book: Book)
    /** Stores which book is open, 0 until Songbook.SIZE. */
    fun saveOpenBook(index: Int)
    /** The child's day or night choice; [ThemeChoice.SYSTEM] until one is made. */
    fun loadTheme(): ThemeChoice
    fun saveTheme(choice: ThemeChoice)
}
