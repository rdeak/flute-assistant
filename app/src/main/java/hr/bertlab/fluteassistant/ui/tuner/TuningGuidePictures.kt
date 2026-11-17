// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.tuner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.ui.common.AppIcons
import hr.bertlab.fluteassistant.ui.common.Emoji
import hr.bertlab.fluteassistant.ui.common.SPEAKER_EMOJI
import hr.bertlab.fluteassistant.ui.common.noteColor
import hr.bertlab.fluteassistant.ui.common.noteText
import hr.bertlab.fluteassistant.ui.theme.FluteTheme
import hr.bertlab.fluteassistant.ui.theme.InTuneGreen
import hr.bertlab.fluteassistant.ui.theme.InkOnFill
import hr.bertlab.fluteassistant.ui.theme.Sun
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/*
 * The pictures of the guide to tuning the flute (spec `2026-11-14-tuning-guide-design.md`, "The guide"),
 * drawn as the brainstorm's mockups draw them (`tuning-guide.html`, `last-card-v4.html`, `fluteSvg`).
 * Each is laid out at one design size and scaled as a whole to the room it is given, with the text size
 * held at the normal one inside it, as it is a picture: its few words are part of the drawing, and the
 * card's own title and words, which do follow the text size, say everything it shows. So it is silent
 * to a screen reader.
 */

/** Which way the arrow over the head joint points: in towards the body, or out. */
enum class JointArrow { PUSH_IN, PULL_OUT }

/** The picture of [card], scaled to fill [modifier]'s size without changing its shape; silent to a screen reader. */
@Composable
internal fun GuidePicture(card: GuideCard, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier = modifier.clearAndSetSemantics { testTag = "guide_picture" },
        contentAlignment = Alignment.Center,
    ) {
        val scale = min(maxWidth / PictureWidth, maxHeight / PictureHeight).coerceAtMost(MAX_SCALE)
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .requiredSize(PictureWidth, PictureHeight)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
            ) {
                when (card) {
                    GuideCard.WARM_UP -> WarmUpPicture()
                    GuideCard.HEAD_JOINT -> HeadJointPicture()
                    GuideCard.EACH_NOTE -> EachNotePicture()
                    GuideCard.ONE_NOTE_OFF -> FluteSide(Modifier.width(300.dp), headColor = Sun, rollRing = true)
                }
            }
        }
    }
}

/** A thermometer over the flute with warm breath rising from its lip plate, and three long notes under it. */
@Composable
private fun WarmUpPicture() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Emoji(THERMOMETER_EMOJI, fontSize = 56)
        FluteSide(Modifier.width(300.dp), breath = true)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(60, 62, 64).forEach { midi -> LongNote(noteColor(midi)) }
        }
    }
}

/**
 * A flute whose head joint goes in, named "push in", over one where it comes out, "pull out": the words the tuner's line
 * says on a flat or sharp A4 ("🔧 Push the head joint in a little"). Each stood under a chip with the tuner's "⬆️ A little
 * higher" / "⬇️ A little lower" until 2026-11-18, when the one line began to say the move itself and those words left
 * the screen (the user picked "drop the chips").
 */
@Composable
private fun HeadJointPicture() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        JointMove(JointArrow.PUSH_IN, R.string.tune_guide_push_in)
        JointMove(JointArrow.PULL_OUT, R.string.tune_guide_pull_out)
    }
}

/** One of card 2's flutes: its head joint moved as [arrow] says, and the move's [label] under it. */
@Composable
private fun JointMove(arrow: JointArrow, label: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val gap = if (arrow == JointArrow.PULL_OUT) PULLED_GAP else 0f
        FluteSide(Modifier.width(fluteWidth(gap)), jointGap = gap, arrow = arrow)
        Text(
            stringResource(label),
            color = FluteTheme.colors.text,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.Start)
                .padding(start = 18.dp),
        )
    }
}

/**
 * ‹ a note ›, a finger and a speaker under it (tap the note to hear it: the tuner has had no Hear pill since
 * 2026-11-18), an arrow down, then a long note and the note's ring filled with a tick.
 */
@Composable
private fun EachNotePicture() {
    val midi = EXAMPLE_NOTE
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ChevronDisc(left = true)
            NoteRing(midi, filled = false)
            ChevronDisc(left = false)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Emoji(TAP_EMOJI, fontSize = 24)
            Emoji(SPEAKER_EMOJI, fontSize = 22)
        }
        Emoji(DOWN_EMOJI, fontSize = 28)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LongNote(noteColor(midi), width = 120)
            NoteRing(midi, filled = true)
        }
    }
}

/**
 * Card 2's flutes are drawn at one scale, [HeadJointFluteWidth] for the closed one, so the pulled-out flute is
 * longer by its open joint ([jointGap] grid steps) rather than drawn smaller to fit the same width.
 */
internal fun fluteWidth(jointGap: Float): Dp = HeadJointFluteWidth * ((GRID_WIDTH + jointGap) / GRID_WIDTH)

/** A long held note: a bar in the note's colour with an ink outline. */
@Composable
private fun LongNote(color: Color, width: Int = 80) {
    Box(
        Modifier
            .size(width.dp, 16.dp)
            .background(color, RoundedCornerShape(8.dp))
            .border(2.dp, InkOnFill, RoundedCornerShape(8.dp)),
    )
}

/** A ‹ or › as the tuner draws its buttons, but only a picture of one. */
@Composable
private fun ChevronDisc(left: Boolean) {
    val colors = FluteTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .background(colors.raised, CircleShape)
            .border(3.dp, colors.outline, CircleShape),
    ) {
        Icon(if (left) AppIcons.ChevronLeft else AppIcons.ChevronRight, contentDescription = null, tint = colors.text, modifier = Modifier.size(26.dp))
    }
}

/** The note's home ring: dashed and empty, or filled in its colour with the tuner's green tick. */
@Composable
internal fun NoteRing(midi: Int, filled: Boolean) {
    val colors = FluteTheme.colors
    val color = noteColor(midi)
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(86.dp)) {
        Canvas(Modifier.size(86.dp)) {
            val stroke = 4.dp.toPx()
            val radius = size.minDimension / 2f - stroke / 2f
            if (filled) {
                drawCircle(color, radius)
                drawCircle(color, radius, style = Stroke(stroke))
            } else {
                drawCircle(colors.card, radius)
                drawCircle(color, radius, style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f * density, 6f * density))))
            }
        }
        Text(noteText(midi), color = if (filled) InkOnFill else colors.text, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        if (filled) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 8.dp, y = (-8).dp)
                    .size(30.dp)
                    .background(InTuneGreen, CircleShape)
                    .border(3.dp, colors.card, CircleShape),
            ) {
                Icon(AppIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/**
 * A flute seen from the side, as the mockups' `fluteSvg` draws it on a 340 x 130 grid (wider by
 * [jointGap] when the joint is pulled open, and cut at 92 under the tube unless the ring needs the room): a crown and a lip plate with its hole on the head joint, a
 * socket band where the head joint slides into the body, a body with six keys and a foot with one. It is
 * silver in both themes, outlined in ink. Optional: a push-in or pull-out [arrow] over the head joint, the
 * head joint in [headColor], a [rollRing] round the tube at the lip plate whose arrowhead follows the ring,
 * and warm [breath] rising from the hole.
 */
@Composable
internal fun FluteSide(
    modifier: Modifier,
    jointGap: Float = 0f,
    arrow: JointArrow? = null,
    headColor: Color? = null,
    rollRing: Boolean = false,
    breath: Boolean = false,
) {
    val colors = FluteTheme.colors
    val gridWidth = GRID_WIDTH + jointGap
    val gridHeight = if (rollRing) GRID_HEIGHT else GRID_HEIGHT_NO_RING
    Canvas(modifier.aspectRatio(gridWidth / gridHeight)) {
        val unit = size.width / gridWidth
        val grid = Grid(this, unit)
        val head = headColor ?: Metal
        val line = colors.text

        if (breath) grid.breath()
        if (rollRing) grid.ringBack(colors.mutedText)

        grid.box(8f, FLUTE_Y - TUBE / 2f, HEAD_END - 8f, TUBE, 6f, head)
        grid.box(2f, FLUTE_Y - 14f, 12f, 28f, 5f, MetalShade)
        grid.box(44f, FLUTE_Y - 17f, 38f, 34f, 14f, head)
        drawOval(InkOnFill, grid.at(63f - 9f, FLUTE_Y - 6f), Size(18f * unit, 12f * unit))
        val bodyStart = HEAD_END + jointGap
        grid.box(bodyStart, FLUTE_Y - TUBE / 2f, BODY_END - HEAD_END, TUBE, 4f, Metal)
        grid.box(bodyStart - 6f, FLUTE_Y - TUBE / 2f - 3f, 12f, TUBE + 6f, 3f, MetalShade)
        BODY_KEYS.forEach { grid.key(it + jointGap, 7f) }
        grid.box(BODY_END + jointGap, FLUTE_Y - TUBE / 2f, 46f, TUBE, 5f, Metal)
        grid.key(314f + jointGap, 6f)

        when (arrow) {
            JointArrow.PUSH_IN -> grid.arrow(40f, 100f, pointsRight = true, InTuneGreen)
            JointArrow.PULL_OUT -> grid.arrow(48f, 108f, pointsRight = false, PullOrange)
            null -> Unit
        }
        if (rollRing) grid.ringFront(line)
    }
}

/** The mockup's 340 x 130 grid drawn on [scope], scaled by [unit] px per grid step. */
private class Grid(private val scope: DrawScope, private val unit: Float) {
    fun at(x: Float, y: Float) = Offset(x * unit, y * unit)

    /** A rounded box filled with [fill] and outlined in ink, as every part of the flute is. */
    fun box(x: Float, y: Float, w: Float, h: Float, r: Float, fill: Color) = with(scope) {
        drawRoundRect(fill, at(x, y), Size(w * unit, h * unit), CornerRadius(r * unit))
        drawRoundRect(InkOnFill, at(x, y), Size(w * unit, h * unit), CornerRadius(r * unit), style = Stroke(3f * unit))
    }

    fun key(x: Float, r: Float) = with(scope) {
        drawCircle(Color.White, r * unit, at(x, FLUTE_Y))
        drawCircle(InkOnFill, r * unit, at(x, FLUTE_Y), style = Stroke(2.5f * unit))
    }

    /** A thick arrow above the head joint from [from] to [to] (grid x), its head at the end it points to. */
    fun arrow(from: Float, to: Float, pointsRight: Boolean, color: Color) = with(scope) {
        val y = 30f
        val stroke = Stroke(7f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawLine(color, at(from, y), at(to, y), 7f * unit, StrokeCap.Round)
        val tip = if (pointsRight) to + 8f else from - 8f
        val back = if (pointsRight) tip - 16f else tip + 16f
        val head = Path().apply {
            moveTo(back * unit, (y - 12f) * unit)
            lineTo(tip * unit, y * unit)
            lineTo(back * unit, (y + 12f) * unit)
        }
        drawPath(head, color, style = stroke)
    }

    /** Two warm waves rising from the lip plate. */
    fun breath() = with(scope) {
        listOf(36f to BreathRed, 56f to BreathOrange).forEach { (x, color) ->
            val wave = Path().apply {
                moveTo(x * unit, 50f * unit)
                quadraticTo((x - 10f) * unit, 38f * unit, x * unit, 28f * unit)
                quadraticTo((x + 10f) * unit, 18f * unit, x * unit, 8f * unit)
            }
            drawPath(wave, color, style = Stroke(5f * unit, cap = StrokeCap.Round))
        }
    }

    /** The ring's half behind the tube: dotted, from the top left round to the bottom. */
    fun ringBack(color: Color) = with(scope) {
        drawArc(
            color = color,
            startAngle = 90f,
            sweepAngle = 270f - RING_BACK_START,
            useCenter = false,
            topLeft = at(RING_X - RING_RX, FLUTE_Y - RING_RY),
            size = Size(2 * RING_RX * unit, 2 * RING_RY * unit),
            style = Stroke(5f * unit, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, 12f * unit))),
        )
    }

    /**
     * The ring's half in front: solid from the bottom up the right, ending in a filled head that follows the
     * ring. At night its line is near-white, which all but vanishes where it crosses a yellow head joint, so a
     * light line gets an ink casing; by day the line is ink already and is drawn as the mockup draws it.
     */
    fun ringFront(color: Color) = with(scope) {
        val cased = color.luminance() > 0.5f
        // Angles are counted as on paper (anticlockwise from the right, y up); the canvas counts clockwise.
        fun arc(stroke: Float, arcColor: Color) = drawArc(
            color = arcColor,
            startAngle = -RING_HEAD_BASE,
            sweepAngle = RING_HEAD_BASE + 90f,
            useCenter = false,
            topLeft = at(RING_X - RING_RX, FLUTE_Y - RING_RY),
            size = Size(2 * RING_RX * unit, 2 * RING_RY * unit),
            style = Stroke(stroke * unit, cap = StrokeCap.Round),
        )
        if (cased) arc(7f + 2 * CASING, InkOnFill)
        arc(7f, color)
        val base = ringPoint(RING_HEAD_BASE)
        val tip = ringPoint(RING_HEAD_TIP)
        val along = Offset(tip.x - base.x, tip.y - base.y)
        val length = hypot(along.x, along.y)
        val across = Offset(-along.y / length * RING_HEAD_HALF, along.x / length * RING_HEAD_HALF)
        val head = Path().apply {
            moveTo(tip.x * unit, tip.y * unit)
            lineTo((base.x + across.x) * unit, (base.y + across.y) * unit)
            lineTo((base.x - across.x) * unit, (base.y - across.y) * unit)
            close()
        }
        if (cased) drawPath(head, InkOnFill, style = Stroke((3f + 2 * CASING) * unit, join = StrokeJoin.Round))
        drawPath(head, color)
        drawPath(head, color, style = Stroke(3f * unit, join = StrokeJoin.Round))
    }

    /** A point of the ring, in grid steps, at [degrees] counted as on paper. */
    private fun ringPoint(degrees: Float): Offset {
        val t = Math.toRadians(degrees.toDouble())
        return Offset(RING_X + RING_RX * cos(t).toFloat(), FLUTE_Y - RING_RY * sin(t).toFloat())
    }
}

private val HeadJointFluteWidth = 250.dp

/** How far card 2's pulled-out joint stands open, in grid steps. */
private const val PULLED_GAP = 22f

private val PictureWidth = 320.dp
private val PictureHeight = 300.dp

/** A picture grows past its design size only this much, on a big card sideways. */
private const val MAX_SCALE = 1.25f

private const val GRID_WIDTH = 340f
private const val GRID_HEIGHT = 130f

/** Without the ring nothing is drawn under the tube's bottom edge (81) but its outline. */
private const val GRID_HEIGHT_NO_RING = 92f
private const val FLUTE_Y = 70f
private const val TUBE = 22f
private const val HEAD_END = 118f
private const val BODY_END = 290f
private val BODY_KEYS = listOf(150f, 172f, 194f, 226f, 248f, 270f)

/** The roll ring round the tube at the hole: its centre, radii, where its arrowhead starts and ends, and its half width. */
private const val RING_X = 63f
private const val RING_RX = 26f
private const val RING_RY = 48f
private const val RING_HEAD_BASE = 62f
private const val RING_HEAD_TIP = 104f
private const val RING_HEAD_HALF = 11f

/** The ink casing round a light (night) ring, each side, in grid steps. */
private const val CASING = 2f

/** Where the dotted back half starts, counted as on paper, a little past the arrowhead's tip. */
private const val RING_BACK_START = 112f

/** Silver, in both themes: the flute is lighter than the card at night too. */
private val Metal = Color(0xFFDCE3EE)
private val MetalShade = Color(0xFFC3D2E8)
private val PullOrange = Color(0xFFE8590C)
private val BreathRed = Color(0xFFFF8A80)
private val BreathOrange = Color(0xFFFFA047)

/** The note on the third card's picture: C⁵ (c² in Croatian), as the mockup draws it. */
private const val EXAMPLE_NOTE = 72

private const val THERMOMETER_EMOJI = "🌡️" // thermometer
private const val DOWN_EMOJI = "⬇️" // down arrow
private const val TAP_EMOJI = "👆" // a finger pointing up: tap the note above it
