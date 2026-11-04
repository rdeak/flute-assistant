package hr.bertlab.fluteassistant.ui.tuner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.domain.tuner.FluteFingerings
import hr.bertlab.fluteassistant.domain.tuner.TunerMode
import hr.bertlab.fluteassistant.ui.common.AdviceLine
import hr.bertlab.fluteassistant.ui.common.AppIcons
import hr.bertlab.fluteassistant.ui.common.ChunkyButton
import hr.bertlab.fluteassistant.ui.common.Emoji
import hr.bertlab.fluteassistant.ui.common.MicrophonePermissionCard
import hr.bertlab.fluteassistant.ui.common.Stage
import hr.bertlab.fluteassistant.ui.common.chunky
import hr.bertlab.fluteassistant.ui.common.fitOrScroll
import hr.bertlab.fluteassistant.ui.common.labelRes
import hr.bertlab.fluteassistant.ui.common.noteColor
import hr.bertlab.fluteassistant.ui.common.noteLabel
import hr.bertlab.fluteassistant.ui.theme.FluteTheme
import hr.bertlab.fluteassistant.ui.theme.Sun

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
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.onPermissionResult(granted) }

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
    LifecycleStartEffect(state.permissionGranted) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted != state.permissionGranted) viewModel.onPermissionResult(granted)
        if (granted) viewModel.startListening()
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
                    onOpenSettings = onOpenSettings,
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
                val fingeredMidi = state.homeMidi
                FingeringDiagram(
                    keys = fingeredMidi?.let(FluteFingerings::keysFor),
                    noteLabel = fingeredMidi?.let { noteLabel(it) },
                    color = fingeredMidi?.let(::noteColor) ?: FluteTheme.colors.raised,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(FINGERING_ASPECT_RATIO),
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    StatusLine(
                        status = state.status,
                        targetNote = noteLabel(state.targetMidi).takeIf { state.mode == TunerMode.TARGET },
                        mismatch = state.mismatch,
                        heardNote = state.reading?.let { noteLabel(it.midi) },
                        playing = state.isPlayingReference,
                    )
                    AdviceLine(state.advice)
                    state.errorMessage?.let { message ->
                        Text(message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
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

private val TunerMode.emoji: String
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
    val groupLabel = stringResource(R.string.tuner_mode)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 5.dp)
            .selectableGroup()
            .semantics { contentDescription = groupLabel },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TunerMode.entries.forEach { entry ->
            val isSelected = entry == mode
            ChunkyButton(
                onClick = { onSelect(entry) },
                selected = isSelected,
                color = if (isSelected) Sun else FluteTheme.colors.raised,
                depth = 5.dp,
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Emoji(entry.emoji, fontSize = 26)
                    Text(
                        text = stringResource(entry.labelRes()),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * One line of feedback; while nothing is heard it names [targetNote] when there is one, or
 * asks for any note, and while the reference note is [playing] it says to listen. When the
 * child plays a different note from the chosen one ([mismatch]) the line says which note it
 * was ([heardNote]), or that it is the octave above or below. What to change about it is
 * the advice line's business ([AdviceLine]). It bounces in when the note comes into tune,
 * and shrinks rather than wraps when the words are long.
 */
@Composable
private fun StatusLine(
    status: TuningStatus,
    targetNote: String?,
    mismatch: TargetMismatch,
    heardNote: String?,
    playing: Boolean,
) {
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
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 18.sp, maxFontSize = 32.sp),
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            },
    )
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
