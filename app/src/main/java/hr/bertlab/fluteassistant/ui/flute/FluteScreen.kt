package hr.bertlab.fluteassistant.ui.flute

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.domain.tuner.FluteFingerings
import hr.bertlab.fluteassistant.ui.common.AppIcons
import hr.bertlab.fluteassistant.ui.common.BigActionButton
import hr.bertlab.fluteassistant.ui.common.DISABLED_ALPHA
import hr.bertlab.fluteassistant.ui.common.Emoji
import hr.bertlab.fluteassistant.ui.common.NoteBubble
import hr.bertlab.fluteassistant.ui.common.OutlineWidth
import hr.bertlab.fluteassistant.ui.common.Stage
import hr.bertlab.fluteassistant.ui.common.chunky
import hr.bertlab.fluteassistant.ui.common.dashedBorder
import hr.bertlab.fluteassistant.ui.common.noteColor
import hr.bertlab.fluteassistant.ui.common.noteLabel
import hr.bertlab.fluteassistant.ui.theme.ActionGo
import hr.bertlab.fluteassistant.ui.theme.ActionStop
import hr.bertlab.fluteassistant.ui.theme.FluteTheme
import hr.bertlab.fluteassistant.ui.tuner.FINGERING_ASPECT_RATIO
import hr.bertlab.fluteassistant.ui.tuner.FingeringDiagram
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

private val BubbleSize = 96.dp
private val HoldDot = 16.dp
private val HoldBeatsGap = 10.dp
private val PlayRowHeight = 84.dp
private val PlayShadow = 8.dp

/**
 * Everything below this height cannot hold the keys at their minimum: the stage (bubble,
 * hold dots, flute, tune line), three key rows and the Play row, plus the gaps between them.
 */
private val FitsHeight = 20.dp + (38.dp + BubbleSize + 6.dp + HoldBeatsGap + HoldDot + 14.dp + 48.dp + 14.dp + TuneLineHeight) +
    14.dp + FluteKeysMinHeight + 14.dp + PlayRowHeight + PlayShadow + 16.dp


/**
 * The whole flute tab, top to bottom: a stage with the note shown (held, last played, or
 * playing now) in its letter's colour, 💤 for a rest and 😮‍💨 for a breath, a dashed bubble
 * with a pointing finger before the first tap, and under it, while a key is held, [HoldBeats]
 * filling one dot per beat; the tuner's flute drawing with that note's fingering (none for a
 * rest or a breath) and the scrolling [TuneLine]; then the octave rows of [FluteKeys], with the
 * breath and the rest key at the end of the top row, taking the height left, and the Play row:
 * one big pill ("Play", green; "Stop", red, while the tune plays) and a round erase
 * button, both faded while the tune is empty. On an ordinary phone held upright nothing
 * scrolls; when the height is short of [FitsHeight] the keys keep their minimum and the
 * whole screen scrolls.
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
            val shown = state.shownMidi
            Stage(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(HoldBeatsGap),
                ) {
                    when (state.shown) {
                        Shown.Rest -> SignBubble(SLEEP_EMOJI, stringResource(R.string.flute_rest))
                        Shown.Breath -> SignBubble(BREATH_EMOJI, stringResource(R.string.flute_breath))
                        is Shown.Note, null -> NoteBubble(
                            midi = shown,
                            size = BubbleSize,
                            fontSize = 48.sp,
                            placeholder = POINTING_EMOJI,
                        )
                    }
                    HoldBeats(
                        beats = state.holdBeats,
                        total = state.maxHoldBeats,
                        color = shown?.let(::noteColor) ?: FluteTheme.colors.mutedText, // a rest's dots: the ink of its symbol
                    )
                }
                FingeringDiagram(
                    keys = shown?.let(FluteFingerings::keysFor),
                    noteLabel = shown?.let { noteLabel(it) },
                    color = shown?.let(::noteColor) ?: FluteTheme.colors.raised,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(FINGERING_ASPECT_RATIO),
                )
                TuneLine(tune = state.tune, litIndex = state.litIndex, timeSignature = state.timeSignature)
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

/** The bubble for a rest or a breath: [emoji] on the quiet track colour, outlined and raised like a note's. */
@Composable
private fun SignBubble(emoji: String, description: String) {
    val colors = FluteTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(BubbleSize)
            .chunky(CircleShape, colors.track, colors.outline, 6.dp)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Emoji(emoji, fontSize = 44)
    }
}

/**
 * [total] dots under the note while a key is held (a whole note's metronome clicks: four in
 * 4/4, eight in 6/8), one more filled in the note's colour for each click its note has grown to
 * ([FluteUiState.holdBeats]: the same length its pill shows). When no key is held they are laid
 * out but invisible, so the keys never move under a finger.
 */
@Composable
private fun HoldBeats(beats: Int?, total: Int, color: Color) {
    val colors = FluteTheme.colors
    val description = beats?.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.flute_held_beats, it, it) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .alpha(if (beats == null) 0f else 1f)
            .clearAndSetSemantics { if (description != null) contentDescription = description },
    ) {
        repeat(total) { index ->
            val filled = beats != null && index < beats
            Box(
                modifier = Modifier
                    .size(HoldDot)
                    .clip(CircleShape)
                    .then(
                        if (filled) {
                            Modifier
                                .background(color)
                                .border(OutlineWidth, colors.outline, CircleShape)
                        } else {
                            Modifier.dashedBorder(OutlineWidth, colors.border, CircleShape)
                        },
                    ),
            )
        }
    }
}

private const val POINTING_EMOJI = "\uD83D\uDC46" // backhand index pointing up
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
