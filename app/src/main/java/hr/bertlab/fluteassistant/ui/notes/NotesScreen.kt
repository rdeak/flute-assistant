// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.notes

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
import hr.bertlab.fluteassistant.ui.common.ChunkyButton
import hr.bertlab.fluteassistant.ui.common.Emoji
import hr.bertlab.fluteassistant.ui.common.LocalSideways
import hr.bertlab.fluteassistant.ui.common.NoteBubble
import hr.bertlab.fluteassistant.ui.common.OutlineWidth
import hr.bertlab.fluteassistant.ui.common.Stage
import hr.bertlab.fluteassistant.ui.common.fitOrScroll
import hr.bertlab.fluteassistant.ui.common.noteColor
import hr.bertlab.fluteassistant.ui.theme.FluteTheme

/**
 * Stateful entry point: wires the ViewModel to the stateless [NotesContent] and silences
 * the note when the tab goes away mid-feedback (the tuner's microphone would hear it).
 */
@Composable
fun NotesScreen(
    state: NotesUiState,
    viewModel: NotesViewModel,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(Unit) {
        onDispose { viewModel.stopSound() }
    }
    NotesContent(
        state = state,
        onNoteTapped = viewModel::onNoteTapped,
        onPlayAgain = viewModel::playAgain,
        onTogglePracticeNote = viewModel::togglePracticeNote,
        onToggleSound = viewModel::toggleSound,
        modifier = modifier,
    )
}

/**
 * The whole game, top to bottom: one hero row (the streak's flames, the note to find in its
 * letter's colour, and the "Pick" button that opens the note picker; the headline once the
 * round is over; "Which notes?" while choosing), then the stage with the staff and its
 * feedback line, or the round summary, or the picker, whose own big OK button closes it
 * (the Pick button hides meanwhile, so there is one way out), and at the bottom the round's
 * stars. Choosing notes pauses the question in place; it comes back untouched.
 */
@Composable
fun NotesContent(
    state: NotesUiState,
    onNoteTapped: (Int) -> Unit,
    onPlayAgain: () -> Unit,
    onTogglePracticeNote: (Int) -> Unit,
    onToggleSound: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Kept above the branch, so the picker stays open when the phone turns.
    var choosingNotes by rememberSaveable { mutableStateOf(false) }

    // A phone on its side has its own layout ([NotesSideways]): the same screens, arranged for the width.
    if (LocalSideways.current) {
        NotesSideways(
            state = state,
            choosingNotes = choosingNotes,
            onChoose = { choosingNotes = true },
            onChosen = { choosingNotes = false },
            onNoteTapped = onNoteTapped,
            onPlayAgain = onPlayAgain,
            onTogglePracticeNote = onTogglePracticeNote,
            onToggleSound = onToggleSound,
            modifier = modifier,
        )
        return
    }

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
            HeroRow(
                state = state,
                choosingNotes = choosingNotes,
                onChoose = { choosingNotes = true },
            )
            Stage(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when {
                    choosingNotes -> PracticeNotePicker(
                        selected = state.practiceNotes,
                        onToggle = onTogglePracticeNote,
                        soundOn = state.soundOn,
                        onToggleSound = onToggleSound,
                        onDone = { choosingNotes = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                    state.roundOver -> RoundSummary(
                        state = state,
                        onPlayAgain = onPlayAgain,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                    else -> Question(state = state, onNoteTapped = onNoteTapped)
                }
            }
            // The round's stars last, under whatever is on screen: how far the round has come and
            // how many notes are left, read after the answer rather than before it.
            RoundProgress(results = state.results, roundOver = state.roundOver)
        }
    }
}

/** Below this height the tab scrolls, so the staff's places never shrink under a finger's width. */
private val NotesMinHeight = 560.dp

private val HeroRowHeight = 96.dp
private val HeroSideWidth = 116.dp

/**
 * The row above the stage. While asking: the streak's flames at the start (one per note
 * found first time in a row, up to [MAX_FLAMES], nothing at zero), "Find" over the note in
 * its letter's colour in the middle, and the Pick button at the end with a dot in the
 * colour of every chosen letter. Once the round is over the headline replaces the flames
 * and the note; while choosing the row is the picker's title alone.
 */
@Composable
private fun HeroRow(state: NotesUiState, choosingNotes: Boolean, onChoose: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (choosingNotes) Arrangement.Center else Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .height(HeroRowHeight),
    ) {
        when {
            choosingNotes -> {
                Emoji(BACKPACK_EMOJI, fontSize = 32)
                Text(
                    text = stringResource(R.string.choose_notes_title),
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 30.sp),
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
            state.roundOver -> {
                Text(
                    text = stringResource(headlineFor(state.score)),
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp),
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = 18.sp, maxFontSize = 28.sp),
                    modifier = Modifier.weight(1f),
                )
                PickButton(state.practiceNotes, onChoose)
            }
            else -> {
                StreakFlames(state.streak)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.find_note), style = MaterialTheme.typography.labelLarge, color = FluteTheme.colors.mutedText)
                    NoteBubble(midi = state.currentMidi, size = 68.dp, fontSize = 36.sp)
                }
                PickButton(state.practiceNotes, onChoose)
            }
        }
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

/** "🎒 Pick" over a dot in the colour of each letter among the chosen notes. */
@Composable
private fun PickButton(practiceNotes: Set<Int>, onChoose: () -> Unit) {
    val description = stringResource(R.string.choose_notes)
    ChunkyButton(
        onClick = onChoose,
        shape = CircleShape,
        modifier = Modifier
            .padding(bottom = 4.dp)
            .size(width = HeroSideWidth, height = 60.dp)
            .semantics { contentDescription = description },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Emoji(BACKPACK_EMOJI, fontSize = 20)
                Text(stringResource(R.string.pick_notes), style = MaterialTheme.typography.titleMedium)
            }
            LetterDots(practiceNotes)
        }
    }
}

/** The colour of each letter among [practiceNotes], lowest first: one dot of the Pick button each. */
internal fun letterColorsOf(practiceNotes: Set<Int>): List<Color> =
    practiceNotes.sorted().map { noteColor(it) }.distinct()

/**
 * A dot of [size] in the colour of each letter among [practiceNotes], [gap] apart, lowest first: what the Pick
 * button says about the chosen notes without a word. Tagged so a test can find it, as the button's
 * own semantics swallow it.
 */
@Composable
internal fun LetterDots(practiceNotes: Set<Int>, modifier: Modifier = Modifier, size: Dp = 10.dp, gap: Dp = 3.dp) {
    val letterColors = letterColorsOf(practiceNotes)
    Row(
        horizontalArrangement = Arrangement.spacedBy(gap),
        modifier = modifier.testTag("pick_dots"),
    ) {
        letterColors.forEach { color ->
            Box(
                Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.5.dp, FluteTheme.colors.outline, CircleShape),
            )
        }
    }
}

/**
 * The staff, taking all the height the stage has left, and the feedback line; the note
 * above, in the hero row, is the question itself.
 */
@Composable
private fun ColumnScope.Question(state: NotesUiState, onNoteTapped: (Int) -> Unit) {
    // The place under the child's finger while they slide, before they let go.
    var placing by remember { mutableStateOf<Int?>(null) }
    NoteStaff(
        currentMidi = state.currentMidi,
        wrongTaps = state.wrongTaps,
        lastWrongTap = state.lastWrongTap,
        solved = state.solved,
        practiceNotes = state.practiceNotes,
        placing = placing,
        onPlacingChange = { placing = it },
        onNoteTapped = onNoteTapped,
        modifier = Modifier.weight(1f),
    )
    Spacer(Modifier.height(12.dp))
    FeedbackLine(state = state, placing = placing)
}

/**
 * The line under the staff (in the panel beside it, sideways): "That's it!" bounces in, as
 * "Perfect!" does on the tuner, "Slide, let go" while a finger is down, "Try again" after a
 * miss. While waiting the line is empty but keeps its [height], so nothing jumps. A caller short
 * of room has it shrink further ([minFont]) and break between words ([maxLines]), with a [style]
 * of relative line height so that the lines follow the font.
 */
@Composable
internal fun FeedbackLine(
    state: NotesUiState,
    placing: Int?,
    modifier: Modifier = Modifier,
    maxFont: TextUnit = 32.sp,
    minFont: TextUnit = 18.sp,
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
        placing != null -> stringResource(R.string.notes_placing)
        state.wrongTaps.isNotEmpty() -> stringResource(R.string.notes_wrong)
        else -> ""
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

internal const val BACKPACK_EMOJI = "\uD83C\uDF92" // school backpack
