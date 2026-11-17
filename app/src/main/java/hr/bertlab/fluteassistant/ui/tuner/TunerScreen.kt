// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.tuner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.domain.tuner.TunerMode
import hr.bertlab.fluteassistant.ui.common.FINGERING_ASPECT_RATIO
import hr.bertlab.fluteassistant.ui.common.FingeringDiagram
import hr.bertlab.fluteassistant.ui.common.LocalSideways
import hr.bertlab.fluteassistant.ui.common.MicrophonePermissionCard
import hr.bertlab.fluteassistant.ui.common.ModeChoice
import hr.bertlab.fluteassistant.ui.common.Stage
import hr.bertlab.fluteassistant.ui.common.StagePadding
import hr.bertlab.fluteassistant.ui.common.TabPadding
import hr.bertlab.fluteassistant.ui.common.TabSide
import hr.bertlab.fluteassistant.ui.common.WordFloor
import hr.bertlab.fluteassistant.ui.common.asTextSize
import hr.bertlab.fluteassistant.ui.common.fitOrScroll
import hr.bertlab.fluteassistant.ui.common.labelRes
import hr.bertlab.fluteassistant.ui.common.noteColor
import hr.bertlab.fluteassistant.ui.common.noteLabel
import hr.bertlab.fluteassistant.ui.theme.FluteColors
import hr.bertlab.fluteassistant.ui.theme.FluteTheme

/**
 * Stateful entry point: handles the RECORD_AUDIO permission and starts / stops the microphone
 * with the screen's lifecycle, then renders the stateless [TunerContent].
 */
@Composable
fun TunerScreen(
    state: TunerUiState,
    viewModel: TunerViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        // After a no, the phone says whether it would show its question again; when it would not, only its settings are left.
        val canAskAgain = activity?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.RECORD_AUDIO) } ?: true
        viewModel.onPermissionAnswer(granted, canAskAgain)
    }

    // Check the permission whenever the screen appears; ask the system only once automatically
    // (the card below offers a manual retry).
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        viewModel.onPermissionResult(granted)
        if (!granted && !state.permissionRequested) {
            viewModel.onPermissionRequested()
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Only hold the microphone while this screen is started (visible). Re-checking the
    // permission here picks up a grant made in system Settings while the app was stopped.
    // Telling the view model of a grant it has not heard of (that one, or on the first opening
    // after the app starts) changes this effect's key, so the effect ends and runs again, and
    // that run opens the microphone. Hence the `else`: opening it in this run too opened it
    // twice, the first time only for the key change to close it again (TunerScreenMicrophoneTest).
    LifecycleStartEffect(state.permissionGranted) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted != state.permissionGranted) viewModel.onPermissionResult(granted)
        else if (granted) viewModel.startListening()
        onStopOrDispose { viewModel.stopListening() }
    }

    val actions = remember(viewModel, permissionLauncher, context) {
        TunerActions(
            onTargetNoteChange = viewModel::setTargetNote,
            onModeChange = viewModel::setMode,
            onPlayNote = viewModel::playTargetNote,
            onRequestPermission = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
            onOpenSettings = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    ),
                )
            },
            onOpenGuide = viewModel::openGuide,
            onGuideCard = viewModel::showGuideCard,
            onCloseGuide = viewModel::closeGuide,
        )
    }
    TunerContent(state = state, actions = actions, modifier = modifier)
}

/**
 * Everything the tuner can ask for, as one object, so [TunerContent] takes its state and this.
 * Each action defaults to doing nothing, so a test names only the ones it looks at.
 */
class TunerActions(
    /** ‹ or ›: the note to tune to. */
    val onTargetNoteChange: (Int) -> Unit = {},
    /** One of the two mode pills. */
    val onModeChange: (TunerMode) -> Unit = {},
    /** A tap on the window in Tune mode, in silence: play the chosen note. */
    val onPlayNote: () -> Unit = {},
    /** The microphone card's Yes, and its Settings once the phone will not ask again. */
    val onRequestPermission: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    /** A tap on a line that names a fix on the flute: the guide, on that fix's card. */
    val onOpenGuide: (GuideCard) -> Unit = {},
    /** The guide's ‹ ›, and its ways out. */
    val onGuideCard: (GuideCard) -> Unit = {},
    val onCloseGuide: (GuideExit) -> Unit = {},
)

/**
 * The whole tuner, from the top: the two mode pills; one line of words ([StatusLine]: what to change when there is
 * something, else how the sound is); then a stage, the card, with the pitch window and the fingering. The window's home
 * ring is either the note the child chose to play ([TunerMode.TARGET], with ‹ › to change it) or the nearest note to the
 * one being played ([TunerMode.IDENTIFY], where the arrows' room stays empty so nothing moves), and its bubble floats up
 * or down from it with the child's sound. No numbers beyond the octave in the note name: a child only needs to know
 * whether to go up or down.
 *
 * Since 2026-11-18 (the user: "tunner tune view is packed, all elements are figthing for attention and overlapping ...
 * drop buttons hear how from view completly"; option B of `2026-11-18-tuner-calmer-tune-design.md`) there are no Hear
 * and How? buttons and no second line: in Tune mode a tap on the window plays the chosen note, a line that names a fix
 * on the flute opens the guide on its card, and the flute is in colour only while it helps ([TunerFingering]). The
 * window grows into the room they left. The line stood under the fingering until 2026-11-17 ("move ... label to top so
 * it does not overlap with flute"). Upright only: on its side the words stand beside the window (`TunerSideways`).
 */
@Composable
fun TunerContent(state: TunerUiState, actions: TunerActions, modifier: Modifier = Modifier) {
    // The guide to tuning the flute takes the tab's place; it branches on the turn itself, so this one
    // call keeps its card when the phone turns.
    val guideCard = state.guideCard
    if (state.permissionGranted && guideCard != null) {
        TuningGuide(card = guideCard, onCard = actions.onGuideCard, onClose = actions.onCloseGuide, modifier = modifier)
        return
    }
    // A phone on its side has its own layout ([TunerSideways]), and its own place for the microphone
    // card, which brings its own page there.
    if (LocalSideways.current) {
        if (state.permissionGranted) {
            TunerSideways(state, actions, modifier)
        } else {
            MicrophonePermissionCard(
                onRequestPermission = actions.onRequestPermission,
                onOpenSettings = actions.onOpenSettings.takeIf { state.micBlocked },
                modifier = modifier.fillMaxSize(),
            )
        }
        return
    }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // The fingering fills the stage's inside and is as tall as that makes it, so the least height follows the width.
        val minHeight = TunerMinHeightLessFingering + (maxWidth - (TabSide + StagePadding) * 2) / FINGERING_ASPECT_RATIO
        Column(
            modifier = Modifier
                .fitOrScroll(fits = maxHeight >= minHeight, minHeight = minHeight)
                .padding(TabPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!state.permissionGranted) {
                MicrophonePermissionCard(
                    onRequestPermission = actions.onRequestPermission,
                    onOpenSettings = actions.onOpenSettings.takeIf { state.micBlocked },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
                return@Column
            }

            TunerModeChoice(mode = state.mode, onSelect = actions.onModeChange)

            // The one line of words, under the pills in both modes, so it keeps its place when the mode changes.
            StatusLine(state.line, actions.onOpenGuide)

            Stage(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag(TUNER_STAGE_TAG),
                verticalArrangement = StageArrangement,
            ) {
                // The window takes what height the flute leaves and shrinks to it on a short screen; in Which? the arrows'
                // room stays, so it keeps its size and place when the mode changes.
                TargetNote(
                    targetMidi = state.targetMidi,
                    onTargetChange = actions.onTargetNoteChange,
                    arrows = state.mode == TunerMode.TARGET,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                ) { windowModifier ->
                    TunerWindow(state, actions.onPlayNote, windowModifier, maxScale = WINDOW_MAX_SCALE)
                }

                // Which keys to press: for the ring's note, chosen or heard, in colour only while it helps.
                TunerFingering(state.homeMidi, state.fingeringLit)
            }
        }
    }
}

/**
 * The pitch window shrinks to fit a small phone; only below a least height (split screen, a tiny
 * phone) does the tab scroll instead: this much and the fingering's own height, under which the window,
 * at its least (120 dp), would stand on the flute. The fingering fills the stage's inside, the tab less [TabSide] and
 * [StagePadding] either side, and its height follows that width (340:66), so the least height is 395 on a 412 dp
 * phone, where the flute is 66 dp tall, 385 at 360 and 430 in a pane 590 dp wide (measured: the window keeps about
 * 124 dp there).
 */
private val TunerMinHeightLessFingering = 329.dp

/**
 * The pressed keys' grey while the right note sounds: halfway from an open key's fill to the muted words, so the fingering
 * stays readable and quiet by day and at night (`border`, the first grey, was nearly an open key's fill at night: the
 * final review of 2026-11-18).
 */
private fun QuietKeys(colors: FluteColors): Color = lerp(colors.raised, colors.mutedText, 0.5f)

/**
 * The upright card's column: 16 dp between the window and the flute, and the card's free height round the two, not
 * between them. One value, as an arrangement built while the tab draws is never equal to the last one and the card
 * would be measured again on every frame the microphone hears.
 */
private val StageArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)

/** The upright tuner's card, for the tests. */
internal const val TUNER_STAGE_TAG = "tuner_stage"

internal val TunerMode.emoji: String
    get() = when (this) {
        TunerMode.IDENTIFY -> "\uD83D\uDD0D" // magnifying glass
        TunerMode.TARGET -> "\uD83C\uDFAF" // direct hit
    }

/**
 * Two chunky pills on top of the tuner, an emoji and a word each: "Which?" names whatever
 * is played, "Tune" measures against the note the child chose. The chosen one is yellow.
 */
@Composable
private fun TunerModeChoice(mode: TunerMode, onSelect: (TunerMode) -> Unit) {
    ModeChoice(
        entries = TunerMode.entries,
        selected = mode,
        onSelect = onSelect,
        groupLabel = stringResource(R.string.tuner_mode),
        emojiOf = { it.emoji },
        labelResOf = { it.labelRes() },
    )
}

/**
 * The tuner's one line of words, written once for both layouts (since 2026-11-18, when the advice line under the
 * flute joined it: "all elements are figthing for attention"). What it says is [TunerUiState.line], and this only
 * turns it into words: what to change when there is something (roll the flute, the head joint, air, fingers, come
 * closer, a clear note), else how the sound is. A failed microphone takes the line, in the error colour. It bounces
 * in when the note comes into tune, and shrinks rather than wraps when the words are long, to a floor in dp
 * (`WordFloor`).
 *
 * A line that names a fix on the flute (`TunerAdvice.guideCard`: the head joint, rolling) is a button that opens the
 * guide on that card ([onOpenGuide]), the guide's one way in since the How? button went. No ripple: the guide opening
 * answers the tap.
 *
 * Upright is the default. A phone on its side draws it bigger ([maxFont], in a box [height] tall) and may break it
 * between words ([maxLines]) where the room beside the window is narrow. Such a caller gives a [style] with a
 * relative line height, so that the lines scale with the font and the largest that fits is found by the box.
 */
@Composable
internal fun StatusLine(
    line: TunerLine,
    onOpenGuide: (GuideCard) -> Unit,
    modifier: Modifier = Modifier,
    maxFont: TextUnit = 32.sp,
    height: Dp = 40.dp,
    maxLines: Int = 1,
    style: TextStyle = MaterialTheme.typography.headlineMedium,
) {
    val inTune = line == TunerLine.InTune
    val scale = remember { Animatable(1f) }
    LaunchedEffect(inTune) {
        if (inTune) {
            scale.snapTo(1.3f)
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        }
    }
    val guideCard = (line as? TunerLine.Advice)?.advice?.guideCard
    val howToTune = stringResource(R.string.tune_line_how)
    Text(
        text = line.words(),
        color = if (line == TunerLine.MicFailed) MaterialTheme.colorScheme.error else Color.Unspecified,
        style = style,
        textAlign = TextAlign.Center,
        maxLines = maxLines,
        autoSize = TextAutoSize.StepBased(minFontSize = WordFloor.asTextSize(), maxFontSize = maxFont),
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .then(
                if (guideCard != null) {
                    Modifier.clickable(
                        interactionSource = null,
                        indication = null,
                        onClickLabel = howToTune,
                        role = Role.Button,
                    ) { onOpenGuide(guideCard) }
                } else {
                    Modifier
                },
            )
            // Two lines sit in the middle of their box, as one line would at the top of it.
            .then(if (maxLines > 1) Modifier.wrapContentHeight(Alignment.CenterVertically) else Modifier)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            },
    )
}

/** The line's words in the child's language. */
@Composable
private fun TunerLine.words(): String = when (this) {
    TunerLine.MicFailed -> stringResource(R.string.mic_failed)
    is TunerLine.Advice -> advice.labelRes()?.let { stringResource(it) }.orEmpty()
    TunerLine.Listen -> stringResource(R.string.listen_status)
    TunerLine.InTune -> stringResource(R.string.in_tune)
    is TunerLine.PlayNote -> stringResource(R.string.play_note_prompt, noteLabel(midi))
    TunerLine.PlayAnyNote -> stringResource(R.string.play_a_note)
}

/**
 * The pitch window for [state], written once for both layouts, which give it its size ([modifier], [maxScale]): what
 * a screen reader hears of it, and in Tune mode, in silence ([TunerUiState.tapPlaysNote]), the window as the Hear
 * button, where a tap plays the chosen note ([onPlayNote]).
 */
@Composable
internal fun TunerWindow(state: TunerUiState, onPlayNote: () -> Unit, modifier: Modifier = Modifier, maxScale: Float = 1f) {
    PitchWindow(
        homeMidi = state.homeMidi,
        heardMidi = state.heardMidi,
        cents = state.gaugeCents,
        description = pitchWindowDescription(state),
        modifier = modifier,
        maxScale = maxScale,
        onTap = onPlayNote.takeIf { state.tapPlaysNote },
        tapLabel = stringResource(R.string.hear_note),
    )
}

/**
 * Which keys to press for the ring's note ([midi], chosen or heard; none in Which? silence), across the room it is
 * given: written once for both layouts, which pass their own [modifier] (the sideways one tags it for the tests).
 * The pressed keys are in the note's colour while it helps ([lit], `TunerUiState.fingeringLit`: before a sound, and
 * on another note or octave) and grey while the right note sounds, so the eyes go to the window (since 2026-11-18,
 * option B of the calmer Tune view; the grey is [QuietKeys]). The colour fades; nothing moves.
 */
@Composable
internal fun TunerFingering(midi: Int?, lit: Boolean, modifier: Modifier = Modifier) {
    val colors = FluteTheme.colors
    val color by animateColorAsState(
        targetValue = when {
            midi == null -> colors.raised
            lit -> noteColor(midi)
            else -> QuietKeys(colors)
        },
        label = "fingering",
    )
    FingeringDiagram(midi, modifier.fillMaxWidth(), color)
}

/**
 * What a screen reader says for the pitch window: the home note and nothing heard yet, or
 * where the child's sound is against it and which way to go.
 */
@Composable
private fun pitchWindowDescription(state: TunerUiState): String {
    val home = state.homeMidi ?: return stringResource(R.string.pitch_listening)
    val homeName = noteLabel(home)
    val heard = state.heardMidi
    val low = state.status == TuningStatus.LOW
    return when {
        heard == null -> stringResource(R.string.pitch_home, homeName)
        state.status == TuningStatus.IN_TUNE -> stringResource(R.string.pitch_in_tune, homeName)
        heard != home -> stringResource(if (low) R.string.pitch_other_low else R.string.pitch_other_high, noteLabel(heard), homeName)
        else -> stringResource(if (low) R.string.pitch_low else R.string.pitch_high, homeName)
    }
}
