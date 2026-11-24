// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * The scale of a tab's line of words whose good news bounces in: when [on] turns true ("Perfect!" on the tuner,
 * "That's it!" on the notes tab) it jumps to 1.3 and springs back to 1. Read through [bounce], in the layer phase, so
 * the spring redraws the line and never recomposes it.
 */
@Composable
fun rememberBounce(on: Boolean): () -> Float {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(on) {
        if (on) {
            scale.snapTo(1.3f)
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        }
    }
    return { scale.value }
}

/** Draws this at [scale] ([rememberBounce]). */
fun Modifier.bounce(scale: () -> Float): Modifier = graphicsLayer {
    val now = scale()
    scaleX = now
    scaleY = now
}
