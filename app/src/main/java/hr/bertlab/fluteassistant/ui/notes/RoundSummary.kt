// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.notes

import androidx.annotation.StringRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.domain.notes.NotesRound
import hr.bertlab.fluteassistant.ui.common.AppIcons
import hr.bertlab.fluteassistant.ui.common.BigActionButton
import hr.bertlab.fluteassistant.ui.common.BigActionHeight
import hr.bertlab.fluteassistant.ui.common.Emoji
import hr.bertlab.fluteassistant.ui.common.fitOrScroll
import hr.bertlab.fluteassistant.ui.common.noteLabel
import hr.bertlab.fluteassistant.ui.theme.ActionGo

/** Flames shown at most, for the streak on the game screen. */
const val MAX_FLAMES = 5

/**
 * What the round came to, in place of the staff, without a single number on screen and as one message
 * (since 2026-11-10, the user's pick "4C" of three drawn options): a hopping trophy (higher, with a
 * sparkle on each side, after a perfect round), the keys of the notes asked, large, their colour rising
 * from where the round began to where it took them, and one big button to go again. The score is the row
 * of stars under the card, where the child watched it fill; the trophy says it to a screen reader.
 * Until then the card also held its own row of ten stars and three tiles (the best streak in flames, the
 * aim in stars, the speed as an animal): the same stars twice in two orders, the aim a third way of
 * saying them, and a slow animal under "Great job!".
 */
@Composable
fun RoundSummary(state: NotesRound, onPlayAgain: () -> Unit, modifier: Modifier = Modifier) {
    val perfect = state.score == NotesRound.ROUND_LENGTH
    val rows = summaryKeyRows(state.askedThisRound.sorted())
    // Pictures, in dp, as the trophy's room is worked out without them: the trophy is always the taller.
    val sparkles = with(LocalDensity.current) { TrophySparkles.toSp().value.toInt() }
    BoxWithConstraints(modifier) {
        // The keys take the size the width gives them; the trophy gives way to the room that leaves; only below its floor does the summary scroll.
        val key = summaryKeySize(maxWidth, rows.maxOfOrNull { it.size } ?: 0)
        val fit = rememberSummaryFit(room = maxHeight, rows = rows.size, keyHeight = key.height)
        Column(
            modifier = Modifier.fitOrScroll(fits = maxHeight >= fit.minHeight, minHeight = fit.minHeight),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            if (perfect) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag("trophy_sparkles"),
                ) {
                    Emoji(SPARKLES_EMOJI, fontSize = sparkles)
                    Trophy(state, fontSize = fit.trophySize)
                    Emoji(SPARKLES_EMOJI, fontSize = sparkles)
                }
            } else {
                Trophy(state, fontSize = fit.trophySize)
            }
            SummaryKeys(state)
            BigActionButton(
                icon = AppIcons.Again,
                text = stringResource(R.string.play_again),
                color = ActionGo,
                onClick = onPlayAgain,
                modifier = Modifier.padding(bottom = AGAIN_PADDING),
            )
        }
    }
}

/** How the summary is drawn in its room: the trophy's size in sp, and the least height the summary then takes. */
internal data class SummaryFit(val trophySize: Int, val minHeight: Dp)

/**
 * How the summary fits a [room] with [rows] of keys [keyHeight] tall. The keys keep their size, as they are
 * what the round earned; the trophy gives way: as large as there is room for with [SUMMARY_AIR] left over
 * for the parts to stand apart (a trophy that took every dp set the keys right on Again), up to
 * [TROPHY_LARGEST] (90 and not 100 leaves a phone with another emoji font some margin), down to [TROPHY_FLOOR]. Below that the
 * summary scrolls ([fitOrScroll]) at its least height rather than squeeze Again, since a child must
 * always reach it. [emojiHeight] is the height an [Emoji] of a size takes, measured: the phone's emoji
 * font draws a line taller than the 1.2 times the size [Emoji] asks of it. On a 411 dp phone nothing
 * scrolls down to the tab's minimum (623 dp, a stage of 352 dp inside), with two rows of the largest keys.
 */
internal fun fitSummary(room: Dp, rows: Int, keyHeight: Dp, emojiHeight: (emoji: String, fontSize: Int) -> Dp): SummaryFit {
    fun minHeight(trophy: Int) = summaryMinHeight(emojiHeight(TROPHY_EMOJI, trophy), rows, keyHeight)
    if (minHeight(TROPHY_FLOOR) > room) return SummaryFit(TROPHY_FLOOR, minHeight(TROPHY_FLOOR))
    // The largest trophy that fits and leaves the parts their air, by halving: an emoji's line grows with its size.
    val comfortable = room - SUMMARY_AIR
    var fits = TROPHY_FLOOR
    var tooBig = TROPHY_LARGEST + 1
    while (tooBig - fits > 1) {
        val size = (fits + tooBig) / 2
        if (minHeight(size) <= comfortable) fits = size else tooBig = size
    }
    return SummaryFit(fits, minHeight(fits))
}

/** [fitSummary] with this phone's emoji font, measured as [Emoji] draws it. */
@Composable
private fun rememberSummaryFit(room: Dp, rows: Int, keyHeight: Dp): SummaryFit {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.titleLarge
    return remember(room, rows, keyHeight, measurer, style, density) {
        fitSummary(room, rows, keyHeight) { emoji, fontSize ->
            val height = measurer.measure(emoji, style.copy(fontSize = fontSize.sp, lineHeight = (fontSize * 1.2f).sp)).size.height
            with(density) { height.toDp() }
        }
    }
}

/**
 * The least height the summary's parts take as they are drawn, [SUMMARY_GAP] apart: the [trophy], [rows] of
 * keys and Again with the room under it.
 */
private fun summaryMinHeight(trophy: Dp, rows: Int, keyHeight: Dp): Dp {
    val parts = if (rows > 0) 3 else 2 // the keys are not drawn when no note was asked
    return trophy + summaryKeysHeight(rows, keyHeight) + BigActionHeight + AGAIN_PADDING + SUMMARY_GAP * (parts - 1)
}

/** The [rows] of keys with their shadows, the room between them and the room over them for the star and sparkle; nothing when there are none. */
private fun summaryKeysHeight(rows: Int, keyHeight: Dp): Dp =
    if (rows == 0) 0.dp else KEY_OVERHANG + (keyHeight + KEY_SHADOW) * rows + KEY_ROW_GAP * (rows - 1)

/**
 * The hopping trophy, higher after a perfect round. It stands in a box of its own because an [Emoji]
 * carries no semantics: the box is what a test finds and what tells a screen reader the score, "Right
 * first time: 8 of 10", which no other part of the summary says since the card's stars went.
 */
@Composable
internal fun Trophy(state: NotesRound, fontSize: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.summary_stars, state.score, NotesRound.ROUND_LENGTH)
    Box(modifier.testTag("trophy").clearAndSetSemantics { contentDescription = description }) {
        Emoji(TROPHY_EMOJI, fontSize = fontSize, modifier = Modifier.trophyHop(big = state.score == NotesRound.ROUND_LENGTH))
    }
}

/** The trophy's hop: up 10 dp and back, once a second; 16 dp when [big], for a perfect round. */
@Composable
internal fun Modifier.trophyHop(big: Boolean = false): Modifier {
    val hop by rememberInfiniteTransition(label = "trophy").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "trophy",
    )
    val height = if (big) 16.dp else 10.dp
    return graphicsLayer { translationY = -height.toPx() * hop }
}

/** The keys stand in rows of at most this many: a round asks ten notes at most, so there are never more than two rows. */
private const val SUMMARY_KEYS_PER_ROW = 5

/** How many rows of keys the summary draws for the notes [asked]. */
internal fun summaryRows(asked: Set<Int>): Int = (asked.size + SUMMARY_KEYS_PER_ROW - 1) / SUMMARY_KEYS_PER_ROW

/**
 * The [notes] in rows of up to [SUMMARY_KEYS_PER_ROW], evened out so a row is never one key beside a
 * full one: six are three and three, seven four and three, ten five and five.
 */
internal fun summaryKeyRows(notes: List<Int>): List<List<Int>> {
    if (notes.isEmpty()) return emptyList()
    val rows = (notes.size + SUMMARY_KEYS_PER_ROW - 1) / SUMMARY_KEYS_PER_ROW
    return notes.chunked((notes.size + rows - 1) / rows)
}

/**
 * A key's size in a row of [perRow] keys, [SUMMARY_KEY_GAP] apart, in a room [width] wide: the largest
 * ([SummaryKeyLargest], 1.4 times the picker's key) when the row has room for it, else as wide as fits,
 * the height following so the key keeps its shape. Five fill a 411 dp phone's stage at 61 dp each.
 */
internal fun summaryKeySize(width: Dp, perRow: Int): DpSize {
    if (perRow <= 0) return SummaryKeyLargest
    val fits = (width - SUMMARY_KEY_GAP * (perRow - 1)) / perRow
    val keyWidth = minOf(SummaryKeyLargest.width, fits)
    return DpSize(keyWidth, SummaryKeyLargest.height * (keyWidth / SummaryKeyLargest.width))
}

/**
 * The notes asked this round as the picker draws them, in staff order, large and centred, in rows of up
 * to five ([summaryKeyRows]) sized from the width they are given ([summaryKeySize]): it is what the
 * round earned, how far each note is learned and which ones grew (those hop with a sparkle), and each
 * key's colour rises from where the round began ([NotesRound.startProgress]) to where it stands now.
 * The keys are a picture, so the block is one accessible node that says "Your notes" and names the notes
 * that grew. Nothing is drawn when no note was asked. The rows do not clip, so a star or a sparkle can
 * hang over a key's corner; the block keeps [KEY_OVERHANG] of room above the first row for them, so they
 * are not drawn over what is above.
 */
@Composable
internal fun SummaryKeys(state: NotesRound, modifier: Modifier = Modifier) {
    val rows = summaryKeyRows(state.askedThisRound.sorted())
    if (rows.isEmpty()) return
    val grown = rows.flatten().filter { it in state.grewThisRound }.map { noteLabel(it) }
    val description = (listOf(stringResource(R.string.your_notes)) + grown).joinToString(", ")
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val key = summaryKeySize(maxWidth, rows.maxOf { it.size })
        Column(
            verticalArrangement = Arrangement.spacedBy(KEY_ROW_GAP),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = KEY_OVERHANG).clearAndSetSemantics { contentDescription = description },
        ) {
            rows.forEach { row ->
                // The shadow under a key is outside its bounds, so a row is as tall as the key and the shadow, as in the picker.
                Row(horizontalArrangement = Arrangement.spacedBy(SUMMARY_KEY_GAP), modifier = Modifier.height(key.height + KEY_SHADOW)) {
                    row.forEach { midi ->
                        NoteKey(
                            midi = midi,
                            progress = state.progress,
                            width = key.width,
                            height = key.height,
                            scale = key.height / PickerKeyHeight,
                            from = state.startProgress,
                            grew = midi in state.grewThisRound,
                        )
                    }
                }
            }
        }
    }
}

/** The headline beside the picker button once the round is over; it grows with the score. */
@StringRes
fun headlineFor(score: Int): Int = when {
    score >= 9 -> R.string.round_amazing
    score >= 7 -> R.string.round_great
    score >= 4 -> R.string.round_good
    else -> R.string.round_keep_going
}

const val FLAME_EMOJI = "🔥" // fire
internal const val TROPHY_EMOJI = "🏆" // trophy
private const val SPARKLES_EMOJI = "✨" // sparkles, a perfect round

/** The depth of a key's shadow, which the picker's `KeyDepth` also has. */
private val KEY_SHADOW = 4.dp
private val KEY_ROW_GAP = 8.dp

/** The largest a summary key is drawn: 1.4 times the picker's 44 x 50, the size the user chose from the drawings. */
private val SummaryKeyLargest = DpSize(62.dp, 70.dp)

/** The room between two keys of a row. */
private val SUMMARY_KEY_GAP = 8.dp

/** The picker's key height, which a key's name, star and sparkle are drawn for: a summary key scales them by its own height over this. */
private val PickerKeyHeight = 50.dp

/** How far a learned key's star and a grown key's sparkle hang over the top of the key (`NoteKey`'s offset). */
private val KEY_OVERHANG = 10.dp

/** The trophy's size in sp at most, and the least it gives way to. */
private const val TROPHY_LARGEST = 90
private const val TROPHY_FLOOR = 36

/** The sparkles either side of the trophy after a perfect round. */
private val TrophySparkles = 28.dp

/** The room under Again, over [BigActionHeight] (`ui/common/Chunky.kt`), its height. */
private val AGAIN_PADDING = 8.dp

/** The least room between two parts of the summary, when the stage is as short as it fits. */
private val SUMMARY_GAP = 2.dp

/** The room a trophy that gives way leaves unused, which the column then spreads between the parts. */
private val SUMMARY_AIR = 16.dp
