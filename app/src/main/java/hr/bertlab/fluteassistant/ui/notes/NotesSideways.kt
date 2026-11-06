// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.ui.common.AppIcons
import hr.bertlab.fluteassistant.ui.common.ChunkyButton
import hr.bertlab.fluteassistant.ui.common.Emoji
import hr.bertlab.fluteassistant.ui.common.NoteBubble
import hr.bertlab.fluteassistant.ui.common.SidewaysActionButton
import hr.bertlab.fluteassistant.ui.common.SidewaysGeometry
import hr.bertlab.fluteassistant.ui.common.SidewaysPage
import hr.bertlab.fluteassistant.ui.common.Stage
import hr.bertlab.fluteassistant.ui.theme.ActionGo
import hr.bertlab.fluteassistant.ui.theme.FluteTheme

/*
 * The notes game on a phone on its side (spec `2026-11-05-landscape-design.md`, "Notes game";
 * mockups `hand.html` and `more.html`). The phone is in the hand here, not on a stand. Three
 * screens, each in the shape every tab has: where am I on the left, the thing itself in the middle,
 * what to do in the right column. `NoteStaff` is the same one upright draws and takes the card's
 * full height; a place is the staff's height over the visible places plus two, which the spec's
 * table gives (26 dp at 411 tall with the usual notes, 23 at 360), and this tab never scrolls
 * at 360 tall or more, as scrolling would fight the slide.
 */

/** The panel left of the staff: Find, the note's bubble, the streak and the feedback line (the mockup's 124). */
private val PanelWidth = 124.dp

/** The streak's slot, as tall as the pill, so the bubble does not move when the first flame comes. */
private val StreakSlotHeight = 52.dp

/** The feedback line's room in the panel: two lines of its 22 sp, so that nothing moves when it comes and goes. */
private val FeedbackHeight = 64.dp

/** The staff's card is padded 10 dp, less than the 16 of the others: the staff needs every dp of height. */
private val StaffCardPadding = 10.dp

/**
 * The whole game sideways: the picker while [choosingNotes] (`NotesContent` keeps that, so it stays
 * open when the phone turns), otherwise the summary once the round is over, otherwise the question.
 */
@Composable
internal fun NotesSideways(
    state: NotesUiState,
    choosingNotes: Boolean,
    onChoose: () -> Unit,
    onChosen: () -> Unit,
    onNoteTapped: (Int) -> Unit,
    onPlayAgain: () -> Unit,
    onTogglePracticeNote: (Int) -> Unit,
    onToggleSound: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        choosingNotes -> PickerSideways(state, onTogglePracticeNote, onToggleSound, onChosen, modifier)
        state.roundOver -> SummarySideways(state, onChoose, onPlayAgain, modifier)
        else -> QuestionSideways(state, onChoose, onNoteTapped, modifier)
    }
}

/**
 * The panel, the staff's card and the column: Find and the note on the left, where a right hand will
 * not cover them, the staff in the middle with its note column in the centre and room on the right
 * for the sliding finger, and the ten places over Pick on the right.
 */
@Composable
private fun QuestionSideways(state: NotesUiState, onChoose: () -> Unit, onNoteTapped: (Int) -> Unit, modifier: Modifier) {
    // The place under the child's finger while they slide, before they let go.
    var placing by remember { mutableStateOf<Int?>(null) }
    SidewaysPage(
        modifier = modifier,
        column = {
            RoundProgress(results = state.results, roundOver = state.roundOver, twoColumns = true)
            SidewaysPickButton(state.practiceNotes, onChoose)
        },
    ) { page ->
        Row(modifier = page, horizontalArrangement = Arrangement.spacedBy(SidewaysGeometry.MARGIN.dp)) {
            QuestionPanel(state, placing, Modifier.width(PanelWidth).fillMaxHeight())
            Stage(modifier = Modifier.weight(1f).fillMaxHeight(), padding = StaffCardPadding) {
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
            }
        }
    }
}

/** On the page, not in a card: Find over the 108 dp bubble, the streak, and the feedback line. */
@Composable
private fun QuestionPanel(state: NotesUiState, placing: Int?, modifier: Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.find_note),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 22.sp, lineHeight = 1.25.em),
            color = FluteTheme.colors.mutedText,
            textAlign = TextAlign.Center,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 22.sp),
            modifier = Modifier.fillMaxWidth(),
        )
        NoteBubble(midi = state.currentMidi, size = 108.dp, fontSize = 56.sp)
        StreakFlames(state.streak, Modifier.height(StreakSlotHeight))
        FeedbackLine(
            state = state,
            placing = placing,
            maxFont = 22.sp,
            minFont = 12.sp,
            maxLines = 2,
            height = FeedbackHeight,
            style = MaterialTheme.typography.headlineMedium.copy(lineHeight = 1.2.em),
        )
    }
}

/**
 * Pick in the column: the backpack and the word over a dot for each chosen letter, as upright's
 * Pick button but 116 wide and 84 tall like the other plain buttons. The backpack is sized in dp
 * so large text leaves the word its room. The dots are 11 dp, or fewer dp where seven of them would
 * come within [SidewaysGeometry.pickDotSize]'s 9 dp of the outline (all fifteen notes on the 100 dp
 * column), sized from the button's own width, which is the column's. The word keeps 6 dp from the
 * outline and shrinks to fit the rest.
 */
@Composable
private fun SidewaysPickButton(practiceNotes: Set<Int>, onChoose: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.choose_notes)
    val backpack = with(LocalDensity.current) { 24.dp.toSp().value.toInt() }
    // The word's floor is in dp, as a floor in sp doubles with the text and the 100 dp column then has no room for "Odaberi" at the largest size.
    val wordFloor = with(LocalDensity.current) { 9.dp.toSp() }
    ChunkyButton(
        onClick = onChoose,
        shape = RoundedCornerShape(30.dp),
        depth = 5.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp)
            .semantics { contentDescription = description },
    ) {
        BoxWithConstraints {
            val dot = SidewaysGeometry.pickDotSize(maxWidth.value, letterColorsOf(practiceNotes).size)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Emoji(BACKPACK_EMOJI, fontSize = backpack)
                    Text(
                        text = stringResource(R.string.pick_notes),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 22.sp, lineHeight = 1.25.em),
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(minFontSize = wordFloor, maxFontSize = 22.sp),
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                LetterDots(practiceNotes, size = dot.dp, gap = SidewaysGeometry.PICK_DOT_GAP.dp)
            }
        }
    }
}

/**
 * The title and the same rows as upright in the card, with bigger keys that narrow to fit the card
 * on the smaller phones; Sound at the top of the column and OK low. The ten places hide: there is
 * nothing to count while choosing.
 */
@Composable
private fun PickerSideways(
    state: NotesUiState,
    onTogglePracticeNote: (Int) -> Unit,
    onToggleSound: () -> Unit,
    onChosen: () -> Unit,
    modifier: Modifier,
) {
    SidewaysPage(
        modifier = modifier,
        column = {
            SoundTile(soundOn = state.soundOn, onToggleSound = onToggleSound)
            SidewaysActionButton(
                icon = AppIcons.Done,
                text = stringResource(R.string.choose_notes_done),
                color = ActionGo,
                onClick = onChosen,
            )
        },
    ) { page ->
        Stage(modifier = page, verticalArrangement = Arrangement.Center) {
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                val keyWidth = minOf(PickerKeyWidth, (maxWidth - PickerKeyGap * 6) / 7)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Emoji(BACKPACK_EMOJI, fontSize = 32)
                        // One line that shrinks to the room the backpack leaves, so that at the largest text it does not wrap and take the note rows' height.
                        Text(
                            text = stringResource(R.string.choose_notes_title),
                            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp, lineHeight = 1.2.em),
                            maxLines = 1,
                            autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 28.sp),
                            modifier = Modifier.padding(start = 10.dp),
                        )
                    }
                    NoteRows(
                        selected = state.practiceNotes,
                        onToggle = onTogglePracticeNote,
                        keyWidth = keyWidth,
                        keyHeight = PickerKeyHeight,
                        gap = PickerKeyGap,
                    )
                }
            }
        }
    }
}

private val PickerKeyWidth = 78.dp
private val PickerKeyHeight = 62.dp
private val PickerKeyGap = 10.dp

/**
 * The trophy beside the headline, the ten stars in one row and the three tiles, all in the card
 * and still no numbers; Pick over Again in the column. The trophy and the stars are sized from the
 * card's width, so the narrowest phone still holds them, and from dp, so large text does not grow
 * them out of the card.
 */
@Composable
private fun SummarySideways(state: NotesUiState, onChoose: () -> Unit, onPlayAgain: () -> Unit, modifier: Modifier) {
    SidewaysPage(
        modifier = modifier,
        column = {
            SidewaysPickButton(state.practiceNotes, onChoose)
            SidewaysActionButton(
                icon = AppIcons.Again,
                text = stringResource(R.string.play_again),
                color = ActionGo,
                onClick = onPlayAgain,
            )
        },
    ) { page ->
        Stage(modifier = page, verticalArrangement = Arrangement.Center) {
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val density = LocalDensity.current
                fun fontSizeOf(size: Dp): Int = with(density) { size.toSp().value.toInt() }
                val trophy = (maxWidth * 0.2f).coerceIn(TrophyMin, TrophyMax)
                val gap = if (maxWidth < 500.dp) 16.dp else 26.dp
                // What the phone's emoji font draws, as it is not the same everywhere: the row is sized from it, not from a guess.
                val room = maxWidth - trophy * emojiWidthShare(TROPHY_EMOJI) - gap
                val star = minOf(StarMax, room / (emojiWidthShare(STAR_EMOJI) * NotesUiState.ROUND_LENGTH))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    Emoji(TROPHY_EMOJI, fontSize = fontSizeOf(trophy), modifier = Modifier.trophyHop())
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = stringResource(headlineFor(state.score)),
                            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 32.sp, lineHeight = 1.2.em),
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 32.sp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        // Unbounded, so a row too wide for the card would show in its size rather than be squeezed.
                        SummaryStars(state, fontSize = fontSizeOf(star), modifier = Modifier.wrapContentWidth(unbounded = true))
                        SummaryTiles(state, compact = true)
                    }
                }
            }
        }
    }
}

private val TrophyMin = 72.dp
private val TrophyMax = 130.dp
private val StarMax = 27.dp

/** The size an emoji is measured at to find how wide it is for its size; a width follows the size. */
private val EmojiMeasureSize = 100.dp

/**
 * How wide [emoji] comes out for each unit of the size it is drawn at, as [Emoji] draws it, measured
 * on this phone: Google's emoji are about 1.2 times as wide as they are high and Samsung's are not.
 */
@Composable
private fun emojiWidthShare(emoji: String): Float {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.titleLarge
    return remember(emoji, measurer, style, density) {
        val size = with(density) { EmojiMeasureSize.toSp() }
        measurer.measure(emoji, style.copy(fontSize = size, lineHeight = size * 1.2f)).size.width / with(density) { EmojiMeasureSize.toPx() }
    }
}
