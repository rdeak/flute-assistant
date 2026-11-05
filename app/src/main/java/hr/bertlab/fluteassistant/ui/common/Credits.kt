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
 * The version, where the privacy policy is (Play asks for it inside the app as well as on the
 * listing), who holds the copyright, the licence and where to read it. The addresses are plain text,
 * never links, so a child cannot leave the app from here. It scrolls for large text on a short
 * screen. The strings are read before the [Dialog]: its window has a context of its own, which the
 * UI tests' LocaleProvider does not reach.
 */
@Composable
private fun Credits(onClose: () -> Unit) {
    val context = LocalContext.current
    val installed = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
    val name = stringResource(R.string.app_name)
    val version = stringResource(R.string.credits_version, installed)
    val privacy = stringResource(R.string.credits_privacy)
    val privacyAddress = stringResource(R.string.credits_privacy_url)
    val copyright = stringResource(R.string.credits_copyright)
    val licence = stringResource(R.string.credits_licence)
    val licenceText = stringResource(R.string.credits_licence_text)
    val licenceAddress = stringResource(R.string.credits_licence_url)
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
            Address(privacy, privacyAddress)
            Line(copyright)
            Line(licence)
            Address(licenceText, licenceAddress)
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

/** A label with its address under it, in bolder type. */
@Composable
private fun Address(label: String, address: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Line(label)
        Line(address, MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun Line(text: String, style: TextStyle = MaterialTheme.typography.bodyMedium) {
    Text(text = text, style = style, color = FluteTheme.colors.text, textAlign = TextAlign.Center)
}
