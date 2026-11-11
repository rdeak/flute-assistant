// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
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
 * offered the long press as an action. Whether they are open is kept here, unless the caller
 * passes [isOpen] and [onOpenChange] together and keeps it: the top bar and the side rail are
 * different places on the screen, so a turn between them would otherwise close the credits.
 */
@Composable
fun CreditsLogo(
    modifier: Modifier = Modifier,
    isOpen: Boolean? = null,
    onOpenChange: ((Boolean) -> Unit)? = null,
) {
    var ownOpen by rememberSaveable { mutableStateOf(false) }
    val open = isOpen ?: ownOpen
    val setOpen by rememberUpdatedState(onOpenChange ?: { ownOpen = it })
    val openLabel = stringResource(R.string.credits_open)
    AppLogo(
        contentDescription = stringResource(R.string.app_name),
        modifier = modifier
            .pointerInput(Unit) { detectTapGestures(onLongPress = { setOpen(true) }) }
            .semantics {
                onLongClick(label = openLabel) {
                    setOpen(true)
                    true
                }
            },
    )
    if (open) Credits(onClose = { setOpen(false) })
}

/**
 * The version, the website's address, who holds the copyright, and the Android robot's attribution
 * line (the icon modifies Google's robot, which its brand guidelines allow with this line, CC BY
 * 3.0). No privacy line and no licence (the user's call on 2026-11-11): the website's footer leads
 * to the policy. The address is plain text, never a link, so a child cannot leave the app from
 * here. It scrolls for large text on a short screen. The strings are read before the [Dialog]: its
 * window has a context of its own, which the UI tests' LocaleProvider does not reach.
 */
@Composable
private fun Credits(onClose: () -> Unit) {
    val context = LocalContext.current
    val installed = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
    val name = stringResource(R.string.app_name)
    val version = stringResource(R.string.credits_version, installed)
    val website = stringResource(R.string.credits_website_url)
    val copyright = stringResource(R.string.credits_copyright)
    val robot = stringResource(R.string.credits_android_robot)
    val done = stringResource(R.string.credits_done)
    Dialog(onDismissRequest = onClose) {
        Stage(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.verticalScroll(rememberScrollState()),
        ) {
            AppLogo(contentDescription = null, modifier = Modifier.size(64.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Line(name, MaterialTheme.typography.titleLarge)
                Line(version)
            }
            Line(website, MaterialTheme.typography.labelLarge)
            Line(copyright)
            Line(robot)
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

@Composable
private fun Line(text: String, style: TextStyle = MaterialTheme.typography.bodyMedium) {
    Text(text = text, style = style, color = FluteTheme.colors.text, textAlign = TextAlign.Center)
}
