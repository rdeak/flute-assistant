// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.notes

import androidx.annotation.StringRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.ui.common.AppIcons
import hr.bertlab.fluteassistant.ui.common.BigActionButton
import hr.bertlab.fluteassistant.ui.common.BigActionHeight
import hr.bertlab.fluteassistant.ui.common.Emoji
import hr.bertlab.fluteassistant.ui.common.OutlineWidth
import hr.bertlab.fluteassistant.ui.common.facingRight
import hr.bertlab.fluteassistant.ui.common.fitOrScroll
import hr.bertlab.fluteassistant.ui.common.noteLabel
import hr.bertlab.fluteassistant.ui.theme.ActionGo
import hr.bertlab.fluteassistant.ui.theme.FluteTheme

/** Flames shown at most, for the streak here and on the game screen. */
const val MAX_FLAMES = 5

/**
 * What the round came to, in place of the staff, without a single number on screen: a
 * hopping trophy (higher, with a sparkle on each side, after a perfect round), a star for every
 * note found on the first try, three tiles (the best streak in flames, the aim in stars, the
 * speed as one of the metronome's animals), the keys of the notes asked and one big button to go
 * again. The numbers are in the descriptions, for screen readers.
 */
@Composable
fun RoundSummary(state: NotesUiState, onPlayAgain: () -> Unit, modifier: Modifier = Modifier) {
    val perfect = state.score == NotesUiState.ROUND_LENGTH
    val rows = summaryRows(state.askedThisRound)
    // Pictures, in dp, as the trophy's room is worked out without them: the trophy is always the taller.
    val sparkles = with(LocalDensity.current) { TrophySparkles.toSp().value.toInt() }
    BoxWithConstraints(modifier) {
        // The trophy gives way to the room first, then the tiles; only below that does the summary scroll.
        val fit = rememberSummaryFit(room = maxHeight, rows = rows)
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
                    Trophy(fontSize = fit.trophySize, big = true)
                    Emoji(SPARKLES_EMOJI, fontSize = sparkles)
                }
            } else {
                Trophy(fontSize = fit.trophySize, big = false)
            }
            SummaryStars(state)
            SummaryTiles(state, tileHeight = fit.tileHeight)
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

/** How the summary is drawn in its room: the trophy's size in sp, the tiles' height, and the least height the summary then takes. */
internal data class SummaryFit(val trophySize: Int, val tileHeight: Dp, val minHeight: Dp)

/**
 * How the summary fits a [room] with [rows] of keys. The trophy gives way first: as large as there is
 * room for, up to its rule (90 sp with one row of keys, 50 with two: at 60 the design phone's stage was
 * still about 5 dp short with two rows, and 90 and not 100 leaves a phone with another emoji font some
 * margin), down to [TROPHY_FLOOR]. When even the smallest trophy leaves too little, the tiles drop to the
 * sideways [COMPACT_TILE_HEIGHT] and the trophy grows back into the 12 dp that frees. Below that the
 * summary scrolls ([fitOrScroll]) at the least height of those smallest sizes, rather than squeeze
 * Again, since a child must always reach it. [emojiHeight] is the height an [Emoji] of a size takes,
 * measured: the phone's emoji font draws a line taller than the 1.2 times the size [Emoji] asks of it.
 * The design phone's stage (915 dp tall, 442 dp inside) keeps the rule's sizes; a 650 dp tab with one
 * row takes a trophy of about 55 sp and nothing scrolls.
 */
internal fun fitSummary(room: Dp, rows: Int, emojiHeight: (emoji: String, fontSize: Int) -> Dp): SummaryFit {
    val largest = if (rows > 1) TROPHY_TWO_ROWS else TROPHY_ONE_ROW
    val stars = emojiHeight(STAR_EMOJI, SUMMARY_STAR_SIZE)
    fun minHeight(trophy: Int, tiles: Dp) = summaryMinHeight(emojiHeight(TROPHY_EMOJI, trophy), stars, tiles, rows)
    for (tiles in listOf(TILE_HEIGHT, COMPACT_TILE_HEIGHT)) {
        if (minHeight(TROPHY_FLOOR, tiles) > room) continue
        // The largest trophy that fits, by halving: an emoji's line grows with its size.
        var fits = TROPHY_FLOOR
        var tooBig = largest + 1
        while (tooBig - fits > 1) {
            val size = (fits + tooBig) / 2
            if (minHeight(size, tiles) <= room) fits = size else tooBig = size
        }
        return SummaryFit(fits, tiles, minHeight(fits, tiles))
    }
    return SummaryFit(TROPHY_FLOOR, COMPACT_TILE_HEIGHT, minHeight(TROPHY_FLOOR, COMPACT_TILE_HEIGHT))
}

/** [fitSummary] with this phone's emoji font, measured as [Emoji] draws it. */
@Composable
private fun rememberSummaryFit(room: Dp, rows: Int): SummaryFit {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.titleLarge
    return remember(room, rows, measurer, style, density) {
        fitSummary(room, rows) { emoji, fontSize ->
            val height = measurer.measure(emoji, style.copy(fontSize = fontSize.sp, lineHeight = (fontSize * 1.2f).sp)).size.height
            with(density) { height.toDp() }
        }
    }
}

/**
 * The least height the summary's parts take as they are drawn, [SUMMARY_GAP] apart: the [trophy], the
 * [stars], the [tiles], [rows] of keys and Again with the room under it. The gaps are the least that keep
 * the parts apart: with 8 dp between them the design phone's stage would scroll by a few dp.
 */
private fun summaryMinHeight(trophy: Dp, stars: Dp, tiles: Dp, rows: Int): Dp {
    val parts = if (rows > 0) 5 else 4 // the keys are not drawn when no note was asked
    return trophy + stars + tiles + summaryKeysHeight(rows) + BigActionHeight + AGAIN_PADDING + SUMMARY_GAP * (parts - 1)
}

/** The [rows] of keys with their shadows, the room between them and the room over them for the star and sparkle; nothing when there are none. */
private fun summaryKeysHeight(rows: Int): Dp =
    if (rows == 0) 0.dp else KEY_OVERHANG + (KEY_HEIGHT + KEY_SHADOW) * rows + KEY_ROW_GAP * (rows - 1)

/** The hopping trophy; [big] for a perfect round. It stands in a box of its own because an [Emoji] carries no semantics for a test to tag. */
@Composable
private fun Trophy(fontSize: Int, big: Boolean) {
    Box(Modifier.testTag("trophy")) {
        Emoji(TROPHY_EMOJI, fontSize = fontSize, modifier = Modifier.trophyHop(big))
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

/** The keys fill rows of this many: one row for the usual five notes, two for up to ten. */
private const val SUMMARY_KEYS_PER_ROW = 7

/** How many rows of keys the summary draws for the notes [asked]; the trophy is at most 50 sp instead of 90 when it is more than one ([fitSummary]). */
internal fun summaryRows(asked: Set<Int>): Int = (asked.size + SUMMARY_KEYS_PER_ROW - 1) / SUMMARY_KEYS_PER_ROW

/**
 * The notes asked this round as the picker draws them, in staff order, up to seven to a row and
 * centred: it is where a child sees what the round earned, how far each note is learned and which
 * ones grew (those hop with a sparkle). The keys are a picture, so the block is one accessible node
 * that says "Your notes" and names the notes that grew. Nothing is drawn when no note was asked.
 * The rows do not clip, so a star or a sparkle can hang over a key's corner; the block keeps
 * [KEY_OVERHANG] of room above the first row for them, so they are not drawn over what is above.
 * The sideways summary passes smaller keys.
 */
@Composable
internal fun SummaryKeys(
    state: NotesUiState,
    modifier: Modifier = Modifier,
    keyWidth: Dp = 44.dp,
    keyHeight: Dp = KEY_HEIGHT,
    gap: Dp = 6.dp,
) {
    if (state.askedThisRound.isEmpty()) return
    val notes = state.askedThisRound.sorted()
    val grown = notes.filter { it in state.grewThisRound }.map { noteLabel(it) }
    val description = (listOf(stringResource(R.string.your_notes)) + grown).joinToString(", ")
    Column(
        verticalArrangement = Arrangement.spacedBy(KEY_ROW_GAP),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(top = KEY_OVERHANG).clearAndSetSemantics { contentDescription = description },
    ) {
        notes.chunked(SUMMARY_KEYS_PER_ROW).forEach { row ->
            // The shadow under a key is outside its bounds, so a row is as tall as the key and the shadow, as in the picker.
            Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.height(keyHeight + KEY_SHADOW)) {
                row.forEach { midi ->
                    NoteKey(
                        midi = midi,
                        progress = state.progress,
                        // Equal shares: a full row of seven is wider than a phone's stage, and then all the keys shrink a little, not the last one alone.
                        modifier = Modifier.weight(1f, fill = false),
                        width = keyWidth,
                        height = keyHeight,
                        grew = midi in state.grewThisRound,
                    )
                }
            }
        }
    }
}

/**
 * A star for every note found on the first try, ten in a row and the rest faint, of [fontSize] sp
 * (a sideways card sizes them from its width). One accessible node, read as the count.
 */
@Composable
internal fun SummaryStars(state: NotesUiState, modifier: Modifier = Modifier, fontSize: Int = SUMMARY_STAR_SIZE) {
    val description = stringResource(R.string.summary_stars, state.score, NotesUiState.ROUND_LENGTH)
    Row(modifier.clearAndSetSemantics { contentDescription = description }) {
        repeat(NotesUiState.ROUND_LENGTH) { index ->
            Emoji(STAR_EMOJI, fontSize = fontSize, modifier = Modifier.alpha(if (index < state.score) 1f else 0.25f))
        }
    }
}

/**
 * The three tiles: the best streak in flames, the aim in stars and the speed as one of the
 * metronome's animals. [compact] is the sideways size, lower and with the room between them and
 * the words shrinking to the tile. Upright on a short stage the tiles are [tileHeight] tall, the
 * sideways height, with the upright look ([fitSummary]).
 */
@Composable
internal fun SummaryTiles(
    state: NotesUiState,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    tileHeight: Dp = if (compact) COMPACT_TILE_HEIGHT else TILE_HEIGHT,
) {
    val speed = state.speed()
    Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 10.dp), modifier = modifier.fillMaxWidth()) {
        StatTile(
            picture = FLAME_EMOJI.repeat(state.bestStreak.coerceIn(1, MAX_FLAMES)),
            faint = state.bestStreak == 0,
            label = stringResource(R.string.summary_streak),
            description = stringResource(R.string.streak_description, state.bestStreak),
            compact = compact,
            height = tileHeight,
            modifier = Modifier.weight(1f),
        )
        StatTile(
            picture = AIM_EMOJI + STAR_EMOJI.repeat(state.aimStars()),
            label = stringResource(R.string.summary_aim),
            description = stringResource(R.string.aim_description, state.aimStars()),
            compact = compact,
            height = tileHeight,
            modifier = Modifier.weight(1f),
        )
        StatTile(
            picture = speed?.emoji.orEmpty(),
            animal = true,
            label = stringResource(R.string.summary_speed),
            description = speed?.let { stringResource(R.string.speed_description, stringResource(it.labelRes)) }.orEmpty(),
            compact = compact,
            height = tileHeight,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * A quiet tile: a picture made of emoji over one word; an [animal] faces right, as on the
 * metronome. The picture shrinks to fit the tile rather than lose an emoji off the edge: five
 * flames are wider than a third of a phone at full size. A [compact] tile (sideways) is lower, and
 * its word shrinks too, as a third of a short phone's card is narrow for a word at large text. The
 * tile is [height] tall; the upright look holds at the sideways 92 dp (at twice the text its two
 * lines take about 77).
 */
@Composable
private fun StatTile(
    picture: String,
    label: String,
    description: String,
    modifier: Modifier = Modifier,
    faint: Boolean = false,
    animal: Boolean = false,
    compact: Boolean = false,
    height: Dp = if (compact) COMPACT_TILE_HEIGHT else TILE_HEIGHT,
) {
    val colors = FluteTheme.colors
    val shape = RoundedCornerShape(24.dp)
    val pictureStyle = MaterialTheme.typography.titleLarge.copy(letterSpacing = (-2).sp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        modifier = modifier
            .height(height)
            .clip(shape)
            .background(colors.track)
            .border(OutlineWidth, colors.border, shape)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Text(
            text = picture,
            // Compact lines are relative: a fixed line of the theme (30 sp) would stay as tall when the size shrinks to fit, and at the largest text the two lines would not fit the 92 dp.
            style = if (compact) pictureStyle.copy(lineHeight = 1.2.em) else pictureStyle,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = if (compact) 8.sp else 10.sp, maxFontSize = if (compact) 24.sp else 22.sp),
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .alpha(if (faint) 0.3f else 1f)
                .then(if (animal) Modifier.facingRight(picture) else Modifier),
        )
        if (compact) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 17.sp, lineHeight = 1.25.em),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 17.sp),
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        } else {
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
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
private const val AIM_EMOJI = "🎯" // direct hit
private const val SPARKLES_EMOJI = "✨" // sparkles, a perfect round

/** The depth of a key's shadow, which the picker's `KeyDepth` also has. */
private val KEY_SHADOW = 4.dp
private val KEY_HEIGHT = 50.dp
private val KEY_ROW_GAP = 8.dp

/** How far a learned key's star and a grown key's sparkle hang over the top of the key (`NoteKey`'s offset). */
private val KEY_OVERHANG = 10.dp

/** The size of the stars of the summary, upright. */
private const val SUMMARY_STAR_SIZE = 26

/** The trophy's size in sp at most with one row of keys, and with two; and the least it gives way to. */
private const val TROPHY_ONE_ROW = 90
private const val TROPHY_TWO_ROWS = 50
private const val TROPHY_FLOOR = 36

/** The sparkles either side of the trophy after a perfect round. */
private val TrophySparkles = 28.dp

/** An upright tile's height. */
private val TILE_HEIGHT = 104.dp

/** A sideways tile's height, which an upright one drops to when the stage is short. */
private val COMPACT_TILE_HEIGHT = 92.dp

/** The room under Again, over [BigActionHeight] (`ui/common/Chunky.kt`), its height. */
private val AGAIN_PADDING = 8.dp

/** The least room between two parts of the summary, when the stage is as short as it fits. */
private val SUMMARY_GAP = 2.dp
