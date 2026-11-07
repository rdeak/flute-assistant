// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.data.settings

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import hr.bertlab.fluteassistant.domain.flute.Book
import hr.bertlab.fluteassistant.domain.flute.Shelf
import hr.bertlab.fluteassistant.domain.flute.Songbook
import hr.bertlab.fluteassistant.domain.model.MetronomeConfig
import hr.bertlab.fluteassistant.domain.model.SoundProfile
import hr.bertlab.fluteassistant.domain.model.Subdivision
import hr.bertlab.fluteassistant.domain.model.ThemeChoice
import hr.bertlab.fluteassistant.domain.model.TimeSignature
import hr.bertlab.fluteassistant.domain.notes.StaffNotes
import hr.bertlab.fluteassistant.domain.repository.SettingsStore
import hr.bertlab.fluteassistant.domain.tuner.FluteRange
import hr.bertlab.fluteassistant.domain.tuner.TunerMode
import hr.bertlab.fluteassistant.domain.tuner.NoteMath
import javax.inject.Inject
import javax.inject.Singleton

/** [SettingsStore] on top of SharedPreferences; no extra dependency needed for a few values. */
@Singleton
class SharedPreferencesSettingsStore @Inject constructor(
    @ApplicationContext context: Context,
) : SettingsStore {

    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun loadMetronomeConfig(): MetronomeConfig = MetronomeConfig(
        timeSignature = enumOrDefault(prefs.getString(KEY_TIME_SIGNATURE, null), TimeSignature.FOUR_FOUR),
        subdivision = enumOrDefault(prefs.getString(KEY_SUBDIVISION, null), Subdivision.NONE),
        sound = enumOrDefault(prefs.getString(KEY_SOUND, null), SoundProfile.WOODBLOCK),
    ).withBpm(prefs.getInt(KEY_BPM, MetronomeConfig.DEFAULT_BPM))

    override fun saveMetronomeConfig(config: MetronomeConfig) = prefs.edit {
        putInt(KEY_BPM, config.bpm)
        putString(KEY_TIME_SIGNATURE, config.timeSignature.name)
        putString(KEY_SUBDIVISION, config.subdivision.name)
        putString(KEY_SOUND, config.sound.name)
    }

    override fun loadVolume(): Float = prefs.getFloat(KEY_VOLUME, DEFAULT_VOLUME).coerceIn(0f, 1f)

    override fun saveVolume(volume: Float) = prefs.edit { putFloat(KEY_VOLUME, volume) }

    override fun loadReferenceA4(): Double =
        prefs.getFloat(KEY_A4, NoteMath.DEFAULT_A4.toFloat()).toDouble()

    override fun saveReferenceA4(hz: Double) = prefs.edit { putFloat(KEY_A4, hz.toFloat()) }

    override fun loadTargetNote(): Int = prefs.getInt(KEY_TARGET_NOTE, FluteRange.DEFAULT_TARGET)

    override fun saveTargetNote(midi: Int) = prefs.edit { putInt(KEY_TARGET_NOTE, midi) }

    override fun loadTunerMode(): TunerMode =
        enumOrDefault(prefs.getString(KEY_TUNER_MODE, null), TunerMode.IDENTIFY)

    override fun saveTunerMode(mode: TunerMode) = prefs.edit { putString(KEY_TUNER_MODE, mode.name) }

    // Stored as decimal strings in a string set; copied out, since the set SharedPreferences
    // returns must not be touched, and anything that is not a number is dropped.
    override fun loadPracticeNotes(): Set<Int> =
        prefs.getStringSet(KEY_PRACTICE_NOTES, null)?.mapNotNull { it.toIntOrNull() }?.toSet()
            ?: StaffNotes.DEFAULT_PRACTICE

    override fun savePracticeNotes(notes: Set<Int>) =
        prefs.edit { putStringSet(KEY_PRACTICE_NOTES, notes.map(Int::toString).toSet()) }

    override fun loadNoteSound(): Boolean = prefs.getBoolean(KEY_NOTE_SOUND, true)

    override fun saveNoteSound(enabled: Boolean) = prefs.edit { putBoolean(KEY_NOTE_SOUND, enabled) }

    // The shelf is six strings and the open index. No open index yet means the shelf was never
    // stored: Songbook.restore moves the old single tune (key "tune") into book 0 at the
    // metronome's stored speed and beat, and that result is written at once, with the old key
    // removed, so the migration runs once and a later empty book is not mistaken for no shelf.
    override fun loadShelf(): Shelf {
        val open = if (prefs.contains(KEY_OPEN_BOOK)) prefs.getInt(KEY_OPEN_BOOK, 0) else null
        val shelf = Songbook.restore(
            open = open,
            stored = List(Songbook.SIZE) { prefs.getString(bookKey(it), null) },
            legacyTune = prefs.getString(KEY_TUNE, null),
            config = loadMetronomeConfig(),
        )
        if (open == null) {
            prefs.edit {
                putInt(KEY_OPEN_BOOK, shelf.open)
                putString(bookKey(0), Songbook.encode(shelf.books[0]))
                remove(KEY_TUNE)
            }
        }
        return shelf
    }

    override fun saveBook(index: Int, book: Book) =
        prefs.edit { putString(bookKey(index), Songbook.encode(book)) }

    override fun saveOpenBook(index: Int) = prefs.edit { putInt(KEY_OPEN_BOOK, index) }

    override fun loadTheme(): ThemeChoice = enumOrDefault(prefs.getString(KEY_THEME, null), ThemeChoice.SYSTEM)

    override fun saveTheme(choice: ThemeChoice) = prefs.edit { putString(KEY_THEME, choice.name) }

    private fun bookKey(index: Int) = "book_$index"

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    private companion object {
        const val FILE = "settings"
        const val KEY_BPM = "bpm"
        const val KEY_TIME_SIGNATURE = "time_signature"
        const val KEY_SUBDIVISION = "subdivision"
        const val KEY_SOUND = "sound"
        const val KEY_VOLUME = "volume"
        const val KEY_A4 = "reference_a4"
        const val KEY_TARGET_NOTE = "target_note"
        const val KEY_TUNER_MODE = "tuner_mode"
        const val KEY_PRACTICE_NOTES = "practice_notes"
        const val KEY_NOTE_SOUND = "note_sound"
        const val KEY_OPEN_BOOK = "open_book"
        const val KEY_THEME = "theme"

        /** The single tune of builds before the shelf; read once to migrate it into book 0, then removed. */
        const val KEY_TUNE = "tune"
        const val DEFAULT_VOLUME = 0.8f
    }
}
