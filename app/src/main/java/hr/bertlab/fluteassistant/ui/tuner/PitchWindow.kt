// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.tuner

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import hr.bertlab.fluteassistant.ui.common.AppIcons
import hr.bertlab.fluteassistant.ui.common.Emoji
import hr.bertlab.fluteassistant.ui.common.OutlineWidth
import hr.bertlab.fluteassistant.ui.common.SPARKLES_EMOJI
import hr.bertlab.fluteassistant.ui.common.SidewaysGeometry
import hr.bertlab.fluteassistant.ui.common.dashedBorder
import hr.bertlab.fluteassistant.ui.common.noteColor
import hr.bertlab.fluteassistant.ui.common.noteText
import hr.bertlab.fluteassistant.ui.theme.FluteTheme
import hr.bertlab.fluteassistant.ui.theme.InTuneGreen
import hr.bertlab.fluteassistant.ui.theme.InkOnFill
import kotlin.math.abs

/** Cents at either end of the window; anything further is pinned there. */
private const val RANGE_CENTS = 50f

/**
 * Share of the travel from the ring to an end at which the bubble lands as soon as the note
 * leaves the in-tune band, so it never sits half in the ring: the bubble is home exactly when
 * the status line says "Perfect!". Beyond, the travel is even up to [RANGE_CENTS].
 */
private const val OUT_OF_TUNE_SHARE = 0.37f

// The canvas's drawing at full size, in dp; on a short screen all of it shrinks together.
private const val WINDOW_WIDTH = SidewaysGeometry.PITCH_WINDOW_WIDTH
private const val WINDOW_HEIGHT = SidewaysGeometry.PITCH_WINDOW_HEIGHT
private const val RING = 132f
private const val BUBBLE = 112f
private const val HOME_BUBBLE = 120f
private const val LABEL = 62f
private const val BUBBLE_LABEL = 56f
/** Where the dotted guide starts and ends, clear of the ▲ and ▼. */
private const val GUIDE_INSET = 24f
private const val CHEVRON = 20f
private const val ARROW = 30f
/** How far the arrow sits from the bubble's centre, towards the ring. */
private const val ARROW_LEAD = 20f
private const val BADGE = 40f
/** The in-tune badge's centre from the bubble's: right of it and up, on its rim; and the tick in it. */
private const val BADGE_RIGHT = 50f
private const val BADGE_UP = 52f
private const val BADGE_TICK = 20f
/** The two sparkles of a note in tune: the bigger up left, the smaller down right, this far from the bubble's centre each way. */
private const val BIG_SPARKLE = 26
private const val SMALL_SPARKLE = 22
private const val SPARKLE_OUT = 58f
/** The room between the bubble and the arrow beside it. */
private const val ARROW_GAP = 4

private val RingWidth = 5.dp

/** The pitch window's height as drawn; it is only ever smaller. */
val PitchWindowHeight = WINDOW_HEIGHT.dp

/** Width over height of the pitch window. */
const val PITCH_WINDOW_ASPECT_RATIO = WINDOW_WIDTH / WINDOW_HEIGHT

/**
 * The tuner's numberless pitch window. The home ring holds the note to play ([homeMidi]),
 * dashed in its letter's colour, or grey with "…" while identifying in silence, on a dotted
 * up-down guide with ▲ and ▼ at its ends. The child's sound is a bubble that floats above the
 * ring when high and below it when low, further the more [cents] are off and pinned at the
 * ends, with an arrow beside it pointing home. The bubble shows [heardMidi]: the home note in
 * a white bubble outlined in its colour, or another note the child played filled in that
 * note's colour; in tune (within [TunerUiState.IN_TUNE_CENTS], the status line's "Perfect!")
 * it fills, grows into the ring and gets a green tick and sparkles. A screen reader reads
 * [description] instead of the drawing. The drawing keeps the canvas's proportions and
 * shrinks to fit its constraints; it never grows past [maxScale] times the canvas's size: what the card's room allows,
 * up to `WINDOW_MAX_SCALE` upright since 2026-11-18, and what the card's height allows on a phone on its side.
 *
 * With [onTap] the whole window is one button, heard as [tapLabel]: in Tune mode, in silence, a tap plays the chosen note (since
 * 2026-11-18, when the Hear button went: the picture is the button, as a flute on the Fingers tab is). No ripple: the
 * sound and the line's "🔊 Listen" answer the tap.
 */
@Composable
fun PitchWindow(
    homeMidi: Int?,
    heardMidi: Int?,
    cents: Double?,
    description: String,
    modifier: Modifier = Modifier,
    maxScale: Float = 1f,
    onTap: (() -> Unit)? = null,
    tapLabel: String? = null,
) {
    val colors = FluteTheme.colors
    val inTune = cents != null && abs(cents) <= TunerUiState.IN_TUNE_CENTS
    val position by animateFloatAsState(
        targetValue = travel(cents?.toFloat() ?: 0f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "bubble",
    )

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .then(if (onTap != null) Modifier.clickable(indication = null, interactionSource = null, onClick = onTap) else Modifier)
            .clearAndSetSemantics {
                contentDescription = description
                if (onTap != null) {
                    role = Role.Button
                    onClick(tapLabel) {
                        onTap()
                        true
                    }
                }
            },
    ) {
        val scale = minOf(maxScale, maxWidth / WINDOW_WIDTH.dp, maxHeight / WINDOW_HEIGHT.dp)
        fun sized(value: Float): Dp = (value * scale).dp
        val travelRoom = sized((WINDOW_HEIGHT - BUBBLE) / 2)
        val bubbleSize by animateDpAsState(sized(if (inTune) HOME_BUBBLE else BUBBLE), label = "bubbleSize")
        val density = LocalDensity.current
        val labelSize = with(density) { sized(LABEL).toSp() }

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(sized(WINDOW_WIDTH), sized(WINDOW_HEIGHT))) {
            Guide(inset = sized(GUIDE_INSET), chevron = sized(CHEVRON), modifier = Modifier.matchParentSize())
            HomeRing(midi = homeMidi, size = sized(RING), fontSize = labelSize)

            if (heardMidi == null || cents == null) return@Box
            // The bubble's centre below the ring's, in px (up is negative); read at placement, so
            // the moving bubble only re-places, never recomposes.
            fun Density.bubbleY(): Float = -travelRoom.toPx() * position
            SoundBubble(
                midi = heardMidi,
                filled = inTune || heardMidi != homeMidi,
                size = bubbleSize,
                fontSize = with(density) { sized(if (inTune) LABEL else BUBBLE_LABEL).toSp() },
                modifier = Modifier.offset { IntOffset(0, bubbleY().toInt()) },
            )
            if (inTune) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .offset { IntOffset(sized(BADGE_RIGHT).roundToPx(), (bubbleY() - sized(BADGE_UP).toPx()).toInt()) }
                        .size(sized(BADGE))
                        .clip(CircleShape)
                        .background(InTuneGreen)
                        .border(OutlineWidth, Color.White, CircleShape),
                ) {
                    Icon(AppIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(sized(BADGE_TICK)))
                }
                Emoji(SPARKLES_EMOJI, fontSize = (BIG_SPARKLE * scale).toInt(), modifier = Modifier.offset(sized(-SPARKLE_OUT), sized(-SPARKLE_OUT)))
                Emoji(SPARKLES_EMOJI, fontSize = (SMALL_SPARKLE * scale).toInt(), modifier = Modifier.offset(sized(SPARKLE_OUT), sized(SPARKLE_OUT)))
            } else {
                val high = cents > 0
                Icon(
                    imageVector = if (high) AppIcons.ArrowDown else AppIcons.ArrowUp,
                    contentDescription = null,
                    tint = colors.text,
                    modifier = Modifier
                        .offset {
                            val lead = sized(ARROW_LEAD).toPx()
                            IntOffset(
                                sized(BUBBLE / 2 + ARROW_GAP + ARROW / 2).roundToPx(),
                                (bubbleY() + if (high) lead else -lead).toInt(),
                            )
                        }
                        .size(sized(ARROW)),
                )
            }
        }
    }
}

/** The dotted up-down line through the window, with ▲ and ▼ at its ends. */
@Composable
private fun Guide(inset: Dp, chevron: Dp, modifier: Modifier) {
    val colors = FluteTheme.colors
    Box(modifier) {
        Canvas(Modifier.matchParentSize()) {
            val dot = OutlineWidth.toPx()
            val x = size.width / 2
            drawLine(
                color = colors.border,
                start = Offset(x, inset.toPx()),
                end = Offset(x, size.height - inset.toPx()),
                strokeWidth = dot,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dot, dot)),
            )
        }
        Icon(
            AppIcons.ChevronUp,
            contentDescription = null,
            tint = colors.mutedText,
            modifier = Modifier.align(Alignment.TopCenter).size(chevron),
        )
        Icon(
            AppIcons.ChevronDown,
            contentDescription = null,
            tint = colors.mutedText,
            modifier = Modifier.align(Alignment.BottomCenter).size(chevron),
        )
    }
}

/** The ring the bubble comes home to, dashed in [midi]'s colour, or grey with "…" when there is no note yet. */
@Composable
private fun HomeRing(midi: Int?, size: Dp, fontSize: TextUnit) {
    val colors = FluteTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.card)
            .dashedBorder(RingWidth, midi?.let(::noteColor) ?: colors.border, CircleShape),
    ) {
        if (midi != null) {
            NoteName(midi, fontSize, colors.text)
        } else {
            Text(
                text = "…",
                style = MaterialTheme.typography.displayLarge.copy(fontSize = fontSize, lineHeight = 1.em),
                color = colors.text,
                modifier = Modifier.alpha(0.6f),
            )
        }
    }
}

/** The child's sound: [midi] filled in its colour, or (not [filled]) in a white bubble outlined in it. */
@Composable
private fun SoundBubble(midi: Int, filled: Boolean, size: Dp, fontSize: TextUnit, modifier: Modifier) {
    val colors = FluteTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (filled) noteColor(midi) else colors.card)
            .border(if (filled) OutlineWidth else RingWidth, if (filled) colors.outline else noteColor(midi), CircleShape),
    ) {
        NoteName(midi, fontSize, if (filled) InkOnFill else colors.text)
    }
}

/** A note's name with its raised octave, shrinking to fit a long one (cis³) into its circle. */
@Composable
private fun NoteName(midi: Int, fontSize: TextUnit, color: Color) {
    Text(
        text = noteText(midi),
        style = MaterialTheme.typography.displayLarge.copy(lineHeight = 1.em, letterSpacing = 0.sp),
        color = color,
        textAlign = TextAlign.Center,
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = fontSize),
        modifier = Modifier.padding(horizontal = 10.dp),
    )
}

/**
 * Where the bubble floats for [cents], -1 (bottom, too low) to 1 (top, too high): home (0)
 * while in tune, then from [OUT_OF_TUNE_SHARE] of the way out evenly to the end at [RANGE_CENTS].
 */
private fun travel(cents: Float): Float {
    val inTuneCents = TunerUiState.IN_TUNE_CENTS.toFloat()
    val magnitude = abs(cents).coerceAtMost(RANGE_CENTS)
    if (magnitude <= inTuneCents) return 0f
    val share = OUT_OF_TUNE_SHARE + (magnitude - inTuneCents) / (RANGE_CENTS - inTuneCents) * (1f - OUT_OF_TUNE_SHARE)
    return if (cents < 0) -share else share
}
