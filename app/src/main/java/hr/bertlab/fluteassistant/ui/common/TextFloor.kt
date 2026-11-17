// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/**
 * [this] as a text size that stays what it is at every text size the phone is set to. A size in sp grows with that
 * setting, so a floor in sp is twice as big at the largest text and pushes a word out of its room; the smallest a
 * shrinking word gets is therefore a size in dp, turned into a text size here.
 */
@Composable
@ReadOnlyComposable
fun Dp.asTextSize(): TextUnit = with(LocalDensity.current) { toSp() }

/**
 * The smallest a shrinking word gets on a pill, a button and the advice line, whatever the text size: what 12 sp is at
 * the normal one. A place with less room names its own (the flute's signed key names, the notes tab's picker).
 */
val WordFloor = 12.dp
