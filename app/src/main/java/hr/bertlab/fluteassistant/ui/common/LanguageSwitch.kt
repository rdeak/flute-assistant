// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.ui.theme.FluteTheme
import hr.bertlab.fluteassistant.ui.theme.Sun

/**
 * Two round flag buttons, one per [AppLanguage]; the active one has an ink outline and a
 * yellow ring around it, the other is faded. Flags and endonyms need no translation, so a
 * child who cannot read the current language still finds their own. Stateless: the host
 * applies the choice.
 */
@Composable
fun LanguageSwitch(
    current: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val groupLabel = stringResource(R.string.language)
    val colors = FluteTheme.colors
    Row(
        modifier = modifier
            .selectableGroup()
            .semantics { contentDescription = groupLabel },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AppLanguage.entries.forEach { language ->
            val isSelected = language == current
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .alpha(if (isSelected) 1f else 0.6f)
                    .drawBehind {
                        // The yellow ring sits just outside the outline.
                        if (isSelected) drawCircle(Sun, radius = size.minDimension / 2 + 3.dp.toPx(), style = Stroke(4.dp.toPx()))
                    }
                    .clip(CircleShape)
                    .background(colors.card)
                    .border(OutlineWidth, if (isSelected) colors.outline else colors.border, CircleShape)
                    .selectable(selected = isSelected, role = Role.RadioButton) { if (!isSelected) onSelect(language) }
                    .semantics { contentDescription = language.endonym },
            ) {
                Text(language.flag, style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp))
            }
        }
    }
}
