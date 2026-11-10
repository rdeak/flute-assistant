// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.notes

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.domain.notes.NoteProgress
import hr.bertlab.fluteassistant.domain.notes.NotesGame
import hr.bertlab.fluteassistant.domain.notes.NotesRound
import hr.bertlab.fluteassistant.ui.common.ChunkyButton
import hr.bertlab.fluteassistant.ui.common.Emoji
import hr.bertlab.fluteassistant.ui.common.LocalSideways
import hr.bertlab.fluteassistant.ui.common.ModeChoice
import hr.bertlab.fluteassistant.ui.common.NoteBubble
import hr.bertlab.fluteassistant.ui.common.OutlineWidth
import hr.bertlab.fluteassistant.ui.common.Stage
import hr.bertlab.fluteassistant.ui.common.fitOrScroll
import hr.bertlab.fluteassistant.ui.common.labelRes
import hr.bertlab.fluteassistant.ui.common.noteColor
import hr.bertlab.fluteassistant.ui.common.tintOf
import hr.bertlab.fluteassistant.ui.theme.FluteTheme

/**
 * Stateful entry point: wires the ViewModel to the stateless [NotesContent] and tells it when the
 * screen goes away ([NotesViewModel.stopSound]): what sounds stops (the tuner's microphone would hear
 * it), a phrase cut while it played back as an answer's feedback moves on, and one cut while being
 * heard plays again when the screen comes back. [NotesContent] asks for a Hear phrase as it comes on
 * screen (the ViewModel plays each once), so nothing sounds while the tab is away or the picker is open.
 */
@Composable
fun NotesScreen(
    state: NotesRound,
    viewModel: NotesViewModel,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(Unit) {
        onDispose { viewModel.stopSound() }
    }
    val actions = remember(viewModel) {
        NotesActions(
            onNoteTapped = viewModel::onNoteTapped,
            onPlayAgain = viewModel::resetRound,
            onTogglePracticeNote = viewModel::togglePracticeNote,
            onToggleSound = viewModel::toggleSound,
            onSetGame = viewModel::setGame,
            onHearAgain = viewModel::hearAgain,
            onPhraseShown = viewModel::onPhraseShown,
        )
    }
    NotesContent(state = state, actions = actions, modifier = modifier)
}

/**
 * Everything the notes tab can ask for, as one object, so [NotesContent] takes its state and this.
 * Each action defaults to doing nothing, so a test names only the ones it looks at.
 */
class NotesActions(
    /** A place on the staff was tapped: the note there. */
    val onNoteTapped: (Int) -> Unit = {},
    /** Again, on the round's summary. */
    val onPlayAgain: () -> Unit = {},
    /** A note of the picker, and its Sound switch. */
    val onTogglePracticeNote: (Int) -> Unit = {},
    val onToggleSound: () -> Unit = {},
    /** One of the two game pills. */
    val onSetGame: (NotesGame) -> Unit = {},
    /** Hear's speaker bubble. */
    val onHearAgain: () -> Unit = {},
    /** A Hear phrase came on screen and wants playing (the view model plays each once). */
    val onPhraseShown: () -> Unit = {},
)

/**
 * The whole game, top to bottom: the two game pills, then one hero row (the streak's flames,
 * the note to find in its letter's colour or, in Hear, the speaker bubble that plays the phrase
 * again, and the "Pick" button that opens the note picker; the headline once the round is over;
 * "Which notes?" while choosing), then the stage with the staff and its feedback line, or the round
 * summary, or the picker, whose own big OK button closes it (the Pick button hides meanwhile, so
 * there is one way out on screen; the phone's Back closes it too), and at the bottom the round's stars. The pills stay on all three screens.
 * Choosing notes pauses the question in place; it comes back untouched. [NotesActions.onPhraseShown] asks for
 * Hear's phrase to be played while the staff is on screen: as a new phrase comes, and as the picker
 * closes over one drawn under it.
 */
@Composable
fun NotesContent(
    state: NotesRound,
    actions: NotesActions,
    modifier: Modifier = Modifier,
) {
    // Kept above the branch, so the picker stays open when the phone turns.
    var choosingNotes by rememberSaveable { mutableStateOf(false) }

    // The phone's Back closes the picker as its OK does; with the staff or the summary on screen it is the phone's.
    BackHandler(enabled = choosingNotes) { choosingNotes = false }

    // A phrase plays as it comes on screen, never under the picker: a note switched there, or the Hear pill, draws one the
    // child cannot see, and it plays when OK closes the picker. Above the branch too, so a turn neither asks again nor loses
    // the ask; the ViewModel plays each phrase once, so asking again for one already heard plays nothing.
    LaunchedEffect(state.phraseId, state.game, choosingNotes) {
        if (!choosingNotes) actions.onPhraseShown()
    }

    // A phone on its side has its own layout ([NotesSideways]): the same screens, arranged for the width.
    if (LocalSideways.current) {
        NotesSideways(
            state = state,
            choosingNotes = choosingNotes,
            onChoose = { choosingNotes = true },
            onChosen = { choosingNotes = false },
            actions = actions,
            modifier = modifier,
        )
        return
    }

    // The place under the child's finger while they slide, before they let go: the staff draws it, the
    // feedback line names it and the speaker bubble waits for it (a finger on the staff is answering).
    var placing by remember { mutableStateOf<Int?>(null) }

    // The staff takes whatever height is left, so the tab fits an ordinary phone without
    // scrolling; only a short screen (or large text) scrolls, keeping the places big enough.
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fitOrScroll(fits = maxHeight >= NotesMinHeight, minHeight = NotesMinHeight)
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GamePills(game = state.game, onSetGame = actions.onSetGame)
            HeroRow(
                state = state,
                choosingNotes = choosingNotes,
                placing = placing,
                onChoose = { choosingNotes = true },
                onHearAgain = actions.onHearAgain,
            )
            Stage(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when {
                    choosingNotes -> PracticeNotePicker(
                        selected = state.practiceNotes,
                        onToggle = actions.onTogglePracticeNote,
                        progress = state.progress,
                        soundOn = state.soundOn,
                        onToggleSound = actions.onToggleSound,
                        onDone = { choosingNotes = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                    state.roundOver -> RoundSummary(
                        state = state,
                        onPlayAgain = actions.onPlayAgain,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                    else -> Question(
                        state = state,
                        placing = placing,
                        onPlacingChange = { placing = it },
                        onNoteTapped = actions.onNoteTapped,
                    )
                }
            }
            // The round's stars last, under whatever is on screen: how far the round has come and
            // how many notes are left, read after the answer rather than before it.
            RoundProgress(results = state.results, roundOver = state.roundOver)
        }
    }
}

/**
 * Below this height the tab scrolls, so the staff's places never shrink under a finger's width.
 * The game pills (64 dp and 5 under them) and the column's gap (14) came on top of the old 560,
 * and the shorter hero row took 20 off.
 */
private val NotesMinHeight = 623.dp

private val HeroRowHeight = 76.dp
private val HeroSideWidth = 116.dp

/** The floor of the Pick word, in dp as a floor in sp doubles at the largest text size. */
private val PickWordFloor = 8.dp

/** The most the Pick word is tall: past it a short word, which has the width to grow, pushes the dots into the outline. */
private val PickWordHeight = 36.dp

/**
 * The least the summary's headline shrinks to, so the longest one stays whole beside Pick at twice the text on a
 * 360 dp phone: "💪 Keep practising!" needs a font of at most about 22.7 dp in its 204 dp there, and 11 sp is 22 dp at
 * twice the text (18 cut it on a 411 dp phone, 16 on a 360 dp one). A cut word is worse than a small one.
 */
private val HeadlineFloor = 11.sp

/**
 * The row above the stage, [HeroRowHeight] tall on all three screens. While asking: the
 * streak's flames at the start (one per note found first time in a row, up to [MAX_FLAMES],
 * nothing at zero), the note in its letter's colour in the middle (the speaker bubble in Hear,
 * which would give the answer away; it waits while a finger is [placing] a note), and the Pick
 * button at the end with a dot in the colour of every chosen letter. Once the round is over the
 * headline replaces the flames and the note; while choosing the row is the picker's title alone.
 * Both words shrink to fit one line, so that large text neither wraps nor outgrows the row.
 */
@Composable
private fun HeroRow(
    state: NotesRound,
    choosingNotes: Boolean,
    placing: Int?,
    onChoose: () -> Unit,
    onHearAgain: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (choosingNotes) Arrangement.Center else Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .height(HeroRowHeight),
    ) {
        when {
            choosingNotes -> {
                // The backpack is a picture, in dp like the Pick button's; the title is the row's words.
                Emoji(BACKPACK_EMOJI, fontSize = with(LocalDensity.current) { 32.dp.toSp().value.toInt() })
                Text(
                    text = stringResource(R.string.choose_notes_title),
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = 30.sp),
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
            state.roundOver -> {
                Text(
                    text = stringResource(headlineFor(state.score)),
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = HeadlineFloor, maxFontSize = 28.sp),
                    modifier = Modifier.weight(1f),
                )
                PickButton(state.practiceNotes, state.progress, onChoose)
            }
            else -> {
                StreakFlames(state.streak)
                if (state.hearing) {
                    HearAgainBubble(size = 68.dp, fontSize = 34, enabled = placing == null, onClick = onHearAgain)
                } else {
                    NoteBubble(midi = state.currentMidi, size = 68.dp, fontSize = 36.sp)
                }
                PickButton(state.practiceNotes, state.progress, onChoose)
            }
        }
    }
}

/**
 * The two games as the tuner's two modes are shown, pills on top of the tab ([ModeChoice]), the
 * chosen one yellow. The emoji is in each word (`find_note`, `hear_game`), so none is drawn apart.
 */
@Composable
private fun GamePills(game: NotesGame, onSetGame: (NotesGame) -> Unit) {
    ModeChoice(
        entries = NotesGame.entries,
        selected = game,
        onSelect = onSetGame,
        groupLabel = stringResource(R.string.notes_game),
        emojiOf = { "" },
        labelResOf = { it.labelRes() },
    )
}

/**
 * Hear's stand-in for the note in the hero row (and the sideways panel): a round raised button with a speaker, which plays
 * the phrase again (it would give the answer away to show the note). A [size] dp circle with a
 * [fontSize] dp speaker, both pictures that keep their size at large text. [enabled] is false while
 * a finger is placing a note, as that is answering and not asking to hear.
 */
@Composable
internal fun HearAgainBubble(size: Dp, fontSize: Int, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.hear_again)
    val speaker = with(LocalDensity.current) { fontSize.dp.toSp().value.toInt() }
    ChunkyButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = FluteTheme.colors.raised,
        depth = 6.dp,
        modifier = modifier
            .size(size)
            .semantics { contentDescription = description },
    ) {
        Emoji(SPEAKER_EMOJI, fontSize = speaker)
    }
}

@Composable
internal fun StreakFlames(streak: Int, modifier: Modifier = Modifier) {
    val colors = FluteTheme.colors
    val description = stringResource(R.string.streak_description, streak)
    val density = LocalDensity.current
    Box(contentAlignment = Alignment.Center, modifier = modifier.width(HeroSideWidth)) {
        if (streak > 0) {
            Text(
                text = FLAME_EMOJI.repeat(streak.coerceAtMost(MAX_FLAMES)),
                // In dp: they are pictures, and five at a larger text size would not fit the pill.
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = with(density) { 17.dp.toSp() },
                    letterSpacing = with(density) { (-2).dp.toSp() },
                ),
                maxLines = 1,
                modifier = Modifier
                    .height(52.dp)
                    .clip(CircleShape)
                    .background(colors.card)
                    .border(OutlineWidth, colors.border, CircleShape)
                    .wrapContentHeight()
                    .padding(horizontal = 12.dp)
                    .clearAndSetSemantics { contentDescription = description },
            )
        }
    }
}

/**
 * "🎒 Pick" over a dot in the colour of each letter among the chosen notes, solid for a letter whose
 * chosen notes are all known ([progress]) and pale otherwise. As on the sideways button,
 * the backpack is in dp, as a picture, and the word keeps 6 dp from the outline and shrinks to fit the
 * rest, so large text neither splits "Odaberi" nor pushes the row off the button.
 */
@Composable
private fun PickButton(practiceNotes: Set<Int>, progress: NoteProgress, onChoose: () -> Unit) {
    val description = stringResource(R.string.choose_notes)
    val wordStyle = MaterialTheme.typography.titleMedium
    val backpack = with(LocalDensity.current) { 20.dp.toSp().value.toInt() }
    val wordFloor = with(LocalDensity.current) { PickWordFloor.toSp() }
    ChunkyButton(
        onClick = onChoose,
        shape = CircleShape,
        modifier = Modifier
            .padding(bottom = 4.dp)
            .size(width = HeroSideWidth, height = 60.dp)
            .semantics { contentDescription = description },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Emoji(BACKPACK_EMOJI, fontSize = backpack, modifier = Modifier.testTag("pick_backpack"))
                Text(
                    stringResource(R.string.pick_notes),
                    style = wordStyle,
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = wordFloor, maxFontSize = wordStyle.fontSize),
                    modifier = Modifier.weight(1f, fill = false).heightIn(max = PickWordHeight),
                )
            }
            LetterDots(practiceNotes, progress)
        }
    }
}

/**
 * The colour of each letter among [practiceNotes], lowest first: one dot of the Pick button each, with
 * whether the letter is known, which is when every chosen note of it is ([NoteProgress.isKnown]). C4 and
 * C5 are one letter and one dot.
 */
internal fun letterDotColors(practiceNotes: Set<Int>, progress: NoteProgress): List<Pair<Color, Boolean>> =
    practiceNotes.sorted().groupBy { noteColor(it) }.map { (color, notes) -> color to notes.all(progress::isKnown) }

/**
 * A dot of [size] for each letter among [practiceNotes], [gap] apart, lowest first: what the Pick
 * button says about the chosen notes without a word. A dot is the letter's colour once every chosen
 * note of it is known ([progress]) and its pale tint before. Tagged so a test can find it, as the button's
 * own semantics swallow it.
 */
@Composable
internal fun LetterDots(
    practiceNotes: Set<Int>,
    progress: NoteProgress,
    modifier: Modifier = Modifier,
    size: Dp = 10.dp,
    gap: Dp = 3.dp,
) {
    val dots = letterDotColors(practiceNotes, progress)
    Row(
        horizontalArrangement = Arrangement.spacedBy(gap),
        modifier = modifier.testTag("pick_dots"),
    ) {
        dots.forEach { (color, known) ->
            Box(
                Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(if (known) color else tintOf(color))
                    .border(1.5.dp, FluteTheme.colors.outline, CircleShape),
            )
        }
    }
}

/**
 * The staff, taking all the height the stage has left, with the streak's burst over it every fifth
 * flame ([StreakBurst]), and the feedback line; the note
 * above, in the hero row, is the question itself. The place under the child's finger
 * ([placing]) is kept by the caller, as the hero row's speaker bubble waits on it too.
 */
@Composable
private fun ColumnScope.Question(
    state: NotesRound,
    placing: Int?,
    onPlacingChange: (Int?) -> Unit,
    onNoteTapped: (Int) -> Unit,
) {
    // The place belongs to the question: were it to leave (a second finger on Pick, the round over)
    // with a finger down, no lift would clear it, and it would come back with the next question.
    DisposableEffect(Unit) {
        onDispose { onPlacingChange(null) }
    }
    // The burst is drawn over the staff in the same box and takes no touch, so the slide goes through to the staff.
    Box(Modifier.fillMaxWidth().weight(1f)) {
        NoteStaff(
            state = state,
            placing = placing,
            onPlacingChange = onPlacingChange,
            onNoteTapped = onNoteTapped,
            modifier = Modifier.fillMaxSize(),
        )
        StreakBurst(state.streak, Modifier.matchParentSize())
    }
    Spacer(Modifier.height(12.dp))
    FeedbackLine(state = state, placing = placing)
}

/**
 * The line under the staff (in the panel beside it, sideways): "That's it!" bounces in, as
 * "Perfect!" does on the tuner, "Slide, let go" while a finger is down, "Try again" after a
 * miss, and "Listen" while Hear's phrase sounds, over a finger's line and a miss's as the staff
 * takes no answer then. "That's it!" goes first: after a phrase's last note the phrase plays back
 * as the answer's feedback, and the line says so rather than "Listen" (a fresh phrase is not solved).
 * Waiting in Hear, after all of those, it asks "Find the next note" (the slot's ❓ is its symbol; the
 * first child test, 2026-11-09, found the staff said nothing of what to do). Waiting in Find it says
 * "Slide, let go" already, how to answer, before the first touch (since 2026-11-10: it was empty there,
 * the same nothing Hear had). It keeps its [height] always, so nothing jumps. The line shrinks to fit down to
 * [minFont], [FeedbackFloor] by default so that the prompt keeps to one line at twice the text on a 360 dp
 * phone, and a caller short of room breaks it between words ([maxLines]), with a [style]
 * of relative line height so that the lines follow the font.
 */
@Composable
internal fun FeedbackLine(
    state: NotesRound,
    placing: Int?,
    modifier: Modifier = Modifier,
    maxFont: TextUnit = 32.sp,
    minFont: TextUnit = FeedbackFloor,
    maxLines: Int = 1,
    height: Dp = FeedbackLineHeight,
    style: TextStyle = MaterialTheme.typography.headlineMedium,
) {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(state.solved) {
        if (state.solved) {
            scale.snapTo(1.3f)
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        }
    }
    val feedback = when {
        state.solved -> stringResource(R.string.notes_correct)
        state.phrasePlaying -> stringResource(R.string.notes_listening)
        placing != null -> stringResource(R.string.notes_placing)
        state.wrongTaps.isNotEmpty() -> stringResource(R.string.notes_wrong)
        state.hearing -> stringResource(R.string.hear_prompt)
        else -> stringResource(R.string.notes_placing)
    }
    Text(
        text = feedback,
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

/** The feedback line's reserved height: one line of the 32 sp headline. */
private val FeedbackLineHeight = 40.dp

/**
 * How small the upright feedback line may shrink to keep to its one line. 11 sp is its own number: it equals
 * [HeadlineFloor] today, but the two floors answer different lines and must not follow each other by value. At
 * twice the text on a 360 dp phone "❓ Find the next note" fits only from about 16 sp down (it was 18, and the end of
 * the line was cut); a floor only matters where the text would not fit, so a low one costs nothing where it does. A
 * word cut short is worse than a small one.
 */
private val FeedbackFloor = 11.sp

internal const val BACKPACK_EMOJI = "\uD83C\uDF92" // school backpack

private const val SPEAKER_EMOJI = "\uD83D\uDD0A" // speaker with sound waves
