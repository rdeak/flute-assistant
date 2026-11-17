// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import hr.bertlab.fluteassistant.domain.listen.TunerAdvice
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * The one-line advice under the tuner's status and the flute's staff: its longest lines stay whole on a narrow
 * phone (360 and 320 dp) at normal and large text, and a line that shrank sits in the middle of its 28 dp, not at its top.
 */
@RunWith(AndroidJUnit4::class)
class AdviceLineTest {

    @get:Rule
    val compose = createComposeRule()

    private var advice by mutableStateOf(TunerAdvice.OUT_OF_AIR)
    private var locale by mutableStateOf(Locale.ENGLISH)
    private var scale by mutableStateOf(1f)
    private var width by mutableStateOf(360)

    private fun show() {
        compose.setContent {
            LocaleProvider(locale) {
                UprightHost(DpSize(width.dp, 200.dp), scale) {
                    // The forced size makes its content fill it; the column lets the slot take the line's own height.
                    Column { Box(Modifier.testTag("slot")) { AdviceLine(advice) } }
                }
            }
        }
    }

    private val longest = mapOf(
        Locale.ENGLISH to listOf(TunerAdvice.OUT_OF_AIR to "😮‍💨 Maybe out of air? Big breath first", TunerAdvice.HEAD_JOINT_OUT to "🔧 Pull the head joint out a little"),
        Locale("hr") to listOf(TunerAdvice.OUT_OF_AIR to "😮‍💨 Možda nema zraka? Udahni", TunerAdvice.HEAD_JOINT_IN to "🔧 Gurni glavu flaute malo unutra"),
    )

    @Test
    fun the_longest_lines_stay_whole_and_centred_on_a_narrow_phone() {
        show()
        for (phone in listOf(360, 320)) for ((language, lines) in longest) for (textScale in listOf(1f, 1.5f)) for ((each, text) in lines) {
            compose.runOnIdle {
                width = phone
                locale = language
                scale = textScale
                advice = each
            }
            compose.waitForIdle()
            val case = "$phone dp, $language at $textScale: $text"
            val node = compose.onNodeWithText(text, useUnmergedTree = true)
            assertWhole(case, node, textScale)
            val layouts = mutableListOf<TextLayoutResult>()
            node.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            val slot = compose.onNodeWithTag("slot").getUnclippedBoundsInRoot()
            val line = node.getUnclippedBoundsInRoot()
            assertEquals("$case: the slot", 28f, (slot.bottom - slot.top).value, 0.5f)
            // The line's box is as tall as its text, in the middle of the slot. Its paragraph's height, not the
            // layout's size, which a fixed height clamps to the slot.
            val textHeight = with(compose.density) { layouts.single().multiParagraph.height.toDp() }
            assertEquals("$case: the line's box", textHeight.value, (line.bottom - line.top).value, 0.5f)
            assertEquals("$case: the line's middle", ((slot.top + slot.bottom) / 2).value, ((line.top + line.bottom) / 2).value, 0.5f)
        }
    }
}
