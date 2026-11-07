// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import hr.bertlab.fluteassistant.R
import kotlin.math.roundToInt

/**
 * One round button beside the flag that turns the app's colours from day to night and back: a
 * moon by day (tap it for night) and a sun by night (tap it for day), so the picture says what
 * the tap does, as Play shows while stopped. No words: the picture needs no translation. A
 * screen reader hears it as a "Dark theme" switch that is on at night. Stateless about the
 * theme: the host applies the choice. The picture is [iconSize] dp, not sp, so large text cannot
 * push it out of its circle, like the flag.
 */
@Composable
fun ThemeSwitch(
    dark: Boolean,
    onDarkChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = ThemeIconSize,
) {
    val font = with(LocalDensity.current) { iconSize.toSp().value.roundToInt() }
    val label = stringResource(R.string.theme_dark)
    ChunkyButton(
        onClick = { onDarkChange(!dark) },
        checked = dark,
        role = Role.Switch,
        modifier = modifier
            .size(48.dp)
            .semantics { contentDescription = label },
    ) {
        Emoji(if (dark) SUN else MOON, fontSize = font)
    }
}

/** The moon or sun's size where the caller gives none, the flag's. */
private val ThemeIconSize = 24.dp

private const val MOON = "🌙" // crescent moon
private const val SUN = "☀️" // sun, as an emoji
