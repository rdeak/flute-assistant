// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.tuner

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.domain.tuner.FluteRange
import hr.bertlab.fluteassistant.ui.common.AppIcons
import hr.bertlab.fluteassistant.ui.common.BigActionButton
import hr.bertlab.fluteassistant.ui.common.ChunkyButton
import hr.bertlab.fluteassistant.ui.common.LocalSideways
import hr.bertlab.fluteassistant.ui.common.SidewaysGeometry
import hr.bertlab.fluteassistant.ui.common.SidewaysPage
import hr.bertlab.fluteassistant.ui.common.Stage
import hr.bertlab.fluteassistant.ui.common.TabPadding
import hr.bertlab.fluteassistant.ui.common.WordFloor
import hr.bertlab.fluteassistant.ui.common.asTextSize
import hr.bertlab.fluteassistant.ui.common.noteLabel
import hr.bertlab.fluteassistant.ui.theme.ActionGo
import hr.bertlab.fluteassistant.ui.theme.FluteTheme
import hr.bertlab.fluteassistant.ui.theme.OnAction

/*
 * The guide to tuning the flute, in the tuner tab's place (spec `2026-11-14-tuning-guide-design.md`,
 * "The guide"; mockups `tuning-guide.html` and `last-card-v4.html`): four picture cards with a short
 * title and a few words each, ‹ › to go through them, dots to show where the child is, and ✕, OK on the
 * last card or the phone's Back to leave. The words give way before the picture: they shrink, down to
 * 12 dp, and the picture keeps at least 40% of the card ("Fit"). Nothing scrolls.
 */

/**
 * The guide on [card], upright or sideways as [LocalSideways] says; [onCard] moves to another card and
 * [onClose] leaves, saying how. The phone's Back leaves it too.
 */
@Composable
internal fun TuningGuide(
    card: GuideCard,
    onCard: (GuideCard) -> Unit,
    onClose: (GuideExit) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { onClose(GuideExit.BACK) }
    if (LocalSideways.current) {
        TuningGuideSideways(card, onCard, onClose, modifier)
    } else {
        TuningGuideUpright(card, onCard, onClose, modifier)
    }
}

@Composable
private fun TuningGuideUpright(card: GuideCard, onCard: (GuideCard) -> Unit, onClose: (GuideExit) -> Unit, modifier: Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            .fillMaxSize()
            .padding(TabPadding),
    ) {
        // The heading row has the mode pills' height and shadow, so the card starts where the stage did.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(HeadingHeight),
        ) {
            Text(
                stringResource(R.string.tune_guide_title),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 1.25.em),
                color = FluteTheme.colors.text,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = WordFloor.asTextSize(), maxFontSize = 26.sp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("guide_heading"),
            )
            Spacer(Modifier.width(12.dp))
            ChunkyButton(onClick = { onClose(GuideExit.CLOSE) }, modifier = Modifier.size(52.dp)) {
                Icon(AppIcons.Close, contentDescription = stringResource(R.string.tune_guide_close), modifier = Modifier.size(28.dp))
            }
        }
        Stage(
            padding = GuideStagePadding,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("guide_stage"),
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                // The words take at most 60% of the card's inside, so the picture keeps the rest.
                val wordsRoom = maxHeight * WORDS_SHARE - WordsGap
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(WordsGap),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    GuidePicture(
                        card,
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                    CardWords(card, center = true, modifier = Modifier.heightIn(max = wordsRoom))
                }
            }
        }
        // ‹ dots › under the card; on the last card the green OK takes the place of the dots and ›, as the
        // pill and the dots do not fit side by side on a 360 dp phone, and OK says the end well enough.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 4.dp),
        ) {
            StepIcon(AppIcons.ChevronLeft, R.string.tune_guide_back, card.previous, onCard, Modifier.size(64.dp))
            if (card.next == null) {
                Spacer(Modifier.width(16.dp))
                BigActionButton(
                    icon = AppIcons.Done,
                    text = stringResource(R.string.tune_guide_done),
                    color = ActionGo,
                    onClick = { onClose(GuideExit.OK) },
                    height = 64.dp,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Dots(card)
                StepIcon(AppIcons.ChevronRight, R.string.tune_guide_next, card.next, onCard, Modifier.size(64.dp))
            }
        }
    }
}

@Composable
private fun TuningGuideSideways(card: GuideCard, onCard: (GuideCard) -> Unit, onClose: (GuideExit) -> Unit, modifier: Modifier) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val short = SidewaysGeometry.isShort(maxHeight.value)
        SidewaysPage(
            column = {
                ChunkyButton(
                    onClick = { onClose(GuideExit.CLOSE) },
                    shape = RoundedCornerShape(30.dp),
                    depth = 5.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (short) 70.dp else 84.dp),
                ) {
                    Icon(AppIcons.Close, contentDescription = stringResource(R.string.tune_guide_close), modifier = Modifier.size(30.dp))
                }
                // ‹ and › side by side low in the column, where Which? and Tune stand on the tuner.
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val pair = Modifier
                        .weight(1f)
                        .height(if (short) 80.dp else 92.dp)
                    StepIcon(AppIcons.ChevronLeft, R.string.tune_guide_back, card.previous, onCard, pair, RoundedCornerShape(26.dp))
                    if (card.next == null) {
                        // No room for "U redu" in half the column: the tick alone, named OK for a screen reader.
                        ChunkyButton(
                            onClick = { onClose(GuideExit.OK) },
                            shape = RoundedCornerShape(26.dp),
                            color = ActionGo,
                            depth = 5.dp,
                            modifier = pair,
                        ) {
                            Icon(AppIcons.Done, contentDescription = stringResource(R.string.tune_guide_done), tint = OnAction, modifier = Modifier.size(30.dp))
                        }
                    } else {
                        StepIcon(AppIcons.ChevronRight, R.string.tune_guide_next, card.next, onCard, pair, RoundedCornerShape(26.dp))
                    }
                }
            },
        ) { cardModifier ->
            Stage(padding = GuideStagePadding, modifier = cardModifier.testTag("guide_stage")) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val pictureWidth = maxWidth * if (maxWidth >= WIDE_CARD) 0.5f else 0.4f
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
                        GuidePicture(
                            card,
                            Modifier
                                .width(pictureWidth)
                                .fillMaxHeight(),
                        )
                        Spacer(Modifier.width(22.dp))
                        Column(
                            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        ) {
                            CardWords(card, center = false, modifier = Modifier.weight(1f, fill = false))
                            Dots(card)
                        }
                    }
                }
            }
        }
    }
}

/**
 * The card's title (two lines at most) over its words, both shrinking to fit what [modifier] leaves them,
 * down to 12 dp; the title is measured first and the words get the rest.
 */
@Composable
private fun CardWords(card: GuideCard, center: Boolean, modifier: Modifier) {
    val note = noteLabel(FluteRange.TUNING_NOTE)
    val (title, words) = when (card) {
        GuideCard.WARM_UP -> stringResource(R.string.tune_guide_warm_up_title) to stringResource(R.string.tune_guide_warm_up_text)
        GuideCard.HEAD_JOINT -> stringResource(R.string.tune_guide_head_joint_title, note) to stringResource(R.string.tune_guide_head_joint_text, note)
        GuideCard.EACH_NOTE -> stringResource(R.string.tune_guide_each_note_title) to stringResource(R.string.tune_guide_each_note_text)
        GuideCard.ONE_NOTE_OFF -> stringResource(R.string.tune_guide_one_note_off_title) to stringResource(R.string.tune_guide_one_note_off_text)
    }
    val align = if (center) TextAlign.Center else TextAlign.Start
    val floor = WordFloor.asTextSize()
    BoxWithConstraints(modifier) {
        // The title is measured first; capped at 40% of the room, so at a large text size on a short tab it
        // cannot take the room the words, which are the instruction, need.
        val titleRoom = maxHeight * TITLE_SHARE
        Column(
            horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, lineHeight = 1.2.em),
                color = FluteTheme.colors.text,
                textAlign = align,
                maxLines = 2,
                autoSize = TextAutoSize.StepBased(minFontSize = floor, maxFontSize = 28.sp),
                modifier = Modifier
                    .heightIn(max = titleRoom)
                    .testTag("guide_title"),
            )
            Text(
                words,
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = (28f / 21f).em),
                color = FluteTheme.colors.mutedText,
                textAlign = align,
                autoSize = TextAutoSize.StepBased(minFontSize = floor, maxFontSize = 21.sp),
                modifier = Modifier
                    .weight(1f, fill = false)
                    .testTag("guide_words"),
            )
        }
    }
}

/** Four dots, the current card's filled; only a picture of where the child is, silent to a screen reader. */
@Composable
private fun Dots(card: GuideCard) {
    val ink = FluteTheme.colors.text
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.clearAndSetSemantics { }) {
        GuideCard.entries.forEach { each ->
            Box(
                Modifier
                    .size(14.dp)
                    .then(if (each == card) Modifier.background(ink, CircleShape) else Modifier.border(3.dp, ink, CircleShape)),
            )
        }
    }
}

/**
 * A ‹ or › of the guide, to the card [to]: the tuner's [StepButton] with a larger chevron, faded when there is no such
 * card.
 */
@Composable
private fun StepIcon(
    icon: ImageVector,
    description: Int,
    to: GuideCard?,
    onCard: (GuideCard) -> Unit,
    modifier: Modifier,
    shape: Shape = CircleShape,
) = StepButton(icon, stringResource(description), enabled = to != null, onClick = { to?.let(onCard) }, modifier, shape, iconSize = 32.dp)

private val HeadingHeight = 69.dp
private val GuideStagePadding = 20.dp
private val WordsGap = 16.dp

/** The title's share of the room its title and words have, at most. */
private const val TITLE_SHARE = 0.4f

/** The words' share of an upright card's inside at most; the picture keeps the rest. */
private const val WORDS_SHARE = 0.6f

/** Sideways, a card inside this wide gives the picture half of it, a narrower one 40%. */
private val WIDE_CARD: Dp = 520.dp
