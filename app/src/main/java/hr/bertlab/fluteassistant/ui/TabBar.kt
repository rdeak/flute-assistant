// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import hr.bertlab.fluteassistant.R
import hr.bertlab.fluteassistant.ui.common.Emoji
import hr.bertlab.fluteassistant.ui.common.OutlineWidth
import hr.bertlab.fluteassistant.ui.common.asTextSize
import hr.bertlab.fluteassistant.ui.common.hopOnBeat
import hr.bertlab.fluteassistant.ui.common.rememberBeatFlash
import hr.bertlab.fluteassistant.ui.theme.FluteTheme
import hr.bertlab.fluteassistant.ui.theme.InkOnFill
import hr.bertlab.fluteassistant.ui.theme.Sun
import kotlin.math.roundToInt

/**
 * The bottom bar: one [TabItem] per tab. It draws its own background under the system navigation
 * bar. While the metronome's drum beats ([drumBeating]) the metronome tab's 🥁 hops on every
 * [beatPulse], on every tab, the same hop as the ring's animal.
 *
 * Since 2026-11-17 it holds five tabs, 73 dp each on a 412 dp phone and 62 on a 360 dp one, and its sizes are in dp
 * as the rail's are ([BarLook], [TabLook]). With a floor in sp "Metronome" was cut on a 360 dp phone at normal text
 * and on any phone at a little larger text (`TabBarTest`).
 */
@Composable
internal fun TabBar(selected: AppTab, onSelect: (AppTab) -> Unit, drumBeating: Boolean, beatPulse: Long) {
    val colors = FluteTheme.colors
    val drumFlash = rememberBeatFlash(beatPulse, drumBeating)
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.card)
            .drawBehind { drawRect(colors.border, size = Size(size.width, OutlineWidth.toPx())) }
            .navigationBarsPadding()
            .selectableGroup()
            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 8.dp)
            .height(66.dp),
    ) {
        AppTab.entries.forEach { tab ->
            TabItem(
                tab = tab,
                selected = tab == selected,
                onSelect = onSelect,
                drumBeating = drumBeating,
                drumFlash = drumFlash,
                look = BarLook,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

/**
 * How a tab looks on the bar or the rail, every size in dp: the [emoji] at the most (a short cell draws it smaller,
 * [TabItem]); the [word] at normal text, the least it shrinks to ([wordFloor]) and the most it grows with the text
 * size, as a share of its size at normal text ([wordGrowth]); the room either side of the word; the open tab's pill.
 * In dp and not sp, so that large text does not grow the emoji out of its cell, and as a floor in sp doubles at the
 * largest text and cut "Metronome".
 */
@Immutable
internal data class TabLook(
    val emoji: Dp,
    val word: Dp,
    val wordFloor: Dp,
    val wordGrowth: Float,
    val wordPadding: Dp,
    val shape: Shape,
)

/** The bottom bar's tabs, in its 66 dp (the rail's two are `SideRail`'s). */
private val BarLook = TabLook(emoji = 28.dp, word = 14.dp, wordFloor = 9.dp, wordGrowth = 1.3f, wordPadding = 5.dp, shape = RoundedCornerShape(22.dp))

/** A tab's word's line height: relative, as one in sp grows past the cell at large text. */
private val TabWordLineHeight = 1.25.em

/**
 * One tab, on the bottom bar or the rail: an emoji over a word, the open one a yellow pill with an
 * ink outline. While the drum beats the metronome's 🥁 hops on [drumFlash] and a screen reader hears
 * that tab as "Playing". The bar and the rail each pass their own [look], in dp, which this turns into the word's
 * sizes: there is no default, as a floor in sp cut "Metronome".
 *
 * The tab measures its own cell (since 2026-11-17; the rail used to work the cell's height out by hand from its
 * paddings and guess the lines' heights). The word is measured first and keeps its size; the emoji, drawn at
 * [TabLook.emoji], takes what the word and the outline leave, and is drawn smaller, down to [TAB_EMOJI_FLOOR] of its
 * size, where that is less than it needs: a cut word is worse than a small picture. So the word stands clear of the
 * open tab's outline at any height, text size and number of tabs, with no number to search again. The emoji's size is
 * therefore the most: an emoji's line is about 1.37 times its size on a phone, so the 360 dp rail's cell leaves its
 * 20 dp about 19 and the 411 dp rail's its 24 about 23.5, as they were drawn before.
 */
@Composable
internal fun TabItem(
    tab: AppTab,
    selected: Boolean,
    onSelect: (AppTab) -> Unit,
    drumBeating: Boolean,
    drumFlash: () -> Float,
    look: TabLook,
    modifier: Modifier = Modifier,
) {
    val colors = FluteTheme.colors
    val drumPlaying = stringResource(R.string.tab_metronome_playing)
    val shape = look.shape
    val wordSize = (look.word * minOf(LocalDensity.current.fontScale, look.wordGrowth)).asTextSize()
    Layout(
        content = {
            Emoji(
                tab.emoji,
                size = look.emoji,
                modifier = if (tab == AppTab.METRONOME) Modifier.hopOnBeat(drumFlash) else Modifier,
            )
            Text(
                text = stringResource(tab.labelRes),
                style = MaterialTheme.typography.labelMedium.copy(lineHeight = TabWordLineHeight),
                color = if (selected) InkOnFill else colors.text,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = look.wordFloor.asTextSize(), maxFontSize = wordSize),
                modifier = Modifier.padding(horizontal = look.wordPadding),
            )
        },
        modifier = modifier
            .clip(shape)
            .background(if (selected) Sun else Color.Transparent)
            .border(OutlineWidth, if (selected) colors.outline else Color.Transparent, shape)
            .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(tab) })
            .semantics {
                if (tab == AppTab.METRONOME && drumBeating) stateDescription = drumPlaying
            },
    ) { (emojiBox, wordBox), constraints ->
        val emoji = emojiBox.measure(Constraints())
        // What the cell has between its outlines: all of it, where the cell has no height of its own.
        val inside = if (constraints.hasBoundedHeight) (constraints.maxHeight - (OutlineWidth * 2).roundToPx()).coerceAtLeast(0) else Constraints.Infinity
        val wordRoom = if (inside == Constraints.Infinity) inside else (inside - (emoji.height * TAB_EMOJI_FLOOR).roundToInt()).coerceAtLeast(0)
        val word = wordBox.measure(Constraints(maxWidth = constraints.maxWidth, maxHeight = wordRoom))
        val scale = if (inside == Constraints.Infinity || emoji.height == 0) 1f else ((inside - word.height).toFloat() / emoji.height).coerceIn(TAB_EMOJI_FLOOR, 1f)
        val emojiHeight = (emoji.height * scale).roundToInt()
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else maxOf(emoji.width, word.width, constraints.minWidth)
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else maxOf(emojiHeight + word.height, constraints.minHeight)
        layout(width, height) {
            val top = (height - emojiHeight - word.height) / 2
            emoji.placeWithLayer((width - emoji.width) / 2, top) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0.5f, 0f)
            }
            word.place((width - word.width) / 2, top + emojiHeight)
        }
    }
}

/** The least a tab's emoji is drawn at, as a share of its size: 12 dp of the short rail's 20, on a very short window. */
private const val TAB_EMOJI_FLOOR = 0.6f
