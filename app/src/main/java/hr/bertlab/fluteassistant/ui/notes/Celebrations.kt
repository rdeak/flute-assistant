// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.notes

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import hr.bertlab.fluteassistant.ui.common.Emoji

/**
 * The small celebration over the staff when the streak reaches [MAX_FLAMES], and again at every fifth
 * flame after (10, 15, ...): six sparkles and party poppers rise from the staff's middle and fade
 * over [BURST_MS]. It is a picture and nothing else: no words and no number, hidden from
 * accessibility (the tag is only for tests), and it handles no touch at all, so every finger
 * falls through to the staff beneath it, whose slide is the game. The caller lays it over the
 * staff ([Modifier.matchParentSize]) and never inside it. Nothing is composed for any other streak.
 */
@Composable
internal fun StreakBurst(streak: Int, modifier: Modifier = Modifier) {
    if (streak <= 0 || streak % MAX_FLAMES != 0) return
    // One run from 0 to 1 drives every piece, started again for each fifth flame.
    val progress = remember { Animatable(0f) }
    LaunchedEffect(streak) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(BURST_MS, easing = LinearOutSlowInEasing))
    }
    // Pictures, so in dp: at a larger text size they keep their size, as the flames do.
    val fontSize = with(LocalDensity.current) { BURST_EMOJI_SIZE.dp.toSp().value.toInt() }
    Box(modifier.clearAndSetSemantics { testTag = BURST_TAG }) {
        BURST_PIECES.forEach { piece ->
            Emoji(
                emoji = piece.emoji,
                fontSize = fontSize,
                modifier = Modifier
                    .align(BiasAlignment(horizontalBias = piece.bias, verticalBias = 0f))
                    .graphicsLayer {
                        // Read here, in the draw phase, so a frame of the burst recomposes nothing.
                        val p = progress.value
                        translationY = (piece.start - BURST_RISE * p).toPx()
                        // Full for the first half of the rise, then gone.
                        alpha = ((1f - p) * 2f).coerceIn(0f, 1f)
                    },
            )
        }
    }
}

/**
 * One piece of the burst: its picture, where it stands across the width ([bias], -1 the left edge to 1 the
 * right, as [BiasAlignment] reads it) and how far below the middle it starts ([start], negative above), so
 * that the six read as a burst and not as a row.
 */
private class BurstPiece(val emoji: String, val bias: Float, val start: Dp)

private val BURST_PIECES = listOf(
    BurstPiece(SPARKLES_EMOJI, -0.86f, 10.dp),
    BurstPiece(PARTY_EMOJI, -0.52f, (-14).dp),
    BurstPiece(SPARKLES_EMOJI, -0.18f, 18.dp),
    BurstPiece(PARTY_EMOJI, 0.18f, (-6).dp),
    BurstPiece(SPARKLES_EMOJI, 0.52f, 14.dp),
    BurstPiece(SPARKLES_EMOJI, 0.86f, (-12).dp),
)

/** How long the burst takes to rise and fade. */
private const val BURST_MS = 1000

/** How far every piece rises. */
private val BURST_RISE = 120.dp

/** The size of a piece, in dp (about 28 sp at the usual text size). */
private const val BURST_EMOJI_SIZE = 28

private const val BURST_TAG = "streak_burst"

private const val SPARKLES_EMOJI = "✨" // sparkles
private const val PARTY_EMOJI = "🎉" // party popper
