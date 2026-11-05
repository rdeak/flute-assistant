// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.sp
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.ui.theme.ActionGo
import hr.bertlab.fluteassistant.ui.theme.FluteTheme
import hr.bertlab.fluteassistant.ui.theme.Sun

/**
 * Shown until the microphone may be used, by the tuner on opening and by the flute's "Your turn"
 * when the phone has said no: a big friendly microphone, one question, one reassuring line, a big
 * "Yes" that asks the phone ([onRequestPermission]), and quieter pills for the app's settings
 * ([onOpenSettings], for when the phone will not ask again) and, when the caller gives one, a way
 * back ([onDismiss], "Not now"; the flute, which works without the microphone). The way back is a
 * quiet pill, not a second big green button, so the card has one "go".
 */
@Composable
fun MicrophonePermissionCard(
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
) {
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
        Spacer(Modifier.height(20.dp))
        // Side by side when they fit, "Not now" under Settings when they do not (a 360 dp phone with
        // large text): a flow, so neither pill is ever squeezed or cut.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            QuietPill(text = stringResource(R.string.open_settings), emoji = GEAR_EMOJI, onClick = onOpenSettings)
            if (onDismiss != null) {
                QuietPill(text = stringResource(R.string.mic_not_now), emoji = null, onClick = onDismiss)
            }
        }
    }
}

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
