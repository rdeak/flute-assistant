// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.ui.common.AppLanguage
import hr.bertlab.fluteassistant.ui.common.CreditsLogo
import hr.bertlab.fluteassistant.ui.common.Emoji
import hr.bertlab.fluteassistant.ui.common.HideSystemBars
import hr.bertlab.fluteassistant.ui.common.KeepScreenOn
import hr.bertlab.fluteassistant.ui.common.LanguageSwitch
import hr.bertlab.fluteassistant.ui.common.LocalSideways
import hr.bertlab.fluteassistant.ui.common.LocalTabZoom
import hr.bertlab.fluteassistant.ui.common.OutlineWidth
import hr.bertlab.fluteassistant.ui.common.SidewaysGeometry
import hr.bertlab.fluteassistant.ui.common.SystemBarsFollowTheme
import hr.bertlab.fluteassistant.ui.common.TabletGeometry
import hr.bertlab.fluteassistant.ui.common.ThemeSwitch
import hr.bertlab.fluteassistant.ui.common.hopOnBeat
import hr.bertlab.fluteassistant.ui.common.rememberBeatFlash
import hr.bertlab.fluteassistant.ui.common.zoomedBy
import hr.bertlab.fluteassistant.ui.flute.FluteScreen
import hr.bertlab.fluteassistant.ui.flute.FluteViewModel
import hr.bertlab.fluteassistant.ui.metronome.MetronomeMessage
import hr.bertlab.fluteassistant.ui.metronome.MetronomeScreen
import hr.bertlab.fluteassistant.ui.metronome.MetronomeViewModel
import hr.bertlab.fluteassistant.ui.notes.NotesScreen
import hr.bertlab.fluteassistant.ui.notes.NotesViewModel
import hr.bertlab.fluteassistant.ui.theme.FluteAssistantTheme
import hr.bertlab.fluteassistant.ui.theme.FluteTheme
import hr.bertlab.fluteassistant.ui.theme.InkOnFill
import hr.bertlab.fluteassistant.ui.theme.Sun
import hr.bertlab.fluteassistant.ui.theme.ThemeViewModel
import hr.bertlab.fluteassistant.ui.tuner.TunerScreen
import hr.bertlab.fluteassistant.ui.tuner.TunerViewModel

/**
 * The four top-level destinations in tab bar order, each with the emoji that stands for it. The
 * flute comes first and is where the app opens: it is where a child practises (the songbook, Play,
 * Your turn, the metronome behind the speed knob). The drum sits beside it, the tab its knob opens.
 */
enum class AppTab(@StringRes val labelRes: Int, val emoji: String) {
    FLUTE(R.string.tab_flute, "\uD83E\uDE88"), // flute
    METRONOME(R.string.tab_metronome, "\uD83E\uDD41"), // drum
    TUNER(R.string.tab_tuner, "\uD83D\uDC42"), // ear
    NOTES(R.string.tab_notes, "\uD83C\uDFBC"), // musical score
}

/**
 * Root of the UI: a [Scaffold] with the tab's title, the day/night switch and the language switch
 * on top and a bottom tab bar that switches between the digital flute, the metronome, the tuner
 * and the note reading game. All the ViewModels are scoped to the Activity, so settings, playback
 * and the flute's tune survive switching tabs, rotation and the re-creation that a language change
 * triggers. The game's round is the exception: leaving its tab resets the score, so the child
 * always comes back to a fresh start.
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
    metronomeViewModel: MetronomeViewModel = hiltViewModel(),
    tunerViewModel: TunerViewModel = hiltViewModel(),
    notesViewModel: NotesViewModel = hiltViewModel(),
    fluteViewModel: FluteViewModel = hiltViewModel(),
    themeViewModel: ThemeViewModel = hiltViewModel(),
) {
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.entries.first()) }
    // What the bars open, kept here: the title bar and the rail stand in different places, so a turn
    // between them would close a credits dialog or a language list held by either.
    var creditsOpen by rememberSaveable { mutableStateOf(false) }
    var languageOpen by remember { mutableStateOf(false) }
    val metronomeState by metronomeViewModel.state.collectAsStateWithLifecycle()
    val tunerState by tunerViewModel.state.collectAsStateWithLifecycle()
    val notesState by notesViewModel.state.collectAsStateWithLifecycle()
    val fluteState by fluteViewModel.state.collectAsStateWithLifecycle()
    val themeChoice by themeViewModel.choice.collectAsStateWithLifecycle()
    val dark = themeChoice.isDark(systemDark = isSystemInDarkTheme())
    SystemBarsFollowTheme(dark)

    // A tune plays back untouched for up to a minute or two, and a child plays it back to the phone with both hands on the flute: the screen must not time out under either.
    KeepScreenOn(enabled = metronomeState.isPlaying || tunerState.isListening || fluteState.isRunning)

    // One-shot messages (audio unavailable) as a snackbar.
    val snackbarHostState = remember { SnackbarHostState() }
    val message = metronomeState.userMessage
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

    val onSelectTab: (AppTab) -> Unit = { tab ->
        if (selectedTab == AppTab.NOTES && tab != AppTab.NOTES) notesViewModel.resetRound()
        selectedTab = tab
    }

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
                            creditsOpen = creditsOpen,
                            onCreditsOpenChange = { creditsOpen = it },
                            languageOpen = languageOpen,
                            onLanguageOpenChange = { languageOpen = it },
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
                            creditsOpen = creditsOpen,
                            onCreditsOpenChange = { creditsOpen = it },
                            languageOpen = languageOpen,
                            onLanguageOpenChange = { languageOpen = it },
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
                            metronomeViewModel = metronomeViewModel,
                        )
                    }
                }
            }
        }
    }
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

/**
 * The in-app language switch, on the top bar or the rail. The current language is read from the
 * resources the words come from (which is what the per-app locale actually changed), so anything
 * that is not Croatian shows as English, the fallback. Choosing hands the tag to the platform's
 * per-app language setting: the system on Android 13+, AppCompat's stored backport below that.
 * Either way the activity is re-created in the new language.
 */
@Composable
internal fun AppLanguageSwitch(
    modifier: Modifier = Modifier,
    listOffset: Dp = 0.dp,
    isOpen: Boolean? = null,
    onOpenChange: ((Boolean) -> Unit)? = null,
) {
    // From the resources the words themselves come from, not from `LocalConfiguration`: a turn
    // hands Compose its configuration through `onConfigurationChanged` and nothing here can say
    // that this carries AppCompat's locale override below Android 13, so the flag could disagree
    // with the words. No `remember`: the resources hold one mutable configuration that is updated in
    // place, so a key on it would keep a stale answer, and picking the language is cheap.
    val locales = ConfigurationCompat.getLocales(LocalResources.current.configuration)
    // An empty list is not seen on a phone; English is the fallback anyway.
    val current = locales[0]?.let(AppLanguage::fromLocale) ?: AppLanguage.ENGLISH
    LanguageSwitch(
        current = current,
        onSelect = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(it.tag)) },
        modifier = modifier,
        listOffset = listOffset,
        isOpen = isOpen,
        onOpenChange = onOpenChange,
    )
}

/**
 * The top bar: the app icon, the open tab's name and, at the end, the day/night switch (a moon
 * by day, a sun by night, [dark] and [onDarkChange]) and then the in-app language switch. The
 * credits and the language list are open or shut as [creditsOpen] and [languageOpen] say when the
 * caller passes them with their change callbacks, so a turn to the rail leaves them as they were.
 */
@Composable
internal fun TitleBar(
    tab: AppTab,
    dark: Boolean,
    onDarkChange: (Boolean) -> Unit,
    creditsOpen: Boolean? = null,
    onCreditsOpenChange: ((Boolean) -> Unit)? = null,
    languageOpen: Boolean? = null,
    onLanguageOpenChange: ((Boolean) -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(FluteTheme.colors.page)
            .statusBarsPadding()
            .height(72.dp)
            .padding(horizontal = 20.dp),
    ) {
        CreditsLogo(modifier = Modifier.size(48.dp), isOpen = creditsOpen, onOpenChange = onCreditsOpenChange)
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(tab.labelRes),
            style = MaterialTheme.typography.headlineMedium,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = 32.sp),
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
                .semantics { heading() },
        )
        ThemeSwitch(dark = dark, onDarkChange = onDarkChange)
        Spacer(Modifier.width(8.dp))
        AppLanguageSwitch(isOpen = languageOpen, onOpenChange = onLanguageOpenChange)
    }
}

/**
 * The bottom bar: one [TabItem] per tab. It draws its own background under the system navigation
 * bar. While the metronome's drum beats ([drumBeating]) the metronome tab's 🥁 hops on every
 * [beatPulse], on every tab, the same hop as the ring's animal.
 */
@Composable
private fun TabBar(selected: AppTab, onSelect: (AppTab) -> Unit, drumBeating: Boolean, beatPulse: Long) {
    val colors = FluteTheme.colors
    val drumFlash = rememberBeatFlash(beatPulse, drumBeating)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.card)
            .drawBehind { drawRect(colors.border, size = Size(size.width, OutlineWidth.toPx())) }
            .navigationBarsPadding()
            .selectableGroup()
            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 8.dp)
            .height(66.dp),
    ) {
        AppTab.entries.forEach { tab ->
            TabItem(
                tab = tab,
                selected = tab == selected,
                onSelect = onSelect,
                drumBeating = drumBeating,
                drumFlash = drumFlash,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

/**
 * One tab, on the bottom bar or the rail: an emoji over a word, the open one a yellow pill with an
 * ink outline. While the drum beats the metronome's 🥁 hops on [drumFlash] and a screen reader hears
 * that tab as "Playing". The rail passes smaller sizes; the defaults are the bottom bar's.
 */
@Composable
internal fun TabItem(
    tab: AppTab,
    selected: Boolean,
    onSelect: (AppTab) -> Unit,
    drumBeating: Boolean,
    drumFlash: () -> Float,
    modifier: Modifier = Modifier,
    emojiSize: Int = 28,
    wordStyle: TextStyle = MaterialTheme.typography.labelMedium,
    minWordSize: TextUnit = 10.sp,
    maxWordSize: TextUnit = 14.sp,
    wordPadding: Dp = 9.dp,
    shape: Shape = TabShape,
) {
    val colors = FluteTheme.colors
    val drumPlaying = stringResource(R.string.tab_metronome_playing)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .clip(shape)
            .background(if (selected) Sun else Color.Transparent)
            .border(OutlineWidth, if (selected) colors.outline else Color.Transparent, shape)
            .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(tab) })
            .semantics {
                if (tab == AppTab.METRONOME && drumBeating) stateDescription = drumPlaying
            },
    ) {
        Emoji(
            tab.emoji,
            fontSize = emojiSize,
            modifier = if (tab == AppTab.METRONOME) Modifier.hopOnBeat(drumFlash) else Modifier,
        )
        Text(
            text = stringResource(tab.labelRes),
            style = wordStyle,
            color = if (selected) InkOnFill else colors.text,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = minWordSize, maxFontSize = maxWordSize),
            modifier = Modifier.padding(horizontal = wordPadding),
        )
    }
}

private val TabShape = RoundedCornerShape(22.dp)
