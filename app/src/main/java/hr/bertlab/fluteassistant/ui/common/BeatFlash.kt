package hr.bertlab.fluteassistant.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One beat, one hop: how long the flash takes to fade from 1 (just hit) to 0, shared by the
 * ring's knob, the flute's knob and the tab bar so every animal moves in step.
 */
const val FLASH_DURATION_MS = 220

/** How far an animal hops on a beat, at the flash's peak. */
val HopDistance: Dp = 8.dp

/**
 * The flash that makes things pop on the beat: it snaps to 1 on every new [beatPulse] while
 * [active] and fades to 0 over [FLASH_DURATION_MS]. It comes back as a lambda so only the layer
 * phase that reads it ([hopOnBeat], a `graphicsLayer`) re-reads it, with no per-frame
 * recomposition.
 */
@Composable
fun rememberBeatFlash(beatPulse: Long, active: Boolean): () -> Float {
    val flash = remember { Animatable(0f) }
    val flashValue: () -> Float = remember(flash) { { flash.value } }
    LaunchedEffect(beatPulse) {
        if (beatPulse > 0L && active) {
            flash.snapTo(1f)
            flash.animateTo(0f, tween(FLASH_DURATION_MS, easing = LinearOutSlowInEasing))
        }
    }
    return flashValue
}

/** Lifts this by [HopDistance] at the flash's peak and lets it fall back as [flash] fades. */
fun Modifier.hopOnBeat(flash: () -> Float): Modifier =
    graphicsLayer { translationY = -HopDistance.toPx() * flash() }
