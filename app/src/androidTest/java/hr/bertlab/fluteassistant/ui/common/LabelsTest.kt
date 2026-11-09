// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import hr.bertlab.fluteassistant.domain.model.MetronomeConfig
import hr.bertlab.fluteassistant.domain.notes.NotesGame
import hr.bertlab.fluteassistant.ui.theme.NoteLetterColors
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class LabelsTest {

    private val base = InstrumentationRegistry.getInstrumentation().targetContext

    private fun resources(locale: Locale): Resources {
        val configuration = Configuration(base.resources.configuration).apply { setLocales(LocaleList(locale)) }
        return base.createConfigurationContext(configuration).resources
    }

    @Test
    fun the_summary_is_the_tempo_and_the_bar() {
        val config = MetronomeConfig(bpm = 120)
        assertEquals("120 BPM · 4/4", config.summary(resources(Locale.ENGLISH)))
        assertEquals("120 BPM · 4/4", config.summary(resources(Locale("hr"))))
    }

    @Test
    fun note_names_follow_the_locale() {
        // English: scientific pitch notation. Croatian musicians use the German names (H for
        // B, B for B flat) and the music-school octaves (c1 is middle C).
        assertEquals("B4", noteLabel(resources(Locale.ENGLISH), 71))
        assertEquals("A♯4", noteLabel(resources(Locale.ENGLISH), 70))
        assertEquals("C4", noteLabel(resources(Locale.ENGLISH), 60))
        assertEquals("C7", noteLabel(resources(Locale.ENGLISH), 96))
        assertEquals("h1", noteLabel(resources(Locale("hr")), 71))
        assertEquals("b1", noteLabel(resources(Locale("hr")), 70))
    }

    @Test
    fun croatian_octaves_follow_the_music_school_convention() {
        val hr = resources(Locale("hr"))
        assertEquals("c1", noteLabel(hr, 60))
        assertEquals("cis1", noteLabel(hr, 61))
        assertEquals("c3", noteLabel(hr, 84))
        assertEquals("c4", noteLabel(hr, 96))
        assertEquals("h", noteLabel(hr, 59))
        assertEquals("c", noteLabel(hr, 48))
        assertEquals("H", noteLabel(hr, 47))
        assertEquals("C", noteLabel(hr, 36))
        assertEquals("C1", noteLabel(hr, 24))
    }

    @Test
    fun the_octave_figure_splits_off_for_raising() {
        assertEquals("A♯" to "4", noteLabelParts(resources(Locale.ENGLISH), 70))
        assertEquals("c" to "1", noteLabelParts(resources(Locale("hr")), 60))
        assertEquals("h" to "", noteLabelParts(resources(Locale("hr")), 59))
        assertEquals("C" to "1", noteLabelParts(resources(Locale("hr")), 24))
    }

    @Test
    fun a_note_takes_its_letter_colour_and_a_sharp_the_letter_below() {
        assertEquals(NoteLetterColors[0], noteColor(60)) // C4
        assertEquals(NoteLetterColors[0], noteColor(61)) // C♯4
        assertEquals(NoteLetterColors[0], noteColor(84)) // C6, any octave
        assertEquals(NoteLetterColors[5], noteColor(70)) // A♯4, B flat in Croatian
        assertEquals(NoteLetterColors[6], noteColor(71)) // B4, H in Croatian
    }

    @Test
    fun note_names_without_octave_follow_the_locale() {
        assertEquals("B", noteName(resources(Locale.ENGLISH), 71))
        assertEquals("H", noteName(resources(Locale("hr")), 71))
        assertEquals("A♯", noteName(resources(Locale.ENGLISH), 70))
        assertEquals("B", noteName(resources(Locale("hr")), 70))
    }

    @Test
    fun notes_games_are_named() {
        // The emoji is part of the word, so the pill draws none of its own.
        assertEquals("🔎 Find", resources(Locale.ENGLISH).getString(NotesGame.FIND.labelRes()))
        assertEquals("👂 Hear", resources(Locale.ENGLISH).getString(NotesGame.HEAR.labelRes()))
        assertEquals("🔎 Nađi", resources(Locale("hr")).getString(NotesGame.FIND.labelRes()))
        assertEquals("👂 Slušaj", resources(Locale("hr")).getString(NotesGame.HEAR.labelRes()))
    }

    @Test
    fun other_system_languages_fall_back_to_english() {
        assertEquals("🔎 Find", resources(Locale.GERMAN).getString(NotesGame.FIND.labelRes()))
        assertEquals("👂 Hear", resources(Locale.FRENCH).getString(NotesGame.HEAR.labelRes()))
    }
}
