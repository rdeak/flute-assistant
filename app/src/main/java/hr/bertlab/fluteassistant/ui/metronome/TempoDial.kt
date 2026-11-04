package hr.bertlab.fluteassistant.ui.metronome

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.ui.common.Emoji
import hr.bertlab.fluteassistant.ui.common.chunky
import hr.bertlab.fluteassistant.ui.common.facingRight
import hr.bertlab.fluteassistant.ui.common.hopOnBeat
import hr.bertlab.fluteassistant.ui.theme.FluteTheme
import hr.bertlab.fluteassistant.ui.theme.Sun
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val START_ANGLE = 135f
private const val SWEEP_ANGLE = 270f

/** The canvas drew the ring in a 304 dp box; every size here is a share of that. */
private const val DESIGN_SIZE = 304f
private const val BAND_RADIUS = 120f / DESIGN_SIZE
private const val BAND_WIDTH = 34f / DESIGN_SIZE
private const val INNER_EDGE = 103f / DESIGN_SIZE
private const val OUTER_EDGE = 137f / DESIGN_SIZE
private const val CENTRE_RADIUS = 94f / DESIGN_SIZE
private const val KNOB_SIZE = 68f / DESIGN_SIZE

/** Degrees left blank between two bands. */
private const val BAND_GAP = 2.4f

/**
 * A chunky tempo ring split into the five [TempoBand]s, each a fifth of the ring in its own
 * colour with its animal on it. A yellow knob carrying the current band's animal sits at the
 * tempo ([dialFraction]); dragging anywhere on the ring moves it with the finger, ticking at
 * every tempo, and tapping a band's animal jumps to that band ([TempoBand.jumpFrom]). The
 * middle shows the tempo figure alone (the animal on the knob names the speed, and a screen reader
 * hears the band as the ring's state); while playing, the knob's animal hops with
 * [flash] (read in the layer phase only) on every beat.
 *
 * Tapping the number swaps it for a [TempoWheel] in the same spot while [editing] is true;
 * the caller owns that flag so it can also close the wheel on a tap anywhere else, using
 * the wheel's position reported through [onWheelPositioned].
 * Exposes slider semantics so assistive technology can adjust the tempo too.
 */
@Composable
fun TempoDial(
    bpm: Int,
    onBpmChange: (Int) -> Unit,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onWheelPositioned: (LayoutCoordinates) -> Unit,
    flash: () -> Float,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val currentBpm by rememberUpdatedState(bpm)
    val currentOnBpmChange by rememberUpdatedState(onBpmChange)
    val colors = FluteTheme.colors
    val dialDescription = stringResource(R.string.tempo_dial)
    val band = tempoBand(bpm)
    val bandName = stringResource(band.labelRes)
    val minBpm = TempoBand.MIN_BPM
    val maxBpm = TempoBand.MAX_BPM

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .progressSemantics(bpm.toFloat(), minBpm.toFloat()..maxBpm.toFloat(), steps = maxBpm - minBpm - 1)
            .semantics {
                contentDescription = dialDescription
                // The ring shows no name any more; a screen reader still hears the speed's animal.
                stateDescription = bandName
                setProgress { value ->
                    onBpmChange(value.roundToInt().coerceIn(minBpm, maxBpm))
                    true
                }
            }
            // On the dial itself, not the canvas, so a drag that starts on an animal still turns it.
            .pointerInput(Unit) {
                var lastAngle = 0f
                var fraction = 0f
                detectDragGestures(
                    onDragStart = { offset ->
                        lastAngle = angleOf(offset, size)
                        fraction = dialFraction(currentBpm)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val angle = angleOf(change.position, size)
                        var delta = angle - lastAngle
                        if (delta > 180f) delta -= 360f else if (delta < -180f) delta += 360f
                        lastAngle = angle
                        fraction = (fraction + delta / SWEEP_ANGLE).coerceIn(0f, 1f)
                        val next = bpmAtDialFraction(fraction)
                        if (next != currentBpm) {
                            currentOnBpmChange(next)
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                        }
                    },
                )
            },
    ) {
        val side = minOf(maxWidth, maxHeight)
        Canvas(Modifier.size(side)) { drawRing(colors.tempoBands, colors.track, colors.outline) }

        // The animals on the bands; the current band's rides on the knob instead.
        TempoBand.entries.forEach { each ->
            val centre = pointOnRing(side, START_ANGLE + SWEEP_ANGLE * (each.ordinal + 0.5f) / TempoBand.entries.size)
            val label = stringResource(each.labelRes)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset(centre.x, centre.y)
                    .size(48.dp)
                    .clickable(role = Role.Button, onClickLabel = label) {
                        val target = each.jumpFrom(currentBpm)
                        if (target != currentBpm) currentOnBpmChange(target)
                    }
                    .semantics { contentDescription = label },
            ) {
                if (each != band) Emoji(each.emoji, fontSize = 22, modifier = Modifier.alpha(0.75f).facingRight(each.emoji))
            }
        }

        if (editing) {
            TempoWheel(
                bpm = bpm,
                onBpmChange = onBpmChange,
                onClose = { onEditingChange(false) },
                modifier = Modifier.onGloballyPositioned(onWheelPositioned),
            )
        } else {
            TempoNumber(bpm = bpm, scale = side / DESIGN_SIZE.dp, onClick = { onEditingChange(true) })
        }

        val knob = side * KNOB_SIZE
        val knobCentre = pointOnRing(side, START_ANGLE + SWEEP_ANGLE * dialFraction(bpm))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .offset(knobCentre.x, knobCentre.y)
                .size(knob)
                .chunky(CircleShape, Sun, colors.outline, depth = 5.dp),
        ) {
            Emoji(
                emoji = band.emoji,
                fontSize = (knob.value * 0.55f).roundToInt(),
                modifier = Modifier
                    .facingRight(band.emoji)
                    .hopOnBeat(flash),
            )
        }
    }
}

/**
 * The big tempo figure alone in the ring (the animal on the knob names the speed), sized with the
 * ring ([scale] 1 = 304 dp); a tap opens the wheel.
 */
@Composable
private fun TempoNumber(bpm: Int, scale: Float, onClick: () -> Unit) {
    val tapLabel = stringResource(R.string.tempo_tap)
    val number = MaterialTheme.typography.displayLarge
    Text(
        text = bpm.toString(),
        style = number.copy(fontSize = number.fontSize * scale, lineHeight = number.lineHeight * scale),
        modifier = Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(bounded = false, radius = 72.dp),
            role = Role.Button,
            onClickLabel = tapLabel,
            onClick = onClick,
        ),
    )
}

/** The five coloured bands between an inner and an outer ink line, closed at both ends. */
private fun DrawScope.drawRing(bandColors: List<Color>, centreColor: Color, ink: Color) {
    val side = size.minDimension
    drawCircle(centreColor, radius = side * CENTRE_RADIUS)
    val bandRadius = side * BAND_RADIUS
    val bandSweep = SWEEP_ANGLE / bandColors.size
    bandColors.forEachIndexed { index, color ->
        drawArc(
            color = color,
            startAngle = START_ANGLE + bandSweep * index + BAND_GAP / 2,
            sweepAngle = bandSweep - BAND_GAP,
            useCenter = false,
            topLeft = Offset(center.x - bandRadius, center.y - bandRadius),
            size = Size(bandRadius * 2, bandRadius * 2),
            style = Stroke(side * BAND_WIDTH),
        )
    }
    val line = 2.5.dp.toPx()
    listOf(INNER_EDGE, OUTER_EDGE).forEach { edge ->
        val radius = side * edge
        drawArc(
            color = ink, startAngle = START_ANGLE, sweepAngle = SWEEP_ANGLE, useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius), size = Size(radius * 2, radius * 2),
            style = Stroke(line),
        )
    }
    listOf(START_ANGLE, START_ANGLE + SWEEP_ANGLE).forEach { angle ->
        val radians = Math.toRadians(angle.toDouble())
        val direction = Offset(cos(radians).toFloat(), sin(radians).toFloat())
        drawLine(ink, center + direction * (side * INNER_EDGE), center + direction * (side * OUTER_EDGE), strokeWidth = line)
    }
}

/**
 * Offset from the dial's centre to the point on the band circle at [angle] (0 = 3 o'clock),
 * for a child laid out centred in a dial [side] wide.
 */
private fun pointOnRing(side: Dp, angle: Float): DpOffset {
    val radians = Math.toRadians(angle.toDouble())
    val radius = side * BAND_RADIUS
    return DpOffset(radius * cos(radians).toFloat(), radius * sin(radians).toFloat())
}

/** Angle in degrees of [point] around the centre of a [size]-sized box (0 = 3 o'clock). */
private fun angleOf(point: Offset, size: IntSize): Float {
    val dx = point.x - size.width / 2f
    val dy = point.y - size.height / 2f
    return Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
}
