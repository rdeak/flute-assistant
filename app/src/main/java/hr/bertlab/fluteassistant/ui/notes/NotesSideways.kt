// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.selection.selectableGroup
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
import hr.bertlab.fluteassistant.domain.notes.NoteProgress
import hr.bertlab.fluteassistant.domain.notes.NotesGame
import hr.bertlab.fluteassistant.domain.notes.NotesRound
import hr.bertlab.fluteassistant.ui.common.AppIcons
import hr.bertlab.fluteassistant.ui.common.ChunkyButton
import hr.bertlab.fluteassistant.ui.common.Emoji
import hr.bertlab.fluteassistant.ui.common.NoteBubble
import hr.bertlab.fluteassistant.ui.common.STAR_EMOJI
import hr.bertlab.fluteassistant.ui.common.SidewaysActionButton
import hr.bertlab.fluteassistant.ui.common.SidewaysGeometry
import hr.bertlab.fluteassistant.ui.common.SidewaysPage
import hr.bertlab.fluteassistant.ui.common.SidewaysPlainButton
import hr.bertlab.fluteassistant.ui.common.Stage
import hr.bertlab.fluteassistant.ui.common.labelRes
import hr.bertlab.fluteassistant.ui.theme.ActionGo

/*
 * The notes game on a phone on its side (spec `2026-11-05-landscape-design.md`, "Notes game";
 * mockups `hand.html` and `more.html`; the two games and the keys, spec
 * `2026-11-07-notes-hear-and-sticker-book-design.md`, "Sideways"). The phone is in the hand here,
 * not on a stand. Three screens, each in the shape every tab has: where am I on the left, the thing
 * itself in the middle, what to do in the right column, which the two game pills head on all three:
 * the question (the bubble in a panel, the staff, the places over Pick), the picker (Sound, OK low)
 * and the summary (the trophy, stars, tiles and keys; Pick, Again low). `NoteStaff` is the same one
 * upright draws, phrase and all, and takes the card's full height; a place is the staff's height over
 * the visible places plus two, which the spec's table gives (26 dp at 411 tall with the usual notes,
 * 23 at 360), and this tab never scrolls at 360 tall or more, as scrolling would fight the slide.
 */

/** The panel left of the staff: the bubble, the streak and the feedback line (the mockup's 124). */
private val PanelWidth = 124.dp

/**
 * The game pills: 44 dp tall and 8 apart, or 30 and 6 on a short phone. At 360 tall the column is 330 dp
 * inside, and the pills, the ten places (168) and Pick (84) left it 4 dp short at 38: Pick was squeezed and
 * Hear's shadow lay on the places. At 30 there are 6 dp between them, and Hear's 5 dp shadow clears the places.
 */
private val PillHeight = 44.dp
private val PillHeightShort = 30.dp
private val PillGap = 8.dp
private val PillGapShort = 6.dp

/** The streak's slot, as tall as the pill, so the bubble does not move when the first flame comes. */
private val StreakSlotHeight = 52.dp

/** The feedback line's room in the panel: two lines of its 22 sp, so that nothing moves when it comes and goes. */
private val FeedbackHeight = 64.dp

/**
 * How small the panel's feedback line may shrink in its two lines (it was 12 sp). At twice the text the 124 dp panel
 * holds "❓ Find the next note" from about 11 sp down and "❓ Nađi sljedeću notu" from about 10, where the words
 * broke inside themselves or ran past the second line; 9 is the least that is still a legible floor, and a floor only
 * matters where the text would not fit.
 */
private val PanelFeedbackFloor = 9.sp

/** The staff's card is padded 10 dp, less than the 16 of the others: the staff needs every dp of height. */
private val StaffCardPadding = 10.dp

/**
 * The whole game sideways: the picker while [choosingNotes] (`NotesContent` keeps that, so it stays
 * open when the phone turns), otherwise the summary once the round is over, otherwise the question.
 * The window's height says whether the column is short, which sizes the game pills on all three.
 */
@Composable
internal fun NotesSideways(
    state: NotesRound,
    choosingNotes: Boolean,
    onChoose: () -> Unit,
    onChosen: () -> Unit,
    actions: NotesActions,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val short = SidewaysGeometry.isShort(maxHeight.value)
        val pills: @Composable () -> Unit = { GamePillsSideways(state.game, actions.onSetGame, short) }
        when {
            choosingNotes -> PickerSideways(state, pills, actions.onTogglePracticeNote, actions.onToggleSound, onChosen)
            state.roundOver -> SummarySideways(state, pills, onChoose, actions.onPlayAgain)
            else -> QuestionSideways(state, pills, onChoose, actions.onNoteTapped, actions.onHearAgain)
        }
    }
}

/**
 * The two games stacked at the top of the column, as the tuner's modes are, the chosen one yellow. The
 * emoji is in each word (`find_note`, `hear_game`), so the button draws the word alone. Lower on a
 * [short] phone, where the column has no more room ([PillHeightShort]).
 */
@Composable
private fun GamePillsSideways(game: NotesGame, onSetGame: (NotesGame) -> Unit, short: Boolean) {
    val groupLabel = stringResource(R.string.notes_game)
    Column(
        verticalArrangement = Arrangement.spacedBy(if (short) PillGapShort else PillGap),
        modifier = Modifier
            .selectableGroup()
            .semantics { contentDescription = groupLabel },
    ) {
        NotesGame.entries.forEach { entry ->
            SidewaysPlainButton(
                emoji = null,
                text = stringResource(entry.labelRes()),
                onClick = { onSetGame(entry) },
                selected = entry == game,
                height = if (short) PillHeightShort else PillHeight,
            )
        }
    }
}

/**
 * The panel, the staff's card and the column: the bubble on the left, where a right hand will not
 * cover it, the staff in the middle with its note column (or the phrase's columns) in the centre and
 * room on the right for the sliding finger, and the pills over the ten places over Pick on the right.
 */
@Composable
private fun QuestionSideways(
    state: NotesRound,
    pills: @Composable () -> Unit,
    onChoose: () -> Unit,
    onNoteTapped: (Int) -> Unit,
    onHearAgain: () -> Unit,
) {
    // The place under the child's finger while they slide, before they let go; the speaker waits for it.
    var placing by remember { mutableStateOf<Int?>(null) }
    SidewaysPage(
        column = {
            pills()
            RoundProgress(results = state.results, roundOver = state.roundOver, twoColumns = true)
            SidewaysPickButton(state.practiceNotes, state.progress, onChoose)
        },
    ) { page ->
        Row(modifier = page, horizontalArrangement = Arrangement.spacedBy(SidewaysGeometry.MARGIN.dp)) {
            QuestionPanel(state, placing, onHearAgain, Modifier.width(PanelWidth).fillMaxHeight())
            Stage(modifier = Modifier.weight(1f).fillMaxHeight(), padding = StaffCardPadding) {
                // As upright: the burst is drawn over the staff in the same box and takes no touch, so the slide goes through.
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    NoteStaff(
                        state = state,
                        placing = placing,
                        onPlacingChange = { placing = it },
                        onNoteTapped = onNoteTapped,
                        modifier = Modifier.fillMaxSize(),
                    )
                    StreakBurst(state.streak, Modifier.matchParentSize())
                }
            }
        }
    }
}

/**
 * On the page, not in a card: the 108 dp bubble, the streak and the feedback line. The bubble is the
 * note in Find and, in Hear, the speaker that plays the phrase again, which waits while a finger is
 * [placing] a note, as that is answering.
 */
@Composable
private fun QuestionPanel(state: NotesRound, placing: Int?, onHearAgain: () -> Unit, modifier: Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        modifier = modifier,
    ) {
        if (state.hearing) {
            HearAgainBubble(size = 108.dp, fontSize = 52, enabled = placing == null, onClick = onHearAgain)
        } else {
            NoteBubble(midi = state.currentMidi, size = 108.dp, fontSize = 56.sp)
        }
        StreakFlames(state.streak, Modifier.height(StreakSlotHeight))
        FeedbackLine(
            state = state,
            placing = placing,
            maxFont = 22.sp,
            minFont = PanelFeedbackFloor,
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
private fun SidewaysPickButton(
    practiceNotes: Set<Int>,
    progress: NoteProgress,
    onChoose: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            val dot = SidewaysGeometry.pickDotSize(maxWidth.value, letterDotColors(practiceNotes, progress).size)
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
                LetterDots(practiceNotes, progress, size = dot.dp, gap = SidewaysGeometry.PICK_DOT_GAP.dp)
            }
        }
    }
}

/**
 * The title and the same rows as upright in the card, with bigger keys that narrow to fit the card
 * on the smaller phones; the pills at the top of the column, Sound under them and OK low. The ten
 * places hide: there is nothing to count while choosing.
 */
@Composable
private fun PickerSideways(
    state: NotesRound,
    pills: @Composable () -> Unit,
    onTogglePracticeNote: (Int) -> Unit,
    onToggleSound: () -> Unit,
    onChosen: () -> Unit,
) {
    SidewaysPage(
        column = {
            pills()
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
                        progress = state.progress,
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
 * The trophy beside the headline, and under the headline the keys of the notes asked, large, all in the
 * card and still no numbers; the pills over Pick over Again in the column. The trophy is sized from the
 * card's width, so the narrowest phone still holds it, and from dp, so large text does not grow it out of
 * the card; the keys take the size the room beside the trophy gives them ([summaryKeySize]). Nothing
 * scrolls: the headline is measured last and shrinks to the height the keys leave, and two rows of keys
 * close the gaps. Since 2026-11-10 the card holds no row of stars and no tiles, as upright ([RoundSummary]).
 */
@Composable
private fun SummarySideways(state: NotesRound, pills: @Composable () -> Unit, onChoose: () -> Unit, onPlayAgain: () -> Unit) {
    SidewaysPage(
        column = {
            pills()
            SidewaysPickButton(state.practiceNotes, state.progress, onChoose)
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    // A perfect round hops higher, as upright (the sparkles beside it are upright's alone).
                    Trophy(state, fontSize = fontSizeOf(trophy))
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(
                            if (summaryRows(state.askedThisRound) > 1) SummaryGapTwoRows else SummaryGap,
                            Alignment.CenterVertically,
                        ),
                        modifier = Modifier.weight(1f),
                    ) {
                        // Weighted, so it is measured after the keys and its autoSize fits the height they leave.
                        Text(
                            text = stringResource(headlineFor(state.score)),
                            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 32.sp, lineHeight = 1.2.em),
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 32.sp),
                            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                        )
                        SummaryKeys(state, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

private val TrophyMin = 72.dp
private val TrophyMax = 130.dp
/** The room between the headline and the keys; two rows of keys bring it to 10, so the headline keeps more of a 360 dp tall card. */
private val SummaryGap = 16.dp
private val SummaryGapTwoRows = 10.dp
