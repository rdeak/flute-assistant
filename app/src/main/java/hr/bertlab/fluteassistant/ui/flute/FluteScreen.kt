// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.flute

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.domain.metronome.TimeSignature
import hr.bertlab.fluteassistant.domain.metronome.tempoBand
import hr.bertlab.fluteassistant.ui.common.AdviceLine
import hr.bertlab.fluteassistant.ui.common.AppIcons
import hr.bertlab.fluteassistant.ui.common.BigActionButton
import hr.bertlab.fluteassistant.ui.common.LocalSideways
import hr.bertlab.fluteassistant.ui.common.LocalTabZoom
import hr.bertlab.fluteassistant.ui.common.MicrophonePermissionCard
import hr.bertlab.fluteassistant.ui.common.SidewaysGeometry
import hr.bertlab.fluteassistant.ui.common.Stage
import hr.bertlab.fluteassistant.ui.common.TabletGeometry
import hr.bertlab.fluteassistant.ui.common.fitOrScroll
import hr.bertlab.fluteassistant.ui.common.rememberBeatFlash
import hr.bertlab.fluteassistant.ui.metronome.MetronomeMinHeight
import hr.bertlab.fluteassistant.ui.metronome.MetronomeStage
import hr.bertlab.fluteassistant.ui.metronome.MetronomeUiState
import hr.bertlab.fluteassistant.ui.metronome.closesTempoWheel
import hr.bertlab.fluteassistant.ui.metronome.rememberTempoWheelState
import hr.bertlab.fluteassistant.ui.theme.ActionGo
import hr.bertlab.fluteassistant.ui.theme.ActionListen
import hr.bertlab.fluteassistant.ui.theme.ActionStop

/**
 * Stateful entry point: opens the flute's sound while the screen is started (visible) and
 * closes it when it stops, the way the tuner holds the microphone, then renders the stateless
 * [FluteContent]. The [metronome] state is the metronome tab's own, for the speed knob on the
 * staff, the staff's time signature and the panel the knob opens; what that panel sets, and the
 * speed and beat a picked book brings, go through [FluteViewModel] to the one place the tempo and
 * the beat are kept, so this screen needs nothing else of the metronome's. "Your turn" needs the
 * microphone: a tap with RECORD_AUDIO granted starts listening at once, without it the tap asks
 * the phone and the answer goes to [FluteViewModel.onMicResult] (a yes listens, a no shows the
 * microphone card, whose "Yes" asks again and whose "Settings" opens the app's page). The tab never
 * asks for the microphone on its own; only the button does.
 */
@Composable
fun FluteScreen(
    state: FluteUiState,
    viewModel: FluteViewModel,
    metronome: MetronomeUiState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onMicResult(granted)
    }
    fun micGranted() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    LifecycleStartEffect(Unit) {
        viewModel.enterTab()
        onStopOrDispose { viewModel.leaveTab() }
    }
    val actions = remember(viewModel, micLauncher, context) {
        FluteActions(
            onKeyPressed = viewModel::onKeyPressed,
            onKeyReleased = viewModel::onKeyReleased,
            onKeyActivated = viewModel::onKeyActivated,
            onRestPressed = viewModel::onRestPressed,
            onRestReleased = viewModel::onRestReleased,
            onRestActivated = viewModel::onRestActivated,
            onBreath = viewModel::onBreath,
            onEraseLast = viewModel::eraseLast,
            onTogglePlayback = viewModel::togglePlayback,
            onYourTurn = {
                if (micGranted()) viewModel.startListening() else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            onStop = viewModel::stopRun,
            onBpmChange = viewModel::setBpm,
            onTimeSignatureChange = viewModel::setTimeSignature,
            onSpeedOpen = viewModel::openSpeed,
            onSpeedDone = viewModel::closeSpeed,
            onShelfOpen = viewModel::openShelf,
            onBookChosen = viewModel::openBook,
            onRequestMic = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
            onOpenSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
            },
            onMicDismiss = viewModel::dismissMic,
        )
    }
    FluteContent(state = state, metronome = metronome, actions = actions, modifier = modifier)
}

/**
 * Everything the flute tab can ask for, as one object, so [FluteContent] takes its state and this
 * rather than a callback for every key and button. Each action defaults to doing nothing, so a
 * test names only the ones it looks at.
 */
class FluteActions(
    val onKeyPressed: (Int) -> Unit = {},
    val onKeyReleased: (Int) -> Unit = {},
    val onKeyActivated: (Int) -> Unit = {},
    val onRestPressed: () -> Unit = {},
    val onRestReleased: () -> Unit = {},
    val onRestActivated: () -> Unit = {},
    val onBreath: () -> Unit = {},
    val onEraseLast: () -> Unit = {},
    /** Play. */
    val onTogglePlayback: () -> Unit = {},
    /** "Your turn"; the stateful entry asks for the microphone first if need be. */
    val onYourTurn: () -> Unit = {},
    /** The one wide Stop while the tune runs. */
    val onStop: () -> Unit = {},
    /** The speed panel's ring and beat buttons: they set the metronome's tempo and beat. */
    val onBpmChange: (Int) -> Unit = {},
    val onTimeSignatureChange: (TimeSignature) -> Unit = {},
    /** The speed knob was tapped, and OK on the panel it opens. */
    val onSpeedOpen: () -> Unit = {},
    val onSpeedDone: () -> Unit = {},
    /** The book button was tapped, and a book of the shelf picked (its index). */
    val onShelfOpen: () -> Unit = {},
    val onBookChosen: (Int) -> Unit = {},
    /** The microphone card, while [FluteUiState.micDenied]: Yes, Settings and Not now. */
    val onRequestMic: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onMicDismiss: () -> Unit = {},
)

private val PlayRowHeight = 84.dp
private val PlayShadow = 8.dp

/**
 * Everything below this height cannot hold the keys: the stage (the staff with the names under
 * it), three key rows and the Play row, plus the gaps between them.
 */
private val FitsHeight = 20.dp + (38.dp + TuneStaffHeight) +
    14.dp + FluteKeysMinHeight + 14.dp + PlayRowHeight + PlayShadow + 16.dp

/**
 * The whole flute tab, top to bottom: a stage with the child's tune on a staff ([TuneStaff]: the
 * playing step, or the held one, ringed in yellow, the held one growing in a yellow band over its
 * notes and its names) with every note's name in a pill right under it, in the same room, so
 * notes and names line up and scroll together, under the names the row of what each note earned
 * in the last "Your turn", and over the staff's top left the [SpeedKnob], the
 * metronome's animal for the tempo in [metronome], where printed music writes the tempo, and under
 * the clef, beside the names, the [BookButton], the sweet of the open book ([FluteUiState.openBook]),
 * whose tap is [FluteActions.onShelfOpen]; then the octave rows of [FluteKeys], with the rest, the breath and
 * the erase key ([FluteActions.onEraseLast], faded while the tune is empty) at the end of the top row, apart
 * from c3, taking the height left, and the Play row: two big buttons sharing its width, each stacking its icon over its
 * word, "Play" (green, [FluteActions.onTogglePlayback], faded while the tune is empty) and "Your turn" (blue,
 * [FluteActions.onYourTurn], faded while the tune holds no note). On an ordinary phone held
 * upright nothing scrolls; when the height is short of [FitsHeight] the keys keep their minimum
 * and the whole screen scrolls.
 *
 * While the tune runs ([FluteUiState.isRunning]: Play, or Your turn with the microphone open) the
 * keys and the sponge go away and the staff is drawn a third bigger ([RunningStaffScale]), so it
 * reads from a music stand at arm's length, with the knob's animal walking the staff at the knob's
 * height ([StaffAnimal]: over the playing step, hopping on to each beat's place on each click, or
 * waiting over the step the child is to play), the knob itself empty and inert, and the book button faded and inert,
 * still showing its sweet, which does not walk the staff; between the stage and the bottom
 * row the tuner's advice line speaks only while the sound stays unclear, and the bottom row is one
 * wide red Stop ([FluteActions.onStop]) where Play was, so the thumb finds it. The marks stay when the run
 * ends. The microphone's permission is the stateful entry's business ([FluteActions.onRequestMic],
 * [FluteActions.onOpenSettings], [FluteActions.onMicDismiss] serve the microphone card while [FluteUiState.micDenied]).
 *
 * A tap on the knob ([FluteActions.onSpeedOpen]) swaps all of that, in place, for the metronome tab's own
 * [MetronomeStage] (the beat bubbles with their − and +, the tempo ring with its wheel, set
 * through [FluteActions.onTimeSignatureChange] and [FluteActions.onBpmChange]) and one big green OK. OK ([FluteActions.onSpeedDone])
 * is the only way back, as the notes tab's picker is; the choice survives rotation. The panel
 * lays out as the metronome tab does, shrinking its ring to a short screen and scrolling only
 * below [MetronomeMinHeight].
 *
 * A tap on the book button ([FluteActions.onShelfOpen]) swaps the stage, the keys and the Play row, in place,
 * for the [TuneShelf]: the six books ([FluteUiState.shelf]), the open one's cover yellow. One tap
 * on a book closes the shelf and is [FluteActions.onBookChosen] with its index, the open book's too (which
 * changes nothing). Unlike the speed panel there is no OK; as in the panel and the notes picker,
 * the back button is not handled. An open shelf survives rotation as the panel does. Its three rows share the height and scroll only
 * below [ShelfMinHeight]; on its side the six books stand in one row, which never scrolls.
 *
 * On a phone on its side ([LocalSideways]) the same pieces stand side by side: [FluteWritingSideways],
 * [FluteRunningSideways] and [FluteSpeedSideways]. Both choices are kept above the branch, so a panel
 * stays open while the phone turns.
 */
@Composable
fun FluteContent(
    state: FluteUiState,
    metronome: MetronomeUiState,
    actions: FluteActions,
    modifier: Modifier = Modifier,
) {
    var choosingSpeed by rememberSaveable { mutableStateOf(false) }
    var choosingBook by rememberSaveable { mutableStateOf(false) }
    val sideways = LocalSideways.current
    val zoom = LocalTabZoom.current
    val zoomed = zoom > 1f

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        if (state.micDenied) {
            // The phone said no to the microphone: the tuner's card, with its quiet "Not now" as the way back.
            if (sideways) {
                // The card is a page of its own there, with the margins of one: it gets the whole tab.
                MicrophonePermissionCard(
                    onRequestPermission = actions.onRequestMic,
                    onOpenSettings = actions.onOpenSettings,
                    onDismiss = actions.onMicDismiss,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
                ) {
                    MicrophonePermissionCard(
                        onRequestPermission = actions.onRequestMic,
                        onOpenSettings = actions.onOpenSettings,
                        onDismiss = actions.onMicDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }
            }
        } else if (choosingSpeed) {
            val wheel = rememberTempoWheelState()
            val closePanel = {
                choosingSpeed = false
                actions.onSpeedDone()
            }
            if (sideways) {
                FluteSpeedSideways(
                    metronome = metronome,
                    wheel = wheel,
                    onBpmChange = actions.onBpmChange,
                    onTimeSignatureChange = actions.onTimeSignatureChange,
                    onDone = closePanel,
                    modifier = Modifier.closesTempoWheel(wheel),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fitOrScroll(fits = maxHeight >= MetronomeMinHeight, minHeight = MetronomeMinHeight)
                        .closesTempoWheel(wheel)
                        .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    MetronomeStage(
                        state = metronome,
                        wheel = wheel,
                        onBpmChange = actions.onBpmChange,
                        onTimeSignatureChange = actions.onTimeSignatureChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                    BigActionButton(
                        icon = AppIcons.Done,
                        text = stringResource(R.string.speed_done),
                        color = ActionGo,
                        onClick = closePanel,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
        } else if (choosingBook) {
            val chooseBook = { index: Int ->
                choosingBook = false
                actions.onBookChosen(index)
            }
            if (sideways) {
                // One row of six in the whole window less the margins: nothing scrolls, and there is no column.
                TuneShelf(
                    books = state.shelf,
                    openBook = state.openBook,
                    onChoose = chooseBook,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(SidewaysGeometry.MARGIN.dp),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fitOrScroll(fits = maxHeight >= ShelfMinHeight, minHeight = ShelfMinHeight)
                        .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
                ) {
                    TuneShelf(
                        books = state.shelf,
                        openBook = state.openBook,
                        onChoose = chooseBook,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }
            }
        } else {
            val fits = maxHeight >= FitsHeight
            val uprightKeyHeight = if (zoomed) TabletGeometry.uprightKeyHeight((maxHeight - FitsHeight).value).dp else KeyHeight
            val running = state.isRunning
            // The staff's place stays here, above the shapes: a phone on its side lays Play and a run
            // out in places of their own, and a staff made anew at each swap would scroll in from its start.
            val staffPlace = rememberLazyListState()
            val staffScale = when {
                !running -> if (sideways && zoomed) TabletGeometry.sidewaysStaffScale(zoom) else 1f
                sideways -> SidewaysGeometry.runningStaffScale(maxHeight.value, StaffGeometry.TOTAL_HEIGHT)
                else -> RunningStaffScale
            }
            // The walking animal's hop: it hops only on the staff, so only while the tune runs.
            val flash = rememberBeatFlash(state.beatPulse, running)
            val animal = if (running) {
                state.animalIndex?.let {
                    StaffAnimal(it, tempoBand(metronome.config.bpm).emoji, flash, waiting = state.waiting, halfBeat = state.animalHalfBeat)
                }
            } else {
                null
            }
            // The pieces both shapes show, so upright and sideways can only differ in where they stand.
            val tune: @Composable (Modifier) -> Unit = { tuneModifier ->
                Box(tuneModifier.fillMaxWidth()) {
                    TuneStaff(
                        tune = state.tune,
                        litIndex = state.litIndex,
                        heldIndex = state.heldIndex,
                        timeSignature = metronome.config.timeSignature,
                        marks = state.marks,
                        animal = animal,
                        wrong = state.wrong,
                        heardNow = state.heardNow,
                        scale = staffScale,
                        listState = staffPlace,
                    )
                    SpeedKnob(
                        bpm = metronome.config.bpm,
                        running = running,
                        onClick = {
                            actions.onSpeedOpen()
                            choosingSpeed = true
                        },
                        modifier = Modifier.align(Alignment.TopStart),
                    )
                    BookButton(
                        sweet = Sweet.entries[state.openBook],
                        running = running,
                        onClick = {
                            actions.onShelfOpen()
                            choosingBook = true
                        },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(y = StaffGeometry.bookButtonTop(staffScale).dp),
                    )
                }
            }
            val keys: @Composable (Modifier, Dp, Dp) -> Unit = { keysModifier, keyHeight, keyGap ->
                FluteKeys(
                    litMidi = state.litMidi,
                    restLit = state.restLit,
                    eraseEnabled = !state.isEmpty,
                    actions = actions,
                    modifier = keysModifier,
                    keyHeight = keyHeight,
                    keyGap = keyGap,
                )
            }
            val playRow: @Composable (Modifier) -> Unit = { rowModifier ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = rowModifier
                        .fillMaxWidth()
                        .padding(bottom = PlayShadow),
                ) {
                    BigActionButton(
                        icon = AppIcons.Play,
                        text = stringResource(R.string.flute_play),
                        color = ActionGo,
                        onClick = actions.onTogglePlayback,
                        enabled = !state.isEmpty,
                        stacked = true,
                        modifier = Modifier.weight(1f),
                    )
                    BigActionButton(
                        icon = AppIcons.Microphone,
                        text = stringResource(R.string.flute_your_turn),
                        color = ActionListen,
                        onClick = actions.onYourTurn,
                        enabled = state.canListen,
                        stacked = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (sideways) {
                if (running) {
                    FluteRunningSideways(tune = tune, advice = state.advice, onStop = actions.onStop)
                } else {
                    FluteWritingSideways(tune = tune, keys = keys, buttons = playRow)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (fits) Modifier else Modifier.verticalScroll(rememberScrollState()))
                        .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Stage(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        tune(Modifier)
                    }
                    if (running) {
                        // The keys' room: the advice line alone in it, and one wide Stop where Play was.
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (fits) Modifier.weight(1f) else Modifier.height(AdviceLineHeight)),
                        ) {
                            AdviceLine(state.advice)
                        }
                        BigActionButton(
                            icon = AppIcons.Stop,
                            text = stringResource(R.string.stop),
                            color = ActionStop,
                            onClick = actions.onStop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = PlayShadow),
                        )
                    } else {
                        keys(
                            Modifier
                                .fillMaxWidth()
                                .then(if (fits) Modifier.weight(1f) else Modifier.height(FluteKeysMinHeight)),
                            uprightKeyHeight,
                            KeyGap,
                        )
                        playRow(Modifier)
                    }
                }
            }
        }
    }
}

/** The advice line's own height, for the short screen where the keys' room is not spread by weight. */
private val AdviceLineHeight = 28.dp
