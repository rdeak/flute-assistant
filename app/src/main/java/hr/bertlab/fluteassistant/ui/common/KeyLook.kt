// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.domain.tuner.Accidental
import hr.bertlab.fluteassistant.ui.theme.FluteTheme
import hr.bertlab.fluteassistant.ui.theme.InkOnFill
import hr.bertlab.fluteassistant.ui.theme.Sun

/*
 * A key a child plays or picks a note with: the flute tab's keys and, since 2026-11-17, the Fingers tab's. Its look
 * (the shape, the depth, the ring, the ♯♭ picture) and the key itself ([ChunkyKey], and the ones both tabs have:
 * [TapKey], [SharpFlatKey], a shrinking [KeyName]) are here in `ui/common`, so that two tabs share them without
 * importing each other and a key is built in one place.
 */

/**
 * A key's numbers in dp, the flute's and the Fingers tab's, written once: plain for the geometry that the unit tests
 * pin (`SidewaysGeometry`, `TabletGeometry`), and as [Dp] below for the screens. The height is a sure target for a
 * small finger and the canvas's 58 at every screen height (the user, 2026-11-02: keys as on the canvas; they used to
 * stretch to 72 dp to fill the space); the depth is the key's solid shadow ([chunky]); the gap is the room between
 * two keys side by side.
 */
internal const val KEY_HEIGHT = 58f
internal const val KEY_DEPTH = 5f
internal const val KEY_GAP = 6f

internal val KeyHeight: Dp = KEY_HEIGHT.dp
internal val KeyDepth: Dp = KEY_DEPTH.dp
internal val KeyGap: Dp = KEY_GAP.dp

private val KeyCorner = 21.dp

/** A key's rounded shape. */
internal val KeyShape: Shape = RoundedCornerShape(KeyCorner)

/** The ring's line, how far its middle runs outside the key's outline, and how much lower the whole ring sits, as a sunk key does. */
private val RingLine = 5.dp
private val RingGap = 4.5.dp
private val RingDrop = 3.dp

/** How far the ring stands over a key's top (4 dp): the room a row of keys that scrolls keeps above it. */
internal val KeyRingReach: Dp = RingGap - RingDrop + RingLine / 2

/** The yellow ring around a pressed key, a little outside its outline. */
internal fun DrawScope.drawKeyRing() {
    val gap = RingGap.toPx()
    drawRoundRect(
        color = Sun,
        topLeft = Offset(-gap, -gap + RingDrop.toPx()),
        size = Size(size.width + gap * 2, size.height + gap * 2),
        cornerRadius = CornerRadius(KeyCorner.toPx() + gap),
        style = Stroke(RingLine.toPx()),
    )
}

/** The ♯♭ key's picture: the staff's own sharp and flat side by side ([drawSign]), 28 x 26 dp at [scale] 1. */
@Composable
internal fun SharpFlatPicture(color: Color, scale: Float, modifier: Modifier = Modifier) {
    Canvas(modifier.size((SHARP_FLAT_WIDTH * scale).dp, (SHARP_FLAT_HEIGHT * scale).dp)) {
        scale(scale * density, pivot = Offset.Zero) {
            drawSign(Accidental.SHARP, cx = 7f, y = 13f, color = color)
            drawSign(Accidental.FLAT, cx = 21f, y = 17f, color = color)
        }
    }
}

private const val SHARP_FLAT_WIDTH = 28f
private const val SHARP_FLAT_HEIGHT = 26f

/** The least a key's name shrinks to, in dp so that it stays this small at the largest text (less than `WordFloor`, for a key's room). */
private val KeyNameFloor = 10.dp

/**
 * A key: [content] on a [color] face in the keys' shape, named [label] for a screen reader, which presses it through
 * [onActivate]. It is sunk in the yellow ring while [down]. While not [enabled] it refuses [onActivate], is heard as
 * disabled and is faded, unless it is down: a key that still sounds or waits is not. [selected] is
 * said only when given, so a screen reader does not hear "not selected" on every other key. A button and never a radio
 * button: a selected radio button cannot be pressed again with a screen reader, and a waiting or chosen key must be.
 *
 * It draws and speaks and has no gesture of its own: each key's finger is different (a note is held, a tool acts as
 * the finger goes down, the sponge repeats), and comes with [modifier], outside the key's own layers.
 */
@Composable
internal fun ChunkyKey(
    label: String,
    color: Color,
    down: Boolean,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean? = null,
    content: @Composable () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fadedUnless(enabled || down, KeyDepth)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = label
                if (!enabled) disabled()
                if (selected != null) this.selected = selected
                onClick {
                    if (enabled) onActivate()
                    enabled
                }
            }
            .drawBehind { if (down) drawKeyRing() }
            .chunky(KeyShape, color, FluteTheme.colors.outline, KeyDepth, pressed = down),
    ) {
        content()
    }
}

/**
 * A key that acts as the finger goes down ([onTap]) and stays down in its yellow ring while [down], where a screen
 * reader hears it selected: the flute's ⌒ key and the ♯♭ key of both tabs while they wait for the next note key, and
 * a letter key of the Fingers tab while its note is the chosen one. A plain key unless given a [color]. Faded and
 * inert while not [enabled], unless it is down: a second tap must still let it up.
 */
@Composable
internal fun TapKey(
    label: String,
    down: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = FluteTheme.colors.raised,
    content: @Composable () -> Unit,
) {
    val currentOnTap by rememberUpdatedState(onTap)
    val active = enabled || down
    ChunkyKey(
        label = label,
        color = color,
        down = down,
        onActivate = { currentOnTap() },
        enabled = active,
        // Selected while it is down; nothing said otherwise, so a screen reader does not hear "not selected" on every visit.
        selected = if (down) true else null,
        modifier = modifier.then(
            if (active) Modifier.pointerInput(Unit) { detectTapGestures(onPress = { currentOnTap() }) } else Modifier,
        ),
        content = content,
    )
}

/**
 * The ♯♭ key (since 2026-11-17), the flute's and the Fingers tab's: a plain key with a sharp and a flat drawn as the
 * staff draws them ([SharpFlatPicture], at [pictureScale]: the Fingers tab's keys are narrower). A finger going down
 * calls [onSign], which makes the next note key take its letter's sharp or flat, or drops a sign waiting; while one
 * waits the key stays down in its yellow ring and a screen reader hears it selected.
 */
@Composable
internal fun SharpFlatKey(waiting: Boolean, onSign: () -> Unit, modifier: Modifier = Modifier, pictureScale: Float = 1.25f) {
    val ink = FluteTheme.colors.text
    TapKey(stringResource(R.string.flute_sign), down = waiting, onTap = onSign, modifier = modifier) {
        SharpFlatPicture(ink, pictureScale)
    }
}

/**
 * A key's name where it may be too long for the key ("gis¹", "C♯⁴", "Cis"): it shrinks to fit on one line, from
 * [maxSize] down to a floor in dp, as one in sp doubles at the largest text and was still too wide there
 * (`asTextSize`; the final review of 2026-11-17: a fixed size was cut at the largest text).
 */
@Composable
internal fun KeyName(name: AnnotatedString, maxSize: TextUnit) {
    Text(
        name,
        style = MaterialTheme.typography.titleLarge,
        color = InkOnFill,
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = KeyNameFloor.asTextSize(), maxFontSize = maxSize),
        modifier = Modifier.padding(horizontal = OutlineWidth),
    )
}
