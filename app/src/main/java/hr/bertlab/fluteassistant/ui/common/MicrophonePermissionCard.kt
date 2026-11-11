// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.ui.theme.ActionGo
import hr.bertlab.fluteassistant.ui.theme.FluteTheme
import hr.bertlab.fluteassistant.ui.theme.Sun

/**
 * Shown until the microphone may be used, by the tuner on opening and by the flute's "Your turn"
 * when the phone has said no: a big friendly microphone, one question, one reassuring line, a big
 * "Yes" that asks the phone ([onRequestPermission]), and quieter pills, each only when the caller
 * gives it: the app's settings ([onOpenSettings]) and a way back ([onDismiss], "Not now"; the flute,
 * which works without the microphone). Settings is the one button in the app that leaves it, so a
 * caller gives it only once the phone will not ask again and Yes can do nothing more (since
 * 2026-11-10; before, a child met it on the tuner's first opening). The way back is a quiet pill,
 * not a second big green button, so the card has one "go".
 *
 * On a phone on its side ([LocalSideways]) the card is a whole page of its own, which the caller
 * gives all of its room, with no padding: the disc and the words beside it in the card, the quiet
 * buttons at the top of the right column and "Yes" low in it, where Play always is.
 */
@Composable
fun MicrophonePermissionCard(
    onRequestPermission: () -> Unit,
    onOpenSettings: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
) {
    if (LocalSideways.current) {
        MicrophonePermissionSideways(onRequestPermission, onOpenSettings, modifier, onDismiss)
        return
    }
    val colors = FluteTheme.colors
    Stage(modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(160.dp)
                    .chunky(CircleShape, Sun, colors.outline, depth = 8.dp),
            ) {
                Emoji(MICROPHONE_EMOJI, fontSize = 80)
            }
            Text(
                text = stringResource(R.string.mic_permission_title),
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 34.sp, lineHeight = 42.sp),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                text = stringResource(R.string.mic_permission_body),
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 30.sp),
                textAlign = TextAlign.Center,
                color = colors.mutedText,
            )
        }
        Spacer(Modifier.height(12.dp))
        BigActionButton(
            icon = AppIcons.Done,
            text = stringResource(R.string.allow_microphone),
            color = ActionGo,
            onClick = onRequestPermission,
        )
        if (onOpenSettings != null || onDismiss != null) {
            Spacer(Modifier.height(20.dp))
            // Side by side when they fit, "Not now" under Settings when they do not (a 360 dp phone with
            // large text): a flow, so neither pill is ever squeezed or cut.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (onOpenSettings != null) {
                    QuietPill(text = stringResource(R.string.open_settings), emoji = GEAR_EMOJI, onClick = onOpenSettings)
                }
                if (onDismiss != null) {
                    QuietPill(text = stringResource(R.string.mic_not_now), emoji = null, onClick = onDismiss)
                }
            }
        }
    }
}

/**
 * The card for a phone on its side (mockup `more.html`, "Asking for the microphone"). The disc
 * shrinks with the card's height and width, and the words beside it take what is left, wrapping
 * between words and shrinking at a large text size, so nothing is cut off or scrolls.
 */
@Composable
private fun MicrophonePermissionSideways(
    onRequestPermission: () -> Unit,
    onOpenSettings: (() -> Unit)?,
    modifier: Modifier,
    onDismiss: (() -> Unit)?,
) {
    val colors = FluteTheme.colors
    SidewaysPage(
        modifier = modifier,
        column = {
            // One child, so that the column's two ends are the quiet buttons (none, one or both) and "Yes".
            Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                if (onOpenSettings != null) SidewaysPlainButton(GEAR_EMOJI, stringResource(R.string.open_settings), onOpenSettings)
                if (onDismiss != null) SidewaysPlainButton(null, stringResource(R.string.mic_not_now), onDismiss)
            }
            SidewaysActionButton(
                icon = AppIcons.Done,
                text = stringResource(R.string.allow_microphone),
                color = ActionGo,
                onClick = onRequestPermission,
            )
        },
    ) { card ->
        Stage(modifier = card, verticalArrangement = Arrangement.Center) {
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val disc = minOf(DiscSize, maxHeight - DiscDepth, maxWidth * DISC_WIDTH_SHARE)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DiscGap, Alignment.CenterHorizontally),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(disc)
                            .chunky(CircleShape, Sun, colors.outline, depth = DiscDepth),
                    ) {
                        Emoji(MICROPHONE_EMOJI, fontSize = (disc.value * DISC_EMOJI_SHARE).toInt())
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .widthIn(max = WordsWidth),
                    ) {
                        // Two lines at most: a word too wide for the line must shrink the text, not be split.
                        Text(
                            text = stringResource(R.string.mic_permission_title),
                            style = MaterialTheme.typography.headlineMedium.copy(lineHeight = 1.25.em),
                            maxLines = 2,
                            autoSize = TextAutoSize.StepBased(minFontSize = 16.sp, maxFontSize = 34.sp),
                        )
                        Text(
                            text = stringResource(R.string.mic_permission_body),
                            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 1.4.em),
                            color = colors.mutedText,
                            maxLines = 3,
                            autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 21.sp),
                        )
                    }
                }
            }
        }
    }
}

private val DiscSize = 190.dp
private val DiscDepth = 6.dp
private val DiscGap = 36.dp
private val WordsWidth = 300.dp

/** The disc takes at most this much of the card's width, so the words keep theirs on a narrow phone. */
private const val DISC_WIDTH_SHARE = 0.38f

/** The mockup's 96 for the 190 dp disc. */
private const val DISC_EMOJI_SHARE = 96f / 190f

/** The card's quieter actions: an outlined pill, not a big coloured one, its word on one line at the size that fits. */
@Composable
private fun QuietPill(text: String, emoji: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = FluteTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .height(56.dp)
            .clip(CircleShape)
            .border(OutlineWidth, colors.border, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
    ) {
        if (emoji != null) Emoji(emoji, fontSize = 22)
        Text(
            text,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = 22.sp),
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

private const val MICROPHONE_EMOJI = "🎤" // microphone
private const val GEAR_EMOJI = "⚙️" // gear
