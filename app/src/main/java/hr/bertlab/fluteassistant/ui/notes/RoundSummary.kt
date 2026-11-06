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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.ui.common.AppIcons
import hr.bertlab.fluteassistant.ui.common.BigActionButton
import hr.bertlab.fluteassistant.ui.common.Emoji
import hr.bertlab.fluteassistant.ui.common.OutlineWidth
import hr.bertlab.fluteassistant.ui.common.facingRight
import hr.bertlab.fluteassistant.ui.theme.ActionGo
import hr.bertlab.fluteassistant.ui.theme.FluteTheme

/** Flames shown at most, for the streak here and on the game screen. */
const val MAX_FLAMES = 5

/**
 * What the round came to, in place of the staff, without a single number on screen: a
 * hopping trophy, a star for every note found on the first try, three tiles (the best
 * streak in flames, the aim in stars, the speed as one of the metronome's animals) and one
 * big button to go again. The numbers are in the descriptions, for screen readers.
 */
@Composable
fun RoundSummary(state: NotesUiState, onPlayAgain: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        Emoji(TROPHY_EMOJI, fontSize = 100, modifier = Modifier.trophyHop())
        SummaryStars(state)
        SummaryTiles(state)
        BigActionButton(
            icon = AppIcons.Again,
            text = stringResource(R.string.play_again),
            color = ActionGo,
            onClick = onPlayAgain,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}

/** The trophy's hop: up 10 dp and back, once a second. */
@Composable
internal fun Modifier.trophyHop(): Modifier {
    val hop by rememberInfiniteTransition(label = "trophy").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "trophy",
    )
    return graphicsLayer { translationY = -10.dp.toPx() * hop }
}

/**
 * A star for every note found on the first try, ten in a row and the rest faint, of [fontSize] sp
 * (a sideways card sizes them from its width). One accessible node, read as the count.
 */
@Composable
internal fun SummaryStars(state: NotesUiState, modifier: Modifier = Modifier, fontSize: Int = 26) {
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
 * the words shrinking to the tile.
 */
@Composable
internal fun SummaryTiles(state: NotesUiState, modifier: Modifier = Modifier, compact: Boolean = false) {
    val speed = state.speed()
    Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 10.dp), modifier = modifier.fillMaxWidth()) {
        StatTile(
            picture = FLAME_EMOJI.repeat(state.bestStreak.coerceIn(1, MAX_FLAMES)),
            faint = state.bestStreak == 0,
            label = stringResource(R.string.summary_streak),
            description = stringResource(R.string.streak_description, state.bestStreak),
            compact = compact,
            modifier = Modifier.weight(1f),
        )
        StatTile(
            picture = AIM_EMOJI + STAR_EMOJI.repeat(state.aimStars()),
            label = stringResource(R.string.summary_aim),
            description = stringResource(R.string.aim_description, state.aimStars()),
            compact = compact,
            modifier = Modifier.weight(1f),
        )
        StatTile(
            picture = speed?.emoji.orEmpty(),
            animal = true,
            label = stringResource(R.string.summary_speed),
            description = speed?.let { stringResource(R.string.speed_description, stringResource(it.labelRes)) }.orEmpty(),
            compact = compact,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * A quiet tile: a picture made of emoji over one word; an [animal] faces right, as on the
 * metronome. The picture shrinks to fit the tile rather than lose an emoji off the edge: five
 * flames are wider than a third of a phone at full size. A [compact] tile (sideways) is lower, and
 * its word shrinks too, as a third of a short phone's card is narrow for a word at large text.
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
) {
    val colors = FluteTheme.colors
    val shape = RoundedCornerShape(24.dp)
    val pictureStyle = MaterialTheme.typography.titleLarge.copy(letterSpacing = (-2).sp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        modifier = modifier
            .height(if (compact) 92.dp else 104.dp)
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
