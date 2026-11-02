package hr.bertlab.fluteassistant.ui.flute

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.ui.common.AppIcons
import hr.bertlab.fluteassistant.ui.common.BigActionButton
import hr.bertlab.fluteassistant.ui.common.DISABLED_ALPHA
import hr.bertlab.fluteassistant.ui.common.Emoji
import hr.bertlab.fluteassistant.ui.common.Stage
import hr.bertlab.fluteassistant.ui.common.chunky
import hr.bertlab.fluteassistant.ui.theme.ActionGo
import hr.bertlab.fluteassistant.ui.theme.ActionStop
import hr.bertlab.fluteassistant.ui.theme.FluteTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Stateful entry point: opens the flute's sound while the screen is started (visible) and
 * closes it when it stops, the way the tuner holds the microphone, then renders the stateless
 * [FluteContent].
 */
@Composable
fun FluteScreen(
    state: FluteUiState,
    viewModel: FluteViewModel,
    modifier: Modifier = Modifier,
) {
    LifecycleStartEffect(Unit) {
        viewModel.enterTab()
        onStopOrDispose { viewModel.leaveTab() }
    }
    FluteContent(
        state = state,
        onKeyPressed = viewModel::onKeyPressed,
        onKeyReleased = viewModel::onKeyReleased,
        onKeyActivated = viewModel::onKeyActivated,
        onRestPressed = viewModel::onRestPressed,
        onRestReleased = viewModel::onRestReleased,
        onRestActivated = viewModel::onRestActivated,
        onBreath = viewModel::onBreath,
        onTogglePlayback = viewModel::togglePlayback,
        onEraseLast = viewModel::eraseLast,
        modifier = modifier,
    )
}

private val PlayRowHeight = 84.dp
private val PlayShadow = 8.dp

/**
 * Everything below this height cannot hold the keys: the stage (the staff with the names under
 * it), three key rows and the Play row, plus the gaps between them.
 */
private val FitsHeight = 20.dp + (38.dp + TuneStaffHeight) +
    14.dp + FluteKeysMinHeight + 14.dp + PlayRowHeight + PlayShadow + 16.dp

/**
 * The whole flute tab, top to bottom: a stage with only two things, the child's tune on a
 * staff ([TuneStaff]: the playing step, or the held one, ringed in yellow, the held one growing
 * in a yellow band over its notes and its names) with every note's name in a pill right under it,
 * in the same room, so notes and names line up and scroll together; then the octave rows of [FluteKeys], with the breath and the rest key at the end of the
 * top row, taking the height left, and the Play row: one big pill ("Play", green; "Stop", red,
 * while the tune plays) and a round erase button, both faded while the tune is empty. On an
 * ordinary phone held upright nothing scrolls; when the height is short of [FitsHeight] the keys
 * keep their minimum and the whole screen scrolls.
 */
@Composable
fun FluteContent(
    state: FluteUiState,
    onKeyPressed: (Int) -> Unit,
    onKeyReleased: (Int) -> Unit,
    onKeyActivated: (Int) -> Unit,
    onRestPressed: () -> Unit,
    onRestReleased: () -> Unit,
    onRestActivated: () -> Unit,
    onBreath: () -> Unit,
    onTogglePlayback: () -> Unit,
    onEraseLast: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val fits = maxHeight >= FitsHeight
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (fits) Modifier else Modifier.verticalScroll(rememberScrollState()))
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Stage(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                TuneStaff(
                    tune = state.tune,
                    litIndex = state.litIndex,
                    heldIndex = state.heldIndex,
                    timeSignature = state.timeSignature,
                )
            }
            FluteKeys(
                litMidi = state.litMidi,
                restLit = state.restLit,
                onPress = onKeyPressed,
                onRelease = onKeyReleased,
                onActivate = onKeyActivated,
                onRestPress = onRestPressed,
                onRestRelease = onRestReleased,
                onRestActivate = onRestActivated,
                onBreath = onBreath,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (fits) Modifier.weight(1f) else Modifier.height(FluteKeysMinHeight)),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = PlayShadow),
            ) {
                BigActionButton(
                    icon = if (state.isPlaying) AppIcons.Stop else AppIcons.Play,
                    text = stringResource(if (state.isPlaying) R.string.stop else R.string.flute_play),
                    color = if (state.isPlaying) ActionStop else ActionGo,
                    onClick = onTogglePlayback,
                    enabled = !state.isEmpty,
                    modifier = Modifier.weight(1f),
                )
                EraseButton(enabled = !state.isEmpty, onErase = onEraseLast, modifier = Modifier.size(PlayRowHeight))
            }
        }
    }
}

private const val SPONGE_EMOJI = "\uD83E\uDDFD" // sponge

/** After this long held, the erase button starts repeating, then every [EraseRepeatMs]: a phone keyboard's backspace. */
private const val EraseRepeatDelayMs = 400L
private const val EraseRepeatMs = 120L

/**
 * One round chunky button with a sponge that erases the last note on a tap and keeps erasing
 * while held, so a long line is cleared by holding; it sinks while a finger is on it, like a
 * key. Faded and inert while [enabled] is false.
 */
@Composable
private fun EraseButton(
    enabled: Boolean,
    onErase: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.flute_erase)
    val scope = rememberCoroutineScope()
    val currentOnErase by rememberUpdatedState(onErase)
    var pressed by remember { mutableStateOf(false) }
    val colors = FluteTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .semantics {
                role = Role.Button
                contentDescription = description
                if (!enabled) disabled()
                onClick {
                    if (enabled) currentOnErase()
                    enabled
                }
            }
            .chunky(CircleShape, colors.raised, colors.outline, PlayShadow, pressed)
            .then(
                if (enabled) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                pressed = true
                                currentOnErase()
                                val repeat = scope.launch {
                                    delay(EraseRepeatDelayMs)
                                    while (isActive) {
                                        currentOnErase()
                                        delay(EraseRepeatMs)
                                    }
                                }
                                try {
                                    tryAwaitRelease()
                                } finally {
                                    pressed = false
                                    repeat.cancel()
                                }
                            },
                        )
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        Emoji(SPONGE_EMOJI, fontSize = 34)
    }
}
