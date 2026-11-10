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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import hr.bertlab.fluteassistant.domain.tuner.FluteFingerings
import hr.bertlab.fluteassistant.domain.tuner.TunerMode
import hr.bertlab.fluteassistant.ui.common.AdviceLine
import hr.bertlab.fluteassistant.ui.common.FINGERING_ASPECT_RATIO
import hr.bertlab.fluteassistant.ui.common.FingeringDiagram
import hr.bertlab.fluteassistant.ui.common.AppIcons
import hr.bertlab.fluteassistant.ui.common.LocalSideways
import hr.bertlab.fluteassistant.ui.common.MicrophonePermissionCard
import hr.bertlab.fluteassistant.ui.common.ModeChoice
import hr.bertlab.fluteassistant.ui.common.Stage
import hr.bertlab.fluteassistant.ui.common.chunky
import hr.bertlab.fluteassistant.ui.common.fitOrScroll
import hr.bertlab.fluteassistant.ui.common.labelRes
import hr.bertlab.fluteassistant.ui.common.noteColor
import hr.bertlab.fluteassistant.ui.common.noteLabel
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

    TunerContent(
        state = state,
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
        modifier = modifier,
    )
}

/**
 * The whole tuner: the two mode pills on top, then a stage with the pitch window, whose home
 * ring is either the note the child chose to play ([TunerMode.TARGET], with ‹ › to change it
 * and a button to hear it) or the nearest note to the one being played ([TunerMode.IDENTIFY])
 * and whose bubble floats up or down from it with the child's sound, then the ring's
 * fingering, one friendly status line (which names the heard note when it is not the chosen
 * one) and under it one quieter advice line saying what to change (always present, empty when
 * there is nothing to say, so nothing moves). No numbers beyond the octave in the note name:
 * a child only needs to know whether to go up or down.
 */
@Composable
fun TunerContent(
    state: TunerUiState,
    onTargetNoteChange: (Int) -> Unit,
    onModeChange: (TunerMode) -> Unit,
    onPlayNote: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A phone on its side has its own layout ([TunerSideways]), and its own place for the microphone
    // card, which brings its own page there.
    if (LocalSideways.current) {
        if (state.permissionGranted) {
            TunerSideways(
                state = state,
                onTargetNoteChange = onTargetNoteChange,
                onModeChange = onModeChange,
                onPlayNote = onPlayNote,
                modifier = modifier,
            )
        } else {
            MicrophonePermissionCard(
                onRequestPermission = onRequestPermission,
                onOpenSettings = onOpenSettings.takeIf { state.micBlocked },
                modifier = modifier.fillMaxSize(),
            )
        }
        return
    }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fitOrScroll(fits = maxHeight >= TunerMinHeight, minHeight = TunerMinHeight)
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!state.permissionGranted) {
                MicrophonePermissionCard(
                    onRequestPermission = onRequestPermission,
                    onOpenSettings = onOpenSettings.takeIf { state.micBlocked },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
                return@Column
            }

            TunerModeChoice(mode = state.mode, onSelect = onModeChange)

            Stage(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                val description = pitchWindowDescription(state)
                val window: @Composable (Modifier) -> Unit = { windowModifier ->
                    PitchWindow(
                        homeMidi = state.homeMidi,
                        heardMidi = state.heardMidi,
                        cents = state.gaugeCents,
                        description = description,
                        modifier = windowModifier,
                    )
                }
                // Both take what height is left over and shrink to it on a short screen.
                when (state.mode) {
                    TunerMode.TARGET -> TargetNote(
                        targetMidi = state.targetMidi,
                        onTargetChange = onTargetNoteChange,
                        onPlay = onPlayNote,
                        isPlaying = state.isPlayingReference,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        window = window,
                    )
                    TunerMode.IDENTIFY -> window(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                    )
                }

                // Which keys to press: for the ring's note, chosen or heard.
                TunerFingering(state)

                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    StatusLine(state)
                    AdviceLine(state.advice)
                    if (state.micFailed) {
                        Text(stringResource(R.string.mic_failed), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

/**
 * The pitch window shrinks to fit a small phone; only below this height (split screen, a tiny
 * phone) does the tab scroll instead.
 */
private val TunerMinHeight = 440.dp

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
 * One line of feedback for [state], written once for both layouts; while nothing is heard it names
 * the chosen note (in Tune mode) or asks for any note, and while the reference note is playing it
 * says to listen. When the child plays a different note from the chosen one (the state's mismatch)
 * the line says which note it was, or that it is the octave above or below. What to change about it
 * is the advice line's business ([AdviceLine]). It bounces in when the note comes into tune, and
 * shrinks rather than wraps when the words are long.
 *
 * Upright is the default. A phone on its side draws it bigger ([maxFont], in a box [height] tall)
 * and may let the longest words shrink further ([minFont]) or break once between words ([maxLines])
 * where the room beside the window is narrow. Such a caller gives a [style] with a relative line
 * height, so that the lines scale with the font and the largest that fits is found by the box.
 */
@Composable
internal fun StatusLine(
    state: TunerUiState,
    modifier: Modifier = Modifier,
    maxFont: TextUnit = 32.sp,
    height: Dp = 40.dp,
    minFont: TextUnit = 18.sp,
    maxLines: Int = 1,
    style: TextStyle = MaterialTheme.typography.headlineMedium,
) {
    val status = state.status
    val targetNote = noteLabel(state.targetMidi).takeIf { state.mode == TunerMode.TARGET }
    val mismatch = state.mismatch
    val heardNote = state.reading?.let { noteLabel(it.midi) }
    val playing = state.isPlayingReference
    val scale = remember { Animatable(1f) }
    LaunchedEffect(status == TuningStatus.IN_TUNE) {
        if (status == TuningStatus.IN_TUNE) {
            scale.snapTo(1.3f)
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        }
    }
    Text(
        text = when (status) {
            TuningStatus.NONE -> when {
                playing -> stringResource(R.string.listen_status)
                targetNote != null -> stringResource(R.string.play_note_prompt, targetNote)
                else -> stringResource(R.string.play_a_note)
            }
            TuningStatus.IN_TUNE -> stringResource(R.string.in_tune)
            TuningStatus.LOW -> when (mismatch) {
                TargetMismatch.OCTAVE_DOWN -> stringResource(R.string.octave_too_low)
                TargetMismatch.OTHER_NOTE -> stringResource(R.string.heard_lower_note, heardNote.orEmpty())
                else -> stringResource(R.string.too_low)
            }
            TuningStatus.HIGH -> when (mismatch) {
                TargetMismatch.OCTAVE_UP -> stringResource(R.string.octave_too_high)
                TargetMismatch.OTHER_NOTE -> stringResource(R.string.heard_higher_note, heardNote.orEmpty())
                else -> stringResource(R.string.too_high)
            }
        },
        style = style,
        textAlign = TextAlign.Center,
        maxLines = maxLines,
        autoSize = TextAutoSize.StepBased(minFontSize = minFont, maxFontSize = maxFont),
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            // Two lines sit in the middle of their box, as one line would at the top of it.
            .then(if (maxLines > 1) Modifier.wrapContentHeight(Alignment.CenterVertically) else Modifier)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            },
    )
}

/**
 * Which keys to press for the ring's note, chosen or heard, across the room it is given: written
 * once for both layouts, which pass their own [modifier] (the sideways one tags it for the tests).
 */
@Composable
internal fun TunerFingering(state: TunerUiState, modifier: Modifier = Modifier) {
    val midi = state.homeMidi
    FingeringDiagram(
        keys = midi?.let(FluteFingerings::keysFor),
        noteLabel = midi?.let { noteLabel(it) },
        color = midi?.let(::noteColor) ?: FluteTheme.colors.raised,
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(FINGERING_ASPECT_RATIO),
    )
}

/**
 * What a screen reader says for the pitch window: the home note and nothing heard yet, or
 * where the child's sound is against it and which way to go.
 */
@Composable
internal fun pitchWindowDescription(state: TunerUiState): String {
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
