// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Bertlab d.o.o. Additional terms: see NOTICE.

package hr.bertlab.fluteassistant.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import hr.bertlab.fluteassistant.ui.theme.FluteAssistantTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The spec's headline promise, that nothing stops when the phone turns, rests on the open tab being
 * called in one place in `MainFrame` whichever way the phone stands: were it in a branch of its own,
 * it would leave the composition, and `FluteScreen`'s `DisposableEffect` would call `leaveTab()` and
 * stop the tune. `MainScreen` needs Hilt view models, so this runs the frame alone with a tab that
 * counts what the real one would lose: a `DisposableEffect` entered and disposed, and an object
 * `remember`ed. Turning the phone is a flip of `sideways`.
 */
@RunWith(AndroidJUnit4::class)
class MainFrameTest {

    @get:Rule
    val compose = createComposeRule()

    private var sideways by mutableStateOf(false)

    /** Times the tab's effect was entered and disposed, and its remembered object made. */
    private var entered = 0
    private var disposed = 0
    private var made = 0

    /** The remembered object, noted every time the tab is composed again. */
    private val seen = mutableListOf<Any>()

    private class Remembered

    @Composable
    private fun CountingTab(sideways: Boolean) {
        val remembered = remember { Remembered().also { made++ } }
        DisposableEffect(Unit) {
            entered++
            onDispose { disposed++ }
        }
        SideEffect { seen += remembered }
        Text("the tab, sideways: $sideways", Modifier.testTag(TAB))
    }

    private fun show() {
        compose.setContent {
            FluteAssistantTheme {
                MainFrame(
                    sideways = sideways,
                    snackbarHostState = remember { SnackbarHostState() },
                    titleBar = { Text("title", Modifier.testTag(TITLE_BAR)) },
                    tabBar = { Text("tabs", Modifier.testTag(TAB_BAR)) },
                    rail = { Box(Modifier.width(92.dp).fillMaxHeight().testTag(RAIL)) },
                ) {
                    CountingTab(sideways)
                }
            }
        }
    }

    private fun turn() {
        compose.runOnIdle { sideways = !sideways }
        compose.waitForIdle()
    }

    private fun assertUpright() {
        compose.onNodeWithTag(TITLE_BAR).assertIsDisplayed()
        compose.onNodeWithTag(TAB_BAR).assertIsDisplayed()
        compose.onNodeWithTag(RAIL).assertDoesNotExist()
        compose.onNodeWithTag(TAB).assertIsDisplayed()
    }

    private fun assertOnItsSide() {
        compose.onNodeWithTag(RAIL).assertIsDisplayed()
        compose.onNodeWithTag(TITLE_BAR).assertDoesNotExist()
        compose.onNodeWithTag(TAB_BAR).assertDoesNotExist()
        compose.onNodeWithTag(TAB).assertIsDisplayed()
    }

    @Test
    fun upright_has_the_two_bars_and_sideways_the_rail() {
        show()
        assertUpright()
        turn()
        assertOnItsSide()
        turn()
        assertUpright()
    }

    @Test
    fun the_tab_is_not_disposed_or_made_again_when_the_phone_turns() {
        show()
        assertEquals("entered when first shown", 1, entered)
        assertEquals("disposed before any turn", 0, disposed)
        repeat(3) {
            turn()
            assertOnItsSide()
            compose.onNodeWithTag(TAB).assertTextEquals("the tab, sideways: true")
            turn()
            assertUpright()
            compose.onNodeWithTag(TAB).assertTextEquals("the tab, sideways: false")
        }
        assertEquals("the effect was entered again: the tab left the composition", 1, entered)
        assertEquals("the effect was disposed: the tab left the composition", 0, disposed)
        assertEquals("the remembered object was made again", 1, made)
        // Composed again on every turn (it was handed the new value), and the same object each time.
        assertTrue("the tab was not composed again on a turn: ${seen.size} passes", seen.size > 1)
        seen.forEach { assertSame("a different object was remembered", seen.first(), it) }
    }

    private companion object {
        const val TITLE_BAR = "title_bar"
        const val TAB_BAR = "tab_bar"
        const val RAIL = "rail"
        const val TAB = "tab"
    }
}
