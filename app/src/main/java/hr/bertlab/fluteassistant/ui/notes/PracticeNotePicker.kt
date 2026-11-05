// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.domain.notes.StaffNotes
import hr.bertlab.fluteassistant.ui.common.AppIcons
import hr.bertlab.fluteassistant.ui.common.BigActionButton
import hr.bertlab.fluteassistant.ui.common.ChunkyButton
import hr.bertlab.fluteassistant.ui.common.Emoji
import hr.bertlab.fluteassistant.ui.common.OutlineWidth
import hr.bertlab.fluteassistant.ui.common.dashedBorder
import hr.bertlab.fluteassistant.ui.common.noteColor
import hr.bertlab.fluteassistant.ui.common.noteLabel
import hr.bertlab.fluteassistant.ui.common.noteText
import hr.bertlab.fluteassistant.ui.theme.ActionGo
import hr.bertlab.fluteassistant.ui.theme.FluteTheme
import hr.bertlab.fluteassistant.ui.theme.Go

/**
 * The game's notes in rows by octave, highest on top as on the staff and the flute: c3
 * alone, then c2 to h2, then c1 to h1, left-aligned so the letters line up.
 */
val OCTAVE_ROWS: List<List<Int>> = StaffNotes.ALL.chunked(7).reversed()

/**
 * The notes the game may ask, one pill each in [OCTAVE_ROWS]: the chosen ones filled in
 * their letter's colour, the rest dashed. A tap calls [onToggle]; the ViewModel keeps the
 * last note from being switched off. Under them one switch row for the sound of a found
 * note ([soundOn], [onToggleSound]): a setting, so it lives here rather than on the game
 * screen. Last, one big OK button ([onDone]) closes the picker: choices apply as they are
 * tapped, so OK only brings the game back.
 */
@Composable
fun PracticeNotePicker(
    selected: Set<Int>,
    onToggle: (Int) -> Unit,
    soundOn: Boolean,
    onToggleSound: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FluteTheme.colors
    val groupLabel = stringResource(R.string.practice_notes)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.semantics { contentDescription = groupLabel },
        ) {
            OCTAVE_ROWS.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.height(54.dp)) {
                    row.forEach { midi ->
                        val isSelected = midi in selected
                        val shape = RoundedCornerShape(20.dp)
                        if (isSelected) {
                            ChunkyButton(
                                onClick = { onToggle(midi) },
                                checked = true,
                                shape = shape,
                                color = noteColor(midi),
                                modifier = Modifier.size(width = 44.dp, height = 50.dp),
                            ) { PillLabel(midi) }
                        } else {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(width = 44.dp, height = 50.dp)
                                    .dashedBorder(OutlineWidth / 2, colors.border, shape)
                                    .clip(shape)
                                    .toggleable(value = false, role = Role.Checkbox) { onToggle(midi) },
                            ) { PillLabel(midi, muted = true) }
                        }
                    }
                }
            }
        }
        SoundSettingRow(soundOn = soundOn, onToggleSound = onToggleSound)
        BigActionButton(
            icon = AppIcons.Done,
            text = stringResource(R.string.choose_notes_done),
            color = ActionGo,
            onClick = onDone,
            modifier = Modifier.padding(bottom = 8.dp),
        )
    }
}

@Composable
private fun PillLabel(midi: Int, muted: Boolean = false) {
    val label = noteLabel(midi)
    Text(
        text = noteText(midi),
        style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
        color = if (muted) FluteTheme.colors.mutedText else Color.Unspecified,
        maxLines = 1,
        modifier = Modifier.semantics { contentDescription = label },
    )
}

/**
 * One rounded settings row, the shape every phone uses for an on/off choice: a speaker
 * (muted while off) and the word at the start, a chunky switch at the end. The whole row
 * toggles and is the one accessible node.
 */
@Composable
private fun SoundSettingRow(soundOn: Boolean, onToggleSound: () -> Unit) {
    val colors = FluteTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(CircleShape)
            .background(colors.track)
            .border(OutlineWidth, colors.border, CircleShape)
            .toggleable(value = soundOn, role = Role.Switch, onValueChange = { onToggleSound() })
            .padding(start = 20.dp, end = 10.dp),
    ) {
        Emoji(if (soundOn) SPEAKER_EMOJI else MUTED_EMOJI, fontSize = 22)
        Text(
            text = stringResource(R.string.note_sound),
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            modifier = Modifier
                .padding(start = 10.dp)
                .weight(1f),
        )
        Box(
            contentAlignment = if (soundOn) Alignment.CenterEnd else Alignment.CenterStart,
            modifier = Modifier
                .size(width = 76.dp, height = 44.dp)
                .clip(CircleShape)
                .background(if (soundOn) Go else colors.raised)
                .border(OutlineWidth, colors.outline, CircleShape)
                .padding(horizontal = 4.dp),
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(colors.card)
                    .border(OutlineWidth, colors.outline, CircleShape),
            )
        }
    }
}

private const val SPEAKER_EMOJI = "🔊" // speaker, loud
private const val MUTED_EMOJI = "🔇" // speaker, muted
