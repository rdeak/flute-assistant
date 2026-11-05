// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/*
 * A fixed, playful palette for a children's app, taken from the Claude Design canvas: white
 * cards on sky blue (light) or deep navy (dark), thick ink outlines, and bright fills that
 * mean the same thing everywhere (yellow = chosen, green = go, coral = stop, one colour per
 * note letter). Wallpaper-based dynamic colour is deliberately not used so the app looks the
 * same on every child's phone. Text on a bright fill is always [InkOnFill], in both themes.
 */

// Bright fills, the same in both themes.
val Sun = Color(0xFFFFD93D)
val Go = Color(0xFF5BE07E)
val Stop = Color(0xFFFF8A80)
val InkOnFill = Color(0xFF1F2440)

// The big action buttons (BigActionButton), the same in both themes: deep enough that white
// text and icons on them pass contrast, unlike [InkOnFill] on the light [Go] and [Stop].
val ActionGo = Color(0xFF0F7A3E)
val ActionStop = Color(0xFFC62F2F)
/** The flute's "Your turn", the button that makes the phone listen: a third deep action colour, blue. */
val ActionListen = Color(0xFF1F5FBF)
val OnAction = Color.White

/** The tuner's tick when the note is in tune. */
val InTuneGreen = Color(0xFF22A35A)

/** The app icon's golden square, also @drawable/ic_launcher_background. */
val AppIconGold = Color(0xFFFFC857)

/**
 * One colour per note letter (C red to B pink, the order of a rainbow), used for a note
 * wherever it appears: name bubbles, fingering keys, the staff, the flute keys and the tune.
 */
val NoteLetterColors = listOf(
    Color(0xFFFF6B6B), // C
    Color(0xFFFFA047), // D
    Color(0xFFFFD93D), // E
    Color(0xFF7BD67B), // F
    Color(0xFF5FD3C8), // G
    Color(0xFFB39DDB), // A
    Color(0xFFF7A1C4), // B (H in Croatian)
)

/** Everything that changes between light and dark; read it through [FluteTheme.colors]. */
@Immutable
data class FluteColors(
    /** Behind everything. */
    val page: Color,
    /** The big rounded stage each tab draws on, and the bars. */
    val card: Color,
    /** A plain (white in light mode) button. */
    val raised: Color,
    /** Quiet fills inside a card: the dial's centre, idle beat bubbles, the fingering track. */
    val track: Color,
    /** Thin borders of cards and of things that cannot be pressed. */
    val border: Color,
    /** The thick outline and the solid shadow of everything that can be pressed. */
    val outline: Color,
    val text: Color,
    val mutedText: Color,
    /** The tint of the staff's spaces in the note game. */
    val staffSpace: Color,
    /** The metronome ring's five speed bands, slowest first. */
    val tempoBands: List<Color>,
)

val LightFluteColors = FluteColors(
    page = Color(0xFFEAF4FF),
    card = Color(0xFFFFFFFF),
    raised = Color(0xFFFFFFFF),
    track = Color(0xFFE3ECF8),
    border = Color(0xFFC3D2E8),
    outline = Color(0xFF1F2440),
    text = Color(0xFF1F2440),
    mutedText = Color(0xFF4F5878),
    staffSpace = Color(0xFFF1F6FD),
    tempoBands = listOf(
        Color(0xFFBCD7FF),
        Color(0xFFB2E7C0),
        Color(0xFFFFE58A),
        Color(0xFFFFC690),
        Color(0xFFFFA0A0),
    ),
)

val DarkFluteColors = FluteColors(
    page = Color(0xFF171A2E),
    card = Color(0xFF23284A),
    raised = Color(0xFF2F365C),
    track = Color(0xFF2F365C),
    border = Color(0xFF3E4670),
    outline = Color(0xFF090B18),
    text = Color(0xFFF4F6FF),
    mutedText = Color(0xFFBAC2E0),
    staffSpace = Color(0xFF2A3055),
    tempoBands = listOf(
        Color(0xFF5C7FC0),
        Color(0xFF4E9B67),
        Color(0xFFC9A93A),
        Color(0xFFC9854A),
        Color(0xFFC25C5C),
    ),
)
