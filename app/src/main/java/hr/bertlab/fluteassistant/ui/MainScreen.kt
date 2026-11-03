package hr.bertlab.fluteassistant.ui

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.ui.common.AppLanguage
import hr.bertlab.fluteassistant.ui.common.AppLogo
import hr.bertlab.fluteassistant.ui.common.Emoji
import hr.bertlab.fluteassistant.ui.common.KeepScreenOn
import hr.bertlab.fluteassistant.ui.common.LanguageSwitch
import hr.bertlab.fluteassistant.ui.common.OutlineWidth
import hr.bertlab.fluteassistant.ui.common.hopOnBeat
import hr.bertlab.fluteassistant.ui.common.rememberBeatFlash
import hr.bertlab.fluteassistant.ui.flute.FluteScreen
import hr.bertlab.fluteassistant.ui.flute.FluteViewModel
import hr.bertlab.fluteassistant.ui.metronome.MetronomeMessage
import hr.bertlab.fluteassistant.ui.metronome.MetronomeScreen
import hr.bertlab.fluteassistant.ui.metronome.MetronomeViewModel
import hr.bertlab.fluteassistant.ui.notes.NotesScreen
import hr.bertlab.fluteassistant.ui.notes.NotesViewModel
import hr.bertlab.fluteassistant.ui.theme.FluteTheme
import hr.bertlab.fluteassistant.ui.theme.InkOnFill
import hr.bertlab.fluteassistant.ui.theme.Sun
import hr.bertlab.fluteassistant.ui.tuner.TunerScreen
import hr.bertlab.fluteassistant.ui.tuner.TunerViewModel
import java.util.Locale

/** The four top-level destinations, each with the emoji that stands for it in the tab bar. */
enum class AppTab(@StringRes val labelRes: Int, val emoji: String) {
    METRONOME(R.string.tab_metronome, "\uD83E\uDD41"), // drum
    TUNER(R.string.tab_tuner, "\uD83D\uDC42"), // ear
    NOTES(R.string.tab_notes, "\uD83C\uDFBC"), // musical score
    FLUTE(R.string.tab_flute, "\uD83E\uDE88"), // flute
}

/**
 * Root of the UI: a [Scaffold] with the tab's title and the language switch on top and a bottom
 * tab bar that switches between the metronome, the tuner, the note reading game and
 * the digital flute. All four ViewModels are scoped to the Activity, so settings, playback and
 * the flute's tune survive switching tabs, rotation and the re-creation that a language change
 * triggers. The game's round is the exception: leaving its tab resets the score, so the child
 * always comes back to a fresh start.
 */
@Composable
fun MainScreen(
    metronomeViewModel: MetronomeViewModel = hiltViewModel(),
    tunerViewModel: TunerViewModel = hiltViewModel(),
    notesViewModel: NotesViewModel = hiltViewModel(),
    fluteViewModel: FluteViewModel = hiltViewModel(),
) {
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.METRONOME) }
    val metronomeState by metronomeViewModel.state.collectAsStateWithLifecycle()
    val tunerState by tunerViewModel.state.collectAsStateWithLifecycle()
    val notesState by notesViewModel.state.collectAsStateWithLifecycle()
    val fluteState by fluteViewModel.state.collectAsStateWithLifecycle()

    // A tune plays back untouched for up to a minute or two: the screen must not time out under it.
    KeepScreenOn(enabled = metronomeState.isPlaying || tunerState.isListening || fluteState.isPlaying)

    // One-shot messages (audio unavailable, persistence failures) as a snackbar.
    val snackbarHostState = remember { SnackbarHostState() }
    val message = metronomeState.userMessage
    val messageText = message?.let {
        stringResource(
            when (it) {
                MetronomeMessage.AUDIO_UNAVAILABLE -> R.string.msg_audio_unavailable
                MetronomeMessage.SAVE_FAILED -> R.string.msg_save_failed
                MetronomeMessage.DELETE_FAILED -> R.string.msg_delete_failed
            },
        )
    }
    LaunchedEffect(message) {
        if (message != null && messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            metronomeViewModel.dismissMessage()
        }
    }

    Scaffold(
        containerColor = FluteTheme.colors.page,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { TitleBar(selectedTab) },
        bottomBar = {
            TabBar(
                selected = selectedTab,
                onSelect = { tab ->
                    if (selectedTab == AppTab.NOTES && tab != AppTab.NOTES) notesViewModel.resetRound()
                    selectedTab = tab
                },
                drumBeating = metronomeState.isPlaying,
                beatPulse = metronomeState.beatPulse,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
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


/**
 * The top bar: the app icon, the open tab's name and, at the end, the in-app language switch.
 * The current language is read from the activity's configuration (which is what the
 * per-app locale actually changed), so anything that is not Croatian shows as English, the
 * fallback. Choosing hands the tag to the platform's per-app language setting: the system on
 * Android 13+, AppCompat's stored backport below that. Either way the activity is re-created
 * in the new language.
 */
@Composable
private fun TitleBar(tab: AppTab) {
    val configuration = LocalConfiguration.current
    val current = remember(configuration) {
        AppLanguage.fromLocale(ConfigurationCompat.getLocales(configuration)[0] ?: Locale.getDefault())
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(FluteTheme.colors.page)
            .statusBarsPadding()
            .height(72.dp)
            .padding(horizontal = 20.dp),
    ) {
        AppLogo(contentDescription = stringResource(R.string.app_name), modifier = Modifier.size(48.dp))
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
        LanguageSwitch(
            current = current,
            onSelect = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(it.tag)) },
        )
    }
}

/**
 * The bottom bar: one emoji-and-word button per tab, the open one a yellow pill with an ink
 * outline. It draws its own background under the system navigation bar. While the metronome's
 * drum beats ([drumBeating]) the metronome tab's 🥁 hops on every [beatPulse], on every tab, the
 * same hop as the ring's animal, and a screen reader hears that tab as "Playing".
 */
@Composable
private fun TabBar(selected: AppTab, onSelect: (AppTab) -> Unit, drumBeating: Boolean, beatPulse: Long) {
    val colors = FluteTheme.colors
    val drumFlash = rememberBeatFlash(beatPulse, drumBeating)
    val drumPlaying = stringResource(R.string.tab_metronome_playing)
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
            val isSelected = tab == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(TabShape)
                    .background(if (isSelected) Sun else Color.Transparent)
                    .border(OutlineWidth, if (isSelected) colors.outline else Color.Transparent, TabShape)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(tab) })
                    .semantics {
                        if (tab == AppTab.METRONOME && drumBeating) stateDescription = drumPlaying
                    },
            ) {
                Emoji(
                    tab.emoji,
                    fontSize = 28,
                    modifier = if (tab == AppTab.METRONOME) Modifier.hopOnBeat(drumFlash) else Modifier,
                )
                Text(
                    text = stringResource(tab.labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) InkOnFill else colors.text,
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 14.sp),
                    modifier = Modifier.padding(horizontal = 9.dp),
                )
            }
        }
    }
}

private val TabShape = RoundedCornerShape(22.dp)
