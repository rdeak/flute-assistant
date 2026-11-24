// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * An emoji used as an icon. Decorative: the words next to it, or the content description of
 * the control it sits in, carry the meaning.
 */
@Composable
fun Emoji(emoji: String, fontSize: Int, modifier: Modifier = Modifier) = EmojiText(emoji, fontSize.sp, modifier)

/**
 * [Emoji] at a [size] in dp, exactly: a picture that large text must not grow out of its room. Every emoji that is a
 * picture goes through here (the callers' own conversions to a whole number of sp went on 2026-11-23: 19.67 dp was
 * drawn at 19).
 */
@Composable
fun Emoji(emoji: String, size: Dp, modifier: Modifier = Modifier) = EmojiText(emoji, size.asTextSize(), modifier)

@Composable
private fun EmojiText(emoji: String, font: TextUnit, modifier: Modifier) {
    Text(
        text = emoji,
        style = MaterialTheme.typography.titleLarge.copy(fontSize = font, lineHeight = font * 1.2f),
        modifier = modifier.clearAndSetSemantics { },
    )
}

/** The star a note earns, in the notes game's round and under a step of the flute's "Your turn". */
const val STAR_EMOJI = "⭐" // star

/** The speaker on whatever plays a note when tapped: the notes game's Hear and Sound, the tuning guide's "tap the note". */
const val SPEAKER_EMOJI = "🔊" // speaker, loud

/** A star for a note that took more than one try: still earned, but faint. */
const val RETRY_STAR_ALPHA = 0.35f

/** Sparkles: a note in tune, a perfect round, a note that moved up, the staff's found note. */
const val SPARKLES_EMOJI = "✨"
