// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui

import java.util.Locale

/** Test helper: the five tabs' words in the order they stand on the bar and the rail, in English and in Croatian. */
internal val TAB_WORDS = listOf("Flute", "Metronome", "Tuner", "Notes", "Fingers")
internal val TAB_WORDS_HR = listOf("Flauta", "Metronom", "Štimer", "Note", "Hvatovi")

internal fun tabWords(locale: Locale): List<String> = if (locale.language == "hr") TAB_WORDS_HR else TAB_WORDS
