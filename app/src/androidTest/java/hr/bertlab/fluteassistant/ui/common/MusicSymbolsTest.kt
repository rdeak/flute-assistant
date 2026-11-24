// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import android.graphics.Paint
import android.graphics.Rect
import androidx.core.content.res.ResourcesCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import hr.bertlab.fluteassistant.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The bundled music font (a cut of Bravura, `tools/cut-music-font.py`) against what the staves draw from it (spec
 * `2026-11-24-flute-staff-music-symbols-design.md`, part 1 and 2): a bad cut, or a re-cut from another Bravura, fails here
 * and not on a phone.
 */
@RunWith(AndroidJUnit4::class)
class MusicSymbolsTest {

    private fun paint(): Paint {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val typeface = ResourcesCompat.getFont(context, R.font.flute_music)
        assertNotNull("the font loads", typeface)
        return Paint().apply { this.typeface = typeface }
    }

    @Test
    fun the_font_has_every_symbol_the_staves_draw() {
        assertEquals(14, MusicSymbols.ALL.size)
        val paint = paint()
        MusicSymbols.ALL.forEach { assertTrue("U+%04X is in the font".format(it.first().code), paint.hasGlyph(it)) }
    }

    /**
     * An advance is the font's own number; text bounds are the outline's control box, a little larger than the curves'
     * own extent that fontTools measured (the clef's foot: 2.69 against 2.632), so they get more room.
     */
    @Test
    fun the_symbols_have_the_measured_sizes() {
        val paint = paint().apply { textSize = 4 * GAP } // a SMuFL em is four line gaps
        fun bounds(text: String) = Rect().also { paint.getTextBounds(text, 0, text.length, it) }
        fun width(name: String, expected: Float, symbol: String) = assertEquals(name, expected, paint.measureText(symbol) / GAP, 0.01f)
        fun rise(name: String, expected: Float, symbol: String) = assertEquals(name, expected, -bounds(symbol).top / GAP, 0.1f)
        fun drop(name: String, expected: Float, symbol: String) = assertEquals(name, expected, bounds(symbol).bottom / GAP, 0.1f)
        with(MusicSymbols) {
            width("the clef's width", CLEF_WIDTH, G_CLEF)
            rise("the clef's rise", CLEF_RISE, G_CLEF)
            drop("the clef's drop", CLEF_DROP, G_CLEF)
            width("a black head's width", NOTE_WIDTH, NOTE_BLACK)
            width("a half head's width", NOTE_WIDTH, NOTE_HALF)
            width("a whole head's width", WHOLE_WIDTH, NOTE_WHOLE)
            drop("a black head's half height", NOTE_HALF_HEIGHT, NOTE_BLACK)
            width("the flag's width", FLAG_WIDTH, FLAG_EIGHTH)
            drop("the flag's drop", FLAG_DROP, FLAG_EIGHTH)
            rise("the quarter rest's rise", REST_QUARTER_RISE, REST_QUARTER)
            drop("the quarter rest's drop", REST_QUARTER_DROP, REST_QUARTER)
            width("the sharp's width", SHARP_WIDTH, SHARP)
            rise("the sharp's rise", SHARP_RISE, SHARP)
            drop("the sharp's drop", SHARP_DROP, SHARP)
            width("the flat's width", FLAT_WIDTH, FLAT)
            rise("the flat's top", FLAT_TOP, FLAT)
            drop("the flat's drop", FLAT_DROP, FLAT)
            width("the natural's width", NATURAL_WIDTH, NATURAL)
            rise("the natural's rise", NATURAL_RISE, NATURAL)
            drop("the natural's drop", NATURAL_DROP, NATURAL)
            width("the dot's width", DOT_WIDTH, DOT)
            width("the comma's width", BREATH_WIDTH, BREATH)
            rise("the comma's rise", BREATH_RISE, BREATH)
        }
    }

    private companion object {
        /** One line gap at the size the metrics are read at, in px. */
        const val GAP = 100f
    }
}
