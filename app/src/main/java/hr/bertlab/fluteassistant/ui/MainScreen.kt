// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.IntState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.ui.common.HideSystemBars
import hr.bertlab.fluteassistant.ui.common.KeepScreenAwake
import hr.bertlab.fluteassistant.ui.common.LocalSideways
import hr.bertlab.fluteassistant.ui.common.LocalTabZoom
import hr.bertlab.fluteassistant.ui.common.SidewaysGeometry
import hr.bertlab.fluteassistant.ui.common.SystemBarsFollowTheme
import hr.bertlab.fluteassistant.ui.common.TabletGeometry
import hr.bertlab.fluteassistant.ui.common.zoomedBy
import hr.bertlab.fluteassistant.ui.fingers.FingersScreen
import hr.bertlab.fluteassistant.ui.fingers.FingersViewModel
import hr.bertlab.fluteassistant.ui.flute.FluteScreen
import hr.bertlab.fluteassistant.ui.flute.FluteViewModel
import hr.bertlab.fluteassistant.ui.metronome.MetronomeMessage
import hr.bertlab.fluteassistant.ui.metronome.MetronomeScreen
import hr.bertlab.fluteassistant.ui.metronome.MetronomeViewModel
import hr.bertlab.fluteassistant.ui.notes.NotesScreen
import hr.bertlab.fluteassistant.ui.notes.NotesViewModel
import hr.bertlab.fluteassistant.ui.theme.FluteAssistantTheme
import hr.bertlab.fluteassistant.ui.theme.FluteTheme
import hr.bertlab.fluteassistant.ui.theme.ThemeViewModel
import hr.bertlab.fluteassistant.ui.tuner.TunerScreen
import hr.bertlab.fluteassistant.ui.tuner.TunerViewModel

/**
 * The five top-level destinations in tab bar order, each with the emoji that stands for it. The
 * flute comes first and is where the app opens: it is where a child practises (the songbook, Play,
 * Your turn, the metronome behind the speed knob). The drum sits beside it, the tab its knob opens.
 * The fingering chart comes last (since 2026-11-17, a teacher's ask), so the four a child knew stay put.
 */
enum class AppTab(@StringRes val labelRes: Int, val emoji: String) {
    FLUTE(R.string.tab_flute, "\uD83E\uDE88"), // flute
    METRONOME(R.string.tab_metronome, "\uD83E\uDD41"), // drum
    TUNER(R.string.tab_tuner, "\uD83D\uDC42"), // ear
    NOTES(R.string.tab_notes, "\uD83C\uDFBC"), // musical score
    FINGERS(R.string.tab_fingers, "\uD83D\uDD90\uFE0F"), // raised hand with fingers splayed
}

/**
 * Root of the UI: a [Scaffold] with the tab's title, the day/night switch and the language switch
 * on top and a bottom tab bar that switches between the digital flute, the metronome, the tuner,
 * the note reading game and the fingering chart. All the ViewModels are scoped to the Activity, so settings, playback
 * and the flute's tune survive switching tabs, rotation and the re-creation that a language change
 * triggers. Two things do not survive leaving their tab: the game's round, whose score resets so the
 * child always comes back to a fresh start, and a ♯♭ waiting on the Fingers tab (`FingersViewModel.leave`). After the
 * Fingers tab's flute opens the tuner, the phone's Back goes back to the Fingers tab until another tab is chosen
 * ([TabRoute], [BackToTab]).
 *
 * The theme is applied here, from the child's stored choice ([ThemeViewModel]) or, before one is
 * made, the phone's night setting: as Compose state, so a tap on the moon changes the colours in
 * place and re-creates nothing. The phone's bar icons are told the same.
 *
 * On a phone on its side a [SideRail] stands in for both bars and the phone's own bars hide. The
 * tab's `when` is written once here and handed to [MainFrame] as one slot, which calls it in one
 * place in both layouts, so a turn only recomposes it: were it in a branch of its own, the tab would
 * leave the composition and `FluteScreen` would stop the tune. `MainFrameTest` guards that.
 *
 * On a tablet the bars and the rail stay phone-sized, and [MainFrame] zooms the tab between them.
 */
@Composable
fun MainScreen(
    interactions: IntState,
    metronomeViewModel: MetronomeViewModel = hiltViewModel(),
    tunerViewModel: TunerViewModel = hiltViewModel(),
    notesViewModel: NotesViewModel = hiltViewModel(),
    fluteViewModel: FluteViewModel = hiltViewModel(),
    themeViewModel: ThemeViewModel = hiltViewModel(),
    fingersViewModel: FingersViewModel = hiltViewModel(),
) {
    // The open tab and where the phone's Back goes from it. Saved, as the language switch re-creates the Activity.
    var route by rememberSaveable { mutableStateOf(TabRoute()) }
    val selectedTab = route.open
    // What the bars open, kept here: the title bar and the rail stand in different places, so a turn
    // between them would close a credits dialog or a language list held by either.
    val barPanels = rememberBarPanels()
    val metronomeState by metronomeViewModel.state.collectAsStateWithLifecycle()
    val tunerState by tunerViewModel.state.collectAsStateWithLifecycle()
    val notesState by notesViewModel.state.collectAsStateWithLifecycle()
    val fluteState by fluteViewModel.state.collectAsStateWithLifecycle()
    val fingersState by fingersViewModel.state.collectAsStateWithLifecycle()
    val themeChoice by themeViewModel.choice.collectAsStateWithLifecycle()
    val dark = themeChoice.isDark(systemDark = isSystemInDarkTheme())
    SystemBarsFollowTheme(dark)

    // The phone stays awake while it sounds or hears a note, and for five minutes after that or after the last touch.
    // Derived, so the frame is drawn again when the answer changes and not on every frame the tuner hears.
    val busy by remember { derivedStateOf { metronomeState.isPlaying || fluteState.isBusy || tunerState.isBusy } }
    KeepScreenAwake(busy = busy, interactions = interactions)

    // One-shot messages (audio unavailable) as a snackbar.
    val snackbarHostState = remember { SnackbarHostState() }
    // Derived: the beat changes the metronome's state many times a second, and the message hardly ever.
    val message by remember { derivedStateOf { metronomeState.userMessage } }
    val messageText = message?.let {
        stringResource(
            when (it) {
                MetronomeMessage.AUDIO_UNAVAILABLE -> R.string.msg_audio_unavailable
            },
        )
    }
    LaunchedEffect(message) {
        if (message != null && messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            metronomeViewModel.dismissMessage()
        }
    }

    // What a tab drops when another is chosen, one branch a tab, so a new tab cannot be forgotten. Here and not in the
    // tab's own `DisposableEffect`: the language switch re-creates the Activity and would drop it too.
    val onLeaveTab: (AppTab) -> Unit = { left ->
        when (left) {
            AppTab.NOTES -> notesViewModel.resetRound()
            AppTab.FINGERS -> fingersViewModel.leave()
            // These keep everything: the flute stops its own tune when it leaves the screen (`FluteScreen`).
            AppTab.FLUTE, AppTab.METRONOME, AppTab.TUNER -> Unit
        }
    }
    // Every way to another tab: the tab that is left is told, then the new one shows.
    val go: (TabRoute) -> Unit = { next ->
        if (next.open != route.open) onLeaveTab(route.open)
        route = next
    }
    // A tab chosen on the bar or the rail is the child's own way: Back is the phone's again ([TabRoute.select]).
    val onSelectTab: (AppTab) -> Unit = { tab -> go(route.select(tab)) }
    // A tap on a flute of the Fingers tab opens its note in the tuner's Tune mode (2026-11-18, the user's ask), the
    // tuner first, so its first frame shows the note, and the phone's Back goes back to the Fingers tab
    // ([TabRoute.openFromHere]). The Fingers tab keeps its note for the way back: leaving it drops only a waiting ♯♭.
    val onTuneNote: (Int) -> Unit = { midi ->
        tunerViewModel.tune(midi)
        go(route.openFromHere(AppTab.TUNER))
    }
    // Before the tab, so a panel the tab opens over itself (the tuner's guide) takes Back first.
    BackToTab(tab = route.back, onBack = onSelectTab)

    FluteAssistantTheme(darkTheme = dark) {
        BoxWithConstraints {
            val sideways = SidewaysGeometry.isSideways(maxWidth.value, maxHeight.value)
            val tablet = TabletGeometry.isTablet(maxWidth.value, maxHeight.value)
            HideSystemBars(TabletGeometry.hidesSystemBars(sideways, tablet))
            CompositionLocalProvider(LocalSideways provides sideways) {
                MainFrame(
                    sideways = sideways,
                    snackbarHostState = snackbarHostState,
                    titleBar = {
                        TitleBar(
                            tab = selectedTab,
                            dark = dark,
                            onDarkChange = themeViewModel::setDark,
                            panels = barPanels,
                        )
                    },
                    tabBar = {
                        TabBar(
                            selected = selectedTab,
                            onSelect = onSelectTab,
                            drumBeating = metronomeState.isPlaying,
                            beatPulse = metronomeState.beatPulse,
                        )
                    },
                    rail = {
                        SideRail(
                            selected = selectedTab,
                            onSelect = onSelectTab,
                            drumBeating = metronomeState.isPlaying,
                            beatPulse = metronomeState.beatPulse,
                            panels = barPanels,
                        )
                    },
                    tablet = tablet,
                ) {
                    // The one call site of the tab: `MainFrame` puts this in the same place upright and sideways.
                    when (selectedTab) {
                        AppTab.METRONOME -> MetronomeScreen(state = metronomeState, viewModel = metronomeViewModel)
                        AppTab.TUNER -> TunerScreen(state = tunerState, viewModel = tunerViewModel)
                        AppTab.NOTES -> NotesScreen(state = notesState, viewModel = notesViewModel)
                        AppTab.FLUTE -> FluteScreen(
                            state = fluteState,
                            viewModel = fluteViewModel,
                            metronome = metronomeState,
                        )
                        AppTab.FINGERS -> FingersScreen(state = fingersState, viewModel = fingersViewModel, onTune = onTuneNote)
                    }
                }
            }
        }
    }
}

/**
 * The phone's Back while the open tab was opened from [tab] ([TabRoute.back]): it goes back there, through [onBack] as
 * a tap on the bar would, which forgets [tab]; with no [tab] it is the phone's and leaves the app. Call it before the
 * tab: the window asks the handler added last first, so a panel the tab opens over itself (the tuner's guide) closes
 * on Back before the tab is left (`BackToTabTest`).
 */
@Composable
internal fun BackToTab(tab: AppTab?, onBack: (AppTab) -> Unit) {
    BackHandler(enabled = tab != null) { tab?.let(onBack) }
}

/**
 * The frame round the open tab, stateless so that a test can turn it: a [Scaffold] with the
 * [titleBar] over and the [tabBar] under the tab when upright, and a [rail] beside it when
 * [sideways]. The [tab] is called in one place, a `BoxWithConstraints` in a `Row` that holds the rail
 * only when sideways, so a turn recomposes it where it stands: written in a branch of its own, it
 * would leave the composition and `FluteScreen` would stop the tune. `MainFrameTest` flips [sideways] and
 * asks that a `DisposableEffect` in the tab is not disposed and a `remember`ed object is the same one.
 *
 * On a [tablet] the tab is drawn at a higher density, by [TabletGeometry.tabZoom] of the room the bars
 * leave it: the phone layouts are laid out in a smaller logical area and come out bigger, and everything
 * outside this box keeps its phone size. The provider stands on a phone too, at 1, so that there is one
 * call site.
 */
@Composable
internal fun MainFrame(
    sideways: Boolean,
    snackbarHostState: SnackbarHostState,
    titleBar: @Composable () -> Unit,
    tabBar: @Composable () -> Unit,
    rail: @Composable () -> Unit,
    tablet: Boolean = false,
    tab: @Composable () -> Unit,
) {
    Scaffold(
        containerColor = FluteTheme.colors.page,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (!sideways) titleBar()
        },
        bottomBar = {
            if (!sideways) tabBar()
        },
        // The default also takes the cutout off every side, which sideways the rail and the
        // tab's box do themselves, so that the rail's background can run under it.
        contentWindowInsets = if (sideways) WindowInsets.systemBars else ScaffoldDefaults.contentWindowInsets,
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (sideways) rail()
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(if (sideways) Modifier.windowInsetsPadding(EndCutout) else Modifier),
            ) {
                val zoom = TabletGeometry.tabZoom(maxWidth.value, maxHeight.value, sideways, tablet)
                val real = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides remember(real, zoom) { real.zoomedBy(zoom) },
                    LocalTabZoom provides zoom,
                    content = tab,
                )
            }
        }
    }
}

/** The side a camera cutout takes from the tab: it swaps sides between the two sideways directions. */
private val EndCutout: WindowInsets
    @Composable get() = WindowInsets.displayCutout.only(WindowInsetsSides.End)
