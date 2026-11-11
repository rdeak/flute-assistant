// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import hr.bertlab.fluteassistant.ui.theme.FluteTheme
import hr.bertlab.fluteassistant.ui.theme.Sun

/**
 * Chunky pills on top of a tab, side by side and as wide as each other, one per entry: an
 * emoji and a word, the chosen one yellow. The tuner's two modes and the notes tab's two games
 * share it, so the pair looks the same on both. A screen reader hears the group as
 * [groupLabel] and each pill as a selectable choice.
 *
 * [emojiOf] may return an empty string for a word that carries its own emoji (the notes tab's
 * "🔎 Find"): no picture is drawn then, and the word alone fills the pill. [labelResOf] gives an
 * entry's word as a string resource (`@StringRes` does not apply to a function, and lint says so).
 */
@Composable
fun <T> ModeChoice(
    entries: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    groupLabel: String,
    emojiOf: (T) -> String,
    labelResOf: (T) -> Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 5.dp)
            .selectableGroup()
            .semantics { contentDescription = groupLabel },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The word shrinks rather than be cut short at large text ("👂 Slušaj" lost its end at twice the text on a
        // 360 dp phone): a cut word is worse than a small one. The floor is in dp, as one in sp doubles at the largest text.
        val wordStyle = MaterialTheme.typography.titleLarge
        val wordFloor = with(LocalDensity.current) { PillWordFloor.toSp() }
        entries.forEach { entry ->
            val isSelected = entry == selected
            val emoji = emojiOf(entry)
            ChunkyButton(
                onClick = { onSelect(entry) },
                selected = isSelected,
                color = if (isSelected) Sun else FluteTheme.colors.raised,
                depth = 5.dp,
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (emoji.isNotEmpty()) Emoji(emoji, fontSize = 26)
                    Text(
                        text = stringResource(labelResOf(entry)),
                        style = wordStyle,
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(minFontSize = wordFloor, maxFontSize = wordStyle.fontSize),
                    )
                }
            }
        }
    }
}

/** The least a pill's word shrinks to. */
private val PillWordFloor = 12.dp
