// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import hr.bertlab.fluteassistant.ui.common.AppLanguage
import hr.bertlab.fluteassistant.ui.common.CreditsLogo
import hr.bertlab.fluteassistant.ui.common.LanguageSwitch
import hr.bertlab.fluteassistant.ui.common.ThemeSwitch
import hr.bertlab.fluteassistant.ui.theme.FluteTheme
import androidx.compose.runtime.Stable
import androidx.compose.runtime.saveable.Saver

/**
 * What the bars open over the app: the credits (a long press on the logo) and the language list (the flag). The
 * title bar and the rail stand in different places and only one of them is on screen, so whoever shows both keeps
 * one of these above them (`MainScreen`): a turn between the two then closes neither.
 */
@Stable
internal class BarPanels(creditsOpen: Boolean = false) {
    var creditsOpen by mutableStateOf(creditsOpen)
    var languageOpen by mutableStateOf(false)
}

/** The credits stay open through a re-created Activity; the language list does not, as choosing a language is what re-creates it. */
@Composable
internal fun rememberBarPanels(): BarPanels =
    rememberSaveable(saver = Saver(save = { it.creditsOpen }, restore = { BarPanels(creditsOpen = it) })) { BarPanels() }

/**
 * The in-app language switch, on the top bar or the rail. The current language is read from the
 * resources the words come from (which is what the per-app locale actually changed), so anything
 * that is not Croatian shows as English, the fallback. Choosing hands the tag to the platform's
 * per-app language setting: the system on Android 13+, AppCompat's stored backport below that.
 * Either way the activity is re-created in the new language.
 */
@Composable
internal fun AppLanguageSwitch(
    isOpen: Boolean,
    onOpenChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    listOffset: Dp = 0.dp,
) {
    // From the resources the words themselves come from, not from `LocalConfiguration`: a turn
    // hands Compose its configuration through `onConfigurationChanged` and nothing here can say
    // that this carries AppCompat's locale override below Android 13, so the flag could disagree
    // with the words. No `remember`: the resources hold one mutable configuration that is updated in
    // place, so a key on it would keep a stale answer, and picking the language is cheap.
    val locales = ConfigurationCompat.getLocales(LocalResources.current.configuration)
    // An empty list is not seen on a phone; English is the fallback anyway.
    val current = locales[0]?.let(AppLanguage::fromLocale) ?: AppLanguage.ENGLISH
    LanguageSwitch(
        current = current,
        onSelect = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(it.tag)) },
        isOpen = isOpen,
        onOpenChange = onOpenChange,
        modifier = modifier,
        listOffset = listOffset,
    )
}

/**
 * The top bar: the app icon, the open tab's name and, at the end, the day/night switch (a moon
 * by day, a sun by night, [dark] and [onDarkChange]) and then the in-app language switch. The
 * credits and the language list are open or shut as [panels] says: the caller that also shows the
 * rail hands both the same one, so a turn to the rail leaves them as they were.
 */
@Composable
internal fun TitleBar(
    tab: AppTab,
    dark: Boolean,
    onDarkChange: (Boolean) -> Unit,
    panels: BarPanels = rememberBarPanels(),
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(FluteTheme.colors.page)
            .statusBarsPadding()
            .height(72.dp)
            .padding(horizontal = 20.dp),
    ) {
        CreditsLogo(isOpen = panels.creditsOpen, onOpenChange = { panels.creditsOpen = it }, modifier = Modifier.size(48.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(tab.labelRes),
            style = MaterialTheme.typography.headlineMedium,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = 32.sp),
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
                .semantics { heading() },
        )
        ThemeSwitch(dark = dark, onDarkChange = onDarkChange)
        Spacer(Modifier.width(8.dp))
        AppLanguageSwitch(isOpen = panels.languageOpen, onOpenChange = { panels.languageOpen = it })
    }
}
