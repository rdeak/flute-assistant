// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.res.ResourcesCompat
import hr.bertlab.fluteassistant.R

/**
 * The music symbols the staves draw from the bundled font, `res/font/flute_music.otf`: a cut of Steinberg's Bravura
 * (SIL OFL 1.1, renamed "Flute Assistant Music"; `tools/cut-music-font.py`, `NOTICE`), since 2026-11-24 (specs
 * `2026-11-24-fingers-music-symbols-and-book-chart-design.md`, `2026-11-24-flute-staff-music-symbols-design.md`,
 * `2026-11-24-notes-staff-music-symbols-design.md`). The Fingers tab's staff (`ui/fingers/SymbolStaff`), the flute
 * tab's (`ui/flute/StaffGeometry`) and the notes game's (`ui/notes/NoteStaffSymbols`) all read them here.
 *
 * Sizes are in staff spaces (line gaps), measured with fontTools on Bravura 1.482; a SMuFL em is four of them, and each
 * symbol's baseline is its line (the clef's G line, a note's or a sign's own line, a rest's middle line). `MusicSymbolsTest`
 * checks them against the bundled font.
 */
object MusicSymbols {
    /** The symbols, by their SMuFL names. */
    const val G_CLEF = "\uE050"
    const val NOTE_BLACK = "\uE0A4"
    const val NOTE_HALF = "\uE0A3"
    const val NOTE_WHOLE = "\uE0A2"
    const val FLAG_EIGHTH = "\uE240"
    const val REST_WHOLE = "\uE4E3"
    const val REST_HALF = "\uE4E4"
    const val REST_QUARTER = "\uE4E5"
    const val REST_EIGHTH = "\uE4E6"
    const val SHARP = "\uE262"
    const val FLAT = "\uE260"
    const val NATURAL = "\uE261"
    const val DOT = "\uE1E7"
    const val BREATH = "\uE4CE"

    /** Every symbol the font must hold: what `tools/cut-music-font.py` keeps. */
    val ALL: List<String> = listOf(
        G_CLEF, NOTE_BLACK, NOTE_HALF, NOTE_WHOLE, FLAG_EIGHTH, REST_WHOLE, REST_HALF, REST_QUARTER, REST_EIGHTH,
        SHARP, FLAT, NATURAL, DOT, BREATH,
    )

    /** The treble clef: its width, its reach over and under its G line, and where along it its highest point stands. */
    const val CLEF_WIDTH = 2.684f
    const val CLEF_RISE = 4.392f
    const val CLEF_DROP = 2.632f
    const val CLEF_TOP_X = 1.82f

    /** The heads: a black or half head's width, a whole head's, and half a head's height. */
    const val NOTE_WIDTH = 1.18f
    const val WHOLE_WIDTH = 1.688f
    const val NOTE_HALF_HEIGHT = 0.5f

    /** An eighth's flag, hanging from its stem's top: its width and how far it hangs. */
    const val FLAG_WIDTH = 1.056f
    const val FLAG_DROP = 3.24f

    /** The half and the whole rest's block: its width. */
    const val REST_BLOCK_WIDTH = 1.132f

    /** The quarter rest's reach over and under the middle line. */
    const val REST_QUARTER_RISE = 1.492f
    const val REST_QUARTER_DROP = 1.5f

    /** The signs: each one's width and its reach over and under its note's line. */
    const val SHARP_WIDTH = 0.996f
    const val SHARP_RISE = 1.4f
    const val SHARP_DROP = 1.392f
    const val FLAT_WIDTH = 0.904f
    const val FLAT_TOP = 1.756f
    const val FLAT_DROP = 0.7f
    const val NATURAL_WIDTH = 0.672f
    const val NATURAL_RISE = 1.364f
    const val NATURAL_DROP = 1.34f

    /** The dot after a dotted note, and the breath's comma (its reach over its baseline). */
    const val DOT_WIDTH = 0.4f
    const val BREATH_WIDTH = 0.61f
    const val BREATH_RISE = 1.004f

    /**
     * What fills an open head's hollow with the letter's colour, its radii in staff spaces: an oval midway in the ring where
     * the ring is thinnest, measured on the font's outline. The whole note's hollow is round; the half note's is a slot,
     * so its oval is turned [HALF_HOLLOW_TURN] degrees on the screen (rising to the right).
     */
    const val WHOLE_HOLLOW_RX = 0.70f
    const val WHOLE_HOLLOW_RY = 0.46f
    const val HALF_HOLLOW_RX = 0.59f
    const val HALF_HOLLOW_RY = 0.31f
    const val HALF_HOLLOW_TURN = -31.5f

    /** A SMuFL em: four line gaps of [gap]. */
    fun em(gap: Float): Float = 4 * gap
}

/** A paint that draws the music font, anti-aliased: one per context, handed to [drawMusicSymbol]. */
@Composable
fun rememberMusicPaint(): Paint {
    val context = LocalContext.current
    return remember(context) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = ResourcesCompat.getFont(context, R.font.flute_music)
            isSubpixelText = true // a symbol stands exactly where its hollow's oval does
        }
    }
}

/**
 * Draws [symbol] (one of [MusicSymbols]) in [color] at text size [em], its baseline on [baseline] and [x] where [align]
 * says, on the native canvas, so the draw scope's own scale applies (a staff drawn in dp draws its symbols in dp). With
 * [edge], the symbol's outline is stroked [edgeWidth] wide in that colour after the fill: a yellow head keeps its edge
 * on the yellow lit disc. Leaves [paint] filling.
 */
fun DrawScope.drawMusicSymbol(
    paint: Paint,
    symbol: String,
    x: Float,
    baseline: Float,
    em: Float,
    color: Color,
    align: Paint.Align = Paint.Align.LEFT,
    edge: Color? = null,
    edgeWidth: Float = 0f,
) {
    val canvas = drawContext.canvas.nativeCanvas
    paint.textSize = em
    paint.textAlign = align
    paint.style = Paint.Style.FILL
    paint.color = color.toArgb()
    canvas.drawText(symbol, x, baseline, paint)
    if (edge != null) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = edgeWidth
        paint.color = edge.toArgb()
        canvas.drawText(symbol, x, baseline, paint)
        paint.style = Paint.Style.FILL
    }
}

/**
 * [symbol] drawn as its outline path, for a symbol too tall for the text renderer's glyph cache: Android 7 to 8.1's
 * renderer drops a glyph taller than its cache texture ("Font size too large to fit in cache"), where a path always
 * draws. The notes game's clef is one (about 470 px tall on a phone, more on a dense one or a tablet); the other
 * symbols are a fraction of that and go through [drawMusicSymbol]. Nothing is drawn at an [em] of 0 or less.
 */
fun DrawScope.drawMusicSymbolPath(
    paint: Paint,
    symbol: String,
    x: Float,
    baseline: Float,
    em: Float,
    color: Color,
    align: Paint.Align = Paint.Align.LEFT,
) {
    if (em <= 0f) return
    paint.textSize = em
    paint.textAlign = align
    val outline = android.graphics.Path()
    paint.getTextPath(symbol, 0, symbol.length, x, baseline, outline)
    drawPath(outline.asComposePath(), color)
}
