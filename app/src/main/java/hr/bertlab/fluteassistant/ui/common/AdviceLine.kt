// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import hr.bertlab.fluteassistant.ui.theme.FluteTheme
import hr.bertlab.fluteassistant.ui.tuner.TunerAdvice

/**
 * The quieter line saying what to change: roll the flute, the air, the fingers, come closer, or
 * play a clear note. Under the tuner's status line, and under the flute's staff while it listens.
 * Empty text for [TunerAdvice.NONE] keeps the line's height, so nothing around it moves when
 * advice comes and goes.
 *
 * With [wrap] it is a small block for the narrow column of a phone on its side: up to four lines at
 * [wrapMaxFont] (19 sp), with no height of its own. A large text size shrinks it, as a fifth line
 * would lose the end of the advice; a caller that gives it a height of its own makes it shrink to
 * that too. It never shrinks below [WrapMinFont], a size in dp and not sp: at the largest text size
 * a floor in sp is twice as big, and Croatian's longest word would not fit the 100 dp column at it.
 * Nor does it grow to a size where a word is wider than the line and would be split inside it, as
 * four lines are room enough to split one: the longest word is measured and caps the size.
 */
@Composable
fun AdviceLine(
    advice: TunerAdvice,
    modifier: Modifier = Modifier,
    wrap: Boolean = false,
    wrapMaxFont: TextUnit = 19.sp,
) {
    val style = MaterialTheme.typography.bodyLarge
    val text = advice.labelRes()?.let { stringResource(it) } ?: ""
    if (wrap) {
        WrappedAdvice(text, style.copy(lineHeight = 1.3.em), wrapMaxFont, modifier)
        return
    }
    Text(
        text = text,
        style = style,
        color = FluteTheme.colors.mutedText,
        textAlign = TextAlign.Center,
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 14.sp, maxFontSize = 22.sp),
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp),
    )
}

/** [AdviceLine]'s block of up to four lines, no bigger than the biggest size at which every word of it fits a line. */
@Composable
private fun WrappedAdvice(text: String, style: TextStyle, maxFont: TextUnit, modifier: Modifier) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val referenceFont = with(density) { REFERENCE_PX.toSp() }
        val widestWord = remember(text, style, measurer, density) {
            text.split(' ')
                .filter { it.isNotEmpty() }
                .maxOfOrNull { measurer.measure(it, style.copy(fontSize = referenceFont)).size.width } ?: 0
        }
        // A word's width grows with the size, so the size it fills the line at follows from the one measured.
        val wordFits = if (widestWord == 0) maxFont else with(density) { (constraints.maxWidth * REFERENCE_PX / widestWord * WORD_ROOM).toSp() }
        val largest = smaller(maxFont, wordFits)
        val smallest = smaller(with(density) { WrapMinFont.toSp() }, largest)
        Text(
            text = text,
            style = style,
            color = FluteTheme.colors.mutedText,
            textAlign = TextAlign.Center,
            maxLines = 4,
            autoSize = TextAutoSize.StepBased(minFontSize = smallest, maxFontSize = largest),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The lesser of two sizes, which are not [Comparable] but compare. */
private fun smaller(a: TextUnit, b: TextUnit): TextUnit = if (a < b) a else b

/** The smallest the wrapped advice gets, whatever the text size: what 12 sp is at the normal one. */
private val WrapMinFont = 12.dp

/** The size, in px, a word is measured at. */
private const val REFERENCE_PX = 100f

/** The share of a line the longest word may fill, as a width is not quite proportional to the size once it is rounded to pixels. */
private const val WORD_ROOM = 0.97f
