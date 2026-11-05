// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.ui.theme.ActionGo
import hr.bertlab.fluteassistant.ui.theme.FluteTheme

/**
 * The app icon of the top bar, which opens the credits on a long press. Nothing on screen says it
 * can: the credits are for grown-ups and for forks, which NOTICE asks to keep them, not a control
 * for a child, so a tap does nothing and the icon looks as it always did. A screen reader is
 * offered the long press as an action.
 */
@Composable
fun CreditsLogo(modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    val openLabel = stringResource(R.string.credits_open)
    AppLogo(
        contentDescription = stringResource(R.string.app_name),
        modifier = modifier
            .pointerInput(Unit) { detectTapGestures(onLongPress = { open = true }) }
            .semantics {
                onLongClick(label = openLabel) {
                    open = true
                    true
                }
            },
    )
    if (open) Credits(onClose = { open = false })
}

/**
 * Who holds the copyright, the licence and where the source is. The address is plain text, never
 * a link, so a child cannot leave the app from here. It scrolls for large text on a short screen.
 * The strings are read before the [Dialog]: its window has a context of its own, which the UI
 * tests' LocaleProvider does not reach.
 */
@Composable
private fun Credits(onClose: () -> Unit) {
    val name = stringResource(R.string.app_name)
    val notices = listOf(
        stringResource(R.string.credits_copyright),
        stringResource(R.string.credits_licence),
        stringResource(R.string.credits_source),
    )
    val address = stringResource(R.string.credits_source_url)
    val done = stringResource(R.string.credits_done)
    val textColor = FluteTheme.colors.text
    Dialog(onDismissRequest = onClose) {
        Stage(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.verticalScroll(rememberScrollState()),
        ) {
            AppLogo(contentDescription = null, modifier = Modifier.size(64.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.titleLarge,
                color = textColor,
                textAlign = TextAlign.Center,
            )
            notices.forEach { notice ->
                Text(
                    text = notice,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                text = address,
                style = MaterialTheme.typography.labelLarge,
                color = textColor,
                textAlign = TextAlign.Center,
            )
            BigActionButton(
                icon = AppIcons.Done,
                text = done,
                color = ActionGo,
                onClick = onClose,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}
