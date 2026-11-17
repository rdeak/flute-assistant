// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.fingers

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import hr.bertlab.fluteassistant.domain.fingers.FingerPick
import hr.bertlab.fluteassistant.domain.tuner.NoteSpelling
import hr.bertlab.fluteassistant.ui.common.KeyName
import hr.bertlab.fluteassistant.ui.common.SharpFlatKey
import hr.bertlab.fluteassistant.ui.common.TapKey
import hr.bertlab.fluteassistant.ui.common.asTextSize
import hr.bertlab.fluteassistant.ui.common.noteColor
import hr.bertlab.fluteassistant.ui.common.noteName

/** The Fingers tab's keys: the seven letters, C to B, and ♯♭. */
internal const val FINGER_KEYS = 8

/**
 * Key [index] of the tab's eight, a letter (0 = C to 6 = B) or, last, ♯♭, [height] tall and sharing its row's width
 * with the others: both layouts lay the same eight out, in one row upright and two by two on a phone on its side.
 */
@Composable
internal fun RowScope.FingerKey(state: FingerPick, index: Int, onLetter: (Int) -> Unit, onSign: () -> Unit, height: Dp) {
    val modifier = Modifier.weight(1f).height(height)
    if (index < FINGER_KEYS - 1) {
        LetterKey(state, index, onLetter, modifier)
    } else {
        // The flute tab's own ♯♭ key, its picture at the size these narrower keys have room for.
        SharpFlatKey(state.signWaiting, onSign, modifier, pictureScale = 1f)
    }
}

/**
 * One letter key, 0 = C to 6 = B, the flute's key in its letter's colour ([TapKey]): its face, its ring and whether
 * it is off all come from [state] ([FingerPick.faceOf], [FingerPick.isChosen]). The chosen note's key is down in the
 * yellow ring and names that note (C♯ while C♯ is on the screen); while ♯♭ waits the keys name their black notes and D
 * and A are faded and off. It acts as the finger goes down, as every key on the flute tab does, and can be pressed
 * again while chosen (C♯ back to C).
 */
@Composable
private fun LetterKey(state: FingerPick, letter: Int, onLetter: (Int) -> Unit, modifier: Modifier = Modifier) {
    val face = state.faceOf(letter)
    val white = NoteSpelling.whiteOf(letter)
    val name = noteName(face ?: white)
    TapKey(
        label = name,
        down = state.isChosen(letter),
        onTap = { onLetter(letter) },
        modifier = modifier,
        enabled = face != null,
        color = noteColor(white),
    ) {
        KeyName(AnnotatedString(name), maxSize = NameSize.asTextSize())
    }
}

/** A key's name at its largest, in dp: these keys are 35 to 41 dp wide and a name must not grow with the text size. */
private val NameSize = 22.dp
