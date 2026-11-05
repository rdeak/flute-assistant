// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.ui.theme.AppIconGold

/**
 * The app icon as the canvas draws it in the top bar: the picture (@drawable/ic_app_art, the
 * same one the launcher shows) on a golden square with the icon's rounded corners.
 */
@Composable
fun AppLogo(contentDescription: String?, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_app_art),
        contentDescription = contentDescription,
        modifier = modifier
            .clip(AppLogoShape)
            .background(AppIconGold),
    )
}

/** The canvas's 114 px corners on its 512 px icon. */
private val AppLogoShape = RoundedCornerShape(percent = 22)
