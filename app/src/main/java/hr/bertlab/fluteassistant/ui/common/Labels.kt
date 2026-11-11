// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.domain.listen.TunerAdvice
import hr.bertlab.fluteassistant.domain.metronome.TempoBand
import hr.bertlab.fluteassistant.domain.notes.NotesGame
import hr.bertlab.fluteassistant.domain.tuner.TunerMode
import hr.bertlab.fluteassistant.ui.theme.FluteTheme
import hr.bertlab.fluteassistant.ui.theme.NoteLetterColors

/*
 * Display names live in string resources so the app can be localised (English by default,
 * Croatian in values-hr); the domain enums stay language-free.
 */

@StringRes
fun TunerMode.labelRes(): Int = when (this) {
    TunerMode.IDENTIFY -> R.string.mode_identify
    TunerMode.TARGET -> R.string.mode_target
}

/** The pill's word; the emoji is in the string itself, so the pill draws none of its own. */
@StringRes
fun NotesGame.labelRes(): Int = when (this) {
    NotesGame.FIND -> R.string.find_note
    NotesGame.HEAR -> R.string.hear_game
}

/** The animal's name, for a screen reader: the speed is never read as a number. */
@StringRes
fun TempoBand.labelRes(): Int = when (this) {
    TempoBand.SNAIL -> R.string.band_snail
    TempoBand.TURTLE -> R.string.band_turtle
    TempoBand.DUCK -> R.string.band_duck
    TempoBand.RABBIT -> R.string.band_rabbit
    TempoBand.CHEETAH -> R.string.band_cheetah
}

/** The advice line's words, or null when there is nothing to say. */
@StringRes
fun TunerAdvice.labelRes(): Int? = when (this) {
    TunerAdvice.NONE -> null
    TunerAdvice.COME_CLOSER -> R.string.advice_come_closer
    TunerAdvice.CLEAR_NOTE -> R.string.advice_clear_note
    TunerAdvice.ROLL_OUT -> R.string.advice_roll_out
    TunerAdvice.ROLL_IN -> R.string.advice_roll_in
    TunerAdvice.FASTER_AIR -> R.string.advice_faster_air
    TunerAdvice.GENTLER_AIR -> R.string.advice_gentler_air
    TunerAdvice.CHECK_FINGERS -> R.string.advice_check_fingers
    TunerAdvice.PLAY_AGAIN -> R.string.advice_play_again
}

/**
 * Note name with octave for a MIDI number in the user's language. English uses scientific
 * pitch notation ("A♯4", middle C is C4). Croatian uses the German names (H for B, B for
 * B flat) and the octave names its music schools teach: middle C starts the one-lined
 * octave "c1", then "c2", "c3"; below it the small octave is lowercase with no number ("h"),
 * the great octave uppercase ("C") and the contra octave "C1".
 */
fun noteLabel(resources: Resources, midi: Int): String =
    noteLabelParts(resources, midi).let { (name, octave) -> name + octave }

@Composable
@ReadOnlyComposable
fun noteLabel(midi: Int): String = noteLabel(LocalResources.current, midi)

/**
 * [noteLabel] split into the name and the octave figure ("A♯" and "4", "c" and "1", "h" and
 * ""), so the screens can raise the figure: the label reads "A♯⁴" or "c¹" on screen while
 * assistive technology still hears [noteLabel].
 */
fun noteLabelParts(resources: Resources, midi: Int): Pair<String, String> {
    val name = resources.getStringArray(R.array.note_names)[Math.floorMod(midi, 12)]
    val octave = Math.floorDiv(midi, 12) - 1 // scientific: C4 = 60
    if (!resources.getBoolean(R.bool.note_octaves_helmholtz)) return name to octave.toString()
    return when {
        octave >= 4 -> name.lowercase() to (octave - 3).toString()
        octave == 3 -> name.lowercase() to ""
        octave == 2 -> name to ""
        else -> name to (2 - octave).toString()
    }
}

/**
 * [noteLabelParts] as display text: the octave figure small and raised, as every canvas board
 * draws it (`<sup style="font-size: 0.48em; margin-left: 1px">`): 0.48 of the letter's size,
 * its baseline raised about a third of the letter's size as a browser raises a `sup` (so its top
 * meets the top of the letter), and a hair of space before it. Matched to the canvas on
 * 2026-11-02 at the user's request; before, it sat higher (0.55) and touched the letter.
 */
@Composable
@ReadOnlyComposable
fun noteText(midi: Int): AnnotatedString {
    val (name, octave) = noteLabelParts(LocalResources.current, midi)
    return buildAnnotatedString {
        if (octave.isEmpty()) {
            append(name)
            return@buildAnnotatedString
        }
        append(name.dropLast(1))
        // Letter spacing is shared either side of a glyph, so this leaves 1 sp after the letter at any size: the sup's 1 px.
        withStyle(SpanStyle(letterSpacing = OCTAVE_GAP)) { append(name.last()) }
        withStyle(SpanStyle(fontSize = OCTAVE_SIZE, baselineShift = BaselineShift(OCTAVE_RISE))) { append(octave) }
    }
}

private val OCTAVE_SIZE = 0.48.em
private val OCTAVE_GAP = 2.sp

/** How far the octave figure's baseline rises, as a share of the letter's size: a browser's `sup`. */
private const val OCTAVE_RISE = 0.36f

/**
 * The colour of a note's letter ([NoteLetterColors]); a sharp takes the colour of the
 * letter it raises, so C♯ is red like C.
 */
fun noteColor(midi: Int): Color = NoteLetterColors[LETTER_OF_PITCH_CLASS[Math.floorMod(midi, 12)]]

/**
 * The pale tint of a note's colour: [noteColor] at [TINT_SHARE] over the card, so it stays a quiet
 * ground in the light theme and in the dark one. A key of the sticker book is this until the note is
 * learned, and the Pick button's dot of a letter that is not yet.
 */
@Composable
fun noteTint(midi: Int): Color = tintOf(noteColor(midi))

/** [color] at [TINT_SHARE] over the card: [noteTint] for a colour that is already in hand. */
@Composable
fun tintOf(color: Color): Color = lerp(FluteTheme.colors.card, color, TINT_SHARE)

private const val TINT_SHARE = 0.3f

/** Index of the letter (C = 0 to B = 6) each pitch class is written on, sharps on the letter below. */
private val LETTER_OF_PITCH_CLASS = intArrayOf(0, 0, 1, 1, 2, 3, 3, 4, 4, 5, 5, 6)

