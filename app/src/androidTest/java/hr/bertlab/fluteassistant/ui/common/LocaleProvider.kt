// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import hr.bertlab.fluteassistant.ui.theme.FluteAssistantTheme
import java.util.Locale

/** Test helper: renders [content] as the app would on a device whose system language is [locale]. */
@Composable
fun LocaleProvider(locale: Locale, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val baseConfiguration = LocalConfiguration.current
    val (localized, configuration) = remember(locale, baseConfiguration) {
        val configuration = Configuration(baseConfiguration).apply { setLocales(LocaleList(locale)) }
        base.createConfigurationContext(configuration) to configuration
    }
    CompositionLocalProvider(
        LocalContext provides localized,
        LocalConfiguration provides configuration,
    ) {
        FluteAssistantTheme { content() }
    }
}
