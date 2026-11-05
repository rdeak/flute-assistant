// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.ui.theme.FluteTheme
import hr.bertlab.fluteassistant.ui.theme.Sun

/**
 * One round button with the current language's flag; a tap opens a small list of every
 * [AppLanguage]'s flag, the current one with an ink outline and a yellow ring. Flags alone, no
 * words: they need no translation, so a child who cannot read the current language still finds
 * their own, and a screen reader hears each language's endonym. The other flag is not faded, as a
 * faded flag looks as if it cannot be pressed. Picking any flag closes the list. Stateless about the
 * language: the host applies the choice.
 */
@Composable
fun LanguageSwitch(
    current: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val label = stringResource(R.string.language)
    val colors = FluteTheme.colors
    Box(modifier) {
        ChunkyButton(
            onClick = { open = true },
            modifier = Modifier
                .size(48.dp)
                .semantics {
                    contentDescription = label
                    stateDescription = current.endonym
                },
        ) {
            Emoji(current.flag, fontSize = 24)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            offset = DpOffset(0.dp, 12.dp),
            shape = StageShape,
            containerColor = colors.card,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(OutlineWidth, colors.outline),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .selectableGroup()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                AppLanguage.entries.forEach { language ->
                    FlagChoice(
                        language = language,
                        selected = language == current,
                        onClick = {
                            open = false
                            if (language != current) onSelect(language)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FlagChoice(language: AppLanguage, selected: Boolean, onClick: () -> Unit) {
    val colors = FluteTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .drawBehind {
                // The yellow ring sits just outside the outline.
                if (selected) drawCircle(Sun, radius = size.minDimension / 2 + 3.dp.toPx(), style = Stroke(4.dp.toPx()))
            }
            .clip(CircleShape)
            .background(colors.card)
            .border(OutlineWidth, if (selected) colors.outline else colors.border, CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = language.endonym },
    ) {
        Text(language.flag, style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp))
    }
}
