// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LayerOutsets
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.bertlab.fluteassistant.ui.theme.ActionGo
import hr.bertlab.fluteassistant.ui.theme.ActionStop
import hr.bertlab.fluteassistant.ui.theme.FluteTheme
import hr.bertlab.fluteassistant.ui.theme.InkOnFill
import hr.bertlab.fluteassistant.ui.theme.OnAction

/** Width of the ink outline around everything that can be pressed. */
val OutlineWidth = 3.dp

/** How far a pressed thing sinks onto its shadow. */
internal val PressDepth = 3.dp

/** Opacity of something that cannot be pressed right now. */
const val DISABLED_ALPHA = 0.45f

/**
 * Fades this to [DISABLED_ALPHA] unless [enabled], with the [chunky] shadow [depth] below it. A faded
 * layer is drawn through an offscreen buffer that stops at its bounds, so the buffer is made that
 * much taller; `Modifier.alpha` would cut the shadow off flat along the bottom edge.
 */
internal fun Modifier.fadedUnless(enabled: Boolean, depth: Dp): Modifier =
    if (enabled) {
        this
    } else {
        graphicsLayer {
            alpha = DISABLED_ALPHA
            outsets = LayerOutsets(bottom = depth)
        }
    }

/**
 * The app's one pressable look: a [color] fill in [shape] with a thick [outline] and a solid
 * shadow [depth] below it, in the outline colour. While [pressed] it sinks onto its shadow.
 * Only draws; the caller adds the gesture. The shadow is drawn outside the bounds, so leave
 * [depth] of room below.
 */
fun Modifier.chunky(
    shape: Shape,
    color: Color,
    outline: Color,
    depth: Dp,
    pressed: Boolean = false,
): Modifier = this
    .drawBehind {
        if (depth > 0.dp) {
            val shadow = shape.createOutline(size, layoutDirection, this)
            translate(top = depth.toPx()) { drawOutline(shadow, outline) }
        }
    }
    .graphicsLayer { translationY = if (pressed) minOf(PressDepth, depth).toPx() else 0f }
    .clip(shape)
    .background(color)
    .border(OutlineWidth, outline, shape)

/**
 * A [chunky] button. Plain by default; pass [selected] to make it one option of a group
 * (a radio button or a tab) or [checked] to make it an on/off choice, so assistive
 * technology hears the state. [color] fills it; text on a bright fill is ink, on the plain
 * raised colour it is the theme's text colour. A disabled button fades to [DISABLED_ALPHA] unless
 * [fade] is false, for a button the caller fades together with what is drawn around it (the book
 * button's page edges), so the whole fades once, as one layer.
 */
@Composable
fun ChunkyButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    color: Color = FluteTheme.colors.raised,
    depth: Dp = 4.dp,
    enabled: Boolean = true,
    selected: Boolean? = null,
    checked: Boolean? = null,
    role: Role = when {
        selected != null -> Role.RadioButton
        checked != null -> Role.Checkbox
        else -> Role.Button
    },
    interactionSource: MutableInteractionSource? = null,
    fade: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val colors = FluteTheme.colors
    val contentColor = if (color == colors.raised || color == colors.card || color == Color.Transparent) colors.text else InkOnFill
    val gesture = when {
        selected != null -> Modifier.selectable(selected, source, null, enabled, role, onClick)
        checked != null -> Modifier.toggleable(checked, source, null, enabled, role) { onClick() }
        else -> Modifier.clickable(source, null, enabled, null, role, onClick)
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fadedUnless(enabled || !fade, depth)
            .chunky(shape, color, colors.outline, depth, pressed && enabled)
            .then(gesture),
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor, content = { content() })
    }
}

/**
 * The big full-width action at the bottom of a screen: a drawn icon and one word, both white,
 * on a deep pill ([ActionGo] for Play, OK, Again and Yes, [ActionStop] for Stop), so the most
 * important button on each screen has the strongest contrast. Two such pills do not fit side by
 * side on a phone with the word beside the icon; [stacked] puts the icon over the word, as the
 * tab bar's buttons stack an emoji over a word, at the same height, the word shrinking a step
 * or two at a narrow width or large text rather than being cut short (the flute's Play and
 * Your turn).
 */
@Composable
fun BigActionButton(
    icon: ImageVector,
    text: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    stacked: Boolean = false,
) {
    ChunkyButton(
        onClick = onClick,
        color = color,
        shape = CircleShape,
        depth = 8.dp,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp),
    ) {
        if (stacked) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Icon(icon, contentDescription = null, tint = OnAction, modifier = Modifier.size(28.dp))
                Text(
                    text,
                    style = MaterialTheme.typography.titleMedium,
                    color = OnAction,
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 18.sp),
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(icon, contentDescription = null, tint = OnAction, modifier = Modifier.size(32.dp))
                Text(
                    text,
                    style = MaterialTheme.typography.headlineMedium,
                    color = OnAction,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The rounded white (navy in dark mode) card each tab draws its main content on, with a
 * thin border: it is not pressable, so it has no ink outline or shadow.
 */
@Composable
fun Stage(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.SpaceBetween,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = FluteTheme.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = verticalArrangement,
        modifier = modifier
            .clip(StageShape)
            .background(colors.card)
            .border(OutlineWidth, colors.border, StageShape)
            .padding(16.dp),
        content = content,
    )
}

val StageShape = RoundedCornerShape(32.dp)

/**
 * An emoji used as an icon. Decorative: the words next to it, or the content description of
 * the control it sits in, carry the meaning.
 */
@Composable
fun Emoji(emoji: String, fontSize: Int, modifier: Modifier = Modifier) {
    Text(
        text = emoji,
        style = MaterialTheme.typography.titleLarge.copy(fontSize = fontSize.sp, lineHeight = (fontSize * 1.2f).sp),
        modifier = modifier.clearAndSetSemantics { },
    )
}
