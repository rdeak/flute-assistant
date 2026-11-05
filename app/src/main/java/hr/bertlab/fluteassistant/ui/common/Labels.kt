package hr.bertlab.fluteassistant.ui.common

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.domain.model.MetronomeConfig
import hr.bertlab.fluteassistant.domain.model.SoundProfile
import hr.bertlab.fluteassistant.domain.model.Subdivision
import hr.bertlab.fluteassistant.domain.tuner.TunerMode
import hr.bertlab.fluteassistant.ui.theme.NoteLetterColors
import hr.bertlab.fluteassistant.ui.tuner.TunerAdvice

/*
 * Display names live in string resources so the app can be localised (English by default,
 * Croatian in values-hr); the domain enums stay language-free.
 */

@StringRes
fun Subdivision.labelRes(): Int = when (this) {
    Subdivision.NONE -> R.string.subdivision_quarter
    Subdivision.EIGHTH -> R.string.subdivision_eighth
    Subdivision.SIXTEENTH -> R.string.subdivision_sixteenth
    Subdivision.TRIPLET -> R.string.subdivision_triplet
    Subdivision.SHUFFLE -> R.string.subdivision_shuffle
}

@StringRes
fun SoundProfile.labelRes(): Int = when (this) {
    SoundProfile.WOODBLOCK -> R.string.sound_woodblock
    SoundProfile.BEEP -> R.string.sound_beep
    SoundProfile.BELL -> R.string.sound_bell
}

@StringRes
fun TunerMode.labelRes(): Int = when (this) {
    TunerMode.IDENTIFY -> R.string.mode_identify
    TunerMode.TARGET -> R.string.mode_target
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

/** One line for preset lists and the playback notification, e.g. "120 BPM · 4/4 · Quarter · Woodblock". */
fun MetronomeConfig.summary(resources: Resources): String = resources.getString(
    R.string.config_summary,
    bpm,
    timeSignature.label,
    resources.getString(subdivision.labelRes()),
    resources.getString(sound.labelRes()),
)

@Composable
@ReadOnlyComposable
fun MetronomeConfig.summary(): String = summary(LocalResources.current)

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

/** Index of the letter (C = 0 to B = 6) each pitch class is written on, sharps on the letter below. */
private val LETTER_OF_PITCH_CLASS = intArrayOf(0, 0, 1, 1, 2, 3, 3, 4, 4, 5, 5, 6)

/** Note name without the octave, e.g. "A♯" (or "B" in Croatian), for the big tuner display. */
fun noteName(resources: Resources, midi: Int): String =
    resources.getStringArray(R.array.note_names)[Math.floorMod(midi, 12)]

@Composable
@ReadOnlyComposable
fun noteName(midi: Int): String = noteName(LocalResources.current, midi)
