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
    val hop by rememberInfiniteTransition(label = "trophy").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "trophy",
    )
    val starsDescription = stringResource(R.string.summary_stars, state.score, NotesUiState.ROUND_LENGTH)
    val speed = state.speed()
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        Emoji(TROPHY_EMOJI, fontSize = 100, modifier = Modifier.graphicsLayer { translationY = -10.dp.toPx() * hop })
        Row(Modifier.clearAndSetSemantics { contentDescription = starsDescription }) {
            repeat(NotesUiState.ROUND_LENGTH) { index ->
                Emoji(STAR_EMOJI, fontSize = 26, modifier = Modifier.alpha(if (index < state.score) 1f else 0.25f))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatTile(
                picture = FLAME_EMOJI.repeat(state.bestStreak.coerceIn(1, MAX_FLAMES)),
                faint = state.bestStreak == 0,
                label = stringResource(R.string.summary_streak),
                description = stringResource(R.string.streak_description, state.bestStreak),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                picture = AIM_EMOJI + STAR_EMOJI.repeat(state.aimStars()),
                label = stringResource(R.string.summary_aim),
                description = stringResource(R.string.aim_description, state.aimStars()),
                modifier = Modifier.weight(1f),
            )
            StatTile(
                picture = speed?.emoji.orEmpty(),
                animal = true,
                label = stringResource(R.string.summary_speed),
                description = speed?.let { stringResource(R.string.speed_description, stringResource(it.labelRes)) }.orEmpty(),
                modifier = Modifier.weight(1f),
            )
        }
        BigActionButton(
            icon = AppIcons.Again,
            text = stringResource(R.string.play_again),
            color = ActionGo,
            onClick = onPlayAgain,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}

/**
 * A quiet tile: a picture made of emoji over one word; an [animal] faces right, as on the
 * metronome. The picture shrinks to fit the tile rather than lose an emoji off the edge: five
 * flames are wider than a third of a phone at full size.
 */
@Composable
private fun StatTile(
    picture: String,
    label: String,
    description: String,
    modifier: Modifier = Modifier,
    faint: Boolean = false,
    animal: Boolean = false,
) {
    val colors = FluteTheme.colors
    val shape = RoundedCornerShape(24.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        modifier = modifier
            .height(104.dp)
            .clip(shape)
            .background(colors.track)
            .border(OutlineWidth, colors.border, shape)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Text(
            text = picture,
            style = MaterialTheme.typography.titleLarge.copy(letterSpacing = (-2).sp),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 22.sp),
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .alpha(if (faint) 0.3f else 1f)
                .then(if (animal) Modifier.facingRight(picture) else Modifier),
        )
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
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
private const val TROPHY_EMOJI = "🏆" // trophy
private const val AIM_EMOJI = "🎯" // direct hit
