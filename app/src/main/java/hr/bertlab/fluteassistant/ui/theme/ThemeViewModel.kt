// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.theme

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import hr.bertlab.fluteassistant.domain.settings.SettingsStore
import hr.bertlab.fluteassistant.domain.settings.ThemeChoice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * The child's day or night choice, remembered through [SettingsStore]. Activity-scoped like the
 * others, so it survives the re-creation a language change triggers. Applied as Compose state
 * (`MainScreen` hands it to the theme) and never through AppCompat's night mode, which would
 * re-create the activity and stop a playing tune.
 */
@HiltViewModel
class ThemeViewModel @Inject constructor(private val settings: SettingsStore) : ViewModel() {

    private val _choice = MutableStateFlow(settings.loadTheme())
    val choice: StateFlow<ThemeChoice> = _choice.asStateFlow()

    /** The moon or the sun was tapped: night if [dark], else day, from now on. */
    fun setDark(dark: Boolean) {
        val choice = if (dark) ThemeChoice.DARK else ThemeChoice.LIGHT
        _choice.value = choice
        settings.saveTheme(choice)
    }
}
